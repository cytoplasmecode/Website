package com.cytoplasmecode.plantwatering.ui

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import com.cytoplasmecode.plantwatering.R
import com.cytoplasmecode.plantwatering.calendar.HistoryEvent
import com.cytoplasmecode.plantwatering.data.Plant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class PlantHistoryDialog(
    private val context: Context,
    private val plant: Plant,
    private val events: List<HistoryEvent>,
    private val onDateChanged: (event: HistoryEvent, newDate: LocalDate, isLastWatering: Boolean) -> Unit,
) {
    private val formatter = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault())

    fun show() {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("History — ${plant.name}")

        if (events.isEmpty()) {
            builder.setMessage("No watering history found in your calendars.")
            builder.setNegativeButton("Close", null)
            builder.show()
            return
        }

        // events are sorted most-recent-first; position 0 is the last watering
        builder.setAdapter(HistoryAdapter()) { _, position ->
            val event = events[position]
            showDatePicker(event, isLastWatering = position == 0)
        }
        builder.setNegativeButton("Close", null)
        builder.show()
    }

    private fun showDatePicker(event: HistoryEvent, isLastWatering: Boolean) {
        val d = event.date
        DatePickerDialog(
            context,
            { _, year, month, day ->
                onDateChanged(event, LocalDate.of(year, month + 1, day), isLastWatering)
            },
            d.year, d.monthValue - 1, d.dayOfMonth,
        ).show()
    }

    private inner class HistoryAdapter : BaseAdapter() {
        override fun getCount() = events.size
        override fun getItem(pos: Int) = events[pos]
        override fun getItemId(pos: Int) = pos.toLong()

        override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView
                ?: LayoutInflater.from(context).inflate(R.layout.item_history, parent, false)
            val event = events[pos]
            view.findViewById<TextView>(R.id.historyDate).text = event.date.format(formatter)
            view.findViewById<TextView>(R.id.historyUser).text = "by ${event.userName}"
            return view
        }
    }
}
