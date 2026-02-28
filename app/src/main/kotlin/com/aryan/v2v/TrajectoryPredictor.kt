package com.aryan.v2v

import android.content.Context
import android.graphics.PointF
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.channels.FileChannel
import java.util.HashMap
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class TrajectoryPredictor(context: Context) {
    private val interpreter: Interpreter
    private val inputCount: Int
    private val inputNames: Array<String>
    private val inputShapes: Array<IntArray>
    private val inputElementCounts: IntArray
    private val inputBuffers: Array<ByteBuffer>
    private val inputFloatBuffers: Array<FloatBuffer>

    private val outputShape: IntArray
    private val outputElements: Int
    private val outputBuffer: ByteBuffer
    private val outputFloatBuffer: FloatBuffer
    private val outputTensor: FloatArray

    init {
        interpreter = Interpreter(loadModelBuffer(context))

        inputCount = interpreter.inputTensorCount
        inputNames = Array(inputCount) { interpreter.getInputTensor(it).name() }
        inputShapes = Array(inputCount) { interpreter.getInputTensor(it).shape() }
        inputElementCounts = IntArray(inputCount)
        inputBuffers = Array(inputCount) { index ->
            var elements = 1
            for (dim in inputShapes[index]) {
                elements *= dim
            }
            inputElementCounts[index] = elements
            ByteBuffer.allocateDirect(elements * 4).order(ByteOrder.nativeOrder())
        }
        inputFloatBuffers = Array(inputCount) { i -> inputBuffers[i].asFloatBuffer() }

        outputShape = interpreter.getOutputTensor(0).shape()
        var outputSize = 1
        for (dim in outputShape) {
            outputSize *= dim
        }
        outputElements = outputSize
        outputTensor = FloatArray(outputElements)
        outputBuffer = ByteBuffer.allocateDirect(outputElements * 4).order(ByteOrder.nativeOrder())
        outputFloatBuffer = outputBuffer.asFloatBuffer()

        val inputDesc = buildString {
            for (i in 0 until inputCount) {
                if (i > 0) append(" | ")
                append(i)
                append(":")
                append(inputNames[i])
                append(":")
                append(inputShapes[i].joinToString(prefix = "[", postfix = "]"))
                append(" elems=")
                append(inputElementCounts[i])
            }
        }
        Log.i(
            TAG,
            "Loaded model. inputs=$inputCount $inputDesc output=${outputShape.joinToString(prefix = "[", postfix = "]")}" 
        )
    }

    @Synchronized
    fun predict(
        egoHistory: List<V2VState>,
        neighbors: List<V2VManager.NeighborSnapshot>
    ): FloatArray {
        val sortedNeighbors = neighbors
            .asSequence()
            .filter { it.history.isNotEmpty() }
            .sortedBy {
                val s = it.latestState
                val ego = egoHistory.lastOrNull() ?: ZERO_STATE
                val dx = s.x - ego.x
                val dy = s.y - ego.y
                dx * dx + dy * dy
            }
            .take(MAX_NEIGHBORS)
            .toList()

        val egoSeq = buildPaddedSequence(egoHistory)
        val neighborSeqs = Array(MAX_NEIGHBORS) { idx ->
            if (idx < sortedNeighbors.size) buildPaddedSequence(sortedNeighbors[idx].history)
            else Array(TIMESTEPS) { ZERO_STATE }
        }

        // Training graph logic: everything is relative to t0 ego (first frame in window).
        val t0 = egoSeq[0]

        val egoTensor = FloatArray(TIMESTEPS * EGO_FEATURES)
        for (t in 0 until TIMESTEPS) {
            val e = egoSeq[t]
            val base = t * EGO_FEATURES
            val f0 = e.x - t0.x
            val f1 = e.y - t0.y
            val f2 = e.speed - t0.speed
            val f3 = e.accel - t0.accel
            val f4 = e.heading - t0.heading
            egoTensor[base] = normalize(f0, EGO_MU[0], EGO_SIGMA[0])
            egoTensor[base + 1] = normalize(f1, EGO_MU[1], EGO_SIGMA[1])
            egoTensor[base + 2] = normalize(f2, EGO_MU[2], EGO_SIGMA[2])
            egoTensor[base + 3] = normalize(f3, EGO_MU[3], EGO_SIGMA[3])
            egoTensor[base + 4] = normalize(f4, EGO_MU[4], EGO_SIGMA[4])
        }

        val nodeTensor = FloatArray(TIMESTEPS * MAX_NEIGHBORS * NODE_FEATURES)
        val edgeTensor = FloatArray(TIMESTEPS * MAX_NEIGHBORS * EDGE_FEATURES)

        for (t in 0 until TIMESTEPS) {
            val ego = egoSeq[t]
            val evx = ego.speed * cos(ego.heading)
            val evy = ego.speed * sin(ego.heading)
            val eax = ego.accel * cos(ego.heading)
            val eay = ego.accel * sin(ego.heading)

            for (n in 0 until MAX_NEIGHBORS) {
                val nbr = neighborSeqs[n][t]

                val nodeBase = (t * MAX_NEIGHBORS + n) * NODE_FEATURES
                val n0 = nbr.x - t0.x
                val n1 = nbr.y - t0.y
                val n2 = nbr.speed - t0.speed
                val n3 = nbr.accel - t0.accel
                val n4 = nbr.heading - t0.heading
                nodeTensor[nodeBase] = normalize(n0, NODE_MU[0], NODE_SIGMA[0])
                nodeTensor[nodeBase + 1] = normalize(n1, NODE_MU[1], NODE_SIGMA[1])
                nodeTensor[nodeBase + 2] = normalize(n2, NODE_MU[2], NODE_SIGMA[2])
                nodeTensor[nodeBase + 3] = normalize(n3, NODE_MU[3], NODE_SIGMA[3])
                nodeTensor[nodeBase + 4] = normalize(n4, NODE_MU[4], NODE_SIGMA[4])

                val nvx = nbr.speed * cos(nbr.heading)
                val nvy = nbr.speed * sin(nbr.heading)
                val nax = nbr.accel * cos(nbr.heading)
                val nay = nbr.accel * sin(nbr.heading)

                val edgeBase = (t * MAX_NEIGHBORS + n) * EDGE_FEATURES
                val dx = nbr.x - ego.x
                val dy = nbr.y - ego.y
                val e0 = sqrt(dx * dx + dy * dy)
                val e1 = sqrt((nvx - evx) * (nvx - evx) + (nvy - evy) * (nvy - evy))
                val e2 = sqrt((nax - eax) * (nax - eax) + (nay - eay) * (nay - eay))
                val e3 = headingDiff(ego.heading, nbr.heading)
                edgeTensor[edgeBase] = normalize(e0, EDGE_MU[0], EDGE_SIGMA[0])
                edgeTensor[edgeBase + 1] = normalize(e1, EDGE_MU[1], EDGE_SIGMA[1])
                edgeTensor[edgeBase + 2] = normalize(e2, EDGE_MU[2], EDGE_SIGMA[2])
                edgeTensor[edgeBase + 3] = normalize(e3, EDGE_MU[3], EDGE_SIGMA[3])
            }
        }

        fillInputs(nodeTensor, edgeTensor, egoTensor)

        val outputs = HashMap<Int, Any>(1)
        outputBuffer.rewind()
        outputFloatBuffer.position(0)
        outputs[0] = outputBuffer

        val inputObjects = Array(inputCount) { i -> inputBuffers[i] as Any }
        interpreter.runForMultipleInputsOutputs(inputObjects, outputs)

        outputBuffer.rewind()
        outputFloatBuffer.position(0)
        outputFloatBuffer.get(outputTensor, 0, outputElements)
        val denorm = outputTensor.copyOf()
        var i = 0
        while (i + 1 < denorm.size) {
            val xRel = denorm[i] * Y_SIGMA[0] + Y_MU[0]
            val yRel = denorm[i + 1] * Y_SIGMA[1] + Y_MU[1]
            denorm[i] = xRel + t0.x
            denorm[i + 1] = yRel + t0.y
            i += 2
        }
        return denorm
    }

    fun toPoints(prediction: FloatArray): List<PointF> {
        if (prediction.size < 2) return emptyList()
        val points = ArrayList<PointF>(prediction.size / 2)
        var i = 0
        while (i + 1 < prediction.size) {
            points.add(PointF(prediction[i], prediction[i + 1]))
            i += 2
        }
        return points
    }

    fun close() {
        interpreter.close()
    }

    private fun fillInputs(node: FloatArray, edge: FloatArray, ego: FloatArray) {
        var nodeIdx = -1
        var edgeIdx = -1
        var egoIdx = -1

        for (i in 0 until inputCount) {
            val n = inputNames[i].lowercase()
            if ("edge" in n) edgeIdx = i
            else if ("ego" in n) egoIdx = i
            else if ("node" in n) nodeIdx = i
        }

        if (nodeIdx != -1 && edgeIdx != -1 && egoIdx != -1) {
            fillInputBuffer(nodeIdx, node)
            fillInputBuffer(edgeIdx, edge)
            fillInputBuffer(egoIdx, ego)
            return
        }

        // Fallback by shape element count if names are stripped in export.
        for (i in 0 until inputCount) {
            when (inputElementCounts[i]) {
                TIMESTEPS * MAX_NEIGHBORS * NODE_FEATURES -> fillInputBuffer(i, node)
                TIMESTEPS * MAX_NEIGHBORS * EDGE_FEATURES -> fillInputBuffer(i, edge)
                TIMESTEPS * EGO_FEATURES -> fillInputBuffer(i, ego)
                else -> fillInputBuffer(i, FloatArray(inputElementCounts[i]))
            }
        }
    }

    private fun fillInputBuffer(index: Int, values: FloatArray) {
        val buffer = inputBuffers[index]
        val floatBuffer = inputFloatBuffers[index]
        buffer.rewind()
        floatBuffer.position(0)
        floatBuffer.put(values, 0, minOf(values.size, inputElementCounts[index]))
        val remaining = inputElementCounts[index] - minOf(values.size, inputElementCounts[index])
        if (remaining > 0) {
            repeat(remaining) { floatBuffer.put(0f) }
        }
    }

    private fun buildPaddedSequence(history: List<V2VState>): Array<V2VState> {
        if (history.isEmpty()) return Array(TIMESTEPS) { ZERO_STATE }
        val latest = history.last()
        val seq = Array(TIMESTEPS) { latest }
        val start = (TIMESTEPS - history.size).coerceAtLeast(0)
        val srcStart = (history.size - TIMESTEPS).coerceAtLeast(0)
        var dst = start
        var src = srcStart
        while (dst < TIMESTEPS && src < history.size) {
            seq[dst] = history[src]
            dst++
            src++
        }
        return seq
    }

    private fun headingDiff(a: Float, b: Float): Float {
        return atan2(sin(a - b), cos(a - b))
    }

    private fun normalize(value: Float, mean: Float, sigma: Float): Float {
        return (value - mean) / sigma
    }

    private fun loadModelBuffer(context: Context): ByteBuffer {
        return try {
            context.assets.openFd(MODEL_NAME).use { afd ->
                FileInputStream(afd.fileDescriptor).channel.use { channel ->
                    channel.map(
                        FileChannel.MapMode.READ_ONLY,
                        afd.startOffset,
                        afd.declaredLength
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falling back to byte[] model load: ${e.message}")
            val modelBytes = context.assets.open(MODEL_NAME).use { it.readBytes() }
            ByteBuffer.allocateDirect(modelBytes.size).order(ByteOrder.nativeOrder()).apply {
                put(modelBytes)
                rewind()
            }
        }
    }

    companion object {
        private const val TAG = "TrajectoryPredictor"
        private const val MODEL_NAME = "vehicle_trajectory.tflite"

        private const val TIMESTEPS = 50
        private const val MAX_NEIGHBORS = 9
        private const val NODE_FEATURES = 5
        private const val EDGE_FEATURES = 4
        private const val EGO_FEATURES = 5

        private val NODE_MU = floatArrayOf(-0.3497f, -0.0806f, -2.6429f, 0.6656f, -0.0301f)
        private val NODE_SIGMA = floatArrayOf(33.1871f, 31.8687f, 5.5200f, 3.0730f, 2.2291f)

        private val EDGE_MU = floatArrayOf(31.6329f, 5.6919f, 1.5132f, 0.0201f)
        private val EDGE_SIGMA = floatArrayOf(23.9369f, 5.4241f, 3.6699f, 1.7878f)

        private val EGO_MU = floatArrayOf(-0.1546f, -0.1562f, 0.3951f, 1.0756f, 0.0005f)
        private val EGO_SIGMA = floatArrayOf(15.9419f, 13.9904f, 2.5315f, 2.1792f, 0.8093f)

        private val Y_MU = floatArrayOf(-0.6823f, -0.5248f)
        private val Y_SIGMA = floatArrayOf(53.7343f, 46.8523f)

        private val ZERO_STATE = V2VState(0f, 0f, 0f, 0f, 0f)
    }
}
