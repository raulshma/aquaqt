package com.keepaside.aquapt.core.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

class AndroidSpeechAssistantDictationController(
    context: Context
) : AssistantDictationController {

    private val appContext = context.applicationContext

    private var speechRecognizer: SpeechRecognizer? = null
    private var onPartialTranscript: ((String) -> Unit)? = null
    private var onFinalTranscript: ((String) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    private var suppressClientError: Boolean = false

    override val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(appContext)

    override fun startListening(
        onPartialTranscript: (String) -> Unit,
        onFinalTranscript: (String) -> Unit,
        onError: (String) -> Unit
    ): Boolean {
        if (!isAvailable) {
            onError("Speech recognition is unavailable on this device.")
            return false
        }

        val recognizer = ensureSpeechRecognizer()
        if (recognizer == null) {
            onError("Could not initialize speech recognition.")
            return false
        }

        this.onPartialTranscript = onPartialTranscript
        this.onFinalTranscript = onFinalTranscript
        this.onError = onError
        suppressClientError = false

        return runCatching {
            recognizer.startListening(createRecognizerIntent())
            true
        }.getOrElse { throwable ->
            onError(
                throwable.message
                    ?.takeIf { it.isNotBlank() }
                    ?.let { "Could not start dictation: $it" }
                    ?: "Could not start dictation."
            )
            false
        }
    }

    override fun stopListening() {
        suppressClientError = true
        runCatching { speechRecognizer?.stopListening() }
    }

    override fun cancelListening() {
        suppressClientError = true
        runCatching { speechRecognizer?.cancel() }
    }

    override fun release() {
        runCatching {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        }
        speechRecognizer = null
        onPartialTranscript = null
        onFinalTranscript = null
        onError = null
        suppressClientError = false
    }

    private fun ensureSpeechRecognizer(): SpeechRecognizer? {
        val existing = speechRecognizer
        if (existing != null) {
            return existing
        }

        return runCatching {
            SpeechRecognizer.createSpeechRecognizer(appContext).also { recognizer ->
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) = Unit

                    override fun onBeginningOfSpeech() = Unit

                    override fun onRmsChanged(rmsdB: Float) = Unit

                    override fun onBufferReceived(buffer: ByteArray?) = Unit

                    override fun onEndOfSpeech() = Unit

                    override fun onError(error: Int) {
                        if (suppressClientError && error == SpeechRecognizer.ERROR_CLIENT) {
                            suppressClientError = false
                            return
                        }

                        suppressClientError = false
                        onError?.invoke(resolveErrorMessage(error))
                    }

                    override fun onResults(results: Bundle?) {
                        suppressClientError = false
                        val transcript = results.firstTranscript()
                        if (transcript.isNullOrBlank()) {
                            onError?.invoke("No speech was detected. Try again.")
                            return
                        }

                        onFinalTranscript?.invoke(transcript)
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val transcript = partialResults.firstTranscript() ?: return
                        onPartialTranscript?.invoke(transcript)
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
                speechRecognizer = recognizer
            }
        }.getOrNull()
    }

    private fun createRecognizerIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

    private fun Bundle?.firstTranscript(): String? =
        this
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun resolveErrorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Dictation failed due to an audio input error."
        SpeechRecognizer.ERROR_CLIENT -> "Dictation was interrupted."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
            "Microphone permission is required for dictation."
        }
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Dictation failed due to a network issue."
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech was detected. Try again."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy. Try again in a moment."
        SpeechRecognizer.ERROR_SERVER -> "Dictation service returned an error."
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "Too many dictation requests. Please wait a moment."
        else -> "Dictation failed. Please try again."
    }
}