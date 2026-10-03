package com.dani.assistant.domain.model

import org.json.JSONArray
import org.json.JSONObject

/** خطوة فرعية داخل المهمة (checklist). */
data class Subtask(val text: String, val done: Boolean = false)

object SubtaskCodec {
    fun encode(list: List<Subtask>): String {
        if (list.isEmpty()) return ""
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("t", it.text).put("d", it.done)) }
        return arr.toString()
    }

    fun decode(raw: String?): List<Subtask> {
        if (raw.isNullOrEmpty()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull {
                val o = arr.optJSONObject(it) ?: return@mapNotNull null
                val t = o.optString("t")
                if (t.isBlank()) null else Subtask(t, o.optBoolean("d"))
            }
        } catch (e: Exception) { emptyList() }
    }
}
