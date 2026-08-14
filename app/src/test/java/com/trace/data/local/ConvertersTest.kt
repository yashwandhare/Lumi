package com.trace.data.local

import com.trace.core.model.AuditOutcome
import com.trace.core.model.TriggerType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The embedding blob encoding is the piece most likely to be subtly wrong — a byte-order or
 * element-count mistake would not crash, it would silently return garbage similarity scores
 * and make retrieval look merely bad rather than broken.
 */
class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `embedding survives a round trip through the blob encoding`() {
        val original = floatArrayOf(0f, 1f, -1f, 0.5f, -0.25f, 3.4028235e38f, 1.4e-45f)

        val restored = converters.toFloatArray(converters.fromFloatArray(original))

        assertArrayEquals(original, restored, 0f)
    }

    @Test
    fun `an empty embedding round trips to an empty array, not to null`() {
        val restored = converters.toFloatArray(converters.fromFloatArray(floatArrayOf()))

        assertEquals(0, restored?.size)
    }

    @Test
    fun `a null embedding stays null`() {
        assertNull(converters.fromFloatArray(null))
        assertNull(converters.toFloatArray(null))
    }

    @Test
    fun `enums are stored as names so reordering them cannot corrupt existing rows`() {
        assertEquals("BATTERY", converters.fromTriggerType(TriggerType.BATTERY))
        assertEquals(TriggerType.BATTERY, converters.toTriggerType("BATTERY"))
    }

    @Test
    fun `an unrecognised enum name decodes to the safe default instead of throwing`() {
        // A row written by a newer build must not crash an older one mid-session.
        assertEquals(AuditOutcome.FAILURE, converters.toAuditOutcome("NOT_A_REAL_OUTCOME"))
        assertEquals(TriggerType.TIME, converters.toTriggerType(""))
    }
}
