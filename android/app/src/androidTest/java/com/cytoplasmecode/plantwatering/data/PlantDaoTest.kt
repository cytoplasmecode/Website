package com.cytoplasmecode.plantwatering.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cytoplasmecode.plantwatering.util.getOrAwaitValue
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlantDaoTest {

    private lateinit var database: PlantDatabase
    private lateinit var dao: PlantDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, PlantDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.plantDao()
    }

    @After
    fun tearDown() = database.close()

    // ── insert + getAllPlants ──────────────────────────────────────────────────

    @Test
    fun insertAndGetSinglePlant() = runTest {
        dao.insert(plant("Basil", 3, nextMillis = dayMillis(3)))

        val all = dao.getAllPlants().getOrAwaitValue()
        assertEquals(1, all.size)
        assertEquals("Basil", all[0].name)
        assertEquals(3, all[0].intervalDays)
        assertNull(all[0].lastWateredMillis)
    }

    @Test
    fun insertMultiplePlantsAndGetAll() = runTest {
        dao.insert(plant("Basil", 3, nextMillis = dayMillis(3)))
        dao.insert(plant("Cactus", 14, nextMillis = dayMillis(14)))
        dao.insert(plant("Orchid", 10, nextMillis = dayMillis(10)))

        val all = dao.getAllPlants().getOrAwaitValue()
        assertEquals(3, all.size)
    }

    @Test
    fun insertReturnsGeneratedId() = runTest {
        val id = dao.insert(plant("Basil", 3, nextMillis = dayMillis(3)))
        assertTrue(id > 0)
    }

    // ── ordering ─────────────────────────────────────────────────────────────

    @Test
    fun getAllPlantsOrderedByNextWateringDateAscending() = runTest {
        dao.insert(plant("Later",  10, nextMillis = dayMillis(10)))
        dao.insert(plant("Sooner",  3, nextMillis = dayMillis(3)))
        dao.insert(plant("Today",   0, nextMillis = dayMillis(0)))

        val all = dao.getAllPlants().getOrAwaitValue()
        assertEquals("Today",  all[0].name)
        assertEquals("Sooner", all[1].name)
        assertEquals("Later",  all[2].name)
    }

    // ── getDuePlants ──────────────────────────────────────────────────────────

    @Test
    fun getDuePlantsReturnsOverduePlant() = runTest {
        val now = System.currentTimeMillis()
        dao.insert(plant("Overdue", 3, nextMillis = now - 1_000))

        val due = dao.getDuePlants(now)
        assertEquals(1, due.size)
        assertEquals("Overdue", due[0].name)
    }

    @Test
    fun getDuePlantsExcludesUpcomingPlant() = runTest {
        val now = System.currentTimeMillis()
        dao.insert(plant("Upcoming", 5, nextMillis = now + 86_400_000))

        val due = dao.getDuePlants(now)
        assertTrue(due.isEmpty())
    }

    @Test
    fun getDuePlantsIncludesPlantDueExactlyNow() = runTest {
        val now = System.currentTimeMillis()
        dao.insert(plant("DueNow", 1, nextMillis = now))

        val due = dao.getDuePlants(now)
        assertEquals(1, due.size)
    }

    @Test
    fun getDuePlantsReturnsMultipleOverduePlants() = runTest {
        val now = System.currentTimeMillis()
        dao.insert(plant("A", 1, nextMillis = now - 2_000))
        dao.insert(plant("B", 2, nextMillis = now - 1_000))
        dao.insert(plant("C", 5, nextMillis = now + 86_400_000))

        val due = dao.getDuePlants(now)
        assertEquals(2, due.size)
        assertTrue(due.map { it.name }.containsAll(listOf("A", "B")))
    }

    @Test
    fun getDuePlantsReturnsEmptyListWhenNoneDue() = runTest {
        val now = System.currentTimeMillis()
        dao.insert(plant("Future", 7, nextMillis = now + 86_400_000 * 7))

        val due = dao.getDuePlants(now)
        assertTrue(due.isEmpty())
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    fun updatePersistsNewIntervalDays() = runTest {
        dao.insert(plant("Basil", 3, nextMillis = dayMillis(3)))
        val inserted = dao.getAllPlants().getOrAwaitValue().first()

        dao.update(inserted.copy(intervalDays = 7))

        val updated = dao.getAllPlants().getOrAwaitValue().first()
        assertEquals(7, updated.intervalDays)
    }

    @Test
    fun updatePersistsLastWateredMillis() = runTest {
        dao.insert(plant("Basil", 3, nextMillis = dayMillis(3)))
        val inserted = dao.getAllPlants().getOrAwaitValue().first()
        val now = System.currentTimeMillis()

        dao.update(inserted.copy(lastWateredMillis = now))

        val updated = dao.getAllPlants().getOrAwaitValue().first()
        assertNotNull(updated.lastWateredMillis)
        assertEquals(now, updated.lastWateredMillis)
    }

    @Test
    fun updatePersistsNewPendingEventId() = runTest {
        dao.insert(plant("Basil", 3, nextMillis = dayMillis(3), eventId = "old"))
        val inserted = dao.getAllPlants().getOrAwaitValue().first()

        dao.update(inserted.copy(pendingEventId = "new_event_id"))

        val updated = dao.getAllPlants().getOrAwaitValue().first()
        assertEquals("new_event_id", updated.pendingEventId)
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    fun deleteRemovesPlantFromDatabase() = runTest {
        dao.insert(plant("Basil", 3, nextMillis = dayMillis(3)))
        val inserted = dao.getAllPlants().getOrAwaitValue().first()

        dao.delete(inserted)

        val all = dao.getAllPlants().getOrAwaitValue()
        assertTrue(all.isEmpty())
    }

    @Test
    fun deleteRemovesOnlyTheTargetPlant() = runTest {
        dao.insert(plant("Basil",   3, nextMillis = dayMillis(3)))
        dao.insert(plant("Orchid", 10, nextMillis = dayMillis(10)))
        val plants = dao.getAllPlants().getOrAwaitValue()
        val basil = plants.first { it.name == "Basil" }

        dao.delete(basil)

        val remaining = dao.getAllPlants().getOrAwaitValue()
        assertEquals(1, remaining.size)
        assertEquals("Orchid", remaining[0].name)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun plant(
        name: String,
        intervalDays: Int,
        nextMillis: Long,
        eventId: String? = null,
    ) = Plant(
        name = name,
        intervalDays = intervalDays,
        lastWateredMillis = null,
        nextWateringMillis = nextMillis,
        pendingEventId = eventId,
    )

    private fun dayMillis(daysFromNow: Long) =
        System.currentTimeMillis() + daysFromNow * 86_400_000L
}
