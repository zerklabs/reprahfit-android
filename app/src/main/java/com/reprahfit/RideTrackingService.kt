package com.reprahfit

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.IBinder
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RideSnapshot(
    val hasFix: Boolean = false,
    val currentSpeedMps: Double = 0.0,
    val distanceMeters: Double = 0.0,
    val currentHeartRate: Int = 0,
    val averageHeartRate: Int = 0
)

class RideTrackingService : Service() {

    private lateinit var locationManager: LocationManager
    private var locationListener: LocationListener? = null
    private var lastAcceptedLocation: Location? = null
    private var totalDistanceMeters = 0.0
    private val hrmManager get() = HeartRateMonitorManager.getInstance(this)
    private var hrSampleSum = 0L
    private var hrSampleCount = 0

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTracking()
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopTracking()
        _snapshot.value = RideSnapshot()
        _isRunning.value = false
        super.onDestroy()
    }

    private fun startTracking() {
        if (locationListener != null) return
        Log.d(TAG, "Starting GPS tracking")

        totalDistanceMeters = 0.0
        lastAcceptedLocation = null
        hrSampleSum = 0L
        hrSampleCount = 0
        _snapshot.value = RideSnapshot()
        _isRunning.value = true

        if (hrmManager.hasSavedDevice() && hrmManager.isBluetoothEnabled()) {
            hrmManager.connectToSaved()
        }

        startForeground(
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        )

        val listener = LocationListener { location ->
            if (location.hasAccuracy() && location.accuracy > MAX_ACCURACY_METERS) {
                Log.d(TAG, "Rejected fix: accuracy=${location.accuracy}m")
                return@LocationListener
            }

            val previous = lastAcceptedLocation
            val calculatedSpeed = when {
                location.hasSpeed() && location.speed > 0f -> location.speed.toDouble()
                previous != null -> {
                    val seconds = (location.time - previous.time) / 1000.0
                    if (seconds > 0.5) {
                        val deltaSpeed = previous.distanceTo(location).toDouble() / seconds
                        // Blend with previous speed to reduce jitter
                        val prevSpeed = _snapshot.value.currentSpeedMps
                        if (prevSpeed > 0.0) (prevSpeed * 0.3 + deltaSpeed * 0.7) else deltaSpeed
                    } else {
                        _snapshot.value.currentSpeedMps
                    }
                }
                else -> 0.0
            }

            if (previous != null && calculatedSpeed >= MIN_SPEED_MPS) {
                val segmentMeters = previous.distanceTo(location).toDouble()
                if (segmentMeters in 1.0..250.0) {
                    totalDistanceMeters += segmentMeters
                }
            }

            lastAcceptedLocation = location

            val currentHr = hrmManager.state.value.heartRate
            if (currentHr > 0) {
                hrSampleSum += currentHr
                hrSampleCount++
            }
            val avgHr = if (hrSampleCount > 0) (hrSampleSum / hrSampleCount).toInt() else 0

            _snapshot.value = RideSnapshot(
                hasFix = true,
                currentSpeedMps = calculatedSpeed,
                distanceMeters = totalDistanceMeters,
                currentHeartRate = currentHr,
                averageHeartRate = avgHr
            )
            Log.d(TAG, "Fix: speed=%.1f m/s, hasSpeed=%b, accuracy=%.0fm, distance=%.0fm".format(
                calculatedSpeed, location.hasSpeed(), location.accuracy, totalDistanceMeters
            ))
        }

        locationListener = listener

        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) return
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1_000L,
                1f,
                listener,
                Looper.getMainLooper()
            )
        } catch (_: SecurityException) {
            stopSelf()
        }
    }

    private fun stopTracking() {
        locationListener?.let(locationManager::removeUpdates)
        locationListener = null
        lastAcceptedLocation = null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Ride Tracking",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.tracking_notification_title))
            .setContentText(getString(R.string.tracking_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "RideTrackingService"
        private const val CHANNEL_ID = "ride_tracking"
        private const val NOTIFICATION_ID = 1
        private const val MAX_ACCURACY_METERS = 30f
        private const val MIN_SPEED_MPS = 0.5 // ~1.1 mph

        const val ACTION_START = "com.reprahfit.ACTION_START_TRACKING"
        const val ACTION_STOP = "com.reprahfit.ACTION_STOP_TRACKING"

        private val _snapshot = MutableStateFlow(RideSnapshot())
        val snapshot: StateFlow<RideSnapshot> = _snapshot.asStateFlow()

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, RideTrackingService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, RideTrackingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
