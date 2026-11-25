package com.xrtcf.chess.core

/** Side to move / piece ownership. */
enum class Color { WHITE, BLACK;

    val opposite: Color get() = if (this == WHITE) BLACK else WHITE
}

/** The six chess piece types. */
enum class PieceType(val symbol: Char) {
    PAWN('p'), KNIGHT('n'), BISHOP('b'), ROOK('r'), QUEEN('q'), KING('k');

    companion object {
        fun fromSymbol(c: Char): PieceType? = entries.firstOrNull { it.symbol == c.lowercaseChar() }
    }
}

/**
 * A piece on the board: type + owning color.
 * [symbol] is the FEN character (uppercase = white).
 */
data class Piece(val type: PieceType, val color: Color) {
    val symbol: Char
        get() = if (color == Color.WHITE) type.symbol.uppercaseChar() else type.symbol

    companion object {
        fun fromFenChar(c: Char): Piece? {
            val type = PieceType.fromSymbol(c) ?: return null
            val color = if (c.isUpperCase()) Color.WHITE else Color.BLACK
            return Piece(type, color)
        }
    }
}

/** Board square 0..63. Index 0 = a8, 63 = h1 (FEN order). */
@JvmInline
value class Square(val index: Int) {
    init {
        require(index in 0..63) { "Square index out of range: $index" }
    }

    val file: Int get() = index % 8          // 0 = a … 7 = h
    val rank: Int get() = index / 8          // 0 = rank8 … 7 = rank1
    val algebraic: String
        get() = "${'a' + file}${'8' - rank}"

    val isLight: Boolean get() = (file + rank) % 2 == 1

    companion object {
        fun fromAlgebraic(s: String): Square? {
            if (s.length != 2) return null
            val f = s[0] - 'a'
            val r = '8' - s[1]
            if (f !in 0..7 || r !in 0..7) return null
            return Square(r * 8 + f)
        }

        /** a8 = 0 … h1 = 63. */
        fun fromFileRank(file: Int, rank: Int): Square? {
            if (file !in 0..7 || rank !in 0..7) return null
            return Square(rank * 8 + file)
        }
    }
}

/** A move from [from] to [to]; promotion piece when a pawn reaches the last rank. */
data class Move(
    val from: Square,
    val to: Square,
    val promotion: PieceType? = null,
    val isEnPassant: Boolean = false,
    val isCastling: Boolean = false,
    /** Captured piece, filled by the rules engine when the move is applied. */
    val captured: Piece? = null,
) {
    /** UCI form, e.g. e2e4, e7e8q. */
    val uci: String
        get() = buildString {
            append(from.algebraic)
            append(to.algebraic)
            if (promotion != null) append(promotion.symbol)
        }

    companion object {
        fun fromUci(uci: String): Move? {
            if (uci.length < 4) return null
            val from = Square.fromAlgebraic(uci.substring(0, 2)) ?: return null
            val to = Square.fromAlgebraic(uci.substring(2, 4)) ?: return null
            val promo = if (uci.length >= 5) PieceType.fromSymbol(uci[4]) else null
            if (uci.length >= 5 && promo == null) return null
            return Move(from, to, promo)
        }
    }
}

/** Castling availability bit flags. */
object CastleRights {
    const val WHITE_KING = 1
    const val WHITE_QUEEN = 2
    const val BLACK_KING = 4
    const val BLACK_QUEEN = 8
    const val ALL = WHITE_KING or WHITE_QUEEN or BLACK_KING or BLACK_QUEEN
}
