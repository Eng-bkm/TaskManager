package com.example.taskmanager

import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
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

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var todoAdapter: TodoAdapter
    private lateinit var dayOfWeekAdapter: DayOfWeekAdapter
    private val todoFile = "todos.dat"
    private val todosByDate = mutableMapOf<Int, MutableList<Todo>>()
    private var selectedDayOfWeek: Int = 0
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private var isWeeklyView = true
    private var currentWeekStart: Date = getStartOfWeek(Date())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.d("MainActivity", "currentWeekStart: $currentWeekStart")
        val daysOfWeek = getDaysOfWeek()
        Log.d("MainActivity", "daysOfWeek: $daysOfWeek")
        selectedDayOfWeek = getSelectedDayOfWeek(Date())
        dayOfWeekAdapter = DayOfWeekAdapter(daysOfWeek, { date ->
            onDayOfWeekClicked(date)
        }, selectedDayOfWeek, true, currentWeekStart)

        binding.rvDaysOfWeek.apply {
            adapter = dayOfWeekAdapter
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
        }

        loadTodos()
        todoAdapter = TodoAdapter(getTodosForDay(selectedDayOfWeek), this)

        binding.rvTodoItems.apply {
            adapter = todoAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
        }
        updateTodoListForSelectedDate()

        binding.btnAddTodo.setOnClickListener {
            val todoTitle = binding.etTodoTitle.text.toString()
            if (todoTitle.isNotEmpty()) {
                val todo = Todo(todoTitle, date = getDateForDayOfWeek(selectedDayOfWeek))
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
            daysOfWeek.add(
                SimpleDateFormat("EEE dd/MM", Locale.getDefault()).format(calendar.time)
            )
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return daysOfWeek
    }

    private fun onDayOfWeekClicked(date: Date) {
        selectedDayOfWeek = getSelectedDayOfWeek(date)
        dayOfWeekAdapter.setSelectedDay(selectedDayOfWeek)
        updateTodoListForSelectedDate()
    }

    private fun updateTodoListForSelectedDate() {
        val todos = getTodosForDay(selectedDayOfWeek)
        todoAdapter.todos.clear()
        todoAdapter.todos.addAll(todos)
        todoAdapter.notifyDataSetChanged()
    }





    fun deleteTodo(date: Date, todo: Todo) {
        val dayOfWeek = getSelectedDayOfWeek(date)
        todosByDate[dayOfWeek]?.remove(todo)
        if (todo.day) {
            for (i in 0..6) {
                todosByDate[i]?.removeAll { it.title == todo.title }
            }
        } else if (todo.week) {
            for (i in 0..6) {
                if (getSelectedDayOfWeek(todo.date!!) == getSelectedDayOfWeek(getDateForDayOfWeek(i))) {
                    todosByDate[i]?.removeAll { it.title == todo.title }
                }
            }
        }
        updateTodoListForSelectedDate()
        saveTodos()
    }

    fun saveTodos() {
        try {
            val file = File(filesDir, todoFile)
            val fileOutputStream = FileOutputStream(file)
            val objectOutputStream = ObjectOutputStream(fileOutputStream)
            objectOutputStream.writeObject(todosByDate)
            objectOutputStream.close()
            fileOutputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }



// ... other imports ...


    fun addTodo(todo: Todo) {
        val dayOfWeek = getSelectedDayOfWeek(todo.date ?: Date())
        val existingTodos = todosByDate.getOrPut(dayOfWeek) { mutableListOf() }

        // Check for duplicates and remove the older one
        val duplicateTodo = existingTodos.find { it.title == todo.title }
        if (duplicateTodo != null) {
            existingTodos.remove(duplicateTodo)
        }

        existingTodos.add(todo)

        // Handle repeat logic
        if (todo.day) {
            val calendar = Calendar.getInstance()
            for (i in 0 until 90) { // Iterate for 90 days (approximately 3 months)
                val repeatedTodo = todo.copy(date = calendar.time, day = true, week = false, month = false)
                val currentDayOfWeek = getSelectedDayOfWeek(calendar.time)
                val todosForDay = todosByDate.getOrPut(currentDayOfWeek) { mutableListOf() }
                // Check for duplicates based on title AND date
                val duplicate = todosForDay.find { it.title == repeatedTodo.title && isSameDay(it.date, repeatedTodo.date) }
                if (duplicate != null) {
                    todosForDay.remove(duplicate)
                }
                todosForDay.add(repeatedTodo)
                calendar.add(Calendar.DAY_OF_YEAR, 1) // Move to the next day
            }
        } else if (todo.week) {
            for (i in 0..6) {
                if (i != dayOfWeek) {
                    val repeatedTodo = todo.copy(date = getDateForDayOfWeek(i), day = false, week = true, month = false)
                    val todosForDay = todosByDate.getOrPut(i) { mutableListOf() }
                    val duplicate = todosForDay.find { it.title == repeatedTodo.title && isSameDay(it.date, repeatedTodo.date) }
                    if (duplicate != null) {
                        todosForDay.remove(duplicate)
                    }
                    if (isSameDay(repeatedTodo.date, getDateForDayOfWeek(i))) {
                        todosForDay.add(repeatedTodo)
                    }
                }
            }
        } else if (todo.month) {
            //TODO
        }

        updateTodoListForSelectedDate()
        saveTodos()
    }

    private fun isSameDay(date1: Date?, date2: Date?): Boolean {
        if (date1 == null || date2 == null) return false
        val cal1 = Calendar.getInstance()
        val cal2 = Calendar.getInstance()
        cal1.time = date1
        cal2.time = date2
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
    private fun loadTodos() {
        try {
            val file = File(filesDir, todoFile)
            if (file.exists()) {
                val fileInputStream = FileInputStream(file)
                val objectInputStream = ObjectInputStream(fileInputStream)
                val loadedTodos = objectInputStream.readObject() as? MutableMap<Int, MutableList<Todo>>
                if (loadedTodos != null) {
                    todosByDate.clear()
                    todosByDate.putAll(loadedTodos)
                    // Re-add repeated todos after loading
                    val calendar = Calendar.getInstance()
                    val today = calendar.time
                    for ((dayOfWeek, todos) in todosByDate.entries) {
                        val todosToAdd = mutableListOf<Todo>()
                        for (todo in todos) {
                            if (todo.day) {
                                calendar.time = today
                                for (i in 0 until 90) {
                                    val currentDate = calendar.time
                                    val repeatedTodo = todo.copy(date = currentDate, day = true, week = false, month = false)
                                    val currentDayOfWeek = getSelectedDayOfWeek(currentDate)
                                    val duplicate = todosByDate.getOrPut(currentDayOfWeek) { mutableListOf() }.find { it.title == repeatedTodo.title && isSameDay(it.date, repeatedTodo.date) }
                                    if (duplicate == null) {
                                        todosToAdd.add(repeatedTodo)
                                    } else {
                                        todosByDate[currentDayOfWeek]?.remove(duplicate)
                                    }
                                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                                }
                            } else if (todo.week) {
                                for (i in 0..6) {
                                    if (i != dayOfWeek) {
                                        val repeatedTodo = todo.copy(date = getDateForDayOfWeek(i), day = false, week = true, month = false)
                                        if (isSameDay(repeatedTodo.date, getDateForDayOfWeek(i))) {
                                            val duplicate = todosByDate.getOrPut(i) { mutableListOf() }.find { it.title == repeatedTodo.title && isSameDay(it.date, repeatedTodo.date) }
                                            if (duplicate == null) {
                                                todosToAdd.add(repeatedTodo)
                                            } else {
                                                todosByDate[i]?.remove(duplicate)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        todosByDate[dayOfWeek]?.addAll(todosToAdd)
                    }
                }
                objectInputStream.close()
                fileInputStream.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    private fun deleteDoneTodos() {
        val todosToRemove = mutableListOf<Todo>()
        for ((_, todos) in todosByDate) {
            todosToRemove.addAll(todos.filter { it.isChecked })
        }
        for (todo in todosToRemove) {
            if (todo.day) {
                for (i in 0..6) {
                    todosByDate[i]?.removeAll { it.title == todo.title }
                }
            } else if (todo.week) {
                for (i in 0..6) {
                    if (getSelectedDayOfWeek(todo.date!!) == getSelectedDayOfWeek(getDateForDayOfWeek(i))) {
                        todosByDate[i]?.removeAll { it.title == todo.title }
                    }
                }
            } else {
                for ((_, todos) in todosByDate) {
                    todos.remove(todo)
                }
            }
        }
        updateTodoListForSelectedDate()
        saveTodos()
    }

    private fun getStartOfWeek(date: Date): Date {
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.firstDayOfWeek = Calendar.MONDAY
        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.time
    }

    private fun getTodosForDay(dayOfWeek: Int): MutableList<Todo> {
        return todosByDate[dayOfWeek] ?: mutableListOf()
    }

    private fun getSelectedDayOfWeek(date: Date): Int {
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.firstDayOfWeek = Calendar.MONDAY
        return calendar.get(Calendar.DAY_OF_WEEK) - calendar.firstDayOfWeek
    }

    private fun getDateForDayOfWeek(dayOfWeek: Int): Date {
        val calendar = Calendar.getInstance()
        calendar.time = currentWeekStart
        calendar.add(Calendar.DAY_OF_WEEK, dayOfWeek)
        return calendar.time
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
                val selectedDate = selectedCalendar.time
                selectedDayOfWeek = getSelectedDayOfWeek(selectedDate)
                isWeeklyView = false
                updateTodoListForSelectedDate()
                dayOfWeekAdapter.setWeeklyView(false)
                dayOfWeekAdapter.setSelectedDay(selectedDayOfWeek)
            },
            year,
            month,
            day
        )
        datePickerDialog.show()
    }
}