package com.example.taskmanager

import android.app.DatePickerDialog
import android.graphics.Paint
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.taskmanager.databinding.ItemTodoBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * TodoAdapter is a RecyclerView.Adapter that manages the display of a list of Todo items.
 * It handles the creation, binding, and updating of Todo item views within a RecyclerView.
 *
 * @param todos A mutable list of {@link Todo} objects to be displayed.
 */
class TodoAdapter(
    val todos: MutableList<Todo>,

    ) : RecyclerView.Adapter<TodoAdapter.TodoViewHolder>() {

    class TodoViewHolder(val binding: ItemTodoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TodoViewHolder {
        val binding = ItemTodoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TodoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TodoViewHolder, position: Int) {
        val curTodo = todos[position]
        holder.binding.apply {
            tvTodoTitle.text = curTodo.title
            cbDone.isChecked = curTodo.isChecked
            toggleStrikeThrough(tvTodoTitle, curTodo.isChecked)
            cbDone.setOnCheckedChangeListener { _, isChecked ->
                toggleStrikeThrough(tvTodoTitle, isChecked)
                curTodo.isChecked = isChecked
            }
            btn1.setOnClickListener {
                Log.d("TodoAdapter", "btn1 clicked for position: $position")
                showDatePickerDialog(holder, position)
            }
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            tvDeadline.text = if (curTodo.deadline != null) {
                "Deadline: ${dateFormat.format(curTodo.deadline!!)}"
            } else {
                "date: ${dateFormat.format(curTodo.date!!)}"
            }
            // Set initial state of important/urgent icons
            updateImportantIcon(ivImportant, curTodo.isImportant)
            updateUrgentIcon(ivUrgent, curTodo.isUrgent)

            // Handle clicks on important icon
            ivImportant.setOnClickListener {
                curTodo.isImportant = !curTodo.isImportant
                updateImportantIcon(ivImportant, curTodo.isImportant)
            }

            // Handle clicks on urgent icon
            ivUrgent.setOnClickListener {
                curTodo.isUrgent = !curTodo.isUrgent
                updateUrgentIcon(ivUrgent, curTodo.isUrgent)
            }
        }
    }

    override fun getItemCount(): Int {
        return todos.size
    }

    fun addTodo(todo: Todo) {
        todos.add(todo)
        notifyItemInserted(todos.size - 1)
    }

    fun deleteDoneTodos() {
        todos.removeAll { todo ->
            todo.isChecked
        }
        notifyDataSetChanged()
    }

    private fun toggleStrikeThrough(tvTodoTitle: TextView, isChecked: Boolean) {
        if (isChecked) {
            tvTodoTitle.paintFlags = tvTodoTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            tvTodoTitle.paintFlags = tvTodoTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }
    }
    private fun showDatePickerDialog(holder: TodoViewHolder, position: Int) {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            holder.itemView.context,
            { _, selectedYear, selectedMonth, selectedDay ->
                val selectedCalendar = Calendar.getInstance()
                selectedCalendar.set(selectedYear, selectedMonth, selectedDay)
                val selectedDate = selectedCalendar.time
                Log.d("TodoAdapter", "Date selected: $selectedDate for position: $position")
                //onDeadlineSet(position, selectedDate)
            },
            year,
            month,
            day
        )
        datePickerDialog.show()
    }
    private fun updateImportantIcon(imageView: ImageView, isImportant: Boolean) {
        if (isImportant) {
            imageView.setBackgroundResource(R.drawable.triangle_highlighted)
        } else {
            imageView.setBackgroundResource(R.drawable.triangle_normal)
        }
    }

    private fun updateUrgentIcon(imageView: ImageView, isUrgent: Boolean) {
        if (isUrgent) {
            imageView.setBackgroundResource(R.drawable.oval_highlighted)
        } else {
            imageView.setBackgroundResource(R.drawable.oval_normal)
        }
    }
}