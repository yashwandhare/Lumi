package com.trace.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Query("SELECT * FROM chats ORDER BY updatedAtMs DESC")
    fun observeChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chat_messages WHERE chatId = :chatId ORDER BY createdAtMs ASC")
    fun observeMessages(chatId: Long): Flow<List<ChatMessageEntity>>

    /** The tail of a conversation, for building the model's context window. */
    @Query("SELECT * FROM chat_messages WHERE chatId = :chatId ORDER BY createdAtMs DESC LIMIT :limit")
    suspend fun recentMessages(chatId: Long, limit: Int): List<ChatMessageEntity>

    @Insert
    suspend fun insertChat(chat: ChatEntity): Long

    @Insert
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("UPDATE chats SET title = :title, updatedAtMs = :nowMs WHERE id = :chatId")
    suspend fun renameChat(chatId: Long, title: String, nowMs: Long)

    @Query("UPDATE chats SET updatedAtMs = :nowMs WHERE id = :chatId")
    suspend fun touchChat(chatId: Long, nowMs: Long)

    @Query("DELETE FROM chats WHERE id = :chatId")
    suspend fun deleteChat(chatId: Long)
}
