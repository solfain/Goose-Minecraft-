package com.xrtcf.chess.core

/** Thrown when FEN/PGN input is malformed. */
class FenException(message: String) : IllegalArgumentException(message)
class PgnException(message: String) : IllegalArgumentException(message)

/**
 * FEN import/export with validation.
 * Spec: https://www.chess.org.cn/fen.txt (Forsyth-Edwards Notation).
 */
object Fen {

    fun parse(fen: String): Position {
        val parts = fen.trim().split(Regex("\\s+"))
        if (parts.size < 4) {
            throw FenException("FEN needs at least 4 fields (placement, turn, castling, ep), got ${parts.size}")
        }
        val board = parsePlacement(parts[0])
        val turn = parseTurn(parts[1])
        val castling = parseCastling(parts[2])
        val ep = parseEnPassant(parts[3])
        val halfmove = if (parts.size > 4) parseNonNegative(parts[4], "halfmove clock") else 0
        val fullmove = if (parts.size > 5) parsePositive(parts[5], "fullmove number") else 1

        val pos = Position(board, turn, castling, ep, halfmove, fullmove)
        validateKings(pos)
        return pos
    }

    fun format(pos: Position): String = buildString {
        append(formatPlacement(pos.board))
        append(' ')
        append(if (pos.turn == Color.WHITE) 'w' else 'b')
        append(' ')
        append(formatCastling(pos.castlingRights))
        append(' ')
        append(pos.enPassant?.algebraic ?: "-")
        append(' ')
        append(pos.halfmoveClock)
        append(' ')
        append(pos.fullmoveNumber)
    }

    /** Returns true if [fen] parses without error. */
    fun isValid(fen: String): Boolean = try {
        parse(fen)
        true
    } catch (_: FenException) {
        false
    }

    // --- internals ---

    private fun parsePlacement(field: String): List<Piece?> {
        val board = MutableList<Piece?>(64) { null }
        val ranks = field.split('/')
        if (ranks.size != 8) throw FenException("Placement must have 8 ranks, got ${ranks.size}")

        for ((rankIdx, rankStr) in ranks.withIndex()) {
            var file = 0
            for (c in rankStr) {
                when {
                    c in '1'..'8' -> {
                        file += c - '0'
                    }
                    Piece.fromFenChar(c) != null -> {
                        if (file > 7) throw FenException("Rank $rankIdx overflows file 8: $rankStr")
                        board[rankIdx * 8 + file] = Piece.fromFenChar(c)
                        file++
                    }
                    else -> throw FenException("Invalid placement character '$c' in rank '$rankStr'")
                }
            }
            if (file != 8) {
                throw FenException("Rank $rankIdx sums to $file files, expected 8: '$rankStr'")
            }
        }
        return board
    }

    private fun parseTurn(field: String): Color = when (field) {
        "w" -> Color.WHITE
        "b" -> Color.BLACK
        else -> throw FenException("Side to move must be 'w' or 'b', got '$field'")
    }

    private fun parseCastling(field: String): Int {
        if (field == "-") return 0
        var rights = 0
        for (c in field) {
            rights = when (c) {
                'K' -> rights or CastleRights.WHITE_KING
                'Q' -> rights or CastleRights.WHITE_QUEEN
                'k' -> rights or CastleRights.BLACK_KING
                'q' -> rights or CastleRights.BLACK_QUEEN
                else -> throw FenException("Invalid castling character '$c'")
            }
        }
        return rights
    }

    private fun parseEnPassant(field: String): Square? {
        if (field == "-") return null
        return Square.fromAlgebraic(field)
            ?: throw FenException("Invalid en-passant square '$field'")
    }

    private fun parseNonNegative(field: String, label: String): Int =
        field.toIntOrNull()?.takeIf { it >= 0 }
            ?: throw FenException("Invalid $label '$field'")

    private fun parsePositive(field: String, label: String): Int =
        field.toIntOrNull()?.takeIf { it >= 1 }
            ?: throw FenException("Invalid $label '$field'")

    private fun validateKings(pos: Position) {
        val whiteKings = pos.board.count { it?.type == PieceType.KING && it.color == Color.WHITE }
        val blackKings = pos.board.count { it?.type == PieceType.KING && it.color == Color.BLACK }
        if (whiteKings != 1) throw FenException("Expected exactly 1 white king, found $whiteKings")
        if (blackKings != 1) throw FenException("Expected exactly 1 black king, found $blackKings")
    }

    private fun formatPlacement(board: List<Piece?>): String =
        (0..7).joinToString("/") { rank ->
            buildString {
                var empty = 0
                for (file in 0..7) {
                    val p = board[rank * 8 + file]
                    if (p == null) empty++
                    else {
                        if (empty > 0) { append(empty); empty = 0 }
                        append(p.symbol)
                    }
                }
                if (empty > 0) append(empty)
            }
        }

    private fun formatCastling(rights: Int): String {
        if (rights == 0) return "-"
        return buildString {
            if (rights and CastleRights.WHITE_KING != 0) append('K')
            if (rights and CastleRights.WHITE_QUEEN != 0) append('Q')
            if (rights and CastleRights.BLACK_KING != 0) append('k')
            if (rights and CastleRights.BLACK_QUEEN != 0) append('q')
        }
    }
}
