package com.cytoplasmecode.plantwatering.data

import androidx.lifecycle.LiveData
import com.cytoplasmecode.plantwatering.calendar.CalendarManager
import java.time.LocalDate

class PlantRepository(
    private val dao: PlantDao,
    private val calendarManager: CalendarManager,
) {
    val plants: LiveData<List<Plant>> = dao.getAllPlants()

    suspend fun addPlant(name: String, intervalDays: Int) {
        val nextWatering = LocalDate.now().plusDays(intervalDays.toLong())
        val nextMillis = nextWatering.toEpochDay() * 86_400_000L
        val result = calendarManager.createWateringEvent(name, nextWatering)
        dao.insert(
            Plant(
                name = name,
                intervalDays = intervalDays,
                lastWateredMillis = null,
                nextWateringMillis = nextMillis,
                pendingEventId = result?.first,
                pendingEventCalendarId = result?.second,
            )
        )
    }

    suspend fun waterPlant(plant: Plant, userName: String) {
        val nowMillis = System.currentTimeMillis()
        val nextWatering = LocalDate.now().plusDays(plant.intervalDays.toLong())
        val nextMillis = nextWatering.toEpochDay() * 86_400_000L

        if (plant.pendingEventId != null) {
            val calId = plant.pendingEventCalendarId ?: "primary"
            calendarManager.markEventDone(plant.pendingEventId, calId, plant.name, userName)
        }
        val result = calendarManager.createWateringEvent(plant.name, nextWatering)

        dao.update(
            plant.copy(
                lastWateredMillis = nowMillis,
                nextWateringMillis = nextMillis,
                pendingEventId = result?.first,
                pendingEventCalendarId = result?.second,
            )
        )
    }

    // Deletes only the pending future event and reschedules from today.
    // Past "DONE by..." events on the calendar are left untouched.
    suspend fun updateInterval(plant: Plant, newIntervalDays: Int) {
        if (plant.pendingEventId != null) {
            val calId = plant.pendingEventCalendarId ?: "primary"
            calendarManager.deleteEvent(plant.pendingEventId, calId)
        }
        val nextWatering = LocalDate.now().plusDays(newIntervalDays.toLong())
        val nextMillis = nextWatering.toEpochDay() * 86_400_000L
        val result = calendarManager.createWateringEvent(plant.name, nextWatering)

        dao.update(
            plant.copy(
                intervalDays = newIntervalDays,
                nextWateringMillis = nextMillis,
                pendingEventId = result?.first,
                pendingEventCalendarId = result?.second,
            )
        )
    }

    suspend fun deletePlant(plant: Plant) {
        if (plant.pendingEventId != null) {
            val calId = plant.pendingEventCalendarId ?: "primary"
            calendarManager.deleteEvent(plant.pendingEventId, calId)
        }
        dao.delete(plant)
    }

    /**
     * Records a watering that happened on a past [date]:
     * - Always creates a "DONE by $userName - $plantName" calendar event on that date.
     * - If [date] is more recent than the last recorded watering (or never watered),
     *   also deletes the current pending event and reschedules from [date] + interval.
     */
    suspend fun logPastWatering(plant: Plant, date: LocalDate, userName: String) {
        calendarManager.createDoneEvent(plant.name, userName, date)

        val dateMillis = date.toEpochDay() * 86_400_000L
        val isMoreRecent = plant.lastWateredMillis == null || dateMillis > plant.lastWateredMillis
        if (isMoreRecent) {
            if (plant.pendingEventId != null) {
                val calId = plant.pendingEventCalendarId ?: "primary"
                calendarManager.deleteEvent(plant.pendingEventId, calId)
            }
            val nextWatering = date.plusDays(plant.intervalDays.toLong())
            val nextMillis = nextWatering.toEpochDay() * 86_400_000L
            val result = calendarManager.createWateringEvent(plant.name, nextWatering)
            dao.update(
                plant.copy(
                    lastWateredMillis = dateMillis,
                    nextWateringMillis = nextMillis,
                    pendingEventId = result?.first,
                    pendingEventCalendarId = result?.second,
                )
            )
        }
    }

    suspend fun getDuePlants(): List<Plant> = dao.getDuePlants(System.currentTimeMillis())
}
