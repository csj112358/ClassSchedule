package com.classschedule.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.classschedule.ui.theme.DEFAULT_THEME_NAME
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** 应用级偏好（主题、上课提醒）。进程内单例，小部件/闹钟/通知等非 UI 组件也能直接读取。 */
private val Context.appSettings: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

/**
 * 全局设置。
 *
 * 说明：本类不依赖 Hilt，任何位置（Glance 小部件、BroadcastReceiver、Worker）都可以用
 * [SettingsStore.get] 取到实例；因为 DataStore 读取是挂起函数，所以统一用 suspend 接口。
 */
class SettingsStore internal constructor(private val context: Context) {

    /** 主题色名，与设置页「主题颜色」一致 */
    val themeName: Flow<String> = context.appSettings.data.map { prefs ->
        prefs[KEY_THEME] ?: DEFAULT_THEME_NAME
    }

    /** 上课提醒总开关（默认关闭，由用户主动开启） */
    val reminderEnabled: Flow<Boolean> = context.appSettings.data.map { prefs ->
        prefs[KEY_REMINDER_ENABLED] ?: false
    }

    /** 提前多少分钟提醒（默认 20 分钟） */
    val reminderLeadMinutes: Flow<Int> = context.appSettings.data.map { prefs ->
        (prefs[KEY_REMINDER_LEAD_MINUTES] ?: DEFAULT_LEAD_MINUTES)
            .coerceIn(MIN_LEAD_MINUTES, MAX_LEAD_MINUTES)
    }

    suspend fun currentThemeName(): String = themeName.first()

    suspend fun isReminderEnabled(): Boolean = reminderEnabled.first()

    suspend fun currentLeadMinutes(): Int = reminderLeadMinutes.first()

    suspend fun setThemeName(name: String) {
        context.appSettings.edit { it[KEY_THEME] = name }
    }

    suspend fun setReminderEnabled(enabled: Boolean) {
        context.appSettings.edit { it[KEY_REMINDER_ENABLED] = enabled }
    }

    suspend fun setReminderLeadMinutes(minutes: Int) {
        context.appSettings.edit {
            it[KEY_REMINDER_LEAD_MINUTES] = minutes.coerceIn(MIN_LEAD_MINUTES, MAX_LEAD_MINUTES)
        }
    }

    companion object {
        const val DEFAULT_LEAD_MINUTES = 20
        const val MIN_LEAD_MINUTES = 1
        const val MAX_LEAD_MINUTES = 120

        /** 设置页里可一键选择的提前档位 */
        val LEAD_PRESETS = listOf(5, 10, 15, 20, 30)

        private val KEY_THEME = stringPreferencesKey("theme_name")
        private val KEY_REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        private val KEY_REMINDER_LEAD_MINUTES = intPreferencesKey("reminder_lead_minutes")

        @Volatile
        private var instance: SettingsStore? = null

        fun get(context: Context): SettingsStore {
            return instance ?: synchronized(this) {
                instance ?: SettingsStore(context.applicationContext).also { instance = it }
            }
        }
    }
}
