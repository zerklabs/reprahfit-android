package com.reprahfit

import android.Manifest
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.BLUETOOTH_SCAN
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun SimpleDashboardScreen(viewModel: RideViewModel = viewModel()) {
    val context = LocalContext.current
    val locationManager = remember {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    var hasLocationPermission by rememberSaveable {
        mutableStateOf(context.hasFineLocationPermission())
    }
    val uiState by viewModel.uiState.collectAsState()
    val snapshot by RideTrackingService.snapshot.collectAsState()
    val hrmState by viewModel.hrmState.collectAsState()

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
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Speed section - takes up the top portion
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "%.1f".format(currentSpeedMph),
                    fontSize = 96.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.simple_speed_unit),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Calorie section
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$calories",
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.simple_cal_unit),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Heart rate monitor
        when (hrmState.connectionStatus) {
            ConnectionStatus.Connected -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = Color.Red,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = if (hrmState.heartRate > 0) {
                            stringResource(R.string.hrm_connected, hrmState.heartRate)
                        } else {
                            hrmState.deviceName ?: ""
                        },
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Red
                    )
                }
                TextButton(onClick = { viewModel.disconnectHrm() }) {
                    Text(text = stringResource(R.string.hrm_disconnect_cta))
                }
            }
            ConnectionStatus.Reconnecting -> {
                Text(
                    text = stringResource(R.string.hrm_lost),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
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
                Spacer(modifier = Modifier.height(8.dp))
            }
            ConnectionStatus.Scanning -> {
                Text(
                    text = stringResource(R.string.hrm_scanning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { viewModel.stopHrmScan() }) {
                    Text(text = stringResource(R.string.hrm_stop_scan_cta))
                }
            }
            ConnectionStatus.Disconnected -> {
                TextButton(
                    onClick = {
                        if (!hasBlePermission) {
                            blePermissionLauncher.launch(
                                arrayOf(BLUETOOTH_SCAN, BLUETOOTH_CONNECT)
                            )
                        } else {
                            viewModel.startHrmScan()
                        }
                    }
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

        // Elapsed time - small, between calories and buttons
        Text(
            text = formatElapsedTime(uiState.elapsedMillis),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // GPS status
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

        Spacer(modifier = Modifier.height(16.dp))

        // Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
    }
}
