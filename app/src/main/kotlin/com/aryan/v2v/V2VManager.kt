package com.aryan.v2v

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class V2VManager(context: Context) {
    data class DebugStatus(
        val advertising: Boolean = false,
        val discovering: Boolean = false,
        val foundEndpoints: Int = 0,
        val connectedEndpoints: Int = 0,
        val txPayloads: Long = 0L,
        val rxPayloads: Long = 0L,
        val lastError: String = ""
    )

    data class NeighborSnapshot(
        val endpointId: String,
        val latestState: V2VState,
        val history: List<V2VState>
    )

    private class NeighborTrack {
        val buffer = CircularBuffer<V2VState>(50)
        @Volatile
        var latestState: V2VState = V2VState(0f, 0f, 0f, 0f, 0f)
    }

    private val appContext = context.applicationContext
    private val connectionsClient: ConnectionsClient = Nearby.getConnectionsClient(appContext)
    private val localEndpointName = "v2v-${UUID.randomUUID().toString().take(8)}"
    private val localStateRef = AtomicReference(V2VState(0f, 0f, 0f, 0f, 0f))
    private val localHistory = CircularBuffer<V2VState>(50)

    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val neighbors = ConcurrentHashMap<String, NeighborTrack>()
    private val connectedEndpoints = ConcurrentHashMap.newKeySet<String>()
    private val discoveredEndpoints = ConcurrentHashMap.newKeySet<String>()
    private val discoveredEndpointNames = ConcurrentHashMap<String, String>()
    private val pendingConnections = ConcurrentHashMap.newKeySet<String>()
    private val retryCounts = ConcurrentHashMap<String, Int>()
    private val statusLock = Any()
    private var debugStatus = DebugStatus()
    private var statusListener: ((DebugStatus) -> Unit)? = null

    private val serviceId = appContext.packageName

    @Volatile
    private var running = false

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val data = payload.asBytes() ?: return
            val text = String(data, StandardCharsets.UTF_8)
            updateStatus { copy(rxPayloads = rxPayloads + 1L) }
            parseState(text)?.let { state ->
                val track = neighbors.computeIfAbsent(endpointId) { NeighborTrack() }
                track.buffer.add(state)
                track.latestState = state
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            Log.i(TAG, "onConnectionInitiated endpoint=$endpointId")
            pendingConnections.add(endpointId)
            connectionsClient.acceptConnection(endpointId, payloadCallback)
                .addOnFailureListener { e ->
                    onError("acceptConnection failed: ${e.message}")
                    pendingConnections.remove(endpointId)
                }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            val status = result.status.statusCode
            pendingConnections.remove(endpointId)
            if (status == ConnectionsStatusCodes.STATUS_OK) {
                neighbors.putIfAbsent(endpointId, NeighborTrack())
                connectedEndpoints.add(endpointId)
                retryCounts.remove(endpointId)
                updateStatus { copy(connectedEndpoints = this@V2VManager.connectedEndpoints.size) }
                Log.i(TAG, "Connected endpoint=$endpointId")
            } else {
                neighbors.remove(endpointId)
                connectedEndpoints.remove(endpointId)
                updateStatus {
                    copy(
                        connectedEndpoints = this@V2VManager.connectedEndpoints.size,
                        lastError = "connectionResult=${CommonStatusCodes.getStatusCodeString(status)}"
                    )
                }
                Log.w(TAG, "Connection failed endpoint=$endpointId status=$status")
                scheduleRetry(endpointId)
            }
        }

        override fun onDisconnected(endpointId: String) {
            neighbors.remove(endpointId)
            connectedEndpoints.remove(endpointId)
            pendingConnections.remove(endpointId)
            updateStatus { copy(connectedEndpoints = this@V2VManager.connectedEndpoints.size) }
            Log.i(TAG, "Disconnected endpoint=$endpointId")
            scheduleRetry(endpointId)
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            // Guard against self-loop connections on devices that may surface local endpoints.
            if (info.endpointName == localEndpointName) {
                Log.i(TAG, "Ignoring self endpoint id=$endpointId")
                return
            }
            discoveredEndpoints.add(endpointId)
            discoveredEndpointNames[endpointId] = info.endpointName
            updateStatus { copy(foundEndpoints = this@V2VManager.discoveredEndpoints.size) }
            Log.i(TAG, "Endpoint found id=$endpointId name=${info.endpointName}")
            maybeRequestConnection(endpointId, info.endpointName)
        }

        override fun onEndpointLost(endpointId: String) {
            neighbors.remove(endpointId)
            connectedEndpoints.remove(endpointId)
            pendingConnections.remove(endpointId)
            discoveredEndpoints.remove(endpointId)
            discoveredEndpointNames.remove(endpointId)
            retryCounts.remove(endpointId)
            updateStatus {
                copy(
                    connectedEndpoints = this@V2VManager.connectedEndpoints.size,
                    foundEndpoints = this@V2VManager.discoveredEndpoints.size
                )
            }
            Log.i(TAG, "Endpoint lost id=$endpointId")
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return
        running = true

        val strategy = Strategy.P2P_CLUSTER
        val advertisingOptions = AdvertisingOptions.Builder().setStrategy(strategy).build()
        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(strategy).build()

        connectionsClient
            .startAdvertising(localEndpointName, serviceId, connectionLifecycleCallback, advertisingOptions)
            .addOnSuccessListener {
                updateStatus { copy(advertising = true, lastError = "") }
                Log.i(TAG, "startAdvertising success")
            }
            .addOnFailureListener { e -> onError("startAdvertising failed: ${e.message}") }

        connectionsClient
            .startDiscovery(serviceId, endpointDiscoveryCallback, discoveryOptions)
            .addOnSuccessListener {
                updateStatus { copy(discovering = true, lastError = "") }
                Log.i(TAG, "startDiscovery success")
            }
            .addOnFailureListener { e -> onError("startDiscovery failed: ${e.message}") }

        scheduler.scheduleAtFixedRate({ publishLocalTelemetry() }, 0L, 100L, TimeUnit.MILLISECONDS)
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        running = false
        scheduler.shutdownNow()
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        neighbors.clear()
        connectedEndpoints.clear()
        discoveredEndpoints.clear()
        discoveredEndpointNames.clear()
        pendingConnections.clear()
        retryCounts.clear()
        updateStatus {
            DebugStatus(
                advertising = false,
                discovering = false,
                foundEndpoints = 0,
                connectedEndpoints = 0,
                txPayloads = txPayloads,
                rxPayloads = rxPayloads,
                lastError = ""
            )
        }
    }

    fun updateLocalState(state: V2VState) {
        localStateRef.set(state)
        localHistory.add(state)
    }

    fun getEgoHistory(): List<V2VState> = localHistory.snapshot()

    fun getLocalState(): V2VState = localStateRef.get()

    fun setStatusListener(listener: (DebugStatus) -> Unit) {
        synchronized(statusLock) {
            statusListener = listener
            listener(debugStatus)
        }
    }

    fun getDebugStatus(): DebugStatus = synchronized(statusLock) { debugStatus }

    fun getNeighborSnapshots(): List<NeighborSnapshot> {
        if (neighbors.isEmpty()) return emptyList()
        val result = ArrayList<NeighborSnapshot>(neighbors.size)
        for ((endpointId, track) in neighbors) {
            result.add(NeighborSnapshot(endpointId, track.latestState, track.buffer.snapshot()))
        }
        return result
    }

    @SuppressLint("MissingPermission")
    private fun publishLocalTelemetry() {
        if (!running) return

        val state = localStateRef.get()
        val payloadJson = String.format(
            Locale.US,
            "{\"x\":%.5f,\"y\":%.5f,\"speed\":%.5f,\"heading\":%.5f,\"accel\":%.5f,\"ts\":%d}",
            state.x,
            state.y,
            state.speed,
            state.heading,
            state.accel,
            state.timestampMs
        )

        val endpointIds = neighbors.keys.toList()
        if (endpointIds.isEmpty()) return

        val payload = Payload.fromBytes(payloadJson.toByteArray(StandardCharsets.UTF_8))
        connectionsClient.sendPayload(endpointIds, payload)
            .addOnSuccessListener { updateStatus { copy(txPayloads = txPayloads + 1L) } }
            .addOnFailureListener { e -> Log.e(TAG, "sendPayload failed", e) }
    }

    private fun parseState(json: String): V2VState? {
        return try {
            val obj = JSONObject(json)
            V2VState(
                x = obj.optDouble("x", 0.0).toFloat(),
                y = obj.optDouble("y", 0.0).toFloat(),
                speed = obj.optDouble("speed", 0.0).toFloat(),
                heading = obj.optDouble("heading", 0.0).toFloat(),
                accel = obj.optDouble("accel", 0.0).toFloat(),
                timestampMs = obj.optLong("ts", System.currentTimeMillis())
            )
        } catch (e: Exception) {
            Log.w(TAG, "Invalid payload: $json", e)
            null
        }
    }

    private fun onError(message: String) {
        Log.e(TAG, message)
        updateStatus { copy(lastError = message) }
    }

    @SuppressLint("MissingPermission")
    private fun maybeRequestConnection(endpointId: String, remoteEndpointName: String) {
        if (connectedEndpoints.contains(endpointId) || pendingConnections.contains(endpointId)) return
        // Deterministic initiator selection avoids both peers racing requestConnection.
        val shouldInitiate = localEndpointName < remoteEndpointName
        if (!shouldInitiate) {
            Log.i(TAG, "Waiting for remote to initiate endpoint=$endpointId")
            return
        }

        pendingConnections.add(endpointId)
        connectionsClient.requestConnection(localEndpointName, endpointId, connectionLifecycleCallback)
            .addOnFailureListener { e ->
                pendingConnections.remove(endpointId)
                onError("requestConnection failed: ${e.message}")
                scheduleRetry(endpointId)
            }
    }

    private fun scheduleRetry(endpointId: String) {
        if (!running) return
        if (!discoveredEndpoints.contains(endpointId)) return
        if (connectedEndpoints.contains(endpointId) || pendingConnections.contains(endpointId)) return
        val attempt = (retryCounts[endpointId] ?: 0) + 1
        retryCounts[endpointId] = attempt
        val delayMs = (attempt * 1000L).coerceAtMost(5000L)
        scheduler.schedule({
            if (!running) return@schedule
            if (!discoveredEndpoints.contains(endpointId)) return@schedule
            if (connectedEndpoints.contains(endpointId) || pendingConnections.contains(endpointId)) return@schedule
            val remoteName = discoveredEndpointNames[endpointId] ?: return@schedule
            maybeRequestConnection(endpointId, remoteName)
        }, delayMs, TimeUnit.MILLISECONDS)
    }

    private fun updateStatus(update: DebugStatus.() -> DebugStatus) {
        val newStatus: DebugStatus
        val listener: ((DebugStatus) -> Unit)?
        synchronized(statusLock) {
            newStatus = debugStatus.update()
            debugStatus = newStatus
            listener = statusListener
        }
        listener?.invoke(newStatus)
    }

    companion object {
        private const val TAG = "V2VManager"
    }
}
