package com.imsx3d.classy.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.imsx3d.classy.data.dao.CourseDao
import com.imsx3d.classy.data.dao.ImportDraftDao
import com.imsx3d.classy.data.dao.PeriodTableDao
import com.imsx3d.classy.data.dao.TimeTableDao
import com.imsx3d.classy.data.entity.CourseEntity
import com.imsx3d.classy.data.entity.ImportDraftEntity
import com.imsx3d.classy.data.entity.PeriodTableEntity
import com.imsx3d.classy.data.entity.TimeTableEntity

@Database(
    entities = [CourseEntity::class, TimeTableEntity::class, PeriodTableEntity::class, ImportDraftEntity::class],
    version = 9,                            // 8 → 9: C2 绑定前快照, 解绑恢复 (issue#40)
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun courseDao(): CourseDao
    abstract fun timeTableDao(): TimeTableDao
    abstract fun periodTableDao(): PeriodTableDao
    abstract fun importDraftDao(): ImportDraftDao

    companion object {
        private const val DB_NAME = "sleepy.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    // 严禁 fallbackToDestructiveMigration — 任意版本升会清空用户课表
                    // 任何 schema 改动必须先在 Migrations.kt 登记, 再升 version
                    .build()
                    .also { instance = it }
            }
        }
    }
}
