package com.lumi.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    @Query("SELECT * FROM documents ORDER BY addedAtMs DESC")
    fun observeDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE uri = :uri LIMIT 1")
    suspend fun findByUri(uri: String): DocumentEntity?

    /**
     * Every stored vector, without any chunk text.
     *
     * This is the retrieval hot path: a brute-force cosine scan needs ids and vectors and
     * nothing else. Loading chunk text here would pull the entire corpus through memory on
     * every query. Text for the top matches comes from [chunksByIds] afterwards.
     */
    @Query("SELECT * FROM chunk_embeddings")
    suspend fun allEmbeddings(): List<ChunkEmbeddingEntity>

    @Query("SELECT * FROM document_chunks WHERE id IN (:chunkIds)")
    suspend fun chunksByIds(chunkIds: List<Long>): List<DocumentChunkEntity>

    @Query("SELECT * FROM document_chunks WHERE documentId = :documentId ORDER BY ordinal ASC")
    suspend fun chunksOf(documentId: Long): List<DocumentChunkEntity>

    @Query("SELECT COUNT(*) FROM document_chunks")
    fun observeChunkCount(): Flow<Int>

    @Query("DELETE FROM documents WHERE id = :documentId")
    suspend fun deleteDocument(documentId: Long)

    /**
     * Store a document, its chunks, and their vectors as one unit.
     *
     * A half-indexed document is worse than an unindexed one: retrieval would silently
     * return partial context and the user would have no way to tell. The transaction means
     * the document either appears fully searchable or does not appear at all.
     */
    @Transaction
    suspend fun saveIndexedDocument(
        document: DocumentEntity,
        chunkTexts: List<String>,
        vectors: List<ByteArray>,
        nowMs: Long,
    ): Long {
        require(chunkTexts.size == vectors.size) {
            "each chunk needs exactly one vector: ${chunkTexts.size} chunks, ${vectors.size} vectors"
        }
        val documentId = insertDocument(document)
        val chunkIds = insertChunks(
            chunkTexts.mapIndexed { ordinal, text ->
                DocumentChunkEntity(documentId = documentId, ordinal = ordinal, text = text)
            },
        )
        insertEmbeddings(
            chunkIds.mapIndexed { index, chunkId ->
                ChunkEmbeddingEntity(chunkId = chunkId, vector = vectors[index])
            },
        )
        markIndexed(documentId, nowMs, chunkTexts.size)
        return documentId
    }

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDocument(document: DocumentEntity): Long

    @Insert
    suspend fun insertChunks(chunks: List<DocumentChunkEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmbeddings(embeddings: List<ChunkEmbeddingEntity>)

    @Query("UPDATE documents SET indexedAtMs = :indexedAtMs, chunkCount = :chunkCount WHERE id = :documentId")
    suspend fun markIndexed(documentId: Long, indexedAtMs: Long, chunkCount: Int)
}
