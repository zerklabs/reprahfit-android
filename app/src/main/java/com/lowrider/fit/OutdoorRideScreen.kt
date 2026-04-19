package com.lowrider.fit

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun OutdoorRideScreen(viewModel: RideViewModel = viewModel()) {
    val context = LocalContext.current
    val locationManager = remember {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }
    val ridePermissions = remember { viewModel.healthExporter.requiredRidePermissions() }
    val weightPermissions = remember { viewModel.healthExporter.requiredWeightPermissions() }

    var hasLocationPermission by rememberSaveable {
        mutableStateOf(context.hasFineLocationPermission())
    }
    val uiState by viewModel.uiState.collectAsState()
    val snapshot by RideTrackingService.snapshot.collectAsState()

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasLocationPermission = granted
        if (granted && !uiState.isTracking) {
            viewModel.startRide()
        }
    }

    val healthPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(
            HEALTH_CONNECT_PROVIDER_PACKAGE
        )
    ) { grantedPermissions ->
        viewModel.onHealthPermissionResult(grantedPermissions.containsAll(ridePermissions))
    }

    val weightPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(
            HEALTH_CONNECT_PROVIDER_PACKAGE
        )
    ) { grantedPermissions ->
        viewModel.onWeightPermissionResult(grantedPermissions.containsAll(weightPermissions))
    }

    val currentSpeedMph = snapshot.currentSpeedMps * 2.23694
    val distanceMiles = snapshot.distanceMeters / 1609.344
    val elapsedHours = uiState.elapsedMillis / 3_600_000.0
    val averageSpeedMph = if (elapsedHours > 0.0) distanceMiles / elapsedHours else 0.0
    val calories = estimateOutdoorCalories(
        weightPounds = uiState.weightInput.toDoubleOrNull() ?: 0.0,
        hours = elapsedHours,
        averageSpeedMph = averageSpeedMph,
        averageHeartRate = uiState.heartRateInput.toIntOrNull(),
        age = uiState.ageInput.toIntOrNull(),
        sex = uiState.sex
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp, top = 48.dp)
    ) {
        Text(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = stringResource(R.string.home_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.status_title),
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                StatRow(
                    label = stringResource(R.string.current_speed_label),
                    value = stringResource(
                        R.string.speed_result_value,
                        "%.1f".format(currentSpeedMph)
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                StatRow(
                    label = stringResource(R.string.average_speed_label),
                    value = stringResource(
                        R.string.speed_result_value,
                        "%.1f".format(averageSpeedMph)
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                StatRow(
                    label = stringResource(R.string.distance_label),
                    value = stringResource(
                        R.string.distance_value,
                        "%.2f".format(distanceMiles)
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                StatRow(
                    label = stringResource(R.string.elapsed_time_label),
                    value = formatElapsedTime(uiState.elapsedMillis)
                )
                Spacer(modifier = Modifier.height(12.dp))
                StatRow(
                    label = stringResource(R.string.calorie_result_label),
                    value = stringResource(R.string.calorie_result_value, calories)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = uiState.weightInput,
            onValueChange = { viewModel.updateWeight(it) },
            label = { Text(stringResource(R.string.weight_label)) },
            supportingText = { Text(stringResource(R.string.weight_supporting)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.hr_section_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = stringResource(R.string.hr_section_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = uiState.ageInput,
                onValueChange = { viewModel.updateAge(it) },
                label = { Text(stringResource(R.string.age_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = uiState.heartRateInput,
                onValueChange = { viewModel.updateHeartRate(it) },
                label = { Text(stringResource(R.string.heart_rate_label)) },
                supportingText = { Text(stringResource(R.string.heart_rate_supporting)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.sex_label),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alignByBaseline()
            )
            FilterChip(
                selected = uiState.sex == Sex.Male,
                onClick = {
                    viewModel.updateSex(if (uiState.sex == Sex.Male) null else Sex.Male)
                },
                label = { Text(stringResource(R.string.sex_male)) }
            )
            FilterChip(
                selected = uiState.sex == Sex.Female,
                onClick = {
                    viewModel.updateSex(if (uiState.sex == Sex.Female) null else Sex.Female)
                },
                label = { Text(stringResource(R.string.sex_female)) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = when {
                !hasLocationPermission -> stringResource(R.string.permission_needed)
                !locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                    stringResource(R.string.location_disabled)
                snapshot.hasFix -> stringResource(R.string.gps_live)
                uiState.isTracking -> stringResource(R.string.waiting_for_fix)
                else -> stringResource(R.string.ready_to_ride)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        uiState.weightStatus?.let { status ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    if (viewModel.healthExporter.availabilityStatus() ==
                        androidx.health.connect.client.HealthConnectClient.SDK_AVAILABLE
                    ) {
                        weightPermissionLauncher.launch(weightPermissions)
                    } else {
                        viewModel.retryWeightLoad()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.retry_weight_cta))
            }
        }

        uiState.healthSyncStatus?.let { status ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            if (uiState.pendingRideSync != null) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { healthPermissionLauncher.launch(ridePermissions) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.retry_sync_cta))
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    when {
                        !hasLocationPermission -> {
                            locationPermissionLauncher.launch(
                                Manifest.permission.ACCESS_FINE_LOCATION
                            )
                        }
                        uiState.isTracking -> {
                            viewModel.stopRideAndSync(snapshot)
                        }
                        else -> {
                            viewModel.startRide()
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = when {
                        !hasLocationPermission -> stringResource(R.string.enable_location_cta)
                        uiState.isTracking -> stringResource(R.string.stop_ride_cta)
                        else -> stringResource(R.string.start_ride_cta)
                    }
                )
            }

            OutlinedButton(
                onClick = { viewModel.resetRide() },
                modifier = Modifier.weight(1f)
            ) {
                Text(text = stringResource(R.string.reset_ride_cta))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.results_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

fun formatElapsedTime(elapsedMillis: Long): String {
    val totalSeconds = elapsedMillis / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

fun Context.hasFineLocationPermission(): Boolean {
    return ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}
