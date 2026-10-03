package com.dani.assistant.core.tts

import android.media.MediaPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** مشغّل mp3 مشترك لأصوات الشبكة (Edge / Azure). */
object TtsPlayer {
    private var player: MediaPlayer? = null

    fun stop() {
        try { player?.release() } catch (e: Exception) { }
        player = null
    }

    /** يرجع null إذا بدا التشغيل، وإلا نص الخطأ. */
    suspend fun play(file: File): String? = try {
        withContext(Dispatchers.Main) {
            stop()
            val mp = MediaPlayer()
            mp.setDataSource(file.absolutePath)
            mp.setOnPreparedListener { it.start() }
            mp.setOnCompletionListener { it.release(); if (player === it) player = null }
            mp.setOnErrorListener { m, _, _ -> try { m.release() } catch (e: Exception) { }; if (player === m) player = null; true }
            player = mp
            mp.prepareAsync()
        }
        null
    } catch (e: Exception) { e.message ?: e.javaClass.simpleName }
}
