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
import com.smartworkalarm.presentation.settings.SettingsScreen
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState

@Composable
fun WorkAlarmNavHost() {
    val navController = rememberNavController()
    val preferences = OnboardingPreferences(LocalContext.current)
    val start = if (preferences.isComplete()) ALARMS else ONBOARDING
    val connectionViewModel: CalendarConnectionViewModel = hiltViewModel()
    val connectionState by connectionViewModel.state.collectAsState()
    val entry by navController.currentBackStackEntryAsState()
    LaunchedEffect(entry?.destination?.route) { connectionViewModel.refresh() }
    CalendarConnectionPrompt(connectionState, connectionViewModel::refresh)
    NavHost(navController = navController, startDestination = start) {
        composable(ONBOARDING) {
            OnboardingScreen {
                preferences.complete()
                navController.navigate(ALARMS) { popUpTo(ONBOARDING) { inclusive = true } }
            }
        }
        composable(ALARMS) {
            AlarmListScreen(
                viewModel = hiltViewModel(),
                onAdd = { navController.navigate("alarm/0") },
                onEdit = { navController.navigate("alarm/$it") },
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
            SettingsScreen(connectionState) { navController.popBackStack() }
        }
    }
}

private const val ONBOARDING = "onboarding"
private const val ALARMS = "alarms"
private const val SETTINGS = "settings"
