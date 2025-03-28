package com.example.taskmanager

import android.Manifest
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.taskmanager.databinding.ActivityMainBinding
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
    private val todosByDate = mutableMapOf<String, MutableList<Todo>>()
    private var selectedDate: Date = Date()
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private var isWeeklyView = true
    private var currentWeekStart: Date = getStartOfWeek(Date())
    private val CHANNEL_ID = "todo_reminder_channel"
    private val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001
    private val EXACT_ALARM_PERMISSION_REQUEST_CODE = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkNotificationPermission()
        checkExactAlarmPermission()
        createNotificationChannel()
        showTestNotification()

        // Initialize days of the week
        val daysOfWeek = getDaysOfWeek()
        selectedDate = getStartOfDay(Date())

        dayOfWeekAdapter = DayOfWeekAdapter(
            daysOfWeek,
            { date -> onDayOfWeekClicked(date) },
            getSelectedDayOfWeek(selectedDate),
            true,
            currentWeekStart
        )

        binding.rvDaysOfWeek.apply {
            adapter = dayOfWeekAdapter
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
        }

        loadTodos()
        todoAdapter = TodoAdapter(getTodosForDate(selectedDate).toMutableList(), this)

        binding.rvTodoItems.apply {
            adapter = todoAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
        }

        updateTodoListForSelectedDate()

        binding.btnAddTodo.setOnClickListener {
            val todoTitle = binding.etTodoTitle.text.toString()
            if (todoTitle.isNotEmpty()) {
                val todo = Todo(todoTitle, date = selectedDate)
                addTodo(todo)
                binding.etTodoTitle.text.clear()
            }
        }

        binding.btnDeleteDoneTodos.setOnClickListener {
            deleteDoneTodos()
        }

        binding.btnDeleteRepeated.setOnClickListener {
            deleteRepeatedTodos()
        }

        binding.tvCalendar.setOnClickListener {
            showDatePickerDialog()
        }

        // Reschedule all notifications when app starts
        rescheduleAllNotifications()
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Permission already granted
                }
                ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) -> {
                    showPermissionExplanationDialog()
                }
                else -> {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        NOTIFICATION_PERMISSION_REQUEST_CODE
                    )
                }
            }
        }
    }

    private fun checkExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                try {
                    startActivityForResult(intent, EXACT_ALARM_PERMISSION_REQUEST_CODE)
                } catch (e: Exception) {
                    Toast.makeText(this, "Please enable exact alarms in settings", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showPermissionExplanationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Notification Permission Needed")
            .setMessage("This app needs notification permission to remind you about your tasks.")
            .setPositiveButton("OK") { _, _ ->
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTestNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val notificationId = 9999
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Task Manager")
            .setContentText("Notifications are working!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        notificationManager.notify(notificationId, builder.build())
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            NOTIFICATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    rescheduleAllNotifications()
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Todo Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Channel for todo task reminders"
                enableVibration(true)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }


    fun scheduleTodoNotification(todo: Todo) {
        try {
            // Priority 1: Use specific reminder time if set
            if (todo.hasReminder()) {
                val reminderDateTime = "${todo.reminderTimeDate} ${todo.reminderTimeTime}"
                val time = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                    .parse(reminderDateTime)?.time ?: 0L

                if (time > System.currentTimeMillis()) {
                    scheduleNotification(todo, time, true)
                    Log.d("Notification", "Reminder scheduled for: ${Date(time)}")
                }
            }
            // Priority 2: Use deadline if set
            else if (todo.hasDeadline()) {
                val deadlineDateTime = "${todo.deadlineDate} ${todo.deadlineTime}"
                val time = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                    .parse(deadlineDateTime)?.time ?: 0L

                if (time > System.currentTimeMillis()) {
                    scheduleNotification(todo, time, false)
                    Log.d("Notification", "Deadline notification scheduled for: ${Date(time)}")
                }
            }
            // Priority 3: Use from time if set (notify 5 minutes before)
            else if (!todo.from.isNullOrEmpty()) {
                val todoDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    .format(todo.date ?: Date())
                val fromDateTime = "$todoDate ${todo.from}"
                val calendar = Calendar.getInstance().apply {
                    time = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                        .parse(fromDateTime) ?: Date()
                    add(Calendar.MINUTE, -5) // Notify 5 minutes before
                }
                scheduleNotification(todo, calendar.timeInMillis, false)
                Log.d("Notification", "Time-based notification scheduled for: ${Date(calendar.timeInMillis)}")
            }
        } catch (e: Exception) {
            Log.e("Notification", "Error scheduling notification for task: ${todo.title}", e)
            Toast.makeText(this, "Error scheduling notification", Toast.LENGTH_SHORT).show()
        }
    }

    private fun scheduleNotification(todo: Todo, triggerTime: Long, isReminder: Boolean) {
        val notificationId = todo.hashCode()
        val intent = Intent(this, ReminderReceiver::class.java).apply {
            putExtra("notification_id", notificationId)
            putExtra("title", todo.title)
            putExtra("content", buildNotificationContent(todo, isReminder))
        }

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }
    }

    private fun buildNotificationContent(todo: Todo, isReminder: Boolean): String {
        return when {
            isReminder -> "Reminder: ${todo.title}"
            todo.hasDeadline() -> "Deadline: ${todo.deadlineDate} ${todo.deadlineTime}"
            !todo.from.isNullOrEmpty() -> "Starting soon at ${todo.from}"
            else -> "Task reminder"
        }
    }

    private fun determineNotificationTime(todo: Todo): Pair<Long, Boolean> {
        // Priority 1: Use reminder time if set
        if (!todo.reminderTimeDate.isNullOrEmpty() && !todo.reminderTimeTime.isNullOrEmpty()) {
            val reminderDateTime = "${todo.reminderTimeDate} ${todo.reminderTimeTime}"
            val time = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                .parse(reminderDateTime)?.time ?: 0L
            if (time > 0) return Pair(time, true)
        }

        // Priority 2: Use deadline time if set
        if (!todo.deadlineDate.isNullOrEmpty() && !todo.deadlineTime.isNullOrEmpty()) {
            val deadlineDate = todo.deadlineDate ?: dateFormat.format(todo.date ?: Date())
            val deadlineDateTime = "$deadlineDate ${todo.deadlineTime}"
            val time = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                .parse(deadlineDateTime)?.time ?: 0L
            if (time > 0) return Pair(time, false)
        }

        // Priority 3: Use from time (5 minutes before) if set
        if (!todo.from.isNullOrEmpty()) {
            val todoDate = dateFormat.format(todo.date ?: Date())
            val fromDateTime = "$todoDate ${todo.from}"
            val calendar = Calendar.getInstance().apply {
                time = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                    .parse(fromDateTime) ?: Date()
                add(Calendar.MINUTE, -5) // Notify 5 minutes before
            }
            return Pair(calendar.timeInMillis, false)
        }

        return Pair(0L, false)
    }


    private fun rescheduleAllNotifications() {
        todosByDate.values.flatten().forEach { todo ->
            scheduleTodoNotification(todo)
        }
    }

    public fun addTodo(todo: Todo) {
        val dateString = dateFormat.format(todo.date ?: Date())
        val existingTodos = todosByDate.getOrPut(dateString) { mutableListOf() }.toMutableList()

        val duplicateTodo = existingTodos.find { it.title == todo.title }
        if (duplicateTodo != null) {
            existingTodos.remove(duplicateTodo)
        }

        existingTodos.add(todo)
        todosByDate[dateString] = existingTodos

        scheduleTodoNotification(todo)
        updateTodoListForSelectedDate()
        saveTodos()
    }

    private fun deleteDoneTodos() {
        try {
            val currentDateKey = dateFormat.format(selectedDate)
            val currentDateTodos = todosByDate[currentDateKey] ?: mutableListOf()
            val todosToRemove = currentDateTodos.filter { it.isChecked }.toList()

            currentDateTodos.removeAll(todosToRemove)

            if (currentDateTodos.isEmpty()) {
                todosByDate.remove(currentDateKey)
            } else {
                todosByDate[currentDateKey] = currentDateTodos
            }

            updateTodoListForSelectedDate()
            saveTodos()

            Toast.makeText(
                this,
                "Deleted ${todosToRemove.size} completed tasks",
                Toast.LENGTH_SHORT
            ).show()

        } catch (e: Exception) {
            Log.e("DeleteDoneTodos", "Error deleting todos", e)
            Toast.makeText(this, "Error deleting tasks", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteRepeatedTodos() {
        try {
            val currentDate = selectedDate
            val calendar = Calendar.getInstance().apply { time = currentDate }
            val currentDateKey = dateFormat.format(currentDate)
            val currentTodos = todosByDate[currentDateKey] ?: mutableListOf()
            val checkedTodos = currentTodos.filter { it.isChecked }

            if (checkedTodos.isEmpty()) {
                Toast.makeText(this, "No checked tasks to delete", Toast.LENGTH_SHORT).show()
                return
            }

            var totalDeleted = 0

            for (dayOffset in 0..100) {
                calendar.time = currentDate
                calendar.add(Calendar.DAY_OF_YEAR, dayOffset)
                val futureDateKey = dateFormat.format(calendar.time)

                todosByDate[futureDateKey]?.let { todos ->
                    val beforeCount = todos.size
                    checkedTodos.forEach { checkedTodo ->
                        todos.removeAll { it.title == checkedTodo.title }
                    }
                    totalDeleted += (beforeCount - todos.size)

                    if (todos.isEmpty()) {
                        todosByDate.remove(futureDateKey)
                    }
                }
            }

            updateTodoListForSelectedDate()
            saveTodos()

            Toast.makeText(
                this,
                "Deleted $totalDeleted tasks across 100 days",
                Toast.LENGTH_SHORT
            ).show()

        } catch (e: Exception) {
            Log.e("DeleteChecked", "Error deleting todos", e)
            Toast.makeText(this, "Failed to delete tasks", Toast.LENGTH_SHORT).show()
        }
    }

    // ... [Keep all your remaining existing functions] ...


    private fun getDaysOfWeek(): List<String> {
        val daysOfWeek = mutableListOf<String>()
        val calendar = Calendar.getInstance()
        calendar.time = currentWeekStart

        val dateFormat = SimpleDateFormat("EEE dd/MM", Locale.getDefault()) // Format: "Mon 23/10"

        for (i in 0 until 7) { // Loop through 7 days of the week
            daysOfWeek.add(dateFormat.format(calendar.time)) // Add formatted date to the list
            calendar.add(Calendar.DAY_OF_YEAR, 1) // Move to the next day
        }

        return daysOfWeek
    }

    private fun onDayOfWeekClicked(date: Date) {
        selectedDate = getStartOfDay(date) // Update selectedDate
        Log.d("MainActivity", "Selected Date: ${dateFormat.format(selectedDate)}")
        dayOfWeekAdapter.setSelectedDay(getSelectedDayOfWeek(selectedDate))
        updateTodoListForSelectedDate()
    }

    private fun updateTodoListForSelectedDate() {
        Log.d("MainActivity", "Updating todos for date: ${dateFormat.format(selectedDate)}")

        // Create a new list to avoid clearing the original list
        val todos = getTodosForDate(selectedDate).toMutableList()
        Log.d("updater1addinsider0", "Todos for date ${dateFormat.format(selectedDate)}: ${todos.size}")

        // Clear the adapter's list
        todoAdapter.todos.clear()
        Log.d("updater1insider1", "Todos for date ${dateFormat.format(selectedDate)}: ${todos.size}")

        // Add the new list to the adapter
        todoAdapter.todos.addAll(todos)
        Log.d("updater1insider2", "Todos for date ${dateFormat.format(selectedDate)}: ${todos.size}")

        // Notify the adapter of data changes
        todoAdapter.notifyDataSetChanged()
    }

    private fun getSelectedDayOfWeek(date: Date): Int {
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.firstDayOfWeek = Calendar.MONDAY // Monday is the first day of the week
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - calendar.firstDayOfWeek
        return if (dayOfWeek < 0) 6 else dayOfWeek // Handle Sunday (value -1)
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
    private fun getStartOfDay(date: Date): Date {
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.time
    }

    private fun getTodosForDate(date: Date): MutableList<Todo> {
        val dateString = dateFormat.format(date)
        return todosByDate[dateString]?.toMutableList() ?: mutableListOf()
    }

    private fun getDateForDayOfWeek(dayOfWeek: Int): Date {
        val calendar = Calendar.getInstance()
        calendar.time = currentWeekStart
        calendar.add(Calendar.DAY_OF_YEAR, dayOfWeek)
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
                val selectedDate = getStartOfDay(selectedCalendar.time)
                this.selectedDate = selectedDate
                isWeeklyView = false
                updateTodoListForSelectedDate()
                dayOfWeekAdapter.setWeeklyView(false)
                dayOfWeekAdapter.setSelectedDay(getSelectedDayOfWeek(selectedDate))
            },
            year,
            month,
            day
        )
        datePickerDialog.show()
    }





    public fun saveTodos() {
        try {
            val fileOutputStream: FileOutputStream = openFileOutput(todoFile, Context.MODE_PRIVATE)
            val objectOutputStream = ObjectOutputStream(fileOutputStream)
            objectOutputStream.writeObject(todosByDate)
            objectOutputStream.close()
            fileOutputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadTodos() {
        try {
            val fileInputStream: FileInputStream = openFileInput(todoFile)
            val objectInputStream = ObjectInputStream(fileInputStream)
            val loadedTodos = objectInputStream.readObject() as? MutableMap<String, MutableList<Todo>>
            if (loadedTodos != null) {
                todosByDate.clear()
                todosByDate.putAll(loadedTodos)
            }
            objectInputStream.close()
            fileInputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    public fun dayRepeater(originalTodo: Todo) {
        val calendar = Calendar.getInstance()
        calendar.time = originalTodo.date ?: Date() // Use today's date if originalTodo.date is null
        calendar.add(Calendar.DAY_OF_YEAR, 1)

        for (i in 0 until 90) { // Create todos for 90 days
            val newTodo = originalTodo.copy(
                date = calendar.time,
                day = true, // Reset repeat flags
                week = false,
                month = false,
                isChecked = false
            )
            newTodo.date = calendar.time

            // Convert the Date to String format to use as key
            val dateKey = dateFormat.format(calendar.time)
            val existingTodos = todosByDate.getOrPut(dateKey) { mutableListOf() }

            // Check if a todo with the same title and date already exists
            val isDuplicate = existingTodos.any { it.title == newTodo.title }

            if (!isDuplicate) {
                // Add the new todo only if it doesn't already exist
                existingTodos.add(newTodo)
            } else {
                Log.d("Repeater", "Duplicate todo skipped: ${newTodo.title} for date: $dateKey")
            }
            Log.d("Repeater", "Added todo: ${newTodo.title} for date: $dateKey")

            calendar.add(Calendar.DAY_OF_YEAR, 1) // Move to the next day
        }

        updateTodoListForSelectedDate()
        saveTodos()
    }
    public fun weekRepeater(originalTodo: Todo) {
        val calendar = Calendar.getInstance()
        calendar.time = originalTodo.date ?: Date() // Use today's date if originalTodo.date is null

        // Find the next occurrence of the same day of week
        val originalDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        calendar.add(Calendar.DAY_OF_YEAR, 7) // Start with next week

        for (i in 0 until 12) { // Create todos for 12 weeks (~3 months)
            val newTodo = originalTodo.copy(
                date = calendar.time,
                day = false,
                week = true, // Set weekly repeat flag
                month = false
            )
            newTodo.date = calendar.time

            // Convert the Date to String format to use as key
            val dateKey = dateFormat.format(calendar.time)
            val existingTodos = todosByDate.getOrPut(dateKey) { mutableListOf() }

            // Check if a todo with the same title and date already exists
            val isDuplicate = existingTodos.any { it.title == newTodo.title && it.week }

            if (!isDuplicate) {
                existingTodos.add(newTodo)
                Log.d("WeekRepeater", "Added weekly todo: ${newTodo.title} for date: $dateKey")
            } else {
                Log.d("WeekRepeater", "Duplicate weekly todo skipped: ${newTodo.title} for date: $dateKey")
            }

            calendar.add(Calendar.DAY_OF_YEAR, 7) // Move to the same day next week
        }

        updateTodoListForSelectedDate()
        saveTodos()
    }

    private fun delete() {
        try {
            val currentDate = selectedDate
            val calendar = Calendar.getInstance()
            calendar.time = currentDate

            // Get checked todos from current date
            val currentDateKey = dateFormat.format(currentDate)
            val currentTodos = todosByDate[currentDateKey] ?: mutableListOf()
            val checkedTodos = currentTodos.filter { it.isChecked }

            if (checkedTodos.isEmpty()) {
                Toast.makeText(this, "No checked tasks to delete", Toast.LENGTH_SHORT).show()
                return
            }

            var totalDeleted = 0

            // Delete matching todos for next 100 days
            for (dayOffset in 0..100) {
                calendar.time = currentDate
                calendar.add(Calendar.DAY_OF_YEAR, dayOffset)
                val futureDate = calendar.time
                val dateKey = dateFormat.format(futureDate)

                todosByDate[dateKey]?.let { todos ->
                    val beforeCount = todos.size
                    checkedTodos.forEach { checkedTodo ->
                        todos.removeAll { it.title == checkedTodo.title }
                    }
                    totalDeleted += (beforeCount - todos.size)

                    // Remove date entry if empty
                    if (todos.isEmpty()) {
                        todosByDate.remove(dateKey)
                    }
                }
            }

            updateTodoListForSelectedDate()
            saveTodos()

            Toast.makeText(
                this,
                "Deleted $totalDeleted tasks across 100 days",
                Toast.LENGTH_SHORT
            ).show()

        } catch (e: Exception) {
            Log.e("DeleteChecked", "Error deleting todos", e)
            Toast.makeText(this, "Failed to delete tasks", Toast.LENGTH_SHORT).show()
        }
    }


}
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = intent.getIntExtra("notification_id", 0)
        val title = intent.getStringExtra("title") ?: "Task Reminder"
        val content = intent.getStringExtra("content") ?: "You have a task to complete"

        val builder = NotificationCompat.Builder(context, "todo_reminder_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        notificationManager.notify(notificationId, builder.build())
    }
}