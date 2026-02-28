package com.aryan.v2v

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sqrt

class TelemetryProvider(context: Context) : SensorEventListener {
    private val appContext = context.applicationContext
    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(appContext)

    private val sensorManager =
        appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val linearAccelSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

    private val lock = Any()
    private var originLatRad: Double? = null
    private var originLonRad: Double? = null
    private var latestX = 0f
    private var latestY = 0f
    private var latestSpeed = 0f
    private var latestHeading = 0f
    private var latestAccel = 0f

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val location = locationResult.lastLocation ?: return
            updateFromLocation(location)
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 100L)
            .setMinUpdateIntervalMillis(100L)
            .setWaitForAccurateLocation(false)
            .build()

        fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        fusedClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) updateFromLocation(location)
        }

        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        linearAccelSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        fusedClient.removeLocationUpdates(locationCallback)
        sensorManager.unregisterListener(this)
    }

    fun currentState(): V2VState {
        synchronized(lock) {
            return V2VState(
                x = latestX,
                y = latestY,
                speed = latestSpeed,
                heading = latestHeading,
                accel = latestAccel,
                timestampMs = System.currentTimeMillis()
            )
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                val rotationMatrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                val orientation = FloatArray(3)
                SensorManager.getOrientation(rotationMatrix, orientation)
                val azimuthRad = normalizeRadians(orientation[0])
                synchronized(lock) {
                    latestHeading = azimuthRad
                }
            }

            Sensor.TYPE_LINEAR_ACCELERATION -> {
                val ax = event.values.getOrNull(0) ?: 0f
                val ay = event.values.getOrNull(1) ?: 0f
                val az = event.values.getOrNull(2) ?: 0f
                val magnitude = sqrt(ax * ax + ay * ay + az * az)
                synchronized(lock) {
                    latestAccel = magnitude
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun updateFromLocation(location: Location) {
        val latRad = Math.toRadians(location.latitude)
        val lonRad = Math.toRadians(location.longitude)

        synchronized(lock) {
            if (originLatRad == null || originLonRad == null) {
                originLatRad = latRad
                originLonRad = lonRad
            }

            val oLat = originLatRad ?: latRad
            val oLon = originLonRad ?: lonRad
            val earthRadiusM = 6378137.0
            val dLat = latRad - oLat
            val dLon = lonRad - oLon

            val xMeters = earthRadiusM * dLon * cos((latRad + oLat) * 0.5)
            val yMeters = earthRadiusM * dLat

            latestX = xMeters.toFloat()
            latestY = yMeters.toFloat()
            latestSpeed = if (location.hasSpeed()) location.speed else latestSpeed
            if (location.hasBearing()) {
                latestHeading = normalizeRadians(Math.toRadians(location.bearing.toDouble()).toFloat())
            } else {
                latestHeading = normalizeRadians(atan2(dLon, dLat).toFloat())
            }
        }
    }

    private fun normalizeRadians(angle: Float): Float {
        val pi = PI.toFloat()
        val twoPi = (2.0 * PI).toFloat()
        var a = angle
        while (a > pi) a -= twoPi
        while (a < -pi) a += twoPi
        return a
    }
}
