package com.cytoplasmecode.plantwatering.ui

import android.accounts.Account
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cytoplasmecode.plantwatering.calendar.CalendarManager
import com.cytoplasmecode.plantwatering.data.Plant
import com.cytoplasmecode.plantwatering.data.PlantDatabase
import com.cytoplasmecode.plantwatering.data.PlantRepository
import kotlinx.coroutines.launch

class PlantsViewModel(application: Application) : AndroidViewModel(application) {

    private val calendarManager = CalendarManager(application)
    private val repository = PlantRepository(
        PlantDatabase.getInstance(application).plantDao(),
        calendarManager
    )

    val plants: LiveData<List<Plant>> = repository.plants

    fun setAccount(account: Account) = calendarManager.setAccount(account)

    fun clearAccount() = calendarManager.setAccount(
        Account("", "com.google") // clears the stored account
    )

    fun addPlant(name: String, intervalDays: Int) {
        viewModelScope.launch { repository.addPlant(name, intervalDays) }
    }

    fun waterPlant(plant: Plant) {
        viewModelScope.launch { repository.waterPlant(plant) }
    }

    fun deletePlant(plant: Plant) {
        viewModelScope.launch { repository.deletePlant(plant) }
    }

    class Factory(private val app: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PlantsViewModel(app) as T
    }
}
