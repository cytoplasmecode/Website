package com.cytoplasmecode.plantwatering.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Plant::class], version = 2, exportSchema = false)
abstract class PlantDatabase : RoomDatabase() {

    abstract fun plantDao(): PlantDao

    companion object {
        @Volatile private var INSTANCE: PlantDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE plants ADD COLUMN pendingEventCalendarId TEXT"
                )
            }
        }

        fun getInstance(context: Context): PlantDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PlantDatabase::class.java,
                    "plant_db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
