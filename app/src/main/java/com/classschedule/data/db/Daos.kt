package com.classschedule.data.db

import androidx.room.*
import com.classschedule.data.model.*
import kotlinx.coroutines.flow.Flow

/**
 * 学期DAO
 */
@Dao
interface SemesterDao {
    @Query("SELECT * FROM semesters ORDER BY startDate DESC")
    fun getAllSemesters(): Flow<List<Semester>>

    @Query("SELECT * FROM semesters WHERE isActive = 1 LIMIT 1")
    fun getActiveSemester(): Flow<Semester?>

    @Query("SELECT * FROM semesters WHERE id = :id")
    suspend fun getSemesterById(id: Long): Semester?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(semester: Semester): Long

    @Update
    suspend fun update(semester: Semester)

    @Delete
    suspend fun delete(semester: Semester)

    @Query("UPDATE semesters SET isActive = 0")
    suspend fun deactivateAll()

    @Query("UPDATE semesters SET isActive = 1 WHERE id = :id")
    suspend fun activate(id: Long)

    @Transaction
    suspend fun setActiveSemester(id: Long) {
        deactivateAll()
        activate(id)
    }
}

/**
 * 课程DAO
 */
@Dao
interface CourseDao {
    @Query("SELECT * FROM courses WHERE semesterId = :semesterId ORDER BY dayOfWeek, startPeriod")
    fun getCoursesBySemester(semesterId: Long): Flow<List<Course>>

    @Query("SELECT * FROM courses WHERE semesterId = :semesterId AND dayOfWeek = :dayOfWeek ORDER BY startPeriod")
    fun getCoursesByDay(semesterId: Long, dayOfWeek: Int): Flow<List<Course>>

    @Query("""
        SELECT * FROM courses
        WHERE semesterId = :semesterId
        AND weekStart <= :week AND weekEnd >= :week
        AND (
            weekParity = 0
            OR (weekParity = 1 AND :week % 2 = 1)
            OR (weekParity = 2 AND :week % 2 = 0)
        )
        ORDER BY dayOfWeek, startPeriod
    """)
    fun getCoursesByWeek(semesterId: Long, week: Int): Flow<List<Course>>

    @Query("SELECT * FROM courses WHERE id = :id")
    suspend fun getCourseById(id: Long): Course?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(course: Course): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(courses: List<Course>)

    @Update
    suspend fun update(course: Course)

    @Delete
    suspend fun delete(course: Course)

    @Query("DELETE FROM courses WHERE semesterId = :semesterId")
    suspend fun deleteBySemester(semesterId: Long)

    @Query("""
        DELETE FROM courses
        WHERE semesterId = :semesterId
        AND weekStart <= :week AND weekEnd >= :week
        AND (
            weekParity = 0
            OR (weekParity = 1 AND :week % 2 = 1)
            OR (weekParity = 2 AND :week % 2 = 0)
        )
    """)
    suspend fun deleteCoursesInWeek(semesterId: Long, week: Int)

    @Query("SELECT MAX(colorIndex) FROM courses WHERE semesterId = :semesterId")
    suspend fun getMaxColorIndex(semesterId: Long): Int?
}

/**
 * 课时配置DAO
 */
@Dao
interface PeriodConfigDao {
    @Query("SELECT * FROM period_configs ORDER BY period ASC")
    fun getAllPeriodConfigs(): Flow<List<PeriodConfig>>

    @Query("SELECT * FROM period_configs ORDER BY period ASC")
    suspend fun getAllPeriodConfigsList(): List<PeriodConfig>

    @Query("SELECT * FROM period_configs WHERE period = :period")
    suspend fun getPeriodConfig(period: Int): PeriodConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: PeriodConfig)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(configs: List<PeriodConfig>)

    @Update
    suspend fun update(config: PeriodConfig)

    @Delete
    suspend fun delete(config: PeriodConfig)

    @Query("DELETE FROM period_configs")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM period_configs")
    suspend fun getCount(): Int
}
