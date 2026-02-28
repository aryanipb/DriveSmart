package com.aryan.v2v

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var radarView: RadarView
    private lateinit var statusText: TextView
    private lateinit var modelOutputText: TextView

    private lateinit var v2vManager: V2VManager
    private lateinit var trajectoryPredictor: TrajectoryPredictor
    private lateinit var telemetryProvider: TelemetryProvider

    private val inferenceExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val telemetryExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    @Volatile
    private var latestDebugStatus: V2VManager.DebugStatus = V2VManager.DebugStatus()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val allGranted = grants.values.all { it }
        if (allGranted) {
            initializePipeline()
        } else {
            statusText.text = getString(R.string.status_permissions_required)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        radarView = findViewById(R.id.radarView)
        statusText = findViewById(R.id.statusText)
        modelOutputText = findViewById(R.id.modelOutputText)

        if (hasAllRuntimePermissions()) {
            initializePipeline()
        } else {
            permissionLauncher.launch(requiredPermissions())
        }
    }

    private fun initializePipeline() {
        if (::v2vManager.isInitialized) return

        v2vManager = V2VManager(this)
        trajectoryPredictor = TrajectoryPredictor(this)
        telemetryProvider = TelemetryProvider(this)
        v2vManager.setStatusListener { status -> latestDebugStatus = status }

        telemetryProvider.start()
        v2vManager.start()
        startLocalEgoLoop()
        startInferenceLoop()

        statusText.text = "V2V active at 10Hz"
    }

    private fun startLocalEgoLoop() {
        telemetryExecutor.scheduleAtFixedRate({
            try {
                v2vManager.updateLocalState(telemetryProvider.currentState())
            } catch (t: Throwable) {
                runOnUiThread {
                    statusText.text = "SIM_ERR: ${t.javaClass.simpleName}: ${t.message}"
                }
            }
        }, 0L, 100L, TimeUnit.MILLISECONDS)
    }

    private fun startInferenceLoop() {
        inferenceExecutor.scheduleAtFixedRate({
            try {
                val egoHistory = v2vManager.getEgoHistory()
                val neighborSnapshots = v2vManager.getNeighborSnapshots()
                val prediction = trajectoryPredictor.predict(egoHistory, neighborSnapshots)
                val predictionText = formatPrediction(prediction)

                val ego = v2vManager.getLocalState()
                val neighbors = neighborSnapshots.map { it.latestState }
                val debug = latestDebugStatus

                runOnUiThread {
                    radarView.render(ego, neighbors, prediction)
                    modelOutputText.text = predictionText
                    statusText.text = buildString {
                        append("N=")
                        append(neighbors.size)
                        append(" | adv=")
                        append(if (debug.advertising) "1" else "0")
                        append(" disc=")
                        append(if (debug.discovering) "1" else "0")
                        append(" found=")
                        append(debug.foundEndpoints)
                        append(" conn=")
                        append(debug.connectedEndpoints)
                        append(" tx=")
                        append(debug.txPayloads)
                        append(" rx=")
                        append(debug.rxPayloads)
                        if (debug.lastError.isNotEmpty()) {
                            append(" | err=")
                            append(debug.lastError)
                        }
                    }
                }
            } catch (t: Throwable) {
                runOnUiThread {
                    statusText.text = "INF_ERR: ${t.javaClass.simpleName}: ${t.message}"
                    modelOutputText.text = "Model output unavailable"
                }
            }
        }, 0L, 100L, TimeUnit.MILLISECONDS)
    }

    private fun formatPrediction(prediction: FloatArray): String {
        if (prediction.isEmpty()) return "Model: empty output"
        var min = Float.POSITIVE_INFINITY
        var max = Float.NEGATIVE_INFINITY
        for (v in prediction) {
            if (v < min) min = v
            if (v > max) max = v
        }
        val points = prediction.size / 2
        val previewPairs = minOf(points, 6)
        return buildString {
            append("Model output")
            append(" | floats=")
            append(prediction.size)
            append(" points=")
            append(points)
            append('\n')
            append(
                String.format(
                    Locale.US,
                    "range=[%.3f, %.3f]",
                    min,
                    max
                )
            )
            append('\n')
            append("first ")
            append(previewPairs)
            append(" pts: ")
            var i = 0
            while (i < previewPairs * 2 && i + 1 < prediction.size) {
                append(
                    String.format(
                        Locale.US,
                        "(%.2f, %.2f)",
                        prediction[i],
                        prediction[i + 1]
                    )
                )
                if (i + 2 < previewPairs * 2) append("  ")
                i += 2
            }
        }
    }

    private fun hasAllRuntimePermissions(): Boolean {
        return requiredPermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requiredPermissions(): Array<String> {
        val permissions = ArrayList<String>(5)
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        return permissions.toTypedArray()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::v2vManager.isInitialized) {
            v2vManager.stop()
        }
        if (::trajectoryPredictor.isInitialized) {
            trajectoryPredictor.close()
        }
        if (::telemetryProvider.isInitialized) {
            telemetryProvider.stop()
        }
        inferenceExecutor.shutdownNow()
        telemetryExecutor.shutdownNow()
    }
}
