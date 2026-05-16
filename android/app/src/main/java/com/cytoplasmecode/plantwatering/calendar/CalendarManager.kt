package com.cytoplasmecode.plantwatering.calendar

import android.accounts.Account
import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

class CalendarManager(private val context: Context) {

    private var account: Account? = null
    private var selectedCalendarId: String = "primary"

    fun setAccount(account: Account) {
        this.account = account
    }

    fun setCalendarId(id: String) {
        selectedCalendarId = id
    }

    fun getCalendarId(): String = selectedCalendarId

    private fun buildService(account: Account): Calendar {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf("https://www.googleapis.com/auth/calendar")
        ).apply { selectedAccount = account }

        return Calendar.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("Plant Watering").build()
    }

    /** Returns all calendars on which the signed-in user can create events. */
    suspend fun fetchCalendars(): List<CalendarInfo> {
        val acc = account ?: return emptyList()
        return withContext(Dispatchers.IO) {
            runCatching {
                buildService(acc)
                    .calendarList()
                    .list()
                    .setMinAccessRole("writer")
                    .execute()
                    .items
                    .orEmpty()
                    .map { entry ->
                        CalendarInfo(
                            id = entry.id,
                            name = entry.summary ?: entry.id,
                            colorHex = entry.backgroundColor,
                            accessRole = entry.accessRole ?: "reader",
                        )
                    }
                    .filter { it.isWritable }
            }.getOrDefault(emptyList())
        }
    }

    /**
     * Creates a "Water $plantName" all-day event on [date].
     * Returns (eventId, calendarId) so the caller can store both, or null on failure.
     */
    suspend fun createWateringEvent(plantName: String, date: LocalDate): Pair<String, String>? {
        val acc = account ?: return null
        val calId = selectedCalendarId
        return withContext(Dispatchers.IO) {
            runCatching {
                val dateStr = date.toString()
                val event = Event().apply {
                    summary = pendingEventTitle(plantName)
                    start = EventDateTime().apply { this.date = DateTime(dateStr) }
                    end = EventDateTime().apply { this.date = DateTime(dateStr) }
                }
                val eventId = buildService(acc).events().insert(calId, event).execute().id
                Pair(eventId, calId)
            }.getOrNull()
        }
    }

    /**
     * Renames [eventId] in [calendarId] to "DONE by $userName - $plantName".
     * Uses the event's own calendarId so the correct calendar is always targeted,
     * even if the user has since switched to a different calendar.
     */
    suspend fun markEventDone(
        eventId: String,
        calendarId: String,
        plantName: String,
        userName: String,
    ) {
        val acc = account ?: return
        withContext(Dispatchers.IO) {
            runCatching {
                val service = buildService(acc)
                val event = service.events().get(calendarId, eventId).execute()
                event.summary = doneEventTitle(userName, plantName)
                service.events().update(calendarId, eventId, event).execute()
            }
        }
    }

    suspend fun deleteEvent(eventId: String, calendarId: String) {
        val acc = account ?: return
        withContext(Dispatchers.IO) {
            runCatching {
                buildService(acc).events().delete(calendarId, eventId).execute()
            }
        }
    }
}
