package com.vitorkrichter.ordo

import com.vitorkrichter.ordo.core.database.OrdoDatabase
import org.junit.Assert.assertEquals
import org.junit.Test

class ArchitectureSmokeTest {
    @Test
    fun databaseUsesCurrentSchemaVersion() {
        assertEquals(6, OrdoDatabase.VERSION)
    }
}
