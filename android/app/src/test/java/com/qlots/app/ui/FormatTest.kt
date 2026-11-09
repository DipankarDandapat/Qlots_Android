package com.qlots.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class FormatTest {
    @Test fun negativeAmountsPutTheSignBeforeTheRupeeSymbol() {
        assertEquals("-₹4.56 L", money(-456_000.0, compact = true))
        assertTrue(money(-1500.0).startsWith("-₹1"))
        assertEquals("₹0", money(-0.2))
    }

    @Test fun compactUnitsUseLakhAndCrore() {
        // (Full-number lakh grouping comes from Android's ICU locale data and is verified on-device, not on the desktop JVM.)
        assertTrue(money(3_515_549.0).startsWith("₹3"))
        assertEquals("₹35.16 L", money(3_515_549.0, compact = true))
        assertEquals("₹1.35 Cr", money(13_500_000.0, compact = true))
    }

    @Test fun axisLabelsAreEvenlyFormatted() {
        assertEquals("₹36 L", axisMoney(3_600_000.0))
        assertEquals("₹31 L", axisMoney(3_104_000.0).replace(".0", ""))
        assertEquals("₹1.3 Cr", axisMoney(13_000_000.0))
        assertEquals("₹0", axisMoney(0.0))
    }

    @Test fun datesAreFormattedWithoutTimeZoneDrift() {
        assertEquals("10 Nov 2027", prettyDate("2027-11-10"))
        assertEquals("31 Mar 2028", prettyDate("2028-03-31"))
        assertEquals("—", prettyDate(null))
        assertEquals(epochDay("2026-11-11") - epochDay("2026-11-10"), 1.0, 0.0)
    }

    @Test fun midnightRefreshIsScheduledForTheNextMidnight() {
        val noon = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 10, 12, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        assertEquals(12 * 3_600_000L, millisUntilNextMidnight(noon))
        val justBefore = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 10, 23, 59, 59); set(Calendar.MILLISECOND, 0) }.timeInMillis
        assertTrue(millisUntilNextMidnight(justBefore) in 1_000L..1_001L)
    }

    @Test fun durationsReadNaturally() {
        assertEquals("9 yr 2 mo", durationLabel(110))
        assertEquals("3 yr", durationLabel(36))
        assertEquals("8 mo", durationLabel(8))
    }
}
