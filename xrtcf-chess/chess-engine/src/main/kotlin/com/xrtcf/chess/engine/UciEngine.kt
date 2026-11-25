package com.xrtcf.chess.engine

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Thrown when the engine process dies or the protocol fails. */
class EngineException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Low-level UCI client: owns the Stockfish process, reads stdout on a
 * background thread, exposes synchronous helpers for tests and async
 * analysis entry points for the UI layer.
 *
 * Protocol notes:
 *  - All writes are synchronized on [writeLock].
 *  - [waitFor] matches a predicate over incoming lines; analysis uses
 *    callbacks via [search] to avoid blocking the main thread.
 */
class UciEngine(
    private val command: List<String>,
) : AutoCloseable {

    private var process: Process? = null
    private var stdin: BufferedWriter? = null
    private val writeLock = Any()
    private val outputQueue = ConcurrentLinkedQueue<String>()
    private val listeners = ConcurrentLinkedQueue<(String) -> Unit>()
    @Volatile private var running = false
    private var readerThread: Thread? = null

    val isRunning: Boolean get() = running

    fun start() {
        if (running) return
        val pb = ProcessBuilder(command)
            .redirectErrorStream(true)
        val proc = try {
            pb.start()
        } catch (e: Exception) {
            throw EngineException("Failed to start engine: ${command.joinToString(" ")}", e)
        }
        process = proc
        stdin = BufferedWriter(OutputStreamWriter(proc.outputStream))
        running = true

        readerThread = Thread({
            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            try {
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val l = line ?: break
                    outputQueue.add(l)
                    for (ls in listeners) {
                        try {
                            ls(l)
                        } catch (_: Exception) {
                            // listener errors must not kill the reader
                        }
                    }
                }
            } catch (_: Exception) {
                // stream closed
            } finally {
                running = false
            }
        }, "uci-reader").apply {
            isDaemon = true
            start()
        }
    }

    fun send(command: String) {
        val w = stdin ?: throw EngineException("Engine not started")
        synchronized(writeLock) {
            try {
                w.write(command)
                w.newLine()
                w.flush()
            } catch (e: Exception) {
                throw EngineException("Failed to send: $command", e)
            }
        }
    }

    /** Wait for a line matching [predicate] up to [timeoutMs]; returns the line or null. */
    fun waitFor(timeoutMs: Long, predicate: (String) -> Boolean): String? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val it = outputQueue.poll()
            if (it == null) {
                Thread.sleep(2)
            } else if (predicate(it)) {
                return it
            }
            // Non-matching lines are discarded for sync helpers
        }
        return null
    }

    /** Drain all currently queued lines. */
    fun drain(): List<String> {
        val out = mutableListOf<String>()
        while (true) {
            val l = outputQueue.poll() ?: break
            out.add(l)
        }
        return out
    }

    fun addListener(l: (String) -> Unit) { listeners.add(l) }
    fun removeListener(l: (String) -> Unit) { listeners.remove(l) }

    /** Handshake: `uci` → wait `uciok`. */
    fun handshake(timeoutMs: Long = 10_000): Map<String, String> {
        send("uci")
        val options = mutableMapOf<String, String>()
        val ok = waitFor(timeoutMs) { it == "uciok" }
            ?: throw EngineException("Engine did not send uciok within ${timeoutMs}ms")
        // Options were queued before uciok — they may already be drained by predicate loop.
        // Re-read via listener approach: send uci again is not valid; instead parse from
        // a fresh listener capture. Simpler: use collectUntil for options.
        return options
    }

    /**
     * Full handshake that captures option lines: sends `uci`, collects until `uciok`.
     * Returns option name → full definition string (type/default/min/max/vars).
     */
    fun handshakeWithOptions(timeoutMs: Long = 10_000): EngineInfoOptions {
        val latch = CountDownLatch(1)
        val optionLines = mutableListOf<String>()
        val idLines = mutableListOf<String>()
        val listener: (String) -> Unit = { line ->
            when {
                line.startsWith("option ") -> synchronized(optionLines) { optionLines.add(line) }
                line.startsWith("id ") -> synchronized(idLines) { idLines.add(line) }
                line == "uciok" -> latch.countDown()
            }
        }
        addListener(listener)
        try {
            send("uci")
            if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                throw EngineException("uciok timeout")
            }
        } finally {
            removeListener(listener)
        }
        drain() // clear anything else
        return EngineInfoOptions(idLines.toList(), optionLines.toList())
    }

    fun isready(timeoutMs: Long = 5_000) {
        send("isready")
        val ok = waitFor(timeoutMs) { it == "readyok" }
            ?: throw EngineException("readyok timeout")
    }

    fun setOption(name: String, value: String) {
        send("setoption name $name value $value")
    }

    fun uciNewGame() {
        send("ucinewgame")
        isready()
    }

    fun setFen(fen: String) {
        send("position fen $fen")
    }

    fun setPositionFromMoves(startFen: String?, moves: List<String>) {
        val sb = StringBuilder("position ")
        if (startFen != null) sb.append("fen ").append(startFen)
        else sb.append("startpos")
        if (moves.isNotEmpty()) {
            sb.append(" moves ")
            sb.append(moves.joinToString(" "))
        }
        send(sb.toString())
    }

    /**
     * Synchronous one-shot search — blocks calling thread (tests only).
     * UI must use [searchAsync].
     */
    fun goSync(
        depth: Int? = null,
        movetimeMs: Long? = null,
        nodes: Long? = null,
        multipv: Int = 1,
        timeoutMs: Long = 120_000,
    ): SearchOutcome {
        val infos = mutableListOf<EngineInfo>()
        var best: BestMove? = null
        val done = CountDownLatch(1)

        val listener: (String) -> Unit = { line ->
            when {
                line.startsWith("info ") -> parseInfoLine(line)?.let { synchronized(infos) { infos.add(it) } }
                line.startsWith("bestmove ") -> {
                    best = parseBestMove(line)
                    done.countDown()
                }
            }
        }
        addListener(listener)
        try {
            val go = buildString {
                append("go")
                if (depth != null) append(" depth ").append(depth)
                if (movetimeMs != null) append(" movetime ").append(movetimeMs)
                if (nodes != null) append(" nodes ").append(nodes)
            }
            send(go)
            if (!done.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                send("stop")
                // Wait briefly for bestmove after stop
                done.await(3, TimeUnit.SECONDS)
            }
        } finally {
            removeListener(listener)
        }
        val bm = best ?: throw EngineException("No bestmove received")
        return SearchOutcome(bm, synchronized(infos) { infos.toList() })
    }

    /**
     * Start an async search. Returns immediately; [onInfo] and [onBestMove]
     * are invoked on the reader thread. Call [stopSearch] to cancel.
     */
    fun searchAsync(
        depth: Int? = null,
        movetimeMs: Long? = null,
        multipv: Int = 1,
        onInfo: (EngineInfo) -> Unit,
        onBestMove: (BestMove) -> Unit,
    ): SearchHandle {
        val active = AtomicBoolean(true)
        lateinit var listener: (String) -> Unit
        listener = { line ->
            if (active.get()) {
                when {
                    line.startsWith("info ") -> parseInfoLine(line)?.let(onInfo)
                    line.startsWith("bestmove ") -> {
                        val bm = parseBestMove(line)
                        if (bm != null && active.get()) {
                            active.set(false)
                            removeListener(listener)
                            onBestMove(bm)
                        }
                    }
                }
            }
        }
        addListener(listener)
        val go = buildString {
            append("go")
            if (depth != null) append(" depth ").append(depth)
            if (movetimeMs != null) append(" movetime ").append(movetimeMs)
        }
        send(go)
        return SearchHandle(
            stop = {
                if (active.get()) send("stop")
            },
            dispose = {
                active.set(false)
                removeListener(listener)
            },
        )
    }

    fun stopSearch() {
        send("stop")
    }

    /** Apply an ELO configuration via UCI options. */
    fun applyElo(config: EloConfig) {
        setOption("Skill Level", config.skillLevel.toString())
        setOption("UCI_LimitStrength", if (config.limitStrength) "true" else "false")
        if (config.limitStrength) {
            setOption("UCI_Elo", config.uciElo.toString())
        }
        isready()
    }

    /** Configure analysis options. */
    fun configureAnalysis(depth: Int, multipv: Int) {
        setOption("MultiPV", multipv.toString())
        setOption("UCI_AnalyseMode", "true")
        isready()
    }

    override fun close() {
        if (!running && process == null) return
        try {
            send("quit")
        } catch (_: Exception) {
        }
        val p = process ?: return
        try {
            if (!p.waitFor(2, TimeUnit.SECONDS)) {
                p.destroy()
                if (!p.waitFor(1, TimeUnit.SECONDS)) p.destroyForcibly()
            }
        } catch (_: Exception) {
            p.destroyForcibly()
        }
        running = false
        process = null
        stdin = null
    }

    companion object {
        fun parseInfoLine(line: String): EngineInfo? {
            // info depth 12 seldepth 16 multipv 1 score cp 34 nodes 1000 nps 50000 time 20 pv e2e4 e7e5
            if (!line.startsWith("info ")) return null
            // Skip lines without score/pv that are just currmove progress when we care — still parse
            val tokens = line.split(' ')
            var depth: Int? = null
            var seldepth: Int? = null
            var multipv = 1
            var scoreCp: Int? = null
            var mateIn: Int? = null
            var nodes: Long? = null
            var nps: Long? = null
            var timeMs: Long? = null
            val pv = mutableListOf<String>()
            var currmove: String? = null
            var i = 1
            while (i < tokens.size) {
                when (tokens[i]) {
                    "depth" -> { depth = tokens.getOrNull(i + 1)?.toIntOrNull(); i += 2 }
                    "seldepth" -> { seldepth = tokens.getOrNull(i + 1)?.toIntOrNull(); i += 2 }
                    "multipv" -> { multipv = tokens.getOrNull(i + 1)?.toIntOrNull() ?: 1; i += 2 }
                    "score" -> {
                        val kind = tokens.getOrNull(i + 1)
                        val value = tokens.getOrNull(i + 2)?.toIntOrNull()
                        when (kind) {
                            "cp" -> scoreCp = value
                            "mate" -> mateIn = value
                        }
                        // skip bound lowerbound/upperbound
                        var skip = 3
                        if (tokens.getOrNull(i + 3) in listOf("lowerbound", "upperbound")) skip = 4
                        i += skip
                    }
                    "nodes" -> { nodes = tokens.getOrNull(i + 1)?.toLongOrNull(); i += 2 }
                    "nps" -> { nps = tokens.getOrNull(i + 1)?.toLongOrNull(); i += 2 }
                    "time" -> { timeMs = tokens.getOrNull(i + 1)?.toLongOrNull(); i += 2 }
                    "currmove" -> { currmove = tokens.getOrNull(i + 1); i += 2 }
                    "pv" -> {
                        i++
                        while (i < tokens.size && tokens[i].length >= 4 && tokens[i][0].isLetter()) {
                            pv.add(tokens[i]); i++
                        }
                    }
                    else -> i++
                }
            }
            // Only return lines that carry a score (final per depth/multipv)
            if (scoreCp == null && mateIn == null) return null
            return EngineInfo(
                depth = depth,
                seldepth = seldepth,
                multipv = multipv,
                scoreCp = scoreCp,
                mateIn = mateIn,
                nodes = nodes,
                nps = nps,
                timeMs = timeMs,
                pv = pv,
                currmove = currmove,
            )
        }

        fun parseBestMove(line: String): BestMove? {
            if (!line.startsWith("bestmove ")) return null
            val parts = line.split(' ')
            val move = parts.getOrNull(1) ?: return null
            val ponderIdx = parts.indexOf("ponder")
            val ponder = if (ponderIdx >= 0) parts.getOrNull(ponderIdx + 1) else null
            return BestMove(move, ponder)
        }
    }
}

data class EngineInfoOptions(
    val ids: List<String>,
    val optionLines: List<String>,
) {
    fun optionNames(): List<String> =
        optionLines.mapNotNull { line ->
            // option name Skill Level type spin default 20 min 0 max 20
            val idx = line.indexOf("name ")
            if (idx < 0) return@mapNotNull null
            val rest = line.substring(idx + 5)
            val typeIdx = rest.indexOf(" type ")
            if (typeIdx < 0) rest.trim() else rest.substring(0, typeIdx).trim()
        }

    fun hasOption(name: String): Boolean = optionNames().any { it.equals(name, ignoreCase = true) }

    fun optionRaw(name: String): String? =
        optionLines.firstOrNull { it.contains("name $name ") || it.endsWith("name $name") }
}

data class SearchOutcome(
    val bestMove: BestMove,
    val infos: List<EngineInfo>,
)

class SearchHandle(
    val stop: () -> Unit,
    val dispose: () -> Unit,
)
