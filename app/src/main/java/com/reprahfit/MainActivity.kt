package com.reprahfit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reprahfit.ui.theme.ReprahfitTheme
import kotlinx.coroutines.launch

private enum class Screen { Detailed, Simple, History }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                    val uiState by viewModel.uiState.collectAsState()

                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        drawerContent = {
                            ModalDrawerSheet(
                                modifier = Modifier.width(300.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .windowInsetsPadding(WindowInsets.statusBars)
                                        .verticalScroll(rememberScrollState())
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

                                    Spacer(modifier = Modifier.height(16.dp))
                                    HorizontalDivider()
                                    Spacer(modifier = Modifier.height(16.dp))

                                    Text(
                                        text = stringResource(R.string.drawer_settings_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    OutlinedTextField(
                                        value = uiState.weightInput,
                                        onValueChange = { viewModel.updateWeight(it) },
                                        label = { Text(stringResource(R.string.weight_label)) },
                                        supportingText = {
                                            Text(stringResource(R.string.weight_supporting))
                                        },
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Decimal
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Text(
                                        text = stringResource(R.string.hr_section_title),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = stringResource(R.string.hr_section_note),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    OutlinedTextField(
                                        value = uiState.ageInput,
                                        onValueChange = { viewModel.updateAge(it) },
                                        label = { Text(stringResource(R.string.age_label)) },
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Number
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    OutlinedTextField(
                                        value = uiState.heartRateInput,
                                        onValueChange = { viewModel.updateHeartRate(it) },
                                        label = { Text(stringResource(R.string.heart_rate_label)) },
                                        supportingText = {
                                            Text(stringResource(R.string.heart_rate_supporting))
                                        },
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Number
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stringResource(R.string.sex_label),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        FilterChip(
                                            selected = uiState.sex == Sex.Male,
                                            onClick = {
                                                viewModel.updateSex(
                                                    if (uiState.sex == Sex.Male) null else Sex.Male
                                                )
                                            },
                                            label = { Text(stringResource(R.string.sex_male)) }
                                        )
                                        FilterChip(
                                            selected = uiState.sex == Sex.Female,
                                            onClick = {
                                                viewModel.updateSex(
                                                    if (uiState.sex == Sex.Female) null
                                                    else Sex.Female
                                                )
                                            },
                                            label = { Text(stringResource(R.string.sex_female)) }
                                        )
                                    }

                                    uiState.weightStatus?.let { status ->
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = status,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        OutlinedButton(
                                            onClick = { viewModel.retryWeightLoad() },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = stringResource(R.string.retry_weight_cta)
                                            )
                                        }
                                    }
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
                                }
                            }

                            // Bottom view toggle
                            if (screen != Screen.History) {
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
}
