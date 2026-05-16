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
import com.cytoplasmecode.plantwatering.databinding.ActivityMainBinding
import com.cytoplasmecode.plantwatering.notifications.WateringReminderWorker
import com.cytoplasmecode.plantwatering.ui.AddPlantDialog
import com.cytoplasmecode.plantwatering.ui.EditIntervalDialog
import com.cytoplasmecode.plantwatering.ui.PlantAdapter
import com.cytoplasmecode.plantwatering.ui.PlantsViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var authManager: GoogleAuthManager
    private val viewModel: PlantsViewModel by viewModels { PlantsViewModel.Factory(application) }
    private lateinit var adapter: PlantAdapter

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        authManager.handleSignInResult(result.data) { success ->
            if (success) {
                showPlantList()
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
        createNotificationChannel()
        requestNotificationPermissionIfNeeded()

        adapter = PlantAdapter(
            onWaterClick = { plant ->
                viewModel.waterPlant(plant)
                Toast.makeText(this, "${plant.name} watered! Calendar updated.", Toast.LENGTH_SHORT).show()
            },
            onEditClick = { plant ->
                EditIntervalDialog(this, plant) { newInterval ->
                    viewModel.updatePlantInterval(plant, newInterval)
                }.show()
            },
            onDeleteClick = { plant -> viewModel.deletePlant(plant) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        viewModel.plants.observe(this) { plants ->
            adapter.submitList(plants)
            binding.emptyView.visibility = if (plants.isEmpty()) View.VISIBLE else View.GONE
        }

        binding.fab.setOnClickListener {
            AddPlantDialog(this) { name, intervalDays ->
                viewModel.addPlant(name, intervalDays)
            }.show()
        }

        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account != null && authManager.hasCalendarPermission(account)) {
            showPlantList()
            scheduleReminderWork()
        } else {
            showSignIn()
        }
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
        val account = GoogleSignIn.getLastSignedInAccount(this) ?: return
        val displayName = account.displayName ?: account.email ?: "Someone"
        account.account?.let { viewModel.setAccount(it, displayName) }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_sign_out) {
            authManager.signOut {
                viewModel.clearAccount()
                showSignIn()
            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Watering Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
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
            request
        )
    }

    companion object {
        const val CHANNEL_ID = "watering_reminders"
    }
}
