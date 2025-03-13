package com.example.taskmanager

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.taskmanager.databinding.ItemDayOfWeekBinding
import java.util.Calendar
import java.util.Date

/**
 * [DayOfWeekAdapter] is a RecyclerView adapter that displays a list of days of the week.
 * It allows the user to select a day, and it updates the UI to reflect the selection.
 *
 * @property days A list of strings representing the names of the days of the week (e.g., "Mon", "Tue").
 * @property onDayClick A lambda function that is called when a day is clicked.
 *                       It receives the selected [Date] as a parameter.
 * @property selectedDay The index of the currently selected day (0-6, where 0 is Monday). Defaults to 0.
 * @property isWeeklyView A boolean indicating whether the adapter is displaying a weekly view.
 *                        If true, the selected day is highlighted. Defaults to true.
 * @property currentWeekStart The [Date] representing the start of the current week. Defaults to the current date.
 */
class DayOfWeekAdapter(
    private val days: List<String>,
    private val onDayClick: (Date) -> Unit,
    private var selectedDay: Int = 0,
    private var isWeeklyView: Boolean = true,
    private var currentWeekStart: Date = Date()
) : RecyclerView.Adapter<DayOfWeekAdapter.DayOfWeekViewHolder>() {

    class DayOfWeekViewHolder(val binding: ItemDayOfWeekBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayOfWeekViewHolder {
        val binding =
            ItemDayOfWeekBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DayOfWeekViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DayOfWeekViewHolder, position: Int) {
        val day = days[position]
        holder.binding.tvDayOfWeek.text = day
        val calendar = Calendar.getInstance()
        calendar.time = currentWeekStart
        calendar.add(Calendar.DAY_OF_MONTH, position)
        val calculatedDate = calendar.time
        holder.itemView.setOnClickListener {
            Log.d("DayOfWeekAdapter", "Clicked position: $position")
            Log.d("DayOfWeekAdapter", "Previous selectedDay: $selectedDay")
            val previousSelected = selectedDay
            selectedDay = (position + 3)%7
            Log.d("DayOfWeekAdapter", "New selectedDay: $selectedDay")
            if (previousSelected != selectedDay) {
                notifyItemChanged(previousSelected)
                notifyItemChanged(selectedDay)
            }

            Log.d("DayOfWeekAdapter", "Calculated date: ${calculatedDate}")
            onDayClick(calculatedDate)
        }
        Log.d("DayOfWeekAdapter", "onBindViewHolder: position: $position, selectedDay: $selectedDay, isWeeklyView: $isWeeklyView")
        val todayPosition = getDayOfWeek(Date())
       // Log.d("DayOfWeekAdapter", "todays: $todayPosition")
        if (isWeeklyView && (position + 3)%7 == selectedDay) {
            holder.binding.root.setBackgroundResource(R.color.purple_200)
        } else {
            holder.binding.root.setBackgroundResource(android.R.color.transparent)
        }
    }

    override fun getItemCount(): Int {
        return days.size
    }

    fun setWeeklyView(isWeekly: Boolean) {
        isWeeklyView = isWeekly
        notifyDataSetChanged()
    }

    fun updateWeekStart(weekStart: Date) {
        currentWeekStart = weekStart
        notifyDataSetChanged()
    }
    fun setSelectedDay(day: Int){
        Log.d("DayOfWeekAdapter", "setSelectedDay: day: $day")
        if (day == -1){
            selectedDay = 6
        }
        else{
        selectedDay = day}
        notifyDataSetChanged()
    }
    private fun getDayOfWeek(date: Date): Int {
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.firstDayOfWeek = Calendar.MONDAY
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        Log.d("getDayOfWeek", "getDayOfWeek: $dayOfWeek")
        return dayOfWeek - calendar.firstDayOfWeek
    }
}