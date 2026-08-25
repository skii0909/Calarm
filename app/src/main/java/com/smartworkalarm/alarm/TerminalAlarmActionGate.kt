package com.smartworkalarm.alarm

import java.util.concurrent.atomic.AtomicBoolean

internal class TerminalAlarmActionGate {
    private val acquired = AtomicBoolean(false)

    fun tryAcquire(): Boolean = acquired.compareAndSet(false, true)

    fun release() {
        acquired.set(false)
    }
}
