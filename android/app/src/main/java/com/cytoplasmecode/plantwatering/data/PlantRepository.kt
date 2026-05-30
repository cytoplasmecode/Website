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

    /** Returns this plant's watering history, sorted most-recent-first. */
    suspend fun getPlantHistory(plant: Plant): List<com.cytoplasmecode.plantwatering.calendar.HistoryEvent> =
        calendarManager.fetchPlantHistory(plant.name)

    /**
     * Reschedules a specific past history event to [newDate].
     * If [isLastWatering] the pending future event is also rebuilt from [newDate] + interval.
     */
    suspend fun updateHistoryEventDate(
        plant: Plant,
        event: com.cytoplasmecode.plantwatering.calendar.HistoryEvent,
        newDate: LocalDate,
        isLastWatering: Boolean,
    ) {
        calendarManager.rescheduleEvent(event.eventId, event.calendarId, newDate)
        if (isLastWatering) {
            if (plant.pendingEventId != null) {
                calendarManager.deleteEvent(plant.pendingEventId, plant.pendingEventCalendarId ?: "primary")
            }
            val nextWatering = newDate.plusDays(plant.intervalDays.toLong())
            val result = calendarManager.createWateringEvent(plant.name, nextWatering)
            dao.update(plant.copy(
                lastWateredMillis = newDate.toEpochDay() * 86_400_000L,
                nextWateringMillis = nextWatering.toEpochDay() * 86_400_000L,
                pendingEventId = result?.first,
                pendingEventCalendarId = result?.second,
            ))
        }
    }

    /**
     * Patches the pending event's date in-place and updates [Plant.nextWateringMillis].
     * Does not change [Plant.lastWateredMillis].
     */
    suspend fun updateNextWateringDate(plant: Plant, newDate: LocalDate) {
        if (plant.pendingEventId != null) {
            calendarManager.rescheduleEvent(
                plant.pendingEventId,
                plant.pendingEventCalendarId ?: "primary",
                newDate,
            )
        }
        dao.update(plant.copy(nextWateringMillis = newDate.toEpochDay() * 86_400_000L))
    }

    /**
     * Corrects the date of the last watering (editing it rather than adding a new one):
     * - Finds and reschedules the most recent DONE calendar event for this plant.
     * - Rebuilds the pending future event from [newDate] + interval.
     */
    suspend fun correctLastWatering(plant: Plant, newDate: LocalDate) {
        val history = calendarManager.fetchPlantHistory(plant.name)
        if (history.isNotEmpty()) {
            calendarManager.rescheduleEvent(history.first().eventId, history.first().calendarId, newDate)
        }
        if (plant.pendingEventId != null) {
            calendarManager.deleteEvent(plant.pendingEventId, plant.pendingEventCalendarId ?: "primary")
        }
        val nextWatering = newDate.plusDays(plant.intervalDays.toLong())
        val result = calendarManager.createWateringEvent(plant.name, nextWatering)
        dao.update(plant.copy(
            lastWateredMillis = newDate.toEpochDay() * 86_400_000L,
            nextWateringMillis = nextWatering.toEpochDay() * 86_400_000L,
            pendingEventId = result?.first,
            pendingEventCalendarId = result?.second,
        ))
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
