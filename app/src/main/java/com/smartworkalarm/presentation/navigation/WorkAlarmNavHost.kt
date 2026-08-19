package com.smartworkalarm.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.smartworkalarm.presentation.alarmedit.AlarmEditScreen
import com.smartworkalarm.presentation.alarmlist.AlarmListScreen
import com.smartworkalarm.presentation.onboarding.OnboardingPreferences
import com.smartworkalarm.presentation.onboarding.OnboardingScreen

@Composable
fun WorkAlarmNavHost() {
    val navController = rememberNavController()
    val preferences = OnboardingPreferences(LocalContext.current)
    val start = if (preferences.isComplete()) ALARMS else ONBOARDING
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
    }
}

private const val ONBOARDING = "onboarding"
private const val ALARMS = "alarms"
