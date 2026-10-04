package com.dani.assistant.core.money

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** تنبيهات الميزانية الشهرية: إشعار مرة عند 80% ومرة عند التجاوز لكل فئة في الشهر. */
object BudgetAlerts {
    private const val PREFS = "dani_budget_alerts"
    private const val CHANNEL = "dani_budget"

    private fun monthKey(): String = SimpleDateFormat("yyyyMM", Locale.US).format(Date())

    /** يرجع نص تحذير (للشات) إذا عبرنا عتبة جديدة، وإلا null. ويرسل إشعاراً. */
    fun check(ctx: Context, category: String): String? {
        val cat = category.ifBlank { "أخرى" }
        val budget = MoneyStore.budgets(ctx)[cat] ?: return null
        if (budget <= 0) return null
        val spent = MoneyStore.spentThisMonth(ctx, cat)
        val pct = (spent * 100 / budget).toInt()
        val level = when { pct >= 100 -> 100; pct >= 80 -> 80; else -> 0 }
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = monthKey() + "|" + cat
        val fired = prefs.getInt(key, 0)
        if (level == 0) {
            if (fired != 0) prefs.edit().remove(key).apply()
            return null
        }
        if (level <= fired) return null
        prefs.edit().putInt(key, level).apply()
        val title: String
        val body: String
        if (level == 100) {
            title = "🚨 تجاوزت ميزانية " + cat
            body = "صرفت " + MoneyStore.fmt(spent) + " من " + MoneyStore.fmt(budget) + " (زيادة " + MoneyStore.fmt(spent - budget) + ")"
        } else {
            title = "⚠️ قربت تكمل ميزانية " + cat
            body = "صرفت " + pct + "% (" + MoneyStore.fmt(spent) + " من " + MoneyStore.fmt(budget) + ") — بقالك " + MoneyStore.fmt(budget - spent)
        }
        notify(ctx, cat, title, body)
        return title + "\n" + body
    }

    private fun notify(ctx: Context, cat: String, title: String, body: String) {
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "ميزانية المصاريف", NotificationManager.IMPORTANCE_DEFAULT))
            }
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_agenda)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(ctx).notify(9200 + (cat.hashCode() and 0xff), n)
        } catch (e: SecurityException) { } catch (e: Exception) { }
    }
}
