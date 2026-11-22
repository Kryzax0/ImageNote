package com.imagenote.app.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.imagenote.app.data.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface VoiceState {
    data object Idle : VoiceState
    data class Listening(
        /** Everything recognised so far, including the in-progress partial phrase. */
        val text: String,
        /** 0..1 microphone level for the animated button. */
        val level: Float,
        val heardSpeech: Boolean,
    ) : VoiceState
    data class Error(val message: String) : VoiceState
}

/**
 * Continuous speech capture that ends after the user has been quiet for [AppSettings.silenceMillis].
 *
 * Android recognisers normally end a session by themselves after a short pause (often < 1 s), so
 * this class transparently restarts the recogniser and stitches the phrases together until the
 * real silence window has elapsed. Must be used from the main thread.
 */
class VoiceCapture(private val context: Context, private val scope: CoroutineScope) {

    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private var recognizer: SpeechRecognizer? = null
    private var usingOnDevice = false
    private var settings = AppSettings()
    private var onFinished: ((String) -> Unit)? = null

    private val committed = StringBuilder()
    private var partial = ""
    private var level = 0f
    private var heardSpeech = false
    private var startedAt = 0L
    private var lastSpeechAt = 0L
    private var active = false
    private var watchdog: Job? = null
    private var busyRetries = 0

    // Streaming our own buffered mic audio to the recogniser (Android 13+), so recording
    // effectively starts the moment the button is tapped.
    private val prefs = context.getSharedPreferences("voice", Context.MODE_PRIVATE)
    private var pipeMode = false
    private var pipeConfirmed = false
    private var source: android.os.ParcelFileDescriptor? = null

    val isListening get() = active

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context) ||
        (Build.VERSION.SDK_INT >= 33 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context))

    fun start(settings: AppSettings, onFinished: (String) -> Unit) {
        if (active) return
        this.settings = settings
        this.onFinished = onFinished
        committed.clear()
        partial = ""
        level = 0f
        heardSpeech = false
        busyRetries = 0
        startedAt = SystemClock.elapsedRealtime()
        lastSpeechAt = startedAt
        active = true
        publish()
        if (!isAvailable()) {
            AudioPump.stop()
            fail("Speech recognition isn't available. Install or enable Google speech services.")
            return
        }
        // Start capturing audio immediately (it may already be running if transparent mode
        // started it when the floating mic was tapped).
        pipeConfirmed = false
        pipeMode = Build.VERSION.SDK_INT >= 33 && prefs.getInt("pipeFailures", 0) < 2 && AudioPump.start()
        if (pipeMode) AudioPump.claim() else AudioPump.stop()
        ensureRecognizer(settings.preferOnDevice)
        listen()
        watchdog = scope.launch {
            while (isActive && active) {
                delay(100)
                val now = SystemClock.elapsedRealtime()
                if (pipeMode) {
                    level = AudioPump.level
                    publish()
                    if (!pipeConfirmed) {
                        if (AudioPump.bytesDelivered > 96_000) {
                            pipeConfirmed = true
                            prefs.edit().putInt("pipeFailures", 0).apply()
                        } else if (AudioPump.isStalled(3000)) abandonPipe(restart = false)
                    }
                }
                if (heardSpeech && now - lastSpeechAt >= settings.silenceMillis) {
                    finish()
                } else if (!heardSpeech && now - startedAt >= maxOf(8000L, settings.silenceMillis + 6000L)) {
                    // Nobody said anything.
                    finish()
                }
            }
        }
    }

    /** Finish now and deliver whatever has been heard. */
    fun stop() {
        if (active) finish()
    }

    /** Abort without delivering text. */
    fun cancel() {
        onFinished = null
        if (active) finish()
        _state.value = VoiceState.Idle
    }

    fun release() {
        cancel()
        recognizer?.destroy()
        recognizer = null
    }

    fun clearError() {
        if (_state.value is VoiceState.Error) _state.value = VoiceState.Idle
    }

    /** Create the recogniser ahead of time so the first tap starts faster. */
    fun prepare(settings: AppSettings) {
        if (!active) runCatching { ensureRecognizer(settings.preferOnDevice) }
    }

    private fun ensureRecognizer(preferOnDevice: Boolean) {
        val wantOnDevice = preferOnDevice && Build.VERSION.SDK_INT >= 33 &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        if (recognizer == null || wantOnDevice != usingOnDevice) createRecognizer(preferOnDevice)
    }

    /**
     * The recogniser ignored or rejected our audio stream: stop our own recording so it can use
     * the microphone directly. After two failures streaming isn't tried again on this phone.
     */
    private fun abandonPipe(restart: Boolean) {
        if (!pipeMode) return
        pipeMode = false
        prefs.edit().putInt("pipeFailures", prefs.getInt("pipeFailures", 0) + 1).apply()
        AudioPump.stop()
        closeSource()
        if (restart) {
            runCatching { recognizer?.cancel() }
            restartSoon(50)
        }
    }

    private fun closeSource() {
        runCatching { source?.close() }
        source = null
    }

    private fun createRecognizer(preferOnDevice: Boolean) {
        recognizer?.destroy()
        usingOnDevice = preferOnDevice && Build.VERSION.SDK_INT >= 33 &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        recognizer = if (usingOnDevice && Build.VERSION.SDK_INT >= 33) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }.also { it.setRecognitionListener(listener) }
    }

    private fun recognizerIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, settings.silenceMillis)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, settings.silenceMillis)
        if (settings.language.isNotBlank()) {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, settings.language)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, settings.language)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY)
        }
        if (usingOnDevice) putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        if (pipeMode && Build.VERSION.SDK_INT >= 33) {
            val src = AudioPump.newSource()
            if (src == null) {
                pipeMode = false
                AudioPump.stop()
            } else {
                closeSource()
                source = src
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, src)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, android.media.AudioFormat.ENCODING_PCM_16BIT)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, AudioPump.SAMPLE_RATE)
            }
        }
    }

    private fun listen() {
        if (!active) return
        runCatching { recognizer?.startListening(recognizerIntent()) }
            .onFailure { fail("Couldn't start the microphone: ${it.message}") }
    }

    private fun restartSoon(delayMs: Long = 0) {
        scope.launch {
            if (delayMs > 0) delay(delayMs)
            if (active) listen()
        }
    }

    private fun currentText(): String = buildString {
        append(committed)
        if (partial.isNotBlank()) {
            if (isNotEmpty()) append(' ')
            append(partial.trim())
        }
    }

    private fun publish() {
        if (active) _state.value = VoiceState.Listening(currentText(), level, heardSpeech)
    }

    private fun commit(text: String?) {
        val t = text?.trim().orEmpty()
        if (t.isNotEmpty()) {
            if (committed.isNotEmpty()) committed.append(' ')
            committed.append(t)
        }
        partial = ""
    }

    private fun finish() {
        if (!active) return
        active = false
        watchdog?.cancel()
        AudioPump.stop()
        closeSource()
        val text = postProcess(currentText())
        runCatching { recognizer?.cancel() }
        _state.value = VoiceState.Idle
        val cb = onFinished
        onFinished = null
        if (text.isNotBlank()) cb?.invoke(text)
    }

    private fun fail(message: String) {
        val heard = currentText()
        if (heard.isNotBlank()) {
            finish()
            return
        }
        active = false
        watchdog?.cancel()
        AudioPump.stop()
        closeSource()
        runCatching { recognizer?.cancel() }
        onFinished = null
        _state.value = VoiceState.Error(message)
    }

    private fun postProcess(raw: String): String {
        val t = raw.trim().replace(Regex("\\s+"), " ")
        if (t.isEmpty() || !settings.autoCapitalize) return t
        return t.replaceFirstChar { it.titlecase() }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            busyRetries = 0
        }

        override fun onBeginningOfSpeech() {
            heardSpeech = true
            lastSpeechAt = SystemClock.elapsedRealtime()
            publish()
        }

        override fun onRmsChanged(rmsdB: Float) {
            if (pipeMode) return // our own meter is used instead
            // Typical range is about -2..10 dB.
            level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            publish()
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit

        override fun onPartialResults(partialResults: Bundle?) {
            val p = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty()
            if (p.isNotBlank() && p != partial) {
                partial = p
                heardSpeech = true
                lastSpeechAt = SystemClock.elapsedRealtime()
            }
            publish()
        }

        override fun onResults(results: Bundle?) {
            if (!active) return
            val r = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            commit(r ?: partial)
            if (!r.isNullOrBlank()) heardSpeech = true
            publish()
            // The recogniser stopped on its own after a short pause; keep listening until the
            // user's silence window has really elapsed (the watchdog will finish us).
            restartSoon()
        }

        override fun onError(error: Int) {
            if (!active) return
            if (pipeMode && !pipeConfirmed && error == SpeechRecognizer.ERROR_AUDIO) {
                commit(partial)
                abandonPipe(restart = true)
                return
            }
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    commit(partial)
                    publish()
                    restartSoon(50)
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    if (busyRetries++ < 3) {
                        runCatching { recognizer?.cancel() }
                        restartSoon(250)
                    } else fail("The speech recogniser is busy. Try again.")
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    fail("Microphone permission is needed for voice notes.")
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
                SpeechRecognizer.ERROR_SERVER,
                SpeechRecognizer.ERROR_CLIENT -> {
                    if (usingOnDevice) {
                        // On-device model missing for this language: fall back to the default service.
                        commit(partial)
                        createRecognizer(preferOnDevice = false)
                        restartSoon(100)
                    } else if (error == SpeechRecognizer.ERROR_CLIENT) {
                        restartSoon(150)
                    } else {
                        fail("Speech recognition error ($error).")
                    }
                }
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                SpeechRecognizer.ERROR_SERVER_DISCONNECTED ->
                    fail("No connection for speech recognition. Turn on 'Prefer on-device' in settings.")
                SpeechRecognizer.ERROR_TOO_MANY_REQUESTS ->
                    fail("Speech service rate limit reached. Wait a moment.")
                else -> fail("Speech recognition error ($error).")
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }
}
