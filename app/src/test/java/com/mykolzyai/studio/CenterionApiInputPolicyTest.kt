package com.mykolzyai.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class CenterionApiInputPolicyTest {
    @Test fun acceptsOnlyHttpsOriginUrls() {
        assertTrue(CenterionApiInputPolicy.isSecureBaseUrl("https://example.supabase.co"))
        assertTrue(CenterionApiInputPolicy.isSecureBaseUrl("https://example.supabase.co/"))
        assertFalse(CenterionApiInputPolicy.isSecureBaseUrl("http://example.supabase.co"))
        assertFalse(CenterionApiInputPolicy.isSecureBaseUrl("https://user:pass@example.supabase.co"))
        assertFalse(CenterionApiInputPolicy.isSecureBaseUrl("https://example.supabase.co/functions/v1"))
        assertFalse(CenterionApiInputPolicy.isSecureBaseUrl("https://example.supabase.co?x=1"))
    }

    @Test fun rejectsOversizedAndBlankTextInsteadOfTruncating() {
        assertEquals("ok", CenterionApiInputPolicy.requiredText(" ok ", 2, "test"))
        assertThrows(IllegalArgumentException::class.java) {
            CenterionApiInputPolicy.requiredText(" ", 10, "test")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CenterionApiInputPolicy.requiredText("x".repeat(13), 12, "test")
        }
    }

    @Test fun validatesUuidPathSegments() {
        val id = "123e4567-e89b-12d3-a456-426614174000"
        assertEquals(id, CenterionApiInputPolicy.requiredUuid(id, "test"))
        assertNull(CenterionApiInputPolicy.optionalUuid("  ", "test"))
        assertThrows(IllegalArgumentException::class.java) {
            CenterionApiInputPolicy.requiredUuid("../settings", "test")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CenterionApiInputPolicy.optionalUuid("abc?admin=true", "test")
        }
    }

    @Test fun validatesFeedbackRatingAndComment() {
        CenterionApiInputPolicy.validateRating(1)
        CenterionApiInputPolicy.validateRating(5)
        assertEquals("ok", CenterionApiInputPolicy.optionalComment(" ok "))
        assertNull(CenterionApiInputPolicy.optionalComment(" "))
        assertThrows(IllegalArgumentException::class.java) {
            CenterionApiInputPolicy.validateRating(0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CenterionApiInputPolicy.optionalComment("x".repeat(2_001))
        }
    }
}
