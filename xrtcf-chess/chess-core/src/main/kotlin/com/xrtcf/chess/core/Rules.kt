package com.xrtcf.chess.core

/** Terminal / draw game states. */
enum class GameResult {
    ONGOING,
    WHITE_WINS,
    BLACK_WINS,
    DRAW_STALEMATE,
    DRAW_REPETITION,
    DRAW_FIFTY_MOVE,
    DRAW_INSUFFICIENT_MATERIAL,
    DRAW_AGREEMENT,
}

/**
 * Position transitions, check detection, and game-end conditions.
 */
object Rules {

    // --- applying moves ---

    /** Apply [move] (must be legal in [pos]) and return the resulting position. */
    fun applyMove(pos: Position, move: Move): Position {
        val piece = pos.board[move.from.index]
            ?: throw IllegalArgumentException("No piece on ${move.from.algebraic}")
        require(piece.color == pos.turn) { "Piece color does not match side to move" }

        val board = pos.board.toMutableList()
        var castling = pos.castlingRights
        var ep: Square? = null

        // Remove captured piece (normal or en passant)
        val captured = if (move.isEnPassant) {
            val capIdx = if (piece.color == Color.WHITE) move.to.index + 8 else move.to.index - 8
            board[capIdx]
        } else {
            board[move.to.index]
        }

        board[move.from.index] = null
        if (move.isEnPassant) {
            val capIdx = if (piece.color == Color.WHITE) move.to.index + 8 else move.to.index - 8
            board[capIdx] = null
        }

        // Place moving piece (promotion if any)
        board[move.to.index] = if (move.promotion != null) {
            Piece(move.promotion, piece.color)
        } else {
            piece
        }

        // Castling: move the rook too
        if (move.isCastling) {
            val rank = move.from.rank
            when (move.to.file) {
                6 -> { // kingside
                    val rookFrom = Square(rank * 8 + 7)
                    val rookTo = Square(rank * 8 + 5)
                    board[rookTo.index] = board[rookFrom.index]
                    board[rookFrom.index] = null
                }
                2 -> { // queenside
                    val rookFrom = Square(rank * 8 + 0)
                    val rookTo = Square(rank * 8 + 3)
                    board[rookTo.index] = board[rookFrom.index]
                    board[rookFrom.index] = null
                }
            }
        }

        // Castling rights updates
        castling = updateCastlingRights(castling, move, piece)

        // En passant target set on double pawn push
        if (piece.type == PieceType.PAWN && kotlin.math.abs(move.to.index - move.from.index) == 16) {
            val mid = (move.from.index + move.to.index) / 2
            ep = Square(mid)
        }

        // Clocks
        val resetsClock = piece.type == PieceType.PAWN || captured != null
        val halfmove = if (resetsClock) 0 else pos.halfmoveClock + 1
        val fullmove = if (pos.turn == Color.BLACK) pos.fullmoveNumber + 1 else pos.fullmoveNumber

        return Position(
            board = board,
            turn = pos.turn.opposite,
            castlingRights = castling,
            enPassant = ep,
            halfmoveClock = halfmove,
            fullmoveNumber = fullmove,
        )
    }

    private fun updateCastlingRights(rights: Int, move: Move, piece: Piece): Int {
        var r = rights
        // King moves clear both rights for that color
        if (piece.type == PieceType.KING) {
            r = if (piece.color == Color.WHITE) {
                r and (CastleRights.WHITE_KING or CastleRights.WHITE_QUEEN).inv()
            } else {
                r and (CastleRights.BLACK_KING or CastleRights.BLACK_QUEEN).inv()
            }
        }
        // Rook moves/captures clear the matching corner
        r = clearRookRight(r, move.from)
        r = clearRookRight(r, move.to)
        return r
    }

    private fun clearRookRight(rights: Int, sq: Square): Int = when (sq.index) {
        63 -> rights and CastleRights.WHITE_KING.inv()    // h1
        56 -> rights and CastleRights.WHITE_QUEEN.inv()   // a1
        7 -> rights and CastleRights.BLACK_KING.inv()     // h8
        0 -> rights and CastleRights.BLACK_QUEEN.inv()    // a8
        else -> rights
    }

    // --- check / attack ---

    /** True if [color]'s king is attacked in [pos]. */
    fun isInCheck(pos: Position, color: Color): Boolean {
        val king = pos.kingSquare(color) ?: return false
        return squareAttacked(pos, king, color.opposite)
    }

    /** True if [sq] is attacked by any piece of [byColor]. */
    fun squareAttacked(pos: Position, sq: Square, byColor: Color): Boolean {
        // Pawns
        val pawnDir = if (byColor == Color.WHITE) 8 else -8  // attacker is behind relative to victim
        // White pawn on rank r attacks toward rank r-1 (index decreases)
        // A square is attacked by white pawn if pawn is at sq+8±1 (index +8 is one rank down for white…)
        // White pawns attack index-9 and index-7 (toward rank 0).
        // So sq is attacked by white pawn at sq+9 or sq+7 (files must match).
        val pawnSourceOffsets = if (byColor == Color.WHITE) intArrayOf(7, 9) else intArrayOf(-7, -9)
        for (off in pawnSourceOffsets) {
            val src = sq.index + off
            if (src !in 0..63) continue
            val df = kotlin.math.abs((src % 8) - sq.file)
            if (df != 1) continue
            val p = pos.board[src]
            if (p != null && p.color == byColor && p.type == PieceType.PAWN) return true
        }

        // Knights
        for (off in intArrayOf(-17, -15, -10, -6, 6, 10, 15, 17)) {
            val src = sq.index + off
            if (src !in 0..63) continue
            val df = kotlin.math.abs((src % 8) - sq.file)
            val dr = kotlin.math.abs((src / 8) - sq.rank)
            if (!((df == 1 && dr == 2) || (df == 2 && dr == 1))) continue
            val p = pos.board[src]
            if (p != null && p.color == byColor && p.type == PieceType.KNIGHT) return true
        }

        // King
        for (off in intArrayOf(-9, -8, -7, -1, 1, 7, 8, 9)) {
            val src = sq.index + off
            if (src !in 0..63) continue
            val df = kotlin.math.abs((src % 8) - sq.file)
            val dr = kotlin.math.abs((src / 8) - sq.rank)
            if (df > 1 || dr > 1) continue
            val p = pos.board[src]
            if (p != null && p.color == byColor && p.type == PieceType.KING) return true
        }

        // Sliding: bishops/queens on diagonals
        if (rayAttack(pos, sq, byColor, intArrayOf(-9, -7, 7, 9), listOf(PieceType.BISHOP, PieceType.QUEEN))) return true
        // Sliding: rooks/queens on orthogonals
        if (rayAttack(pos, sq, byColor, intArrayOf(-8, -1, 1, 8), listOf(PieceType.ROOK, PieceType.QUEEN))) return true

        return false
    }

    private fun rayAttack(
        pos: Position,
        sq: Square,
        byColor: Color,
        dirs: IntArray,
        types: List<PieceType>,
    ): Boolean {
        for (dir in dirs) {
            var src = sq.index + dir
            val baseFile = sq.file
            val baseRank = sq.rank
            while (src in 0..63) {
                val sf = src % 8
                val sr = src / 8
                val onRay = when (dir) {
                    -1, 1 -> sr == baseRank
                    -8, 8 -> sf == baseFile
                    else -> kotlin.math.abs(sf - baseFile) == kotlin.math.abs(sr - baseRank)
                }
                if (!onRay) break
                val p = pos.board[src]
                if (p != null) {
                    if (p.color == byColor && p.type in types) return true
                    break
                }
                src += dir
            }
        }
        return false
    }

    // --- game end ---

    /** Determine result if the game is over in [pos] given prior position keys for repetition. */
    fun resultOf(pos: Position, repetitionKeys: List<String> = emptyList()): GameResult {
        val legal = MoveGenerator.legalMoves(pos)
        if (legal.isEmpty()) {
            return if (isInCheck(pos, pos.turn)) {
                if (pos.turn == Color.WHITE) GameResult.BLACK_WINS else GameResult.WHITE_WINS
            } else {
                GameResult.DRAW_STALEMATE
            }
        }
        if (pos.halfmoveClock >= 100) return GameResult.DRAW_FIFTY_MOVE
        if (hasInsufficientMaterial(pos)) return GameResult.DRAW_INSUFFICIENT_MATERIAL
        if (isThreefold(repetitionKeys)) return GameResult.DRAW_REPETITION
        return GameResult.ONGOING
    }

    fun isCheckmate(pos: Position): Boolean =
        MoveGenerator.legalMoves(pos).isEmpty() && isInCheck(pos, pos.turn)

    fun isStalemate(pos: Position): Boolean =
        MoveGenerator.legalMoves(pos).isEmpty() && !isInCheck(pos, pos.turn)

    fun isInsufficientMaterial(pos: Position): Boolean = hasInsufficientMaterial(pos)

    /** K vs K, KB vs K, KN vs K, KBB vs K (same-color bishops only simplified to material count). */
    fun hasInsufficientMaterial(pos: Position): Boolean {
        val nonKings = pos.board.filterNotNull().filter { it.type != PieceType.KING }
        if (nonKings.isEmpty()) return true
        if (nonKings.size == 1) {
            val p = nonKings[0]
            return p.type == PieceType.BISHOP || p.type == PieceType.KNIGHT
        }
        // Two bishops same color only (both on same square-color) vs bare king is draw-ish;
        // standard rule: exactly two bishops of the same color with no pawns/rooks/queens/knights.
        if (nonKings.size == 2 &&
            nonKings.all { it.type == PieceType.BISHOP } &&
            nonKings[0].color == nonKings[1].color
        ) {
            val squares = pos.board.withIndex()
                .filter { (_, p) -> p != null && p.type == PieceType.BISHOP }
                .map { (i, _) -> Square(i) }
            if (squares.size == 2) {
                return squares[0].isLight == squares[1].isLight
            }
        }
        // King + bishop vs king + bishop on opposite colors: not always theoretical draw
        // but FIDE dead position requires no possible mate — keep conservative false.
        return false
    }

    fun isThreefold(keys: List<String>): Boolean {
        if (keys.size < 3) return false
        val last = keys.last()
        return keys.count { it == last } >= 3
    }

    /**
     * Repetition key: piece placement + turn + castling + en passant
     * (en passant only when a capture is actually possible — simplified: always include if set).
     */
    fun repetitionKey(pos: Position): String = buildString {
        for (p in pos.board) append(p?.symbol ?: '.')
        append(if (pos.turn == Color.WHITE) 'w' else 'b')
        append(pos.castlingRights)
        append(pos.enPassant?.algebraic ?: "-")
    }
}
