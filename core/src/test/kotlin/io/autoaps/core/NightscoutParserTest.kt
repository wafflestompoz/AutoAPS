package io.autoaps.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NightscoutParserTest {
    private fun fixture(name: String) = javaClass.getResource("/$name")!!.readText()
    private val readingTime = 1761998400000L

    @Test
    fun `parses AAPS properties with active temp basal`() {
        val now = readingTime + 60_000
        val s = NightscoutParser.parseProperties(fixture("properties_aaps.json"), now)
        assertEquals(142.0, s.glucoseMgdl)
        assertEquals(readingTime, s.readingTimeMillis)
        assertEquals(Trend.FORTY_FIVE_UP, s.trend)
        assertEquals(4.0, s.deltaMgdl)
        assertEquals(1.234, s.iobUnits)
        assertEquals(18.4, s.cobGrams)
        assertEquals(0.8, s.scheduledBasal)
        val temp = assertNotNull(s.tempBasal)
        assertEquals(0.5, temp.rateUnitsPerHour)
        assertEquals(62, temp.percentOfScheduled)
        // Started 3 min before the reading, 30 min long, now is 1 min after the reading.
        assertEquals(26, temp.remainingMinutes)
    }

    @Test
    fun `no temp basal when no treatment is running`() {
        val s = NightscoutParser.parseProperties(fixture("properties_no_temp.json"), readingTime)
        assertNull(s.tempBasal)
        assertEquals(0.8, s.scheduledBasal)
        assertEquals(-2.0, s.deltaMgdl)
        assertEquals(Trend.FLAT, s.trend)
    }

    @Test
    fun `missing plugins give nulls, not errors`() {
        val s = NightscoutParser.parseProperties("""{"bgnow":{"last":120,"mills":1}}""", 0)
        assertEquals(120.0, s.glucoseMgdl)
        assertNull(s.iobUnits)
        assertNull(s.cobGrams)
        assertNull(s.tempBasal)
        assertEquals(Trend.UNKNOWN, s.trend)
    }

    @Test
    fun `parses entries fallback with computed delta`() {
        val s = NightscoutParser.parseEntries(fixture("entries.json"))
        assertEquals(182.0, s.glucoseMgdl)
        assertEquals(11.0, s.deltaMgdl)
        assertEquals(Trend.SINGLE_UP, s.trend)
    }

    @Test
    fun `parses status units`() {
        assertEquals(GlucoseUnit.MMOL, NightscoutParser.parseStatusUnits("""{"settings":{"units":"mmol"}}"""))
        assertEquals(GlucoseUnit.MGDL, NightscoutParser.parseStatusUnits("""{"settings":{"units":"mg/dl"}}"""))
        assertNull(NightscoutParser.parseStatusUnits("""{"status":"ok"}"""))
    }
}
