package com.xrtcf.chess.engine

/**
 * Engine strength configuration mapped onto Stockfish UCI options.
 *
 * Stockfish exposes:
 *  - `Skill Level` (0–20) — multi-move randomization / strength cap
 *  - `UCI_LimitStrength` (bool) + `UCI_Elo` — when the engine supports it
 *
 * We map the app's 400–3000 slider onto these options so the selected ELO
 * genuinely changes search behavior, not just the label. Exact rating is NOT
 * guaranteed — see README calibration notes.
 */
data class EloConfig(
    /** Requested playing strength, 400..3000. */
    val targetElo: Int,
    /** Engine Skill Level 0..20 to set. */
    val skillLevel: Int,
    /** Whether to enable UCI_LimitStrength. */
    val limitStrength: Boolean,
    /** UCI_Elo value when limitStrength is true (engine-clamped). */
    val uciElo: Int,
    /** Preset band label for UI. */
    val band: String,
) {
    init {
        require(targetElo in MIN_ELO..MAX_ELO) { "targetElo out of range: $targetElo" }
        require(skillLevel in 0..20) { "skillLevel out of range: $skillLevel" }
    }

    companion object {
        const val MIN_ELO = 400
        const val MAX_ELO = 3000

        /** UCI_Elo range supported by Stockfish 11+ (engine clamps further). */
        const val UCI_ELO_MIN = 1350
        const val UCI_ELO_MAX = 2850

        /**
         * Build config for [elo].
         *
         * Mapping strategy:
         *  - Below UCI_ELO_MIN: Skill Level scales 0..~8, LimitStrength off
         *    (UCI_Elo cannot go below engine minimum).
         *  - Within UCI_ELO_MIN..UCI_ELO_MAX: LimitStrength on, UCI_Elo = elo,
         *    Skill Level stays high so LimitStrength does the work.
         *  - Above UCI_ELO_MAX: full strength (Skill 20, LimitStrength off) —
         *    engine max is the ceiling; we do not fake higher ratings.
         */
        fun fromElo(elo: Int): EloConfig {
            val clamped = elo.coerceIn(MIN_ELO, MAX_ELO)
            val band = bandFor(clamped)
            return when {
                clamped < UCI_ELO_MIN -> {
                    // 400..1349 → Skill Level 0..8 linear-ish
                    val t = (clamped - MIN_ELO).toDouble() / (UCI_ELO_MIN - MIN_ELO)
                    val skill = (t * 8.0).toInt().coerceIn(0, 8)
                    EloConfig(clamped, skill, limitStrength = false, uciElo = UCI_ELO_MIN, band = band)
                }
                clamped <= UCI_ELO_MAX -> {
                    EloConfig(
                        targetElo = clamped,
                        skillLevel = 20,
                        limitStrength = true,
                        uciElo = clamped.coerceIn(UCI_ELO_MIN, UCI_ELO_MAX),
                        band = band,
                    )
                }
                else -> {
                    // 2851..3000 → full strength; report engine ceiling honestly
                    EloConfig(clamped, skillLevel = 20, limitStrength = false, uciElo = UCI_ELO_MAX, band = band)
                }
            }
        }

        fun bandFor(elo: Int): String = when (elo) {
            in 400..800 -> "Beginner"
            in 900..1200 -> "Casual"
            in 1300..1600 -> "Intermediate"
            in 1700..2000 -> "Advanced"
            in 2100..2500 -> "Expert"
            in 2600..3000 -> "Master"
            else -> when {
                elo < 400 -> "Beginner"
                elo < 900 -> "Beginner"
                elo < 1300 -> "Casual"
                elo < 1700 -> "Intermediate"
                elo < 2100 -> "Advanced"
                elo < 2600 -> "Expert"
                else -> "Master"
            }
        }

        /** Preset ELO stops for the slider UI. */
        val PRESETS: List<Pair<Int, String>> = listOf(
            400 to "Beginner",
            600 to "Beginner",
            800 to "Beginner",
            1000 to "Casual",
            1200 to "Casual",
            1400 to "Intermediate",
            1600 to "Intermediate",
            1800 to "Advanced",
            2000 to "Advanced",
            2200 to "Expert",
            2400 to "Expert",
            2600 to "Master",
            2800 to "Master",
            3000 to "Master",
        )
    }
}
