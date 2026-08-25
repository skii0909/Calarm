package com.smartworkalarm.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.smartworkalarm.presentation.alarmedit.AlarmEditScreen
import com.smartworkalarm.presentation.alarmlist.AlarmListScreen
import com.smartworkalarm.presentation.onboarding.OnboardingPreferences
import com.smartworkalarm.presentation.onboarding.OnboardingScreen
import com.smartworkalarm.presentation.calendar.CalendarConnectionPrompt
import com.smartworkalarm.presentation.calendar.CalendarConnectionViewModel
import com.smartworkalarm.presentation.calendar.calendarConnectionPromptRequestKey
import com.smartworkalarm.presentation.settings.SettingsRoute
import com.smartworkalarm.presentation.shift.ShiftGroupScreen
import com.smartworkalarm.presentation.shift.ShiftTypeEditScreen
import com.smartworkalarm.permissions.RequiredPermission
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier

@Composable
fun WorkAlarmNavHost(
    missingRequiredPermissions: List<RequiredPermission> = emptyList(),
    onRequiredPermissionAction: (RequiredPermission) -> Unit = {},
    onBatteryOptimizationAction: () -> Unit = {},
) {
    val navController = rememberNavController()
    val preferences = OnboardingPreferences(LocalContext.current)
    val start = if (preferences.isComplete()) ALARMS else ONBOARDING
    val connectionViewModel: CalendarConnectionViewModel = hiltViewModel()
    val connectionState by connectionViewModel.state.collectAsState()
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    LaunchedEffect(route, missingRequiredPermissions) {
        connectionViewModel.refresh()
    }
    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
    CalendarConnectionPrompt(
        state = connectionState,
        requiredPermissionsMissing = missingRequiredPermissions.isNotEmpty(),
        requestKey = calendarConnectionPromptRequestKey(route, entry?.id),
        onOpenSettings = {
            if (entry?.destination?.route != SETTINGS) navController.navigate(SETTINGS)
        },
    )
    NavHost(navController = navController, startDestination = start) {
        composable(ONBOARDING) {
            OnboardingScreen(
                onComplete = {
                    preferences.complete()
                    navController.navigate(ALARMS) { popUpTo(ONBOARDING) { inclusive = true } }
                },
                onOpenCalendarSettings = {
                    preferences.complete()
                    navController.navigate(ALARMS) { popUpTo(ONBOARDING) { inclusive = true } }
                    navController.navigate(SETTINGS)
                },
            )
        }
        composable(ALARMS) {
            AlarmListScreen(
                viewModel = hiltViewModel(),
                shiftViewModel = hiltViewModel(),
                onAdd = { navController.navigate("alarm/0") },
                onEdit = { navController.navigate("alarm/$it") },
                onAddShiftGroup = { navController.navigate("shift-group/0") },
                onEditShiftGroup = { navController.navigate("shift-group/$it") },
                onSettings = { navController.navigate(SETTINGS) },
            )
        }
        composable(
            route = "alarm/{alarmId}",
            arguments = listOf(navArgument("alarmId") { type = NavType.LongType }),
        ) { entry ->
            AlarmEditScreen(
                viewModel = hiltViewModel(),
                alarmId = entry.arguments?.getLong("alarmId") ?: 0L,
                onDone = { navController.popBackStack() },
            )
        }
        composable(SETTINGS) {
            SettingsRoute(
                onBack = { navController.popBackStack() },
                missingRequiredPermissions = missingRequiredPermissions,
                onPermissionAction = onRequiredPermissionAction,
                onBatteryOptimizationAction = onBatteryOptimizationAction,
                onCalendarConnectionChanged = connectionViewModel::refresh,
            )
        }
        composable(
            route = "shift-group/{groupId}",
            arguments = listOf(navArgument("groupId") { type = NavType.LongType }),
        ) { entry ->
            val routeGroupId = entry.arguments?.getLong("groupId") ?: 0L
            ShiftGroupScreen(
                viewModel = hiltViewModel(),
                groupId = routeGroupId,
                onDone = { navController.popBackStack() },
                onEditShiftType = { groupId, shiftTypeId ->
                    if (routeGroupId == 0L) {
                        navController.popBackStack()
                        navController.navigate("shift-group/$groupId")
                    }
                    navController.navigate("shift-type/$groupId/$shiftTypeId")
                },
            )
        }
        composable(
            route = "shift-type/{groupId}/{shiftTypeId}",
            arguments = listOf(
                navArgument("groupId") { type = NavType.LongType },
                navArgument("shiftTypeId") { type = NavType.LongType },
            ),
        ) { entry ->
            ShiftTypeEditScreen(
                viewModel = hiltViewModel(),
                groupId = entry.arguments?.getLong("groupId") ?: 0L,
                shiftTypeId = entry.arguments?.getLong("shiftTypeId") ?: 0L,
                onDone = { navController.popBackStack() },
            )
        }
    }
    }
}

private const val ONBOARDING = "onboarding"
private const val ALARMS = "alarms"
private const val SETTINGS = "settings"
