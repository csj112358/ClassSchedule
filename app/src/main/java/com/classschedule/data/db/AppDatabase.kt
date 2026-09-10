package com.classschedule.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.classschedule.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        Semester::class,
        Course::class,
        PeriodConfig::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun semesterDao(): SemesterDao
    abstract fun courseDao(): CourseDao
    abstract fun periodConfigDao(): PeriodConfigDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** v1 -> v2: courses 表新增 weekParity 列（单双周），默认 0=每周 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE courses ADD COLUMN weekParity INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v2 -> v3: 移除 API 配置功能，删除 api_configs 表 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS api_configs")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "class_schedule_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .addCallback(DatabaseCallback())
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                CoroutineScope(Dispatchers.IO).launch {
                    populateDefaultData(database)
                }
            }
        }

        private suspend fun populateDefaultData(database: AppDatabase) {
            // 插入默认课时配置（8节课）
            val defaultPeriods = listOf(
                PeriodConfig(1, "08:00", "08:45", "上午第一节"),
                PeriodConfig(2, "08:55", "09:40", "上午第二节"),
                PeriodConfig(3, "10:00", "10:45", "上午第三节"),
                PeriodConfig(4, "10:55", "11:40", "上午第四节"),
                PeriodConfig(5, "14:00", "14:45", "下午第一节"),
                PeriodConfig(6, "14:55", "15:40", "下午第二节"),
                PeriodConfig(7, "16:00", "16:45", "下午第三节"),
                PeriodConfig(8, "16:55", "17:40", "下午第四节"),
            )
            database.periodConfigDao().insertAll(defaultPeriods)
        }
    }
}
