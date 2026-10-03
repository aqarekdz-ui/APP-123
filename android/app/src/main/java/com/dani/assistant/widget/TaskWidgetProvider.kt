package com.dani.assistant.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.dani.assistant.DaniApplication
import com.dani.assistant.MainActivity
import com.dani.assistant.R
import com.dani.assistant.domain.model.Task
import com.dani.assistant.domain.model.TaskStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** ودجت الشاشة الرئيسية: أقرب 5 مهام متبقية. */
class TaskWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                update(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val rows = intArrayOf(R.id.w_task1, R.id.w_task2, R.id.w_task3, R.id.w_task4, R.id.w_task5)

        /** يُستدعى بعد أي تعديل على المهام. */
        fun refresh(context: Context) {
            val app = context.applicationContext
            CoroutineScope(Dispatchers.IO).launch {
                try { update(app) } catch (e: Exception) { }
            }
        }

        private suspend fun update(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, TaskWidgetProvider::class.java))
            if (ids.isEmpty()) return

            val pending: List<Task> = DaniApplication.instance.taskRepository.getAllTasks().first()
                .filter { it.status != TaskStatus.COMPLETED && it.status != TaskStatus.CANCELLED }
                .sortedWith(compareBy<Task>({ it.dueDate == null }, { it.dueDate ?: Long.MAX_VALUE }, { it.priority.level }))

            val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
            val rv = RemoteViews(context.packageName, R.layout.widget_tasks)
            rv.setTextViewText(R.id.w_title, "DANI · " + pending.size + " متبقية")
            rows.forEachIndexed { i, viewId ->
                val t = pending.getOrNull(i)
                if (t == null) {
                    rv.setViewVisibility(viewId, View.GONE)
                } else {
                    val due = t.dueDate
                    val line = "• " + t.title + (if (due != null) "  ⏰ " + fmt.format(Date(due)) else "")
                    rv.setTextViewText(viewId, line)
                    rv.setViewVisibility(viewId, View.VISIBLE)
                }
            }
            rv.setViewVisibility(R.id.w_empty, if (pending.isEmpty()) View.VISIBLE else View.GONE)

            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            rv.setOnClickPendingIntent(R.id.w_root, open)
            ids.forEach { mgr.updateAppWidget(it, rv) }
        }
    }
}
