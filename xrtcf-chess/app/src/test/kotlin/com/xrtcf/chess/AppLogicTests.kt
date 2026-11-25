package com.xrtcf.chess

import com.xrtcf.chess.engine.EloConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * App-level pure-JVM tests (no Robolectric): ELO mapping surface used by Settings.
 */
class AppLogicTests {

    @Test
    fun eloSliderBoundsMatchSpec() {
        assertEquals(400, EloConfig.MIN_ELO)
        assertEquals(3000, EloConfig.MAX_ELO)
    }

    @Test
    fun allSixPresetBandsPresent() {
        val bands = listOf(
            EloConfig.bandFor(400),
            EloConfig.bandFor(1000),
            EloConfig.bandFor(1500),
            EloConfig.bandFor(1800),
            EloConfig.bandFor(2300),
            EloConfig.bandFor(2800),
        )
        assertEquals(
            listOf("Beginner", "Casual", "Intermediate", "Advanced", "Expert", "Master"),
            bands,
        )
    }

    @Test
    fun midRangeEnablesLimitStrength() {
        val c = EloConfig.fromElo(1800)
        assertTrue(c.limitStrength)
        assertEquals(1800, c.uciElo)
        assertEquals(20, c.skillLevel)
    }

    @Test
    fun belowUciRangeUsesSkillOnly() {
        val c = EloConfig.fromElo(700)
        assertTrue(!c.limitStrength)
        assertTrue(c.skillLevel in 0..8)
    }

    @Test
    fun aboveEngineCeilingDoesNotFakeRating() {
        val c = EloConfig.fromElo(3000)
        // Full strength — we do not invent a phantom UCI_Elo above engine max
        assertTrue(!c.limitStrength)
        assertEquals(20, c.skillLevel)
    }
}
