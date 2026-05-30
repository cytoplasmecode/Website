package com.cytoplasmecode.plantwatering.data

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import com.cytoplasmecode.plantwatering.calendar.CalendarManager
import com.cytoplasmecode.plantwatering.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class PlantRepositoryTest {

    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val mockDao: PlantDao = mockk()
    private val mockCalendar: CalendarManager = mockk()
    private lateinit var repository: PlantRepository

    @Before
    fun setUp() {
        every { mockDao.getAllPlants() } returns MutableLiveData(emptyList())
        repository = PlantRepository(mockDao, mockCalendar)
    }

    // ── addPlant ──────────────────────────────────────────────────────────────

    @Test
    fun `addPlant creates a calendar event with the correct plant name`() = runTest {
        coEvery { mockCalendar.createWateringEvent(any(), any()) } returns Pair("evt_1", "cal_a")
        coEvery { mockDao.insert(any()) } returns 1L

        repository.addPlant("Basil", 3)

        coVerify { mockCalendar.createWateringEvent("Basil", any()) }
    }

    @Test
    fun `addPlant schedules the event on today plus the interval`() = runTest {
        val expectedDate = LocalDate.now().plusDays(5)
        coEvery { mockCalendar.createWateringEvent(any(), any()) } returns Pair("evt_1", "cal_a")
        coEvery { mockDao.insert(any()) } returns 1L

        repository.addPlant("Fern", 5)

        coVerify { mockCalendar.createWateringEvent("Fern", expectedDate) }
    }

    @Test
    fun `addPlant inserts a plant with null lastWateredMillis`() = runTest {
        coEvery { mockCalendar.createWateringEvent(any(), any()) } returns Pair("evt_1", "cal_a")
        coEvery { mockDao.insert(any()) } returns 1L

        repository.addPlant("Cactus", 14)

        coVerify { mockDao.insert(match { it.lastWateredMillis == null }) }
    }

    @Test
    fun `addPlant stores both the event ID and calendar ID returned by CalendarManager`() = runTest {
        coEvery { mockCalendar.createWateringEvent(any(), any()) } returns Pair("evt_xyz", "shared_cal")
        coEvery { mockDao.insert(any()) } returns 1L

        repository.addPlant("Orchid", 10)

        coVerify {
            mockDao.insert(match {
                it.pendingEventId == "evt_xyz" && it.pendingEventCalendarId == "shared_cal"
            })
        }
    }

    // ── waterPlant ────────────────────────────────────────────────────────────

    @Test
    fun `waterPlant marks the pending event done in the correct calendar`() = runTest {
        val plant = plant(pendingEventId = "old_evt", pendingEventCalendarId = "shared_cal")
        coEvery { mockCalendar.markEventDone(any(), any(), any(), any()) } just Runs
        coEvery { mockCalendar.createWateringEvent(any(), any()) } returns Pair("new_evt", "shared_cal")
        coEvery { mockDao.update(any()) } just Runs

        repository.waterPlant(plant, "Alice")

        coVerify { mockCalendar.markEventDone("old_evt", "shared_cal", "Basil", "Alice") }
    }

    @Test
    fun `waterPlant falls back to primary calendar when pendingEventCalendarId is null`() = runTest {
        val plant = plant(pendingEventId = "old_evt", pendingEventCalendarId = null)
        coEvery { mockCalendar.markEventDone(any(), any(), any(), any()) } just Runs
        coEvery { mockCalendar.createWateringEvent(any(), any()) } returns Pair("new_evt", "primary")
        coEvery { mockDao.update(any()) } just Runs

        repository.waterPlant(plant, "Alice")

        coVerify { mockCalendar.markEventDone("old_evt", "primary", "Basil", "Alice") }
    }

    @Test
    fun `waterPlant creates the next watering event in the currently selected calendar`() = runTest {
        val plant = plant(intervalDays = 3, pendingEventId = "old_evt", pendingEventCalendarId = "cal_a")
        coEvery { mockCalendar.markEventDone(any(), any(), any(), any()) } just Runs
        coEvery { mockCalendar.createWateringEvent(any(), any()) } returns Pair("new_evt", "cal_b")
        coEvery { mockDao.update(any()) } just Runs

        repository.waterPlant(plant, "Alice")

        val expectedNext = LocalDate.now().plusDays(3)
        coVerify { mockCalendar.createWateringEvent("Basil", expectedNext) }
    }

    @Test
    fun `waterPlant persists the new event ID and calendar ID from the new event`() = runTest {
        val plant = plant(pendingEventId = "old_evt", pendingEventCalendarId = "cal_a")
        coEvery { mockCalendar.markEventDone(any(), any(), any(), any()) } just Runs
        coEvery { mockCalendar.createWateringEvent(any(), any()) } returns Pair("next_evt", "new_cal")
        coEvery { mockDao.update(any()) } just Runs

        repository.waterPlant(plant, "Alice")

        coVerify {
            mockDao.update(match {
                it.pendingEventId == "next_evt" && it.pendingEventCalendarId == "new_cal"
            })
        }
    }

    @Test
    fun `waterPlant skips markEventDone when plant has no pending event`() = runTest {
        val plant = plant(pendingEventId = null, pendingEventCalendarId = null)
        coEvery { mockCalendar.createWateringEvent(any(), any()) } returns Pair("next_evt", "cal_a")
        coEvery { mockDao.update(any()) } just Runs

        repository.waterPlant(plant, "Bob")

        coVerify(exactly = 0) { mockCalendar.markEventDone(any(), any(), any(), any()) }
    }

    @Test
    fun `waterPlant forwards different user names correctly`() = runTest {
        val plant = plant(pendingEventId = "evt", pendingEventCalendarId = "cal_a")
        coEvery { mockCalendar.markEventDone(any(), any(), any(), any()) } just Runs
        coEvery { mockCalendar.createWateringEvent(any(), any()) } returns Pair("next", "cal_a")
        coEvery { mockDao.update(any()) } just Runs

        repository.waterPlant(plant, "Bob")

        coVerify { mockCalendar.markEventDone(any(), any(), any(), "Bob") }
    }

    // ── updateInterval ────────────────────────────────────────────────────────

    @Test
    fun `updateInterval deletes the pending event from its original calendar`() = runTest {
        val plant = plant(pendingEventId = "pending_evt", pendingEventCalendarId = "shared_cal")
        coEvery { mockCalendar.deleteEvent(any(), any()) } just Runs
        coEvery { mockCalendar.createWateringEvent(any(), any()) } returns Pair("new_evt", "shared_cal")
        coEvery { mockDao.update(any()) } just Runs

        repository.updateInterval(plant, 7)

        // Exactly one delete — the pending event; past DONE events are untouched
        coVerify(exactly = 1) { mockCalendar.deleteEvent("pending_evt", "shared_cal") }
    }

    @Test
    fun `updateInterval falls back to primary when pendingEventCalendarId is null`() = runTest {
        val plant = plant(pendingEventId = "evt", pendingEventCalendarId = null)
        coEvery { mockCalendar.deleteEvent(any(), any()) } just Runs
        coEvery { mockCalendar.createWateringEvent(any(), any()) } returns Pair("new_evt", "primary")
        coEvery { mockDao.update(any()) } just Runs

        repository.updateInterval(plant, 5)

        coVerify { mockCalendar.deleteEvent("evt", "primary") }
    }

    @Test
    fun `updateInterval creates a new event from today plus the new interval`() = runTest {
        val plant = plant(pendingEventId = "old_evt", pendingEventCalendarId = "cal_a")
        coEvery { mockCalendar.deleteEvent(any(), any()) } just Runs
        coEvery { mockCalendar.createWateringEvent(any(), any()) } returns Pair("new_evt", "cal_a")
        coEvery { mockDao.update(any()) } just Runs

        repository.updateInterval(plant, 10)

        val expectedDate = LocalDate.now().plusDays(10)
        coVerify { mockCalendar.createWateringEvent("Basil", expectedDate) }
    }

    @Test
    fun `updateInterval saves the new interval and both event IDs to the database`() = runTest {
        val plant = plant(pendingEventId = "old_evt", pendingEventCalendarId = "cal_a")
        coEvery { mockCalendar.deleteEvent(any(), any()) } just Runs
        coEvery { mockCalendar.createWateringEvent(any(), any()) } returns Pair("new_evt", "new_cal")
        coEvery { mockDao.update(any()) } just Runs

        repository.updateInterval(plant, 10)

        val expectedMillis = LocalDate.now().plusDays(10).toEpochDay() * 86_400_000L
        coVerify {
            mockDao.update(match {
                it.intervalDays == 10 &&
                it.nextWateringMillis == expectedMillis &&
                it.pendingEventId == "new_evt" &&
                it.pendingEventCalendarId == "new_cal"
            })
        }
    }

    @Test
    fun `updateInterval gracefully handles a plant with no pending event`() = runTest {
        val plant = plant(pendingEventId = null, pendingEventCalendarId = null)
        coEvery { mockCalendar.createWateringEvent(any(), any()) } returns Pair("new_evt", "cal_a")
        coEvery { mockDao.update(any()) } just Runs

        repository.updateInterval(plant, 5)

        coVerify(exactly = 0) { mockCalendar.deleteEvent(any(), any()) }
        coVerify { mockCalendar.createWateringEvent("Basil", any()) }
    }

    // ── deletePlant ───────────────────────────────────────────────────────────

    @Test
    fun `deletePlant removes the pending event from its original calendar`() = runTest {
        val plant = plant(pendingEventId = "orchid_evt", pendingEventCalendarId = "shared_cal")
        coEvery { mockCalendar.deleteEvent(any(), any()) } just Runs
        coEvery { mockDao.delete(any()) } just Runs

        repository.deletePlant(plant)

        coVerify { mockCalendar.deleteEvent("orchid_evt", "shared_cal") }
    }

    @Test
    fun `deletePlant removes the plant from the database`() = runTest {
        val plant = plant(pendingEventId = "orchid_evt", pendingEventCalendarId = "cal_a")
        coEvery { mockCalendar.deleteEvent(any(), any()) } just Runs
        coEvery { mockDao.delete(any()) } just Runs

        repository.deletePlant(plant)

        coVerify { mockDao.delete(plant) }
    }

    @Test
    fun `deletePlant skips calendar delete when plant has no pending event`() = runTest {
        val plant = plant(pendingEventId = null, pendingEventCalendarId = null)
        coEvery { mockDao.delete(any()) } just Runs

        repository.deletePlant(plant)

        coVerify(exactly = 0) { mockCalendar.deleteEvent(any(), any()) }
    }

    // ── logPastWatering ───────────────────────────────────────────────────────

    @Test
    fun `logPastWatering always creates a done calendar event on the given date`() = runTest {
        val plant = plant(pendingEventId = "evt", pendingEventCalendarId = "cal_a")
        val pastDate = LocalDate.now().minusDays(2)
        coEvery { mockCalendar.createDoneEvent(any(), any(), any()) } just Runs
        coEvery { mockCalendar.deleteEvent(any(), any()) } just Runs
        coEvery { mockCalendar.createWateringEvent(any(), any()) } returns Pair("new_evt", "cal_a")
        coEvery { mockDao.update(any()) } just Runs

        repository.logPastWatering(plant, pastDate, "Alice")

        coVerify { mockCalendar.createDoneEvent("Basil", "Alice", pastDate) }
    }

    @Test
    fun `logPastWatering reschedules when date is more recent than lastWatered`() = runTest {
        val plant = plant(pendingEventId = "evt", pendingEventCalendarId = "cal_a")
        val pastDate = LocalDate.now().minusDays(1)
        coEvery { mockCalendar.createDoneEvent(any(), any(), any()) } just Runs
        coEvery { mockCalendar.deleteEvent(any(), any()) } just Runs
        coEvery { mockCalendar.createWateringEvent(any(), any()) } returns Pair("new_evt", "cal_a")
        coEvery { mockDao.update(any()) } just Runs

        repository.logPastWatering(plant, pastDate, "Alice")

        // Deletes old pending, creates new one from pastDate + interval
        coVerify { mockCalendar.deleteEvent("evt", "cal_a") }
        coVerify { mockCalendar.createWateringEvent("Basil", pastDate.plusDays(3)) }
        coVerify { mockDao.update(match { it.lastWateredMillis == pastDate.toEpochDay() * 86_400_000L }) }
    }

    @Test
    fun `logPastWatering reschedules when plant was never watered`() = runTest {
        val plant = plant(pendingEventId = "evt", pendingEventCalendarId = "cal_a")
        val pastDate = LocalDate.now().minusDays(3)
        coEvery { mockCalendar.createDoneEvent(any(), any(), any()) } just Runs
        coEvery { mockCalendar.deleteEvent(any(), any()) } just Runs
        coEvery { mockCalendar.createWateringEvent(any(), any()) } returns Pair("new_evt", "cal_a")
        coEvery { mockDao.update(any()) } just Runs

        repository.logPastWatering(plant, pastDate, "Bob")

        coVerify { mockCalendar.createWateringEvent("Basil", pastDate.plusDays(3)) }
    }

    @Test
    fun `logPastWatering does not reschedule when date is older than last watering`() = runTest {
        val recentWateringMillis = LocalDate.now().minusDays(1).toEpochDay() * 86_400_000L
        val plant = plant(pendingEventId = "evt", pendingEventCalendarId = "cal_a")
            .copy(lastWateredMillis = recentWateringMillis)
        val olderDate = LocalDate.now().minusDays(5)
        coEvery { mockCalendar.createDoneEvent(any(), any(), any()) } just Runs

        repository.logPastWatering(plant, olderDate, "Alice")

        // Done event is created but schedule is not touched
        coVerify { mockCalendar.createDoneEvent("Basil", "Alice", olderDate) }
        coVerify(exactly = 0) { mockCalendar.deleteEvent(any(), any()) }
        coVerify(exactly = 0) { mockCalendar.createWateringEvent(any(), any()) }
        coVerify(exactly = 0) { mockDao.update(any()) }
    }

    @Test
    fun `logPastWatering falls back to primary when pendingEventCalendarId is null`() = runTest {
        val plant = plant(pendingEventId = "evt", pendingEventCalendarId = null)
        val pastDate = LocalDate.now().minusDays(2)
        coEvery { mockCalendar.createDoneEvent(any(), any(), any()) } just Runs
        coEvery { mockCalendar.deleteEvent(any(), any()) } just Runs
        coEvery { mockCalendar.createWateringEvent(any(), any()) } returns Pair("new_evt", "primary")
        coEvery { mockDao.update(any()) } just Runs

        repository.logPastWatering(plant, pastDate, "Alice")

        coVerify { mockCalendar.deleteEvent("evt", "primary") }
    }

    @Test
    fun `logPastWatering skips delete when plant has no pending event`() = runTest {
        val plant = plant(pendingEventId = null, pendingEventCalendarId = null)
        val pastDate = LocalDate.now().minusDays(2)
        coEvery { mockCalendar.createDoneEvent(any(), any(), any()) } just Runs
        coEvery { mockCalendar.createWateringEvent(any(), any()) } returns Pair("new_evt", "primary")
        coEvery { mockDao.update(any()) } just Runs

        repository.logPastWatering(plant, pastDate, "Alice")

        coVerify(exactly = 0) { mockCalendar.deleteEvent(any(), any()) }
        coVerify { mockCalendar.createWateringEvent("Basil", pastDate.plusDays(3)) }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun plant(
        intervalDays: Int = 3,
        pendingEventId: String? = "evt",
        pendingEventCalendarId: String? = "cal_a",
    ) = Plant(
        id = 1L,
        name = "Basil",
        intervalDays = intervalDays,
        lastWateredMillis = null,
        nextWateringMillis = System.currentTimeMillis(),
        pendingEventId = pendingEventId,
        pendingEventCalendarId = pendingEventCalendarId,
    )
}
