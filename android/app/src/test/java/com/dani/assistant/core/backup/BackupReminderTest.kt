package com.dani.assistant.core.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class BackupReminderTest {
    private val day = TimeUnit.DAYS.toMillis(1)
    private val now = 100 * day

    @Test fun recentBackupNoReminder() = assertFalse(BackupReminder.shouldRemind(now - 5 * day, now - 90 * day, 0, now))

    @Test fun oldBackupReminds() {
        assertTrue(BackupReminder.shouldRemind(now - 14 * day, 0, 0, now))
        assertFalse(BackupReminder.shouldRemind(now - 13 * day, 0, 0, now))
    }

    @Test fun neverBackedUpUsesFirstSeen() {
        assertFalse(BackupReminder.shouldRemind(0, now - 3 * day, 0, now))
        assertTrue(BackupReminder.shouldRemind(0, now - 20 * day, 0, now))
    }

    @Test fun snoozeSuppresses() {
        assertFalse(BackupReminder.shouldRemind(now - 30 * day, 0, now + day, now))
        assertTrue(BackupReminder.shouldRemind(now - 30 * day, 0, now - 1, now))
    }

    @Test fun nothingKnownNoReminder() = assertFalse(BackupReminder.shouldRemind(0, 0, 0, now))

    @Test fun daysSince() {
        assertNull(BackupReminder.daysSince(0, now))
        assertEquals(20, BackupReminder.daysSince(now - 20 * day, now))
        assertEquals(0, BackupReminder.daysSince(now + day, now))
    }
}
