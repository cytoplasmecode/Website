package com.cytoplasmecode.plantwatering.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "plants")
data class Plant(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val intervalDays: Int,
    val lastWateredMillis: Long?,
    val nextWateringMillis: Long,
    val pendingEventId: String?,
    // Stores which calendar holds the pending event so markEventDone / deleteEvent
    // always target the correct calendar, even if the user later switches calendars.
    // Falls back to "primary" for plants created before this field was introduced.
    val pendingEventCalendarId: String?,
)
