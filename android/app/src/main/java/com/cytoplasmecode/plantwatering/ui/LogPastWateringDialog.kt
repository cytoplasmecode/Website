package com.cytoplasmecode.plantwatering.ui

import android.app.DatePickerDialog
import android.content.Context
import java.time.LocalDate

class LogPastWateringDialog(
    private val context: Context,
    private val plantName: String,
    private val onDateSelected: (LocalDate) -> Unit,
) {
    fun show() {
        val today = LocalDate.now()
        val picker = DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val selected = LocalDate.of(year, month + 1, dayOfMonth)
                // Guard: only accept dates that are not in the future
                if (!selected.isAfter(today)) {
                    onDateSelected(selected)
                }
            },
            today.year,
            today.monthValue - 1,
            today.dayOfMonth,
        )
        picker.setTitle(context.getString(
            com.cytoplasmecode.plantwatering.R.string.log_past_watering_title,
            plantName,
        ))
        picker.datePicker.maxDate = System.currentTimeMillis()
        picker.show()
    }
}
