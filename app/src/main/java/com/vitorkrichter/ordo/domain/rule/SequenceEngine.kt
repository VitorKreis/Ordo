package com.vitorkrichter.ordo.domain.rule

import com.vitorkrichter.ordo.domain.model.WorkoutSessionStatus
import com.vitorkrichter.ordo.domain.model.WorkoutTemplate
import com.vitorkrichter.ordo.domain.model.WorkoutTemplateId

/**
 * Pure rules for a free workout sequence.
 *
 * The cursor points to the next normal workout. Repeat and extra selections
 * deliberately do not change it. Persistence can store [SequenceCursor.nextWorkoutId]
 * as the durable cursor and use [SequenceCursor.nextIndex] while reconciling
 * records from older versions.
 */
object SequenceEngine {
    /** The kind of session selection made by the user. */
    enum class SelectionKind {
        NORMAL,
        REPEAT,
        EXTRA,
    }

    /** The terminal outcome relevant to sequence progression. */
    enum class Outcome {
        COMPLETED,
        PARTIALLY_COMPLETED,
        CANCELED,
        ABANDONED,
    }

    /**
     * A cursor is intentionally identity-first. [nextIndex] is a fallback for
     * old/index-based persistence and for reconciling a removed workout.
     */
    data class SequenceCursor(
        val nextWorkoutId: WorkoutTemplateId? = null,
        val nextIndex: Int = 0,
        val lastAdvancedEventId: String? = null,
    ) {
        companion object {
            fun atIndex(index: Int): SequenceCursor = SequenceCursor(nextIndex = index)

            fun atWorkout(workoutId: WorkoutTemplateId): SequenceCursor =
                SequenceCursor(nextWorkoutId = workoutId)
        }
    }

    data class SelectionRequest(
        val kind: SelectionKind = SelectionKind.NORMAL,
        val workoutId: WorkoutTemplateId? = null,
    )

    data class Selection(
        val workout: WorkoutTemplate,
        val kind: SelectionKind,
        /** Cursor after reconciling the available active workouts. */
        val reconciledCursor: SequenceCursor,
    )

    sealed interface SelectionResult {
        data class Selected(val value: Selection) : SelectionResult

        data object NoAvailableWorkout : SelectionResult

        /** An explicitly requested repeat/extra workout no longer exists. */
        data class RequestedWorkoutUnavailable(val workoutId: WorkoutTemplateId) : SelectionResult
    }

    data class AdvanceEvent(
        val id: String,
        val outcome: Outcome,
        val kind: SelectionKind = SelectionKind.NORMAL,
        val workoutId: WorkoutTemplateId? = null,
    )

    /**
     * Selects a workout without mutating any state.
     *
     * Normal selection uses the reconciled cursor. A repeat or extra selection
     * may name a workout explicitly; otherwise it uses the current normal
     * workout, still without consuming the next normal item.
     */
    fun select(
        workouts: List<WorkoutTemplate>,
        cursor: SequenceCursor = SequenceCursor(),
        request: SelectionRequest = SelectionRequest(),
    ): SelectionResult {
        val sequence = activeSequence(workouts)
        if (sequence.isEmpty()) {
            return SelectionResult.NoAvailableWorkout
        }

        val reconciledCursor = reconcileActiveSequence(sequence, cursor)
        val workout = when {
            request.kind == SelectionKind.NORMAL -> sequence[indexOfCursor(sequence, reconciledCursor)]
            request.workoutId != null -> sequence.firstOrNull { it.id == request.workoutId }
            else -> sequence[indexOfCursor(sequence, reconciledCursor)]
        }

        if (workout == null && request.workoutId != null) {
            return SelectionResult.RequestedWorkoutUnavailable(request.workoutId)
        }

        return SelectionResult.Selected(
            Selection(
                workout = requireNotNull(workout),
                kind = request.kind,
                reconciledCursor = reconciledCursor,
            ),
        )
    }

    /** Convenience overload for the common normal/repeat/extra call site. */
    fun select(
        workouts: List<WorkoutTemplate>,
        cursor: SequenceCursor = SequenceCursor(),
        kind: SelectionKind,
        workoutId: WorkoutTemplateId? = null,
    ): SelectionResult = select(
        workouts = workouts,
        cursor = cursor,
        request = SelectionRequest(kind = kind, workoutId = workoutId),
    )

    /**
     * Reconciles an ID/index cursor against the current active sequence.
     * Archived workouts are excluded. A removed ID falls back to its persisted
     * index, normalized with wrap-around; an absent cursor starts at the first
     * active workout.
     */
    fun reconcileCursor(
        workouts: List<WorkoutTemplate>,
        cursor: SequenceCursor,
    ): SequenceCursor {
        val sequence = activeSequence(workouts)
        return reconcileActiveSequence(sequence, cursor)
    }

    /**
     * Applies one terminal session event to the normal cursor.
     *
     * COMPLETED advances exactly one normal item. PARTIALLY_COMPLETED,
     * CANCELED/ABANDONED and all REPEAT/EXTRA events leave the cursor intact.
     * Replaying the same advancing event is idempotent through
     * [AdvanceEvent.id]/[SequenceCursor.lastAdvancedEventId].
     */
    fun advance(
        workouts: List<WorkoutTemplate>,
        cursor: SequenceCursor,
        event: AdvanceEvent,
    ): SequenceCursor {
        if (event.kind != SelectionKind.NORMAL ||
            event.outcome != Outcome.COMPLETED ||
            event.id == cursor.lastAdvancedEventId
        ) {
            return cursor
        }

        val sequence = activeSequence(workouts)
        if (sequence.isEmpty()) {
            return SequenceCursor(lastAdvancedEventId = cursor.lastAdvancedEventId)
        }

        val reconciledCursor = reconcileActiveSequence(sequence, cursor)
        val currentIndex = indexOfCursor(sequence, reconciledCursor)
        if (event.workoutId != null && event.workoutId != sequence[currentIndex].id) {
            return reconciledCursor
        }

        val nextIndex = (currentIndex + 1) % sequence.size
        return SequenceCursor(
            nextWorkoutId = sequence[nextIndex].id,
            nextIndex = nextIndex,
            lastAdvancedEventId = event.id,
        )
    }

    /**
     * Compatibility overload for existing sessions. The new [Outcome.ABANDONED]
     * is represented by [Outcome]; existing persisted statuses remain unchanged.
     */
    fun advance(
        workouts: List<WorkoutTemplate>,
        cursor: SequenceCursor,
        eventId: String,
        status: WorkoutSessionStatus,
        kind: SelectionKind = SelectionKind.NORMAL,
        workoutId: WorkoutTemplateId? = null,
    ): SequenceCursor {
        val outcome = when (status) {
            WorkoutSessionStatus.COMPLETED -> Outcome.COMPLETED
            WorkoutSessionStatus.PARTIALLY_COMPLETED -> Outcome.PARTIALLY_COMPLETED
            WorkoutSessionStatus.CANCELED -> Outcome.CANCELED
            WorkoutSessionStatus.IN_PROGRESS -> return cursor
        }
        return advance(
            workouts = workouts,
            cursor = cursor,
            event = AdvanceEvent(
                id = eventId,
                outcome = outcome,
                kind = kind,
                workoutId = workoutId,
            ),
        )
    }

    private fun activeSequence(workouts: List<WorkoutTemplate>): List<WorkoutTemplate> = workouts
        .asSequence()
        .filterNot { it.isArchived }
        .sortedWith(compareBy<WorkoutTemplate> { it.position }.thenBy { it.id.value })
        .distinctBy { it.id }
        .toList()

    private fun reconcileActiveSequence(
        sequence: List<WorkoutTemplate>,
        cursor: SequenceCursor,
    ): SequenceCursor {
        if (sequence.isEmpty()) {
            return SequenceCursor(lastAdvancedEventId = cursor.lastAdvancedEventId)
        }

        val idIndex = cursor.nextWorkoutId?.let { id ->
            sequence.indexOfFirst { it.id == id }.takeIf { it >= 0 }
        }
        val index = idIndex ?: Math.floorMod(cursor.nextIndex, sequence.size)
        return SequenceCursor(
            nextWorkoutId = sequence[index].id,
            nextIndex = index,
            lastAdvancedEventId = cursor.lastAdvancedEventId,
        )
    }

    private fun indexOfCursor(
        sequence: List<WorkoutTemplate>,
        cursor: SequenceCursor,
    ): Int = cursor.nextWorkoutId
        ?.let { id -> sequence.indexOfFirst { it.id == id }.takeIf { it >= 0 } }
        ?: Math.floorMod(cursor.nextIndex, sequence.size)
}
