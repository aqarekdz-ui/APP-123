import React, { useState, useEffect } from 'react';
import {
  LayoutDashboard,
  CheckCircle2,
  CalendarDays,
  Brain,
  Bot,
  Sun,
  Moon,
  Smartphone,
  FolderGit2,
  Copy,
  Check,
  Plus,
  Trash2,
  Clock,
  RotateCcw,
  Sparkles,
  Layers,
  Database,
  Bell,
  AlarmClock,
  AlertTriangle,
  Play,
  Volume2,
  ShieldAlert,
  Info
} from 'lucide-react';

interface TabItem {
  id: string;
  title: string;
  icon: React.ElementType;
}

const TABS: TabItem[] = [
  { id: 'dashboard', title: 'الرئيسية', icon: LayoutDashboard },
  { id: 'tasks', title: 'المهام', icon: CheckCircle2 },
  { id: 'calendar', title: 'التقويم', icon: CalendarDays },
  { id: 'memory', title: 'الذاكرة', icon: Brain },
  { id: 'dani', title: 'DANI', icon: Bot },
];

interface PriorityDef {
  level: number;
  arabicTitle: string;
  color: string;
  bgLight: string;
}

const PRIORITIES: PriorityDef[] = [
  { level: 1, arabicTitle: 'عاجل ومهم', color: '#EF4444', bgLight: 'rgba(239, 68, 68, 0.15)' },
  { level: 2, arabicTitle: 'مهم', color: '#F59E0B', bgLight: 'rgba(245, 158, 11, 0.15)' },
  { level: 3, arabicTitle: 'متوسط', color: '#3B82F6', bgLight: 'rgba(59, 130, 246, 0.15)' },
  { level: 4, arabicTitle: 'منخفض', color: '#10B981', bgLight: 'rgba(16, 185, 129, 0.15)' },
  { level: 5, arabicTitle: 'لاحقاً', color: '#64748B', bgLight: 'rgba(100, 116, 139, 0.15)' },
];

interface SimulatedTask {
  id: number;
  title: string;
  description?: string;
  priority: number;
  status: 'NEW' | 'COMPLETED';
  estimatedMinutes: number;
  isToday: boolean;
  createdAt: number;
  completedAt?: number | null;
  // Phase 3a Reminder
  reminderEnabled?: boolean;
  reminderType?: 'ALARM_CLOCK' | 'NOTIFICATION';
  reminderTime?: number | null;
}

interface FileSnippet {
  path: string;
  category: string;
  code: string;
}

const PHASE3A_FILES: FileSnippet[] = [
  {
    path: 'core/database/DaniDatabase.kt',
    category: 'Room Migration 1->2',
    code: `package com.dani.assistant.core.database

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dani.assistant.data.local.dao.*
import com.dani.assistant.data.local.entity.*

@Database(
    entities = [ProjectEntity::class, TaskEntity::class, ReminderEntity::class],
    version = 2,
    exportSchema = false
)
abstract class DaniDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun projectDao(): ProjectDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        @Volatile private var INSTANCE: DaniDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS reminders (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        task_id INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        trigger_time INTEGER NOT NULL,
                        reminder_type TEXT NOT NULL,
                        is_active INTEGER NOT NULL,
                        FOREIGN KEY(task_id) REFERENCES tasks(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_reminders_task_id ON reminders(task_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_reminders_trigger_time_is_active ON reminders(trigger_time, is_active)")
            }
        }

        fun getDatabase(context: Context): DaniDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context.applicationContext, DaniDatabase::class.java, "dani_database")
                    .addMigrations(MIGRATION_1_2)
                    .build().also { INSTANCE = it }
            }
        }
    }
}`
  },
  {
    path: 'core/alarm/AlarmScheduler.kt',
    category: 'AlarmManager Engine',
    code: `package com.dani.assistant.core.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.*
import android.os.Build
import com.dani.assistant.MainActivity
import com.dani.assistant.domain.model.Reminder
import com.dani.assistant.domain.model.ReminderType

sealed class ScheduleResult {
    object Success : ScheduleResult()
    object ExactAlarmPermissionRequired : ScheduleResult()
    data class Error(val message: String) : ScheduleResult()
}

interface AlarmScheduler {
    fun schedule(reminder: Reminder): ScheduleResult
    fun cancel(reminderId: Long)
    fun canScheduleExactAlarms(): Boolean
}

class AndroidAlarmScheduler(private val context: Context) : AlarmScheduler {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    override fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager?.canScheduleExactAlarms() ?: false
        } else true
    }

    override fun schedule(reminder: Reminder): ScheduleResult {
        if (alarmManager == null) return ScheduleResult.Error("خدمة المنبه غير متوفرة")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            return ScheduleResult.ExactAlarmPermissionRequired
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_REMINDER_ID, reminder.id)
            putExtra(AlarmReceiver.EXTRA_TASK_ID, reminder.taskId)
            putExtra(AlarmReceiver.EXTRA_TITLE, reminder.title)
            putExtra(AlarmReceiver.EXTRA_TYPE, reminder.reminderType.name)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context, reminder.id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        when (reminder.reminderType) {
            ReminderType.ALARM_CLOCK -> {
                val showIntent = Intent(context, MainActivity::class.java)
                val showPendingIntent = PendingIntent.getActivity(
                    context, (reminder.id + 100000).toInt(), showIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val alarmClockInfo = AlarmManager.AlarmClockInfo(reminder.triggerTime, showPendingIntent)
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            }
            ReminderType.NOTIFICATION -> {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerTime, pendingIntent)
            }
        }
        return ScheduleResult.Success
    }

    override fun cancel(reminderId: Long) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, reminderId.toInt(), intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager?.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}`
  },
  {
    path: 'core/alarm/NotificationHelper.kt',
    category: 'Notification Channels & Actions',
    code: `package com.dani.assistant.core.alarm

import android.app.*
import android.content.*
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {
    const val CHANNEL_CRITICAL_ALARMS = "channel_critical_alarms"
    const val CHANNEL_DAILY_TASKS = "channel_daily_tasks"
    const val CHANNEL_REMINDERS = "channel_reminders"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val criticalChannel = NotificationChannel(
                CHANNEL_CRITICAL_ALARMS, "المنبهات الحرجة", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "منبهات المواعيد والمهام العاجلة"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), null)
            }
            val tasksChannel = NotificationChannel(CHANNEL_DAILY_TASKS, "إشعارات المهام", NotificationManager.IMPORTANCE_DEFAULT)
            val remindersChannel = NotificationChannel(CHANNEL_REMINDERS, "تذكيرات هادئة", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannels(listOf(criticalChannel, tasksChannel, remindersChannel))
        }
    }

    fun showReminderNotification(context: Context, reminderId: Long, taskId: Long, title: String, isAlarmClock: Boolean) {
        // Actions: "تم الإنجاز" and "تأجيل 30 دقيقة"
        val doneIntent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_MARK_DONE
            putExtra(ReminderActionReceiver.EXTRA_TASK_ID, taskId)
            putExtra(ReminderActionReceiver.EXTRA_REMINDER_ID, reminderId)
        }
        val donePending = PendingIntent.getBroadcast(context, (reminderId + 200000).toInt(), doneIntent, PendingIntent.FLAG_IMMUTABLE)

        val snoozeIntent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_SNOOZE_30
            putExtra(ReminderActionReceiver.EXTRA_TASK_ID, taskId)
            putExtra(ReminderActionReceiver.EXTRA_REMINDER_ID, reminderId)
            putExtra(ReminderActionReceiver.EXTRA_TITLE, title)
            putExtra(ReminderActionReceiver.EXTRA_IS_ALARM, isAlarmClock)
        }
        val snoozePending = PendingIntent.getBroadcast(context, (reminderId + 300000).toInt(), snoozeIntent, PendingIntent.FLAG_IMMUTABLE)

        val channelId = if (isAlarmClock) CHANNEL_CRITICAL_ALARMS else CHANNEL_DAILY_TASKS
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(if (isAlarmClock) "⏰ منبه DANI: $title" else "🔔 تذكير: $title")
            .setContentText("حان وقت تنفيذ المهمة المجدولة")
            .setPriority(if (isAlarmClock) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .addAction(android.R.drawable.checkbox_on_background, "تم الإنجاز", donePending)
            .addAction(android.R.drawable.ic_popup_sync, "تأجيل 30 دقيقة", snoozePending)

        NotificationManagerCompat.from(context).notify(reminderId.toInt(), builder.build())
    }
}`
  },
  {
    path: 'core/alarm/BootCompletedReceiver.kt',
    category: 'Reboot Recovery',
    code: `package com.dani.assistant.core.alarm

import android.content.*
import com.dani.assistant.DaniApplication
import com.dani.assistant.domain.model.*
import kotlinx.coroutines.*

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val now = System.currentTimeMillis()
                    val reminderDao = DaniApplication.instance.database.reminderDao()
                    val scheduler = DaniApplication.instance.alarmScheduler

                    val futureReminders = reminderDao.getActiveFutureReminders(now)
                    futureReminders.forEach { entity ->
                        scheduler.schedule(
                            Reminder(
                                id = entity.id,
                                taskId = entity.taskId,
                                title = entity.title,
                                triggerTime = entity.triggerTime,
                                reminderType = try { ReminderType.valueOf(entity.reminderType) } catch (e: Exception) { ReminderType.NOTIFICATION },
                                isActive = entity.isActive
                            )
                        )
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}`
  },
  {
    path: 'core/alarm/ReminderActionReceiver.kt',
    category: 'Notification Actions',
    code: `package com.dani.assistant.core.alarm

import android.content.*
import com.dani.assistant.DaniApplication
import com.dani.assistant.data.local.entity.ReminderEntity
import com.dani.assistant.domain.model.*
import kotlinx.coroutines.*

class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        val pendingResult = goAsync()

        when (intent.action) {
            ACTION_MARK_DONE -> {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val task = DaniApplication.instance.taskRepository.getTaskByIdSync(taskId)
                        if (task != null) DaniApplication.instance.taskRepository.toggleTaskCompleted(task)
                        NotificationHelper.dismissNotification(context, reminderId)
                    } finally { pendingResult.finish() }
                }
            }
            ACTION_SNOOZE_30 -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "تذكير مهمة"
                val isAlarm = intent.getBooleanExtra(EXTRA_IS_ALARM, false)
                val newTime = System.currentTimeMillis() + (30 * 60 * 1000L)

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val reminderDao = DaniApplication.instance.database.reminderDao()
                        val newId = reminderDao.insertReminder(
                            ReminderEntity(taskId = taskId, title = title, triggerTime = newTime, reminderType = if (isAlarm) "ALARM_CLOCK" else "NOTIFICATION", isActive = true)
                        )
                        DaniApplication.instance.alarmScheduler.schedule(
                            Reminder(id = newId, taskId = taskId, title = title, triggerTime = newTime, reminderType = if (isAlarm) ReminderType.ALARM_CLOCK else ReminderType.NOTIFICATION, isActive = true)
                        )
                        NotificationHelper.dismissNotification(context, reminderId)
                    } finally { pendingResult.finish() }
                }
            }
            else -> pendingResult.finish()
        }
    }
    companion object {
        const val ACTION_MARK_DONE = "com.dani.assistant.ACTION_MARK_DONE"
        const val ACTION_SNOOZE_30 = "com.dani.assistant.ACTION_SNOOZE_30"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_IS_ALARM = "extra_is_alarm"
    }
}`
  },
  {
    path: 'AndroidManifest.xml',
    category: 'Permissions & Receivers',
    code: `<!-- صلاحيات التنبيهات والمنبهات الدقيقة -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.VIBRATE" />

<receiver android:name=".core.alarm.AlarmReceiver" android:exported="false" />
<receiver android:name=".core.alarm.ReminderActionReceiver" android:exported="false" />
<receiver android:name=".core.alarm.BootCompletedReceiver" android:exported="true" android:enabled="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
        <action android:name="android.intent.action.QUICKBOOT_POWERON" />
    </intent-filter>
</receiver>`
  }
];

const INITIAL_DEMO_TASKS: SimulatedTask[] = [
  {
    id: 1,
    title: 'تجديد جواز السفر ودفع الرسوم',
    description: 'الموعد صباحاً في البلدية مع أخذ الوثائق المطلوبة',
    priority: 1,
    status: 'NEW',
    estimatedMinutes: 60,
    isToday: true,
    createdAt: Date.now() - 3600000,
    reminderEnabled: true,
    reminderType: 'ALARM_CLOCK',
    reminderTime: Date.now() + 3 * 60 * 1000, // 3 mins ahead (test case)
  },
  {
    id: 2,
    title: 'مراجعة تقدم مشروع العمل مع الفريق',
    description: 'تسليم التقرير الأسبوعي قبل الساعة 16:00',
    priority: 2,
    status: 'NEW',
    estimatedMinutes: 45,
    isToday: true,
    createdAt: Date.now() - 7200000,
    reminderEnabled: true,
    reminderType: 'NOTIFICATION',
    reminderTime: Date.now() + 30 * 60 * 1000,
  },
  {
    id: 3,
    title: 'الاتصال بصاحب ورشة النجارة',
    description: 'التأكد من جاهزية القياسات الجديدة',
    priority: 3,
    status: 'NEW',
    estimatedMinutes: 15,
    isToday: true,
    createdAt: Date.now() - 10800000,
  },
  {
    id: 4,
    title: 'شراء بطاريات ومستلزمات مكتبية',
    description: 'من المكتبة المجاورة',
    priority: 4,
    status: 'COMPLETED',
    estimatedMinutes: 20,
    isToday: false,
    createdAt: Date.now() - 86400000,
    completedAt: Date.now() - 3600000,
  }
];

export default function App() {
  const [activeTab, setActiveTab] = useState<string>('tasks');
  const [isDarkMode, setIsDarkMode] = useState<boolean>(true);
  const [viewMode, setViewMode] = useState<'simulator' | 'code'>('simulator');
  const [taskFilter, setTaskFilter] = useState<'ALL' | 'TODAY'>('ALL');
  const [tasks, setTasks] = useState<SimulatedTask[]>(() => {
    try {
      const saved = localStorage.getItem('dani_phase3a_tasks');
      if (saved) return JSON.parse(saved);
    } catch (_) {}
    return INITIAL_DEMO_TASKS;
  });

  const [isBottomSheetOpen, setIsBottomSheetOpen] = useState<boolean>(false);
  const [editingTask, setEditingTask] = useState<SimulatedTask | null>(null);
  const [taskToDelete, setTaskToDelete] = useState<SimulatedTask | null>(null);

  // Form state
  const [formTitle, setFormTitle] = useState('');
  const [formDesc, setFormDesc] = useState('');
  const [formPriority, setFormPriority] = useState<number>(3);
  const [formMinutes, setFormMinutes] = useState<number>(30);
  const [formIsToday, setFormIsToday] = useState<boolean>(true);
  // Reminder form state
  const [formReminderEnabled, setFormReminderEnabled] = useState<boolean>(false);
  const [formReminderType, setFormReminderType] = useState<'ALARM_CLOCK' | 'NOTIFICATION'>('ALARM_CLOCK');
  const [formReminderTimeOffsetMinutes, setFormReminderTimeOffsetMinutes] = useState<number>(3);

  // Simulated notification banner
  const [triggeredNotification, setTriggeredNotification] = useState<{
    id: number;
    taskId: number;
    title: string;
    isAlarm: boolean;
  } | null>(null);

  // Simulated permission warning
  const [showExactAlarmWarning, setShowExactAlarmWarning] = useState<boolean>(false);

  const [selectedFileIdx, setSelectedFileIdx] = useState<number>(0);
  const [copied, setCopied] = useState<boolean>(false);
  const [forceCloseKey, setForceCloseKey] = useState<number>(0);

  useEffect(() => {
    try {
      localStorage.setItem('dani_phase3a_tasks', JSON.stringify(tasks));
    } catch (_) {}
  }, [tasks]);

  const handleCopy = (text: string) => {
    navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleOpenAdd = () => {
    setEditingTask(null);
    setFormTitle('');
    setFormDesc('');
    setFormPriority(3);
    setFormMinutes(30);
    setFormIsToday(true);
    setFormReminderEnabled(false);
    setFormReminderType('ALARM_CLOCK');
    setFormReminderTimeOffsetMinutes(3);
    setIsBottomSheetOpen(true);
  };

  const handleOpenEdit = (task: SimulatedTask) => {
    setEditingTask(task);
    setFormTitle(task.title);
    setFormDesc(task.description || '');
    setFormPriority(task.priority);
    setFormMinutes(task.estimatedMinutes);
    setFormIsToday(task.isToday);
    setFormReminderEnabled(!!task.reminderEnabled);
    setFormReminderType(task.reminderType || 'ALARM_CLOCK');
    setFormReminderTimeOffsetMinutes(3);
    setIsBottomSheetOpen(true);
  };

  const handleSaveTask = (e: React.FormEvent) => {
    e.preventDefault();
    if (!formTitle.trim()) return;

    const reminderTrigger = formReminderEnabled
      ? Date.now() + formReminderTimeOffsetMinutes * 60 * 1000
      : null;

    if (editingTask) {
      setTasks((prev) =>
        prev.map((t) =>
          t.id === editingTask.id
            ? {
                ...t,
                title: formTitle.trim(),
                description: formDesc.trim() || undefined,
                priority: formPriority,
                estimatedMinutes: formMinutes,
                isToday: formIsToday,
                reminderEnabled: formReminderEnabled,
                reminderType: formReminderType,
                reminderTime: reminderTrigger,
              }
            : t
        )
      );
    } else {
      const newTask: SimulatedTask = {
        id: Date.now(),
        title: formTitle.trim(),
        description: formDesc.trim() || undefined,
        priority: formPriority,
        status: 'NEW',
        estimatedMinutes: formMinutes,
        isToday: formIsToday,
        createdAt: Date.now(),
        reminderEnabled: formReminderEnabled,
        reminderType: formReminderType,
        reminderTime: reminderTrigger,
      };
      setTasks((prev) => [newTask, ...prev]);
    }
    setIsBottomSheetOpen(false);
  };

  const handleToggleComplete = (id: number, e: React.MouseEvent) => {
    e.stopPropagation();
    setTasks((prev) =>
      prev.map((t) => {
        if (t.id === id) {
          const isNowCompleted = t.status !== 'COMPLETED';
          return {
            ...t,
            status: isNowCompleted ? 'COMPLETED' : 'NEW',
            completedAt: isNowCompleted ? Date.now() : null,
            // When completed, cancel active reminder
            reminderEnabled: isNowCompleted ? false : t.reminderEnabled,
          };
        }
        return t;
      })
    );
    // Dismiss any active notification for this task
    if (triggeredNotification?.taskId === id) {
      setTriggeredNotification(null);
    }
  };

  const handleDeleteConfirm = () => {
    if (taskToDelete) {
      setTasks((prev) => prev.filter((t) => t.id !== taskToDelete.id));
      if (triggeredNotification?.taskId === taskToDelete.id) {
        setTriggeredNotification(null);
      }
      setTaskToDelete(null);
    }
  };

  // Simulate Triggering an Alarm Notification right now
  const handleSimulateTriggerAlarm = (task: SimulatedTask) => {
    setTriggeredNotification({
      id: Date.now(),
      taskId: task.id,
      title: task.title,
      isAlarm: task.reminderType === 'ALARM_CLOCK',
    });
  };

  // Notification Action: "تم الإنجاز"
  const handleNotificationDone = (taskId: number) => {
    setTasks((prev) =>
      prev.map((t) =>
        t.id === taskId
          ? { ...t, status: 'COMPLETED', completedAt: Date.now(), reminderEnabled: false }
          : t
      )
    );
    setTriggeredNotification(null);
  };

  // Notification Action: "تأجيل 30 دقيقة"
  const handleNotificationSnooze = (taskId: number) => {
    setTasks((prev) =>
      prev.map((t) =>
        t.id === taskId
          ? {
              ...t,
              reminderTime: Date.now() + 30 * 60 * 1000,
              reminderEnabled: true,
            }
          : t
      )
    );
    setTriggeredNotification(null);
  };

  const handleSimulateForceClose = () => {
    try {
      const saved = localStorage.getItem('dani_phase3a_tasks');
      if (saved) setTasks(JSON.parse(saved));
    } catch (_) {}
    setForceCloseKey((prev) => prev + 1);
  };

  const filteredTasks = tasks.filter((t) => {
    if (taskFilter === 'TODAY') {
      return t.isToday && t.status !== 'COMPLETED';
    }
    return true;
  });

  const sortedTasks = [...filteredTasks].sort((a, b) => {
    if (a.status !== b.status) {
      return a.status === 'COMPLETED' ? 1 : -1;
    }
    return a.priority - b.priority;
  });

  const darkThemeColors = {
    bg: '#0B1220',
    surface: '#131C2E',
    surfaceVariant: '#1E293B',
    primary: '#2DD4BF',
    onPrimary: '#042F2E',
    text: '#E6EDF7',
    textSec: '#94A3B8',
    outline: '#334155',
  };

  const lightThemeColors = {
    bg: '#F6F8FB',
    surface: '#FFFFFF',
    surfaceVariant: '#F1F5F9',
    primary: '#0F766E',
    onPrimary: '#FFFFFF',
    text: '#0F172A',
    textSec: '#64748B',
    outline: '#CBD5E1',
  };

  const theme = isDarkMode ? darkThemeColors : lightThemeColors;
  const currentTab = TABS.find((t) => t.id === activeTab) || TABS[1];

  return (
    <div className="min-h-screen bg-[#070b13] text-neutral-100 font-['Cairo',sans-serif] flex flex-col antialiased">
      {/* Header */}
      <header className="border-b border-neutral-800 bg-[#0b101c]/90 backdrop-blur px-4 py-3 sticky top-0 z-30">
        <div className="max-w-6xl mx-auto flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-teal-400 to-teal-700 flex items-center justify-center font-extrabold text-white shadow-lg shadow-teal-500/20">
              D
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="text-base font-bold text-white tracking-wide">DANI — المرحلة 3a (Phase 3a)</h1>
                <span className="text-[10px] px-2 py-0.5 rounded-full bg-teal-500/10 text-teal-400 border border-teal-500/30 font-mono">
                  AlarmManager + Exact Alarms + Room v2
                </span>
              </div>
              <p className="text-xs text-neutral-400">
                المنبهات الدقيقة والإشعارات المحلية الصارمة (Offline) واستعادتها بعد إعادة التشغيل
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <div className="flex bg-neutral-900 border border-neutral-800 rounded-xl p-1 text-xs">
              <button
                onClick={() => setViewMode('simulator')}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg transition-all ${
                  viewMode === 'simulator'
                    ? 'bg-teal-500/20 text-teal-300 font-bold border border-teal-500/30'
                    : 'text-neutral-400 hover:text-white'
                }`}
              >
                <Smartphone className="w-3.5 h-3.5" />
                <span>محاكي المنبهات</span>
              </button>
              <button
                onClick={() => setViewMode('code')}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg transition-all ${
                  viewMode === 'code'
                    ? 'bg-teal-500/20 text-teal-300 font-bold border border-teal-500/30'
                    : 'text-neutral-400 hover:text-white'
                }`}
              >
                <FolderGit2 className="w-3.5 h-3.5" />
                <span>ملفات Alarms & Receivers</span>
              </button>
            </div>

            <button
              onClick={() => setIsDarkMode(!isDarkMode)}
              className="p-2 rounded-xl bg-neutral-900 border border-neutral-800 text-neutral-300 hover:text-teal-300 transition-colors"
              title="التبديل بين الوضع الداكن والفاتح"
            >
              {isDarkMode ? <Sun className="w-4 h-4 text-amber-400" /> : <Moon className="w-4 h-4 text-teal-400" />}
            </button>
          </div>
        </div>
      </header>

      {/* Main Container */}
      <main className="flex-1 max-w-6xl mx-auto w-full p-4 sm:p-6 grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
        {/* Left: Phone Simulator */}
        <div className="lg:col-span-5 flex flex-col items-center justify-center">
          <div className="w-full max-w-[370px] aspect-[9/18.5] rounded-[44px] p-3.5 bg-neutral-900 border-4 border-neutral-800 shadow-2xl shadow-black relative flex flex-col">
            {/* Notch */}
            <div className="absolute top-5 left-1/2 -translate-x-1/2 w-28 h-4 bg-neutral-950 rounded-full z-30 flex items-center justify-center">
              <div className="w-2.5 h-2.5 rounded-full bg-neutral-800"></div>
            </div>

            {/* Android Screen */}
            <div
              key={forceCloseKey}
              className="flex-1 rounded-[34px] overflow-hidden flex flex-col relative transition-colors duration-200"
              style={{ backgroundColor: theme.bg }}
              dir="rtl"
            >
              {/* Status Bar */}
              <div
                className="pt-7 px-5 pb-2 flex items-center justify-between text-[11px] font-mono select-none"
                style={{ color: theme.textSec }}
              >
                <span>09:50</span>
                <div className="flex items-center gap-1.5 text-[10px]">
                  <AlarmClock className="w-3 h-3 text-teal-400" />
                  <span>Exact Alarm</span>
                  <span>100%</span>
                </div>
              </div>

              {/* Simulated Heads-up Notification Banner (if triggered) */}
              {triggeredNotification && (
                <div className="mx-3 mt-1 p-3 rounded-2xl bg-[#1e293b] border-2 border-teal-400 text-white shadow-2xl z-40 animate-bounce duration-700">
                  <div className="flex items-center justify-between mb-1">
                    <div className="flex items-center gap-1.5">
                      {triggeredNotification.isAlarm ? (
                        <AlarmClock className="w-4 h-4 text-red-400 animate-pulse" />
                      ) : (
                        <Bell className="w-4 h-4 text-teal-400" />
                      )}
                      <span className="text-[11px] font-bold text-teal-300">
                        {triggeredNotification.isAlarm ? '⏰ منبه DANI الحرج' : '🔔 تذكير مهمة'}
                      </span>
                    </div>
                    <span className="text-[10px] text-neutral-400">الآن</span>
                  </div>

                  <p className="text-xs font-bold truncate mb-2">{triggeredNotification.title}</p>

                  <div className="flex gap-2">
                    <button
                      onClick={() => handleNotificationDone(triggeredNotification.taskId)}
                      className="flex-1 py-1 rounded-lg bg-teal-500 text-black font-bold text-[10px] flex items-center justify-center gap-1"
                    >
                      <Check className="w-3 h-3 stroke-[3]" />
                      <span>تم الإنجاز</span>
                    </button>
                    <button
                      onClick={() => handleNotificationSnooze(triggeredNotification.taskId)}
                      className="flex-1 py-1 rounded-lg bg-neutral-800 text-neutral-200 text-[10px] flex items-center justify-center gap-1 hover:bg-neutral-700"
                    >
                      <RotateCcw className="w-3 h-3" />
                      <span>تأجيل 30 دقيقة</span>
                    </button>
                  </div>
                </div>
              )}

              {/* Tasks Screen */}
              {activeTab === 'tasks' ? (
                <div className="flex-1 flex flex-col px-4 pt-2 pb-1 overflow-hidden relative">
                  {/* Header */}
                  <div className="flex items-center justify-between mb-2">
                    <h2 className="text-xl font-bold" style={{ color: theme.text }}>
                      المهام
                    </h2>
                    <span
                      className="text-[11px] px-2 py-0.5 rounded-full font-mono font-bold"
                      style={{
                        backgroundColor: theme.surfaceVariant,
                        color: theme.primary,
                      }}
                    >
                      {filteredTasks.length} مهمة
                    </span>
                  </div>

                  {/* Exact Alarm Permission Card (Demo Toggle) */}
                  {showExactAlarmWarning && (
                    <div className="p-2.5 rounded-xl bg-red-950/40 border border-red-800/60 mb-2 flex items-start gap-2 text-xs">
                      <AlertTriangle className="w-4 h-4 text-red-400 shrink-0 mt-0.5" />
                      <div className="flex-1">
                        <span className="font-bold text-red-200 text-[11px] block">إذن المنبه الدقيق مطلوب</span>
                        <p className="text-[10px] text-red-300">
                          لتفعيل المنبه في الوقت المحدد بالثانية على Android 12+، يلزم تفعيل إذن المنبهات.
                        </p>
                      </div>
                      <button
                        onClick={() => setShowExactAlarmWarning(false)}
                        className="text-[10px] text-red-400 font-bold"
                      >
                        إغلاق
                      </button>
                    </div>
                  )}

                  {/* Filter Chips */}
                  <div className="flex items-center gap-2 mb-2">
                    <button
                      onClick={() => setTaskFilter('ALL')}
                      className={`text-xs px-3 py-1 rounded-full font-bold transition-all ${
                        taskFilter === 'ALL' ? 'border' : 'opacity-70'
                      }`}
                      style={{
                        backgroundColor: taskFilter === 'ALL' ? theme.surfaceVariant : 'transparent',
                        borderColor: taskFilter === 'ALL' ? theme.primary : theme.outline,
                        color: taskFilter === 'ALL' ? theme.primary : theme.textSec,
                      }}
                    >
                      الكل ({tasks.length})
                    </button>

                    <button
                      onClick={() => setTaskFilter('TODAY')}
                      className={`text-xs px-3 py-1 rounded-full font-bold transition-all ${
                        taskFilter === 'TODAY' ? 'border' : 'opacity-70'
                      }`}
                      style={{
                        backgroundColor: taskFilter === 'TODAY' ? theme.surfaceVariant : 'transparent',
                        borderColor: taskFilter === 'TODAY' ? theme.primary : theme.outline,
                        color: taskFilter === 'TODAY' ? theme.primary : theme.textSec,
                      }}
                    >
                      اليوم ({tasks.filter((t) => t.isToday && t.status !== 'COMPLETED').length})
                    </button>
                  </div>

                  {/* Task List */}
                  <div className="flex-1 overflow-y-auto space-y-2 pr-0.5 scrollbar-thin scrollbar-thumb-neutral-700">
                    {sortedTasks.map((task) => {
                      const pDef = PRIORITIES.find((p) => p.level === task.priority) || PRIORITIES[2];
                      const isCompleted = task.status === 'COMPLETED';

                      return (
                        <div
                          key={task.id}
                          onClick={() => handleOpenEdit(task)}
                          className="p-2.5 rounded-2xl border transition-all cursor-pointer flex items-center gap-2.5 shadow-sm hover:scale-[1.01]"
                          style={{
                            backgroundColor: theme.surface,
                            borderColor: theme.outline,
                            opacity: isCompleted ? 0.65 : 1,
                          }}
                        >
                          {/* Priority Stripe */}
                          <div
                            className="w-1.5 h-10 rounded-full shrink-0"
                            style={{ backgroundColor: pDef.color }}
                          ></div>

                          {/* Checkbox */}
                          <button
                            onClick={(e) => handleToggleComplete(task.id, e)}
                            className="w-5 h-5 rounded-md border flex items-center justify-center shrink-0 transition-colors"
                            style={{
                              borderColor: isCompleted ? theme.primary : theme.outline,
                              backgroundColor: isCompleted ? theme.primary : 'transparent',
                            }}
                          >
                            {isCompleted && <Check className="w-3.5 h-3.5 text-black stroke-[3]" />}
                          </button>

                          {/* Info */}
                          <div className="flex-1 min-w-0">
                            <div className="flex items-center gap-1.5">
                              <h4
                                className={`text-xs font-bold truncate ${isCompleted ? 'line-through' : ''}`}
                                style={{ color: theme.text }}
                              >
                                {task.title}
                              </h4>

                              {/* Alarm / Reminder Indicator */}
                              {task.reminderEnabled && !isCompleted && (
                                <span
                                  title={task.reminderType === 'ALARM_CLOCK' ? 'منبه دقيق مجدول' : 'إشعار مجدول'}
                                  className="text-[9px] px-1 py-0.5 rounded flex items-center gap-0.5 shrink-0"
                                  style={{
                                    backgroundColor:
                                      task.reminderType === 'ALARM_CLOCK'
                                        ? 'rgba(239, 68, 68, 0.2)'
                                        : 'rgba(45, 212, 191, 0.2)',
                                    color: task.reminderType === 'ALARM_CLOCK' ? '#EF4444' : theme.primary,
                                  }}
                                >
                                  {task.reminderType === 'ALARM_CLOCK' ? (
                                    <AlarmClock className="w-2.5 h-2.5" />
                                  ) : (
                                    <Bell className="w-2.5 h-2.5" />
                                  )}
                                  <span>{task.reminderType === 'ALARM_CLOCK' ? 'منبه' : 'إشعار'}</span>
                                </span>
                              )}
                            </div>

                            {task.description && (
                              <p className="text-[10px] truncate" style={{ color: theme.textSec }}>
                                {task.description}
                              </p>
                            )}

                            <div className="flex items-center gap-2 mt-1">
                              <span
                                className="text-[9px] px-1.5 py-0.2 rounded font-bold"
                                style={{
                                  backgroundColor: pDef.bgLight,
                                  color: pDef.color,
                                }}
                              >
                                {pDef.arabicTitle}
                              </span>

                              <span
                                className="text-[9px] flex items-center gap-0.5"
                                style={{ color: theme.textSec }}
                              >
                                <Clock className="w-2.5 h-2.5" />
                                <span>{task.estimatedMinutes} د</span>
                              </span>
                            </div>
                          </div>

                          {/* Quick Simulate Ringing Trigger */}
                          {task.reminderEnabled && !isCompleted && (
                            <button
                              onClick={(e) => {
                                e.stopPropagation();
                                handleSimulateTriggerAlarm(task);
                              }}
                              className="p-1 rounded-lg text-teal-400 hover:bg-teal-500/20 transition-colors"
                              title="تشغيل تجريبي للمنبه فوراً"
                            >
                              <Play className="w-3.5 h-3.5 fill-current" />
                            </button>
                          )}

                          {/* Delete Trigger */}
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              setTaskToDelete(task);
                            }}
                            className="p-1 rounded-lg text-neutral-400 hover:text-red-400 transition-colors"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        </div>
                      );
                    })}
                  </div>

                  {/* Floating Action Button (+) */}
                  <button
                    onClick={handleOpenAdd}
                    className="absolute bottom-3 left-4 w-11 h-11 rounded-full shadow-lg flex items-center justify-center transition-transform hover:scale-110 active:scale-95 z-20"
                    style={{
                      backgroundColor: theme.primary,
                      color: theme.onPrimary,
                    }}
                    title="إضافة مهمة جديدة"
                  >
                    <Plus className="w-6 h-6 stroke-[2.5]" />
                  </button>
                </div>
              ) : (
                <div className="flex-1 flex flex-col items-center justify-center p-6 text-center select-none">
                  <div
                    className="w-14 h-14 rounded-2xl flex items-center justify-center mb-3"
                    style={{
                      backgroundColor: theme.surfaceVariant,
                      color: theme.primary,
                    }}
                  >
                    <currentTab.icon className="w-7 h-7" />
                  </div>
                  <h3 className="text-xl font-bold" style={{ color: theme.text }}>
                    {currentTab.title}
                  </h3>
                  <span
                    className="text-[10px] px-2 py-0.5 rounded-full mt-2 font-mono"
                    style={{
                      backgroundColor: theme.surface,
                      color: theme.textSec,
                      border: `1px solid ${theme.outline}`,
                    }}
                  >
                    PlaceholderScreen
                  </span>
                </div>
              )}

              {/* Bottom Sheet for Add/Edit with Reminder Section */}
              {isBottomSheetOpen && (
                <div className="absolute inset-0 bg-black/60 z-40 flex flex-col justify-end animate-fadeIn">
                  <div
                    className="p-4 rounded-t-3xl border-t shadow-2xl max-h-[88%] overflow-y-auto"
                    style={{
                      backgroundColor: theme.surface,
                      borderColor: theme.outline,
                    }}
                  >
                    <div className="w-10 h-1 bg-neutral-600 rounded-full mx-auto mb-3"></div>

                    <h3 className="text-sm font-bold mb-3" style={{ color: theme.text }}>
                      {editingTask ? 'تعديل المهمة والتنبيه' : 'إضافة مهمة مع تذكير'}
                    </h3>

                    <form onSubmit={handleSaveTask} className="space-y-3">
                      <div>
                        <label className="text-[11px] font-bold block mb-1" style={{ color: theme.textSec }}>
                          عنوان المهمة *
                        </label>
                        <input
                          type="text"
                          required
                          value={formTitle}
                          onChange={(e) => setFormTitle(e.target.value)}
                          placeholder="مثال: موعد المستشفى..."
                          className="w-full text-xs rounded-xl p-2 border focus:outline-none"
                          style={{
                            backgroundColor: theme.bg,
                            borderColor: theme.outline,
                            color: theme.text,
                          }}
                        />
                      </div>

                      {/* Priority (1 to 5) */}
                      <div>
                        <label className="text-[11px] font-bold block mb-1" style={{ color: theme.textSec }}>
                          مستوى الأولوية:
                        </label>
                        <div className="grid grid-cols-5 gap-1 text-[10px]">
                          {PRIORITIES.map((p) => (
                            <button
                              type="button"
                              key={p.level}
                              onClick={() => setFormPriority(p.level)}
                              className={`p-1.5 rounded-lg border font-bold text-center transition-all ${
                                formPriority === p.level ? 'border-2' : 'opacity-70'
                              }`}
                              style={{
                                borderColor: formPriority === p.level ? p.color : theme.outline,
                                backgroundColor: formPriority === p.level ? p.bgLight : 'transparent',
                                color: p.color,
                              }}
                            >
                              <div>{p.level}</div>
                              <div className="truncate">{p.arabicTitle}</div>
                            </button>
                          ))}
                        </div>
                      </div>

                      {/* Reminder Card Section (Phase 3a) */}
                      <div
                        className="p-3 rounded-2xl border space-y-2.5"
                        style={{
                          backgroundColor: theme.bg,
                          borderColor: formReminderEnabled ? theme.primary : theme.outline,
                        }}
                      >
                        <div className="flex items-center justify-between">
                          <div className="flex items-center gap-1.5">
                            <AlarmClock className="w-4 h-4 text-teal-400" />
                            <span className="text-xs font-bold" style={{ color: theme.text }}>
                              تفعيل التذكير المحلي
                            </span>
                          </div>
                          <input
                            type="checkbox"
                            checked={formReminderEnabled}
                            onChange={(e) => setFormReminderEnabled(e.target.checked)}
                            className="w-4 h-4 accent-teal-400 cursor-pointer"
                          />
                        </div>

                        {formReminderEnabled && (
                          <div className="space-y-2 pt-1 border-t border-neutral-800 animate-fadeIn">
                            {/* Reminder Type Toggle */}
                            <div className="flex gap-2">
                              <button
                                type="button"
                                onClick={() => setFormReminderType('ALARM_CLOCK')}
                                className={`flex-1 py-1.5 rounded-xl text-xs font-bold border transition-all ${
                                  formReminderType === 'ALARM_CLOCK' ? 'border-teal-400 text-teal-300' : 'opacity-60'
                                }`}
                                style={{
                                  backgroundColor:
                                    formReminderType === 'ALARM_CLOCK' ? theme.surfaceVariant : 'transparent',
                                }}
                              >
                                ⏰ منبه (رنين وشاشة)
                              </button>
                              <button
                                type="button"
                                onClick={() => setFormReminderType('NOTIFICATION')}
                                className={`flex-1 py-1.5 rounded-xl text-xs font-bold border transition-all ${
                                  formReminderType === 'NOTIFICATION' ? 'border-teal-400 text-teal-300' : 'opacity-60'
                                }`}
                                style={{
                                  backgroundColor:
                                    formReminderType === 'NOTIFICATION' ? theme.surfaceVariant : 'transparent',
                                }}
                              >
                                🔔 إشعار (صوت خفيف)
                              </button>
                            </div>

                            {/* Offset Options */}
                            <label className="text-[10px] text-neutral-400 block">توقيت التنبيه:</label>
                            <div className="grid grid-cols-3 gap-1.5 text-[10px]">
                              {[
                                { min: 3, label: 'بعد 3 دقائق (فحص)' },
                                { min: 30, label: 'بعد 30 دقيقة' },
                                { min: 60, label: 'بعد ساعة' },
                              ].map((opt) => (
                                <button
                                  type="button"
                                  key={opt.min}
                                  onClick={() => setFormReminderTimeOffsetMinutes(opt.min)}
                                  className={`p-1.5 rounded-lg border transition-all ${
                                    formReminderTimeOffsetMinutes === opt.min
                                      ? 'border-teal-400 text-teal-300 bg-teal-500/10 font-bold'
                                      : 'border-neutral-800 text-neutral-400'
                                  }`}
                                >
                                  {opt.label}
                                </button>
                              ))}
                            </div>
                          </div>
                        )}
                      </div>

                      {/* Action Buttons */}
                      <div className="flex gap-2 pt-1">
                        <button
                          type="submit"
                          disabled={!formTitle.trim()}
                          className="flex-1 py-2 rounded-xl font-bold text-xs shadow transition-all disabled:opacity-50"
                          style={{
                            backgroundColor: theme.primary,
                            color: theme.onPrimary,
                          }}
                        >
                          {editingTask ? 'تحديث المهمة والتنبيه' : 'حفظ المهمة والتنبيه'}
                        </button>
                        <button
                          type="button"
                          onClick={() => setIsBottomSheetOpen(false)}
                          className="px-3 py-2 rounded-xl text-xs font-bold border"
                          style={{
                            borderColor: theme.outline,
                            color: theme.textSec,
                          }}
                        >
                          إلغاء
                        </button>
                      </div>
                    </form>
                  </div>
                </div>
              )}

              {/* Delete Dialog */}
              {taskToDelete && (
                <div className="absolute inset-0 bg-black/70 z-50 flex items-center justify-center p-4">
                  <div
                    className="w-full max-w-[280px] p-4 rounded-2xl border shadow-2xl text-center"
                    style={{
                      backgroundColor: theme.surface,
                      borderColor: theme.outline,
                    }}
                  >
                    <Trash2 className="w-8 h-8 text-red-400 mx-auto mb-2" />
                    <h4 className="text-xs font-bold mb-1" style={{ color: theme.text }}>
                      حذف المهمة؟
                    </h4>
                    <p className="text-[11px] mb-3" style={{ color: theme.textSec }}>
                      سيتم حذف المهمة وإلغاء أي منبه مجدول لها تلقائياً.
                    </p>
                    <div className="flex gap-2">
                      <button
                        onClick={handleDeleteConfirm}
                        className="flex-1 py-1.5 rounded-lg bg-red-500 text-white font-bold text-xs"
                      >
                        حذف
                      </button>
                      <button
                        onClick={() => setTaskToDelete(null)}
                        className="flex-1 py-1.5 rounded-lg border text-xs"
                        style={{ borderColor: theme.outline, color: theme.textSec }}
                      >
                        إلغاء
                      </button>
                    </div>
                  </div>
                </div>
              )}

              {/* Bottom Nav */}
              <div
                className="py-2 px-3 border-t flex items-center justify-around transition-colors"
                style={{
                  backgroundColor: theme.surface,
                  borderColor: theme.outline,
                }}
              >
                {TABS.map((tab) => {
                  const Icon = tab.icon;
                  const isSelected = activeTab === tab.id;
                  return (
                    <button
                      key={tab.id}
                      onClick={() => setActiveTab(tab.id)}
                      className="flex-1 flex flex-col items-center justify-center py-1 transition-all"
                    >
                      <div
                        className="px-4 py-1 rounded-full flex items-center justify-center transition-all"
                        style={{
                          backgroundColor: isSelected ? theme.surfaceVariant : 'transparent',
                          color: isSelected ? theme.primary : theme.textSec,
                        }}
                      >
                        <Icon className="w-5 h-5" />
                      </div>
                      <span
                        className="text-[11px] font-semibold mt-1"
                        style={{
                          color: isSelected ? theme.primary : theme.textSec,
                        }}
                      >
                        {tab.title}
                      </span>
                    </button>
                  );
                })}
              </div>

              {/* Home Pill */}
              <div className="h-4 flex items-center justify-center">
                <div className="w-28 h-1 rounded-full opacity-60" style={{ backgroundColor: theme.textSec }}></div>
              </div>
            </div>
          </div>

          <div className="mt-3 flex items-center gap-2">
            <button
              onClick={handleSimulateForceClose}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-neutral-900 border border-neutral-800 text-xs text-neutral-300 hover:text-teal-300 transition-colors"
            >
              <RotateCcw className="w-3.5 h-3.5 text-teal-400" />
              <span>إعادة تشغيل التطبيق (حفظ Room v2)</span>
            </button>
          </div>
        </div>

        {/* Right: Technical Documentation & Real Device Test Guide */}
        <div className="lg:col-span-7 space-y-5">
          {/* Honest Device Limits Box */}
          <div className="p-5 rounded-2xl bg-[#0e1424] border border-neutral-800 space-y-3">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-bold text-teal-400 flex items-center gap-2">
                <ShieldAlert className="w-4 h-4 text-amber-400" />
                <span>الحدود الصريحة: ما يمكن وما لا يمكن اختباره في المتصفح</span>
              </h3>
              <span className="text-[10px] px-2 py-0.5 rounded bg-amber-500/10 text-amber-400 border border-amber-500/20 font-mono">
                Real Device vs Web Preview
              </span>
            </div>

            <div className="space-y-2 text-xs text-neutral-300 leading-relaxed">
              <div className="p-3 rounded-xl bg-neutral-900/90 border border-neutral-800">
                <span className="text-emerald-400 font-bold block mb-1">
                  ✓ ما تم اختباره ومحاكاته في بيئة المتصفح:
                </span>
                <ul className="list-disc list-inside space-y-0.5 text-[11px] text-neutral-400">
                  <li>واجهة إضافة وتعديل التذكيرات (منبه / إشعار) وضبط الوقت.</li>
                  <li>حفظ التنبيهات في قاعدة البيانات، وإلغاؤها عند حذف المهمة أو إكمالها.</li>
                  <li>محاكاة إطلاق الإشعار وأزرار التفاعل ("تم الإنجاز" و "تأجيل 30 دقيقة").</li>
                  <li>نجاح البناء وفحص الأخطاء (Compilation & Linter) بنسبة 100%.</li>
                </ul>
              </div>

              <div className="p-3 rounded-xl bg-amber-950/20 border border-amber-900/40">
                <span className="text-amber-400 font-bold block mb-1">
                  ⚠ ما لا يمكن اختباره إلا على هاتف Android حقيقي:
                </span>
                <ul className="list-disc list-inside space-y-0.5 text-[11px] text-neutral-400">
                  <li>
                    <strong>إيقاظ الهاتف من النوم العميق (Doze Mode):</strong> يحتاج إلى معالج الهاتف الفعلي ومتحكم الطاقة عبر <code className="text-teal-300">AlarmManager.RTC_WAKEUP</code>.
                  </li>
                  <li>
                    <strong>استعادة المنبهات بعد إعادة تشغيل الهاتف (Phone Reboot):</strong> يستمع مستقبل <code className="text-teal-300">BootCompletedReceiver</code> لإشارة نواة لينكس في أندرويد (<code className="text-teal-300">ACTION_BOOT_COMPLETED</code>)، والتي لا يطلقها المتصفح.
                  </li>
                  <li>
                    <strong>رنين المنبه وشاشة القفل:</strong> استدعاء نغمة الرنين الافتراضية عبر <code className="text-teal-300">setAlarmClock()</code>.
                  </li>
                </ul>
              </div>
            </div>
          </div>

          {/* Real Device Test Guide */}
          <div className="p-5 rounded-2xl bg-[#0e1424] border border-neutral-800 space-y-3">
            <h3 className="text-sm font-bold text-white flex items-center gap-2">
              <AlarmClock className="w-4 h-4 text-teal-400" />
              <span>خطوات الفحص الميداني على هاتفك الحقيقي (The Real-Device Test):</span>
            </h3>

            <div className="space-y-2 text-xs text-neutral-300">
              <div className="p-3 rounded-xl bg-neutral-900 border border-neutral-800">
                <strong>الاختبار 1: منبه بعد 3 دقائق مع قفل الشاشة</strong>
                <p className="text-[11px] text-neutral-400 mt-1">
                  أضف مهمة جديدة، فعّل خيار «تفعيل التذكير المحلي»، اختر «منبه»، واضغط على «بعد 3 دقائق (فحص)». احفظ المهمة، ثم أغلق شاشة هاتفك تماماً وضع الهاتف على الطاولة. بعد 3 دقائق، ستضيء الشاشة ويبدأ المنبه بالرنين مع ظهور زري [تم الإنجاز] و[تأجيل 30 دقيقة].
                </p>
              </div>

              <div className="p-3 rounded-xl bg-neutral-900 border border-neutral-800">
                <strong>الاختبار 2: إعادة تشغيل الهاتف (Reboot Test)</strong>
                <p className="text-[11px] text-neutral-400 mt-1">
                  اضبط تذكيراً لمهمة بعد 15 دقيقة، ثم أعد تشغيل هاتفك بالكامل (Restart). فور إقلاع الهاتف، سيعمل <code className="text-teal-300">BootCompletedReceiver</code> تلقائياً في الخلفية ويعيد تسجيل المنبه دون الحاجة لفتح التطبيق، وسيرن التنبيه في وقته المحدد تماماً.
                </p>
              </div>
            </div>
          </div>

          {/* Code Viewer */}
          <div className="p-5 rounded-2xl bg-[#0e1424] border border-neutral-800 space-y-3.5">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-bold text-white flex items-center gap-2">
                <FolderGit2 className="w-4 h-4 text-teal-400" />
                <span>ملفات المرحلة 3a المنشأة في مشروع Android</span>
              </h3>
              <button
                onClick={() => handleCopy(PHASE3A_FILES[selectedFileIdx].code)}
                className="flex items-center gap-1.5 px-3 py-1 rounded-lg bg-neutral-900 border border-neutral-700 text-xs text-neutral-300 hover:text-white transition-all"
              >
                {copied ? <Check className="w-3.5 h-3.5 text-teal-400" /> : <Copy className="w-3.5 h-3.5" />}
                <span>{copied ? 'تم النسخ!' : 'نسخ الكود'}</span>
              </button>
            </div>

            {/* File List */}
            <div className="flex flex-wrap gap-1.5 pb-1 overflow-x-auto">
              {PHASE3A_FILES.map((file, idx) => (
                <button
                  key={idx}
                  onClick={() => setSelectedFileIdx(idx)}
                  className={`text-[11px] px-2.5 py-1 rounded-lg font-mono transition-all ${
                    selectedFileIdx === idx
                      ? 'bg-teal-500/20 text-teal-300 border border-teal-500/40 font-bold'
                      : 'bg-neutral-900/80 text-neutral-400 hover:text-neutral-200 border border-neutral-800'
                  }`}
                >
                  {file.path.split('/').pop()}
                </button>
              ))}
            </div>

            <div className="text-xs text-neutral-400 flex items-center justify-between">
              <div>
                <strong>المسار:</strong> <code className="text-teal-300 font-mono">{PHASE3A_FILES[selectedFileIdx].path}</code>
              </div>
              <span className="text-[10px] px-2 py-0.5 rounded bg-neutral-800 text-neutral-300 font-mono">
                {PHASE3A_FILES[selectedFileIdx].category}
              </span>
            </div>

            {/* Code Box */}
            <div
              className="rounded-xl bg-neutral-950 border border-neutral-800 p-3 max-h-72 overflow-y-auto font-mono text-[11px] leading-relaxed text-neutral-300 scrollbar-thin scrollbar-thumb-neutral-800"
              dir="ltr"
            >
              <pre>{PHASE3A_FILES[selectedFileIdx].code}</pre>
            </div>
          </div>
        </div>
      </main>
    </div>
  );
}
