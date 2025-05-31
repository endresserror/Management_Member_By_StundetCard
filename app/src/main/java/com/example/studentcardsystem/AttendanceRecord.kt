package com.example.studentcardsystem

import java.text.SimpleDateFormat
import java.util.*

data class AttendanceRecord(
    val studentId: String,
    val eventName: String,
    val recordType: String,
    val timestamp: Long
) {
    override fun toString(): String {
        val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
        val formattedTime = dateFormat.format(Date(timestamp))
        return "$studentId ($eventName - $recordType) [$formattedTime]"
    }
}
