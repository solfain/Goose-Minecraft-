package com.xrtcf.chess.core

/**
 * Standard Algebraic Notation (SAN) generation and parsing helpers.
 */
object San {

    /** Generate SAN for [move] applied to [before], with [after] for check/mate suffix. */
    fun toSan(before: Position, move: Move, after: Position): String {
        val piece = before.board[move.from.index]
            ?: return move.uci

        if (move.isCastling) {
            val base = if (move.to.file == 6) "O-O" else "O-O-O"
            return base + suffix(after, before.turn)
        }

        val sb = StringBuilder()
        if (piece.type != PieceType.PAWN) {
            sb.append(piece.type.symbol.uppercaseChar())
            // Disambiguation: other same-type pieces that can also reach 'to'
            appendDisambiguation(before, move, piece, sb)
        } else if (move.captured != null || isCapture(before, move)) {
            sb.append('a' + move.from.file)
            sb.append('x')
        }

        sb.append(move.to.algebraic)

        if (move.promotion != null) {
            sb.append('=')
            sb.append(move.promotion.symbol.uppercaseChar())
        }

        sb.append(suffix(after, before.turn))
        return sb.toString()
    }

    private fun isCapture(before: Position, move: Move): Boolean {
        if (move.isEnPassant) return true
        val target = before.board[move.to.index]
        return target != null && target.color != before.turn
    }

    private fun suffix(after: Position, mover: Color): String {
        return when {
            Rules.isCheckmate(after) -> "#"
            Rules.isInCheck(after, after.turn) -> "+"
            else -> ""
        }
    }

    private fun appendDisambiguation(before: Position, move: Move, piece: Piece, sb: StringBuilder) {
        val others = MoveGenerator.legalMoves(before).filter {
            it.to == move.to && it.from != move.from &&
                before.board[it.from.index]?.type == piece.type &&
                before.board[it.from.index]?.color == piece.color
        }
        if (others.isEmpty()) return

        val sameFile = others.any { it.from.file == move.from.file }
        val sameRank = others.any { it.from.rank == move.from.rank }
        when {
            !sameFile -> sb.append('a' + move.from.file)
            !sameRank -> sb.append('8' - move.from.rank)
            else -> {
                sb.append('a' + move.from.file)
                sb.append('8' - move.from.rank)
            }
        }
    }

    /**
     * Parse a SAN token in the context of [pos] into a [Move], or null.
     * Supports: e4, exd5, Nf3, R1a3, Qh4xe1, e8=Q, O-O, O-O-O, +/# suffixes.
     */
    fun parseSan(pos: Position, san: String): Move? {
        var s = san.trim()
            .removeSuffix("+").removeSuffix("#")
            .removeSuffix("?!").removeSuffix("!?")
            .removeSuffix("?").removeSuffix("!")
        if (s.isEmpty()) return null

        if (s == "O-O" || s == "0-0") {
            return MoveGenerator.legalMoves(pos).firstOrNull { it.isCastling && it.to.file == 6 }
        }
        if (s == "O-O-O" || s == "0-0-0") {
            return MoveGenerator.legalMoves(pos).firstOrNull { it.isCastling && it.to.file == 2 }
        }

        var promo: PieceType? = null
        val promoIdx = s.indexOf('=')
        if (promoIdx >= 0) {
            promo = PieceType.fromSymbol(s.last()) ?: return null
            s = s.substring(0, promoIdx)
        }

        // Destination is always the last two chars that look like a square
        if (s.length < 2) return null
        val destAlgebraic = s.takeLast(2)
        val dest = Square.fromAlgebraic(destAlgebraic) ?: return null
        s = s.dropLast(2)

        // Capture marker
        val isCapture = if (s.endsWith("x")) {
            s = s.dropLast(1)
            true
        } else false

        // Leading piece letter
        var pieceType = PieceType.PAWN
        if (s.isNotEmpty() && s[0].isUpperCase() && s[0] in "NBRQK") {
            pieceType = PieceType.fromSymbol(s[0])!!
            s = s.substring(1)
        }

        // Remaining: optional disambiguation file/rank (and maybe from-file for pawn capture already handled)
        var fromFile: Int? = null
        var fromRank: Int? = null
        for (c in s) {
            when (c) {
                in 'a'..'h' -> fromFile = c - 'a'
                in '1'..'8' -> fromRank = '8' - c
                else -> return null
            }
        }

        val candidates = MoveGenerator.legalMoves(pos).filter { m ->
            m.to == dest &&
                pos.board[m.from.index]?.type == pieceType &&
                pos.board[m.from.index]?.color == pos.turn &&
                (promo == null || m.promotion == promo) &&
                (fromFile == null || m.from.file == fromFile) &&
                (fromRank == null || m.from.rank == fromRank) &&
                (!isCapture || m.isEnPassant || pos.board[m.to.index] != null || pieceType == PieceType.PAWN)
        }
        return candidates.singleOrNull() ?: candidates.firstOrNull()
    }
}
