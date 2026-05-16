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

    fun setAccount(account: Account) {
        this.account = account
    }

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

    suspend fun createWateringEvent(plantName: String, date: LocalDate): String? {
        val acc = account ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val service = buildService(acc)
                val dateStr = date.toString()
                val event = Event().apply {
                    summary = pendingEventTitle(plantName)
                    start = EventDateTime().apply { this.date = DateTime(dateStr) }
                    end = EventDateTime().apply { this.date = DateTime(dateStr) }
                }
                service.events().insert("primary", event).execute().id
            }.getOrNull()
        }
    }

    suspend fun markEventDone(eventId: String, plantName: String, userName: String) {
        val acc = account ?: return
        withContext(Dispatchers.IO) {
            runCatching {
                val service = buildService(acc)
                val event = service.events().get("primary", eventId).execute()
                event.summary = doneEventTitle(userName, plantName)
                service.events().update("primary", eventId, event).execute()
            }
        }
    }

    suspend fun deleteEvent(eventId: String) {
        val acc = account ?: return
        withContext(Dispatchers.IO) {
            runCatching {
                val service = buildService(acc)
                service.events().delete("primary", eventId).execute()
            }
        }
    }
}
