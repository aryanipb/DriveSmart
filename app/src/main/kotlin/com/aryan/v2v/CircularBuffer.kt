package com.aryan.v2v

class CircularBuffer<T>(private val capacity: Int) {
    private val items: Array<Any?> = Array(capacity) { null }
    private var head = 0
    private var size = 0

    @Synchronized
    fun add(item: T) {
        items[head] = item
        head = (head + 1) % capacity
        if (size < capacity) {
            size++
        }
    }

    @Suppress("UNCHECKED_CAST")
    @Synchronized
    fun snapshot(): List<T> {
        if (size == 0) return emptyList()
        val result = ArrayList<T>(size)
        val start = if (size == capacity) head else 0
        for (i in 0 until size) {
            val index = (start + i) % capacity
            result.add(items[index] as T)
        }
        return result
    }

    @Synchronized
    fun size(): Int = size
}
