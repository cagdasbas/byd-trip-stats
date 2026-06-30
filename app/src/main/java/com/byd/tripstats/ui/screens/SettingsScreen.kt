package com.byd.tripstats.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.tripstats.data.preferences.PreferencesManager
import com.byd.tripstats.ui.screens.settings.AboutTab
import com.byd.tripstats.ui.screens.settings.AppManagementTab
import com.byd.tripstats.ui.screens.settings.AppPreferencesTab
import com.byd.tripstats.ui.screens.settings.ConnectionsTab
import com.byd.tripstats.ui.theme.AccelerationOrange
import com.byd.tripstats.ui.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel         : DashboardViewModel,
    onNavigateBack    : () -> Unit,
    onNavigateToBackup: () -> Unit,
    onNavigateToTripGoals: () -> Unit = {}
) {
    val context           = LocalContext.current
    val preferencesManager = remember { PreferencesManager(context) }
    val scope             = rememberCoroutineScope()

    val snackbarHostState  = remember { SnackbarHostState() }
    var selectedTab       by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("App", "Connections", "Preferences", "About & FAQ")

    val updateInfo by viewModel.updateInfo.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", modifier = Modifier.size(28.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TabRow(selectedTabIndex = selectedTab, modifier = Modifier.fillMaxWidth()) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick  = { selectedTab = index },
                        text = {
                            if (index == 3 && updateInfo != null) {
                                BadgedBox(
                                    badge = {
                                        Badge(
                                            containerColor = AccelerationOrange,
                                            modifier = Modifier.offset(x = 18.dp, y = (-2).dp)
                                        ) {
                                            Text(
                                                text = "1",
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                ) {
                                    Text(
                                        title,
                                        fontSize   = 17.sp,
                                        fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            } else {
                                Text(
                                    title,
                                    fontSize   = 17.sp,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedTab) {
                    0 -> AppManagementTab(
                        viewModel         = viewModel,
                        context           = context,
                        onNavigateToBackup = onNavigateToBackup,
                        scope             = scope
                    )
                    1 -> ConnectionsTab()
                    2 -> AppPreferencesTab(
                        viewModel = viewModel,
                        preferencesManager = preferencesManager,
                        onNavigateToTripGoals = onNavigateToTripGoals
                    )
                    3 -> AboutTab(viewModel = viewModel)
                }
            }
        }
    }
}
