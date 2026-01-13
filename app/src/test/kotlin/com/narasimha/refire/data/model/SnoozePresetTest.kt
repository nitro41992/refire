package com.narasimha.refire.data.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

class SnoozePresetTest {

    @Test
    fun `FixedDuration calculates end time correctly`() {
        val preset = SnoozePreset.FixedDuration("30 min", Duration.ofMinutes(30))
        val now = LocalDateTime.of(2024, 1, 15, 10, 0)

        val endTime = preset.calculateEndTime(now)

        assertEquals(LocalDateTime.of(2024, 1, 15, 10, 30), endTime)
    }

    @Test
    fun `TimeOfDay moves to next day when time has passed`() {
        val preset = SnoozePreset.TimeOfDay("Tonight", LocalTime.of(20, 0))
        val now = LocalDateTime.of(2024, 1, 15, 21, 0)

        val endTime = preset.calculateEndTime(now)

        assertEquals(LocalDateTime.of(2024, 1, 16, 20, 0), endTime)
    }

    @Test
    fun `TimeOfDay stays same day when time has not passed`() {
        val preset = SnoozePreset.TimeOfDay("Tonight", LocalTime.of(20, 0))
        val now = LocalDateTime.of(2024, 1, 15, 15, 0)

        val endTime = preset.calculateEndTime(now)

        assertEquals(LocalDateTime.of(2024, 1, 15, 20, 0), endTime)
    }

    @Test
    fun `Custom preset returns exact time`() {
        val customTime = LocalDateTime.of(2024, 1, 20, 14, 30)
        val preset = SnoozePreset.Custom(customTime)

        assertEquals(customTime, preset.calculateEndTime())
    }

    @Test
    fun `defaults returns expected presets`() {
        val defaults = SnoozePreset.defaults()

        assertEquals(6, defaults.size)
        assertEquals("30 min", defaults[0].displayLabel)
        assertEquals("Tomorrow", defaults[5].displayLabel)
    }
}
