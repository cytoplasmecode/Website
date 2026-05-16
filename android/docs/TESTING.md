# Testing Guide

---

## Overview

The test suite is split into two layers:

| Layer | Location | Runner | When to run |
|---|---|---|---|
| **Unit tests** | `src/test/` | JVM (Robolectric for Android types) | Every commit — fast |
| **Instrumented tests** | `src/androidTest/` | On-device / emulator | Pre-release, CI |

Google Calendar API calls are **never made** during tests — `CalendarManager` is always mocked at the repository boundary.

---

## Running tests

### Unit tests (JVM)

```bash
# From the android/ directory:
./gradlew :app:test

# Or a single test class:
./gradlew :app:test --tests "com.cytoplasmecode.plantwatering.data.PlantRepositoryTest"
```

### Instrumented tests (requires a connected device or emulator)

```bash
./gradlew :app:connectedAndroidTest
```

### All tests

```bash
./gradlew :app:test :app:connectedAndroidTest
```

---

## Test files

```
src/test/
├── calendar/
│   └── EventTitlesTest.kt          # Pure-function title formatting
├── data/
│   └── PlantRepositoryTest.kt      # Business logic (MockK, coroutines-test)
├── ui/
│   └── PlantsViewModelTest.kt      # ViewModel delegation (Robolectric + MockK)
└── util/
    └── MainDispatcherRule.kt       # JUnit rule — replaces Dispatchers.Main

src/androidTest/
├── data/
│   └── PlantDaoTest.kt             # Room queries (in-memory database)
└── util/
    └── LiveDataTestUtil.kt         # getOrAwaitValue() extension for LiveData
```

---

## What each test file covers

### `EventTitlesTest` (8 tests)

Tests the two pure title-formatting functions in isolation:

- `pendingEventTitle("Basil")` → `"Water Basil"`
- `doneEventTitle("Alice", "Basil")` → `"DONE by Alice - Basil"`
- Multi-word plant names, multi-word user names, casing preservation

No mocking needed — these are pure Kotlin functions.

### `PlantRepositoryTest` (13 tests)

Tests all business logic in `PlantRepository` using MockK to stub `PlantDao` and `CalendarManager`.

| Scenario | Assertions |
|---|---|
| `addPlant` | Correct event name, correct date (today + interval), `null` lastWatered, event ID stored |
| `waterPlant` | `markEventDone` called with correct args including `userName`; new event created; DB updated |
| `waterPlant` with no pending event | `markEventDone` skipped, no crash |
| `updateInterval` | Old event deleted (exactly once), new event from today, DB updated with new interval |
| `updateInterval` with no pending event | `deleteEvent` skipped gracefully |
| `deletePlant` | Pending event deleted, plant removed from DB |
| `deletePlant` with no pending event | `deleteEvent` skipped gracefully |

### `PlantsViewModelTest` (7 tests)

Tests ViewModel behaviour using Robolectric for the `Application` context and MockK for the repository and calendar manager.

| Scenario | Assertions |
|---|---|
| `waterPlant` after `setAccount("Alice")` | Repository receives `"Alice"` |
| `waterPlant` before `setAccount` | Repository receives `"Unknown"` |
| Multiple `setAccount` calls | Latest name wins |
| `addPlant`, `deletePlant`, `updatePlantInterval` | Each delegates to the repository with exact arguments |
| `setAccount` | Forwards the account to `CalendarManager.setAccount` |

### `PlantDaoTest` (13 tests)

Instrumented tests using a Room **in-memory database** so nothing persists between test runs.

| Area | Tests |
|---|---|
| Insert + retrieve | Single plant, multiple plants, generated ID > 0 |
| Ordering | Plants returned ascending by `nextWateringMillis` |
| `getDuePlants` | Returns overdue plants, excludes future plants, handles exact-now boundary, multiple overdue plants, empty list |
| Update | Interval, `lastWateredMillis`, `pendingEventId` all persisted correctly |
| Delete | Removes target plant only; list is empty after deleting the last plant |

---

## Key utilities

### `MainDispatcherRule`

A JUnit `TestWatcher` that installs `UnconfinedTestDispatcher` as `Dispatchers.Main` for the duration of each test. Required for `viewModelScope` and `runTest` to work correctly in JVM tests.

```kotlin
@get:Rule val mainDispatcherRule = MainDispatcherRule()
```

### `LiveDataTestUtil.getOrAwaitValue()`

Blocks the calling thread until a `LiveData` emits a value, then returns it. Throws `TimeoutException` after 2 seconds. Used in `PlantDaoTest` to observe `getAllPlants()`.

```kotlin
val plants = dao.getAllPlants().getOrAwaitValue()
```

---

## Mocking strategy

| Component | Strategy | Reason |
|---|---|---|
| `CalendarManager` | MockK `mockk(relaxed = true)` in ViewModel tests; explicit stubs in Repository tests | Avoids real network calls |
| `PlantDao` | MockK stubs | Avoids real database in unit tests |
| `PlantRepository` | MockK `mockk(relaxed = true)` | Isolates ViewModel from business logic |
| `PlantDatabase` | Room in-memory | Fast, no disk I/O, clean state per test |
| Google Calendar REST API | Never touched in tests | Mocked at the `CalendarManager` boundary |

---

## What is not tested (and why)

| Component | Reason |
|---|---|
| `GoogleAuthManager` | Wraps Google Play Services, which cannot run on JVM or an emulator without a real Google account |
| `WateringReminderWorker` | WorkManager integration tests are slow and flaky; the logic (`getDuePlants` + `notify`) is covered by `PlantDaoTest` and manual testing |
| Full sign-in / Calendar UI flow | Requires real OAuth credentials; covered by manual QA |
| `CalendarManager` API calls | Network-dependent; tested indirectly by verifying the correct arguments reach the mock |
