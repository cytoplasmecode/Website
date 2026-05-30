package com.cytoplasmecode.plantwatering.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import com.cytoplasmecode.plantwatering.R
import com.cytoplasmecode.plantwatering.calendar.CalendarInfo

class CalendarPickerDialog(
    private val context: Context,
    private val calendars: List<CalendarInfo>,
    private val currentCalendarId: String?,
    private val onSelect: (CalendarInfo) -> Unit,
) {
    fun show() {
        val adapter = CalendarListAdapter(context, calendars, currentCalendarId)
        AlertDialog.Builder(context)
            .setTitle("Choose a calendar")
            .setAdapter(adapter) { _, index ->
                onSelect(calendars[index])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private class CalendarListAdapter(
        private val context: Context,
        private val items: List<CalendarInfo>,
        private val selectedId: String?,
    ) : BaseAdapter() {

        override fun getCount() = items.size
        override fun getItem(pos: Int) = items[pos]
        override fun getItemId(pos: Int) = pos.toLong()

        override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView
                ?: LayoutInflater.from(context).inflate(R.layout.item_calendar, parent, false)

            val item = items[pos]

            val dot = view.findViewById<ImageView>(R.id.calendarColor)
            val name = view.findViewById<TextView>(R.id.calendarName)
            val check = view.findViewById<ImageView>(R.id.calendarSelected)

            val circle = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(parseColor(item.colorHex))
            }
            dot.setImageDrawable(circle)
            name.text = item.name
            check.visibility = if (item.id == selectedId) View.VISIBLE else View.GONE

            return view
        }

        private fun parseColor(hex: String?): Int = runCatching {
            Color.parseColor(hex)
        }.getOrDefault(Color.parseColor("#4ADE80"))
    }
}
