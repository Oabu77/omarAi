package com.darcloud.omarai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

enum class MainDestination(val label: String) {
    HOME("Home"), BUSINESS("Business"), TASKS("Tasks"), SETTINGS("Settings")
}

@Composable
fun OmarAiRoot(viewModel: OmarViewModel) {
    val onboarding by viewModel.onboardingComplete.collectAsStateWithLifecycle()
    if (onboarding == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                Modifier.semantics { contentDescription = "Loading Omar AI" },
            )
        }
        return
    }
    if (onboarding == false) {
        OnboardingScreen(onFinish = viewModel::finishOnboarding)
        return
    }

    var destination by rememberSaveable { mutableStateOf(MainDestination.HOME) }
    val snackbar = remember { SnackbarHostState() }
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    LaunchedEffect(feedback) {
        feedback?.let {
            snackbar.showSnackbar(it)
            viewModel.clearFeedback()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp
        Row(Modifier.fillMaxSize()) {
            if (useNavigationRail) {
                OmarNavigationRail(
                    selected = destination,
                    onSelected = { destination = it },
                )
            }
            Scaffold(
                modifier = Modifier.weight(1f),
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    if (!useNavigationRail) {
                        OmarNavigationBar(
                            selected = destination,
                            onSelected = { destination = it },
                        )
                    }
                },
            ) { padding ->
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    DestinationContent(
                        destination = destination,
                        viewModel = viewModel,
                        modifier = Modifier.widthIn(max = 1120.dp).fillMaxSize(),
                        onDestination = { destination = it },
                    )
                }
            }
        }
    }
}

@Composable
private fun OmarNavigationBar(
    selected: MainDestination,
    onSelected: (MainDestination) -> Unit,
) {
    NavigationBar {
        MainDestination.entries.forEach { item ->
            NavigationBarItem(
                selected = selected == item,
                onClick = { onSelected(item) },
                icon = { Icon(destinationIcon(item), contentDescription = null) },
                label = { Text(item.label, maxLines = 1) },
            )
        }
    }
}

@Composable
private fun OmarNavigationRail(
    selected: MainDestination,
    onSelected: (MainDestination) -> Unit,
) {
    NavigationRail(modifier = Modifier.widthIn(min = 96.dp)) {
        Spacer(Modifier.height(16.dp))
        OmarMark(size = 40)
        Spacer(Modifier.height(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            MainDestination.entries.forEach { item ->
                NavigationRailItem(
                    selected = selected == item,
                    onClick = { onSelected(item) },
                    icon = { Icon(destinationIcon(item), contentDescription = null) },
                    label = { Text(item.label, maxLines = 2, textAlign = TextAlign.Center) },
                )
            }
        }
    }
}

private fun destinationIcon(destination: MainDestination): ImageVector = when (destination) {
    MainDestination.HOME -> Icons.Rounded.Home
    MainDestination.BUSINESS -> Icons.Rounded.BusinessCenter
    MainDestination.TASKS -> Icons.Rounded.TaskAlt
    MainDestination.SETTINGS -> Icons.Rounded.Settings
}

@Composable
private fun DestinationContent(
    destination: MainDestination,
    viewModel: OmarViewModel,
    modifier: Modifier,
    onDestination: (MainDestination) -> Unit,
) {
    val paneModifier = modifier.semantics { paneTitle = "${destination.label} screen" }
    when (destination) {
        MainDestination.HOME -> HomeScreen(
            viewModel = viewModel,
            modifier = paneModifier,
            onOpenBusiness = { onDestination(MainDestination.BUSINESS) },
            onOpenTasks = { onDestination(MainDestination.TASKS) },
            onOpenSettings = { onDestination(MainDestination.SETTINGS) },
        )
        MainDestination.BUSINESS -> BusinessScreen(viewModel, paneModifier)
        MainDestination.TASKS -> TaskCenterScreen(viewModel, paneModifier)
        MainDestination.SETTINGS -> SettingsScreen(viewModel, paneModifier)
    }
}
