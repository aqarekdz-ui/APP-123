package com.dani.assistant.core.goals

import android.content.Context
import com.dani.assistant.DaniApplication
import com.dani.assistant.core.ai.GeminiAI
import com.dani.assistant.domain.model.PriorityLevel
import com.dani.assistant.domain.model.ReminderType
import com.dani.assistant.domain.model.Task
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar

data class Goal(
    val id: Long,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val horizonDays: Int = 30,
    val taskIds: List<Long> = emptyList()
)

/** أهداف كبرى (ملف dani_goals.json): كل هدف مربوط بمهامه (خارج Room، مثل AreaStore). */
object GoalStore {
    private const val FILE = "dani_goals.json"

    @Synchronized
    private fun readArr(ctx: Context): JSONArray {
        val f = File(ctx.filesDir, FILE)
        if (!f.exists()) return JSONArray()
        return try { JSONObject(f.readText()).optJSONArray("goals") ?: JSONArray() } catch (e: Exception) { JSONArray() }
    }

    @Synchronized
    private fun writeArr(ctx: Context, arr: JSONArray) {
        val tmp = File(ctx.filesDir, "$FILE.tmp")
        tmp.writeText(JSONObject().put("goals", arr).toString())
        tmp.renameTo(File(ctx.filesDir, FILE))
    }

    private fun toJson(g: Goal) = JSONObject().put("id", g.id).put("title", g.title).put("created", g.createdAt)
        .put("horizon", g.horizonDays).put("tasks", JSONArray(g.taskIds))

    private fun fromJson(o: JSONObject): Goal {
        val t = o.optJSONArray("tasks") ?: JSONArray()
        return Goal(o.optLong("id"), o.optString("title"), o.optLong("created"), o.optInt("horizon", 30), List(t.length()) { t.optLong(it) })
    }

    fun list(ctx: Context): List<Goal> {
        val a = readArr(ctx)
        return List(a.length()) { fromJson(a.getJSONObject(it)) }.sortedByDescending { it.createdAt }
    }

    @Synchronized
    fun save(ctx: Context, g: Goal) {
        val arr = readArr(ctx)
        val out = JSONArray()
        var done = false
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optLong("id") == g.id) { out.put(toJson(g)); done = true } else out.put(o)
        }
        if (!done) out.put(toJson(g))
        writeArr(ctx, out)
    }

    @Synchronized
    fun delete(ctx: Context, id: Long) {
        val arr = readArr(ctx)
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optLong("id") != id) out.put(o)
        }
        writeArr(ctx, out)
    }

    fun goalOf(ctx: Context, taskId: Long): Long? =
        list(ctx).firstOrNull { it.taskIds.contains(taskId) }?.id

    @Synchronized
    fun attachTask(ctx: Context, goalId: Long, taskId: Long) {
        val g = list(ctx).firstOrNull { it.id == goalId } ?: return
        if (!g.taskIds.contains(taskId)) save(ctx, g.copy(taskIds = g.taskIds + taskId))
    }

    fun exportJson(ctx: Context): JSONObject = JSONObject().put("goals", readArr(ctx))

    /** دمج: يضيف الأهداف الجديدة (id) بدون مهامها؛ المهام تتربط وقت استيراد المهام. */
    @Synchronized
    fun importJson(ctx: Context, incoming: JSONObject): Int {
        val inc = incoming.optJSONArray("goals") ?: return 0
        val cur = readArr(ctx)
        val ids = HashSet<Long>()
        for (i in 0 until cur.length()) cur.optJSONObject(i)?.let { ids.add(it.optLong("id")) }
        var added = 0
        for (i in 0 until inc.length()) {
            val o = inc.optJSONObject(i) ?: continue
            if (ids.add(o.optLong("id"))) { cur.put(o.put("tasks", JSONArray())); added++ }
        }
        if (added > 0) writeArr(ctx, cur)
        return added
    }
}

/** ينشئ هدف: Gemini يقسمه لمهام بتواريخ، وتتسجل كمهام عادية (مع تذكير 09:00). */
object GoalPlanner {
    /** يرجع (الهدف، رسالة). الهدف null إذا فشل. */
    suspend fun create(ctx: Context, title: String, horizonDays: Int): Pair<Goal?, String> {
        val steps = try { GeminiAI().planGoal(title, horizonDays) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { return null to GeminiAI.friendlyError(e) }
        if (steps.isEmpty()) return null to "ما قدرتش نقسّم الهدف. جرّب صياغة أوضح ولا عاود."
        val repo = DaniApplication.instance.taskRepository
        val ids = ArrayList<Long>()
        for (s in steps) {
            val c = Calendar.getInstance()
            c.add(Calendar.DAY_OF_YEAR, s.day.coerceIn(1, horizonDays.coerceAtLeast(1)))
            c.set(Calendar.HOUR_OF_DAY, 9); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
            val pr = when (s.priority) { "High" -> PriorityLevel.IMPORTANT; "Low" -> PriorityLevel.LOW; else -> PriorityLevel.MEDIUM }
            val id = repo.insertTask(Task(title = s.title, description = "🎯 ضمن هدف: " + title, priority = pr, estimatedMinutes = s.minutes, dueDate = c.timeInMillis))
            try { repo.setTaskReminder(id, s.title, c.timeInMillis, ReminderType.NOTIFICATION) } catch (e: Exception) { }
            ids.add(id)
        }
        val g = Goal(id = System.currentTimeMillis(), title = title, horizonDays = horizonDays, taskIds = ids)
        GoalStore.save(ctx, g)
        return g to "ok"
    }
}
