package com.cytoplasmecode.plantwatering.calendar

import android.content.Context

class CalendarPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var selectedCalendarId: String?
        get() = prefs.getString(KEY_ID, null)
        set(value) = prefs.edit().putString(KEY_ID, value).apply()

    var selectedCalendarName: String?
        get() = prefs.getString(KEY_NAME, null)
        set(value) = prefs.edit().putString(KEY_NAME, value).apply()

    fun select(info: CalendarInfo) {
        selectedCalendarId = info.id
        selectedCalendarName = info.name
    }

    fun clear() = prefs.edit().clear().apply()

    companion object {
        private const val PREFS_NAME = "plant_watering_prefs"
        private const val KEY_ID = "selected_calendar_id"
        private const val KEY_NAME = "selected_calendar_name"
    }
}
