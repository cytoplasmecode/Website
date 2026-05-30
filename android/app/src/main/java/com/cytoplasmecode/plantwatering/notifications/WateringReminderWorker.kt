package com.cytoplasmecode.plantwatering.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cytoplasmecode.plantwatering.MainActivity
import com.cytoplasmecode.plantwatering.R
import com.cytoplasmecode.plantwatering.data.Plant
import com.cytoplasmecode.plantwatering.data.PlantDatabase

class WateringReminderWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val dao = PlantDatabase.getInstance(context).plantDao()
        val duePlants = dao.getDuePlants(System.currentTimeMillis())
        duePlants.forEach { sendNotification(it) }
        return Result.success()
    }

    private fun sendNotification(plant: Plant) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, plant.id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, MainActivity.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_plant)
            .setContentTitle("Time to water ${plant.name}!")
            .setContentText("Due every ${plant.intervalDays} day(s). Open the app to mark as watered.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(plant.id.toInt(), notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS permission not granted; silently skip
        }
    }
}
