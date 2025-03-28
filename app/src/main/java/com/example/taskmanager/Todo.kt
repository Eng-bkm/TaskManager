package com.example.taskmanager

import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Todo(
    var title: String,
    var isChecked: Boolean = false,
    var from: String? = null,          // Format: "HH:mm" (e.g., "14:30")
    var to: String? = null,            // Format: "HH:mm" (e.g., "15:30")
    var isImportant: Boolean = false,
    var isUrgent: Boolean = false,
    var deadlineDate: String? = null,  // Format: "dd/MM/yyyy" (e.g., "25/12/2023")
    var deadlineTime: String? = null,  // Format: "HH:mm" (e.g., "23:59")
    var reminders: MutableList<String> = mutableListOf(), // Not currently used in notification logic
    var day: Boolean = false,          // For daily repeating tasks
    var week: Boolean = false,         // For weekly repeating tasks
    var month: Boolean = false,        // For monthly repeating tasks
    var date: Date? = null,            // The base date for the task
    var reminderTimeDate: String? = null, // Format: "dd/MM/yyyy" (specific reminder date)
    var reminderTimeTime: String? = null  // Format: "HH:mm" (specific reminder time)
) : Serializable {

    // Helper function to check if this todo has a reminder set
    fun hasReminder(): Boolean {
        return !reminderTimeDate.isNullOrEmpty() && !reminderTimeTime.isNullOrEmpty()
    }

    // Helper function to check if this todo has a deadline set
    fun hasDeadline(): Boolean {
        return !deadlineDate.isNullOrEmpty() && !deadlineTime.isNullOrEmpty()
    }

    // Helper function to check if this todo has a time range set
    fun hasTimeRange(): Boolean {
        return !from.isNullOrEmpty() && !to.isNullOrEmpty()
    }

    // Returns a string representation of the reminder time
    fun getReminderTimeString(): String {
        return if (hasReminder()) {
            "$reminderTimeDate $reminderTimeTime"
        } else if (hasDeadline()) {
            "$deadlineDate $deadlineTime"
        } else if (!from.isNullOrEmpty()) {
            "${dateFormat.format(date ?: Date())} $from"
        } else {
            "No reminder set"
        }
    }

    companion object {
        private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    }
}