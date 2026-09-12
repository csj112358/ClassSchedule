package com.classschedule.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.classschedule.data.api.*
import com.classschedule.data.model.*
import com.classschedule.data.parser.*
import com.classschedule.data.prefs.SettingsStore
import com.classschedule.data.repository.*
import com.classschedule.data.share.PeriodSharing
import com.classschedule.reminder.ReminderScheduler
import com.classschedule.ui.theme.DEFAULT_THEME_NAME
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import retrofit2.HttpException

/**
 * 主ViewModel
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val semesterRepository: SemesterRepository,
    private val courseRepository: CourseRepository,
    private val periodConfigRepository: PeriodConfigRepository,
    private val apiConfigRepository: ApiConfigRepository,
    private val settingsStore: SettingsStore,
    private val application: android.app.Application
) : ViewModel() {

    // 当前选中的主题（持久化在 DataStore，重启后保留）
    val currentTheme: StateFlow<String> = settingsStore.themeName
        .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_THEME_NAME)

    // 当前活跃学期
    val activeSemester: StateFlow<Semester?> = semesterRepository.getActiveSemester()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // 所有学期
    val allSemesters: StateFlow<List<Semester>> = semesterRepository.getAllSemesters()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 当前周次
    private val _currentWeek = MutableStateFlow(1)
    val currentWeek: StateFlow<Int> = _currentWeek.asStateFlow()

    // 当前学期的课程（按周筛选）
    val coursesByWeek: StateFlow<List<Course>> = combine(
        activeSemester,
        _currentWeek
    ) { semester, week ->
        Pair(semester?.id ?: 0, week)
    }.flatMapLatest { (semesterId, week) ->
        if (semesterId > 0) {
            courseRepository.getCoursesByWeek(semesterId, week)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 所有课程（当前学期）
    val allCourses: StateFlow<List<Course>> = activeSemester.flatMapLatest { semester ->
        if (semester != null) {
            courseRepository.getCoursesBySemester(semester.id)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 课时配置
    val periodConfigs: StateFlow<List<PeriodConfig>> = periodConfigRepository.getAllPeriodConfigs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // API配置
    val allApiConfigs: StateFlow<List<ApiConfig>> = apiConfigRepository.getAllApiConfigs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeApiConfig: StateFlow<ApiConfig?> = apiConfigRepository.getActiveApiConfig()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // ===== 上课提醒设置 =====

    /** 提醒总开关（默认关闭） */
    val reminderEnabled: StateFlow<Boolean> = settingsStore.reminderEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** 提前多少分钟提醒（默认 20） */
    val reminderLeadMinutes: StateFlow<Int> = settingsStore.reminderLeadMinutes
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            SettingsStore.DEFAULT_LEAD_MINUTES
        )

    private fun rescheduleReminders() {
        viewModelScope.launch { ReminderScheduler.reschedule(application) }
    }

    /**
     * 打开或关闭上课提醒。
     * 开关打开（或提前分钟变化）后立即重排下一次闹钟，不必等设置流自己传播。
     */
    fun setReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setReminderEnabled(enabled)
            ReminderScheduler.reschedule(application)
        }
    }

    fun setReminderLeadMinutes(minutes: Int) {
        viewModelScope.launch {
            settingsStore.setReminderLeadMinutes(minutes)
            if (settingsStore.isReminderEnabled()) {
                ReminderScheduler.reschedule(application)
            }
        }
    }

    // 导入状态
    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    // 实时日志
    private val _importLogs = MutableStateFlow<List<String>>(emptyList())
    val importLogs: StateFlow<List<String>> = _importLogs.asStateFlow()

    private fun addLog(msg: String) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val line = "[$time] $msg"
        android.util.Log.d("ScheduleImport", msg)
        _importLogs.value = _importLogs.value + line
    }

    init {
        calculateCurrentWeek()

        // 课表、学期或提醒开关变化时重排下一次提醒闹钟
        viewModelScope.launch {
            combine(
                reminderEnabled,
                reminderLeadMinutes,
                allCourses,
                activeSemester
            ) { enabled, _, courses, semester -> Triple(enabled, courses, semester) }
                .collect { (enabled, courses, semester) ->
                    if (!enabled) return@collect
                    if (semester == null && courses.isEmpty()) return@collect
                    ReminderScheduler.reschedule(application)
                }
        }
    }

    /**
     * 计算当前是第几周
     */
    private fun calculateCurrentWeek() {
        viewModelScope.launch {
            activeSemester.collect { semester ->
                if (semester != null) {
                    if (semester.startDate <= 0) {
                        // 未设置学期起始日期时不自动跳周，默认第1周
                        _currentWeek.value = 1
                    } else {
                        val now = System.currentTimeMillis()
                        val diff = now - semester.startDate
                        val weeks = (diff / (7 * 24 * 60 * 60 * 1000L)).toInt() + 1
                        _currentWeek.value = weeks.coerceIn(1, getMaxWeek(semester))
                    }
                }
            }
        }
    }

    /**
     * 获取最大周数（未设置日期时给一个宽松上限，供手动翻周使用）
     */
    private fun getMaxWeek(semester: Semester): Int {
        if (semester.startDate <= 0) return 30
        val diff = semester.endDate - semester.startDate
        return (diff / (7 * 24 * 60 * 60 * 1000L)).toInt() + 1
    }

    /**
     * 获取指定周次、星期几的日期
     * @param week 周次（1-based）
     * @param dayOfWeek 星期几（1=周一, 7=周日）
     * @return 日期时间戳
     */
    fun getDateForWeekAndDay(week: Int, dayOfWeek: Int): Long? {
        val semester = activeSemester.value ?: return null
        // 未设置学期起始日期时不显示真实日期
        if (semester.startDate <= 0) return null
        // 学期开始日期 + (week-1)*7 + (dayOfWeek-1) 天
        val cal = Calendar.getInstance().apply {
            timeInMillis = semester.startDate
            add(Calendar.DAY_OF_YEAR, (week - 1) * 7 + (dayOfWeek - 1))
        }
        return cal.timeInMillis
    }

    /**
     * 获取当前周的日期范围字符串，如 "9/8 - 9/14"
     */
    fun getCurrentWeekDateRange(): String = getWeekDateRange(_currentWeek.value)

    /**
     * 获取指定周的日期范围字符串；未设置学期起止日期时返回空串。
     */
    fun getWeekDateRange(week: Int): String {
        val semester = activeSemester.value ?: return ""
        if (semester.startDate <= 0) return ""
        val sdf = SimpleDateFormat("M/d", Locale.getDefault())
        val cal = Calendar.getInstance().apply {
            timeInMillis = semester.startDate
            add(Calendar.DAY_OF_YEAR, (week - 1) * 7)
        }
        val monday = sdf.format(cal.time)
        cal.add(Calendar.DAY_OF_YEAR, 6)
        val sunday = sdf.format(cal.time)
        return "$monday - $sunday"
    }

    /**
     * 学期可用周数（用于第N周选择器的上限）。
     * 未设置学期起止日期时给一个宽松上限供手动选周。
     */
    fun semesterWeekCount(): Int {
        val semester = activeSemester.value ?: return 30
        if (semester.startDate <= 0) return 30
        val weeks = ((semester.endDate - semester.startDate) / (7 * 24 * 60 * 60 * 1000L)).toInt() + 1
        return weeks.coerceIn(1, 60)
    }

    /**
     * 切换周次
     */
    fun setWeek(week: Int) {
        _currentWeek.value = week
    }

    /**
     * 切换主题（写入 DataStore，重启后仍然生效）
     */
    fun setTheme(themeName: String) {
        viewModelScope.launch { settingsStore.setThemeName(themeName) }
    }

    // ===== 学期管理 =====

    fun addSemester(name: String, startDate: Long, endDate: Long, setAsActive: Boolean) {
        viewModelScope.launch {
            val semester = Semester(
                name = name,
                startDate = startDate,
                endDate = endDate,
                isActive = setAsActive
            )
            val id = semesterRepository.insert(semester)
            if (setAsActive) {
                semesterRepository.setActiveSemester(id)
            }
        }
    }

    fun updateSemester(semester: Semester) {
        viewModelScope.launch {
            semesterRepository.update(semester)
        }
    }

    fun deleteSemester(semester: Semester) {
        viewModelScope.launch {
            semesterRepository.delete(semester)
        }
    }

    fun setActiveSemester(id: Long) {
        viewModelScope.launch {
            semesterRepository.setActiveSemester(id)
        }
    }

    // ===== 课程管理 =====

    fun addCourse(
        name: String,
        room: String,
        dayOfWeek: Int,
        startPeriod: Int,
        endPeriod: Int,
        weekStart: Int,
        weekEnd: Int,
        weekParity: Int = 0,
        teacher: String = "",
        note: String = ""
    ) {
        viewModelScope.launch {
            val semester = activeSemester.value ?: return@launch
            val colorIndex = courseRepository.getNextColorIndex(semester.id)
            val course = Course(
                semesterId = semester.id,
                name = name,
                room = room,
                dayOfWeek = dayOfWeek,
                startPeriod = startPeriod,
                endPeriod = endPeriod,
                weekStart = weekStart,
                weekEnd = weekEnd,
                weekParity = weekParity,
                colorIndex = colorIndex % CourseColors.colors.size,
                teacher = teacher,
                note = note
            )
            courseRepository.insert(course)
        }
    }

    fun updateCourse(course: Course) {
        viewModelScope.launch {
            courseRepository.update(course)
        }
    }

    fun deleteCourse(course: Course) {
        viewModelScope.launch {
            courseRepository.delete(course)
        }
    }

    // ===== 课时配置 =====

    fun updatePeriodConfig(config: PeriodConfig) {
        viewModelScope.launch {
            periodConfigRepository.update(config)
        }
    }

    fun addPeriodConfig(config: PeriodConfig) {
        viewModelScope.launch {
            periodConfigRepository.insert(config)
        }
    }

    fun deletePeriodConfig(config: PeriodConfig) {
        viewModelScope.launch {
            periodConfigRepository.delete(config)
        }
    }

    // ===== API配置 =====

    fun addApiConfig(name: String, apiKey: String, baseUrl: String, modelName: String, setAsActive: Boolean) {
        viewModelScope.launch {
            val config = ApiConfig(
                name = name,
                apiKey = apiKey,
                baseUrl = baseUrl,
                modelName = modelName,
                isActive = setAsActive
            )
            val id = apiConfigRepository.insert(config)
            if (setAsActive) {
                apiConfigRepository.setActiveConfig(id)
            }
        }
    }

    fun updateApiConfig(config: ApiConfig) {
        viewModelScope.launch {
            apiConfigRepository.update(config)
        }
    }

    fun deleteApiConfig(config: ApiConfig) {
        viewModelScope.launch {
            apiConfigRepository.delete(config)
        }
    }

    fun setActiveApiConfig(id: Long) {
        viewModelScope.launch {
            apiConfigRepository.setActiveConfig(id)
        }
    }

    // ===== 课时时间分享 / 导入 =====

    private val _periodImportState = MutableStateFlow<PeriodImportState>(PeriodImportState.Idle)
    val periodImportState: StateFlow<PeriodImportState> = _periodImportState.asStateFlow()

    /** 生成分享用的课时时间文本（按节次排序） */
    suspend fun buildPeriodShareText(): String =
        PeriodSharing.encode(periodConfigRepository.getAllPeriodConfigsList())

    /** 当前课时时间文本（用于预览，数据来自已订阅的 Flow） */
    fun currentPeriodShareText(): String =
        PeriodSharing.encode(periodConfigs.value.sortedBy { it.period })

    /** 解析分享文本；失败时把原因放进 [periodImportState] */
    fun parsePeriodText(text: String, sourceLabel: String = "粘贴内容") {
        viewModelScope.launch {
            _importLogs.value = emptyList()
            _periodImportState.value = PeriodImportState.Parsing
            try {
                if (text.isBlank()) {
                    addLog("ERROR: 没有可解析的内容")
                    _periodImportState.value = PeriodImportState.Error("请先选择文件或粘贴课时时间内容")
                    return@launch
                }
                val result = withContext(Dispatchers.Default) { PeriodSharing.decode(text) }
                result.warnings.forEach { addLog("提示: $it") }
                addLog("解析完成: ${result.configs.size} 个节次（来源: $sourceLabel）")
                _periodImportState.value = PeriodImportState.Parsed(
                    configs = result.configs,
                    warnings = result.warnings,
                    sourceLabel = sourceLabel
                )
            } catch (e: Exception) {
                addLog("ERROR: ${e.message}")
                _periodImportState.value = PeriodImportState.Error(e.message ?: "解析失败")
            }
        }
    }

    /** 用解析出来的课时时间整份覆盖现有课时配置 */
    fun applyParsedPeriods() {
        val parsed = (_periodImportState.value as? PeriodImportState.Parsed) ?: return
        viewModelScope.launch {
            try {
                periodConfigRepository.deleteAll()
                periodConfigRepository.insertAll(parsed.configs)
                addLog("已写入 ${parsed.configs.size} 个节次（整份覆盖）")
                _periodImportState.value = PeriodImportState.Done(parsed.configs.size)
                // 课时时间变了，提醒的“上课时刻”也随之改变，重排一次
                ReminderScheduler.reschedule(application)
            } catch (e: Exception) {
                addLog("ERROR: ${e.message}")
                _periodImportState.value = PeriodImportState.Error("写入失败：${e.message}")
            }
        }
    }

    fun resetPeriodImportState() {
        _periodImportState.value = PeriodImportState.Idle
    }

    // ===== 课表导入（粘贴教务JSON → 本地/AI解析 → 预览 → 清空导入） =====

    /**
     * 解析粘贴的整学期课表 JSON 到课次预览
     * @param text 教务系统 response 原文
     * @param viaApi true=调用默认API(AI)解析; false=本地代码解析
     */
    fun parseScheduleJson(text: String, viaApi: Boolean) {
        viewModelScope.launch {
            _importLogs.value = emptyList()
            _importState.value = ImportState.Loading(if (viaApi) "正在调用AI解析（默认API）..." else "正在本地解析...")
            try {
                val trimmed = text.trim()
                if (trimmed.isEmpty()) {
                    addLog("ERROR: 未粘贴内容")
                    _importState.value = ImportState.Error("请先粘贴教务课表JSON")
                    return@launch
                }

                val outcome = if (viaApi) {
                    parseWithApi(trimmed)
                } else {
                    addLog("开始本地解析...")
                    val result = withContext(Dispatchers.Default) { SemesterJsonParser.parse(trimmed) }
                    addLog("本地解析完成: ${result.occurrences.size} 条课次, ${result.distinctCourseCount} 门课程")
                    result
                }

                if (outcome.occurrences.isEmpty()) {
                    addLog("ERROR: 未提取到课程")
                    _importState.value = ImportState.Error("没有解析出课程，请确认粘贴内容是否为完整课表JSON")
                    return@launch
                }

                addLog("覆盖周次范围: 第1周~第${outcome.maxWeek}周")
                _importState.value = ImportState.Success(
                    ImportPreview(
                        occurrences = outcome.occurrences,
                        sectionTimes = outcome.sectionTimes,
                        viaApi = viaApi
                    )
                )
            } catch (e: java.net.UnknownHostException) {
                addLog("ERROR: 无法解析主机 ${e.message}")
                _importState.value = ImportState.Error("无法连接服务器，请检查网络")
            } catch (e: retrofit2.HttpException) {
                addLog("ERROR: HTTP ${e.code()} ${e.message()}")
                _importState.value = ImportState.Error("HTTP ${e.code()}，请查看日志")
            } catch (e: Exception) {
                addLog("ERROR: ${e.javaClass.simpleName}: ${e.message}")
                _importState.value = ImportState.Error(e.message ?: "解析失败")
            }
        }
    }

    /**
     * 调用默认API（纯文本）解析课表 JSON
     */
    private suspend fun parseWithApi(rawJson: String): TimetableParseResult {
        val apiConfig = activeApiConfig.value
        if (apiConfig == null) {
            addLog("ERROR: 未配置API")
            throw IllegalStateException("请先在设置页面配置默认API")
        }
        addLog("API配置: ${apiConfig.baseUrl} | 模型: ${apiConfig.modelName}")

        val api = KimiApiClient.createApi(apiConfig.baseUrl)
        val request = KimiApiClient.buildTimetableParseRequest(rawJson, apiConfig.modelName)
        addLog("发送请求到 ${apiConfig.baseUrl}v1/chat/completions ...")
        val startTime = System.currentTimeMillis()
        val response = api.chatCompletion("Bearer ${apiConfig.apiKey}", request)
        val elapsed = (System.currentTimeMillis() - startTime) / 1000
        addLog("响应收到, 耗时: ${elapsed}秒")

        if (response.error != null) {
            addLog("ERROR: API返回: ${response.error.message}")
            throw IllegalStateException("API错误: ${response.error.message}")
        }
        val responseText = response.choices?.firstOrNull()?.message?.content
        if (responseText.isNullOrBlank()) {
            throw IllegalStateException("API返回内容为空，请重试")
        }
        addLog("响应长度: ${responseText.length} | 预览: ${responseText.take(160)}")

        val occurrences = KimiApiClient.parseLlmOccurrences(responseText)
        if (occurrences.isEmpty()) {
            throw IllegalStateException("AI未能提取出课程，请查看日志或改用本地解析")
        }
        addLog("AI解析出 ${occurrences.size} 条课次")

        // 节次时间尽量从原文本地提取，仅用于补齐课时配置
        val sectionTimes = withContext(Dispatchers.Default) {
            SemesterJsonParser.extractSectionTimes(rawJson)
        }
        val maxWeek = occurrences.maxOf { it.weekEnd }
        return TimetableParseResult(
            occurrences = occurrences,
            sectionTimes = sectionTimes,
            maxWeek = maxWeek,
            distinctCourseCount = occurrences.map { it.name }.distinct().size
        )
    }

    /**
     * 把预览结果写入课表。
     * @param mode WHOLE_SEMESTER=整学期导入(清空整个学期,按JSON自带周次写入)
     *             WEEK_SNAPSHOT=只把第 week 周写入(先清空该周,课程只存为该周)
     * @param week WEEK_SNAPSHOT 模式下的目标周
     */
    fun importSchedule(mode: ImportMode, week: Int = 1) {
        val preview = (importState.value as? ImportState.Success)?.preview ?: return
        viewModelScope.launch {
            _importState.value = ImportState.Loading("正在导入...")
            try {
                val semesterId = ensureActiveSemesterId()

                val rows: List<Course>
                if (mode == ImportMode.WHOLE_SEMESTER) {
                    // 整学期：清空整个学期，按 JSON 自带的周次范围写入
                    courseRepository.deleteBySemester(semesterId)
                    addLog("已清空原课表")
                    rows = buildCourseRows(preview.occurrences, semesterId)
                } else {
                    // 周快照：只覆盖第 week 周
                    val snapshot = snapshotForWeek(preview.occurrences, week)
                    if (snapshot.isEmpty()) {
                        addLog("ERROR: 第${week}周没有可导入的课程")
                        _importState.value = ImportState.Error("第${week}周没有可导入的课程，请确认粘贴内容包含该周")
                        return@launch
                    }
                    courseRepository.deleteCoursesInWeek(semesterId, week)
                    addLog("已清空第${week}周原课程")
                    rows = buildCourseRows(snapshot, semesterId)
                }

                if (rows.isEmpty()) {
                    addLog("ERROR: 没有可导入的课程")
                    _importState.value = ImportState.Error("没有可导入的课程")
                    return@launch
                }

                val maxEndPeriod = rows.maxOf { it.endPeriod }
                syncPeriodConfigs(preview.sectionTimes, maxEndPeriod)

                courseRepository.insertAll(rows)
                val courseCount = rows.map { it.name }.distinct().size
                val summary = if (mode == ImportMode.WHOLE_SEMESTER) {
                    addLog("导入完成: ${rows.size} 条课次 / $courseCount 门课程")
                    _currentWeek.value = 1
                    "已导入 ${rows.size} 条课次（$courseCount 门课程）"
                } else {
                    addLog("第${week}周导入完成: ${rows.size} 条课次 / $courseCount 门课程")
                    _currentWeek.value = week
                    "已将第 ${week} 周写入课表：${rows.size} 条课次（$courseCount 门课程）"
                }
                _importState.value = ImportState.Done(summary)
            } catch (e: Exception) {
                addLog("ERROR: ${e.javaClass.simpleName}: ${e.message}")
                _importState.value = ImportState.Error("导入失败：${e.message}")
            }
        }
    }

    /**
     * 批量按周导入：items 是已解析并折叠为单周快照的周文件。
     * 每个周先清空该周旧课，再写入该周快照；跨周同一门课分配一致颜色。
     */
    fun importWeeklySnapshots(items: List<WeeklyImport>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            val distinctWeeks = items.map { it.week }.distinct().sorted()
            _importState.value = ImportState.Loading("正在批量导入 ${distinctWeeks.size} 个周...")
            try {
                val semesterId = ensureActiveSemesterId()
                // 同一周被选多次时，后选的覆盖先选的
                val unique = items.associateBy { it.week }.values.toList()
                val allOccurrences = unique.flatMap { it.occurrences }
                if (allOccurrences.isEmpty()) {
                    addLog("ERROR: 没有可导入的课程")
                    _importState.value = ImportState.Error("没有可导入的课程")
                    return@launch
                }
                val rows = buildCourseRows(allOccurrences, semesterId)
                unique.forEach { courseRepository.deleteCoursesInWeek(semesterId, it.week) }
                val sectionTimes = unique.fold(emptyMap<Int, Pair<String, String>>()) { acc, it -> acc + it.sectionTimes }
                syncPeriodConfigs(sectionTimes, rows.maxOf { it.endPeriod })
                courseRepository.insertAll(rows)

                val courseCount = rows.map { it.name }.distinct().size
                val weekText = distinctWeeks.joinToString("、") { "第${it}周" }
                addLog("批量导入完成: ${distinctWeeks.size} 个周, ${rows.size} 条课次 / $courseCount 门课程")
                _currentWeek.value = distinctWeeks.first()
                _importState.value = ImportState.Done(
                    "已导入 ${distinctWeeks.size} 个周（$weekText），共 ${rows.size} 条课次 / $courseCount 门课程"
                )
            } catch (e: Exception) {
                addLog("ERROR: ${e.javaClass.simpleName}: ${e.message}")
                _importState.value = ImportState.Error("批量导入失败：${e.message}")
            }
        }
    }

    /**
     * 若没有当前学期则自动创建一个（无需设置起止日期）
     */
    private suspend fun ensureActiveSemesterId(): Long {
        activeSemester.value?.let { return it.id }
        val name = if (allSemesters.value.isEmpty()) "我的课表" else "课表 ${SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())}"
        val id = semesterRepository.insert(Semester(name = name, startDate = 0, endDate = 0, isActive = true))
        semesterRepository.setActiveSemester(id)
        addLog("自动创建学期: $name")
        return id
    }

    /**
     * 根据 JSON 里的节次真实时间补齐/更新课时配置，保证高节次（含晚上）也能显示。
     */
    private suspend fun syncPeriodConfigs(sectionTimes: Map<Int, Pair<String, String>>, maxPeriod: Int) {
        val existing = periodConfigRepository.getAllPeriodConfigsList().associateBy { it.period }
        val defaults = defaultPeriodTimes(maxPeriod)
        val rows = (1..maxPeriod).map { p ->
            val t = sectionTimes[p] ?: defaults[p]!!
            PeriodConfig(
                period = p,
                startTime = t.first,
                endTime = t.second,
                label = existing[p]?.label ?: ""
            )
        }
        periodConfigRepository.insertAll(rows)
        addLog("课时配置已同步: 1~${maxPeriod}节")
    }

    /** 兜底节次时间（上午/下午/晚上近似，仅在真实时间缺失时使用） */
    private fun defaultPeriodTimes(maxPeriod: Int): Map<Int, Pair<String, String>> {
        fun hhmm(total: Int): String = String.format(java.util.Locale.US, "%02d:%02d", total / 60, total % 60)
        fun startOf(p: Int): Int = when (p) {
            in 1..4 -> 8 * 60 + (p - 1) * 55
            in 5..8 -> 14 * 60 + (p - 5) * 55
            else -> 18 * 60 + 30 + (p - 9) * 50
        }
        return (1..maxPeriod).associate { p ->
            val start = startOf(p)
            p to (hhmm(start) to hhmm(start + 45))
        }
    }

    /**
     * 课次 -> Course 行；同一门逻辑课（去重键）分配一致颜色，跨周颜色统一。
     */
    private fun buildCourseRows(occurrences: List<ParsedOccurrence>, semesterId: Long): List<Course> {
        val colorKeys = LinkedHashMap<String, Int>()
        var nextColor = 0
        return occurrences.map { o ->
            val key = "${o.name}|${o.teacher}|${o.room}|${o.dayOfWeek}|${o.startPeriod}-${o.endPeriod}"
            val colorIndex = colorKeys.getOrPut(key) { nextColor++ % CourseColors.colors.size }
            Course(
                semesterId = semesterId,
                name = o.name,
                room = o.room,
                dayOfWeek = o.dayOfWeek,
                startPeriod = o.startPeriod,
                endPeriod = o.endPeriod,
                weekStart = o.weekStart,
                weekEnd = o.weekEnd,
                weekParity = o.weekParity,
                colorIndex = colorIndex,
                teacher = o.teacher
            )
        }
    }

    fun resetImportState() {
        _importState.value = ImportState.Idle
    }

    fun clearLogs() {
        _importLogs.value = emptyList()
    }
}

/**
 * 导入状态
 */
sealed class ImportState {
    object Idle : ImportState()
    data class Loading(val message: String = "处理中...") : ImportState()
    data class Success(val preview: ImportPreview) : ImportState()
    data class Done(val message: String) : ImportState()
    data class Error(val message: String) : ImportState()
}

/**
 * 导入方式：整学期导入 / 第N周快照导入
 */
enum class ImportMode { WHOLE_SEMESTER, WEEK_SNAPSHOT }

/**
 * 课时时间导入状态
 */
sealed class PeriodImportState {
    object Idle : PeriodImportState()
    object Parsing : PeriodImportState()
    data class Parsed(
        val configs: List<PeriodConfig>,
        val warnings: List<String> = emptyList(),
        val sourceLabel: String = ""
    ) : PeriodImportState()

    data class Done(val count: Int) : PeriodImportState()
    data class Error(val message: String) : PeriodImportState()
}

/**
 * 解析预览结果
 */
data class ImportPreview(
    val occurrences: List<ParsedOccurrence>,
    val sectionTimes: Map<Int, Pair<String, String>> = emptyMap(),
    val viaApi: Boolean = false
)

/**
 * 判断某课次在第 week 周是否会上课（含单双周）
 */
fun occursInWeek(occ: ParsedOccurrence, week: Int): Boolean {
    if (week < occ.weekStart || week > occ.weekEnd) return false
    return when (occ.weekParity) {
        0 -> true
        1 -> week % 2 == 1
        2 -> week % 2 == 0
        else -> true
    }
}

/**
 * 取出一份课次列表里“在第 week 周上课”的课次（整学期导入时的预览过滤）
 */
fun occurrencesInWeek(occurrences: List<ParsedOccurrence>, week: Int): List<ParsedOccurrence> =
    occurrences.filter { occursInWeek(it, week) }

/**
 * 把整份课次折叠成“第 week 周”的单周快照：
 * 只保留该周会上课的课次，并把周次统一写成 week..week、双周标记清零。
 */
fun snapshotForWeek(occurrences: List<ParsedOccurrence>, week: Int): List<ParsedOccurrence> =
    occurrencesInWeek(occurrences, week).map { occ ->
        occ.copy(weekStart = week, weekEnd = week, weekParity = 0)
    }

/**
 * 单个周文件解析结果（课次已折叠为单周快照，周次统一为 week..week）
 */
data class WeeklyImport(
    val week: Int,
    val fileName: String,
    val occurrences: List<ParsedOccurrence>,
    val sectionTimes: Map<Int, Pair<String, String>> = emptyMap()
)

/**
 * 从 txt 文件名识别要导入的周数。
 * 支持 "5.txt" / "第5周.txt" / "week5.txt" / "5周.txt" 等写法；识别不到返回 null。
 */
fun weekFromFileName(fileName: String): Int? {
    val base = fileName.substringBeforeLast('.')
    Regex("""第\s*(\d+)\s*周""").find(base)?.let { return it.groupValues[1].toIntOrNull() }
    Regex("""(\d+)\s*周""").find(base)?.let { return it.groupValues[1].toIntOrNull() }
    return Regex("""\d+""").findAll(base).mapNotNull { it.value.toIntOrNull() }.lastOrNull()
}

/**
 * 工具函数
 */
fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

fun formatTime(timeStr: String): String {
    return timeStr
}

fun getDayName(dayOfWeek: Int): String {
    return when (dayOfWeek) {
        1 -> "周一"
        2 -> "周二"
        3 -> "周三"
        4 -> "周四"
        5 -> "周五"
        6 -> "周六"
        7 -> "周日"
        else -> ""
    }
}
