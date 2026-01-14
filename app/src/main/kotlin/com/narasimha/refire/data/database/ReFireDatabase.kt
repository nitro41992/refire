package com.narasimha.refire.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SnoozeEntity::class],
    version = 7,
    exportSchema = false
)
@androidx.room.TypeConverters(Converters::class)
abstract class ReFireDatabase : RoomDatabase() {
    abstract fun snoozeDao(): SnoozeDao

    companion object {
        @Volatile
        private var INSTANCE: ReFireDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add appName column with default empty string
                database.execSQL(
                    "ALTER TABLE snoozes ADD COLUMN appName TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add contentType column (nullable, defaults to null for existing records)
                database.execSQL(
                    "ALTER TABLE snoozes ADD COLUMN contentType TEXT"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add messagesJson column (nullable, defaults to null for existing records)
                database.execSQL(
                    "ALTER TABLE snoozes ADD COLUMN messagesJson TEXT"
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add status column for tracking snooze lifecycle (ACTIVE/EXPIRED)
                database.execSQL(
                    "ALTER TABLE snoozes ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE'"
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add suppressedCount column for tracking messages suppressed after snooze creation
                database.execSQL(
                    "ALTER TABLE snoozes ADD COLUMN suppressedCount INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add contentIntentUri column for persisting deep-link intent data
                database.execSQL(
                    "ALTER TABLE snoozes ADD COLUMN contentIntentUri TEXT"
                )
            }
        }

        fun getInstance(context: Context): ReFireDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): ReFireDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                ReFireDatabase::class.java,
                "refire_database"
            )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
            .build()
        }
    }
}
