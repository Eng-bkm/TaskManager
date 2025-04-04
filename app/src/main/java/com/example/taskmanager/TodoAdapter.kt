package com.example.taskmanager

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.taskmanager.databinding.TodoItemBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TodoAdapter(
    private var todos: MutableList<Todo>,
    private val context: Context,
    private val onTodoUpdated: (Todo) -> Unit,
    private val onTodoDeleted: (Int) -> Unit
) : RecyclerView.Adapter<TodoAdapter.TodoViewHolder>() {

    private var expandedPosition = -1
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    inner class TodoViewHolder(val binding: TodoItemBinding) : RecyclerView.ViewHolder(binding.root) {
        private var currentTodo: Todo? = null

        fun bind(todo: Todo) {
            currentTodo = todo.copy()
            updateUI(currentTodo!!)
            setupClickListeners()
            setupTextWatchers()

            binding.llTodoDetails.visibility = if (adapterPosition == expandedPosition) View.VISIBLE else View.GONE
            itemView.setOnClickListener {
                expandedPosition = if (adapterPosition == expandedPosition) -1 else adapterPosition
                notifyDataSetChanged()
            }
        }

        private fun updateUI(todo: Todo) {
            with(binding) {
                tvTodoTitle.text = todo.title
                cbDone.isChecked = todo.isChecked
                tvFrom.text = todo.from ?: "Set start"
                tvTo.text = todo.to ?: "Set end"
                tvDeadlineDate.setText(todo.deadlineDate ?: "Set date")
                tvDeadlineTime.setText(todo.deadlineTime ?: "Set time")
                tvReminderDate.setText(todo.reminderTimeDate ?: "Set date")
                tvReminderTime.setText(todo.reminderTimeTime ?: "Set time")
                tvDay.isSelected = todo.day
                tvWeek.isSelected = todo.week
                tvMonth.isSelected = todo.month
                updateImportanceUI(todo.isImportant)
                updateUrgencyUI(todo.isUrgent)
            }
        }

        private fun updateImportanceUI(isImportant: Boolean) {
            binding.ivImportant.apply {
                setImageResource(if (isImportant) R.drawable.ic_star_filled else R.drawable.ic_star_outline)
                setColorFilter(ContextCompat.getColor(context,
                    if (isImportant) R.color.important_active else R.color.important_default))
            }
        }

        private fun updateUrgencyUI(isUrgent: Boolean) {
            binding.ivUrgent.apply {
                setImageResource(if (isUrgent) R.drawable.ic_warning_filled else R.drawable.ic_warning_outline)
                setColorFilter(ContextCompat.getColor(context,
                    if (isUrgent) R.color.urgent_active else R.color.urgent_default))
            }
        }

        private fun setupClickListeners() {
            with(binding) {
                ivImportant.setOnClickListener {
                    currentTodo?.let { todo ->
                        todo.isImportant = !todo.isImportant
                        updateImportanceUI(todo.isImportant)
                        onTodoUpdated(todo.copy())
                    }
                }

                ivUrgent.setOnClickListener {
                    currentTodo?.let { todo ->
                        todo.isUrgent = !todo.isUrgent
                        updateUrgencyUI(todo.isUrgent)
                        onTodoUpdated(todo.copy())
                    }
                }

                tvDay.setOnClickListener {
                    currentTodo?.let { todo ->
                        todo.day = !todo.day
                        if (todo.day) {
                            (context as MainActivity).dayRepeater(todo.copy())
                        }
                        onTodoUpdated(todo.copy())
                    }
                }

                tvWeek.setOnClickListener {
                    currentTodo?.let { todo ->
                        (context as MainActivity).weekRepeater(todo.copy())
                    }
                }

                tvMonth.setOnClickListener {
                    currentTodo?.let { todo ->
                        showMonthRepeatDialog(todo.copy())
                    }
                }

                cbDone.setOnCheckedChangeListener { _, isChecked ->
                    currentTodo?.let { todo ->
                        todo.isChecked = isChecked
                        onTodoUpdated(todo.copy())
                    }
                }

                tvFrom.setOnClickListener {
                    currentTodo?.let { todo ->
                        showTimePicker(todo.copy(), true) { updatedTodo ->
                            currentTodo?.from = updatedTodo.from
                            updateUI(currentTodo!!)
                            onTodoUpdated(updatedTodo)
                        }
                    }
                }

                tvTo.setOnClickListener {
                    currentTodo?.let { todo ->
                        showTimePicker(todo.copy(), false) { updatedTodo ->
                            currentTodo?.to = updatedTodo.to
                            updateUI(currentTodo!!)
                            onTodoUpdated(updatedTodo)
                        }
                    }
                }

                tvDeadlineDate.setOnClickListener {
                    currentTodo?.let { todo ->
                        showDatePicker(todo.copy(), true) { updatedTodo ->
                            currentTodo?.deadlineDate = updatedTodo.deadlineDate
                            updateUI(currentTodo!!)
                            onTodoUpdated(updatedTodo)
                        }
                    }
                }

                tvDeadlineTime.setOnClickListener {
                    currentTodo?.let { todo ->
                        showTimePicker(todo.copy(), null) { updatedTodo ->
                            currentTodo?.deadlineTime = updatedTodo.deadlineTime
                            updateUI(currentTodo!!)
                            onTodoUpdated(updatedTodo)
                        }
                    }
                }

                tvReminderDate.setOnClickListener {
                    currentTodo?.let { todo ->
                        showDatePicker(todo.copy(), false) { updatedTodo ->
                            currentTodo?.reminderTimeDate = updatedTodo.reminderTimeDate
                            updateUI(currentTodo!!)
                            onTodoUpdated(updatedTodo)
                        }
                    }
                }

                tvReminderTime.setOnClickListener {
                    currentTodo?.let { todo ->
                        showReminderTimePicker(todo.copy()) { updatedTodo ->
                            currentTodo?.reminderTimeTime = updatedTodo.reminderTimeTime
                            updateUI(currentTodo!!)
                            onTodoUpdated(updatedTodo)
                        }
                    }
                }
            }
        }

        private fun setupTextWatchers() {
            binding.tvTodoTitle.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    currentTodo?.let { todo ->
                        todo.title = s?.toString() ?: ""
                        onTodoUpdated(todo.copy())
                    }
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        }

        private fun showMonthRepeatDialog(todo: Todo) {
            AlertDialog.Builder(context)
                .setTitle("Monthly Repeat")
                .setMessage("Set this task to repeat monthly?")
                .setPositiveButton("Yes") { _, _ ->
                    val updatedTodo = todo.copy(month = true)
                    onTodoUpdated(updatedTodo)
                }
                .setNegativeButton("No", null)
                .show()
        }

        private fun showTimePicker(
            todo: Todo,
            isFrom: Boolean?,
            callback: (Todo) -> Unit
        ) {
            (context as? MainActivity)?.cancelTodoNotification(todo)

            val calendar = Calendar.getInstance()
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)

            TimePickerDialog(
                context,
                { _, selectedHour, selectedMinute ->
                    val formattedTime = String.format("%02d:%02d", selectedHour, selectedMinute)
                    val updatedTodo = todo.copy().apply {
                        when (isFrom) {
                            true -> from = formattedTime
                            false -> to = formattedTime
                            null -> deadlineTime = formattedTime
                        }
                    }
                    (context as? MainActivity)?.scheduleTodoNotification(updatedTodo)
                    callback(updatedTodo)
                },
                hour, minute, true
            ).show()
        }

        private fun showReminderTimePicker(
            todo: Todo,
            callback: (Todo) -> Unit
        ) {
            val calendar = Calendar.getInstance()
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)

            TimePickerDialog(
                context,
                { _, selectedHour, selectedMinute ->
                    val updatedTodo = todo.copy().apply {
                        reminderTimeTime = String.format("%02d:%02d", selectedHour, selectedMinute)
                        if (reminderTimeDate == null) {
                            reminderTimeDate = dateFormat.format(date ?: Date())
                        }
                    }
                    (context as? MainActivity)?.scheduleTodoNotification(updatedTodo)
                    callback(updatedTodo)
                },
                hour, minute, true
            ).show()
        }

        private fun showDatePicker(
            todo: Todo,
            isDeadline: Boolean,
            callback: (Todo) -> Unit
        ) {
            (context as? MainActivity)?.cancelTodoNotification(todo)

            val calendar = Calendar.getInstance()
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            val day = calendar.get(Calendar.DAY_OF_MONTH)

            DatePickerDialog(
                context,
                { _, selectedYear, selectedMonth, selectedDay ->
                    val formattedDate = String.format("%02d/%02d/%04d",
                        selectedDay, selectedMonth + 1, selectedYear)
                    val updatedTodo = todo.copy().apply {
                        if (isDeadline) {
                            deadlineDate = formattedDate
                        } else {
                            reminderTimeDate = formattedDate
                        }
                    }
                    (context as? MainActivity)?.scheduleTodoNotification(updatedTodo)
                    callback(updatedTodo)
                },
                year, month, day
            ).show()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TodoViewHolder {
        val binding = TodoItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TodoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TodoViewHolder, position: Int) {
        holder.bind(todos[position].copy())
    }

    override fun getItemCount(): Int = todos.size

    fun updateTodos(newTodos: List<Todo>) {
        todos.clear()
        todos.addAll(newTodos.map { it.copy() })
        notifyDataSetChanged()
    }

    fun removeTodo(position: Int) {
        if (position in 0 until todos.size) {
            todos.removeAt(position)
            notifyItemRemoved(position)
            onTodoDeleted(position)
        }
    }
}