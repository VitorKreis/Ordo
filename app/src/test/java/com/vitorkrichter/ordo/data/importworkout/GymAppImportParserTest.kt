package com.vitorkrichter.ordo.data.importworkout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class GymAppImportParserTest {
    private val parser = GymAppImportParser()

    @Test
    fun parsesValidImportText() {
        val result = parser.parse(
            """
            # GYMAPP_IMPORT_V1

            [TREINO A]
            NOME=Peito, ombro e biceps

            EXERCICIO=Supino reto
            SERIES=4
            REPETICOES=8-10
            DESCANSO_SERIES=01:30
            ACOMPANHAMENTO=CARGA_REPETICOES
            ---

            [TREINO B]
            NOME=Costas

            EXERCICIO=Puxada frontal
            SERIES=3
            REPETICOES=10,10,8
            DESCANSO_SERIES=1m30s
            ---
            """.trimIndent(),
        )

        assertTrue(result.errors.joinToString(), result.canSave)
        assertEquals(2, result.workouts.size)
        assertEquals(2, result.exerciseCount)
        assertEquals(90, result.workouts.first().exercises.first().restBetweenSetsSeconds)
    }

    @Test
    fun rejectsRepetitionListWithWrongSeriesCount() {
        val result = parser.parse(
            """
            # GYMAPP_IMPORT_V1

            [TREINO A]
            NOME=Peito

            EXERCICIO=Supino reto
            SERIES=4
            REPETICOES=12,10,8
            DESCANSO_SERIES=90s
            ---
            """.trimIndent(),
        )

        assertFalse(result.canSave)
        assertTrue(result.errors.any { it.contains("incompativel", ignoreCase = true) })
    }

    @Test
    fun parsesPlanNameAndTrainingWeekdays() {
        val result = parser.parse(
            """
            # GYMAPP_IMPORT_V1

            NOME_PLANO=Forca duas vezes
            DIAS_TREINO=segunda, quinta-feira

            [TREINO A]
            NOME=Corpo inteiro

            EXERCICIO=Agachamento
            SERIES=3
            REPETICOES=8
            DESCANSO_SERIES=90s
            ---
            """.trimIndent(),
        )

        assertTrue(result.errors.joinToString(), result.canSave)
        assertEquals("Forca duas vezes", result.planName)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), result.trainingWeekdays)
    }

    @Test
    fun rejectsUnknownTrainingWeekday() {
        val result = parser.parse(
            """
            # GYMAPP_IMPORT_V1
            NOME_PLANO=Plano invalido
            DIAS_TREINO=SEGUNDA,FERIADO

            [TREINO A]
            NOME=Pernas
            EXERCICIO=Agachamento
            SERIES=3
            REPETICOES=8
            DESCANSO_SERIES=90s
            ---
            """.trimIndent(),
        )

        assertFalse(result.canSave)
        assertTrue(result.errors.any { it.contains("FERIADO") })
    }

    @Test
    fun rejectsContentBeforeHeaderInsteadOfAcceptingLateHeader() {
        val result = parser.parse(
            """
            NOME_PLANO=Plano tardio
            # GYMAPP_IMPORT_V1

            [TREINO A]
            NOME=Peito
            EXERCICIO=Supino
            SERIES=3
            REPETICOES=8
            DESCANSO_SERIES=90s
            """.trimIndent(),
        )

        assertFalse(result.canSave)
        assertTrue(result.errors.any { it.contains("primeira linha util", ignoreCase = true) })
    }

    @Test
    fun rejectsArbitraryBracketLineAsWorkoutHeader() {
        val result = parser.parse(
            """
            # GYMAPP_IMPORT_V1

            [x]
            NOME=Pernas
            EXERCICIO=Agachamento
            SERIES=3
            REPETICOES=8
            DESCANSO_SERIES=90s
            """.trimIndent(),
        )

        assertFalse(result.canSave)
        assertTrue(result.workouts.isEmpty())
        assertTrue(result.errors.any { it.contains("cabecalho de treino invalido", ignoreCase = true) })
    }

    @Test
    fun rejectsPartialOptionalDurationInsteadOfUsingTheMatchedPrefix() {
        val result = parser.parse(
            """
            # GYMAPP_IMPORT_V1

            [TREINO A]
            NOME=Peito
            EXERCICIO=Supino
            SERIES=3
            REPETICOES=8
            DESCANSO_SERIES=90s
            DESCANSO_PROXIMO=1m30sx
            """.trimIndent(),
        )

        assertFalse(result.canSave)
        assertTrue(result.errors.any { it.contains("DESCANSO_PROXIMO invalido", ignoreCase = true) })
        assertTrue(result.warnings.none { it.contains("DESCANSO_PROXIMO ausente", ignoreCase = true) })
    }

    @Test
    fun rejectsEmptyRepetitionTokenInsteadOfDroppingIt() {
        val result = parser.parse(
            """
            # GYMAPP_IMPORT_V1

            [TREINO A]
            NOME=Peito
            EXERCICIO=Supino
            SERIES=2
            REPETICOES=10,,10
            DESCANSO_SERIES=90s
            """.trimIndent(),
        )

        assertFalse(result.canSave)
        assertTrue(result.errors.any { it.contains("REPETICOES invalido", ignoreCase = true) })
    }

    @Test
    fun rejectsEmptyWeekdayTokenInsteadOfDroppingIt() {
        val result = parser.parse(
            """
            # GYMAPP_IMPORT_V1
            NOME_PLANO=Plano
            DIAS_TREINO=SEGUNDA,,QUINTA

            [TREINO A]
            NOME=Peito
            EXERCICIO=Supino
            SERIES=3
            REPETICOES=8
            DESCANSO_SERIES=90s
            """.trimIndent(),
        )

        assertFalse(result.canSave)
        assertTrue(result.errors.any { it.contains("token vazio", ignoreCase = true) })
    }
}
