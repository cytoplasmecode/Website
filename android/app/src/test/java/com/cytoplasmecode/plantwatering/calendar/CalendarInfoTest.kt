package com.cytoplasmecode.plantwatering.calendar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarInfoTest {

    @Test
    fun `owner role is writable`() {
        assertTrue(info("owner").isWritable)
    }

    @Test
    fun `writer role is writable`() {
        assertTrue(info("writer").isWritable)
    }

    @Test
    fun `reader role is not writable`() {
        assertFalse(info("reader").isWritable)
    }

    @Test
    fun `freeBusyReader role is not writable`() {
        assertFalse(info("freeBusyReader").isWritable)
    }

    @Test
    fun `unknown role is not writable`() {
        assertFalse(info("unknown").isWritable)
    }

    private fun info(role: String) = CalendarInfo(
        id = "cal_id",
        name = "My Calendar",
        colorHex = "#4ADE80",
        accessRole = role,
    )
}
