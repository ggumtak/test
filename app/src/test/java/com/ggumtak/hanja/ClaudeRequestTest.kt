package com.ggumtak.hanja

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaudeRequestTest {
    private val user = JSONArray().put(JSONObject().put("role", "user").put("content", "x"))

    @Test
    fun lowEffortTurnsThinkingOffWhereTheModelAllowsIt() {
        assertEquals("disabled", ClaudeRequest.thinking("claude-haiku-5-5", "low").getString("type"))
        assertEquals("between_tools", ClaudeRequest.thinking("claude-sonnet-5-5", "low").getString("type"))
        // Opus 5.5 rejects disabled thinking at every effort: it stays adaptive.
        assertEquals("adaptive", ClaudeRequest.thinking("claude-opus-5-5", "low").getString("type"))
        for (m in ClaudeClient.CHAT_MODELS.map { it.first }) {
            assertEquals("adaptive", ClaudeRequest.thinking(m, "medium").getString("type"))
            assertEquals("adaptive", ClaudeRequest.thinking(m, "high").getString("type"))
            // Only the type: between_tools takes no other field.
            assertEquals(1, ClaudeRequest.thinking(m, "low").length())
        }
    }

    @Test
    fun structuredRequestsCacheTheSystemPromptOnly() {
        val schema = JSONObject().put("type", "object")
        val b = ClaudeRequest.body("claude-haiku-5-5", "sys", user, "low", 1024, false, schema, false, false)
        assertEquals("disabled", b.getJSONObject("thinking").getString("type"))
        val out = b.getJSONObject("output_config")
        assertEquals("low", out.getString("effort"))
        assertEquals("json_schema", out.getJSONObject("format").getString("type"))
        val sys = b.getJSONArray("system").getJSONObject(0)
        assertEquals("sys", sys.getString("text"))
        assertEquals("ephemeral", sys.getJSONObject("cache_control").getString("type"))
        // A one-off question ends in unique text: no automatic breakpoint, no stream, no fallback.
        assertFalse(b.has("cache_control"))
        assertFalse(b.has("stream"))
        assertFalse(b.has("fallbacks"))
        assertFalse(ClaudeRequest.fallback("claude-haiku-5-5"))
    }

    @Test
    fun chatStreamsWithFallbackAndCachesTheConversation() {
        val b = ClaudeRequest.body("claude-sonnet-5-5", "sys", user, "medium", 4096, true, null, true, true)
        assertTrue(b.getBoolean("stream"))
        assertEquals("default", b.getString("fallbacks"))
        assertEquals("ephemeral", b.getJSONObject("cache_control").getString("type"))
        assertFalse(b.getJSONObject("output_config").has("format"))
        assertTrue(ClaudeRequest.fallback("claude-sonnet-5-5"))
        assertTrue(ClaudeRequest.fallback("claude-opus-5-5"))
    }

    @Test
    fun plainRequestIsTheEarlierShape() {
        val b = ClaudeRequest.body("claude-sonnet-5-5", "sys", user, "low", 4096, true, null, true, true, plain = true)
        assertEquals("adaptive", b.getJSONObject("thinking").getString("type"))
        assertEquals("sys", b.getString("system"))
        assertFalse(b.has("cache_control"))
        assertTrue(ClaudeRequest.rejectsSpeedSettings(
            """{"type":"error","error":{"type":"invalid_request_error","message":"thinking.type: Input tag 'between_tools' found"}}"""))
        assertTrue(ClaudeRequest.rejectsSpeedSettings("""{"error":{"message":"cache_control: Extra inputs are not permitted"}}"""))
        assertFalse(ClaudeRequest.rejectsSpeedSettings("""{"error":{"message":"Your credit balance is too low"}}"""))
        assertFalse(ClaudeRequest.rejectsSpeedSettings("""{"error":{"message":"model: claude-x not found"}}"""))
    }
}
