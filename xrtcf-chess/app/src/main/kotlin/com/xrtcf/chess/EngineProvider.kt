package com.xrtcf.chess

import android.content.Context
import android.util.Log
import com.xrtcf.chess.engine.StockfishEngine
import java.io.File

/**
 * Extracts the bundled Stockfish binary from assets (or uses a path placed by
 * the build pipeline), starts a single shared engine process, and survives
 * configuration changes. Gracefully reports failure so the UI can show a real
 * error instead of faking evaluations.
 */
object EngineProvider {

    private const val TAG = "EngineProvider"
    private const val ASSET_NAME = "stockfish/stockfish"
    private const val BIN_DIR = "engine"
    private const val BIN_NAME = "stockfish"

    @Volatile private var instance: StockfishEngine? = null
    @Volatile private var startError: String? = null
    private val lock = Any()

    fun obtain(context: Context): StockfishEngine? {
        instance?.let { if (it.isRunning) return it }
        synchronized(lock) {
            instance?.let { if (it.isRunning) return it }
            return try {
                val bin = ensureBinary(context)
                val eng = StockfishEngine(listOf(bin.absolutePath))
                eng.start(defaultElo = defaultElo(context))
                instance = eng
                startError = null
                eng
            } catch (t: Throwable) {
                Log.e(TAG, "Engine start failed", t)
                startError = t.message ?: t.javaClass.simpleName
                null
            }
        }
    }

    fun lastError(): String? = startError

    fun release() {
        synchronized(lock) {
            instance?.close()
            instance = null
        }
    }

    private fun defaultElo(context: Context): Int {
        val prefs = context.getSharedPreferences("xrtcf_settings", Context.MODE_PRIVATE)
        return prefs.getInt("default_elo", 1500)
    }

    /**
     * Prefer an ABI-specific binary dropped into assets by the build script
     * (`scripts/fetch-stockfish.sh`), extract to filesDir, chmod +x.
     */
    private fun ensureBinary(context: Context): File {
        val outDir = File(context.filesDir, BIN_DIR)
        outDir.mkdirs()
        val out = File(outDir, BIN_NAME)

        // 1) Prefer pre-extracted binary from native library dir if packaging put it there
        val abis = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        for (abi in abis) {
            val lib = File(context.applicationInfo.nativeLibraryDir, "libstockfish.so")
            if (lib.exists() && lib.canExecute()) return lib
        }

        // 2) Extract from assets if present
        val assetPath = pickAsset(context, ASSET_NAME) ?: pickAsset(context, "stockfish")
        if (assetPath != null) {
            if (!out.exists() || out.length() == 0L) {
                context.assets.open(assetPath).use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                }
            }
            out.setExecutable(true, false)
            if (out.exists() && out.length() > 0) return out
        }

        // 3) Fall back to a system stockfish if the user installed one (rare)
        for (cand in listOf("/system/bin/stockfish", "/data/local/tmp/stockfish")) {
            val f = File(cand)
            if (f.exists()) return f
        }

        throw IllegalStateException(
            "Stockfish binary not found in assets ($ASSET_NAME). " +
                "Run scripts/fetch-stockfish.sh before building the APK.",
        )
    }

    private fun pickAsset(context: Context, prefix: String): String? {
        return try {
            val files = context.assets.list("") ?: return null
            when {
                files.contains(prefix) -> prefix
                files.contains("stockfish") && prefix.startsWith("stockfish") -> "stockfish"
                else -> {
                    // nested dir
                    val dirs = context.assets.list("stockfish") ?: return null
                    dirs.firstOrNull()?.let { "stockfish/$it" }
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}
