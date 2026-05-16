package com.cytoplasmecode.plantwatering.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.graphics.drawable.DrawableCompat
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
    private val onEditClick: (Plant) -> Unit,
    private val onDeleteClick: (Plant) -> Unit,
) : ListAdapter<Plant, PlantAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(private val binding: ItemPlantBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(plant: Plant) {
            binding.plantName.text = plant.name
            binding.intervalText.text = "Every ${plant.intervalDays} day(s)"

            val nextDate = Instant.ofEpochMilli(plant.nextWateringMillis)
                .atZone(ZoneId.systemDefault()).toLocalDate()
            val daysUntil = ChronoUnit.DAYS.between(LocalDate.now(), nextDate)

            val (label, tintColor) = when {
                daysUntil < 0 -> "⚠ Overdue ${-daysUntil}d" to Color.parseColor("#F87171")
                daysUntil == 0L -> "● Due today" to Color.parseColor("#FB923C")
                else -> "● In ${daysUntil}d" to Color.parseColor("#4ADE80")
            }
            binding.nextWateringText.text = label
            binding.nextWateringText.setTextColor(tintColor)
            binding.nextWateringText.background?.let {
                DrawableCompat.setTint(
                    DrawableCompat.wrap(it.mutate()),
                    (tintColor and 0x00FFFFFF) or 0x22000000
                )
            }

            binding.waterButton.setOnClickListener { onWaterClick(plant) }
            binding.editButton.setOnClickListener { onEditClick(plant) }
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
