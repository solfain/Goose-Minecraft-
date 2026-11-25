package com.xrtcf.chess.core

/**
 * Immutable chess position: piece placement, side to move, castling rights,
 * en-passant target, halfmove clock, fullmove number.
 *
 * Board indexing: 0 = a8 … 63 = h1 (FEN order).
 */
data class Position(
    val board: List<Piece?>,          // size 64
    val turn: Color,
    val castlingRights: Int,
    val enPassant: Square?,           // target square, null if none
    val halfmoveClock: Int,
    val fullmoveNumber: Int,
) {
    init {
        require(board.size == 64) { "Board must have 64 squares" }
        require(halfmoveClock >= 0) { "halfmoveClock must be >= 0" }
        require(fullmoveNumber >= 1) { "fullmoveNumber must be >= 1" }
    }

    fun pieceAt(sq: Square): Piece? = board[sq.index]

    fun kingSquare(color: Color): Square? =
        board.indexOfFirst { it?.type == PieceType.KING && it.color == color }
            .let { if (it >= 0) Square(it) else null }

    companion object {
        const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

        fun empty(): Position = Position(
            board = List(64) { null },
            turn = Color.WHITE,
            castlingRights = 0,
            enPassant = null,
            halfmoveClock = 0,
            fullmoveNumber = 1,
        )

        fun start(): Position = Fen.parse(START_FEN)
    }
}
