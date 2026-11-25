package com.ggumtak.hanja.update

import org.json.JSONObject

/** Pure parts of the in-app update: reading GitHub's "latest" release and the texts shown. */
object UpdateInfo {
    /** The rolling release CI publishes every build to (.github/workflows/build.yml). */
    const val LATEST_URL = "https://api.github.com/repos/ggumtak/test/releases/tags/latest"

    data class Asset(val url: String, val size: Long)

    /** [patches]: by installed build, the patch that turns that build's APK into this one (tools/apk_patch.py). */
    data class Latest(val build: Int, val url: String, val size: Long, val patches: Map<Int, Asset> = emptyMap())

    private val APK = Regex("""^Hanja-(\d+)\.apk$""")
    private val PATCH = Regex("""^Hanja-(\d+)\.from-(\d+)\.patch$""")

    /** The newest Hanja-N.apk in the release JSON, or null. */
    fun parse(json: String): Latest? {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val assets = o.optJSONArray("assets") ?: return null
        var best: Latest? = null
        val patches = HashMap<Pair<Int, Int>, Asset>()
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            val name = a.optString("name")
            val url = a.optString("browser_download_url")
            if (!url.startsWith("https://")) continue
            PATCH.find(name)?.groupValues?.let { g ->
                val target = g[1].toIntOrNull()
                val base = g[2].toIntOrNull()
                if (target != null && base != null) patches[Pair(target, base)] = Asset(url, a.optLong("size", -1))
            }
            val build = APK.find(name)?.groupValues?.get(1)?.toIntOrNull() ?: continue
            if (best == null || build > best.build) best = Latest(build, url, a.optLong("size", -1))
        }
        val b = best ?: return null
        return b.copy(patches = patches.filterKeys { it.first == b.build }.mapKeys { it.key.second })
    }

    /** The patch from [current] when there is one and it is clearly smaller than the whole APK. */
    fun patchFor(latest: Latest, current: Int): Asset? =
        latest.patches[current]?.takeIf { it.size > 0 && (latest.size <= 0 || it.size < latest.size * 6 / 10) }

    /** "0.1MB", "25MB". */
    fun sizeText(bytes: Long): String {
        val mb = bytes / 1_048_576.0
        return if (mb < 10) "%.1fMB".format(java.util.Locale.ROOT, mb) else "%.0fMB".format(java.util.Locale.ROOT, mb)
    }

    fun isNewer(latest: Latest, current: Int): Boolean = latest.build > current

    fun fileName(build: Int) = "Hanja-$build.apk"

    fun progressText(percent: Int): String = if (percent < 0) "다운로드 중…" else "다운로드 중… $percent%"
}
