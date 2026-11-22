package com.imagenote.app.speech

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Starts recording the instant the user taps the mic and buffers everything, so the first words
 * aren't lost while Android's speech recogniser is still starting up. The buffered + live audio is
 * streamed to the recogniser through a pipe (RecognizerIntent.EXTRA_AUDIO_SOURCE, Android 13+).
 *
 * One instance per process, so transparent mode can start recording before the app is back on
 * screen and the app's recogniser picks up from there.
 */
object AudioPump {
    const val SAMPLE_RATE = 16_000
    private const val CHUNK = 640 // 20 ms of 16-bit mono
    private const val MAX_BUFFERED_CHUNKS = 50 * 60 // one minute

    private val queue = LinkedBlockingQueue<ByteArray>()
    @Volatile private var record: AudioRecord? = null
    @Volatile private var sink: OutputStream? = null
    @Volatile private var running = false
    @Volatile private var blockedSince = 0L
    @Volatile var level = 0f; private set
    @Volatile var bytesDelivered = 0L; private set
    @Volatile private var startedAt = 0L
    @Volatile private var claimed = false

    val isRunning get() = running

    /** Begin recording now. Safe to call twice. Returns false if the mic couldn't be opened. */
    @SuppressLint("MissingPermission")
    @Synchronized
    fun start(): Boolean {
        if (running) return true
        val min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, CHUNK * 10),
            )
        }.getOrNull() ?: return false
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return false }
        queue.clear()
        bytesDelivered = 0
        claimed = false
        startedAt = SystemClock.elapsedRealtime()
        record = rec
        running = true
        rec.startRecording()
        Thread({ readLoop(rec) }, "ImageNoteMic").start()
        Thread({ writeLoop() }, "ImageNoteMicPipe").start()
        return true
    }

    /** The recogniser session takes over; stops the "nobody came" safety timeout. */
    fun claim() { claimed = true }

    /** True if started by transparent mode but no recogniser picked it up in time. */
    fun abandoned(): Boolean = running && !claimed && SystemClock.elapsedRealtime() - startedAt > 8000

    /** A fresh pipe for the next recogniser session; buffered audio is delivered first. */
    fun newSource(): ParcelFileDescriptor? {
        if (!running) return null
        val pipe = runCatching { ParcelFileDescriptor.createPipe() }.getOrNull() ?: return null
        val old = sink
        sink = ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])
        runCatching { old?.close() }
        return pipe[0]
    }

    /** The recogniser isn't reading our audio (it ignored EXTRA_AUDIO_SOURCE). */
    fun isStalled(ms: Long): Boolean = blockedSince != 0L && SystemClock.elapsedRealtime() - blockedSince > ms

    @Synchronized
    fun stop() {
        if (!running) return
        running = false
        runCatching { record?.stop() }
        runCatching { record?.release() }
        record = null
        runCatching { sink?.close() }
        sink = null
        queue.clear()
        level = 0f
    }

    private fun readLoop(rec: AudioRecord) {
        while (running) {
            val buf = ByteArray(CHUNK)
            val n = rec.read(buf, 0, CHUNK)
            if (n <= 0) { if (n < 0) break else continue }
            level = levelOf(buf, n)
            if (queue.size < MAX_BUFFERED_CHUNKS) queue.offer(if (n == CHUNK) buf else buf.copyOf(n))
        }
    }

    private fun writeLoop() {
        var pendingChunk: ByteArray? = null
        while (running) {
            val out = sink
            if (out == null) { Thread.sleep(10); continue }
            val chunk = pendingChunk ?: queue.poll(50, TimeUnit.MILLISECONDS) ?: continue
            try {
                blockedSince = SystemClock.elapsedRealtime()
                out.write(chunk)
                blockedSince = 0
                bytesDelivered += chunk.size
                pendingChunk = null
            } catch (e: IOException) {
                // The recogniser closed this session's pipe; keep the chunk for the next one.
                blockedSince = 0
                pendingChunk = chunk
                if (sink === out) sink = null
            }
        }
        blockedSince = 0
    }

    private fun levelOf(buf: ByteArray, n: Int): Float {
        var sum = 0.0
        var i = 0
        while (i + 1 < n) {
            val s = ((buf[i + 1].toInt() shl 8) or (buf[i].toInt() and 0xFF)).toShort().toDouble()
            sum += s * s
            i += 2
        }
        val rms = sqrt(sum / (n / 2).coerceAtLeast(1))
        val db = 20 * log10(rms.coerceAtLeast(1.0))
        // ~30 dB (quiet room) .. ~75 dB (loud speech) → 0..1
        return ((db - 30) / 45).toFloat().coerceIn(0f, 1f)
    }
}
