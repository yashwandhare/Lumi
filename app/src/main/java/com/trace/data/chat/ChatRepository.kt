package com.trace.data.chat

import com.trace.core.model.MessageRole
import com.trace.data.local.ChatDao
import com.trace.data.local.ChatEntity
import com.trace.data.local.ChatMessageEntity
import com.trace.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Conversations, on disk.
 *
 * Wraps [ChatDao] rather than letting the view model use it directly, because three rules belong
 * together and none of them are the DAO's business: a chat row is created lazily, its title comes from
 * the first thing the user said, and both are one transaction with the message that caused them.
 *
 * **Lazily** matters. Creating a chat when the screen opens would fill the history drawer with empty
 * conversations every time the app was launched and closed — which is what "history doesn't work"
 * looks like from the outside even when the storage is fine.
 */
@Singleton
class ChatRepository @Inject constructor(
    private val chatDao: ChatDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    fun observeChats(): Flow<List<ChatEntity>> = chatDao.observeChats()

    fun observeMessages(chatId: Long): Flow<List<ChatMessageEntity>> =
        chatDao.observeMessages(chatId)

    /**
     * Store a user turn, creating the conversation if this is its first.
     *
     * @return the chat id the message landed in, so the caller can keep using it for the reply.
     */
    suspend fun appendUserMessage(chatId: Long?, text: String, nowMs: Long): Long =
        withContext(io) {
            val id = chatId ?: chatDao.insertChat(
                ChatEntity(title = titleFrom(text), createdAtMs = nowMs, updatedAtMs = nowMs),
            )
            chatDao.insertMessage(
                ChatMessageEntity(
                    chatId = id,
                    role = MessageRole.USER,
                    text = text,
                    createdAtMs = nowMs,
                ),
            )
            chatDao.touchChat(id, nowMs)
            id
        }

    /**
     * Store a finished model reply.
     *
     * Written once, when generation completes, rather than updated per token. A row rewritten on every
     * delta would be hundreds of writes per reply for a value nothing reads until it is final — the
     * streaming text the user watches lives in memory.
     */
    suspend fun appendModelMessage(chatId: Long, text: String, nowMs: Long) = withContext(io) {
        chatDao.insertMessage(
            ChatMessageEntity(
                chatId = chatId,
                role = MessageRole.MODEL,
                text = text,
                createdAtMs = nowMs,
            ),
        )
        chatDao.touchChat(chatId, nowMs)
    }

    suspend fun deleteChat(chatId: Long) = withContext(io) { chatDao.deleteChat(chatId) }

    /**
     * A title from the user's first message.
     *
     * Cut on a word boundary so the drawer never shows a chopped word. Deliberately not model-generated:
     * a title is worth no inference time, and asking the model would make the first send slower for
     * something the user can already recognise.
     */
    private fun titleFrom(text: String): String {
        val flattened = text.trim().replace(WHITESPACE, " ")
        if (flattened.length <= TITLE_MAX) return flattened
        val cut = flattened.take(TITLE_MAX)
        val lastSpace = cut.lastIndexOf(' ')
        return (if (lastSpace > TITLE_MIN) cut.take(lastSpace) else cut).trimEnd() + "…"
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
        const val TITLE_MAX = 48
        const val TITLE_MIN = 24
    }
}
