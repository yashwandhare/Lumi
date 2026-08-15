package com.lumi.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lumi.core.model.MessageRole

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)

/**
 * One turn in a conversation.
 *
 * Deleting a chat deletes its messages — the cascade is declared rather than done in
 * code so a stray delete cannot leave orphan rows behind.
 */
@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["chatId", "createdAtMs"])],
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chatId: Long,
    val role: MessageRole,
    val text: String,
    val createdAtMs: Long,
    /** Content uri of an attachment. The file itself is never copied into the database. */
    val attachmentUri: String? = null,
    val attachmentMimeType: String? = null,
)
