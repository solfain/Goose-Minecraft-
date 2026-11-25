package com.xrtcf.chess.analysis

import org.junit.Assert.assertEquals
import org.junit.Test

class AnalysisJUnit {
    @Test
    fun allAnalysisTestsPass() {
        AnalysisTests.runAll()
        assertEquals("chess-analysis tests failed (see stdout)", 0, AnalysisTests.failed)
    }
}
