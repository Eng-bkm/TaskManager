package com.example.taskmanager

import android.Manifest
import java.util.Date

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.taskmanager.databinding.TodoItemBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TodoAdapter(
    public val todos: MutableList<Todo>,
    private val context: Context
) : RecyclerView.Adapter<TodoAdapter.TodoViewHolder>() {

    private var expandedPosition = -1
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    inner class TodoViewHolder(val binding: TodoItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(todo: Todo) {
            with(binding) {
                // Basic task info
                tvTodoTitle.text = todo.title
                cbDone.isChecked = todo.isChecked

                // Time range
                tvFrom.text = todo.from ?: "Set start"
                tvTo.text = todo.to ?: "Set end"

                // Deadline
                tvDeadlineDate.text = Editable.Factory.getInstance().newEditable(todo.deadlineDate ?: "Set date")
                tvDeadlineTime.text = Editable.Factory.getInstance().newEditable(todo.deadlineTime ?: "Set Time")


                // Reminder
                tvReminderDate.text = Editable.Factory.getInstance().newEditable(todo.reminderTimeDate ?: "Set date")
                tvReminderTime.text = Editable.Factory.getInstance().newEditable(todo.reminderTimeTime ?: "Set Time")


                // Repeat options
                tvDay.isSelected = todo.day
                tvWeek.isSelected = todo.week
                tvMonth.isSelected = todo.month

                // Priority indicators
                ivImportant.visibility = if (todo.isImportant) View.VISIBLE else View.GONE
                ivUrgent.visibility = if (todo.isUrgent) View.VISIBLE else View.GONE

                // Click listeners
                tvDay.setOnClickListener { toggleDayRepeat(todo) }
                tvWeek.setOnClickListener { (context as MainActivity).weekRepeater(todo) }
                tvMonth.setOnClickListener { showMonthRepeatDialog(todo) }

                cbDone.setOnCheckedChangeListener { _, isChecked ->
                    todo.isChecked = isChecked
                    saveTodos()
                }

                // Time/date pickers
                tvFrom.setOnClickListener { showTimePicker(todo, true) }
                tvTo.setOnClickListener { showTimePicker(todo, false) }
                tvDeadlineDate.setOnClickListener { showDatePicker(todo, true) }
                tvDeadlineTime.setOnClickListener { showTimePicker(todo, null) }
                tvReminderDate.setOnClickListener { showDatePicker(todo, false) }
                tvReminderTime.setOnClickListener { showReminderTimePicker(todo) }

                // Expand/collapse
                llTodoDetails.visibility = if (adapterPosition == expandedPosition) View.VISIBLE else View.GONE
                itemView.setOnClickListener {
                    expandedPosition = if (adapterPosition == expandedPosition) -1 else adapterPosition
                    notifyDataSetChanged()
                }

                // Handle text changes for direct editing
                setupTextWatchers(todo)
            }
        }

        private fun setupTextWatchers(todo: Todo) {
            binding.tvTodoTitle.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    todo.title = s.toString()
                    saveTodos()
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        }

        private fun toggleDayRepeat(todo: Todo) {
            todo.day = !todo.day
            if (todo.day) {
                (context as MainActivity).dayRepeater(todo)
            }
            notifyDataSetChanged()
            saveTodos()
        }

        private fun showMonthRepeatDialog(todo: Todo) {
            AlertDialog.Builder(context)
                .setTitle("Monthly Repeat")
                .setMessage("Set this task to repeat monthly?")
                .setPositiveButton("Yes") { _, _ ->
                    todo.month = true
                    // Implement monthly repeat logic here
                    notifyDataSetChanged()
                    saveTodos()
                }
                .setNegativeButton("No", null)
                .show()
        }
    }

    private fun showTimePicker(todo: Todo, isFrom: Boolean?) {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        TimePickerDialog(
            context,
            { _, selectedHour, selectedMinute ->
                val formattedTime = String.format("%02d:%02d", selectedHour, selectedMinute)
                when (isFrom) {
                    true -> {
                        todo.from = formattedTime
                        if (todo.reminderTimeDate == null) {
                            todo.reminderTimeDate = dateFormat.format(todo.date ?: Date())
                        }
                    }
                    false -> todo.to = formattedTime
                    null -> todo.deadlineTime = formattedTime
                }
                scheduleNotification(todo)
                notifyDataSetChanged()
                saveTodos()
            },
            hour, minute, true
        ).show()
    }

    private fun showReminderTimePicker(todo: Todo) {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        TimePickerDialog(
            context,
            { _, selectedHour, selectedMinute ->
                todo.reminderTimeTime = String.format("%02d:%02d", selectedHour, selectedMinute)
                if (todo.reminderTimeDate == null) {
                    todo.reminderTimeDate = dateFormat.format(todo.date ?: Date())
                }
                scheduleNotification(todo)
                notifyDataSetChanged()
                saveTodos()
            },
            hour, minute, true
        ).show()
    }

    private fun showDatePicker(todo: Todo, isDeadline: Boolean) {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(
            context,
            { _, selectedYear, selectedMonth, selectedDay ->
                val formattedDate = String.format(
                    "%02d/%02d/%04d",
                    selectedDay,
                    selectedMonth + 1,
                    selectedYear
                )
                if (isDeadline) {
                    todo.deadlineDate = formattedDate
                } else {
                    todo.reminderTimeDate = formattedDate
                }
                scheduleNotification(todo)
                notifyDataSetChanged()
                saveTodos()
            },
            year, month, day
        ).show()
    }

    private fun scheduleNotification(todo: Todo) {
        (context as? MainActivity)?.let { activity ->
            if (hasNotificationPermission()) {
                activity.scheduleTodoNotification(todo)
            } else {
                Toast.makeText(context, "Notification permission required", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun saveTodos() {
        (context as MainActivity).saveTodos()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TodoViewHolder {
        val binding = TodoItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TodoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TodoViewHolder, position: Int) {
        holder.bind(todos[position])
    }

    override fun getItemCount(): Int = todos.size

    fun addTodo(todo: Todo) {
        todos.add(todo)
        notifyItemInserted(todos.size - 1)
        saveTodos()
        scheduleNotification(todo)
    }

    fun removeTodo(position: Int) {
        todos.removeAt(position)
        notifyItemRemoved(position)
        saveTodos()
    }
}