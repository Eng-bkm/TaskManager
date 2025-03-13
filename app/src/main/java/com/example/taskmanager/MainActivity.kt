package com.example.taskmanager

import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.util.remove
import androidx.core.util.size
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.taskmanager.databinding.ActivityMainBinding
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * The MainActivity class is the main entry point for the application.
 * It manages the UI for displaying a list of TODO items, handling user interactions,
 * and persisting TODO data to a file.
 * <p>
 * This class implements the following functionalities:
 * 1. Displaying a list of TODO items for a selected date.
 * 2. Allowing users to add new TODO items.
 * 3. Deleting completed TODO items.
 * 4. Displaying a horizontal list of days of the week for navigation.
 * 5. Selecting a specific date via a date picker dialog.
 * 6. Persisting TODO items to a file (`todos.dat`).
 * 7. Loading TODO items from the file on application startup.
 * 8. Managing weekly view and daily view.
 * </p>
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var todoAdapter: TodoAdapter
    private lateinit var dayOfWeekAdapter: DayOfWeekAdapter
    private val todoFile = "todos.dat"
    private val todosByDate = mutableMapOf<Date, MutableList<Todo>>()
    private var selectedDate: Date? = null
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private var isWeeklyView = true
    private var currentWeekStart: Date = Date()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.d("MainActivity", "currentWeekStart: $currentWeekStart")
        val daysOfWeek = getDaysOfWeek()
        Log.d("MainActivity", "daysOfWeek: $daysOfWeek")
        dayOfWeekAdapter = DayOfWeekAdapter(daysOfWeek, { date ->
            onDayOfWeekClicked(date)
        }, getDayOfWeek(Date()), true, currentWeekStart)

        binding.rvDaysOfWeek.apply {
            adapter = dayOfWeekAdapter
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
        }

        loadTodos()
        selectedDate = normalizeDate(Date()) // Normalize selectedDate immediately
        Log.d("onCreate", "Initial selectedDate: $selectedDate")
        todoAdapter = TodoAdapter(getTodosForDate(selectedDate ?: Date()))

        binding.rvTodoItems.apply {
            adapter = todoAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
        }
        updateTodoListForSelectedDate()
        Log.d("onCreate", "updateTodoListForSelectedDate called after loadTodos")

        binding.btnAddTodo.setOnClickListener {
            Log.d("btnAddTodo", "btnAddTodo clicked")
            val todoTitle = binding.etTodoTitle.text.toString()
            if (todoTitle.isNotEmpty() && selectedDate != null) {
                Log.d("brook", "selectedDate: $selectedDate")
                val todo = Todo(todoTitle, date = selectedDate)
                addTodo(todo)
                binding.etTodoTitle.text.clear()
            }
        }


        binding.btnDeleteDoneTodos.setOnClickListener {
            deleteDoneTodos()
        }

        binding.tvCalendar.setOnClickListener {
            showDatePickerDialog()
        }
    }

    private fun getDaysOfWeek(): List<String> {
        val daysOfWeek = mutableListOf<String>()
        val calendar = Calendar.getInstance()
        calendar.time = currentWeekStart
        for (i in 0 until 7) {
            daysOfWeek.add(calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault()) ?: "")
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        return daysOfWeek
    }

    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            this,
            { _, selectedYear, selectedMonth, selectedDay ->
                val selectedCalendar = Calendar.getInstance()
                selectedCalendar.set(selectedYear, selectedMonth, selectedDay)
                selectedDate = selectedCalendar.time
                isWeeklyView = false
                updateTodoListForSelectedDate()
                dayOfWeekAdapter.setWeeklyView(false)
            },
            year,
            month,
            day
        )
        datePickerDialog.show()
    }

    private fun updateTodoListForSelectedDate() {
        Log.d("updateTodoListForSelectedDate", "updateTodoListForSelectedDate called")
        val currentTodos = getTodosForDate(selectedDate ?: Date())
        todoAdapter.todos.clear()
        todoAdapter.todos.addAll(currentTodos)
        todoAdapter.notifyDataSetChanged()
        Log.d("updateTodoListForSelectedDate", "todosByDate size after update: ${todosByDate.size}")
    }


    private fun onDayOfWeekClicked(date: Date) {
        Log.d("onDayOfWeekClicked", "Clicked date: $date")
        Log.d("onDayOfWeekClicked", "getDayOfWeek(date): ${getDayOfWeek(date)}")
        isWeeklyView = true
        selectedDate = date
        if (getDayOfWeek(date) == getDayOfWeek(Date())) {
            selectedDate = normalizeDate(Date())
        }
        updateTodoListForSelectedDate()
        dayOfWeekAdapter.setWeeklyView(true)
        dayOfWeekAdapter.setSelectedDay(getDayOfWeek(date))


    }

    private fun onDeadlineSet(position: Int, date: Date?) {
        if (position >= 0 && position < todoAdapter.todos.size) {
            todoAdapter.todos[position].deadline = date
            todoAdapter.todos.sortBy { it.deadline }
            todoAdapter.notifyDataSetChanged()
            saveTodos()
        }
    }

    private fun addTodo(todo: Todo) {
        Log.d("addTodo", "--------------------------------------------------")
        Log.d("addTodo", "addTodo called")
        Log.d("addTodo", "Todo to add: ${todo.title}, date: ${todo.date}")
        Log.d("addTodo", "todosByDate before adding: $todosByDate")
        val todoDate = todo.date ?: return
        Log.d("addTodo", "todoDate before normalization: $todoDate")
        val normalizedTodoDate = normalizeDate(todoDate)
        Log.d("addTodo", "todoDate after normalization: $normalizedTodoDate")
        val currentDayTodos = todosByDate.getOrPut(normalizedTodoDate) {
            Log.d("addTodo", "Creating new list for date: $normalizedTodoDate")
            mutableListOf()
        }
        Log.d("addTodo", "currentDayTodos size before adding: ${currentDayTodos.size}")
        // Check if the todo is already in the list before adding
        if (!currentDayTodos.any { it.title == todo.title && it.date == todo.date }) {
            currentDayTodos.add(todo)
            Log.d("addTodo", "Todo added: ${todo.title}")
        } else {
            Log.d("addTodo", "Todo already exists: ${todo.title}")
        }
        Log.d("addTodo", "currentDayTodos size after adding: ${currentDayTodos.size}")
        Log.d("addTodo", "todosByDate size after adding: ${todosByDate.size}")
        Log.d("addTodo", "todosByDate after adding: $todosByDate")
        // Instead of adding directly, refresh the list properly
        updateTodoListForSelectedDate()
        Log.d("addTodo", "Calling saveTodos() after adding a todo")
        saveTodos()
        Log.d("addTodo", "--------------------------------------------------")
    }

    private fun deleteDoneTodos() {
        val currentTodos = getTodosForDate(selectedDate ?: Date())
        val iterator = currentTodos.iterator()
        while (iterator.hasNext()) {
            val todo = iterator.next()
            if (todo.isChecked) {
                iterator.remove()
            }
        }
        todoAdapter.notifyDataSetChanged()
        saveTodos()
    }

    private fun saveTodos() {
        try {
            Log.d("SaveTodos", "--------------------------------------------------")
            Log.d("SaveTodos", "saveTodos called")
            Log.d("SaveTodos", "todosByDate before saving: $todosByDate")
            Log.d("SaveTodos", "todosByDate size before saving: ${todosByDate.size}")
            for ((date, todos) in todosByDate) {
                Log.d("SaveTodos", "  Date: $date")
                for (todo in todos) {
                    Log.d("SaveTodos", "    - ${todo.title}, isChecked: ${todo.isChecked}, deadline: ${todo.deadline}, isImportant: ${todo.isImportant}, isUrgent: ${todo.isUrgent}")
                }
            }
            val file = File(filesDir, todoFile)
            Log.d("SaveTodos", "Saving to: ${file.absolutePath}")
            val fos = FileOutputStream(file)
            val oos = ObjectOutputStream(fos)
            oos.writeObject(todosByDate)
            oos.close()
            Log.d("SaveTodos", "saveTodos finished")
            Log.d("SaveTodos", "--------------------------------------------------")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadTodos() {
        try {
            Log.d("LoadTodos", "--------------------------------------------------")
            Log.d("LoadTodos", "loadTodos called")
            val file = File(filesDir, todoFile)
            Log.d("LoadTodos", "Loading from: ${file.absolutePath}")
            if (!file.exists()) {
                Log.d("LoadTodos", "todos.dat file does not exist.")
                Log.d("LoadTodos", "--------------------------------------------------")
                return
            }
            val fis = FileInputStream(file)
            val ois = ObjectInputStream(fis)
            @Suppress("UNCHECKED_CAST")
            val loadedTodos = ois.readObject() as MutableMap<Date, MutableList<Todo>>
            todosByDate.clear()
            todosByDate.putAll(loadedTodos)
            ois.close()
            Log.d("LoadTodos", "todosByDate size after loading: ${todosByDate.size}")
            Log.d("LoadTodos", "todosByDate after loading: $todosByDate")
            for ((date, todos) in todosByDate) {
                Log.d("LoadTodos", "  Date: $date")
                for (todo in todos) {
                    Log.d("LoadTodos", "    - ${todo.title}, isChecked: ${todo.isChecked}, deadline: ${todo.deadline}, isImportant: ${todo.isImportant}, isUrgent: ${todo.isUrgent}")
                }
            }
            Log.d("LoadTodos", "loadTodos finished")
            Log.d("LoadTodos", "--------------------------------------------------")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getStartOfDay(date: Date): Date {
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.time
    }

    private fun normalizeDate(date: Date): Date {
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.time
    }

    private fun getTodosForDate(date: Date): MutableList<Todo> {
        val normalizedDate = normalizeDate(date)
        return todosByDate.getOrDefault(normalizedDate, mutableListOf())
    }

    private fun getDayOfWeek(date: Date): Int {
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.firstDayOfWeek = Calendar.MONDAY
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        Log.d("getDayOfWeek", "getDayOfWeek: ${dayOfWeek.toString()}")
        return dayOfWeek - calendar.firstDayOfWeek
    }
}