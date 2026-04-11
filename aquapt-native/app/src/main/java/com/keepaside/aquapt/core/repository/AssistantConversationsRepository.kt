package com.keepaside.aquapt.core.repository

import android.content.Context
import android.content.SharedPreferences
import com.keepaside.aquapt.core.model.AssistantConversation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

interface AssistantConversationsStore {
    val conversations: StateFlow<List<AssistantConversation>>

    suspend fun setConversations(conversations: List<AssistantConversation>)
}

class AssistantConversationsRepository(
    context: Context
) : AssistantConversationsStore {

    private val preferences: SharedPreferences = context.getSharedPreferences(
        preferencesFile,
        Context.MODE_PRIVATE
    )

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val _conversations = MutableStateFlow(readConversations())
    override val conversations: StateFlow<List<AssistantConversation>> = _conversations.asStateFlow()

    override suspend fun setConversations(conversations: List<AssistantConversation>) {
        val normalized = conversations.sortedWith(
            compareByDescending<AssistantConversation> { it.pinned }
                .thenByDescending { it.updatedAt }
        )

        preferences.edit()
            .putString(
                keyConversations,
                json.encodeToString(
                    ListSerializer(AssistantConversation.serializer()),
                    normalized
                )
            )
            .apply()

        _conversations.update { normalized }
    }

    private fun readConversations(): List<AssistantConversation> {
        val raw = preferences.getString(keyConversations, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString(
                ListSerializer(AssistantConversation.serializer()),
                raw
            )
        }.getOrDefault(emptyList())
            .sortedWith(
                compareByDescending<AssistantConversation> { it.pinned }
                    .thenByDescending { it.updatedAt }
            )
    }

    companion object {
        private const val preferencesFile = "aquapt_assistant"
        private const val keyConversations = "assistant_conversations"
    }
}