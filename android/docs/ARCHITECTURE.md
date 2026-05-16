# Architecture

The app follows **MVVM** with a clear separation between UI, business logic, and data sources. There are two data sources: a local Room database (fast, offline) and the Google Calendar API (remote, async).

---

## Layer diagram

```
┌─────────────────────────────────────────────┐
│                    UI Layer                  │
│  MainActivity · PlantAdapter                 │
│  AddPlantDialog · EditIntervalDialog         │
│                  │                           │
│            PlantsViewModel                   │
│  - owns `userName` (from Google account)     │
│  - exposes `plants: LiveData<List<Plant>>`   │
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
```

---

## Database schema

**Table: `plants`**

| Column | Type | Notes |
|---|---|---|
| `id` | `INTEGER` PK (autoGenerate) | |
| `name` | `TEXT` | Display name, e.g. "Basil" |
| `intervalDays` | `INTEGER` | Watering frequency |
| `lastWateredMillis` | `INTEGER?` | Epoch ms; `null` if never watered |
| `nextWateringMillis` | `INTEGER` | Epoch ms; used for ordering and due-check |
| `pendingEventId` | `TEXT?` | Google Calendar event ID for the next scheduled event |

`nextWateringMillis` is indexed implicitly by the `ORDER BY` in `getAllPlants()` and the `WHERE` clause in `getDuePlants()`.

---

## Data flows

### Add a plant

```
User taps "Add"
  → AddPlantDialog validates input
  → PlantsViewModel.addPlant(name, intervalDays)
  → PlantRepository.addPlant()
      ├─ CalendarManager.createWateringEvent(name, today + interval)
      │    └─ Creates "Water <name>" all-day event → returns eventId
      └─ PlantDao.insert(Plant(…, pendingEventId = eventId))
```

### Water a plant

```
User taps "Water Now"
  → PlantsViewModel.waterPlant(plant)          // passes stored userName
  → PlantRepository.waterPlant(plant, userName)
      ├─ CalendarManager.markEventDone(eventId, plantName, userName)
      │    └─ Patches event title → "DONE by <userName> - <plantName>"
      ├─ CalendarManager.createWateringEvent(name, today + interval)
      │    └─ Creates new "Water <name>" event → returns nextEventId
      └─ PlantDao.update(plant.copy(
             lastWateredMillis = now,
             nextWateringMillis = today + interval,
             pendingEventId = nextEventId))
```

### Edit watering interval

```
User taps ✏ → enters new interval → taps "Save"
  → PlantsViewModel.updatePlantInterval(plant, newInterval)
  → PlantRepository.updateInterval(plant, newInterval)
      ├─ CalendarManager.deleteEvent(plant.pendingEventId)
      │    └─ Removes ONLY the pending future event
      │    ✗  Past "DONE by..." events are NEVER touched
      ├─ CalendarManager.createWateringEvent(name, today + newInterval)
      └─ PlantDao.update(plant.copy(
             intervalDays = newInterval,
             nextWateringMillis = today + newInterval,
             pendingEventId = newEventId))
```

### Daily reminder

```
WorkManager fires WateringReminderWorker (once per day)
  → PlantDao.getDuePlants(now)    // WHERE nextWateringMillis <= now
  → For each due plant:
      NotificationManagerCompat.notify(plantId, notification)
      // Notification links back to MainActivity
```

---

## Google Calendar integration

Authentication uses **Google Sign-In** (`play-services-auth`) requesting the `https://www.googleapis.com/auth/calendar` scope. After sign-in, `GoogleSignInAccount.account` is passed to `GoogleAccountCredential`, which handles token refresh automatically.

`CalendarManager` wraps all API calls in `withContext(Dispatchers.IO)` and uses `runCatching` so network failures are swallowed and do not crash the app. The database is always updated regardless of Calendar API success; if an event call fails, it is simply not reflected in the calendar.

Event titles are pure functions in `EventTitles.kt`:

```kotlin
fun pendingEventTitle(plantName: String) = "Water $plantName"
fun doneEventTitle(userName: String, plantName: String) = "DONE by $userName - $plantName"
```

This isolation makes them trivially testable without any mocking.

---

## Multi-user design

The `userName` is the `displayName` from `GoogleSignInAccount` (falls back to `email`). It is stored in `PlantsViewModel` at sign-in time and passed down through `PlantRepository.waterPlant()` to `CalendarManager.markEventDone()`. Each person's device signs into the same Google account and therefore shares the same calendar; the event title records who performed the watering.

There is no server-side component and no real-time sync between devices. The shared calendar acts as the single source of truth for historical records, while each device's local Room database is the source of truth for scheduling state.
