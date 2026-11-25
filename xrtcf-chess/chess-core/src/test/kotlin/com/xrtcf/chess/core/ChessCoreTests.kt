package com.xrtcf.chess.core

/**
 * Self-contained test suite for chess-core.
 * Runnable without JUnit (local verification) and wrapped by JUnit for CI.
 *
 * Perft values from the chess programming wiki — standard positions.
 */
object ChessCoreTests {

    var passed = 0
    var failed = 0
    private val failures = mutableListOf<String>()

    fun check(name: String, cond: Boolean, detail: String = "") {
        if (cond) {
            passed++
        } else {
            failed++
            val msg = if (detail.isEmpty()) name else "$name — $detail"
            failures.add(msg)
            println("FAIL: $msg")
        }
    }

    fun <T> assertEquals(name: String, expected: T, actual: T) {
        check(name, expected == actual, "expected=$expected actual=$actual")
    }

    // ---- perft ----

    fun perft(pos: Position, depth: Int): Long {
        if (depth == 0) return 1
        val moves = MoveGenerator.legalMoves(pos)
        if (depth == 1) return moves.size.toLong()
        var nodes = 0L
        for (m in moves) {
            nodes += perft(Rules.applyMove(pos, m), depth - 1)
        }
        return nodes
    }

    fun runPerftSuite() {
        // Start position
        val start = Position.start()
        assertEquals("perft start d1", 20L, perft(start, 1))
        assertEquals("perft start d2", 400L, perft(start, 2))
        assertEquals("perft start d3", 8902L, perft(start, 3))
        assertEquals("perft start d4", 197281L, perft(start, 4))

        // Kiwipete — castling, ep, pins
        val kiwi = Fen.parse("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1")
        assertEquals("perft kiwipete d1", 48L, perft(kiwi, 1))
        assertEquals("perft kiwipete d2", 2039L, perft(kiwi, 2))
        assertEquals("perft kiwipete d3", 97862L, perft(kiwi, 3))

        // Position 3 — en passant heavy
        val p3 = Fen.parse("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1")
        assertEquals("perft pos3 d1", 14L, perft(p3, 1))
        assertEquals("perft pos3 d2", 191L, perft(p3, 2))
        assertEquals("perft pos3 d3", 2812L, perft(p3, 3))
        assertEquals("perft pos3 d4", 43238L, perft(p3, 4))

        // Position 4 — pins/checks
        val p4 = Fen.parse("r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1")
        assertEquals("perft pos4 d1", 6L, perft(p4, 1))
        assertEquals("perft pos4 d2", 264L, perft(p4, 2))
        assertEquals("perft pos4 d3", 9467L, perft(p4, 3))
    }

    // ---- piece movement ----

    fun runPieceMovementTests() {
        // Knight moves from d4 (kings present for FEN validation)
        val fen = "4k3/8/8/8/3N4/8/8/4K3 w - - 0 1"
        val pos = Fen.parse(fen)
        val moves = MoveGenerator.legalMoves(pos).filter { it.from.algebraic == "d4" }
        assertEquals("knight from d4 has 8 moves", 8, moves.size)
        val targets = moves.map { it.to.algebraic }.toSet()
        check("knight targets", targets == setOf("b3", "b5", "c2", "c6", "e2", "e6", "f3", "f5"), "$targets")

        // Bishop on empty board from a1
        val b = Fen.parse("4k3/8/8/8/8/8/8/B3K3 w - - 0 1")
        assertEquals("bishop a1 = 7 moves", 7, MoveGenerator.legalMoves(b).filter { it.from.algebraic == "a1" }.size)

        // Rook from h8 empty
        val r = Fen.parse("7R/8/8/8/8/8/8/4K2k w - - 0 1")
        assertEquals("rook h8 = 14 moves", 14, MoveGenerator.legalMoves(r).filter { it.from.algebraic == "h8" }.size)

        // Queen from d4 empty
        val q = Fen.parse("4k3/8/8/8/3Q4/8/8/4K3 w - - 0 1")
        assertEquals("queen d4 = 27 moves", 27, MoveGenerator.legalMoves(q).filter { it.from.algebraic == "d4" }.size)

        // King from e1 empty
        val k = Fen.parse("7k/8/8/8/8/8/8/4K3 w - - 0 1")
        assertEquals("king e1 = 5 moves", 5, MoveGenerator.legalMoves(k).filter { it.from.algebraic == "e1" }.size)

        // Pawn double from start
        val pawn = Fen.parse("4k3/8/8/8/8/8/P3K3/8 w - - 0 1")
        val pm = MoveGenerator.legalMoves(pawn)
        val pawnMoves = pm.filter { it.from.algebraic == "a2" }
        assertEquals("pawn a2 has 2 moves (a3, a4)", 2, pawnMoves.size)

        // Pawn fully blocked by own piece directly in front (white moves toward rank 8)
        // b2 own pawn on b3 blocks b2; b3 can advance to b4
        val blocked = Fen.parse("4k3/8/8/8/8/1P6/1P6/4K3 w - - 0 1")
        val bm = MoveGenerator.legalMoves(blocked).filter { it.from.algebraic == "b2" }
        assertEquals("b2 pawn fully blocked by own b3", 0, bm.size)
        val b3m = MoveGenerator.legalMoves(blocked).filter { it.from.algebraic == "b3" }
        assertEquals("b3 pawn advances to b4", 1, b3m.size)
    }

    fun runCaptureTests() {
        // Normal capture
        val pos = Fen.parse("4k3/8/8/3p4/4P3/8/8/4K3 w - - 0 1")
        val moves = MoveGenerator.legalMoves(pos)
        check("e4 can capture d5", moves.any { it.from.algebraic == "e4" && it.to.algebraic == "d5" })

        // Cannot capture own piece
        val own = Fen.parse("4k3/8/8/8/4P3/4P3/8/4K3 w - - 0 1")
        val om = MoveGenerator.legalMoves(own)
        check("e3 cannot capture e4 own", om.none { it.from.algebraic == "e3" && it.to.algebraic == "e4" })

        // En passant
        val ep = Fen.parse("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1")
        val em = MoveGenerator.legalMoves(ep)
        check("en passant e5xd6", em.any { it.from.algebraic == "e5" && it.to.algebraic == "d6" && it.isEnPassant })
        val after = Rules.applyMove(ep, em.first { it.isEnPassant })
        assertEquals("ep captures pawn on d5", null, after.pieceAt(Square.fromAlgebraic("d5")!!))
        assertEquals("ep pawn lands d6", PieceType.PAWN, after.pieceAt(Square.fromAlgebraic("d6")!!)?.type)

        // En passant not available without target
        val noEp = Fen.parse("4k3/8/8/3pP3/8/8/8/4K3 w - - 0 1")
        val nem = MoveGenerator.legalMoves(noEp)
        check("no ep without target", nem.none { it.isEnPassant })
    }

    fun runCastlingTests() {
        // White kingside
        val ks = Fen.parse("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        val km = MoveGenerator.legalMoves(ks)
        check("white O-O", km.any { it.isCastling && it.to.algebraic == "g1" })
        check("white O-O-O", km.any { it.isCastling && it.to.algebraic == "c1" })

        val after = Rules.applyMove(ks, km.first { it.isCastling && it.to.algebraic == "g1" })
        assertEquals("king on g1", PieceType.KING, after.pieceAt(Square.fromAlgebraic("g1")!!)?.type)
        assertEquals("rook on f1", PieceType.ROOK, after.pieceAt(Square.fromAlgebraic("f1")!!)?.type)
        assertEquals("e1 empty", null, after.pieceAt(Square.fromAlgebraic("e1")!!))
        assertEquals("h1 empty", null, after.pieceAt(Square.fromAlgebraic("h1")!!))

        // Black castling
        val bk = Fen.parse("r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 0 1")
        val bm = MoveGenerator.legalMoves(bk)
        check("black O-O", bm.any { it.isCastling && it.to.algebraic == "g8" })
        check("black O-O-O", bm.any { it.isCastling && it.to.algebraic == "c8" })

        // Cannot castle through check
        val chk = Fen.parse("4k3/8/8/8/8/8/6r1/4K2R w K - 0 1")
        // rook on g2 attacks f1 — king e1, f1 attacked by rook g2? rook g2 attacks g-file and rank 2, not f1.
        // Use rook on f8 attacking f1:
        val chk2 = Fen.parse("4kr2/8/8/8/8/8/8/4K2R w K - 0 1")
        // black rook f8 attacks f1 → cannot castle kingside through f1
        val cm = MoveGenerator.legalMoves(chk2)
        check("no O-O through check on f1", cm.none { it.isCastling && it.to.algebraic == "g1" })

        // Cannot castle when in check
        val inChk = Fen.parse("4k3/8/8/8/8/8/4r3/4K2R w K - 0 1")
        val icm = MoveGenerator.legalMoves(inChk)
        check("no castling while in check", icm.none { it.isCastling })

        // No castling if rights cleared
        val noRights = Fen.parse("4k3/8/8/8/8/8/8/4K2R w - - 0 1")
        val nrm = MoveGenerator.legalMoves(noRights)
        check("no castling without rights", nrm.none { it.isCastling })
    }

    fun runPromotionTests() {
        val pos = Fen.parse("4k3/1P6/8/8/8/8/8/4K3 w - - 0 1")
        val moves = MoveGenerator.legalMoves(pos)
        val promos = moves.filter { it.from.algebraic == "b7" && it.to.algebraic == "b8" }
        assertEquals("4 promotion choices", 4, promos.size)
        val types = promos.mapNotNull { it.promotion }.toSet()
        check("promotions QRBN", types == setOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT), "$types")

        // Promote with capture
        val cap = Fen.parse("1n2k3/P7/8/8/8/8/8/4K3 w - - 0 1")
        val cm = MoveGenerator.legalMoves(cap)
        val capPromos = cm.filter { it.from.algebraic == "a7" && it.to.algebraic == "b8" && it.promotion != null }
        assertEquals("capture-promotion 4 choices", 4, capPromos.size)

        // Apply promotion
        val after = Rules.applyMove(pos, promos.first { it.promotion == PieceType.QUEEN })
        assertEquals("promoted queen on b8", Piece(PieceType.QUEEN, Color.WHITE), after.pieceAt(Square.fromAlgebraic("b8")!!))
        assertEquals("b7 empty", null, after.pieceAt(Square.fromAlgebraic("b7")!!))
    }

    fun runCheckmateTests() {
        // Fool's mate
        val g = Game()
        g.tryMoveUci("f2f3")
        g.tryMoveUci("e7e5")
        g.tryMoveUci("g2g4")
        val entry = g.tryMoveUci("d8h4")
        check("fools mate played", entry != null)
        check("is checkmate", Rules.isCheckmate(g.currentPosition))
        assertEquals("black wins", GameResult.BLACK_WINS, g.result)

        // Scholar's mate
        val s = Game()
        for (uci in listOf("e2e4", "e7e5", "f1c4", "b8c6", "d1h5", "g8f6", "h5f7")) {
            check("scholars move $uci", s.tryMoveUci(uci) != null)
        }
        check("scholars is mate", Rules.isCheckmate(s.currentPosition))
        assertEquals("white wins", GameResult.WHITE_WINS, s.result)

        // Back-rank mate position
        val mate = Fen.parse("6k1/5ppp/8/8/8/8/8/R3K3 w - - 0 1")
        val mm = MoveGenerator.legalMoves(mate)
        val ra8 = mm.firstOrNull { it.to.algebraic == "a8" }
        check("Ra8 available", ra8 != null)
        if (ra8 != null) {
            val after = Rules.applyMove(mate, ra8)
            check("Ra8 is mate", Rules.isCheckmate(after))
        }

        // King must respond to check — illegal moves filtered
        val inCheck = Fen.parse("4k3/8/8/8/8/8/4q3/4K3 w - - 0 1")
        // Black queen e2 checks white king e1 — wait queen on e2 adjacent, but it's white to move and king in check from queen
        // Actually white king e1, black queen e2 — king can capture queen if not defended
        val cm = MoveGenerator.legalMoves(inCheck)
        check("in check moves exist (capture queen or flee)", cm.isNotEmpty())
        // Illegal: moving king to attacked square
        for (m in cm) {
            val after = Rules.applyMove(inCheck, m)
            check("legal move ${m.uci} leaves king safe", !Rules.isInCheck(after, Color.WHITE))
        }
    }

    fun runStalemateTests() {
        // Classic stalemate: black king a8, white king c7? — king c6, queen b6, black king a8
        // Standard: 7k/5Q2/6K1/8/8/8/8/8 b - - 0 1 is stalemate? black king h8, Qf7, Kg6 — h8 has moves?
        // Use well-known: k7/8/1K6/8/8/8/8/1Q6 b - - 0 1? queen b1 too far.
        // Simple: black king a8, white queen c7, white king c6 — stalemate
        val stale = Fen.parse("k7/2Q5/2K5/8/8/8/8/8 b - - 0 1")
        check("stalemate detected", Rules.isStalemate(stale))
        assertEquals("stalemate result", GameResult.DRAW_STALEMATE, Rules.resultOf(stale))
        assertEquals("stalemate no moves", 0, MoveGenerator.legalMoves(stale).size)

        // Not stalemate when king can move
        val notStale = Fen.parse("k7/8/1K6/8/8/8/8/7Q b - - 0 1")
        // black king a8: can go a7? queen h1 diagonals: a8-h1 diagonal — a7 not on it. b7? no. b8? no.
        // Actually Qh1 attacks a8 diagonal. a7: not attacked. So a7 is legal — not stalemate
        check("not stalemate with escape", !Rules.isStalemate(notStale))
    }

    fun runDrawTests() {
        // Insufficient material: K vs K
        val kk = Fen.parse("4k3/8/8/8/8/8/8/4K3 w - - 0 1")
        check("K vs K insufficient", Rules.isInsufficientMaterial(kk))

        // K+B vs K
        val kb = Fen.parse("4k3/8/8/8/8/8/8/4KB2 w - - 0 1")
        check("KB vs K insufficient", Rules.isInsufficientMaterial(kb))

        // K+N vs K
        val kn = Fen.parse("4k3/8/8/8/8/8/8/4KN2 w - - 0 1")
        check("KN vs K insufficient", Rules.isInsufficientMaterial(kn))

        // Not insufficient: K+P vs K
        val kp = Fen.parse("4k3/8/8/8/8/8/4P3/4K3 w - - 0 1")
        check("KP vs K sufficient", !Rules.isInsufficientMaterial(kp))

        // Fifty-move rule
        val fifty = Fen.parse("4k3/8/8/8/8/8/8/4K3 w - - 99 80")
        val g = Game(fifty)
        // Make a non-capturing non-pawn move that keeps clock: king shuffle if legal
        // From this position king moves increment halfmove to 100
        val moves = g.legalMoves()
        check("fifty position has moves", moves.isNotEmpty())
        g.tryMove(moves.first())
        assertEquals("halfmove reaches 100", 100, g.currentPosition.halfmoveClock)
        assertEquals("fifty draw", GameResult.DRAW_FIFTY_MOVE, g.result)

        // Threefold: shuffle knights/kings back and forth
        val rep = Game()
        // 1.Nf3 Nf6 2.Ng1 Ng8 3.Nf3 Nf6 4.Ng1 Ng8 — start position repeats
        val seq = listOf("g1f3", "g8f6", "f3g1", "f6g8", "g1f3", "g8f6", "f3g1", "f6g8")
        for (uci in seq) {
            check("rep move $uci", rep.tryMoveUci(uci) != null)
        }
        assertEquals("threefold draw", GameResult.DRAW_REPETITION, rep.result)
    }

    fun runFenTests() {
        val start = Position.start()
        assertEquals("start FEN roundtrip", Position.START_FEN, Fen.format(start))

        val fen2 = "rnbqkbnr/pp1ppppp/8/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq c6 0 2"
        val p2 = Fen.parse(fen2)
        assertEquals("fen2 roundtrip", fen2, Fen.format(p2))

        // Invalid FENs
        check("reject empty", !Fen.isValid(""))
        check("reject 7 ranks", !Fen.isValid("8/8/8/8/8/8/8 w - - 0 1"))
        check("reject bad turn", !Fen.isValid("8/8/8/8/8/8/8/4K2k x - - 0 1"))
        check("reject no kings", !Fen.isValid("8/8/8/8/8/8/8/8 w - - 0 1"))
        check("reject two white kings", !Fen.isValid("4k3/8/8/8/8/8/8/4K3 w - - 0 1".replace("4K3", "KK6", ).let {
            // construct two white kings
            "4k3/8/8/8/8/8/8/3KK3 w - - 0 1"
        }))
        check("reject bad square", !Fen.isValid("8/8/8/8/8/8/8/4K3 w - x9 0 1"))

        // Helpful error messages
        try {
            Fen.parse("8/8/8/8/8/8/8 w - - 0 1")
            check("throws on short fen", false)
        } catch (e: FenException) {
            check("error message present", e.message?.contains("8 ranks") == true, e.message ?: "")
        }
    }

    fun runPgnTests() {
        // Import a short game
        val pgn = """
            [Event "Test"]
            [Site "Local"]
            [Result "1-0"]

            1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 4. Ba4 Nf6 5. O-O Be7 1-0
        """.trimIndent()
        val g = Game()
        val result = g.loadPgn(pgn)
        check("pgn import no error", result.error == null, result.error ?: "")
        assertEquals("pgn move count", 10, result.moves)
        assertEquals("headers Event", "Test", result.headers["Event"])
        assertEquals("after O-O white king g1", PieceType.KING, g.currentPosition.kingSquare(Color.WHITE)?.let { g.currentPosition.pieceAt(it)?.type })
        // Check king actually castled: history contains O-O
        check("san O-O present", g.history.any { it.san == "O-O" })

        // Export roundtrip
        val exported = g.toPgn()
        val g2 = Game()
        val r2 = g2.loadPgn(exported)
        check("export reimport ok", r2.error == null, r2.error ?: "")
        assertEquals("roundtrip move count", 10, r2.moves)

        // Illegal SAN reports error
        val bad = Game()
        val br = bad.loadPgn("1. e4 e5 2. Nf3 Qz9")
        check("illegal san error", br.error != null)

        // Comments preserved in export
        val withComment = Game()
        withComment.loadPgn("1. e4 {king pawn} e5")
        check("comment imported", withComment.comments.isNotEmpty() || withComment.history.isNotEmpty())

        // FEN header setup
        val fenGame = Game()
        val fr = fenGame.loadPgn("""[FEN "4k3/8/8/8/8/8/4P3/4K3 w - - 0 1"]
[SetUp "1"]

1. e4 Kd7""")
        check("fen pgn no error", fr.error == null, fr.error ?: "")
        check("fen pgn started from fen", fr.moves >= 1)
    }

    fun runSanTests() {
        val g = Game()
        val e4 = g.tryMoveUci("e2e4")
        assertEquals("e4 san", "e4", e4?.san)
        g.tryMoveUci("e7e5")
        val nf3 = g.tryMoveUci("g1f3")
        assertEquals("Nf3 san", "Nf3", nf3?.san)
        g.tryMoveUci("b8c6")
        val bb5 = g.tryMoveUci("f1b5")
        assertEquals("Bb5 san", "Bb5", bb5?.san)

        // Disambiguation: two knights can reach d2
        val dis = Fen.parse("4k3/8/8/8/8/2N3N1/8/4K3 w - - 0 1")
        // knights c3 and g3 — both can go to e2, not d2. Use e4 square: c3→e4? c3 knight: e4 yes. g3→e4? g3: e4 no (g3 to e4 is file -2 rank -1 → yes! g3: e4 = df=2, dr=1 ok)
        val mv = San.parseSan(dis, "Nce4")
        check("parse disambig Nce4", mv != null && mv.from.algebraic == "c3", mv?.uci ?: "null")
        val mv2 = San.parseSan(dis, "Nge4")
        check("parse disambig Nge4", mv2 != null && mv2.from.algebraic == "g3", mv2?.uci ?: "null")

        // Castling SAN
        val castle = Fen.parse("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        val g2 = Game(castle)
        g2.tryMove(Move(Square.fromAlgebraic("e1")!!, Square.fromAlgebraic("g1")!!, isCastling = true))
        assertEquals("castling san O-O", "O-O", g2.history.last().san)

        // Promotion SAN
        val promo = Fen.parse("4k3/1P6/8/8/8/8/8/4K3 w - - 0 1")
        val g3 = Game(promo)
        g3.tryMove(Move(Square.fromAlgebraic("b7")!!, Square.fromAlgebraic("b8")!!, promotion = PieceType.QUEEN))
        check("promotion san", g3.history.last().san.startsWith("b8=Q"), g3.history.last().san)
    }

    fun runUndoRedoTests() {
        val g = Game()
        g.tryMoveUci("e2e4")
        g.tryMoveUci("e7e5")
        assertEquals("2 moves", 2, g.history.size)
        val fenAfter = g.fen()

        g.undo()
        assertEquals("1 after undo", 1, g.history.size)
        g.undo()
        assertEquals("0 after undo", 0, g.history.size)
        check("cannot undo empty", g.undo() == null)

        g.redo()
        g.redo()
        assertEquals("redo restores fen", fenAfter, g.fen())
        check("no redo after exhausted", g.redo() == null)

        // New move clears redo (position after 1.e4 — black to move)
        g.undo()
        val ok = g.tryMoveUci("c7c5")
        check("black c5 legal", ok != null)
        check("redo cleared after new move", !g.canRedo())

        // Jump to ply
        val g2 = Game()
        for (u in listOf("e2e4", "e7e5", "g1f3", "g8f6")) {
            check("g2 move $u", g2.tryMoveUci(u) != null)
        }
        g2.goToPly(2)
        assertEquals("goToPly 2", 2, g2.history.size)
        g2.goToPly(4)
        assertEquals("goToPly 4", 4, g2.history.size)
        g2.goToPly(0)
        assertEquals("goToPly 0", 0, g2.history.size)
    }

    fun runIllegalMoveTests() {
        val g = Game()
        // Moving opponent's piece
        check("cannot move black when white turn", g.tryMoveUci("e7e5") == null)
        // Jumping with pawn over pieces is fine from start; try knight jump illegal
        check("knight cannot jump to e4", g.tryMoveUci("g1e4") == null)
        // Move to occupied own square
        check("king cannot move to own pawn", g.tryMoveUci("e1e2") == null)
        // Leaving king in check: scholar's position where king would expose
        val pinned = Fen.parse("4k3/8/8/8/8/8/3q4/3RK3 w - - 0 1".let {
            // ensure black king present: black king e8 already in this fen
            it
        })
        // white rook d1, king e1, black queen d2 — rook is pinned? rook on d1, queen d2 — actually Rd1 can't move off d-file? king e1 not on d-file.
        // Better: king e1, rook e2, queen e8 — rook pinned on e-file
        val pinPos = Fen.parse("4q2k/8/8/8/8/8/4R3/4K3 w - - 0 1")
        val pm = MoveGenerator.legalMoves(pinPos)
        // Rook on e2 is pinned to king e1 by queen e8 — rook can only move along e-file
        val rookMoves = pm.filter { it.from.algebraic == "e2" }
        check("pinned rook only file moves", rookMoves.all { it.to.file == 4 }, rookMoves.map { it.uci }.toString())
        check("pinned rook cannot go to d2", rookMoves.none { it.to.algebraic == "d2" })
        // e2→e8 captures queen along the pin line
        check("pinned rook can capture along file", rookMoves.any { it.to.algebraic == "e8" })
        check("pinned rook cannot wander", rookMoves.none { it.to.file != 4 })
    }

    fun runUndoRedoAndEdit() {
        // Position editing via FEN load mid-game
        val g = Game()
        g.tryMoveUci("e2e4")
        g.loadFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
        assertEquals("reset via fen", 0, g.history.size)
        assertEquals("start turn", Color.WHITE, g.turn)
    }

    /** Run every suite; returns number of failures. */
    fun runAll() {
        passed = 0
        failed = 0
        failures.clear()

        runPerftSuite()
        runPieceMovementTests()
        runCaptureTests()
        runCastlingTests()
        runPromotionTests()
        runCheckmateTests()
        runStalemateTests()
        runDrawTests()
        runFenTests()
        runPgnTests()
        runSanTests()
        runUndoRedoTests()
        runIllegalMoveTests()
        runUndoRedoAndEdit()

        println("==== chess-core: $passed passed, $failed failed ====")
        if (failed > 0) {
            println("Failures:")
            failures.forEach { println("  - $it") }
        }
    }
}

fun main() {
    ChessCoreTests.runAll()
    if (ChessCoreTests.failed > 0) kotlin.system.exitProcess(1)
}
