package com.mykolzyai.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRequestPolicyTest {
    @Test
    fun acceptsOnlyHttpsOriginUrls() {
        assertTrue(ChatRequestPolicy.isSecureBaseUrl("https://api.example.com"))
        assertTrue(ChatRequestPolicy.isSecureBaseUrl("https://api.example.com/"))
        assertFalse(ChatRequestPolicy.isSecureBaseUrl("http://api.example.com"))
        assertFalse(ChatRequestPolicy.isSecureBaseUrl("file:///tmp/chat"))
        assertFalse(ChatRequestPolicy.isSecureBaseUrl("https://user:password@api.example.com"))
        assertFalse(ChatRequestPolicy.isSecureBaseUrl("https://api.example.com/path"))
        assertFalse(ChatRequestPolicy.isSecureBaseUrl("https://api.example.com?token=secret"))
        assertFalse(ChatRequestPolicy.isSecureBaseUrl("not a url"))
    }

    @Test
    fun trimsMessagesAndEnforcesLengthLimit() {
        assertEquals("Привет", ChatRequestPolicy.normalizeMessage("  Привет  "))
        assertEquals(
            ChatRequestPolicy.MAX_MESSAGE_CHARS,
            ChatRequestPolicy.normalizeMessage("x".repeat(ChatRequestPolicy.MAX_MESSAGE_CHARS)).length
        )
        assertFails { ChatRequestPolicy.normalizeMessage("  ") }
        assertFails {
            ChatRequestPolicy.normalizeMessage("x".repeat(ChatRequestPolicy.MAX_MESSAGE_CHARS + 1))
        }
    }

    @Test
    fun acceptsOnlyBoundedSafeConversationIds() {
        assertNull(ChatRequestPolicy.normalizeConversationId(null))
        assertNull(ChatRequestPolicy.normalizeConversationId("  "))
        assertEquals("chat_123-ABC", ChatRequestPolicy.normalizeConversationId(" chat_123-ABC "))
        assertFails { ChatRequestPolicy.normalizeConversationId("chat/123") }
        assertFails { ChatRequestPolicy.normalizeConversationId("x".repeat(129)) }
    }

    @Test
    fun requiresAValidRuntimeAccessToken() {
        assertEquals("short-lived-token", ChatRequestPolicy.normalizeAccessToken(" short-lived-token "))
        assertFails { ChatRequestPolicy.normalizeAccessToken(null) }
        assertFails { ChatRequestPolicy.normalizeAccessToken("  ") }
        assertFails { ChatRequestPolicy.normalizeAccessToken("token\nwith-line-break") }
        assertFails { ChatRequestPolicy.normalizeAccessToken("x".repeat(4_097)) }
    }

    private fun assertFails(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected validation failure.
        }
    }
}
