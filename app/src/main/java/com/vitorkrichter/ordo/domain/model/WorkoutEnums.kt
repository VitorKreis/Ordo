package com.vitorkrichter.ordo.domain.model

enum class WorkoutDivisionType {
    FULL_BODY,
    AB,
    ABC,
    ABCD,
    ABCDE,
    PUSH_PULL_LEGS,
    UPPER_LOWER,
    CUSTOM,
}

enum class WorkoutSelectionMode {
    FIXED_BY_WEEKDAY,
    SEQUENTIAL,
}

enum class TrackingMode {
    NONE,
    REPETITIONS,
    LOAD_AND_REPETITIONS,
}

enum class TrainingDayStatus {
    PLANNED,
    IN_PROGRESS,
    COMPLETED,
    PARTIALLY_COMPLETED,
    MISSED,
    CANCELED,
    RESCHEDULED,
}

enum class WorkoutSessionStatus {
    IN_PROGRESS,
    COMPLETED,
    PARTIALLY_COMPLETED,
    CANCELED,
}

enum class SessionExerciseStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED,
    SKIPPED,
}

enum class SessionSetStatus {
    NOT_STARTED,
    COMPLETED,
    SKIPPED,
}
