package com.dani.assistant.core.areas

import android.content.Context

data class LifeArea(val key: String, val label: String, val emoji: String)

/** مجالات الحياة: كل مهمة تقدر تنتمي لمجال (يتخزن خارج Room حتى ما نغيّروش قاعدة البيانات). */
object AreaStore {
    private const val PREFS = "dani_areas"

    val areas = listOf(
        LifeArea("work", "العمل", "💼"),
        LifeArea("family", "العائلة", "👪"),
        LifeArea("health", "الصحة", "🩺"),
        LifeArea("money", "المال", "💰"),
        LifeArea("study", "الدراسة", "📚"),
        LifeArea("home", "البيت", "🏠"),
        LifeArea("personal", "شخصي", "🧘")
    )

    fun find(key: String?): LifeArea? = areas.firstOrNull { it.key == key }

    fun get(ctx: Context, taskId: Long): String? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("t_$taskId", null)

    fun set(ctx: Context, taskId: Long, key: String?) {
        val e = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (key == null) e.remove("t_$taskId") else e.putString("t_$taskId", key)
        e.apply()
    }

    fun all(ctx: Context): Map<Long, String> {
        val out = HashMap<Long, String>()
        for ((k, v) in ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all) {
            if (k.startsWith("t_") && v is String) {
                val id = k.removePrefix("t_").toLongOrNull()
                if (id != null) out[id] = v
            }
        }
        return out
    }
}
