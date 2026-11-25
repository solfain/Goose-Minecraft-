package com.xrtcf.chess.core

/**
 * PGN import/export.
 *
 * Import: seven-tag roster headers, FEN setup tag, move text with SAN,
 * NAGs stripped, comments in `{}` preserved per ply, `;` line comments,
 * variations in `()` ignored for linear replay (mainline only).
 * Export: headers + mainline SAN with move numbers.
 */
object Pgn {

    data class ImportResult(
        val moves: Int,
        val headers: Map<String, String>,
        val error: String? = null,
    )

    /** Export the mainline of [game] as PGN text. */
    fun export(game: Game, headers: Map<String, String> = defaultHeaders(game)): String = buildString {
        for ((k, v) in headers) {
            append("[")
            append(k)
            append(" \"")
            append(v.replace("\"", "\\\""))
            append("\"]\n")
        }
        append("\n")

        // Replay from start to emit clean SAN line
        val startFen = headers["FEN"]
        val temp = if (startFen != null) Game(Fen.parse(startFen)) else Game()
        for (entry in game.history) {
            temp.tryMove(entry.move)
        }

        var ply = 0
        for (entry in temp.history) {
            if (ply % 2 == 0) {
                if (ply > 0) append(' ')
                append(ply / 2 + 1)
                append('.')
            }
            append(' ')
            append(entry.san)
            val comment = game.comments[ply] ?: entry.comment
            if (comment.isNotEmpty()) {
                append(" {")
                append(comment)
                append('}')
            }
            append(' ')
            ply++
        }
        when (game.result) {
            GameResult.WHITE_WINS -> append(" 1-0")
            GameResult.BLACK_WINS -> append(" 0-1")
            GameResult.DRAW_STALEMATE,
            GameResult.DRAW_REPETITION,
            GameResult.DRAW_FIFTY_MOVE,
            GameResult.DRAW_INSUFFICIENT_MATERIAL,
            GameResult.DRAW_AGREEMENT -> append(" 1/2-1/2")
            GameResult.ONGOING -> {}
        }
        append('\n')
    }

    private fun defaultHeaders(game: Game): Map<String, String> = buildMap {
        put("Event", "XRTCF Chess Analysis")
        put("Site", "XRTCF Chess")
        put("Date", "????.??.??")
        put("Round", "-")
        put("White", "Player")
        put("Black", "Player")
        put("Result", when (game.result) {
            GameResult.WHITE_WINS -> "1-0"
            GameResult.BLACK_WINS -> "0-1"
            GameResult.ONGOING -> "*"
            else -> "1/2-1/2"
        })
        val start = game.history.firstOrNull()?.positionBefore
        if (start != null && start != Position.start()) {
            put("FEN", Fen.format(start))
            put("SetUp", "1")
        }
    }

    /**
     * Parse [pgn] and play the mainline into [game] (resets it first).
     * Returns move count and headers; on illegal SAN sets [ImportResult.error].
     */
    fun importInto(game: Game, pgn: String): ImportResult {
        val headers = parseHeaders(pgn)
        val body = stripHeaders(pgn)

        val startFen = headers["FEN"] ?: headers["Fen"]
        val startPos = if (startFen != null) {
            try {
                Fen.parse(startFen)
            } catch (e: FenException) {
                return ImportResult(0, headers, "Invalid FEN in PGN header: ${e.message}")
            }
        } else {
            Position.start()
        }

        game.reset(startPos)

        val tokens = tokenize(body)
        var moves = 0
        var ply = 0

        for (raw in tokens) {
            var token = raw
            // Skip move numbers like "12." "12..." "."
            if (token.endsWith(".")) {
                token = token.dropLast(1)
                if (token.isEmpty() || token.all { it.isDigit() }) continue
            }
            // Results
            if (token in setOf("1-0", "0-1", "1/2-1/2", "*")) continue
            // NAGs
            if (token.startsWith("$")) continue
            // Strip residual variation markers
            if (token == "(" || token == ")" || token == "<" || token == ">") continue

            val entry = game.tryMoveSan(token)
            if (entry == null) {
                return ImportResult(moves, headers, "Illegal or unparsable SAN '$token' at ply $ply")
            }
            moves++
            ply++
        }
        return ImportResult(moves, headers, null)
    }

    private fun parseHeaders(pgn: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val re = Regex("""\[(\w+)\s+"((?:\\.|[^"\\])*)"\]""")
        for (m in re.findAll(pgn)) {
            result[m.groupValues[1]] = m.groupValues[2].replace("\\\"", "\"").replace("\\\\", "\\")
        }
        return result
    }

    private fun stripHeaders(pgn: String): String {
        val re = Regex("""\[\w+\s+"(?:\\.|[^"\\])*"\]""")
        return re.replace(pgn, " ")
    }

    private fun tokenize(body: String): List<String> {
        // Remove comments {…} and ; line comments; collapse variations markers
        var text = body
        text = Regex("""\{[^}]*}""").replace(text, " ")
        text = text.lineSequence()
            .map { line ->
                val idx = line.indexOf(';')
                if (idx >= 0) line.substring(0, idx) else line
            }
            .joinToString(" ")
        // Drop nested variation content (simple paren matcher)
        text = dropVariations(text)
        return text.split(Regex("\\s+")).filter { it.isNotBlank() }
    }

    private fun dropVariations(text: String): String {
        val out = StringBuilder()
        var depth = 0
        for (c in text) {
            when (c) {
                '(' -> depth++
                ')' -> if (depth > 0) depth--
                else -> if (depth == 0) out.append(c)
            }
        }
        return out.toString()
    }
}

/** Extension: SAN move into [Game]. */
fun Game.tryMoveSan(san: String): MoveHistoryEntry? {
    val move = San.parseSan(currentPosition, san) ?: return null
    return tryMove(move)
}
