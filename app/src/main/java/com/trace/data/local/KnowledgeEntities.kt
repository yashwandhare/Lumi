package com.trace.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A document or note the user explicitly attached.
 *
 * Trace never scans device storage to populate this table. Rows appear only when the user
 * attaches a file in chat or picks one directly — see decisions.md.
 */
@Entity(
    tableName = "documents",
    indices = [Index(value = ["uri"], unique = true)],
)
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val addedAtMs: Long,
    /** Null until chunking and embedding finish. Drives the "indexing" state in the UI. */
    val indexedAtMs: Long? = null,
    val chunkCount: Int = 0,
)

@Entity(
    tableName = "document_chunks",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["documentId", "ordinal"])],
)
data class DocumentChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val ordinal: Int,
    val text: String,
)

/**
 * Chunk embeddings, kept in their own table on purpose.
 *
 * Retrieval scans every vector but needs no chunk text to do it. Splitting the vector out
 * means a search reads only ids and blobs instead of dragging every chunk's full text
 * through memory, and the text for the top matches is fetched afterwards in one query.
 *
 * Not a data class: it holds an array, and identity equality is the honest behaviour for
 * a row that is written once and never compared.
 */
@Entity(
    tableName = "chunk_embeddings",
    foreignKeys = [
        ForeignKey(
            entity = DocumentChunkEntity::class,
            parentColumns = ["id"],
            childColumns = ["chunkId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
class ChunkEmbeddingEntity(
    @PrimaryKey val chunkId: Long,
    /** Little-endian float32 vector. Converted by [Converters.toFloatArray]. */
    val vector: ByteArray,
)
