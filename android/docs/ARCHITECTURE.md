# Architecture

The app follows **MVVM** with a clear separation between UI, business logic, and data sources. There are two data sources: a local Room database (fast, offline) and the Google Calendar API (remote, async).

---

## Layer diagram

```
┌─────────────────────────────────────────────┐
│                    UI Layer                  │
│  MainActivity · PlantAdapter                 │
│  AddPlantDialog · EditIntervalDialog         │
│  CalendarPickerDialog                        │
│                  │                           │
│            PlantsViewModel                   │
│  - owns `userName` (from Google account)     │
│  - exposes `plants: LiveData<List<Plant>>`   │
│  - exposes `calendars: LiveData<…>`          │
└──────────────────┬──────────────────────────┘
                   │
┌──────────────────▼──────────────────────────┐
│              Business Logic                  │
│              PlantRepository                 │
│  - coordinates DAO and CalendarManager       │
│  - owns all scheduling math (nextWatering)   │
└──────┬───────────────────────┬──────────────┘
       │                       │
┌──────▼──────┐     ┌──────────▼──────────────┐
│  Data Layer │     │      Calendar Layer       │
│  PlantDao   │     │      CalendarManager      │
│  Room DB    │     │  Google Calendar REST API │
└─────────────┘     └─────────────────────────┘
                               │
                    ┌──────────▼──────────────┐
                    │    CalendarPreferences   │
                    │    SharedPreferences     │
                    │  (persists selected      │
                    │   calendar across runs)  │
                    └─────────────────────────┘
```

---

## Database schema

**Table: `plants`** (version 2)

| Column | Type | Notes |
|---|---|---|
| `id` | `INTEGER` PK (autoGenerate) | |
| `name` | `TEXT` | Display name, e.g. "Basil" |
| `intervalDays` | `INTEGER` | Watering frequency |
| `lastWateredMillis` | `INTEGER?` | Epoch ms; `null` if never watered |
| `nextWateringMillis` | `INTEGER` | Epoch ms; used for ordering and due-check |
| `pendingEventId` | `TEXT?` | Google Calendar event ID for the next scheduled event |
| `pendingEventCalendarId` | `TEXT?` | Calendar ID that holds the pending event (added in v2) |

`pendingEventCalendarId` is stored alongside `pendingEventId` so that `markEventDone` and `deleteEvent` always target the exact calendar that holds the event — even if the user later switches to a different calendar. Rows migrated from v1 have `NULL` here; the repository falls back to `"primary"` for those.

**Migration 1 → 2**

```sql
ALTER TABLE plants ADD COLUMN pendingEventCalendarId TEXT;
```

---

## Data flows

### Add a plant

```
User taps "Add"
  → AddPlantDialog validates input
  → PlantsViewModel.addPlant(name, intervalDays)
  → PlantRepository.addPlant()
      ├─ CalendarManager.createWateringEvent(name, today + interval)
      │    └─ Creates "Water <name>" in selectedCalendarId
      │    └─ Returns Pair(eventId, calendarId)
      └─ PlantDao.insert(Plant(…,
             pendingEventId = eventId,
             pendingEventCalendarId = calendarId))
```

### Water a plant

```
User taps "Water Now"
  → PlantsViewModel.waterPlant(plant)          // passes stored userName
  → PlantRepository.waterPlant(plant, userName)
      ├─ CalendarManager.markEventDone(
      │      plant.pendingEventId,
      │      plant.pendingEventCalendarId ?: "primary",   ← original calendar
      │      plantName, userName)
      │    └─ Patches event title → "DONE by <userName> - <plantName>"
      ├─ CalendarManager.createWateringEvent(name, today + interval)
      │    └─ Creates in the *currently selected* calendar
      │    └─ Returns Pair(nextEventId, calendarId)
      └─ PlantDao.update(plant.copy(
             lastWateredMillis = now,
             nextWateringMillis = today + interval,
             pendingEventId = nextEventId,
             pendingEventCalendarId = calendarId))
```

### Edit watering interval

```
User taps ✏ → enters new interval → taps "Save"
  → PlantsViewModel.updatePlantInterval(plant, newInterval)
  → PlantRepository.updateInterval(plant, newInterval)
      ├─ CalendarManager.deleteEvent(
      │      plant.pendingEventId,
      │      plant.pendingEventCalendarId ?: "primary")   ← original calendar
      │    ✗  Past "DONE by..." events are NEVER touched
      ├─ CalendarManager.createWateringEvent(name, today + newInterval)
      └─ PlantDao.update(plant.copy(
             intervalDays = newInterval,
             nextWateringMillis = today + newInterval,
             pendingEventId = newEventId,
             pendingEventCalendarId = newCalendarId))
```

### Change calendar

```
User taps ⋮ → "Change calendar"
  → PlantsViewModel.loadCalendars()
      └─ CalendarManager.fetchCalendars()
           └─ calendarList.list(minAccessRole="writer")
           └─ Returns only calendars where accessRole is "owner" or "writer"
  → CalendarPickerDialog shown with list of writable calendars
  → User selects a calendar
  → CalendarPreferences.select(info)        ← persisted to SharedPreferences
  → PlantsViewModel.setCalendarId(info.id)
  → CalendarManager.setCalendarId(info.id)

  All NEW events go into the new calendar.
  Existing pending events remain in their original calendars
  (tracked via pendingEventCalendarId) and are still correctly
  marked done when watered.
```

### Daily reminder

```
WorkManager fires WateringReminderWorker (once per day)
  → PlantDao.getDuePlants(now)    // WHERE nextWateringMillis <= now
  → For each due plant:
      NotificationManagerCompat.notify(plantId, notification)
```

---

## Google Calendar integration

Authentication uses **Google Sign-In** (`play-services-auth`) requesting the `https://www.googleapis.com/auth/calendar` scope. After sign-in, `GoogleSignInAccount.account` is passed to `GoogleAccountCredential`, which handles token refresh automatically.

`CalendarManager` wraps all API calls in `withContext(Dispatchers.IO)` and uses `runCatching` so network failures are swallowed and do not crash the app.

### Calendar selection

`fetchCalendars()` calls `calendarList.list(minAccessRole="writer")` which returns only calendars where the user can create events. Shared calendars appear here as long as the user has at least writer access. The API-level filter is reinforced by `CalendarInfo.isWritable` (checks `accessRole == "owner" || "writer"`) so that any edge-cases in the API response are caught client-side too.

The selected calendar ID is persisted in `CalendarPreferences` (SharedPreferences). On next launch, the saved ID is restored before `loadCalendars()` completes — if the saved calendar is no longer in the list (e.g. access was revoked), the picker is shown again.

### Event titles

Pure functions in `EventTitles.kt`:

```kotlin
fun pendingEventTitle(plantName: String) = "Water $plantName"
fun doneEventTitle(userName: String, plantName: String) = "DONE by $userName - $plantName"
```

---

## Multi-user design

The `userName` is the `displayName` from `GoogleSignInAccount` (falls back to `email`). It is stored in `PlantsViewModel` at sign-in time and passed down through `PlantRepository.waterPlant()` to `CalendarManager.markEventDone()`. Multiple people sharing the same Google Calendar (or a shared calendar they both have write access to) each sign in on their own device; the event title records who performed the watering.

---

## Calendar selection and shared calendars

To use a shared calendar:

1. The calendar owner shares it with edit access to all household members
2. Each member signs into the app with **their own Google account** (not the owner's)
3. The shared calendar appears in the picker because the API filters to `minAccessRole="writer"`
4. All watering events are created in that shared calendar
5. The `"DONE by <name> - <plant>"` title identifies who watered it

There is no server-side component. The shared calendar is the single source of truth for historical records; the local Room database holds scheduling state per device.
