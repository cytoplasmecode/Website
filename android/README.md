# Plant Watering

An Android app that helps you remember to water your plants. Watering events are synced to Google Calendar so multiple household members can see who watered what and when.

---

## Features

| Feature | Details |
|---|---|
| **Google Sign-In** | OAuth 2.0 via Google Play Services — no separate backend |
| **Multiple plants** | Each plant has a name and its own watering interval (in days) |
| **Google Calendar sync** | Creates `Water <plant>` all-day events; marks them `DONE by <name> - <plant>` on watering |
| **Edit interval** | Change the frequency at any time — only future events are affected |
| **Multi-user** | Anyone with Calendar access can water plants; the event records their name |
| **Daily reminders** | Background WorkManager job notifies you when plants are due |
| **Boot persistence** | Reminders are rescheduled automatically after a device restart |

---

## Quick start

1. **Set up Google Cloud** — see [`SETUP.md`](SETUP.md)
2. Open the `android/` folder in **Android Studio Hedgehog** or later
3. Let Gradle sync complete
4. Run on a device or emulator with Google Play Services (API 26+)
5. Sign in → add plants → water them

---

## Project structure

```
android/
├── app/
│   └── src/
│       ├── main/java/com/cytoplasmecode/plantwatering/
│       │   ├── MainActivity.kt          # Entry point; sign-in / plant list
│       │   ├── auth/
│       │   │   └── GoogleAuthManager.kt # Google Sign-In wrapper
│       │   ├── calendar/
│       │   │   ├── CalendarManager.kt   # Google Calendar API calls
│       │   │   └── EventTitles.kt       # Pure title-formatting functions
│       │   ├── data/
│       │   │   ├── Plant.kt             # Room entity
│       │   │   ├── PlantDao.kt          # Room queries
│       │   │   ├── PlantDatabase.kt     # Singleton Room database
│       │   │   └── PlantRepository.kt   # Business logic; coordinates DAO + Calendar
│       │   ├── notifications/
│       │   │   ├── WateringReminderWorker.kt  # Daily WorkManager job
│       │   │   └── BootReceiver.kt            # Reschedules on boot
│       │   └── ui/
│       │       ├── PlantsViewModel.kt   # ViewModel; owns userName
│       │       ├── PlantAdapter.kt      # RecyclerView adapter
│       │       ├── AddPlantDialog.kt    # Bottom-sheet dialog — add plant
│       │       └── EditIntervalDialog.kt # Bottom-sheet dialog — change interval
│       ├── test/                        # JVM unit tests (MockK + Robolectric)
│       └── androidTest/                 # Instrumented tests (Room in-memory DB)
├── docs/
│   ├── ARCHITECTURE.md
│   ├── SETUP.md
│   └── TESTING.md
└── SETUP.md                             # Quick-start setup (mirrors docs/SETUP.md)
```

---

## Documentation

| Document | Contents |
|---|---|
| [SETUP.md](SETUP.md) | Google Cloud setup, SHA-1 fingerprint, first run |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Layers, data flow, database schema, Calendar integration |
| [docs/TESTING.md](docs/TESTING.md) | Test strategy, how to run tests, coverage targets |

---

## Tech stack

| Concern | Library |
|---|---|
| Language | Kotlin 1.9 |
| UI | View-based (XML), Material Components 3 |
| Architecture | MVVM — ViewModel + LiveData + Repository |
| Local storage | Room 2.6 |
| Background work | WorkManager 2.9 |
| Auth | Google Sign-In (`play-services-auth` 21) |
| Calendar API | `google-api-services-calendar v3` |
| Async | Kotlin Coroutines |
| Testing | JUnit 4, MockK, Robolectric, coroutines-test |
| Min SDK | API 26 (Android 8.0) |
