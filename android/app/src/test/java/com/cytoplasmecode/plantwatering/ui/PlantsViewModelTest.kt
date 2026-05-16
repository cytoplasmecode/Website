package com.cytoplasmecode.plantwatering.ui

import android.accounts.Account
import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import androidx.test.core.app.ApplicationProvider
import com.cytoplasmecode.plantwatering.calendar.CalendarManager
import com.cytoplasmecode.plantwatering.data.Plant
import com.cytoplasmecode.plantwatering.data.PlantRepository
import com.cytoplasmecode.plantwatering.util.MainDispatcherRule
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlantsViewModelTest {

    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val mockCalendar: CalendarManager = mockk(relaxed = true)
    private val mockRepository: PlantRepository = mockk(relaxed = true)

    private lateinit var viewModel: PlantsViewModel

    @Before
    fun setUp() {
        every { mockRepository.plants } returns MutableLiveData(emptyList())
        val app = ApplicationProvider.getApplicationContext<Application>()
        viewModel = PlantsViewModel(app, mockCalendar, mockRepository)
    }

    // ── waterPlant + userName ─────────────────────────────────────────────────

    @Test
    fun `waterPlant passes the stored display name to the repository`() = runTest {
        val account = mockk<Account>()
        viewModel.setAccount(account, "Alice")

        viewModel.waterPlant(plant())
        advanceUntilIdle()

        coVerify { mockRepository.waterPlant(any(), "Alice") }
    }

    @Test
    fun `waterPlant defaults to Unknown when setAccount has not been called`() = runTest {
        viewModel.waterPlant(plant())
        advanceUntilIdle()

        coVerify { mockRepository.waterPlant(any(), "Unknown") }
    }

    @Test
    fun `waterPlant uses the most recently set display name`() = runTest {
        val account = mockk<Account>()
        viewModel.setAccount(account, "Alice")
        viewModel.setAccount(account, "Bob")

        viewModel.waterPlant(plant())
        advanceUntilIdle()

        coVerify { mockRepository.waterPlant(any(), "Bob") }
    }

    // ── addPlant ──────────────────────────────────────────────────────────────

    @Test
    fun `addPlant delegates to repository with correct arguments`() = runTest {
        viewModel.addPlant("Monstera", 7)
        advanceUntilIdle()

        coVerify { mockRepository.addPlant("Monstera", 7) }
    }

    // ── updatePlantInterval ───────────────────────────────────────────────────

    @Test
    fun `updatePlantInterval delegates to repository`() = runTest {
        val p = plant()
        viewModel.updatePlantInterval(p, 14)
        advanceUntilIdle()

        coVerify { mockRepository.updateInterval(p, 14) }
    }

    // ── deletePlant ───────────────────────────────────────────────────────────

    @Test
    fun `deletePlant delegates to repository`() = runTest {
        val p = plant()
        viewModel.deletePlant(p)
        advanceUntilIdle()

        coVerify { mockRepository.deletePlant(p) }
    }

    // ── setAccount ────────────────────────────────────────────────────────────

    @Test
    fun `setAccount forwards the account to the calendar manager`() = runTest {
        val account = mockk<Account>()
        viewModel.setAccount(account, "Alice")

        coVerify { mockCalendar.setAccount(account) }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun plant() = Plant(
        id = 1L,
        name = "Basil",
        intervalDays = 3,
        lastWateredMillis = null,
        nextWateringMillis = System.currentTimeMillis(),
        pendingEventId = "evt",
    )
}
