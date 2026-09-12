package com.classschedule.di

import android.content.Context
import com.classschedule.data.db.AppDatabase
import com.classschedule.data.db.*
import com.classschedule.data.prefs.SettingsStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSettingsStore(@ApplicationContext context: Context): SettingsStore {
        return SettingsStore.get(context)
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideSemesterDao(database: AppDatabase): SemesterDao {
        return database.semesterDao()
    }

    @Provides
    @Singleton
    fun provideCourseDao(database: AppDatabase): CourseDao {
        return database.courseDao()
    }

    @Provides
    @Singleton
    fun providePeriodConfigDao(database: AppDatabase): PeriodConfigDao {
        return database.periodConfigDao()
    }

    @Provides
    @Singleton
    fun provideApiConfigDao(database: AppDatabase): ApiConfigDao {
        return database.apiConfigDao()
    }
}
