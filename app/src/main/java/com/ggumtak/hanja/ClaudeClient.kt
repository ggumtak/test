package com.ggumtak.hanja

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Claude Messages API requests over raw HTTP (no libraries). [run] streams, [json] returns structured output.
 * Both block: call them from a background thread; [run]'s onText gets each text piece on that thread.
 * Speed first: thinking is off wherever the model allows it at low effort ([ClaudeRequest.thinking]), the system
 * prompt carries a cache breakpoint, and [warmUp] opens the TLS connection before the first question.
 */
class ClaudeClient(private val apiKey: String, private val model: String = MODEL) {
    @Volatile
    private var conn: HttpURLConnection? = null

    @Volatile
    var cancelled = false
        private set

    fun cancel() {
        cancelled = true
        runCatching { conn?.disconnect() }
    }

    /**
     * A conversation ([messages]: alternating user/assistant text turns) streamed at [effort]. [conversation]:
     * the chat will continue, so the growing history is cached too. Null when the answer finished, otherwise
     * the Korean message to show.
     */
    fun run(system: String, messages: JSONArray, effort: String, conversation: Boolean = false, onText: (String) -> Unit): String? =
        attempt({ it }) { fallback, plain ->
            stream(ClaudeRequest.body(model, system, messages, effort, 4096, true, null, fallback, conversation, plain), fallback, plain, onText)
        }

    /**
     * One non-streamed request at low effort whose answer must follow [schema] (structured outputs). Returns the
     * JSON text, or null with the Korean message to show.
     */
    fun json(system: String, user: String, schema: JSONObject, maxTokens: Int = 1024): Pair<String?, String?> {
        val messages = JSONArray().put(JSONObject().put("role", "user").put("content", user))
        return attempt({ it.second }) { fallback, plain ->
            jsonOnce(ClaudeRequest.body(model, system, messages, "low", maxTokens, false, schema, fallback, false, plain), fallback, plain)
        }
    }

    /**
     * Sends, and asks again once without what the API turned down: the fallback beta (an account without it),
     * or the speed settings (thinking off, cache breakpoints) - then the plain request every earlier build sent,
     * for the rest of this process.
     */
    private inline fun <T> attempt(problem: (T) -> String?, send: (fallback: Boolean, plain: Boolean) -> T): T {
        var fallback = ClaudeRequest.fallback(model)
        var plain = plainOnly
        while (true) {
            val result = send(fallback, plain)
            when (problem(result)) {
                FALLBACK_REJECTED -> fallback = false
                SHAPE_REJECTED -> {
                    plain = true
                    plainOnly = true
                }
                else -> return result
            }
        }
    }

    /** Opens the request; null when cancelled meanwhile. */
    private fun open(body: JSONObject, fallback: Boolean): HttpURLConnection? {
        if (cancelled) return null
        val bytes = body.toString().toByteArray(Charsets.UTF_8)
        val c = URL(ENDPOINT).openConnection() as HttpURLConnection
        conn = c
        if (cancelled) {
            c.disconnect()
            return null
        }
        c.requestMethod = "POST"
        c.connectTimeout = 15_000
        c.readTimeout = 60_000
        c.doOutput = true
        // The body goes out as it is written, without the connection buffering a copy first.
        c.setFixedLengthStreamingMode(bytes.size)
        c.setRequestProperty("content-type", "application/json")
        c.setRequestProperty("x-api-key", apiKey)
        c.setRequestProperty("anthropic-version", "2023-06-01")
        if (fallback) c.setRequestProperty("anthropic-beta", "server-side-fallback-2026-07-01")
        c.outputStream.use { it.write(bytes) }
        return c
    }

    /** The error to show for a non-200 answer, or [FALLBACK_REJECTED] / [SHAPE_REJECTED] to ask again. */
    private fun failure(c: HttpURLConnection, code: Int, fallback: Boolean, plain: Boolean): String {
        val err = runCatching { c.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull()
        if (code == 400 && err != null) {
            if (fallback && err.contains("fallback", ignoreCase = true)) return FALLBACK_REJECTED
            if (!plain && ClaudeRequest.rejectsSpeedSettings(err)) return SHAPE_REJECTED
        }
        return ClaudeStream.httpError(code, err)
    }

    private fun stream(body: JSONObject, fallback: Boolean, plain: Boolean, onText: (String) -> Unit): String? {
        var c: HttpURLConnection? = null
        try {
            c = open(body, fallback) ?: return null
            val code = c.responseCode
            if (code != 200) return failure(c, code, fallback, plain)
            var stop: String? = null
            var finished = false
            BufferedReader(InputStreamReader(c.inputStream, Charsets.UTF_8)).use { r ->
                while (!cancelled) {
                    val line = r.readLine() ?: break
                    when (val e = ClaudeStream.parse(line)) {
                        is ClaudeStream.Event.Text -> onText(e.text)
                        is ClaudeStream.Event.Stop -> stop = e.reason
                        is ClaudeStream.Event.Done -> finished = true
                        is ClaudeStream.Event.Error -> return e.message
                        null -> {}
                    }
                }
            }
            if (cancelled) return null
            if (!finished) return "(연결이 중간에 끊겼습니다)"
            return ClaudeStream.stopMessage(stop)
        } catch (e: Exception) {
            if (cancelled) return null
            return OFFLINE
        } finally {
            conn = null
            // A body read to the end has already gone back to the connection pool; this only frees the rest.
            runCatching { c?.disconnect() }
        }
    }

    private fun jsonOnce(body: JSONObject, fallback: Boolean, plain: Boolean): Pair<String?, String?> {
        var c: HttpURLConnection? = null
        try {
            c = open(body, fallback) ?: return null to null
            val code = c.responseCode
            if (code != 200) return null to failure(c, code, fallback, plain)
            val text = c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (cancelled) return null to null
            return ClaudeStream.messageText(text)
        } catch (e: Exception) {
            if (cancelled) return null to null
            return null to OFFLINE
        } finally {
            conn = null
            runCatching { c?.disconnect() }
        }
    }

    companion object {
        const val ENDPOINT = "https://api.anthropic.com/v1/messages"
        /** Haiku: fast and cheap, for the reading checks; the chat model is a setting (Prefs.chatModel). */
        const val MODEL = "claude-haiku-5-5"
        private const val FALLBACK_REJECTED = "\u0000fallback-rejected"
        private const val SHAPE_REJECTED = "\u0000shape-rejected"

        /** The API turned the speed settings down once: send plain requests until the app restarts. */
        @Volatile
        private var plainOnly = false
        private const val OFFLINE = "연결하지 못했습니다. 인터넷 연결을 확인해 주세요."

        /** The chat / 설명 models offered in the settings: id to label. */
        val CHAT_MODELS = listOf(
            "claude-haiku-5-5" to "Haiku 5.5 — 가장 빠르고 쌈(질문 1개 약 1원)",
            "claude-sonnet-5-5" to "Sonnet 5.5 — 똑똑하고 빠름(약 15~30원)",
            "claude-opus-5-5" to "Opus 5.5 — 가장 똑똑하지만 느림(약 30~60원)",
        )

        @Volatile
        private var warmedAt = 0L

        /**
         * Opens a connection to the API host (DNS, TCP, TLS) on a background thread and leaves it in the
         * platform's connection pool, so the next request starts sending at once. A tiny unauthenticated GET
         * (the API answers 401); at most once every 30 seconds.
         */
        fun warmUp() {
            val now = System.currentTimeMillis()
            if (now - warmedAt < 30_000) return
            warmedAt = now
            Thread {
                runCatching {
                    val c = URL("https://api.anthropic.com/v1/models").openConnection() as HttpURLConnection
                    c.connectTimeout = 10_000
                    c.readTimeout = 10_000
                    // Reading the answer to the end returns the connection to the pool for reuse.
                    val s = if (c.responseCode >= 400) c.errorStream else c.inputStream
                    s?.use { it.readBytes() }
                }
            }.start()
        }
    }
}

/** The request bodies; pure, so the JVM tests cover them. */
object ClaudeRequest {
    /** Sonnet / Opus go out with server-side fallbacks ("default": a safety decline is retried on another model). */
    fun fallback(model: String): Boolean = model != ClaudeClient.MODEL

    /**
     * The fastest thinking setting [model] allows at [effort]. At low effort no thinking at all where the model
     * permits it (Haiku 5.5: disabled; Sonnet 5.5: between_tools, its "no thinking" setting): the answer starts at
     * once. Opus 5.5 cannot turn thinking off, so it thinks adaptively at low effort. Higher effort: adaptive.
     */
    fun thinking(model: String, effort: String): JSONObject {
        val type = when {
            effort != "low" -> "adaptive"
            model == "claude-haiku-5-5" -> "disabled"
            model == "claude-sonnet-5-5" -> "between_tools"
            else -> "adaptive"
        }
        return JSONObject().put("type", type)
    }

    /**
     * One Messages request. The system prompt is a cached block (reused across cards when it is long enough to
     * cache); [conversation] also caches the history as it grows. [schema]: structured output.
     */
    fun body(
        model: String, system: String, messages: JSONArray, effort: String, maxTokens: Int, stream: Boolean,
        schema: JSONObject?, fallback: Boolean, conversation: Boolean, plain: Boolean = false,
    ): JSONObject {
        val output = JSONObject().put("effort", effort)
        if (schema != null) output.put("format", JSONObject().put("type", "json_schema").put("schema", schema))
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", maxTokens)
            .put("thinking", if (plain) JSONObject().put("type", "adaptive") else thinking(model, effort))
            .put("output_config", output)
            .put("messages", messages)
        // [plain]: the request shape of earlier builds, for when the API turns the speed settings down.
        if (plain) body.put("system", system)
        else body.put("system", JSONArray().put(JSONObject().put("type", "text").put("text", system)
            .put("cache_control", JSONObject().put("type", "ephemeral"))))
        if (stream) body.put("stream", true)
        if (conversation && !plain) body.put("cache_control", JSONObject().put("type", "ephemeral"))
        if (fallback) body.put("fallbacks", "default")
        return body
    }

    /** A 400 that names one of the speed settings [body] adds (not, say, a bad key or a long prompt). */
    fun rejectsSpeedSettings(error: String): Boolean =
        listOf("thinking", "between_tools", "disabled", "cache_control").any { error.contains(it, ignoreCase = true) }
}

/** Parsing of the server-sent events and errors; pure, so the JVM tests cover it. */
object ClaudeStream {
    sealed class Event {
        data class Text(val text: String) : Event()
        data class Stop(val reason: String) : Event()
        data class Error(val message: String) : Event()
        data object Done : Event()
    }

    /** One SSE line; null for anything that changes nothing (event names, pings, thinking). */
    fun parse(line: String): Event? {
        if (!line.startsWith("data:")) return null
        val o = runCatching { JSONObject(line.substring(5).trim()) }.getOrNull() ?: return null
        return when (o.optString("type")) {
            "content_block_delta" -> {
                val d = o.optJSONObject("delta") ?: return null
                if (d.optString("type") == "text_delta") Event.Text(d.optString("text")) else null
            }
            "message_delta" -> {
                val r = o.optJSONObject("delta")?.optString("stop_reason").orEmpty()
                if (r.isNotEmpty() && r != "null") Event.Stop(r) else null
            }
            "message_stop" -> Event.Done
            "error" -> o.optJSONObject("error").let { Event.Error(errorText(it?.optString("type").orEmpty(), 0, it?.optString("message"))) }
            else -> null
        }
    }

    /** The text of a non-streamed Messages response, or null with the reason (refusal, cut off). */
    fun messageText(body: String): Pair<String?, String?> {
        val o = runCatching { JSONObject(body) }.getOrNull() ?: return null to "응답을 읽지 못했습니다."
        when (o.optString("stop_reason")) {
            "refusal" -> return null to "Claude가 이 요청에 답하지 않았습니다."
            "max_tokens" -> return null to "답이 길어 끊겼습니다."
        }
        val content = o.optJSONArray("content") ?: return null to "응답을 읽지 못했습니다."
        val sb = StringBuilder()
        for (i in 0 until content.length()) {
            val b = content.optJSONObject(i) ?: continue
            if (b.optString("type") == "text") sb.append(b.optString("text"))
        }
        return if (sb.isEmpty()) null to "응답을 읽지 못했습니다." else sb.toString() to null
    }

    /** Null when the answer is complete, else a note to show under it. */
    fun stopMessage(reason: String?): String? = when (reason) {
        null, "end_turn", "stop_sequence" -> null
        "max_tokens" -> "(답이 길어 여기서 끊겼습니다)"
        "refusal" -> "Claude가 이 요청에 답하지 않았습니다."
        else -> null
    }

    fun httpError(code: Int, body: String?): String {
        val err = runCatching { JSONObject(body ?: "").optJSONObject("error") }.getOrNull()
        val type = err?.optString("type")
        return when {
            code == 401 || type == "authentication_error" -> "API 키가 맞지 않습니다. 앱 아이콘을 눌러 키를 다시 넣어 주세요."
            code == 403 || type == "permission_error" -> "이 API 키로는 이 모델을 쓸 수 없습니다."
            code == 400 && body?.contains("credit balance") == true -> "API 크레딧이 부족합니다. console.anthropic.com에서 확인해 주세요."
            else -> errorText(type.orEmpty(), code, err?.optString("message"))
        }
    }

    /** [detail]: the API's own message, kept for errors this app has no Korean text for (a rejected parameter). */
    private fun errorText(type: String, code: Int, detail: String? = null): String = when {
        type == "rate_limit_error" || code == 429 -> "요청이 많습니다. 잠시 후 다시 눌러 주세요."
        type == "overloaded_error" || code == 529 -> "Claude 서버가 바쁩니다. 잠시 후 다시 눌러 주세요."
        code >= 500 || type == "api_error" -> "Claude 서버 오류입니다. 잠시 후 다시 눌러 주세요."
        else -> "요청이 실패했습니다" + (if (code > 0) " ($code)" else "") + "." +
            (detail?.takeIf { it.isNotBlank() }?.let { "\n" + it.take(300) } ?: "")
    }
}
