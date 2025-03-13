package com.example.taskmanager

import java.io.Serializable
import java.util.Date

data class Todo(
    val title: String,
    var isChecked: Boolean = false,
    var date: Date? = null,
    var deadline: Date? = null,
    var isImportant: Boolean = false,
    var isUrgent: Boolean = false
) : Serializable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Todo

        if (title != other.title) return false
        if (isChecked != other.isChecked) return false
        if (date != other.date) return false
        if (deadline != other.deadline) return false
        if (isImportant != other.isImportant) return false
        if (isUrgent != other.isUrgent) return false

        return true
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + isChecked.hashCode()
        result = 31 * result + (date?.hashCode() ?: 0)
        result = 31 * result + (deadline?.hashCode() ?: 0)
        result = 31 * result + isImportant.hashCode()
        result = 31 * result + isUrgent.hashCode()
        return result
    }
}