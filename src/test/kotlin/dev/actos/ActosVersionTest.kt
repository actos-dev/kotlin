package dev.actos

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ActosVersionTest {
    @Test
    fun `version should match expected version`() {
        assertEquals("0.1.0", ActosVersion.VERSION)
    }
}
