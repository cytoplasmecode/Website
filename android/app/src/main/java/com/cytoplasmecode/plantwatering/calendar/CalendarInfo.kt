package com.cytoplasmecode.plantwatering.calendar

/**
 * Represents one entry from the user's Google Calendar list.
 * Only calendars where [accessRole] is "owner" or "writer" are shown in the picker.
 */
data class CalendarInfo(
    val id: String,
    val name: String,
    val colorHex: String?,
    val accessRole: String,
) {
    val isWritable: Boolean
        get() = accessRole == "owner" || accessRole == "writer"
}
