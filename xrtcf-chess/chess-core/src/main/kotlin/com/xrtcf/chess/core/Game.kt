package com.xrtcf.chess.core

/** A single history entry: move + resulting position + SAN. */
data class MoveHistoryEntry(
    val move: Move,
    val positionBefore: Position,
    val positionAfter: Position,
    val san: String,
    val comment: String = "",
)

/**
 * Mutable game state with full move history, undo/redo, repetition tracking,
 * PGN import/export, and result detection.
 */
class Game(
    startPosition: Position = Position.start(),
) {
    var currentPosition: Position = startPosition
        private set

    val history = mutableListOf<MoveHistoryEntry>()
    private val redoStack = mutableListOf<MoveHistoryEntry>()
    private val positionKeys = mutableListOf<String>()

    var result: GameResult = GameResult.ONGOING
        private set

    var comments: MutableMap<Int, String> = mutableMapOf() // ply index -> comment

    init {
        positionKeys.add(Rules.repetitionKey(startPosition))
    }

    val turn: Color get() = currentPosition.turn
    val isOver: Boolean get() = result != GameResult.ONGOING

    fun legalMoves(): List<Move> = MoveGenerator.legalMoves(currentPosition)

    fun movesFrom(square: Square): List<Move> = legalMoves().filter { it.from == square }

    /** Play a move if legal; returns the new history entry, or null if illegal. */
    fun tryMove(move: Move): MoveHistoryEntry? {
        val legal = legalMoves().firstOrNull {
            it.from == move.from && it.to == move.to &&
                (it.promotion == move.promotion || (move.promotion == null && it.promotion == null))
        } ?: return null

        val before = currentPosition
        val after = Rules.applyMove(before, legal)
        val san = San.toSan(before, legal, after)

        redoStack.clear()
        val entry = MoveHistoryEntry(legal, before, after, san)
        history.add(entry)
        currentPosition = after
        positionKeys.add(Rules.repetitionKey(after))
        result = Rules.resultOf(after, positionKeys)
        return entry
    }

    /** Try a move given UCI string (e.g. "e2e4", "e7e8q"). */
    fun tryMoveUci(uci: String): MoveHistoryEntry? {
        val base = Move.fromUci(uci) ?: return null
        // Prefer exact promotion match; if absent, try queen promo when needed
        tryMove(base)?.let { return it }
        if (base.promotion == null) {
            for (promo in listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)) {
                tryMove(base.copy(promotion = promo))?.let { return it }
            }
        }
        return null
    }

    fun undo(): MoveHistoryEntry? {
        if (history.isEmpty()) return null
        val entry = history.removeAt(history.lastIndex)
        redoStack.add(entry)
        currentPosition = entry.positionBefore
        if (positionKeys.size > 1) positionKeys.removeAt(positionKeys.lastIndex)
        result = Rules.resultOf(currentPosition, positionKeys)
        return entry
    }

    fun redo(): MoveHistoryEntry? {
        if (redoStack.isEmpty()) return null
        val entry = redoStack.removeAt(redoStack.lastIndex)
        history.add(entry)
        currentPosition = entry.positionAfter
        positionKeys.add(Rules.repetitionKey(entry.positionAfter))
        result = Rules.resultOf(currentPosition, positionKeys)
        return entry
    }

    fun canUndo(): Boolean = history.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun reset(position: Position = Position.start()) {
        currentPosition = position
        history.clear()
        redoStack.clear()
        positionKeys.clear()
        positionKeys.add(Rules.repetitionKey(position))
        comments.clear()
        result = Rules.resultOf(position, positionKeys)
    }

    /** Jump to ply [n] (0 = start); n may go forward via redo. */
    fun goToPly(n: Int) {
        val maxPly = history.size + redoStack.size
        require(n in 0..maxPly) { "ply $n out of range 0..$maxPly" }
        while (history.size > n) undo()
        while (history.size < n) redo()
    }

    fun fen(): String = Fen.format(currentPosition)

    fun loadFen(fen: String) {
        reset(Fen.parse(fen))
    }

    fun toPgn(): String = Pgn.export(this)

    fun loadPgn(pgn: String): Pgn.ImportResult = Pgn.importInto(this, pgn)
}
