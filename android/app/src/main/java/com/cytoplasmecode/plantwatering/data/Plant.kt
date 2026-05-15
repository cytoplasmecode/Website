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
    val pendingEventId: String?
)
