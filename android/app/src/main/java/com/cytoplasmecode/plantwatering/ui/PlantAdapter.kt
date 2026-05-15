package com.cytoplasmecode.plantwatering.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cytoplasmecode.plantwatering.data.Plant
import com.cytoplasmecode.plantwatering.databinding.ItemPlantBinding
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class PlantAdapter(
    private val onWaterClick: (Plant) -> Unit,
    private val onDeleteClick: (Plant) -> Unit
) : ListAdapter<Plant, PlantAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(private val binding: ItemPlantBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(plant: Plant) {
            binding.plantName.text = plant.name
            binding.intervalText.text = "Every ${plant.intervalDays} day(s)"

            val nextDate = Instant.ofEpochMilli(plant.nextWateringMillis)
                .atZone(ZoneId.systemDefault()).toLocalDate()
            val daysUntil = ChronoUnit.DAYS.between(LocalDate.now(), nextDate)
            binding.nextWateringText.text = when {
                daysUntil < 0 -> "Overdue by ${-daysUntil} day(s) ⚠️"
                daysUntil == 0L -> "Due today!"
                else -> "Due in $daysUntil day(s)"
            }

            binding.waterButton.setOnClickListener { onWaterClick(plant) }
            binding.deleteButton.setOnClickListener { onDeleteClick(plant) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPlantBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Plant>() {
            override fun areItemsTheSame(a: Plant, b: Plant) = a.id == b.id
            override fun areContentsTheSame(a: Plant, b: Plant) = a == b
        }
    }
}
