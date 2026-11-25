package com.xrtcf.chess.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xrtcf.chess.analysis.ArrowColor
import com.xrtcf.chess.analysis.BoardArrow
import com.xrtcf.chess.core.Color as ChessColor
import com.xrtcf.chess.core.Move
import com.xrtcf.chess.core.Piece
import com.xrtcf.chess.core.PieceType
import com.xrtcf.chess.core.Position
import com.xrtcf.chess.core.Square
import kotlin.math.abs

/**
 * Interactive chessboard with:
 * tap-to-select / tap-to-move, drag-and-drop, legal-move dots,
 * capture indicators, selected/last-move/check highlights, colored arrows.
 *
 * All rendering is Compose Canvas; no bitmaps required (pieces drawn as glyphs).
 */
data class BoardUiState(
    val position: Position,
    val flipped: Boolean = false,
    val selected: Square? = null,
    val legalTargets: Set<Square> = emptySet(),
    val captureTargets: Set<Square> = emptySet(),
    val lastMove: Pair<Square, Square>? = null,
    val checkSquare: Square? = null,
    val arrows: List<BoardArrow> = emptyList(),
    val arrowVisible: Boolean = true,
    val interactive: Boolean = true,
    /** Drag ghost piece while dragging. */
    val dragSquare: Square? = null,
)

data class BoardCallbacks(
    val onSquareTap: (Square) -> Unit,
    val onMoveDropped: (from: Square, to: Square) -> Unit = { _, _ -> },
)

@Composable
fun ChessBoard(
    state: BoardUiState,
    callbacks: BoardCallbacks,
    modifier: Modifier = Modifier,
    showCoordinates: Boolean = true,
) {
    var dragFrom by remember { mutableStateOf<Square?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }

    val density = LocalDensity.current

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(state.position, state.flipped, state.interactive) {
                    if (!state.interactive) return@pointerInput
                    val cell = size.width / 8f
                    fun offsetToSquare(offset: Offset): Square? {
                        val file = (offset.x / cell).toInt().coerceIn(0, 7)
                        val rank = (offset.y / cell).toInt().coerceIn(0, 7)
                        val boardFile = if (state.flipped) 7 - file else file
                        val boardRank = if (state.flipped) 7 - rank else rank
                        return Square(boardRank * 8 + boardFile)
                    }
                    detectTapGestures(
                        onTap = { offset ->
                            offsetToSquare(offset)?.let { callbacks.onSquareTap(it) }
                        },
                    )
                    // Note: detectTapGestures consumes; drag via separate pointerInput
                }
                .pointerInput(state.position, state.flipped, state.interactive) {
                    if (!state.interactive) return@pointerInput
                    val cell = size.width / 8f
                    fun offsetToSquare(offset: Offset): Square? {
                        val file = (offset.x / cell).toInt().coerceIn(0, 7)
                        val rank = (offset.y / cell).toInt().coerceIn(0, 7)
                        val boardFile = if (state.flipped) 7 - file else file
                        val boardRank = if (state.flipped) 7 - rank else rank
                        return Square(boardRank * 8 + boardFile)
                    }
                    detectDragGestures(
                        onDragStart = { offset ->
                            val sq = offsetToSquare(offset)
                            if (sq != null && state.position.pieceAt(sq)?.color == state.position.turn) {
                                dragFrom = sq
                                dragging = true
                                dragPos = offset
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            dragPos = change.position
                        },
                        onDragEnd = {
                            if (dragging && dragFrom != null) {
                                val target = offsetToSquare(dragPos)
                                if (target != null && target != dragFrom) {
                                    callbacks.onMoveDropped(dragFrom!!, target)
                                } else {
                                    callbacks.onSquareTap(dragFrom!!)
                                }
                            }
                            dragging = false
                            dragFrom = null
                        },
                        onDragCancel = {
                            dragging = false
                            dragFrom = null
                        },
                    )
                },
        ) {
            val boardSize = size.minDimension
            val cell = boardSize / 8f

            fun toScreen(sq: Square): Pair<Int, Int> {
                val f = if (state.flipped) 7 - sq.file else sq.file
                val r = if (state.flipped) 7 - sq.rank else sq.rank
                return f to r
            }

            // Squares
            for (rank in 0..7) {
                for (file in 0..7) {
                    val light = (file + rank) % 2 == 1
                    val color = if (light) XrtcfColors.LightSquare else XrtcfColors.DarkSquare
                    drawRect(
                        color = color,
                        topLeft = Offset(file * cell, rank * cell),
                        size = Size(cell, cell),
                    )
                }
            }

            // Last move highlight
            state.lastMove?.let { (from, to) ->
                for (sq in listOf(from, to)) {
                    val (f, r) = toScreen(sq)
                    drawRect(
                        color = XrtcfColors.LastMove,
                        topLeft = Offset(f * cell, r * cell),
                        size = Size(cell, cell),
                    )
                }
            }

            // Selected highlight
            state.selected?.let { sq ->
                val (f, r) = toScreen(sq)
                drawRect(
                    color = XrtcfColors.Selected,
                    topLeft = Offset(f * cell, r * cell),
                    size = Size(cell, cell),
                )
            }

            // Check highlight
            state.checkSquare?.let { sq ->
                val (f, r) = toScreen(sq)
                drawCircle(
                    color = XrtcfColors.Check,
                    radius = cell * 0.45f,
                    center = Offset(f * cell + cell / 2, r * cell + cell / 2),
                )
            }

            // Pieces
            for (idx in 0..63) {
                val piece = state.position.board[idx] ?: continue
                if (dragging && dragFrom?.index == idx) continue
                val sq = Square(idx)
                val (f, r) = toScreen(sq)
                drawPiece(
                    piece = piece,
                    center = Offset(f * cell + cell / 2, r * cell + cell / 2),
                    radius = cell * 0.40f,
                )
            }

            // Legal move indicators
            for (sq in state.legalTargets) {
                val (f, r) = toScreen(sq)
                val center = Offset(f * cell + cell / 2, r * cell + cell / 2)
                if (sq in state.captureTargets) {
                    drawCircle(
                        color = XrtcfColors.Capture,
                        radius = cell * 0.42f,
                        center = center,
                        style = Stroke(width = cell * 0.08f),
                    )
                } else {
                    drawCircle(
                        color = XrtcfColors.LegalMove,
                        radius = cell * 0.14f,
                        center = center,
                    )
                }
            }

            // Arrows (below drag ghost)
            if (state.arrowVisible) {
                for (arrow in state.arrows) {
                    drawBoardArrow(arrow, cell, ::toScreen)
                }
            }

            // Drag ghost
            if (dragging && dragFrom != null) {
                val piece = state.position.pieceAt(dragFrom!!)
                if (piece != null) {
                    val (f, r) = toScreen(dragFrom!!)
                    // Ghost follows pointer — approximate at finger
                    val screenX = if (state.flipped) (7 - f) * cell + cell / 2 else f * cell + cell / 2
                    // Use raw drag position converted to board space:
                    val gx = dragPos.x
                    val gy = dragPos.y
                    drawPiece(piece, Offset(gx, gy), cell * 0.42f)
                }
            }

            // Coordinates
            if (showCoordinates) {
                for (i in 0..7) {
                    val fileChar = (('a' + i)).toString()
                    val rankChar = ('8' - i).toString()
                    val fileLabel = if (state.flipped) ('a' + (7 - i)) else ('a' + i)
                    val rankLabel = if (state.flipped) ('8' - (7 - i)) else ('8' - i)
                    // Draw as simple ticks using text is not available in pure DrawScope without
                    // text layout — coordinates drawn via BoardOverlay Text in ChessBoardWithCoords.
                }
            }
        }

        // Drag ghost piece as overlay for crisp rendering
        if (dragging && dragFrom != null) {
            val piece = state.position.pieceAt(dragFrom!!)
            if (piece != null) {
                val cellPx = with(density) { 0f } // placeholder; ghost drawn on canvas above
            }
        }
    }
}

private fun DrawScope.drawPiece(piece: Piece, center: Offset, radius: Float) {
    val fill = if (piece.color == ChessColor.WHITE) Color(0xFFF8F8F8) else Color(0xFF1B1B1B)
    val stroke = if (piece.color == ChessColor.WHITE) Color(0xFF222222) else Color(0xFFEEEEEE)
    when (piece.type) {
        PieceType.KING -> {
            drawCircle(fill, radius, center)
            drawCircle(stroke, radius, center, style = Stroke(radius * 0.12f))
            // cross
            val s = radius * 0.45f
            drawLine(stroke, Offset(center.x - s, center.y), Offset(center.x + s, center.y), radius * 0.14f)
            drawLine(stroke, Offset(center.x, center.y - s), Offset(center.x, center.y + s), radius * 0.14f)
        }
        PieceType.QUEEN -> {
            drawCircle(fill, radius, center)
            drawCircle(stroke, radius, center, style = Stroke(radius * 0.12f))
            // crown points
            for (k in -2..2) {
                val px = center.x + k * radius * 0.35f
                val py = center.y - radius * 0.55f - (if (abs(k) == 2) radius * 0.1f else 0f)
                drawCircle(stroke, radius * 0.12f, Offset(px, py))
            }
        }
        PieceType.ROOK -> {
            drawRect(fill, Offset(center.x - radius * 0.7f, center.y - radius * 0.6f), Size(radius * 1.4f, radius * 1.2f))
            drawRect(stroke, Offset(center.x - radius * 0.7f, center.y - radius * 0.6f), Size(radius * 1.4f, radius * 1.2f), style = Stroke(radius * 0.1f))
            // battlements
            for (k in 0..2) {
                val bx = center.x - radius * 0.55f + k * radius * 0.45f
                drawRect(fill, Offset(bx, center.y - radius * 0.85f), Size(radius * 0.28f, radius * 0.3f))
            }
        }
        PieceType.BISHOP -> {
            drawCircle(fill, radius * 0.9f, center)
            drawCircle(stroke, radius * 0.9f, center, style = Stroke(radius * 0.1f))
            // slit
            drawLine(stroke, Offset(center.x - radius * 0.3f, center.y - radius * 0.3f), Offset(center.x + radius * 0.15f, center.y + radius * 0.15f), radius * 0.1f)
            drawCircle(stroke, radius * 0.15f, Offset(center.x, center.y - radius * 0.55f))
        }
        PieceType.KNIGHT -> {
            drawCircle(fill, radius * 0.9f, center)
            drawCircle(stroke, radius * 0.9f, center, style = Stroke(radius * 0.1f))
            // horse-head suggestion: wedge
            val path = Path().apply {
                moveTo(center.x - radius * 0.4f, center.y + radius * 0.5f)
                lineTo(center.x - radius * 0.2f, center.y - radius * 0.5f)
                lineTo(center.x + radius * 0.45f, center.y - radius * 0.15f)
                lineTo(center.x + radius * 0.2f, center.y + radius * 0.5f)
                close()
            }
            drawPath(path, stroke)
        }
        PieceType.PAWN -> {
            drawCircle(fill, radius * 0.55f, Offset(center.x, center.y - radius * 0.15f))
            drawRect(fill, Offset(center.x - radius * 0.35f, center.y + radius * 0.25f), Size(radius * 0.7f, radius * 0.35f))
            drawCircle(stroke, radius * 0.55f, Offset(center.x, center.y - radius * 0.15f), style = Stroke(radius * 0.1f))
        }
    }
}

private fun DrawScope.drawBoardArrow(
    arrow: BoardArrow,
    cell: Float,
    toScreen: (Square) -> Pair<Int, Int>,
) {
    val from = Square.fromAlgebraic(arrow.from) ?: return
    val to = Square.fromAlgebraic(arrow.to) ?: return
    val (ff, fr) = toScreen(from)
    val (tf, tr) = toScreen(to)
    val start = Offset(ff * cell + cell / 2, fr * cell + cell / 2)
    val end = Offset(tf * cell + cell / 2, tr * cell + cell / 2)
    val color = when (arrow.color) {
        ArrowColor.GREEN -> XrtcfColors.LegalMove.copy(alpha = 0.85f)
        ArrowColor.BLUE -> Color(0xFF3B82F6).copy(alpha = 0.85f)
        ArrowColor.YELLOW -> Color(0xFFEAB308).copy(alpha = 0.85f)
        ArrowColor.ORANGE -> Color(0xFFF97316).copy(alpha = 0.85f)
        ArrowColor.RED -> Color(0xFFEF4444).copy(alpha = 0.85f)
    }
    val stroke = cell * 0.16f
    // Shorten end so head fits
    val dx = end.x - start.x
    val dy = end.y - start.y
    val len = kotlin.math.sqrt(dx * dx + dy * dy)
    if (len < 1f) return
    val ux = dx / len
    val uy = dy / len
    val headLen = cell * 0.35f
    val bodyEnd = Offset(end.x - ux * headLen, end.y - uy * headLen)

    // Arrow body — axis-aligned rect from start toward bodyEnd, rotated into place
    val angle = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
    val bodyLen = kotlin.math.max(0f, (bodyEnd - start).getDistance())
    withTransform(
        transformBlock = {
            rotate(degrees = angle, pivot = start)
        },
        drawBlock = {
            drawRect(
                color = color,
                topLeft = Offset(start.x, start.y - stroke / 2),
                size = Size(bodyLen, stroke),
            )
        },
    )
    // Arrow head
    val head = Path().apply {
        moveTo(end.x, end.y)
        lineTo(end.x - ux * headLen - uy * headLen * 0.55f, end.y - uy * headLen + ux * headLen * 0.55f)
        lineTo(end.x - ux * headLen + uy * headLen * 0.55f, end.y - uy * headLen - ux * headLen * 0.55f)
        close()
    }
    drawPath(head, color)
}

private operator fun Offset.minus(other: Offset): Offset = Offset(x - other.x, y - other.y)
private fun Offset.getDistance(): Float = kotlin.math.sqrt(x * x + y * y)
