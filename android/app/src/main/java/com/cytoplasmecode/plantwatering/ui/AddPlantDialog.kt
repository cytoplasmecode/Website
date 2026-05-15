package com.cytoplasmecode.plantwatering.ui

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import com.cytoplasmecode.plantwatering.databinding.DialogAddPlantBinding

class AddPlantDialog(
    private val context: Context,
    private val onAdd: (name: String, intervalDays: Int) -> Unit
) {
    fun show() {
        val binding = DialogAddPlantBinding.inflate(LayoutInflater.from(context))
        val dialog = AlertDialog.Builder(context)
            .setTitle("Add Plant")
            .setView(binding.root)
            .setPositiveButton("Add", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = binding.plantNameInput.text?.toString()?.trim() ?: ""
                val interval = binding.intervalInput.text?.toString()?.trim()?.toIntOrNull()
                when {
                    name.isEmpty() -> binding.plantNameInput.error = "Required"
                    interval == null || interval < 1 -> binding.intervalInput.error = "Must be at least 1"
                    else -> {
                        onAdd(name, interval)
                        dialog.dismiss()
                    }
                }
            }
        }

        dialog.show()
    }
}
