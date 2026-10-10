package com.vitorkrichter.ordo.data.importworkout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GymAppImportUserFormatTest {
    private val parser = GymAppImportParser()

    @Test
    fun acceptsLongDashExerciseSeparator() {
        val result = parser.parse(
            """
            # GYMAPP_IMPORT_V1

            [TREINO A]
            NOME=Pernas

            EXERCICIO=Agachamento no Rack
            SERIES=3
            REPETICOES=8-12
            DESCANSO_SERIES=90s
            DESCANSO_PROXIMO=120s
            ACOMPANHAMENTO=CARGA_REPETICOES
            -------------------------------

            EXERCICIO=Leg Press
            SERIES=3
            REPETICOES=10-12
            DESCANSO_SERIES=90s
            DESCANSO_PROXIMO=0s
            ACOMPANHAMENTO=CARGA_REPETICOES
            -------------------------------
            """.trimIndent(),
        )

        assertTrue(result.errors.joinToString(), result.canSave)
        assertEquals(1, result.workouts.size)
        assertEquals(2, result.exerciseCount)
    }
}
