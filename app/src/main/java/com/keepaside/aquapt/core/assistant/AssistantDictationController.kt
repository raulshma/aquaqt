package com.keepaside.aquapt.core.assistant

interface AssistantDictationController {
    val isAvailable: Boolean

    fun startListening(
        onPartialTranscript: (String) -> Unit,
        onFinalTranscript: (String) -> Unit,
        onError: (String) -> Unit
    ): Boolean

    fun stopListening()

    fun cancelListening()

    fun release()
}

object NoOpAssistantDictationController : AssistantDictationController {
    override val isAvailable: Boolean = false

    override fun startListening(
        onPartialTranscript: (String) -> Unit,
        onFinalTranscript: (String) -> Unit,
        onError: (String) -> Unit
    ): Boolean = false

    override fun stopListening() = Unit

    override fun cancelListening() = Unit

    override fun release() = Unit
}