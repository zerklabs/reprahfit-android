package com.reprahfit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.reprahfit.workers.WeightRefreshWorker
import java.util.concurrent.TimeUnit
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reprahfit.ui.theme.ReprahfitTheme
import kotlinx.coroutines.launch

private enum class Screen { Detailed, Simple, History, Settings }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scheduleWeightRefresh()
        setContent {
            ReprahfitTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var screen by rememberSaveable { mutableStateOf(Screen.Simple) }
                    val drawerState = rememberDrawerState(DrawerValue.Closed)
                    val scope = rememberCoroutineScope()
                    val viewModel: RideViewModel = viewModel()

                    BackHandler(
                        enabled = screen != Screen.Simple
                    ) {
                        screen = Screen.Simple
                    }

                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        drawerContent = {
                            ModalDrawerSheet(
                                modifier = Modifier.width(260.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .windowInsetsPadding(WindowInsets.statusBars)
                                        .padding(16.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.app_name),
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))
                                    HorizontalDivider()
                                    Spacer(modifier = Modifier.height(8.dp))

                                    NavigationDrawerItem(
                                        label = { Text(stringResource(R.string.history_cta)) },
                                        selected = screen == Screen.History,
                                        onClick = {
                                            screen = Screen.History
                                            scope.launch { drawerState.close() }
                                        }
                                    )

                                    NavigationDrawerItem(
                                        label = {
                                            Text(
                                                stringResource(
                                                    R.string.drawer_settings_title
                                                )
                                            )
                                        },
                                        selected = screen == Screen.Settings,
                                        onClick = {
                                            screen = Screen.Settings
                                            scope.launch { drawerState.close() }
                                        }
                                    )
                                }
                            }
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(WindowInsets.statusBars)
                        ) {
                            // Top bar with menu icon
                            IconButton(
                                onClick = { scope.launch { drawerState.open() } },
                                modifier = Modifier.padding(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Menu,
                                    contentDescription = stringResource(
                                        R.string.drawer_menu_description
                                    )
                                )
                            }

                            // Screen content
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(bottom = 40.dp)
                            ) {
                                when (screen) {
                                    Screen.Detailed -> OutdoorRideScreen(viewModel)
                                    Screen.Simple -> SimpleDashboardScreen(viewModel)
                                    Screen.History -> RideHistoryScreen(
                                        onBack = { screen = Screen.Simple },
                                        viewModel = viewModel
                                    )
                                    Screen.Settings -> SettingsScreen(
                                        onBack = { screen = Screen.Simple },
                                        viewModel = viewModel
                                    )
                                }
                            }

                            // Bottom view toggle
                            if (screen == Screen.Simple || screen == Screen.Detailed) {
                                TextButton(
                                    onClick = {
                                        screen = if (screen == Screen.Simple) {
                                            Screen.Detailed
                                        } else {
                                            Screen.Simple
                                        }
                                    },
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                ) {
                                    Text(
                                        text = if (screen == Screen.Simple) {
                                            stringResource(R.string.switch_to_detailed_cta)
                                        } else {
                                            stringResource(R.string.switch_to_simple_cta)
                                        },
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun scheduleWeightRefresh() {
        val request = PeriodicWorkRequestBuilder<WeightRefreshWorker>(6, TimeUnit.HOURS, 30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "weight_refresh",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
