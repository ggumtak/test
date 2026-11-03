package com.ggumtak.hanja.update

import org.json.JSONObject

/** Pure parts of the in-app update: reading GitHub's "latest" release and the texts shown. */
object UpdateInfo {
    /** The rolling release CI publishes every build to (.github/workflows/build.yml). */
    const val LATEST_URL = "https://api.github.com/repos/ggumtak/test/releases/tags/latest"

    data class Latest(val build: Int, val url: String, val size: Long)

    private val APK = Regex("""^Hanja-(\d+)\.apk$""")

    /** The newest Hanja-N.apk in the release JSON, or null. */
    fun parse(json: String): Latest? {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val assets = o.optJSONArray("assets") ?: return null
        var best: Latest? = null
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            val build = APK.find(a.optString("name"))?.groupValues?.get(1)?.toIntOrNull() ?: continue
            val url = a.optString("browser_download_url")
            if (!url.startsWith("https://")) continue
            if (best == null || build > best.build) best = Latest(build, url, a.optLong("size", -1))
        }
        return best
    }

    fun isNewer(latest: Latest, current: Int): Boolean = latest.build > current

    fun fileName(build: Int) = "Hanja-$build.apk"

    fun progressText(percent: Int): String = if (percent < 0) "내려받는 중…" else "내려받는 중… $percent%"
}
