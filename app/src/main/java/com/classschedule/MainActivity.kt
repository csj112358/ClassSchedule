package com.classschedule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.classschedule.ui.components.BottomNavItem
import com.classschedule.ui.components.GlassBottomBar
import com.classschedule.ui.screens.*
import com.classschedule.ui.theme.ClassScheduleTheme
import com.classschedule.ui.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: MainViewModel = hiltViewModel()
            val currentTheme by viewModel.currentTheme.collectAsState()

            ClassScheduleTheme(themeName = currentTheme) {
                MainApp(viewModel)
            }
        }
    }
}

/**
 * 主应用导航
 */
@Composable
fun MainApp(viewModel: MainViewModel) {
    val navController = rememberNavController()

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
        containerColor = MaterialTheme.colorScheme.background,
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
                    }
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
        }
    }
}
