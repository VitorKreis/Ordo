package com.vitorkrichter.ordo.data.importworkout

import com.vitorkrichter.ordo.domain.model.TrackingMode
import java.text.Normalizer
import java.time.DayOfWeek

private const val IMPORT_HEADER = "# GYMAPP_IMPORT_V1"
private val WORKOUT_HEADER_PATTERN = Regex("""^\[TREINO\s+([^\[\]]+)\]$""", RegexOption.IGNORE_CASE)
private val CLOCK_DURATION_PATTERN = Regex("""(\d+):(\d{2})""")
private val UNIT_DURATION_PATTERN = Regex("""(?:(\d+)m)?(?:(\d+)s)?""")

class GymAppImportParser {
    fun parse(text: String): GymAppImportResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val workouts = mutableListOf<ImportedWorkout>()
        var currentWorkout: MutableImportedWorkout? = null
        var currentExercise = MutableImportedExercise()
        var sawHeader = false
        var contentBeforeHeader = false
        var planName: String? = null
        var trainingWeekdays: Set<DayOfWeek>? = null

        text.lineSequence()
            .mapIndexed { index, raw -> index + 1 to raw.trim() }
            .forEach { (lineNumber, line) ->
                when {
                    line.isBlank() -> Unit
                    line.startsWith("#") -> {
                        if (line.equals(IMPORT_HEADER, ignoreCase = true)) {
                            if (sawHeader) {
                                errors += "Linha $lineNumber: cabecalho GYMAPP_IMPORT_V1 duplicado."
                            } else {
                                if (contentBeforeHeader) {
                                    errors += "Linha $lineNumber: cabecalho GYMAPP_IMPORT_V1 deve ser a primeira linha util."
                                }
                                sawHeader = true
                            }
                        }
                    }
                    !sawHeader -> {
                        contentBeforeHeader = true
                        errors += "Linha $lineNumber: esperado o cabecalho $IMPORT_HEADER antes do conteudo."
                    }
                    line.startsWith("[") -> {
                        val headerMatch = WORKOUT_HEADER_PATTERN.matchEntire(line)
                        if (headerMatch == null) {
                            errors += "Linha $lineNumber: cabecalho de treino invalido; use [TREINO <codigo>]."
                        } else {
                            val code = headerMatch.groupValues[1].trim()
                            if (code.isBlank()) {
                                errors += "Linha $lineNumber: cabecalho de treino sem codigo."
                            } else {
                                flushExercise(currentWorkout, currentExercise, errors, warnings)
                                currentExercise = MutableImportedExercise()
                                currentWorkout?.toImported()?.let(workouts::add)
                                currentWorkout = MutableImportedWorkout(code = code)
                            }
                        }
                    }
                    line.matches(Regex("-{3,}")) -> {
                        flushExercise(currentWorkout, currentExercise, errors, warnings)
                        currentExercise = MutableImportedExercise()
                    }
                    "=" in line -> {
                        val key = line.substringBefore("=").trim().uppercase()
                        val value = line.substringAfter("=").trim()
                        when (key) {
                            "NOME_PLANO" -> {
                                if (currentWorkout != null) {
                                    errors += "Linha $lineNumber: NOME_PLANO deve aparecer antes do primeiro treino."
                                } else if (value.isBlank()) {
                                    errors += "Linha $lineNumber: NOME_PLANO nao pode ficar vazio."
                                } else {
                                    planName = value
                                }
                            }
                            "DIAS_TREINO" -> {
                                if (currentWorkout != null) {
                                    errors += "Linha $lineNumber: DIAS_TREINO deve aparecer antes do primeiro treino."
                                } else {
                                    trainingWeekdays = parseWeekdays(value, lineNumber, errors)
                                }
                            }
                            "NOME" -> {
                                if (currentWorkout == null) {
                                    errors += "Linha $lineNumber: NOME apareceu antes de um treino."
                                } else {
                                    currentWorkout.name = value
                                }
                            }
                            "EXERCICIO" -> currentExercise.name = value
                            "SERIES" -> currentExercise.series = value.toIntOrNull()
                            "REPETICOES" -> currentExercise.repetitions = value
                            "DESCANSO_SERIES" -> currentExercise.restBetweenSetsSeconds = parseDuration(value)
                            "DESCANSO_PROXIMO" -> {
                                currentExercise.transitionRestSpecified = true
                                currentExercise.transitionRestSeconds = parseDuration(value)
                            }
                            "ACOMPANHAMENTO" -> currentExercise.trackingMode = parseTrackingMode(value)
                                ?: run {
                                    errors += "Linha $lineNumber: acompanhamento desconhecido: $value."
                                    null
                                }
                            "CARGA_SUGERIDA" -> currentExercise.suggestedLoadKg = value
                                .replace(",", ".")
                                .toDoubleOrNull()
                                ?.takeIf { it > 0.0 }
                                ?: run {
                                    errors += "Linha $lineNumber: carga sugerida invalida."
                                    null
                                }
                            "OBSERVACAO" -> currentExercise.notes = value
                            else -> errors += "Linha $lineNumber: campo desconhecido: $key."
                        }
                    }
                    else -> errors += "Linha $lineNumber: texto fora do formato V1."
                }
            }

        flushExercise(currentWorkout, currentExercise, errors, warnings)
        currentWorkout?.toImported()?.let(workouts::add)

        if (!sawHeader) {
            errors += "A primeira linha util deve ser # GYMAPP_IMPORT_V1."
        }
        if (workouts.isEmpty()) {
            errors += "Nenhum treino foi encontrado."
        }
        if (planName == null) {
            warnings += "NOME_PLANO ausente; informe o nome na tela antes de salvar."
        }
        if (trainingWeekdays == null) {
            warnings += "DIAS_TREINO ausente; o app usara os dias escolhidos anteriormente."
        }

        workouts.groupBy { it.code.uppercase() }
            .filterValues { it.size > 1 }
            .keys
            .forEach { errors += "Treino duplicado: $it." }

        workouts.forEach { workout ->
            if (workout.name.isBlank()) {
                errors += "Treino ${workout.code} esta sem NOME."
            }
            if (workout.exercises.isEmpty()) {
                errors += "Treino ${workout.code} nao possui exercicios."
            }
        }

        return GymAppImportResult(
            workouts = workouts,
            planName = planName,
            trainingWeekdays = trainingWeekdays,
            errors = errors.distinct(),
            warnings = warnings.distinct(),
        )
    }

    private fun flushExercise(
        workout: MutableImportedWorkout?,
        exercise: MutableImportedExercise,
        errors: MutableList<String>,
        warnings: MutableList<String>,
    ) {
        if (exercise.isBlank()) return
        if (workout == null) {
            errors += "Exercicio encontrado antes de um cabecalho [TREINO ...]."
            return
        }

        val name = exercise.name.orEmpty().trim()
        val series = exercise.series
        val repetitions = exercise.repetitions.orEmpty().trim()
        val rest = exercise.restBetweenSetsSeconds

        if (name.isBlank()) errors += "Treino ${workout.code}: exercicio sem nome."
        if (series == null || series <= 0) errors += "Treino ${workout.code}, $name: SERIES invalido."
        if (repetitions.isBlank()) errors += "Treino ${workout.code}, $name: REPETICOES ausente."
        if (rest == null || rest < 0) errors += "Treino ${workout.code}, $name: DESCANSO_SERIES invalido."

        val repetitionTargets = parseRepetitions(repetitions, series ?: 0)
        if (repetitionTargets == null) {
            errors += "Treino ${workout.code}, $name: REPETICOES invalido ou incompativel com SERIES."
            return
        }

        val transition = exercise.transitionRestSeconds ?: 0
        if (exercise.transitionRestSeconds == null) {
            if (exercise.transitionRestSpecified) {
                errors += "Treino ${workout.code}, $name: DESCANSO_PROXIMO invalido."
            } else {
                warnings += "Treino ${workout.code}, $name: DESCANSO_PROXIMO ausente, usando 0s."
            }
        }
        val tracking = exercise.trackingMode ?: TrackingMode.NONE
        if (exercise.trackingMode == null) {
            warnings += "Treino ${workout.code}, $name: ACOMPANHAMENTO ausente, usando NENHUM."
        }

        if (name.isNotBlank() && series != null && series > 0 && rest != null && rest >= 0) {
            workout.exercises += ImportedExercise(
                name = name,
                series = series,
                repetitionTargets = repetitionTargets,
                restBetweenSetsSeconds = rest,
                transitionRestSeconds = transition,
                trackingMode = tracking,
                suggestedLoadKg = exercise.suggestedLoadKg,
                notes = exercise.notes?.takeIf { it.isNotBlank() },
            )
        }
    }

    private fun parseRepetitions(value: String, series: Int): List<ImportedRepetitionTarget>? {
        if (series <= 0) return null
        val normalizedValue = value.trim()
        if (normalizedValue.isBlank() || normalizedValue.first() == ',' || normalizedValue.last() == ',') {
            return null
        }
        val list = normalizedValue.split(",").map { it.trim() }
        if (list.any { it.isBlank() }) return null
        return if (list.size > 1) {
            if (list.size != series) return null
            list.map { parseSingleRepetitionTarget(it) ?: return null }
        } else {
            val target = parseSingleRepetitionTarget(normalizedValue) ?: return null
            List(series) { target }
        }
    }

    private fun parseSingleRepetitionTarget(value: String): ImportedRepetitionTarget? {
        val normalized = value.replace(" ", "")
        return if ("-" in normalized) {
            val min = normalized.substringBefore("-").toIntOrNull()
            val max = normalized.substringAfter("-").toIntOrNull()
            if (min == null || max == null || min <= 0 || max < min) return null
            ImportedRepetitionTarget(minimum = min, maximum = max, target = null)
        } else {
            val target = normalized.toIntOrNull()?.takeIf { it > 0 } ?: return null
            ImportedRepetitionTarget(minimum = target, maximum = target, target = target)
        }
    }

    private fun parseDuration(value: String): Int? {
        val normalized = value.trim().lowercase()
        if (normalized.isBlank() || normalized.startsWith("-")) return null

        normalized.toIntOrNull()?.takeIf { it >= 0 }?.let { return it }

        CLOCK_DURATION_PATTERN.matchEntire(normalized)?.let { match ->
            val minutes = match.groupValues[1].toLongOrNull() ?: return null
            val seconds = match.groupValues[2].toLongOrNull() ?: return null
            if (seconds >= 60) return null
            return durationInSeconds(minutes, seconds)
        }

        UNIT_DURATION_PATTERN.matchEntire(normalized)?.let { match ->
            val minuteToken = match.groupValues[1]
            val secondToken = match.groupValues[2]
            if (minuteToken.isBlank() && secondToken.isBlank()) return null

            val minutes = minuteToken.toLongOrNull() ?: 0L
            val seconds = secondToken.toLongOrNull() ?: 0L
            if (minuteToken.isNotBlank() && seconds >= 60) return null
            return durationInSeconds(minutes, seconds)
        }

        return null
    }

    private fun durationInSeconds(minutes: Long, seconds: Long): Int? {
        if (minutes < 0 || seconds < 0 || seconds > Int.MAX_VALUE) return null
        if (minutes > (Int.MAX_VALUE.toLong() - seconds) / 60) return null
        val total = minutes * 60 + seconds
        return total.toInt()
    }

    private fun parseTrackingMode(value: String): TrackingMode? = when (value.trim().uppercase()) {
        "NENHUM" -> TrackingMode.NONE
        "REPETICOES" -> TrackingMode.REPETITIONS
        "CARGA_REPETICOES" -> TrackingMode.LOAD_AND_REPETITIONS
        else -> null
    }

    private fun parseWeekdays(
        value: String,
        lineNumber: Int,
        errors: MutableList<String>,
    ): Set<DayOfWeek>? {
        val normalizedValue = value.trim()
        val tokens = normalizedValue.split(',', ';').map(String::trim)
        if (normalizedValue.isBlank() ||
            normalizedValue.first() in charArrayOf(',', ';') ||
            normalizedValue.last() in charArrayOf(',', ';') ||
            tokens.any(String::isBlank)
        ) {
            errors += "Linha $lineNumber: DIAS_TREINO possui token vazio; informe dias separados por virgula ou ponto e virgula."
            return null
        }
        val parsed = tokens.map { token ->
            parseWeekday(token) ?: run {
                errors += "Linha $lineNumber: dia de treino desconhecido: $token."
                return null
            }
        }.toSet()
        return parsed.takeIf { it.isNotEmpty() }
    }

    private fun parseWeekday(value: String): DayOfWeek? {
        val normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .trim()
            .uppercase()
        return when (normalized) {
            "SEG", "SEGUNDA", "SEGUNDA-FEIRA" -> DayOfWeek.MONDAY
            "TER", "TERCA", "TERCA-FEIRA" -> DayOfWeek.TUESDAY
            "QUA", "QUARTA", "QUARTA-FEIRA" -> DayOfWeek.WEDNESDAY
            "QUI", "QUINTA", "QUINTA-FEIRA" -> DayOfWeek.THURSDAY
            "SEX", "SEXTA", "SEXTA-FEIRA" -> DayOfWeek.FRIDAY
            "SAB", "SABADO" -> DayOfWeek.SATURDAY
            "DOM", "DOMINGO" -> DayOfWeek.SUNDAY
            else -> null
        }
    }
}

data class GymAppImportResult(
    val workouts: List<ImportedWorkout>,
    val planName: String?,
    val trainingWeekdays: Set<DayOfWeek>?,
    val errors: List<String>,
    val warnings: List<String>,
) {
    val canSave: Boolean get() = errors.isEmpty() && workouts.isNotEmpty()
    val exerciseCount: Int get() = workouts.sumOf { it.exercises.size }
}

data class ImportedWorkout(
    val code: String,
    val name: String,
    val exercises: List<ImportedExercise>,
)

data class ImportedExercise(
    val name: String,
    val series: Int,
    val repetitionTargets: List<ImportedRepetitionTarget>,
    val restBetweenSetsSeconds: Int,
    val transitionRestSeconds: Int,
    val trackingMode: TrackingMode,
    val suggestedLoadKg: Double?,
    val notes: String?,
)

data class ImportedRepetitionTarget(
    val minimum: Int?,
    val maximum: Int?,
    val target: Int?,
)

private data class MutableImportedWorkout(
    val code: String,
    var name: String = "",
    val exercises: MutableList<ImportedExercise> = mutableListOf(),
) {
    fun toImported() = ImportedWorkout(code = code, name = name, exercises = exercises.toList())
}

private data class MutableImportedExercise(
    var name: String? = null,
    var series: Int? = null,
    var repetitions: String? = null,
    var restBetweenSetsSeconds: Int? = null,
    var transitionRestSeconds: Int? = null,
    var transitionRestSpecified: Boolean = false,
    var trackingMode: TrackingMode? = null,
    var suggestedLoadKg: Double? = null,
    var notes: String? = null,
) {
    fun isBlank(): Boolean {
        return name == null &&
            series == null &&
            repetitions == null &&
            restBetweenSetsSeconds == null &&
            transitionRestSeconds == null &&
            !transitionRestSpecified &&
            trackingMode == null &&
            suggestedLoadKg == null &&
            notes == null
    }
}
