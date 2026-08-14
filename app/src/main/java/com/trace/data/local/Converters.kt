package com.trace.data.local

import androidx.room.TypeConverter
import com.trace.core.model.ActionType
import com.trace.core.model.AuditOutcome
import com.trace.core.model.CapabilityId
import com.trace.core.model.MemoryKind
import com.trace.core.model.MemorySource
import com.trace.core.model.MessageRole
import com.trace.core.model.TriggerType

/**
 * Enums are stored as their names, not their ordinals.
 *
 * Ordinals would make reordering an enum a silent data corruption. Names cost a few bytes
 * per row and make the database readable when something goes wrong at 2am.
 *
 * Unknown values decode to a safe default rather than throwing, so a row written by a
 * newer build cannot crash an older one during development.
 */
class Converters {

    @TypeConverter
    fun fromCapabilityId(value: CapabilityId): String = value.name

    @TypeConverter
    fun toCapabilityId(value: String): CapabilityId =
        enumValueOrDefault(value, CapabilityId.CHAT)

    @TypeConverter
    fun fromMessageRole(value: MessageRole): String = value.name

    @TypeConverter
    fun toMessageRole(value: String): MessageRole =
        enumValueOrDefault(value, MessageRole.USER)

    @TypeConverter
    fun fromMemoryKind(value: MemoryKind): String = value.name

    @TypeConverter
    fun toMemoryKind(value: String): MemoryKind =
        enumValueOrDefault(value, MemoryKind.USER_AUTHORED)

    @TypeConverter
    fun fromMemorySource(value: MemorySource): String = value.name

    @TypeConverter
    fun toMemorySource(value: String): MemorySource =
        enumValueOrDefault(value, MemorySource.UNSPECIFIED)

    @TypeConverter
    fun fromTriggerType(value: TriggerType): String = value.name

    @TypeConverter
    fun toTriggerType(value: String): TriggerType =
        enumValueOrDefault(value, TriggerType.TIME)

    @TypeConverter
    fun fromActionType(value: ActionType): String = value.name

    @TypeConverter
    fun toActionType(value: String): ActionType =
        enumValueOrDefault(value, ActionType.NOTIFY)

    @TypeConverter
    fun fromAuditOutcome(value: AuditOutcome): String = value.name

    @TypeConverter
    fun toAuditOutcome(value: String): AuditOutcome =
        enumValueOrDefault(value, AuditOutcome.FAILURE)

    /** Embeddings are small float arrays; storing them as a BLOB avoids a join per chunk. */
    @TypeConverter
    fun fromFloatArray(value: FloatArray?): ByteArray? = value?.let { floats ->
        java.nio.ByteBuffer.allocate(floats.size * Float.SIZE_BYTES).apply {
            floats.forEach { putFloat(it) }
        }.array()
    }

    @TypeConverter
    fun toFloatArray(value: ByteArray?): FloatArray? = value?.let { bytes ->
        val buffer = java.nio.ByteBuffer.wrap(bytes)
        FloatArray(bytes.size / Float.SIZE_BYTES) { buffer.float }
    }
}

private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default
