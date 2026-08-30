package com.darcloud.omarai.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle

enum class MainDestination(val label: String) {
    HOME("Home"), BUSINESS("Business"), TASKS("Tasks"), SETTINGS("Settings")
}

@Composable
fun OmarAiRoot(viewModel: OmarViewModel) {
    val onboarding by viewModel.onboardingComplete.collectAsStateWithLifecycle()
    if (onboarding == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (onboarding == false) {
        OnboardingScreen(onFinish = viewModel::finishOnboarding)
        return
    }

    var destination by remember { mutableStateOf(MainDestination.HOME) }
    val snackbar = remember { SnackbarHostState() }
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    LaunchedEffect(feedback) {
        feedback?.let {
            snackbar.showSnackbar(it)
            viewModel.clearFeedback()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                MainDestination.entries.forEach { item ->
                    val icon = when (item) {
                        MainDestination.HOME -> Icons.Rounded.Home
                        MainDestination.BUSINESS -> Icons.Rounded.BusinessCenter
                        MainDestination.TASKS -> Icons.Rounded.TaskAlt
                        MainDestination.SETTINGS -> Icons.Rounded.Settings
                    }
                    NavigationBarItem(
                        selected = destination == item,
                        onClick = { destination = item },
                        icon = { Icon(icon, item.label) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        when (destination) {
            MainDestination.HOME -> HomeScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(padding),
                onOpenBusiness = { destination = MainDestination.BUSINESS },
                onOpenTasks = { destination = MainDestination.TASKS },
                onOpenSettings = { destination = MainDestination.SETTINGS },
            )
            MainDestination.BUSINESS -> BusinessScreen(viewModel, Modifier.padding(padding))
            MainDestination.TASKS -> TaskCenterScreen(viewModel, Modifier.padding(padding))
            MainDestination.SETTINGS -> SettingsScreen(viewModel, Modifier.padding(padding))
        }
    }
}
