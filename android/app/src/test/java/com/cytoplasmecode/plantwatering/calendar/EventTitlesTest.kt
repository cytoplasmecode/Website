package com.cytoplasmecode.plantwatering.calendar

import org.junit.Assert.assertEquals
import org.junit.Test

class EventTitlesTest {

    // --- pendingEventTitle ---

    @Test
    fun `pendingEventTitle produces Water prefix`() {
        assertEquals("Water Basil", pendingEventTitle("Basil"))
    }

    @Test
    fun `pendingEventTitle works with multi-word plant names`() {
        assertEquals("Water Snake Plant", pendingEventTitle("Snake Plant"))
    }

    @Test
    fun `pendingEventTitle preserves original casing`() {
        assertEquals("Water fiddle-leaf fig", pendingEventTitle("fiddle-leaf fig"))
    }

    // --- doneEventTitle ---

    @Test
    fun `doneEventTitle includes user and plant separated correctly`() {
        assertEquals("DONE by Alice - Basil", doneEventTitle("Alice", "Basil"))
    }

    @Test
    fun `doneEventTitle works with different users`() {
        assertEquals("DONE by Bob - Monstera", doneEventTitle("Bob", "Monstera"))
    }

    @Test
    fun `doneEventTitle works with multi-word plant names`() {
        assertEquals("DONE by Alice - Snake Plant", doneEventTitle("Alice", "Snake Plant"))
    }

    @Test
    fun `doneEventTitle works with multi-word user names`() {
        assertEquals("DONE by Alice Smith - Orchid", doneEventTitle("Alice Smith", "Orchid"))
    }

    @Test
    fun `pendingEventTitle and doneEventTitle share the plant name exactly`() {
        val plant = "Fiddle-Leaf Fig"
        val pending = pendingEventTitle(plant)
        val done = doneEventTitle("Alice", plant)
        // The plant name appears verbatim in both
        assert(pending.endsWith(plant))
        assert(done.endsWith(plant))
    }
}
