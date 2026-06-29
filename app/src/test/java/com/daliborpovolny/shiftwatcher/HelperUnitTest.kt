package com.daliborpovolny.shiftwatcher

import org.junit.Assert.assertEquals
import org.junit.Test

class HelperUnitTest {

    @Test
    fun formatTime_isCorrect() {
        assertEquals("00:00", formatTime(0))
        assertEquals("00:05", formatTime(5))
        assertEquals("00:59", formatTime(59))
        assertEquals("01:00", formatTime(60))
        assertEquals("01:01", formatTime(61))
        assertEquals("59:59", formatTime(3599))
        assertEquals("100:00", formatTime(6000))
    }

    @Test
    fun screenTypeToCzechName_isCorrect() {
        assertEquals("Ostatní", ScreenTypeToCzechName(ScreenType.Other))
        assertEquals("Eskalace", ScreenTypeToCzechName(ScreenType.Escalation))
        assertEquals("Info", ScreenTypeToCzechName(ScreenType.Info))
    }

    @Test
    fun normalizePhoneNumber_isCorrect() {
        assertEquals("777888999", WatcherService.normalizePhoneNumber("+420 777 888 999"))
        assertEquals("777888999", WatcherService.normalizePhoneNumber("777-888-999"))
        assertEquals("777888999", WatcherService.normalizePhoneNumber("(777) 888 999"))
        assertEquals("777888999", WatcherService.normalizePhoneNumber("777888999"))
        assertEquals("12345", WatcherService.normalizePhoneNumber("12345"))
        assertEquals("", WatcherService.normalizePhoneNumber(""))
    }
}
