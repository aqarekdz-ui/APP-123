package com.dani.assistant.core.backup

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.dani.assistant.core.events.EventStore
import com.dani.assistant.core.events.LifeEvent
import com.dani.assistant.core.goals.Goal
import com.dani.assistant.core.goals.GoalStore
import com.dani.assistant.core.habits.HabitStore
import com.dani.assistant.core.meds.Med
import com.dani.assistant.core.meds.MedStore
import com.dani.assistant.core.money.MoneyEntry
import com.dani.assistant.core.money.MoneyStore
import com.dani.assistant.core.notes.Note
import com.dani.assistant.core.notes.NoteStore
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * تجربة تصدير ← مسح ← استيراد على المخازن الحقيقية (Robolectric، بلا DaniApplication ولا Room).
 * تتأكد: ما يضيعش شيء، ما يتكرّرش شيء، والموجود محلياً يبقى.
 * ما تغطيش BackupManager نفسو (يحتاج Room/DaniApplication) ولا التشفير.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class StoreRoundTripTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun wipe(file: String) { File(ctx.filesDir, file).delete() }

    @Test fun eventsRoundTripIsLosslessAndIdempotent() {
        EventStore.save(ctx, LifeEvent(id = 1, name = "أحمد", day = 15, month = 3, year = 1990))
        EventStore.save(ctx, LifeEvent(id = 2, name = "عرس", kind = "occasion", day = 20, month = 7, yearly = false))
        val snap = EventStore.exportJson(ctx)
        wipe("dani_events.json")
        assertEquals(0, EventStore.list(ctx).size)
        assertEquals(2, EventStore.importJson(ctx, snap))
        assertEquals(listOf(1L, 2L), EventStore.list(ctx).map { it.id }.sorted())
        assertEquals(1990, EventStore.list(ctx).first { it.id == 1L }.year)
        assertEquals(0, EventStore.importJson(ctx, snap))
        assertEquals(2, EventStore.list(ctx).size)
    }

    @Test fun importKeepsLocalItemsAndNeverOverwrites() {
        NoteStore.save(ctx, Note(id = 10, text = "قديمة"))
        val snap = NoteStore.exportJson(ctx)
        NoteStore.save(ctx, Note(id = 11, text = "جديدة محلية"))
        NoteStore.save(ctx, Note(id = 10, text = "معدّلة محلياً"))
        assertEquals(0, NoteStore.importJson(ctx, snap))
        val all = NoteStore.list(ctx)
        assertEquals(2, all.size)
        assertEquals("معدّلة محلياً", all.first { it.id == 10L }.text)
    }

    @Test fun notesKeepPinnedAndTextWithArabicAndNewlines() {
        NoteStore.save(ctx, Note(id = 5, text = "سطر 1\nسطر \"2\"", pinned = true))
        val snap = NoteStore.exportJson(ctx)
        wipe("dani_notes.json")
        assertEquals(1, NoteStore.importJson(ctx, snap))
        val n = NoteStore.list(ctx).single()
        assertEquals("سطر 1\nسطر \"2\"", n.text)
        assertTrue(n.pinned)
    }

    @Test fun moneyEntriesAndBudgetsRoundTrip() {
        MoneyStore.save(ctx, MoneyEntry(id = 1, type = "expense", amount = 500, category = "أكل", note = "قهوة"))
        MoneyStore.save(ctx, MoneyEntry(id = 2, type = "debt_to_me", amount = 3000, person = "علي"))
        MoneyStore.setBudget(ctx, "أكل", 20000)
        val snap = MoneyStore.exportJson(ctx)
        wipe("dani_money.json")
        val added = MoneyStore.importJson(ctx, snap)
        assertEquals(3, added) // عمليتين + ميزانية
        assertEquals(2, MoneyStore.entries(ctx).size)
        assertEquals(20000L, MoneyStore.budgets(ctx)["أكل"])
        assertEquals(0, MoneyStore.importJson(ctx, snap))
    }

    @Test fun moneyImportDoesNotOverrideExistingBudget() {
        MoneyStore.setBudget(ctx, "أكل", 20000)
        val snap = MoneyStore.exportJson(ctx)
        MoneyStore.setBudget(ctx, "أكل", 35000)
        MoneyStore.importJson(ctx, snap)
        assertEquals(35000L, MoneyStore.budgets(ctx)["أكل"])
    }

    @Test fun goalsAndMedsRoundTrip() {
        GoalStore.save(ctx, Goal(id = 7, title = "هدف", horizonDays = 60, taskIds = listOf(1, 2)))
        MedStore.save(ctx, Med(id = 8, name = "دواء", dose = "1 حبة", times = listOf(480, 1200)))
        val gs = GoalStore.exportJson(ctx); val ms = MedStore.exportJson(ctx)
        wipe("dani_goals.json"); wipe("dani_meds.json")
        assertEquals(1, GoalStore.importJson(ctx, gs))
        assertEquals(1, MedStore.importJson(ctx, ms))
        assertEquals(listOf(1L, 2L), GoalStore.list(ctx).single().taskIds)
        assertEquals(listOf(480, 1200), MedStore.list(ctx).single().times)
        assertEquals(0, GoalStore.importJson(ctx, gs))
        assertEquals(0, MedStore.importJson(ctx, ms))
    }

    @Test fun habitsRoundTripAndMergeDays() {
        HabitStore.add(ctx, "جري", "🏃")
        val h = HabitStore.list(ctx).single()
        HabitStore.toggle(ctx, h.id, "2026-10-01")
        val snap = HabitStore.exportJson(ctx)
        HabitStore.toggle(ctx, h.id, "2026-10-02") // يوم محلي زيادة
        assertEquals(0, HabitStore.importJson(ctx, snap))
        assertEquals(setOf("2026-10-01", "2026-10-02"), HabitStore.list(ctx).single().days)
        wipe("dani_habits.json")
        assertEquals(1, HabitStore.importJson(ctx, snap))
        assertEquals(setOf("2026-10-01"), HabitStore.list(ctx).single().days)
    }

    @Test fun importingGarbageOrEmptyJsonIsHarmless() {
        EventStore.save(ctx, LifeEvent(id = 1, name = "x", day = 1, month = 1))
        assertEquals(0, EventStore.importJson(ctx, JSONObject()))
        assertEquals(0, NoteStore.importJson(ctx, JSONObject("{\"notes\":\"not-an-array\"}")))
        assertEquals(1, EventStore.list(ctx).size)
    }
}
