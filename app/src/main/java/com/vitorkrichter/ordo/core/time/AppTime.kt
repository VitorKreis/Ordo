package com.vitorkrichter.ordo.core.time

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Single application time zone used for calendar-day and user-facing date calculations. */
object AppTime {
    val zoneId: ZoneId = ZoneId.of("America/Sao_Paulo")
    val clock: Clock = Clock.system(zoneId)

    fun now(): Instant = Instant.now(clock)

    fun today(): LocalDate = LocalDate.now(clock)
}
