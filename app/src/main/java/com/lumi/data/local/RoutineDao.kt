package com.lumi.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.lumi.core.model.TriggerType
import kotlinx.coroutines.flow.Flow

/**
 * A routine and its full structure, loaded in one read.
 *
 * The worker that fires a routine needs the triggers and the actions together. Fetching
 * them in separate queries would let a routine change between reads and fire a mixture of
 * old and new steps.
 */
data class RoutineWithGraph(
    @Embedded val routine: RoutineEntity,
    @Relation(parentColumn = "id", entityColumn = "routineId")
    val triggers: List<RoutineTriggerEntity>,
    @Relation(parentColumn = "id", entityColumn = "routineId")
    val actions: List<RoutineActionEntity>,
) {
    /** Actions in the order the user arranged them. */
    val orderedActions: List<RoutineActionEntity> get() = actions.sortedBy { it.ordinal }
}

@Dao
interface RoutineDao {

    @Transaction
    @Query("SELECT * FROM routines ORDER BY createdAtMs DESC")
    fun observeRoutines(): Flow<List<RoutineWithGraph>>

    @Transaction
    @Query("SELECT * FROM routines WHERE id = :routineId")
    suspend fun findRoutine(routineId: Long): RoutineWithGraph?

    /**
     * Enabled routines that have at least one trigger of this kind.
     *
     * This is the query a woken worker runs first. DISTINCT matters: a routine with two
     * wifi triggers would otherwise be returned twice and fire twice.
     */
    @Transaction
    @Query(
        """
        SELECT DISTINCT r.* FROM routines r
        INNER JOIN routine_triggers t ON t.routineId = r.id
        WHERE r.enabled = 1 AND t.type = :type
        """,
    )
    suspend fun enabledRoutinesFor(type: TriggerType): List<RoutineWithGraph>

    @Query("SELECT COUNT(*) FROM routines WHERE enabled = 1")
    fun observeEnabledCount(): Flow<Int>

    @Query("UPDATE routines SET enabled = :enabled, updatedAtMs = :nowMs WHERE id = :routineId")
    suspend fun setEnabled(routineId: Long, enabled: Boolean, nowMs: Long)

    @Query("UPDATE routines SET lastFiredAtMs = :firedAtMs WHERE id = :routineId")
    suspend fun markFired(routineId: Long, firedAtMs: Long)

    @Query("DELETE FROM routines WHERE id = :routineId")
    suspend fun deleteRoutine(routineId: Long)

    /**
     * Persist a routine and its structure together.
     *
     * A routine with triggers but no actions would arm itself and then do nothing, which
     * looks to the user like the app is broken. One transaction, or nothing.
     */
    @Transaction
    suspend fun saveRoutine(
        routine: RoutineEntity,
        triggers: List<RoutineTriggerEntity>,
        actions: List<RoutineActionEntity>,
    ): Long {
        require(triggers.isNotEmpty()) { "a routine with no trigger can never fire" }
        require(actions.isNotEmpty()) { "a routine with no action would fire and do nothing" }
        val routineId = insertRoutine(routine)
        insertTriggers(triggers.map { it.copy(routineId = routineId) })
        insertActions(actions.mapIndexed { index, action -> action.copy(routineId = routineId, ordinal = index) })
        return routineId
    }

    @Insert
    suspend fun insertRoutine(routine: RoutineEntity): Long

    @Insert
    suspend fun insertTriggers(triggers: List<RoutineTriggerEntity>)

    @Insert
    suspend fun insertActions(actions: List<RoutineActionEntity>)
}
