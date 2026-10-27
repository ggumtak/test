package com.ggumtak.hanja

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * "한자" in the text selection menu: a card at the bottom of the screen over the current app with every kanji of
 * the selection (Korean 훈음, Japanese readings, strokes), the Naver dictionaries and a Claude explanation.
 * Tapping outside or back closes it.
 */
class LookupActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var pal: Palette
    private lateinit var entries: LinearLayout
    private lateinit var answer: TextView
    private lateinit var claudeButton: Button
    private var selection = ""
    private var kanji: List<String> = emptyList()
    private var claude: ClaudeClient? = null
    private var destroyed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selection = KanjiText.clean(intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT))
        kanji = KanjiText.kanjiIn(selection)
        pal = Palette(this)
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        window.setGravity(Gravity.BOTTOM)
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        setContentView(build())
        if (KanjiStore.loaded) fill() else Thread {
            runCatching { KanjiStore.load(applicationContext) }
            main.post { if (!destroyed) fill() }
        }.start()
    }

    override fun onDestroy() {
        destroyed = true
        claude?.cancel()
        super.onDestroy()
    }

    private fun build(): View {
        val pad = dp(18f)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(pal.card, dp(22f))
            setPadding(pad, dp(14f), pad, dp(14f))
        }
        if (selection.length > kanji.size || kanji.isEmpty()) {
            card.addView(label(selection.ifEmpty { "(선택한 글자 없음)" }, 14f, pal.sub).apply {
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, 0, 0, dp(6f))
            })
        }
        entries = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        answer = label("", 15f, pal.text).apply {
            visibility = View.GONE
            setTextIsSelectable(true)
            setLineSpacing(0f, 1.25f)
            setPadding(0, dp(12f), 0, dp(4f))
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(entries)
            addView(answer)
        }
        val maxHeight = (resources.displayMetrics.heightPixels * 0.62f).toInt()
        card.addView(CappedScrollView(this, maxHeight).apply {
            isVerticalScrollBarEnabled = true
            addView(body)
        })
        if (kanji.isEmpty()) {
            entries.addView(label("선택한 글자에 한자가 없습니다.", 16f, pal.text).apply { setPadding(0, dp(6f), 0, dp(6f)) })
        } else {
            entries.addView(label("불러오는 중…", 15f, pal.sub))
        }
        card.addView(buttons())
        return LinearLayout(this).apply {
            setPadding(dp(10f), 0, dp(10f), dp(10f))
            addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun buttons(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12f), 0, 0)
        }
        fun add(text: String, onClick: () -> Unit): Button = Button(this).apply {
            this.text = text
            isAllCaps = false
            textSize = 14f
            setTextColor(pal.text)
            background = rounded(pal.button, dp(12f))
            minHeight = dp(44f)
            minimumHeight = dp(44f)
            stateListAnimator = null
            setOnClickListener { onClick() }
            row.addView(this, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (row.childCount > 0) leftMargin = dp(8f)
            })
        }
        add("한자사전") { open(KanjiText.naverHanjaUrl(kanji.firstOrNull() ?: selection)) }
        add("일본어사전") { open(KanjiText.naverJapaneseUrl(selection)) }
        claudeButton = add("Claude 설명") { askClaude() }
        if (selection.isEmpty()) for (i in 0 until row.childCount) row.getChildAt(i).isEnabled = false
        return row
    }

    private fun fill() {
        if (kanji.isEmpty()) return
        entries.removeAllViews()
        kanji.forEachIndexed { i, ch ->
            if (i > 0) entries.addView(View(this).apply { setBackgroundColor(pal.line) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
            entries.addView(entry(ch, KanjiStore.get(ch)))
        }
        if (kanji.size == 1) entries.addView(label("카드를 누르면 네이버 한자사전이 열립니다", 12f, pal.sub).apply {
            setPadding(0, dp(2f), 0, 0)
        })
    }

    private fun entry(ch: String, k: Kanji?): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8f), 0, dp(8f))
            background = selectableBackground()
            setOnClickListener { open(KanjiText.naverHanjaUrl(ch)) }
        }
        row.addView(label(ch, 46f, pal.text).apply {
            gravity = Gravity.CENTER
            minWidth = dp(64f)
        })
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12f), 0, 0, 0)
        }
        val hun = when {
            k == null -> "자료에 없는 한자입니다"
            k.hun.isNotEmpty() -> k.hun
            k.kokuji -> "일본에서 만든 한자(국자)"
            else -> k.meanings.ifEmpty { "한국 훈음 자료 없음" }
        }
        col.addView(label(hun, 18f, pal.text, bold = true))
        if (k != null && k.variant.isNotEmpty()) col.addView(label("한국 정자 ${k.variant}", 14f, pal.accent))
        if (k != null) {
            val reading = listOfNotNull(
                k.on.takeIf { it.isNotEmpty() }?.let { "음 $it" },
                k.kun.takeIf { it.isNotEmpty() }?.let { "훈 $it" },
            ).joinToString("   ")
            if (reading.isNotEmpty()) col.addView(label(reading, 15f, pal.text).apply { setPadding(0, dp(2f), 0, 0) })
            val meta = listOfNotNull(
                k.strokes.takeIf { it > 0 }?.let { "${it}획" },
                k.gradeLabel.takeIf { it.isNotEmpty() },
                k.meanings.takeIf { it.isNotEmpty() && hun != it },
            ).joinToString(" · ")
            if (meta.isNotEmpty()) col.addView(label(meta, 13f, pal.sub).apply { setPadding(0, dp(2f), 0, 0) })
        }
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    private fun open(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "주소를 열 앱이 없습니다", Toast.LENGTH_SHORT).show()
        }
    }

    private fun askClaude() {
        val key = Prefs.apiKey(this)
        if (key.isEmpty()) {
            Toast.makeText(this, "먼저 Claude API 키를 넣어 주세요", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        claude?.cancel()
        val client = ClaudeClient(key)
        claude = client
        claudeButton.isEnabled = false
        answer.visibility = View.VISIBLE
        answer.setTextColor(pal.sub)
        answer.text = "Claude가 답하는 중…"
        val text = StringBuilder()
        Thread {
            val problem = client.run(KanjiText.SYSTEM_PROMPT, KanjiText.userPrompt(selection)) { piece ->
                main.post {
                    if (destroyed || claude !== client) return@post
                    if (text.isEmpty()) answer.setTextColor(pal.text)
                    text.append(piece)
                    answer.text = text.toString()
                }
            }
            main.post {
                if (destroyed || claude !== client) return@post
                claudeButton.isEnabled = true
                claudeButton.text = "다시 묻기"
                if (problem != null) {
                    if (text.isEmpty()) answer.setTextColor(pal.accent)
                    answer.text = if (text.isEmpty()) problem else "$text\n\n$problem"
                }
            }
        }.start()
    }
}
