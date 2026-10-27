package com.ggumtak.hanja

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * "한자" in the text selection menu (or the share sheet): a card at the bottom of the screen over the current app with every kanji of
 * the selection (Korean 훈음, Japanese readings, strokes), the Naver dictionaries and a Claude explanation.
 * Tapping outside or back closes it.
 */
class LookupActivity : Activity() {
    private companion object {
        const val DOT = " · "
    }

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
        val shared = if (intent.action == Intent.ACTION_SEND) intent.getCharSequenceExtra(Intent.EXTRA_TEXT) else null
        selection = KanjiText.clean(shared ?: intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT))
        kanji = KanjiText.kanjiIn(selection)
        pal = Palette(this)
        setContentView(build())
        // After setContentView: installing the decor resets a floating window to WRAP_CONTENT.
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        window.setGravity(Gravity.BOTTOM)
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
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
            japanese()
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
        card.addView(CappedScrollView(this, 0.62f).apply {
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
    }

    private fun entry(ch: String, k: Kanji?): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8f), 0, dp(8f))
            background = selectableBackground()
            setOnClickListener { open(KanjiText.naverHanjaUrl(ch)) }
        }
        row.addView(label(ch, 52f, pal.text).apply {
            gravity = Gravity.CENTER
            minWidth = dp(72f)
            japanese()
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP
        })
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14f), 0, 0, 0)
        }
        val hun = when {
            k == null -> "자료에 없는 한자입니다"
            k.hun.isNotEmpty() -> k.hun
            k.kokuji -> "일본에서 만든 한자(국자)"
            else -> k.meanings.ifEmpty { "한국 훈음 자료 없음" }
        }
        col.addView(label(hun, 20f, pal.text, bold = true).apply { setLineSpacing(0f, 1.1f) })
        if (k != null && k.variant.isNotEmpty()) col.addView(label("한국 정자 ${k.variant}", 14f, pal.accent).apply {
            japanese()
            setPadding(0, dp(2f), 0, 0)
        })
        if (k != null) {
            val on = KanjiText.onReadings(k.on)
            if (on.isNotEmpty()) col.addView(readingRow("음", SpannableStringBuilder(on.joinToString(DOT))))
            val kun = KanjiText.kunReadings(k.kun)
            if (kun.isNotEmpty()) {
                val s = SpannableStringBuilder()
                kun.forEachIndexed { i, r ->
                    if (i > 0) s.append(DOT)
                    s.append(r.stem)
                    if (r.okurigana.isNotEmpty()) {
                        val start = s.length
                        s.append(r.okurigana)
                        s.setSpan(ForegroundColorSpan(pal.sub), start, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
                col.addView(readingRow("훈", s))
            }
            val meta = listOfNotNull(
                k.strokes.takeIf { it > 0 }?.let { "${it}획" },
                k.gradeLabel.takeIf { it.isNotEmpty() },
            ).joinToString(DOT)
            if (meta.isNotEmpty()) col.addView(label(meta, 13f, pal.sub).apply { setPadding(0, dp(6f), 0, 0) })
            if (k.meanings.isNotEmpty() && hun != k.meanings) col.addView(label(k.meanings, 13f, pal.sub).apply {
                setPadding(0, dp(1f), 0, 0)
            })
        }
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    /** "음  メイ · ミョウ": a small label column, then the readings in the Japanese font. */
    private fun readingRow(name: String, value: CharSequence): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(5f), 0, 0)
        addView(label(name, 13f, pal.accent, bold = true).apply {
            minWidth = dp(22f)
            setPadding(0, dp(2f), 0, 0)
        })
        addView(label(value, 17f, pal.text).apply {
            japanese()
            setLineSpacing(0f, 1.15f)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
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
