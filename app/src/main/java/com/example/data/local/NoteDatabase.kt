package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [NoteEntity::class],
    version = 5,
    exportSchema = false
)
abstract class NoteDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var INSTANCE: NoteDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE notes ADD COLUMN isLocked INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE notes ADD COLUMN pageFormat TEXT NOT NULL DEFAULT 'BOOK'")
                } catch (_: Exception) {}
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE notes ADD COLUMN fontFormat TEXT NOT NULL DEFAULT 'DEFAULT'")
                    db.execSQL("ALTER TABLE notes ADD COLUMN textColorHex TEXT NOT NULL DEFAULT '#1C1B1F'")
                } catch (_: Exception) {}
            }
        }

        fun getInstance(context: Context): NoteDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NoteDatabase::class.java,
                    "notes_database"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
