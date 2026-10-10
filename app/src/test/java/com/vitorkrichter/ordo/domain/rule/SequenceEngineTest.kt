package com.vitorkrichter.ordo.domain.rule

import com.vitorkrichter.ordo.domain.model.WorkoutTemplate
import com.vitorkrichter.ordo.domain.model.WorkoutTemplateId
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SequenceEngineTest {
    private val now = Instant.parse("2026-10-10T12:00:00Z")
    private val a = WorkoutTemplateId("a")
    private val b = WorkoutTemplateId("b")
    private val c = WorkoutTemplateId("c")

    @Test
    fun normalSelectionIsDeterministicByPositionThenId() {
        val result = SequenceEngine.select(
            workouts = listOf(workout(c, position = 1), workout(a, position = 0), workout(b, position = 0)),
        )

        assertEquals(a, selected(result).workout.id)
        assertEquals(listOf(a, b, c), sequenceIds(listOf(workout(c, 1), workout(a, 0), workout(b, 0))))
    }

    @Test
    fun aNewCursorStartsAtTheFirstActiveWorkoutRegardlessOfInputOrder() {
        val result = SequenceEngine.select(
            workouts = listOf(workout(c, position = 2), workout(b, position = 1), workout(a, position = 0)),
            cursor = SequenceEngine.SequenceCursor(),
        )

        assertEquals(a, selected(result).workout.id)
        assertEquals(a, selected(result).reconciledCursor.nextWorkoutId)
        assertEquals(0, selected(result).reconciledCursor.nextIndex)
    }

    @Test
    fun archivedWorkoutsAreExcludedAndMissingCursorFallsBackByIndex() {
        val cursor = SequenceEngine.SequenceCursor(
            nextWorkoutId = WorkoutTemplateId("removed"),
            nextIndex = 1,
        )

        val result = SequenceEngine.select(
            workouts = listOf(workout(a, 0), workout(b, 1, archived = true), workout(c, 2)),
            cursor = cursor,
        )

        assertEquals(c, selected(result).workout.id)
        assertEquals(c, selected(result).reconciledCursor.nextWorkoutId)
    }

    @Test
    fun anExistingCursorIdSurvivesReordering() {
        val result = SequenceEngine.select(
            workouts = listOf(workout(c, position = 0), workout(a, position = 2), workout(b, position = 1)),
            cursor = SequenceEngine.SequenceCursor.atWorkout(b),
        )

        assertEquals(b, selected(result).workout.id)
        assertEquals(b, selected(result).reconciledCursor.nextWorkoutId)
    }

    @Test
    fun emptySequenceReturnsNoAvailableWorkout() {
        assertTrue(
            SequenceEngine.select(emptyList()) is SequenceEngine.SelectionResult.NoAvailableWorkout,
        )
        assertEquals(
            SequenceEngine.SequenceCursor(),
            SequenceEngine.advance(
                workouts = emptyList(),
                cursor = SequenceEngine.SequenceCursor.atIndex(4),
                event = SequenceEngine.AdvanceEvent("event", SequenceEngine.Outcome.COMPLETED),
            ),
        )
    }

    @Test
    fun onlyCompletedAdvancesAndWrapsAround() {
        val initial = SequenceEngine.SequenceCursor.atWorkout(a)
        val completed = SequenceEngine.advance(
            workouts = workouts(),
            cursor = initial,
            event = SequenceEngine.AdvanceEvent(
                id = "session-a",
                outcome = SequenceEngine.Outcome.COMPLETED,
                workoutId = a,
            ),
        )
        val partial = SequenceEngine.advance(
            workouts = workouts(),
            cursor = completed,
            event = SequenceEngine.AdvanceEvent(
                id = "session-b",
                outcome = SequenceEngine.Outcome.PARTIALLY_COMPLETED,
                workoutId = b,
            ),
        )
        val completedAfterPartial = SequenceEngine.advance(
            workouts = workouts(),
            cursor = partial,
            event = SequenceEngine.AdvanceEvent(
                id = "session-b-complete",
                outcome = SequenceEngine.Outcome.COMPLETED,
                workoutId = b,
            ),
        )
        val wrapped = SequenceEngine.advance(
            workouts = workouts(),
            cursor = completedAfterPartial,
            event = SequenceEngine.AdvanceEvent(
                id = "session-c",
                outcome = SequenceEngine.Outcome.COMPLETED,
                workoutId = c,
            ),
        )

        assertEquals(b, completed.nextWorkoutId)
        assertEquals(b, partial.nextWorkoutId)
        assertEquals(c, completedAfterPartial.nextWorkoutId)
        assertEquals(a, wrapped.nextWorkoutId)
    }

    @Test
    fun replayingSameAdvanceEventIsIdempotent() {
        val cursor = SequenceEngine.SequenceCursor.atWorkout(a)
        val event = SequenceEngine.AdvanceEvent(
            id = "session-a",
            outcome = SequenceEngine.Outcome.COMPLETED,
            workoutId = a,
        )

        val once = SequenceEngine.advance(workouts(), cursor, event)
        val twice = SequenceEngine.advance(workouts(), once, event)

        assertEquals(once, twice)
    }

    @Test
    fun cancelAndAbandonDoNotAdvance() {
        val cursor = SequenceEngine.SequenceCursor.atWorkout(b)

        for (outcome in listOf(SequenceEngine.Outcome.CANCELED, SequenceEngine.Outcome.ABANDONED)) {
            assertEquals(
                cursor,
                SequenceEngine.advance(
                    workouts(),
                    cursor,
                    SequenceEngine.AdvanceEvent("$outcome-event", outcome, workoutId = b),
                ),
            )
        }
    }

    @Test
    fun repeatAndExtraSelectWithoutConsumingNormalCursor() {
        val cursor = SequenceEngine.SequenceCursor.atWorkout(b)

        val repeat = SequenceEngine.select(
            workouts(),
            cursor,
            SequenceEngine.SelectionKind.REPEAT,
            workoutId = a,
        )
        val extra = SequenceEngine.select(
            workouts(),
            cursor,
            SequenceEngine.SelectionKind.EXTRA,
        )
        val repeatCursor = SequenceEngine.advance(
            workouts(),
            cursor,
            SequenceEngine.AdvanceEvent(
                id = "repeat",
                outcome = SequenceEngine.Outcome.COMPLETED,
                kind = SequenceEngine.SelectionKind.REPEAT,
                workoutId = a,
            ),
        )

        assertEquals(a, selected(repeat).workout.id)
        assertEquals(b, selected(extra).workout.id)
        assertEquals(cursor, repeatCursor)
    }

    @Test
    fun indexCursorWrapsAndExplicitlyMissingRepeatIsReported() {
        val selected = SequenceEngine.select(
            workouts(),
            SequenceEngine.SequenceCursor.atIndex(-1),
        )
        val missing = SequenceEngine.select(
            workouts(),
            SequenceEngine.SequenceCursor.atWorkout(a),
            SequenceEngine.SelectionKind.REPEAT,
            workoutId = WorkoutTemplateId("gone"),
        )

        assertEquals(c, selected(selected).workout.id)
        assertTrue(missing is SequenceEngine.SelectionResult.RequestedWorkoutUnavailable)
    }

    private fun selected(result: SequenceEngine.SelectionResult): SequenceEngine.Selection =
        (result as SequenceEngine.SelectionResult.Selected).value

    private fun workouts(): List<WorkoutTemplate> = listOf(
        workout(a, 0),
        workout(b, 1),
        workout(c, 2),
    )

    private fun sequenceIds(workouts: List<WorkoutTemplate>): List<WorkoutTemplateId> =
        workouts.filterNot { it.isArchived }
            .sortedWith(compareBy<WorkoutTemplate> { it.position }.thenBy { it.id.value })
            .map { it.id }

    private fun workout(
        id: WorkoutTemplateId,
        position: Int,
        archived: Boolean = false,
    ) = WorkoutTemplate(
        id = id,
        planId = com.vitorkrichter.ordo.domain.model.TrainingPlanId("plan"),
        code = id.value.uppercase(),
        name = "Workout ${id.value}",
        position = position,
        isArchived = archived,
        createdAt = now,
        updatedAt = now,
    )
}
