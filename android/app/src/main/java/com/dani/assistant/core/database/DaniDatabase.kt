package com.dani.assistant.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dani.assistant.data.local.dao.ProjectDao
import com.dani.assistant.data.local.dao.ReminderDao
import com.dani.assistant.data.local.dao.TaskDao
import com.dani.assistant.data.local.entity.ProjectEntity
import com.dani.assistant.data.local.entity.ReminderEntity
import com.dani.assistant.data.local.entity.TaskEntity

@Database(
    entities = [
        ProjectEntity::class,
        TaskEntity::class,
        ReminderEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class DaniDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun projectDao(): ProjectDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        @Volatile
        private var INSTANCE: DaniDatabase? = null

        // Room Migration from Version 1 to Version 2 (Non-destructive)
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `reminders` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `task_id` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `trigger_time` INTEGER NOT NULL,
                        `reminder_type` TEXT NOT NULL,
                        `is_active` INTEGER NOT NULL,
                        FOREIGN KEY(`task_id`) REFERENCES `tasks`(`id`) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_task_id` ON `reminders` (`task_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_trigger_time_is_active` ON `reminders` (`trigger_time`, `is_active`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `recurrence` TEXT NOT NULL DEFAULT 'NONE'")
            }
        }

        fun getDatabase(context: Context): DaniDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DaniDatabase::class.java,
                    "dani_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
