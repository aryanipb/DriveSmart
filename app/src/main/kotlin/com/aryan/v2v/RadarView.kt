package com.aryan.v2v

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.min
import kotlin.math.cos
import kotlin.math.sin

class RadarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val lock = Any()

    private var egoState: V2VState = V2VState(0f, 0f, 0f, 0f, 0f)
    private var neighbors: List<V2VState> = emptyList()
    private var trajectory: FloatArray = FloatArray(0)

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(80, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val scanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(140, 92, 247, 255)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val egoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        setShadowLayer(18f, 0f, 0f, Color.argb(180, 255, 255, 255))
    }

    private val neighborPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4CAF50")
        style = Paint.Style.FILL
        setShadowLayer(16f, 0f, 0f, Color.argb(190, 90, 235, 120))
    }

    private val trajectoryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.CYAN
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        setShadowLayer(16f, 0f, 0f, Color.argb(210, 80, 233, 255))
    }

    fun render(ego: V2VState, neighborStates: List<V2VState>, prediction: FloatArray) {
        synchronized(lock) {
            egoState = ego
            neighbors = ArrayList(neighborStates)
            trajectory = prediction.copyOf()
        }
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = min(width, height) * 0.44f
        val metersToPx = radius / 120f

        canvas.drawCircle(centerX, centerY, radius, scanPaint)
        canvas.drawCircle(centerX, centerY, radius, gridPaint)
        canvas.drawCircle(centerX, centerY, radius * 0.66f, gridPaint)
        canvas.drawCircle(centerX, centerY, radius * 0.33f, gridPaint)

        val ego: V2VState
        val localNeighbors: List<V2VState>
        val localTrajectory: FloatArray
        synchronized(lock) {
            ego = egoState
            localNeighbors = neighbors
            localTrajectory = trajectory
        }

        val theta = ((System.currentTimeMillis() % 4000L) / 4000f) * (2f * Math.PI.toFloat())
        val scanX = centerX + radius * cos(theta)
        val scanY = centerY + radius * sin(theta)
        canvas.drawLine(centerX, centerY, scanX, centerY - (scanY - centerY), scanPaint)

        canvas.drawCircle(centerX, centerY, 12f, egoPaint)

        for (n in localNeighbors) {
            val dx = (n.x - ego.x) * metersToPx
            val dy = (n.y - ego.y) * metersToPx
            canvas.drawCircle(centerX + dx, centerY - dy, 8f, neighborPaint)
        }

        if (localTrajectory.size >= 4) {
            val path = Path()
            val x0 = centerX + (localTrajectory[0] - ego.x) * metersToPx
            val y0 = centerY - (localTrajectory[1] - ego.y) * metersToPx
            path.moveTo(x0, y0)

            var i = 2
            while (i + 1 < localTrajectory.size) {
                val px = centerX + (localTrajectory[i] - ego.x) * metersToPx
                val py = centerY - (localTrajectory[i + 1] - ego.y) * metersToPx
                path.lineTo(px, py)
                i += 2
            }
            canvas.drawPath(path, trajectoryPaint)
        }
    }
}
