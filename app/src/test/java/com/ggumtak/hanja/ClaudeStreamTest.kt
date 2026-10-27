package com.ggumtak.hanja

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaudeStreamTest {
    @Test
    fun readsTextDeltas() {
        val e = ClaudeStream.parse("""data: {"type":"content_block_delta","index":1,"delta":{"type":"text_delta","text":"気(氣)"}}""")
        assertEquals(ClaudeStream.Event.Text("気(氣)"), e)
    }

    @Test
    fun ignoresThinkingPingsAndEventLines() {
        assertNull(ClaudeStream.parse("""data: {"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"x"}}"""))
        assertNull(ClaudeStream.parse("""data: {"type":"ping"}"""))
        assertNull(ClaudeStream.parse("event: content_block_delta"))
        assertNull(ClaudeStream.parse(""))
        assertNull(ClaudeStream.parse("data: not json"))
    }

    @Test
    fun readsStopReasonAndErrors() {
        assertEquals(ClaudeStream.Event.Stop("max_tokens"),
            ClaudeStream.parse("""data: {"type":"message_delta","delta":{"stop_reason":"max_tokens"},"usage":{"output_tokens":5}}"""))
        val err = ClaudeStream.parse("""data: {"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}""")
        assertTrue((err as ClaudeStream.Event.Error).message.contains("바쁩니다"))
    }

    @Test
    fun explainsStopsAndHttpErrors() {
        assertNull(ClaudeStream.stopMessage("end_turn"))
        assertNull(ClaudeStream.stopMessage(null))
        assertTrue(ClaudeStream.stopMessage("max_tokens")!!.contains("끊겼"))
        assertTrue(ClaudeStream.stopMessage("refusal")!!.contains("답하지"))
        assertTrue(ClaudeStream.httpError(401, """{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}""").contains("API 키"))
        assertTrue(ClaudeStream.httpError(429, null).contains("잠시 후"))
        assertTrue(ClaudeStream.httpError(500, "<html>").contains("서버"))
        assertTrue(ClaudeStream.httpError(400, """{"error":{"type":"invalid_request_error","message":"Your credit balance is too low"}}""").contains("크레딧"))
    }
}
