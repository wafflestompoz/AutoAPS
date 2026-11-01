package io.autoaps.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DisplayFormatterTest {
    private val t0 = 1_000_000_000L

    private fun snapshot(
        mgdl: Double? = 142.0,
        delta: Double? = 4.0,
        temp: TempBasal? = TempBasal(0.5, 63, 22),
    ) = NightscoutSnapshot(mgdl, t0, Trend.FORTY_FIVE_UP, delta, 1.234, 18.4, 0.8, temp, null)

    @Test
    fun `formats fresh mg per dL reading`() {
        val lines = DisplayFormatter.format(snapshot(), t0 + 3 * 60_000, DisplaySettings())
        assertEquals("142 mg/dL  ↗  +4", lines.glucose)
        assertEquals("3 min ago", lines.age)
        assertFalse(lines.isStale)
        assertNull(lines.rangeFlag)
        assertEquals("IOB 1.23 U  ·  COB 18 g", lines.iobCob)
        assertEquals("Temp basal 0.50 U/h (63%) · 22 min left", lines.basal)
    }

    @Test
    fun `formats mmol per L`() {
        val lines = DisplayFormatter.format(snapshot(delta = -9.0), t0, DisplaySettings(units = GlucoseUnit.MMOL))
        assertEquals("7.9 mmol/L  ↗  -0.5", lines.glucose)
        assertEquals("Just now", lines.age)
    }

    @Test
    fun `stale data hides trend and delta`() {
        val lines = DisplayFormatter.format(snapshot(), t0 + 20 * 60_000, DisplaySettings())
        assertTrue(lines.isStale)
        assertEquals("142 mg/dL", lines.glucose)
        assertEquals("STALE: last reading 20 min ago", lines.age)
        assertNull(lines.rangeFlag)
    }

    @Test
    fun `flags low and high`() {
        assertEquals("LOW", DisplayFormatter.format(snapshot(mgdl = 62.0), t0, DisplaySettings()).rangeFlag)
        assertEquals("HIGH", DisplayFormatter.format(snapshot(mgdl = 250.0), t0, DisplaySettings()).rangeFlag)
    }

    @Test
    fun `scheduled basal when no temp`() {
        assertEquals("Basal 0.80 U/h (scheduled)", DisplayFormatter.formatBasal(snapshot(temp = null)))
    }

    @Test
    fun `zero delta`() {
        assertEquals("±0", DisplayFormatter.formatDelta(0.2, GlucoseUnit.MGDL))
        assertEquals("±0.0", DisplayFormatter.formatDelta(0.4, GlucoseUnit.MMOL))
    }
}
