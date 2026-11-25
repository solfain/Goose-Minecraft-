package com.xrtcf.chess.core

/**
 * Legal move generation for standard chess.
 *
 * Generates pseudo-legal moves then filters those that leave the mover's king
 * in check. Implements pawn double-step, en passant, castling (both sides),
 * promotion, and all capture rules.
 */
object MoveGenerator {

    private val KNIGHT_OFFSETS = intArrayOf(
        -17, -15, -10, -6, 6, 10, 15, 17,
    )
    private val KING_OFFSETS = intArrayOf(
        -9, -8, -7, -1, 1, 7, 8, 9,
    )
    private val BISHOP_DIRS = intArrayOf(-9, -7, 7, 9)
    private val ROOK_DIRS = intArrayOf(-8, -1, 1, 8)

    /** All legal moves for the side to move in [pos], sorted for stable output. */
    fun legalMoves(pos: Position): List<Move> =
        pseudoLegalMoves(pos).filter { move ->
            val after = Rules.applyMove(pos, move)
            !Rules.isInCheck(after, pos.turn)
        }.sortedWith(compareBy({ it.from.index }, { it.to.index }, { it.promotion?.ordinal ?: -1 }))

    /** Pseudo-legal moves: may leave own king in check (used by legality filter). */
    fun pseudoLegalMoves(pos: Position): List<Move> {
        val moves = ArrayList<Move>(48)
        val us = pos.turn
        for (idx in 0..63) {
            val piece = pos.board[idx] ?: continue
            if (piece.color != us) continue
            val from = Square(idx)
            when (piece.type) {
                PieceType.PAWN -> generatePawnMoves(pos, from, piece, moves)
                PieceType.KNIGHT -> generateStepMoves(pos, from, piece, KNIGHT_OFFSETS, moves)
                PieceType.KING -> {
                    generateStepMoves(pos, from, piece, KING_OFFSETS, moves)
                    generateCastlingMoves(pos, from, piece, moves)
                }
                PieceType.BISHOP -> generateSlideMoves(pos, from, piece, BISHOP_DIRS, moves)
                PieceType.ROOK -> generateSlideMoves(pos, from, piece, ROOK_DIRS, moves)
                PieceType.QUEEN -> {
                    generateSlideMoves(pos, from, piece, BISHOP_DIRS, moves)
                    generateSlideMoves(pos, from, piece, ROOK_DIRS, moves)
                }
            }
        }
        return moves
    }

    /** Moves that start on [from] (for tap-to-move / drag UI). */
    fun movesFrom(pos: Position, from: Square): List<Move> =
        legalMoves(pos).filter { it.from == from }

    // --- generators ---

    private fun generatePawnMoves(pos: Position, from: Square, piece: Piece, out: MutableList<Move>) {
        val white = piece.color == Color.WHITE
        val dir = if (white) -8 else 8          // white moves toward rank index 0
        val startRank = if (white) 6 else 1
        val promoRank = if (white) 0 else 7

        val oneStep = from.index + dir
        if (oneStep in 0..63 && pos.board[oneStep] == null) {
            addPawnMove(from, Square(oneStep), pos, promotionRank = promoRank, out)
            // Double step from starting rank
            if (from.rank == startRank) {
                val twoStep = from.index + 2 * dir
                if (pos.board[twoStep] == null) {
                    out.add(Move(from, Square(twoStep)))
                }
            }
        }

        // Captures (including en passant)
        val file = from.file
        for (df in intArrayOf(-1, 1)) {
            val nf = file + df
            if (nf !in 0..7) continue
            val targetIdx = oneStep - (if (white) 0 else 0) + (nf - file)
            // oneStep already includes dir; adjust file:
            val tIdx = (from.index + dir) + (nf - file)
            if (tIdx !in 0..63) continue
            val target = Square(tIdx)
            val occupant = pos.board[tIdx]
            if (occupant != null && occupant.color != piece.color) {
                addPawnMove(from, target, pos, promotionRank = promoRank, out)
            } else if (occupant == null && pos.enPassant == target) {
                out.add(Move(from, target, isEnPassant = true))
            }
        }
    }

    private fun addPawnMove(
        from: Square,
        to: Square,
        pos: Position,
        promotionRank: Int,
        out: MutableList<Move>,
    ) {
        if (to.rank == promotionRank) {
            for (promo in listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)) {
                out.add(Move(from, to, promotion = promo))
            }
        } else {
            out.add(Move(from, to))
        }
    }

    private fun generateStepMoves(
        pos: Position,
        from: Square,
        piece: Piece,
        offsets: IntArray,
        out: MutableList<Move>,
    ) {
        val base = from.index
        val baseFile = from.file
        val baseRank = from.rank
        for (offset in offsets) {
            val target = base + offset
            if (target !in 0..63) continue
            // Reject wraps across file edges for knight/king
            val tf = target % 8
            val tr = target / 8
            if (knightOrKingWrapped(baseFile, baseRank, tf, tr, offsets === KNIGHT_OFFSETS || offsets === KING_OFFSETS)) {
                // handled below via distance check for knights; kings use chebyshev
            }
            if (offsets === KNIGHT_OFFSETS) {
                val df = kotlin.math.abs(tf - baseFile)
                val dr = kotlin.math.abs(tr - baseRank)
                if (!((df == 1 && dr == 2) || (df == 2 && dr == 1))) continue
            } else {
                // King: chebyshev distance 1
                if (kotlin.math.abs(tf - baseFile) > 1 || kotlin.math.abs(tr - baseRank) > 1) continue
            }
            val occupant = pos.board[target]
            if (occupant == null || occupant.color != piece.color) {
                out.add(Move(from, Square(target)))
            }
        }
    }

    private fun knightOrKingWrapped(bf: Int, br: Int, tf: Int, tr: Int, check: Boolean): Boolean = false

    private fun generateSlideMoves(
        pos: Position,
        from: Square,
        piece: Piece,
        directions: IntArray,
        out: MutableList<Move>,
    ) {
        val base = from.index
        val baseFile = from.file
        val baseRank = from.rank
        for (dir in directions) {
            var target = base + dir
            while (target in 0..63) {
                val tf = target % 8
                val tr = target / 8
                // Reject file/rank wraps: each step must stay on the same ray.
                val onRay = when (dir) {
                    -1, 1 -> tr == baseRank
                    -8, 8 -> tf == baseFile
                    else -> kotlin.math.abs(tf - baseFile) == kotlin.math.abs(tr - baseRank)
                }
                if (!onRay) break
                val occupant = pos.board[target]
                if (occupant == null) {
                    out.add(Move(from, Square(target)))
                } else {
                    if (occupant.color != piece.color) out.add(Move(from, Square(target)))
                    break
                }
                target += dir
            }
        }
    }

    private fun generateCastlingMoves(pos: Position, from: Square, piece: Piece, out: MutableList<Move>) {
        if (Rules.isInCheck(pos, piece.color)) return
        val white = piece.color == Color.WHITE
        val rank = if (white) 7 else 0

        // King must be on its home square
        if (from.file != 4 || from.rank != rank) return

        val kingSide = if (white) CastleRights.WHITE_KING else CastleRights.BLACK_KING
        val queenSide = if (white) CastleRights.WHITE_QUEEN else CastleRights.BLACK_QUEEN

        // Kingside: rook on h-file corner, f and g empty, king not through check on e→f→g
        if (pos.castlingRights and kingSide != 0) {
            val rookSq = Square(rank * 8 + 7)
            val rook = pos.board[rookSq.index]
            val f = Square(rank * 8 + 5)
            val g = Square(rank * 8 + 6)
            if (rook?.type == PieceType.ROOK && rook.color == piece.color &&
                pos.board[f.index] == null && pos.board[g.index] == null &&
                !Rules.squareAttacked(pos, f, piece.color.opposite) &&
                !Rules.squareAttacked(pos, g, piece.color.opposite)
            ) {
                out.add(Move(from, g, isCastling = true))
            }
        }
        // Queenside: rook on a-file corner, b/c/d empty; king not through check on e→d→c
        if (pos.castlingRights and queenSide != 0) {
            val rookSq = Square(rank * 8 + 0)
            val rook = pos.board[rookSq.index]
            val d = Square(rank * 8 + 3)
            val c = Square(rank * 8 + 2)
            val b = Square(rank * 8 + 1)
            if (rook?.type == PieceType.ROOK && rook.color == piece.color &&
                pos.board[d.index] == null && pos.board[c.index] == null && pos.board[b.index] == null &&
                !Rules.squareAttacked(pos, d, piece.color.opposite) &&
                !Rules.squareAttacked(pos, c, piece.color.opposite)
            ) {
                out.add(Move(from, c, isCastling = true))
            }
        }
    }
}
