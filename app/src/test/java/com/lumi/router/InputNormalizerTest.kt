package com.lumi.router

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Normalization is the first thing every tier sees, so its contract is tested in isolation:
 * typed input, a voice transcript with guessed punctuation spacing, and a pasted string must
 * land on the same representation before any rule or embedding touches them.
 */
class InputNormalizerTest {

    @Test
    fun `trims, lowercases, and collapses whitespace from typed input`() {
        assertEquals("remind me at six", InputNormalizer.normalize("  Remind   Me\nAT six  "))
    }

    @Test
    fun `collapses the pauses a recogniser leaves as repeated spaces`() {
        assertEquals("water the plants", InputNormalizer.normalize("Water the    plants"))
    }

    @Test
    fun `an empty or blank string normalizes to empty`() {
        assertEquals("", InputNormalizer.normalize(""))
        assertEquals("", InputNormalizer.normalize("   \n\t "))
    }
}
