package com.trace.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.trace.core.model.ActionType
import com.trace.core.model.TriggerType

/**
 * A routine: one or more triggers, and an ordered list of actions.
 *
 * [sourceText] is the sentence the user originally typed. It exists so the UI can show
 * and re-edit the original wording. It is **never** re-parsed when the routine fires —
 * firing reads the persisted trigger and action rows. That rule is the whole reason the
 * structure is stored separately from the sentence.
 */
@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sourceText: String,
    val enabled: Boolean = true,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val lastFiredAtMs: Long? = null,
)

/**
 * What starts a routine.
 *
 * [value] is interpreted according to [type]: local time as `HH:mm` for `TIME`, an SSID
 * for `WIFI`, a percentage for `BATTERY`, a place label for `LOCATION`, a calendar title
 * match for `CALENDAR`. Geofence coordinates use the dedicated columns rather than being
 * packed into the string.
 *
 * Indexed on type because the worker wakes up asking "which routines care about wifi?"
 */
@Entity(
    tableName = "routine_triggers",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("routineId"), Index("type")],
)
data class RoutineTriggerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long,
    val type: TriggerType,
    val value: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radiusMeters: Float? = null,
)

/**
 * One step a routine performs, run in [ordinal] order.
 *
 * [value] is the argument for [type]: `"on"`/`"off"` for a toggle, a package name for
 * `OPEN_APP`, the message for `NOTIFY` or `SPEAK`, the query for `RUN_RAG_QUERY`.
 */
@Entity(
    tableName = "routine_actions",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["routineId", "ordinal"])],
)
data class RoutineActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long,
    val ordinal: Int,
    val type: ActionType,
    val value: String,
)
