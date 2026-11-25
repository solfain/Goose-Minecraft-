package com.xrtcf.chess.training

import org.junit.Assert.assertEquals
import org.junit.Test

class TrainingJUnit {
    @Test
    fun allTrainingTestsPass() {
        TrainingTests.runAll()
        assertEquals("chess-training tests failed (see stdout)", 0, TrainingTests.failed)
    }
}
