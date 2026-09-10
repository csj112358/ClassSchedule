package com.classschedule.data.repository

import com.classschedule.data.db.*
import com.classschedule.data.model.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 学期仓库
 */
@Singleton
class SemesterRepository @Inject constructor(
    private val semesterDao: SemesterDao
) {
    fun getAllSemesters(): Flow<List<Semester>> = semesterDao.getAllSemesters()

    fun getActiveSemester(): Flow<Semester?> = semesterDao.getActiveSemester()

    suspend fun getSemesterById(id: Long): Semester? = semesterDao.getSemesterById(id)

    suspend fun insert(semester: Semester): Long = semesterDao.insert(semester)

    suspend fun update(semester: Semester) = semesterDao.update(semester)

    suspend fun delete(semester: Semester) = semesterDao.delete(semester)

    suspend fun setActiveSemester(id: Long) = semesterDao.setActiveSemester(id)
}

/**
 * 课程仓库
 */
@Singleton
class CourseRepository @Inject constructor(
    private val courseDao: CourseDao
) {
    fun getCoursesBySemester(semesterId: Long): Flow<List<Course>> =
        courseDao.getCoursesBySemester(semesterId)

    fun getCoursesByDay(semesterId: Long, dayOfWeek: Int): Flow<List<Course>> =
        courseDao.getCoursesByDay(semesterId, dayOfWeek)

    fun getCoursesByWeek(semesterId: Long, week: Int): Flow<List<Course>> =
        courseDao.getCoursesByWeek(semesterId, week)

    suspend fun getCourseById(id: Long): Course? = courseDao.getCourseById(id)

    suspend fun insert(course: Course): Long = courseDao.insert(course)

    suspend fun insertAll(courses: List<Course>) = courseDao.insertAll(courses)

    suspend fun update(course: Course) = courseDao.update(course)

    suspend fun delete(course: Course) = courseDao.delete(course)

    suspend fun deleteBySemester(semesterId: Long) = courseDao.deleteBySemester(semesterId)

    suspend fun deleteCoursesInWeek(semesterId: Long, week: Int) =
        courseDao.deleteCoursesInWeek(semesterId, week)

    suspend fun getNextColorIndex(semesterId: Long): Int {
        return (courseDao.getMaxColorIndex(semesterId) ?: -1) + 1
    }
}

/**
 * 课时配置仓库
 */
@Singleton
class PeriodConfigRepository @Inject constructor(
    private val periodConfigDao: PeriodConfigDao
) {
    fun getAllPeriodConfigs(): Flow<List<PeriodConfig>> = periodConfigDao.getAllPeriodConfigs()

    suspend fun getAllPeriodConfigsList(): List<PeriodConfig> =
        periodConfigDao.getAllPeriodConfigsList()

    suspend fun getPeriodConfig(period: Int): PeriodConfig? =
        periodConfigDao.getPeriodConfig(period)

    suspend fun insert(config: PeriodConfig) = periodConfigDao.insert(config)

    suspend fun insertAll(configs: List<PeriodConfig>) = periodConfigDao.insertAll(configs)

    suspend fun update(config: PeriodConfig) = periodConfigDao.update(config)

    suspend fun delete(config: PeriodConfig) = periodConfigDao.delete(config)

    suspend fun deleteAll() = periodConfigDao.deleteAll()

    suspend fun getCount(): Int = periodConfigDao.getCount()
}

