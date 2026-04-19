package com.reprahfit

import android.Manifest
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.BLUETOOTH_SCAN
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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

    var hasLocationPermission by rememberSaveable {
        mutableStateOf(context.hasFineLocationPermission())
    }
    val uiState by viewModel.uiState.collectAsState()
    val snapshot by RideTrackingService.snapshot.collectAsState()
    val hrmState by viewModel.hrmState.collectAsState()
    var showDevicePicker by rememberSaveable { mutableStateOf(false) }

    var hasBlePermission by rememberSaveable {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val blePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasBlePermission = permissions.values.all { it }
        if (hasBlePermission) {
            viewModel.startHrmScan()
            showDevicePicker = true
        }
    }

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

    val currentSpeedMph = snapshot.currentSpeedMps * 2.23694
    val distanceMiles = snapshot.distanceMeters / 1609.344
    val elapsedHours = uiState.elapsedMillis / 3_600_000.0
    val averageSpeedMph = if (elapsedHours > 0.0) distanceMiles / elapsedHours else 0.0
    val liveAvgHr = snapshot.averageHeartRate
    val effectiveHr = if (liveAvgHr > 0) liveAvgHr else uiState.heartRateInput.toIntOrNull()
    val calories = estimateOutdoorCalories(
        weightPounds = uiState.weightInput.toDoubleOrNull() ?: 0.0,
        hours = elapsedHours,
        averageSpeedMph = averageSpeedMph,
        averageHeartRate = effectiveHr,
        age = uiState.ageInput.toIntOrNull(),
        sex = uiState.sex
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
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
                if (hrmState.connectionStatus == ConnectionStatus.Connected && hrmState.heartRate > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    StatRow(
                        label = stringResource(R.string.hrm_section_title),
                        value = stringResource(R.string.hrm_connected, hrmState.heartRate)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.hrm_section_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        when (hrmState.connectionStatus) {
            ConnectionStatus.Connected -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = null,
                            tint = Color.Red,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.hrm_connected, hrmState.heartRate),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    Text(
                        text = hrmState.deviceName ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { viewModel.disconnectHrm() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.hrm_disconnect_cta))
                }
            }
            ConnectionStatus.Reconnecting -> {
                Text(
                    text = stringResource(R.string.hrm_lost),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            ConnectionStatus.Connecting -> {
                Text(
                    text = stringResource(
                        R.string.hrm_connecting,
                        hrmState.deviceName ?: ""
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ConnectionStatus.Scanning -> {
                Text(
                    text = stringResource(R.string.hrm_scanning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = {
                        viewModel.stopHrmScan()
                        showDevicePicker = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.hrm_stop_scan_cta))
                }
            }
            ConnectionStatus.Disconnected -> {
                OutlinedButton(
                    onClick = {
                        if (!hasBlePermission) {
                            blePermissionLauncher.launch(
                                arrayOf(BLUETOOTH_SCAN, BLUETOOTH_CONNECT)
                            )
                        } else {
                            viewModel.startHrmScan()
                            showDevicePicker = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Filled.FavoriteBorder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.hrm_connect_cta),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
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

    if (showDevicePicker) {
        @OptIn(ExperimentalMaterial3Api::class)
        ModalBottomSheet(
            onDismissRequest = {
                showDevicePicker = false
                viewModel.stopHrmScan()
            },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.hrm_section_title),
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (hrmState.scanResults.isEmpty()) {
                    Text(
                        text = if (hrmState.connectionStatus == ConnectionStatus.Scanning) {
                            stringResource(R.string.hrm_scanning)
                        } else {
                            stringResource(R.string.hrm_no_devices)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }

                hrmState.scanResults.forEach { result ->
                    ListItem(
                        headlineContent = { Text(result.name) },
                        supportingContent = {
                            Text("Signal: ${result.rssi} dBm")
                        },
                        modifier = Modifier.clickable {
                            viewModel.connectHrmDevice(result.device)
                            showDevicePicker = false
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = {
                        viewModel.stopHrmScan()
                        showDevicePicker = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.hrm_stop_scan_cta))
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
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
