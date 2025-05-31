package com.example.studentcardsystem

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class AttendanceManager(private val context: Context) {
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences("attendance_settings", Context.MODE_PRIVATE)
    
    private val recentRecords = mutableListOf<AttendanceRecord>()
    
    fun isRecordModeEnabled(): Boolean {
        return sharedPreferences.getBoolean("record_mode_enabled", false)
    }
    
    fun setRecordModeEnabled(enabled: Boolean) {
        sharedPreferences.edit()
            .putBoolean("record_mode_enabled", enabled)
            .apply()
    }
    
    fun getCurrentEvent(): String {
        return sharedPreferences.getString("current_event", "") ?: ""
    }
    
    fun getCurrentRecordType(): String {
        return sharedPreferences.getString("current_record_type", "出席") ?: "出席"
    }
    
    fun setCurrentEvent(eventName: String, recordType: String) {
        sharedPreferences.edit()
            .putString("current_event", eventName)
            .putString("current_record_type", recordType)
            .apply()
    }
    
    fun addRecord(studentId: String): Boolean {
        return try {
            val timestamp = System.currentTimeMillis()
            val eventName = getCurrentEvent()
            val recordType = getCurrentRecordType()
            
            val record = AttendanceRecord(
                studentId = studentId,
                eventName = eventName,
                recordType = recordType,
                timestamp = timestamp
            )
            
            // メモリ内の記録に追加
            recentRecords.add(record)
            
            // CSVファイルに保存
            saveToCsv(record)
            
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    private fun saveToCsv(record: AttendanceRecord) {
        val attendanceDir = File(context.getExternalFilesDir(null), "attendance")
        if (!attendanceDir.exists()) {
            attendanceDir.mkdirs()
        }
        
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val fileName = "${record.eventName}_${dateFormat.format(Date(record.timestamp))}.csv"
        val csvFile = File(attendanceDir, fileName)
        
        val isNewFile = !csvFile.exists()
        
        FileWriter(csvFile, true).use { writer ->
            if (isNewFile) {
                // ヘッダー行を追加
                writer.append("学籍番号,イベント名,記録種別,日時\n")
            }
            
            val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val formattedTime = timeFormat.format(Date(record.timestamp))
            
            writer.append("${record.studentId},${record.eventName},${record.recordType},$formattedTime\n")
        }
    }
    
    fun getRecentRecords(): List<AttendanceRecord> {
        return recentRecords.sortedByDescending { it.timestamp }
    }
    
    fun clearRecentRecords() {
        recentRecords.clear()
    }
    
    fun getCsvFiles(): List<File> {
        val attendanceDir = File(context.getExternalFilesDir(null), "attendance")
        if (!attendanceDir.exists()) {
            return emptyList()
        }
        
        return attendanceDir.listFiles { file -> 
            file.isFile && file.name.endsWith(".csv")
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
}
