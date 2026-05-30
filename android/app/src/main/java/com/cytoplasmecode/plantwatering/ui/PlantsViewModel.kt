package com.cytoplasmecode.plantwatering.ui

import android.accounts.Account
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cytoplasmecode.plantwatering.calendar.CalendarInfo
import com.cytoplasmecode.plantwatering.calendar.CalendarManager
import com.cytoplasmecode.plantwatering.data.Plant
import com.cytoplasmecode.plantwatering.calendar.HistoryEvent
import com.cytoplasmecode.plantwatering.data.PlantDatabase
import com.cytoplasmecode.plantwatering.data.PlantRepository
import kotlinx.coroutines.launch
import java.time.LocalDate

class PlantsViewModel(
    application: Application,
    private val calendarManager: CalendarManager,
    private val repository: PlantRepository,
) : AndroidViewModel(application) {

    val plants: LiveData<List<Plant>> = repository.plants

    private val _calendars = MutableLiveData<List<CalendarInfo>>()
    val calendars: LiveData<List<CalendarInfo>> = _calendars

    private var userName: String = "Unknown"

    fun setAccount(account: Account, displayName: String) {
        calendarManager.setAccount(account)
        userName = displayName
    }

    fun clearAccount() = calendarManager.setAccount(Account("", "com.google"))

    fun setCalendarId(id: String) = calendarManager.setCalendarId(id)

    fun getCalendarId(): String = calendarManager.getCalendarId()

    /** Fetches the user's writable calendars and posts them to [calendars]. */
    fun loadCalendars() {
        viewModelScope.launch {
            _calendars.value = calendarManager.fetchCalendars()
        }
    }

    fun addPlant(name: String, intervalDays: Int) {
        viewModelScope.launch { repository.addPlant(name, intervalDays) }
    }

    fun waterPlant(plant: Plant) {
        viewModelScope.launch { repository.waterPlant(plant, userName) }
    }

    fun updatePlantInterval(plant: Plant, newIntervalDays: Int) {
        viewModelScope.launch { repository.updateInterval(plant, newIntervalDays) }
    }

    fun deletePlant(plant: Plant) {
        viewModelScope.launch { repository.deletePlant(plant) }
    }

    fun logPastWatering(plant: Plant, date: LocalDate) {
        viewModelScope.launch { repository.logPastWatering(plant, date, userName) }
    }

    fun loadPlantHistory(plant: Plant, onResult: (List<HistoryEvent>) -> Unit) {
        viewModelScope.launch { onResult(repository.getPlantHistory(plant)) }
    }

    fun updateHistoryEventDate(plant: Plant, event: HistoryEvent, newDate: LocalDate, isLastWatering: Boolean) {
        viewModelScope.launch { repository.updateHistoryEventDate(plant, event, newDate, isLastWatering) }
    }

    fun updateNextWateringDate(plant: Plant, newDate: LocalDate) {
        viewModelScope.launch { repository.updateNextWateringDate(plant, newDate) }
    }

    fun correctLastWatering(plant: Plant, newDate: LocalDate) {
        viewModelScope.launch { repository.correctLastWatering(plant, newDate) }
    }

    class Factory(private val app: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val cm = CalendarManager(app)
            return PlantsViewModel(
                app,
                cm,
                PlantRepository(PlantDatabase.getInstance(app).plantDao(), cm),
            ) as T
        }
    }
}
