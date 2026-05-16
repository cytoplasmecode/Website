package com.cytoplasmecode.plantwatering.ui

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import com.cytoplasmecode.plantwatering.data.Plant
import com.cytoplasmecode.plantwatering.databinding.DialogEditIntervalBinding

class EditIntervalDialog(
    private val context: Context,
    private val plant: Plant,
    private val onSave: (newIntervalDays: Int) -> Unit
) {
    fun show() {
        val binding = DialogEditIntervalBinding.inflate(LayoutInflater.from(context))
        binding.intervalInput.setText(plant.intervalDays.toString())

        val dialog = AlertDialog.Builder(context)
            .setTitle("Edit "${plant.name}"")
            .setView(binding.root)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val interval = binding.intervalInput.text?.toString()?.trim()?.toIntOrNull()
                when {
                    interval == null || interval < 1 -> binding.intervalInput.error = "Must be at least 1"
                    interval == plant.intervalDays -> dialog.dismiss()
                    else -> {
                        onSave(interval)
                        dialog.dismiss()
                    }
                }
            }
        }

        dialog.show()
    }
}
