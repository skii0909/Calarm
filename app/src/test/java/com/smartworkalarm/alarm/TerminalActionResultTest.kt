package com.smartworkalarm.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TerminalActionResultTest {
    @Test
    fun `terminal result wire values round trip and unknown values fail closed`() {
        TerminalActionResult.entries.forEach { result ->
            assertEquals(result, TerminalActionResult.fromWireValue(result.wireValue))
        }
        assertNull(TerminalActionResult.fromWireValue(null))
        assertNull(TerminalActionResult.fromWireValue("unknown"))
    }
}
