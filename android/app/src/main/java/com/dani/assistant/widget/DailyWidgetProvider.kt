package com.dani.assistant.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.dani.assistant.MainActivity
import com.dani.assistant.R
import com.dani.assistant.core.habits.HabitStore
import com.dani.assistant.core.meds.MedAlarms
import com.dani.assistant.core.meds.MedStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** ودجت "اليوم": عادات اليوم وجرعات الأدوية، بلمسة وحدة تعلّم ☑/☐. */
class DailyWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { update(context.applicationContext) } catch (e: Exception) { } finally { pending.finish() }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val act = intent.action
        if (act != ACT_HABIT && act != ACT_DOSE) { super.onReceive(context, intent); return }
        val ctx = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (act == ACT_HABIT) {
                    val id = intent.getLongExtra("id", 0L)
                    if (id != 0L) HabitStore.toggle(ctx, id, HabitStore.dayKey(0))
                } else {
                    val med = intent.getLongExtra("med", 0L)
                    val t = intent.getIntExtra("t", -1)
                    if (med != 0L && t >= 0) {
                        val nowTaken = !MedStore.isTaken(ctx, med, t)
                        MedStore.mark(ctx, med, t, nowTaken)
                        if (nowTaken) MedAlarms.cancelNotif(ctx, med, t)
                    }
                }
                update(ctx)
            } catch (e: Exception) { } finally { pending.finish() }
        }
    }

    companion object {
        const val ACT_HABIT = "com.dani.assistant.widget.HABIT"
        const val ACT_DOSE = "com.dani.assistant.widget.DOSE"
        private val habitRows = intArrayOf(R.id.d_h1, R.id.d_h2, R.id.d_h3, R.id.d_h4)
        private val doseRows = intArrayOf(R.id.d_m1, R.id.d_m2, R.id.d_m3, R.id.d_m4)

        /** يُستدعى بعد أي تعديل على العادات أو الأدوية. */
        fun refresh(context: Context) {
            val app = context.applicationContext
            CoroutineScope(Dispatchers.IO).launch { try { update(app) } catch (e: Exception) { } }
        }

        private fun broadcast(ctx: Context, action: String, path: String, fill: Intent.() -> Unit): PendingIntent {
            val i = Intent(ctx, DailyWidgetProvider::class.java).setAction(action).setData(Uri.parse("dani://widget/" + path))
            i.fill()
            return PendingIntent.getBroadcast(ctx, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        private fun update(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, DailyWidgetProvider::class.java))
            if (ids.isEmpty()) return

            val habits = DailyWidgetLogic.pickHabits(HabitStore.list(ctx).map {
                WHabit(it.id, it.emoji, it.name, it.days.contains(HabitStore.dayKey(0)), HabitStore.streak(it))
            })
            val doses = DailyWidgetLogic.pickDoses(MedStore.todayDoses(ctx).map { WDose(it.med.id, it.minute, it.med.name, it.taken) })

            val rv = RemoteViews(ctx.packageName, R.layout.widget_daily)
            rv.setTextViewText(R.id.d_title, DailyWidgetLogic.title(habits, doses))
            habitRows.forEachIndexed { i, viewId ->
                val h = habits.getOrNull(i)
                if (h == null) rv.setViewVisibility(viewId, View.GONE) else {
                    rv.setTextViewText(viewId, DailyWidgetLogic.habitLine(h))
                    rv.setViewVisibility(viewId, View.VISIBLE)
                    rv.setOnClickPendingIntent(viewId, broadcast(ctx, ACT_HABIT, "habit/" + h.id) { putExtra("id", h.id) })
                }
            }
            doseRows.forEachIndexed { i, viewId ->
                val d = doses.getOrNull(i)
                if (d == null) rv.setViewVisibility(viewId, View.GONE) else {
                    rv.setTextViewText(viewId, DailyWidgetLogic.doseLine(d))
                    rv.setViewVisibility(viewId, View.VISIBLE)
                    rv.setOnClickPendingIntent(viewId, broadcast(ctx, ACT_DOSE, "dose/" + d.medId + "/" + d.minute) { putExtra("med", d.medId); putExtra("t", d.minute) })
                }
            }
            rv.setViewVisibility(R.id.d_sep, if (habits.isNotEmpty() && doses.isNotEmpty()) View.VISIBLE else View.GONE)
            rv.setViewVisibility(R.id.d_empty, if (habits.isEmpty() && doses.isEmpty()) View.VISIBLE else View.GONE)
            rv.setOnClickPendingIntent(R.id.d_title, PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            ids.forEach { mgr.updateAppWidget(it, rv) }
        }
    }
}
