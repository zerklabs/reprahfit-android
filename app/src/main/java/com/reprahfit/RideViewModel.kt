package com.reprahfit

import android.app.Application
import android.os.SystemClock
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.reprahfit.data.AppDatabase
import com.reprahfit.data.RideEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RideUiState(
    val isTracking: Boolean = false,
    val elapsedMillis: Long = 0L,
    val weightInput: String = "180",
    val ageInput: String = "",
    val heartRateInput: String = "",
    val sex: Sex? = null,
    val healthSyncStatus: String? = null,
    val weightStatus: String? = null,
    val pendingRideSync: CompletedRide? = null
)

class RideViewModel(application: Application) : AndroidViewModel(application) {
    private val context get() = getApplication<Application>()
    val healthExporter = HealthConnectRideExporter(context)
    private val rideDao = AppDatabase.getInstance(context).rideDao()
    val rideHistory = rideDao.getAll()

    private val hrmManager get() = HeartRateMonitorManager.getInstance(context)

    val hrmState: StateFlow<HrmState> get() = hrmManager.state

    fun startHrmScan() {
        hrmManager.startScan()
    }

    fun stopHrmScan() {
        hrmManager.stopScan()
    }

    fun connectHrmDevice(device: android.bluetooth.BluetoothDevice) {
        hrmManager.connectToDevice(device)
    }

    fun disconnectHrm() {
        hrmManager.disconnect()
    }

    fun isHrmBluetoothEnabled(): Boolean {
        return hrmManager.isBluetoothEnabled()
    }

    fun hasHrmSavedDevice(): Boolean {
        return hrmManager.hasSavedDevice()
    }

    private val _uiState = MutableStateFlow(RideUiState())
    val uiState: StateFlow<RideUiState> = _uiState.asStateFlow()

    private var rideStartRealtime: Long? = null
    private var rideStartWallClockMillis: Long? = null
    private var completedElapsedMillis = 0L
    private var timerJob: Job? = null

    fun startRide() {
        RideTrackingService.start(context)
        completedElapsedMillis = 0L
        rideStartRealtime = SystemClock.elapsedRealtime()
        rideStartWallClockMillis = System.currentTimeMillis()
        _uiState.value = _uiState.value.copy(
            isTracking = true,
            elapsedMillis = 0L,
            healthSyncStatus = null,
            pendingRideSync = null
        )
        startTimer()
    }

    fun stopRideAndSync(snapshot: RideSnapshot) {
        RideTrackingService.stop(context)
        stopTimer()

        val sessionStart = rideStartWallClockMillis
        if (sessionStart == null) {
            _uiState.value = _uiState.value.copy(
                isTracking = false,
                healthSyncStatus = context.getString(R.string.health_connect_no_ride)
            )
            rideStartRealtime = null
            return
        }

        val stopRealtime = SystemClock.elapsedRealtime()
        val finalElapsedMillis =
            completedElapsedMillis + (stopRealtime - (rideStartRealtime ?: stopRealtime))
        completedElapsedMillis = finalElapsedMillis
        rideStartRealtime = null

        val currentState = _uiState.value
        val elapsedHours = finalElapsedMillis / 3_600_000.0
        val distanceMiles = snapshot.distanceMeters / 1609.344
        val averageSpeedMph = if (elapsedHours > 0.0) distanceMiles / elapsedHours else 0.0
        val liveAvgHr = snapshot.averageHeartRate
        val effectiveHr = if (liveAvgHr > 0) liveAvgHr else currentState.heartRateInput.toIntOrNull()
        val calories = estimateOutdoorCalories(
            weightPounds = currentState.weightInput.toDoubleOrNull() ?: 0.0,
            hours = elapsedHours,
            averageSpeedMph = averageSpeedMph,
            averageHeartRate = effectiveHr,
            age = currentState.ageInput.toIntOrNull(),
            sex = currentState.sex
        )

        val ride = CompletedRide(
            startEpochMillis = sessionStart,
            endEpochMillis = System.currentTimeMillis(),
            distanceMeters = snapshot.distanceMeters,
            calories = calories,
            averageHeartRate = snapshot.averageHeartRate
        )

        _uiState.value = currentState.copy(
            isTracking = false,
            elapsedMillis = finalElapsedMillis
        )

        if (finalElapsedMillis < 1_000L || ride.distanceMeters <= 0.0) {
            _uiState.value = _uiState.value.copy(
                healthSyncStatus = context.getString(R.string.health_connect_no_ride)
            )
            return
        }

        viewModelScope.launch {
            rideDao.insert(
                RideEntity(
                    startTimeMillis = ride.startEpochMillis,
                    endTimeMillis = ride.endEpochMillis,
                    durationMillis = finalElapsedMillis,
                    distanceMeters = ride.distanceMeters,
                    averageSpeedMph = averageSpeedMph,
                    calories = ride.calories
                )
            )
        }

        syncRide(ride)
    }

    fun resetRide() {
        RideTrackingService.stop(context)
        stopTimer()
        completedElapsedMillis = 0L
        rideStartRealtime = null
        rideStartWallClockMillis = null
        _uiState.value = _uiState.value.copy(
            isTracking = false,
            elapsedMillis = 0L,
            healthSyncStatus = null,
            pendingRideSync = null
        )
    }

    fun deleteRide(id: Long) {
        viewModelScope.launch { rideDao.deleteById(id) }
    }

    fun updateWeight(value: String) {
        val filtered = value.filter { it.isDigit() || it == '.' }
        if (filtered.count { it == '.' } <= 1) {
            _uiState.value = _uiState.value.copy(weightInput = filtered)
        }
    }

    fun updateAge(value: String) {
        val filtered = value.filter { it.isDigit() }
        _uiState.value = _uiState.value.copy(ageInput = filtered)
    }

    fun updateHeartRate(value: String) {
        val filtered = value.filter { it.isDigit() }
        _uiState.value = _uiState.value.copy(heartRateInput = filtered)
    }

    fun updateSex(sex: Sex?) {
        _uiState.value = _uiState.value.copy(sex = sex)
    }

    fun updateHealthSyncStatus(status: String?) {
        _uiState.value = _uiState.value.copy(healthSyncStatus = status)
    }

    fun clearPendingSync() {
        _uiState.value = _uiState.value.copy(pendingRideSync = null)
    }

    fun syncPendingRide() {
        val ride = _uiState.value.pendingRideSync ?: return
        viewModelScope.launch {
            runHealthSync(ride)
        }
        _uiState.value = _uiState.value.copy(pendingRideSync = null)
    }

    fun retryWeightLoad() {
        when (healthExporter.availabilityStatus()) {
            HealthConnectClient.SDK_AVAILABLE -> {
                viewModelScope.launch {
                    if (healthExporter.hasRequiredWeightPermissions()) {
                        loadWeightFromHealthConnect()
                    }
                }
            }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                _uiState.value = _uiState.value.copy(
                    weightStatus = context.getString(R.string.health_connect_update_required)
                )
            }
            HealthConnectClient.SDK_UNAVAILABLE -> {
                _uiState.value = _uiState.value.copy(
                    weightStatus = context.getString(R.string.health_connect_unavailable)
                )
            }
        }
    }

    fun onWeightPermissionResult(granted: Boolean) {
        if (granted) {
            viewModelScope.launch { loadWeightFromHealthConnect() }
        } else {
            _uiState.value = _uiState.value.copy(
                weightStatus = context.getString(R.string.health_connect_weight_denied)
            )
        }
    }

    fun onHealthPermissionResult(granted: Boolean) {
        if (granted) {
            syncPendingRide()
        } else {
            _uiState.value = _uiState.value.copy(
                healthSyncStatus = context.getString(R.string.health_connect_permissions_denied)
            )
        }
    }

    private fun syncRide(ride: CompletedRide) {
        viewModelScope.launch {
            when (healthExporter.availabilityStatus()) {
                HealthConnectClient.SDK_UNAVAILABLE -> {
                    _uiState.value = _uiState.value.copy(
                        healthSyncStatus = context.getString(R.string.health_connect_unavailable)
                    )
                }
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                    _uiState.value = _uiState.value.copy(
                        healthSyncStatus = context.getString(R.string.health_connect_update_required)
                    )
                }
                HealthConnectClient.SDK_AVAILABLE -> {
                    if (healthExporter.hasRequiredRidePermissions()) {
                        runHealthSync(ride)
                    } else {
                        _uiState.value = _uiState.value.copy(pendingRideSync = ride)
                    }
                }
            }
        }
    }

    private suspend fun runHealthSync(ride: CompletedRide) {
        try {
            healthExporter.exportRide(ride)
            _uiState.value = _uiState.value.copy(
                healthSyncStatus = context.getString(R.string.health_connect_synced)
            )
        } catch (error: Exception) {
            _uiState.value = _uiState.value.copy(
                healthSyncStatus = error.message
                    ?: context.getString(R.string.health_connect_sync_failed)
            )
        }
    }

    private suspend fun loadWeightFromHealthConnect() {
        val pounds = healthExporter.readLatestWeightPounds()
        if (pounds != null) {
            _uiState.value = _uiState.value.copy(
                weightInput = "%.1f".format(pounds),
                weightStatus = context.getString(R.string.health_connect_weight_loaded)
            )
        } else {
            _uiState.value = _uiState.value.copy(
                weightStatus = context.getString(R.string.health_connect_weight_missing)
            )
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                val start = rideStartRealtime ?: break
                _uiState.value = _uiState.value.copy(
                    elapsedMillis = completedElapsedMillis +
                        (SystemClock.elapsedRealtime() - start)
                )
                delay(1_000)
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }
}
