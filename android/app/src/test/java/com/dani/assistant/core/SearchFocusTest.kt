package com.dani.assistant.core

import com.dani.assistant.core.search.Hit
import com.dani.assistant.core.search.SearchFocus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchFocusTest {
    @Test fun taskIsRememberedUntilCleared() {
        SearchFocus.remember(Hit("✅", "مهمة", "t", "", "tasks", "42"))
        assertEquals(42L, SearchFocus.peekTask())
        assertEquals(42L, SearchFocus.peekTask())
        SearchFocus.clearTask()
        assertNull(SearchFocus.peekTask())
    }

    @Test fun factTextIsTakenOnce() {
        SearchFocus.remember(Hit("🧠", "معلومة", "s", "", "memory", "اسمي أوسامة"))
        assertEquals("اسمي أوسامة" to "معلومة", SearchFocus.takeText())
        assertNull(SearchFocus.takeText())
    }

    @Test fun otherKindsClearPreviousFocus() {
        SearchFocus.remember(Hit("✅", "مهمة", "t", "", "tasks", "7"))
        SearchFocus.remember(Hit("📝", "ملاحظة", "n", "", "notes"))
        assertNull(SearchFocus.peekTask())
        assertNull(SearchFocus.takeText())
    }

    @Test fun badTaskRefIsIgnored() {
        SearchFocus.remember(Hit("✅", "مهمة", "t", "", "tasks", "abc"))
        assertNull(SearchFocus.peekTask())
    }
}
