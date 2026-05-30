package com.cytoplasmecode.plantwatering.calendar

import java.time.LocalDate

data class HistoryEvent(
    val eventId: String,
    val calendarId: String,
    val date: LocalDate,
    val userName: String,
    val plantName: String,
)
