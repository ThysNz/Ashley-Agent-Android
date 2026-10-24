package nz.thys.ashley.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice

/** Speech-to-text. Phase 1: Android's recogniser (on-device when the phone offers it). Phase 4: sherpa-onnx Whisper. */
interface SttEngine {
    fun start(language: String, listener: Listener)
    /** Hold-to-talk released: finish and deliver the result. */
    fun stop()
    fun cancel()
    fun release()

    interface Listener {
        fun onLevel(level: Float) {}
        fun onPartial(text: String) {}
        fun onResult(text: String)
        fun onError(message: String)
    }
}

/** Text-to-speech. Phase 1: Android's TTS (Samsung or Google engine). Phase 4: sherpa-onnx with the Piper voices. */
interface TtsEngine {
    val ready: Boolean
    fun voices(): List<String>
    fun speak(text: String, voice: String, speed: Float, pitch: Float, onDone: () -> Unit)
    fun stop()
    fun release()
}

/** Applies the "name fixes" list from voice.json, e.g. "guys" -> "Thys" (whole words, any case). */
fun applyCorrections(text: String, corrections: Map<String, String>): String =
    corrections.entries.fold(text) { acc, (from, to) ->
        if (from.isBlank()) acc else acc.replace(Regex("\\b${Regex.escape(from)}\\b", RegexOption.IGNORE_CASE), to)
    }

class AndroidStt(private val context: Context) : SttEngine {
    private var recognizer: SpeechRecognizer? = null
    private var delivered = false
    private var stopRequested = false

    /** Samsung phones often have no working on-device recogniser for every language (error 11/12/13). After one failure we use the standard one. */
    private var onDeviceFailed = false

    private fun useOnDevice() = !onDeviceFailed && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    override fun start(language: String, listener: SttEngine.Listener) {
        if (!SpeechRecognizer.isRecognitionAvailable(context) && !SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            listener.onError("No speech recogniser on this phone. Install or enable Google speech services, or wait for Whisper in phase 4.")
            return
        }
        stopRequested = false
        begin(language, listener)
    }

    private fun begin(language: String, listener: SttEngine.Listener) {
        destroyRecognizer()
        delivered = false
        val onDevice = useOnDevice()
        val r = if (onDevice) SpeechRecognizer.createOnDeviceSpeechRecognizer(context) else SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                listener.onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                if (delivered) return
                val onDeviceProblem = error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED ||
                    error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                    error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ||
                    error == SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT
                if (onDevice && onDeviceProblem) {
                    // Fall back to the phone's standard recogniser and carry on.
                    onDeviceFailed = true
                    if (!stopRequested) { begin(language, listener); return }
                    delivered = true
                    listener.onError("Switched to the phone's standard speech recogniser. Hold the mic and try again.")
                    return
                }
                delivered = true
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT, SpeechRecognizer.ERROR_CLIENT -> listener.onResult("")
                    else -> listener.onError(describe(error))
                }
            }
            override fun onResults(results: Bundle?) {
                if (delivered) return
                delivered = true
                listener.onResult(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: "")
            }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(listener::onPartial)
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, onDevice)
            // Hold-to-talk: don't end on a short pause while the button is still held.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
        }
        r.startListening(intent)
    }

    private fun describe(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition needs the network for this language. Check Wi-Fi or mobile data."
        SpeechRecognizer.ERROR_AUDIO -> "Couldn't record audio. Is another app using the microphone?"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is off."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "The speech recogniser is busy. Try again in a moment."
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "Too many speech requests. Wait a few seconds."
        SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "The speech service stopped (error $error). Try again; if it keeps happening, update Speech Recognition and Synthesis from Google in the Play Store."
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "This language isn't available for speech recognition. Try en-US or en-GB in Settings, Voice."
        else -> "Speech recognition error $error."
    }

    override fun stop() {
        stopRequested = true
        recognizer?.stopListening()
    }

    override fun cancel() {
        stopRequested = true
        destroyRecognizer()
    }

    private fun destroyRecognizer() {
        recognizer?.let { runCatching { it.cancel(); it.destroy() } }
        recognizer = null
    }

    override fun release() = cancel()
}

class AndroidTts(context: Context) : TtsEngine {
    @Volatile
    private var isReady = false
    override val ready: Boolean get() = isReady
    private var onDone: (() -> Unit)? = null
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status -> isReady = status == TextToSpeech.SUCCESS }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { finish() }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { finish() }
            override fun onStop(utteranceId: String?, interrupted: Boolean) { finish() }
        })
    }

    private fun finish() {
        val cb = onDone
        onDone = null
        cb?.invoke()
    }

    override fun voices(): List<String> = if (!ready) emptyList() else runCatching {
        tts.voices.orEmpty()
            .filter { it.locale.language == "en" && !it.isNetworkConnectionRequired && TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features }
            .map { it.name }.sorted()
    }.getOrDefault(emptyList())

    override fun speak(text: String, voice: String, speed: Float, pitch: Float, onDone: () -> Unit) {
        if (!ready || text.isBlank()) { onDone(); return }
        tts.stop()
        this.onDone = onDone
        if (voice.isNotBlank()) tts.voices?.firstOrNull { it.name == voice }?.let { tts.voice = it }
        tts.setSpeechRate(speed)
        tts.setPitch(pitch)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ashley-" + System.currentTimeMillis())
    }

    override fun stop() {
        tts.stop()
        finish()
    }

    override fun release() {
        tts.stop()
        tts.shutdown()
    }
}
