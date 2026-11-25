package com.xrtcf.chess.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class EngineJUnit {
    @Test
    fun allEngineTestsPass() {
        EngineTests.runAll()
        assertEquals("chess-engine tests failed (see stdout)", 0, EngineTests.failed)
    }
}
