package com.dani.assistant.core.memory

import android.content.Context
import org.json.JSONArray

object MemoryStore {
    private const val PREFS = "dani_memory"
    private const val KEY = "facts"

    fun getAll(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun save(context: Context, items: List<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, JSONArray(items).toString()).apply()
    }

    fun add(context: Context, fact: String) {
        val clean = fact.trim()
        if (clean.isEmpty()) return
        val items = getAll(context)
        if (items.contains(clean)) return
        save(context, items + clean)
    }

    fun remove(context: Context, fact: String) {
        save(context, getAll(context).filter { it != fact })
    }

    fun replaceAll(context: Context, items: List<String>) = save(context, items.distinct())
}
