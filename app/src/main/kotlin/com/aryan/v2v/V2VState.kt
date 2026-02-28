package com.aryan.v2v

data class V2VState(
    val x: Float,
    val y: Float,
    val speed: Float,
    val heading: Float,
    val accel: Float,
    val timestampMs: Long = System.currentTimeMillis()
)
