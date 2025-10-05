package com.soumo.child.utils

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

object AccessibilityState {
    val isBound = AtomicBoolean(false)
    val onServiceConnectedCount = AtomicInteger(0)
    val onUnbindCount = AtomicInteger(0)
    val eventExceptionCount = AtomicInteger(0)
    val lastConnectedAtMs = AtomicLong(0L)
    val lastHeartbeatAtMs = AtomicLong(0L)
}


