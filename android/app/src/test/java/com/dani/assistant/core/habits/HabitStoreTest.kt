package com.dani.assistant.core.habits

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Robolectric: حفظ واسترجاع الأيام المخصّصة للعادات (المخزن الحقيقي، بلا Room). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HabitStoreTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun weekdaysSurviveExportWipeImport() {
        File(ctx.filesDir, "dani_habits.json").delete()
        HabitStore.add(ctx, "رياضة", "🏃", setOf(2, 4, 9, 0)) // القيم الخارج 1..7 تتجاهل
        val snap = HabitStore.exportJson(ctx)
        File(ctx.filesDir, "dani_habits.json").delete()
        assertEquals(1, HabitStore.importJson(ctx, snap))
        val h = HabitStore.list(ctx).single()
        assertEquals(setOf(2, 4), h.weekdays)
    }

    @Test fun allSevenOrNoneMeansDaily() {
        File(ctx.filesDir, "dani_habits.json").delete()
        HabitStore.add(ctx, "ماء", "💧", (1..7).toSet())
        assertEquals(emptySet<Int>(), HabitStore.list(ctx).single().weekdays)
    }

    @Test fun oldJsonWithoutWdIsDaily() {
        File(ctx.filesDir, "dani_habits.json").delete()
        val old = org.json.JSONObject("{\"habits\":[{\"id\":5,\"name\":\"قديمة\",\"emoji\":\"✅\",\"created\":1,\"days\":[\"2026-10-01\"]}]}")
        assertEquals(1, HabitStore.importJson(ctx, old))
        val h = HabitStore.list(ctx).single()
        assertEquals(emptySet<Int>(), h.weekdays)
        assertEquals(setOf("2026-10-01"), h.days)
    }
}
