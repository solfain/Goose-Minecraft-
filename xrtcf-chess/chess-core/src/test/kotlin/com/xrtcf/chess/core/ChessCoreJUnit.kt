package com.xrtcf.chess.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JUnit 4 wrapper so Gradle `test` executes the same suite used for local verification.
 */
class ChessCoreJUnit {

    @Test
    fun allChessCoreTestsPass() {
        ChessCoreTests.runAll()
        assertEquals("chess-core tests failed (see stdout for details)", 0, ChessCoreTests.failed)
    }
}
