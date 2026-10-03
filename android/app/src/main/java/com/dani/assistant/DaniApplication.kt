package com.dani.assistant

import android.app.Application
import com.dani.assistant.core.alarm.AlarmScheduler
import com.dani.assistant.core.alarm.AndroidAlarmScheduler
import com.dani.assistant.core.alarm.NotificationHelper
import com.dani.assistant.core.digest.MorningDigest
import com.dani.assistant.core.database.DaniDatabase
import com.dani.assistant.data.repository.TaskRepository
import com.dani.assistant.data.repository.TaskRepositoryImpl

class DaniApplication : Application() {

    val database: DaniDatabase by lazy {
        DaniDatabase.getDatabase(this)
    }

    val alarmScheduler: AlarmScheduler by lazy {
        AndroidAlarmScheduler(this)
    }

    val taskRepository: TaskRepository by lazy {
        TaskRepositoryImpl(
            taskDao = database.taskDao(),
            reminderDao = database.reminderDao(),
            alarmScheduler = alarmScheduler
        )
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize notification channels at application startup
        NotificationHelper.createNotificationChannels(this)

        // الملخص الصباحي اليومي
        try { MorningDigest.schedule(this) } catch (e: Exception) { }

        // مراقب الإعلانات (WorkManager)
        try { com.dani.assistant.core.watch.AdWatcher.reschedule(this) } catch (e: Exception) { }
    }

    companion object {
        lateinit var instance: DaniApplication
            private set
    }
}
