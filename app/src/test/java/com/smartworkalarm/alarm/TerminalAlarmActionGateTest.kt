package com.smartworkalarm.alarm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalAlarmActionGateTest {
    @Test
    fun `only the first terminal action acquires the gate`() {
        val gate = TerminalAlarmActionGate()

        assertTrue(gate.tryAcquire())
        assertFalse(gate.tryAcquire())
    }

    @Test
    fun `failed dispatch can release the gate for retry`() {
        val gate = TerminalAlarmActionGate()
        assertTrue(gate.tryAcquire())

        gate.release()

        assertTrue(gate.tryAcquire())
    }
}
