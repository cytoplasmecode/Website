package com.cytoplasmecode.plantwatering.data

import androidx.lifecycle.LiveData
import com.cytoplasmecode.plantwatering.calendar.CalendarManager
import java.time.LocalDate

class PlantRepository(
    private val dao: PlantDao,
    private val calendarManager: CalendarManager
) {
    val plants: LiveData<List<Plant>> = dao.getAllPlants()

    suspend fun addPlant(name: String, intervalDays: Int) {
        val nextWatering = LocalDate.now().plusDays(intervalDays.toLong())
        val nextMillis = nextWatering.toEpochDay() * 86_400_000L
        val eventId = calendarManager.createWateringEvent(name, nextWatering)
        dao.insert(
            Plant(
                name = name,
                intervalDays = intervalDays,
                lastWateredMillis = null,
                nextWateringMillis = nextMillis,
                pendingEventId = eventId
            )
        )
    }

    suspend fun waterPlant(plant: Plant) {
        val nowMillis = System.currentTimeMillis()
        val nextWatering = LocalDate.now().plusDays(plant.intervalDays.toLong())
        val nextMillis = nextWatering.toEpochDay() * 86_400_000L

        plant.pendingEventId?.let { calendarManager.markEventDone(it, plant.name) }
        val nextEventId = calendarManager.createWateringEvent(plant.name, nextWatering)

        dao.update(
            plant.copy(
                lastWateredMillis = nowMillis,
                nextWateringMillis = nextMillis,
                pendingEventId = nextEventId
            )
        )
    }

    suspend fun deletePlant(plant: Plant) {
        plant.pendingEventId?.let { calendarManager.deleteEvent(it) }
        dao.delete(plant)
    }

    suspend fun getDuePlants(): List<Plant> = dao.getDuePlants(System.currentTimeMillis())
}
