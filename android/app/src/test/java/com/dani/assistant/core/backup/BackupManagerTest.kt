package com.dani.assistant.core.backup

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.dani.assistant.core.areas.AreaStore
import com.dani.assistant.core.events.EventStore
import com.dani.assistant.core.events.LifeEvent
import com.dani.assistant.core.goals.Goal
import com.dani.assistant.core.goals.GoalStore
import com.dani.assistant.core.habits.HabitStore
import com.dani.assistant.core.memory.MemoryStore
import com.dani.assistant.core.money.MoneyEntry
import com.dani.assistant.core.money.MoneyStore
import com.dani.assistant.core.notes.Note
import com.dani.assistant.core.notes.NoteStore
import com.dani.assistant.domain.model.PriorityLevel
import com.dani.assistant.domain.model.Recurrence
import com.dani.assistant.domain.model.Subtask
import com.dani.assistant.domain.model.Task
import com.dani.assistant.domain.model.TaskStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** بديل في الذاكرة لمصدر المهام (بلا Room ولا DaniApplication). */
private class FakeTasks(startId: Long) : BackupTaskSource {
    private var next = startId
    val list = mutableListOf<Task>()
    val reminders = mutableListOf<Pair<Long, String>>()
    override suspend fun all(): List<Task> = list.toList()
    override suspend fun insert(task: Task): Long { val id = next++; list.add(task.copy(id = id)); return id }
    override suspend fun setReminder(taskId: Long, title: String, time: Long) { reminders.add(taskId to title) }
}

/**
 * BackupManager الحقيقي (export + import) على Robolectric مع مصدر مهام بديل.
 * يغطي: تدفّق كامل للمهام والمخازن، الدمج بلا تكرار، التذكيرات، ونسخ قديمة/تالفة الحقول.
 * ما يغطيش: الأسرار (Android Keystore مش متوفر في Robolectric)، Room الحقيقي، المنبهات الفعلية، المعرفة (KnowledgeBase).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BackupManagerTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun wipeJsonFiles() {
        ctx.filesDir.listFiles()?.filter { it.name.startsWith("dani_") }?.forEach { it.delete() }
    }

    @Test fun fullRoundTripIntoEmptyDevice_thenIdempotent() = runBlocking {
        val day = 24L * 3600 * 1000
        val now = System.currentTimeMillis()
        val a = FakeTasks(100)
        val t1 = a.insert(Task(title = "مهمة قادمة", priority = PriorityLevel.IMPORTANT, estimatedMinutes = 45,
            dueDate = now + day, createdAt = 1111, recurrence = Recurrence.DAILY,
            subtasks = listOf(Subtask("أ", true), Subtask("ب", false))))
        val t2 = a.insert(Task(title = "منجزة", status = TaskStatus.COMPLETED, dueDate = now + day, createdAt = 2222, completedAt = 3333))
        a.insert(Task(title = "فايتة", dueDate = now - day, createdAt = 4444))

        AreaStore.set(ctx, t1, AreaStore.areas.first().key)
        GoalStore.save(ctx, Goal(id = 7, title = "هدف", horizonDays = 30))
        GoalStore.attachTask(ctx, 7, t1)
        MemoryStore.add(ctx, "اسمي علي")
        HabitStore.add(ctx, "جري", "🏃", setOf(2, 4))
        HabitStore.toggle(ctx, HabitStore.list(ctx).single().id, "2026-10-01")
        EventStore.save(ctx, LifeEvent(id = 1, name = "أحمد", day = 15, month = 3, year = 1990))
        NoteStore.save(ctx, Note(id = 5, text = "ملاحظة", pinned = true))
        MoneyStore.save(ctx, MoneyEntry(id = 1, type = "expense", amount = 500, category = "أكل"))

        val json = BackupManager.export(ctx, "", a)
        assertTrue(BackupManager.isValid(json))
        assertFalse(BackupManager.hasSecrets(json))

        // جهاز فارغ
        wipeJsonFiles()
        MemoryStore.replaceAll(ctx, emptyList())
        val b = FakeTasks(500)
        val r = BackupManager.import(ctx, json, "", b)

        assertEquals(3, r.tasks); assertEquals(1, r.facts); assertEquals(1, r.habits)
        assertEquals(1, r.events); assertEquals(1, r.notes); assertEquals(1, r.money); assertEquals(1, r.goals)

        val first = b.list.first { it.title == "مهمة قادمة" }
        assertEquals(PriorityLevel.IMPORTANT, first.priority)
        assertEquals(45, first.estimatedMinutes)
        assertEquals(1111L, first.createdAt)
        assertEquals(Recurrence.DAILY, first.recurrence)
        assertEquals(listOf(Subtask("أ", true), Subtask("ب", false)), first.subtasks)
        val done = b.list.first { it.title == "منجزة" }
        assertEquals(TaskStatus.COMPLETED, done.status); assertEquals(3333L, done.completedAt)

        // التذكير فقط للمهمة القادمة غير المنجزة
        assertEquals(listOf("مهمة قادمة"), b.reminders.map { it.second })
        // المجال والهدف يتربطو بالـ id الجديد
        assertEquals(AreaStore.areas.first().key, AreaStore.get(ctx, first.id))
        assertTrue(GoalStore.list(ctx).single().taskIds.contains(first.id))
        assertEquals(setOf(2, 4), HabitStore.list(ctx).single().weekdays)
        assertTrue(HabitStore.list(ctx).single().days.contains("2026-10-01"))
        assertEquals(listOf("اسمي علي"), MemoryStore.getAll(ctx))

        // استيراد ثاني = ما يتكرّر شيء
        val r2 = BackupManager.import(ctx, json, "", b)
        assertEquals(0, r2.tasks); assertEquals(0, r2.facts); assertEquals(0, r2.habits)
        assertEquals(0, r2.events); assertEquals(0, r2.notes); assertEquals(0, r2.money); assertEquals(0, r2.goals)
        assertEquals(3, b.list.size)
        assertEquals(1, HabitStore.list(ctx).size)
        assertEquals(1, MemoryStore.getAll(ctx).size)
        assertTrue(t2 > 0)
    }

    @Test fun importMergesWithoutTouchingLocalTasks() = runBlocking {
        val a = FakeTasks(1)
        a.insert(Task(title = "من النسخة", createdAt = 10))
        val json = BackupManager.export(ctx, "", a)
        val local = FakeTasks(900)
        local.insert(Task(title = "محلية", createdAt = 20))
        val r = BackupManager.import(ctx, json, "", local)
        assertEquals(1, r.tasks)
        assertEquals(setOf("محلية", "من النسخة"), local.list.map { it.title }.toSet())
    }

    @Test fun legacyBackupWithOnlyBasicFieldsImports() = runBlocking {
        val legacy = "{\"app\":\"dani\",\"version\":1,\"tasks\":[{\"title\":\"قديمة\",\"status\":\"???\",\"rec\":\"WEIRD\"}]}"
        assertTrue(BackupManager.isValid(legacy))
        val b = FakeTasks(1)
        val r = BackupManager.import(ctx, legacy, "", b)
        assertEquals(1, r.tasks)
        assertEquals(0, r.habits); assertEquals(0, r.events); assertEquals(0, r.notes); assertEquals(0, r.money)
        assertEquals(TaskStatus.NEW, b.list.single().status)
        assertEquals(Recurrence.NONE, b.list.single().recurrence)
        assertTrue(b.reminders.isEmpty())
    }

    @Test fun validationRejectsForeignAndGarbage() {
        assertFalse(BackupManager.isValid("{\"app\":\"other\"}"))
        assertFalse(BackupManager.isValid("not json"))
        assertFalse(BackupManager.hasSecrets("not json"))
    }
}
