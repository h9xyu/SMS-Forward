package com.example.smstopc

import java.util.concurrent.ConcurrentHashMap

object DedupCache {

    private val recent = ConcurrentHashMap<String, Long>()

    fun tryMark(code: String): Boolean {
        val now = System.currentTimeMillis()
        // Clean stale entries
        recent.entries.removeAll { now - it.value > 30_000 }
        // Try to insert; returns null if new, or the old value if already present
        val old = recent.putIfAbsent(code, now)
        return old == null
    }
}
