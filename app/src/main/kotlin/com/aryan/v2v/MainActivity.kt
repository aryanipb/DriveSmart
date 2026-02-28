package com.aryan.v2v

import android.Manifest
import android.animation.ObjectAnimator
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.aryan.v2v.ui.DashboardState
import com.aryan.v2v.ui.MainPagerAdapter
import com.aryan.v2v.ui.V2VUiStateViewModel
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var v2vManager: V2VManager
    private lateinit var trajectoryPredictor: TrajectoryPredictor
    private lateinit var telemetryProvider: TelemetryProvider

    private lateinit var topGlow: View
    private lateinit var bottomGlow: View

    private val uiState: V2VUiStateViewModel by viewModels()
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
            uiState.publishDashboard(
                DashboardState(
                    statusText = getString(R.string.status_permissions_required),
                    modelText = "Model output unavailable"
                )
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tabLayout: TabLayout = findViewById(R.id.tabLayout)
        val viewPager: ViewPager2 = findViewById(R.id.viewPager)
        topGlow = findViewById(R.id.topGlow)
        bottomGlow = findViewById(R.id.bottomGlow)

        viewPager.adapter = MainPagerAdapter(this)
        viewPager.offscreenPageLimit = 3

        val tabTitles = arrayOf("Dashboard", "Devices", "Coords")
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = tabTitles[position]
        }.attach()

        startGlowAnimations()

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
    }

    private fun startLocalEgoLoop() {
        telemetryExecutor.scheduleAtFixedRate({
            try {
                v2vManager.updateLocalState(telemetryProvider.currentState())
            } catch (t: Throwable) {
                uiState.publishDashboard(
                    DashboardState(
                        statusText = "SIM_ERR: ${t.javaClass.simpleName}: ${t.message}",
                        modelText = "Model output unavailable"
                    )
                )
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
                val endpointIds = neighborSnapshots.map { it.endpointId }.sorted()
                val statusText = formatStatus(neighbors.size, latestDebugStatus)

                uiState.publishDashboard(
                    DashboardState(
                        ego = ego,
                        neighbors = neighbors,
                        prediction = prediction,
                        statusText = statusText,
                        modelText = predictionText
                    )
                )
                uiState.publishConnectedIds(endpointIds)
                uiState.publishPredictedRows(prediction)
            } catch (t: Throwable) {
                uiState.publishDashboard(
                    DashboardState(
                        statusText = "INF_ERR: ${t.javaClass.simpleName}: ${t.message}",
                        modelText = "Model output unavailable"
                    )
                )
                uiState.publishConnectedIds(emptyList())
                uiState.publishPredictedRows(FloatArray(0))
            }
        }, 0L, 100L, TimeUnit.MILLISECONDS)
    }

    private fun formatStatus(neighbors: Int, debug: V2VManager.DebugStatus): String {
        return buildString {
            append("N=")
            append(neighbors)
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
            append(String.format(Locale.US, "range=[%.3f, %.3f]", min, max))
            append('\n')
            append("first ")
            append(previewPairs)
            append(" pts: ")
            var i = 0
            while (i < previewPairs * 2 && i + 1 < prediction.size) {
                append(String.format(Locale.US, "(%.2f, %.2f)", prediction[i], prediction[i + 1]))
                if (i + 2 < previewPairs * 2) append("  ")
                i += 2
            }
        }
    }

    private fun startGlowAnimations() {
        ObjectAnimator.ofFloat(topGlow, View.SCALE_X, 0.92f, 1.08f).apply {
            duration = 4200L
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            start()
        }
        ObjectAnimator.ofFloat(topGlow, View.SCALE_Y, 0.92f, 1.08f).apply {
            duration = 4200L
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            start()
        }
        ObjectAnimator.ofFloat(bottomGlow, View.ALPHA, 0.55f, 0.95f).apply {
            duration = 3600L
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            start()
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
