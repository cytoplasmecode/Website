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
        coEvery { mockCalendar.createWateringEvent(any(), any()) } returns "evt_1"
        coEvery { mockDao.insert(any()) } returns 1L

        repository.addPlant("Basil", 3)

        coVerify { mockCalendar.createWateringEvent("Basil", any()) }
    }

    @Test
    fun `addPlant schedules the event on today plus the interval`() = runTest {
        val expectedDate = LocalDate.now().plusDays(5)
        coEvery { mockCalendar.createWateringEvent(any(), any()) } returns "evt_1"
        coEvery { mockDao.insert(any()) } returns 1L

        repository.addPlant("Fern", 5)

        coVerify { mockCalendar.createWateringEvent("Fern", expectedDate) }
    }

    @Test
    fun `addPlant inserts a plant with null lastWateredMillis`() = runTest {
        coEvery { mockCalendar.createWateringEvent(any(), any()) } returns "evt_1"
        coEvery { mockDao.insert(any()) } returns 1L

        repository.addPlant("Cactus", 14)

        coVerify { mockDao.insert(match { it.lastWateredMillis == null }) }
    }

    @Test
    fun `addPlant stores the returned calendar event ID`() = runTest {
        coEvery { mockCalendar.createWateringEvent(any(), any()) } returns "cal_id_xyz"
        coEvery { mockDao.insert(any()) } returns 1L

        repository.addPlant("Orchid", 10)

        coVerify { mockDao.insert(match { it.pendingEventId == "cal_id_xyz" }) }
    }

    // ── waterPlant ────────────────────────────────────────────────────────────

    @Test
    fun `waterPlant marks the pending event done with user name and plant name`() = runTest {
        val plant = plant(pendingEventId = "old_evt")
        coEvery { mockCalendar.markEventDone(any(), any(), any()) } just Runs
        coEvery { mockCalendar.createWateringEvent(any(), any()) } returns "new_evt"
        coEvery { mockDao.update(any()) } just Runs

        repository.waterPlant(plant, "Alice")

        coVerify { mockCalendar.markEventDone("old_evt", "Basil", "Alice") }
    }

    @Test
    fun `waterPlant creates the next watering event immediately`() = runTest {
        val plant = plant(intervalDays = 3, pendingEventId = "old_evt")
        coEvery { mockCalendar.markEventDone(any(), any(), any()) } just Runs
        coEvery { mockCalendar.createWateringEvent(any(), any()) } returns "new_evt"
        coEvery { mockDao.update(any()) } just Runs

        repository.waterPlant(plant, "Alice")

        val expectedNext = LocalDate.now().plusDays(3)
        coVerify { mockCalendar.createWateringEvent("Basil", expectedNext) }
    }

    @Test
    fun `waterPlant updates the plant with new pending event ID and lastWateredMillis`() = runTest {
        val plant = plant(pendingEventId = "old_evt")
        coEvery { mockCalendar.markEventDone(any(), any(), any()) } just Runs
        coEvery { mockCalendar.createWateringEvent(any(), any()) } returns "next_evt"
        coEvery { mockDao.update(any()) } just Runs

        repository.waterPlant(plant, "Alice")

        coVerify {
            mockDao.update(match {
                it.pendingEventId == "next_evt" && it.lastWateredMillis != null
            })
        }
    }

    @Test
    fun `waterPlant skips markEventDone when plant has no pending event`() = runTest {
        val plant = plant(pendingEventId = null)
        coEvery { mockCalendar.createWateringEvent(any(), any()) } returns "next_evt"
        coEvery { mockDao.update(any()) } just Runs

        repository.waterPlant(plant, "Bob")

        coVerify(exactly = 0) { mockCalendar.markEventDone(any(), any(), any()) }
    }

    @Test
    fun `waterPlant forwards different user names correctly`() = runTest {
        val plant = plant(pendingEventId = "evt")
        coEvery { mockCalendar.markEventDone(any(), any(), any()) } just Runs
        coEvery { mockCalendar.createWateringEvent(any(), any()) } returns "next"
        coEvery { mockDao.update(any()) } just Runs

        repository.waterPlant(plant, "Bob")

        coVerify { mockCalendar.markEventDone(any(), any(), "Bob") }
    }

    // ── updateInterval ────────────────────────────────────────────────────────

    @Test
    fun `updateInterval deletes only the pending future event`() = runTest {
        val plant = plant(pendingEventId = "pending_evt")
        coEvery { mockCalendar.deleteEvent(any()) } just Runs
        coEvery { mockCalendar.createWateringEvent(any(), any()) } returns "new_evt"
        coEvery { mockDao.update(any()) } just Runs

        repository.updateInterval(plant, 7)

        // Exactly one delete — the pending event; past DONE events are untouched
        coVerify(exactly = 1) { mockCalendar.deleteEvent("pending_evt") }
    }

    @Test
    fun `updateInterval creates a new event from today plus the new interval`() = runTest {
        val plant = plant(intervalDays = 3, pendingEventId = "old_evt")
        coEvery { mockCalendar.deleteEvent(any()) } just Runs
        coEvery { mockCalendar.createWateringEvent(any(), any()) } returns "new_evt"
        coEvery { mockDao.update(any()) } just Runs

        repository.updateInterval(plant, 10)

        val expectedDate = LocalDate.now().plusDays(10)
        coVerify { mockCalendar.createWateringEvent("Basil", expectedDate) }
    }

    @Test
    fun `updateInterval saves the new interval and next watering date to the database`() = runTest {
        val plant = plant(intervalDays = 3, pendingEventId = "old_evt")
        coEvery { mockCalendar.deleteEvent(any()) } just Runs
        coEvery { mockCalendar.createWateringEvent(any(), any()) } returns "new_evt"
        coEvery { mockDao.update(any()) } just Runs

        repository.updateInterval(plant, 10)

        val expectedMillis = LocalDate.now().plusDays(10).toEpochDay() * 86_400_000L
        coVerify {
            mockDao.update(match {
                it.intervalDays == 10 && it.nextWateringMillis == expectedMillis
            })
        }
    }

    @Test
    fun `updateInterval gracefully handles a plant with no pending event`() = runTest {
        val plant = plant(pendingEventId = null)
        coEvery { mockCalendar.createWateringEvent(any(), any()) } returns "new_evt"
        coEvery { mockDao.update(any()) } just Runs

        repository.updateInterval(plant, 5)

        coVerify(exactly = 0) { mockCalendar.deleteEvent(any()) }
        coVerify { mockCalendar.createWateringEvent("Basil", any()) }
    }

    // ── deletePlant ───────────────────────────────────────────────────────────

    @Test
    fun `deletePlant removes the pending calendar event`() = runTest {
        val plant = plant(pendingEventId = "orchid_evt")
        coEvery { mockCalendar.deleteEvent(any()) } just Runs
        coEvery { mockDao.delete(any()) } just Runs

        repository.deletePlant(plant)

        coVerify { mockCalendar.deleteEvent("orchid_evt") }
    }

    @Test
    fun `deletePlant removes the plant from the database`() = runTest {
        val plant = plant(pendingEventId = "orchid_evt")
        coEvery { mockCalendar.deleteEvent(any()) } just Runs
        coEvery { mockDao.delete(any()) } just Runs

        repository.deletePlant(plant)

        coVerify { mockDao.delete(plant) }
    }

    @Test
    fun `deletePlant skips calendar delete when plant has no pending event`() = runTest {
        val plant = plant(pendingEventId = null)
        coEvery { mockDao.delete(any()) } just Runs

        repository.deletePlant(plant)

        coVerify(exactly = 0) { mockCalendar.deleteEvent(any()) }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun plant(
        intervalDays: Int = 3,
        pendingEventId: String? = "evt",
    ) = Plant(
        id = 1L,
        name = "Basil",
        intervalDays = intervalDays,
        lastWateredMillis = null,
        nextWateringMillis = System.currentTimeMillis(),
        pendingEventId = pendingEventId,
    )
}
