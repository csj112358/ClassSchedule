package com.classschedule

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.classschedule.ui.components.AmbientGlow
import com.classschedule.ui.components.BottomNavItem
import com.classschedule.ui.components.GlassBottomBar
import com.classschedule.ui.glass.globalGlassSheen
import com.classschedule.ui.screens.*
import com.classschedule.ui.theme.ClassScheduleTheme
import com.classschedule.ui.theme.LocalThemeName
import com.classschedule.ui.theme.resolveTheme
import com.classschedule.ui.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** 点上课提醒通知进来时置为 true，供界面切回课表页 */
    private val openScheduleRequest = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // edge-to-edge：内容铺到底，系统栏透明，悬浮玻璃层才有东西可以透
        enableEdgeToEdge()
        openScheduleRequest.value = intent?.getBooleanExtra(EXTRA_OPEN_SCHEDULE, false) == true
        setContent {
            val viewModel: MainViewModel = hiltViewModel()
            val currentTheme by viewModel.currentTheme.collectAsState()

            ClassScheduleTheme(themeName = currentTheme) {
                MainApp(viewModel, openScheduleRequest = openScheduleRequest.value)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 应用已在后台时，通知点击不会重建 Activity，需要在这里接收
        if (intent.getBooleanExtra(EXTRA_OPEN_SCHEDULE, false)) {
            setIntent(intent)
            openScheduleRequest.value = true
        }
    }

    companion object {
        /** 通知点击进入时带上，用于定位到课表页 */
        const val EXTRA_OPEN_SCHEDULE = "extra_open_schedule"
    }
}

/**
 * 主应用导航
 */
@Composable
fun MainApp(viewModel: MainViewModel, openScheduleRequest: Boolean = false) {
    val navController = rememberNavController()
    // 从上课提醒通知点进来时，切回课表页
    LaunchedEffect(openScheduleRequest) {
        if (openScheduleRequest) {
            navController.navigate("schedule") {
                popUpTo("schedule") { inclusive = true }
            }
        }
    }

    // 底部导航项
    val bottomNavItems = listOf(
        BottomNavItem("课表", Icons.Default.CalendarMonth),
        BottomNavItem("导入", Icons.Default.ContentPaste),
        BottomNavItem("设置", Icons.Default.Settings)
    )

    // 当前选中的底部导航项
    var selectedNavIndex by remember { mutableIntStateOf(0) }

    // 是否显示底部导航栏
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in listOf("schedule", "recognition", "settings")

    Scaffold(
        // 透明：背景由最外层那层柔光负责，Scaffold 不该再盖一层
        containerColor = Color.Transparent,
        bottomBar = {
            if (showBottomBar) {
                GlassBottomBar(
                    items = bottomNavItems,
                    selectedIndex = selectedNavIndex,
                    onItemSelected = { index ->
                        selectedNavIndex = index
                        when (index) {
                            0 -> navController.navigate("schedule") {
                                popUpTo("schedule") { inclusive = true }
                            }
                            1 -> navController.navigate("recognition") {
                                popUpTo("schedule")
                            }
                            2 -> navController.navigate("settings") {
                                popUpTo("schedule")
                            }
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        // 最外层：整体是一块玻璃
        //   1. 主题底色 —— 玻璃所在的环境
        //   2. 极光      —— 玻璃后面会缓慢漂移的光
        //   3. 全局高光带 —— 从**整块玻璃**上扫过的淡光（覆盖所有内容，不跟任何卡片走）
        val palette = resolveTheme(LocalThemeName.current)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            AmbientGlow(palette.primary, palette.tertiary)

            // 内容之上再盖一层全局高光：这是"一块玻璃"而非"一堆卡片"的关键
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .globalGlassSheen()
            ) {
                NavHost(
                    navController = navController,
                    startDestination = "schedule",
                    modifier = Modifier.padding(paddingValues)
                ) {
                // 课程表页面
                composable("schedule") {
                    ScheduleScreen(
                        viewModel = viewModel,
                        onAddCourse = {
                            navController.navigate("course_edit/-1")
                        },
                        onEditCourse = { courseId ->
                            navController.navigate("course_edit/$courseId")
                        },
                        onNavigateToSettings = {
                            selectedNavIndex = 2
                            navController.navigate("settings") {
                                popUpTo("schedule")
                            }
                        }
                    )
                }

            // 识别页面
            composable("recognition") {
                RecognitionScreen(
                    viewModel = viewModel,
                    onBack = {
                        navController.popBackStack()
                        selectedNavIndex = 0
                    }
                )
            }

            // 设置页面
            composable("settings") {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateToSemester = {
                        navController.navigate("semester")
                    },
                    onNavigateToPeriodConfig = {
                        navController.navigate("period_config")
                    },
                    onNavigateToPeriodSharing = {
                        navController.navigate("period_sharing")
                    }
                )
            }

            // 分享 / 导入课时时间
            composable("period_sharing") {
                PeriodSharingScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            // 学期管理页面
            composable("semester") {
                SemesterScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            // 课时配置页面
            composable("period_config") {
                PeriodConfigScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            // 课程编辑页面
            composable(
                route = "course_edit/{courseId}",
                arguments = listOf(
                    navArgument("courseId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) { backStackEntry ->
                val courseId = backStackEntry.arguments?.getLong("courseId") ?: -1L
                CourseEditScreen(
                    viewModel = viewModel,
                    courseId = if (courseId > 0) courseId else null,
                    onBack = { navController.popBackStack() }
                )
            }
                } // NavHost
            } // Box（全局高光带）
        } // Box（玻璃基底：底色 + 极光）
    } // Scaffold
} // MainApp