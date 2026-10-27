package com.ggumtak.hanja

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * One streamed Claude Messages API request (raw HTTP, the same request shape as ReaderaPlus's AI dictionary).
 * [run] blocks: call it from a background thread; [onText] gets each text piece on that thread.
 */
class ClaudeClient(private val apiKey: String) {
    @Volatile
    private var conn: HttpURLConnection? = null

    @Volatile
    var cancelled = false
        private set

    fun cancel() {
        cancelled = true
        runCatching { conn?.disconnect() }
    }

    /** Returns null when the answer finished, otherwise the Korean message to show. */
    fun run(system: String, user: String, onText: (String) -> Unit): String? {
        val body = JSONObject()
            .put("model", MODEL)
            .put("max_tokens", 2048)
            .put("stream", true)
            .put("thinking", JSONObject().put("type", "adaptive"))
            .put("output_config", JSONObject().put("effort", "low"))
            .put("system", system)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", user)))
        var c: HttpURLConnection? = null
        try {
            c = URL(ENDPOINT).openConnection() as HttpURLConnection
            conn = c
            c.requestMethod = "POST"
            c.connectTimeout = 15_000
            c.readTimeout = 60_000
            c.doOutput = true
            c.setRequestProperty("content-type", "application/json")
            c.setRequestProperty("x-api-key", apiKey)
            c.setRequestProperty("anthropic-version", "2023-06-01")
            c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            if (code != 200) {
                val err = runCatching { c.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull()
                return ClaudeStream.httpError(code, err)
            }
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
            return "연결하지 못했습니다. 인터넷 연결을 확인해 주세요."
        } finally {
            conn = null
            runCatching { c?.disconnect() }
        }
    }

    companion object {
        const val ENDPOINT = "https://api.anthropic.com/v1/messages"
        const val MODEL = "claude-haiku-5-5"
    }
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
