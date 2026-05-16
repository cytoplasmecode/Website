package com.cytoplasmecode.plantwatering

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.cytoplasmecode.plantwatering.auth.GoogleAuthManager
import com.cytoplasmecode.plantwatering.calendar.CalendarInfo
import com.cytoplasmecode.plantwatering.calendar.CalendarPreferences
import com.cytoplasmecode.plantwatering.databinding.ActivityMainBinding
import com.cytoplasmecode.plantwatering.notifications.WateringReminderWorker
import com.cytoplasmecode.plantwatering.ui.AddPlantDialog
import com.cytoplasmecode.plantwatering.ui.CalendarPickerDialog
import com.cytoplasmecode.plantwatering.ui.EditIntervalDialog
import com.cytoplasmecode.plantwatering.ui.PlantAdapter
import com.cytoplasmecode.plantwatering.ui.PlantsViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var authManager: GoogleAuthManager
    private lateinit var calendarPrefs: CalendarPreferences
    private val viewModel: PlantsViewModel by viewModels { PlantsViewModel.Factory(application) }
    private lateinit var adapter: PlantAdapter

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        authManager.handleSignInResult(result.data) { success ->
            if (success) {
                onSignedIn()
                scheduleReminderWork()
            } else {
                Toast.makeText(this, "Sign-in failed or Calendar access denied", Toast.LENGTH_LONG).show()
            }
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* proceed regardless of outcome */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        authManager = GoogleAuthManager(this)
        calendarPrefs = CalendarPreferences(this)
        createNotificationChannel()
        requestNotificationPermissionIfNeeded()

        adapter = PlantAdapter(
            onWaterClick = { plant ->
                viewModel.waterPlant(plant)
                Toast.makeText(this, "${plant.name} watered!", Toast.LENGTH_SHORT).show()
            },
            onEditClick = { plant ->
                EditIntervalDialog(this, plant) { newInterval ->
                    viewModel.updatePlantInterval(plant, newInterval)
                }.show()
            },
            onDeleteClick = { plant -> viewModel.deletePlant(plant) },
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        viewModel.plants.observe(this) { plants ->
            adapter.submitList(plants)
            binding.emptyView.visibility = if (plants.isEmpty()) View.VISIBLE else View.GONE
        }

        // Show calendar picker once calendars are loaded
        viewModel.calendars.observe(this) { calendars ->
            if (calendars.isEmpty()) {
                Toast.makeText(this, "No writable calendars found", Toast.LENGTH_LONG).show()
                return@observe
            }
            val savedId = calendarPrefs.selectedCalendarId
            if (savedId != null && calendars.any { it.id == savedId }) {
                // Restore saved selection silently
                applyCalendar(calendars.first { it.id == savedId })
            } else if (calendars.size == 1) {
                // Auto-select the only option
                applyCalendar(calendars.first())
            } else {
                showCalendarPicker(calendars)
            }
        }

        binding.fab.setOnClickListener {
            AddPlantDialog(this) { name, intervalDays ->
                viewModel.addPlant(name, intervalDays)
            }.show()
        }

        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account != null && authManager.hasCalendarPermission(account)) {
            onSignedIn()
            scheduleReminderWork()
        } else {
            showSignIn()
        }
    }

    private fun onSignedIn() {
        val account = GoogleSignIn.getLastSignedInAccount(this) ?: return
        val displayName = account.displayName ?: account.email ?: "Someone"
        account.account?.let { viewModel.setAccount(it, displayName) }
        viewModel.loadCalendars()
        showPlantList()
    }

    private fun showSignIn() {
        binding.signInLayout.visibility = View.VISIBLE
        binding.contentLayout.visibility = View.GONE
        binding.fab.visibility = View.GONE
        binding.signInButton.setOnClickListener {
            signInLauncher.launch(authManager.getSignInIntent())
        }
    }

    private fun showPlantList() {
        binding.signInLayout.visibility = View.GONE
        binding.contentLayout.visibility = View.VISIBLE
        binding.fab.visibility = View.VISIBLE
        updateToolbarSubtitle()
    }

    private fun applyCalendar(info: CalendarInfo) {
        calendarPrefs.select(info)
        viewModel.setCalendarId(info.id)
        updateToolbarSubtitle()
    }

    private fun updateToolbarSubtitle() {
        supportActionBar?.subtitle = calendarPrefs.selectedCalendarName
    }

    private fun showCalendarPicker(calendars: List<CalendarInfo>) {
        CalendarPickerDialog(
            context = this,
            calendars = calendars,
            currentCalendarId = calendarPrefs.selectedCalendarId,
        ) { selected ->
            applyCalendar(selected)
            Toast.makeText(this, "Calendar: ${selected.name}", Toast.LENGTH_SHORT).show()
        }.show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_change_calendar -> {
                val calendars = viewModel.calendars.value ?: emptyList()
                if (calendars.isEmpty()) {
                    viewModel.loadCalendars()
                    Toast.makeText(this, "Loading calendars…", Toast.LENGTH_SHORT).show()
                } else {
                    showCalendarPicker(calendars)
                }
                true
            }
            R.id.action_sign_out -> {
                authManager.signOut {
                    viewModel.clearAccount()
                    calendarPrefs.clear()
                    showSignIn()
                    supportActionBar?.subtitle = null
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Watering Reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Daily reminders to water your plants" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun scheduleReminderWork() {
        val request = PeriodicWorkRequestBuilder<WateringReminderWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "watering_check",
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    companion object {
        const val CHANNEL_ID = "watering_reminders"
    }
}
