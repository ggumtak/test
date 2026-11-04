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
import android.graphics.Typeface
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.StyleSpan
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

/**
 * A card at the bottom of the screen over the current app: the selected word (with its Hangul pronunciation when it
 * is all kana), every kanji in it (Korean 훈음, readings with Hangul, strokes), links to the Naver dictionaries and
 * a Claude explanation. Opened from the text selection menu ("한자"), the share sheet, or a hanja://lookup link
 * (the Anki card script, assets/anki-tap.html). Tapping outside or back closes it.
 */
class LookupActivity : Activity() {
    private companion object {
        const val DOT = " · "
    }

    private val main = Handler(Looper.getMainLooper())
    private lateinit var pal: Palette
    private lateinit var entries: LinearLayout
    private lateinit var headerView: TextView
    private lateinit var wordNote: TextView
    private lateinit var answer: TextView
    private lateinit var claudeButton: Button
    private var selection = ""
    /** The sentence around a word tapped in Anki, for Claude; empty otherwise. */
    private var context = ""
    private var kanji: List<String> = emptyList()
    private var claude: ClaudeClient? = null
    /** The context-reading request (separate from the 'Claude 설명' stream); set on a background thread. */
    @Volatile
    private var contextClient: ClaudeClient? = null
    private var lastWord: Word? = null
    /** The 질문 chat: its log, input row, the turns sent so far and the running request. */
    private lateinit var scroll: CappedScrollView
    private lateinit var chatLog: LinearLayout
    private lateinit var inputRow: LinearLayout
    private lateinit var input: EditText
    private lateinit var sendButton: Button
    private val chatTurns = JSONArray()
    /** The 예문 section under the kanji rows: a "예문 보기" line, then the sentences. */
    private lateinit var examplesBox: LinearLayout
    @Volatile
    private var examplesClient: ClaudeClient? = null
    /** A tap on "예문 보기" is being handled (saved examples being read, or Claude asked). */
    private var examplesBusy = false
    private var chatClient: ClaudeClient? = null
    private var contextReading: ContextReading? = null
    @Volatile
    private var destroyed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val raw: CharSequence? = when (intent.action) {
            Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.data?.let { runCatching { it.getQueryParameter("q") }.getOrNull() }
            else -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
        }
        selection = KanjiText.clean(raw)
        if (intent.action == Intent.ACTION_VIEW) {
            context = KanjiText.clean(intent.data?.let { runCatching { it.getQueryParameter("s") }.getOrNull() })
        }
        kanji = KanjiText.kanjiIn(selection)
        pal = Palette(this)
        setContentView(build())
        // After setContentView: installing the decor resets a floating window to WRAP_CONTENT.
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        window.setGravity(Gravity.BOTTOM)
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        val app = applicationContext
        val target = selection
        val sentence = context
        // The Anki card code goes current here too: many users never open the first screen after an update.
        Thread {
            // Silent: what could not be changed is shown on the first screen (Anki 카드에 적용).
            AnkiSync.refresh(app)
        }.start()
        // One background pass: dictionaries, the saved context reading, then (only if needed) the Claude request.
        Thread {
            runCatching { KanjiStore.load(app) }
            runCatching { PartsStore.load(app) }
            runCatching { WordStore.load(app) }
            val word = findWord()
            val kun = kunMatch()
            val key = Prefs.apiKey(app)
            // The word in its dictionary form, for 자주 까먹는 단어 (VocabAdder).
            vocabForm = kun?.let { target + it.okurigana } ?: word?.form ?: ""
            vocabReading = kun?.let { it.stem + it.okurigana } ?: word?.readings?.firstOrNull() ?: ""
            vocabLookup(app, target, sentence)
            // Anki taps carry the sentence; a selection of two or more characters is its own context.
            val wantContext = Prefs.autoContext(app) && kanji.isNotEmpty() &&
                (sentence.isNotEmpty() || target.codePointCount(0, target.length) >= 2)
            fun show(cr: ContextReading?) = main.post {
                kunMatched = kun
                if (destroyed) return@post
                if (cr != null) contextReading = cr
                fill(word)
            }
            // A reviewed card is answered from the phone (even offline or without a key), in the very first frame.
            // Only the one-time move of earlier builds' answers after an update is slow: the dictionary shows first.
            val ready = !wantContext || Prefs.answersReady(app)
            if (!ready) show(null)
            val cached = if (!wantContext) null else Prefs.cachedReading(app, target, sentence)
                ?.let { ContextReading.parse(it, target, sentence) }
            if (ready || cached != null) show(cached)
            if (key.isEmpty()) return@Thread
            if (wantContext && cached == null) askContext(ClaudeClient(key), target, sentence)
            else ClaudeClient.warmUp()
        }.start()
    }

    override fun onDestroy() {
        destroyed = true
        if (VocabService.listener === heard) VocabService.listener = null
        claude?.cancel()
        contextClient?.cancel()
        chatClient?.cancel()
        examplesClient?.cancel()
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun build(): View {
        val pad = dp(18f)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(pal.card, dp(22f))
            setPadding(pad, dp(14f), pad, dp(14f))
        }
        headerView = header(null)
        card.addView(headerView)
        wordNote = label("", 13f, pal.sub).apply {
            visibility = View.GONE
            setPadding(0, 0, 0, dp(6f))
        }
        card.addView(wordNote)
        entries = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6f), 0, 0)
        }
        answer = label("", 15f, pal.text).apply {
            japanese()
            visibility = View.GONE
            setTextIsSelectable(true)
            setLineSpacing(0f, 1.25f)
            setPadding(0, dp(12f), 0, dp(4f))
        }
        chatLog = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        examplesBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            if (selection.any { KanaHangul.isKana(it) || KanjiText.isKanji(it.code) }) addView(examplesLink())
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(entries)
            addView(examplesBox)
            addView(answer)
            addView(chatLog)
        }
        scroll = CappedScrollView(this, 0.62f).apply {
            isVerticalScrollBarEnabled = true
            addView(body)
            // When the keyboard takes the room, keep the text that was at the bottom (the latest answer) in view.
            addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
                val shrunk = (oldBottom - oldTop) - (bottom - top)
                if (shrunk > 0 && oldBottom > oldTop) post { scrollBy(0, shrunk) }
            }
        }
        // Weight 1: when the card is taller than the room left above the keyboard, only this scrolling part
        // shrinks, so the question input and the buttons under it always stay visible.
        card.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (kanji.isNotEmpty()) entries.addView(label("불러오는 중…", 15f, pal.sub))
        card.addView(chatInput())
        if (hasWord()) card.addView(vocabButton())
        card.addView(buttons())
        return LinearLayout(this).apply {
            setPadding(dp(10f), 0, dp(10f), dp(10f))
            addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    /** A JMdict word found for the selection, its readings and, per character, the reading it takes there. */
    private class Word(val form: String, val readings: List<String>, val split: List<String>?)

    /** The kun reading of a single tapped kanji from the kana after it (貼 + って → は, 貼る); set before [fill]. */
    private var kunMatched: KanjiText.Kun? = null

    /** Background thread: [Readings.kunBefore] for a single kanji, else null. */
    private fun kunMatch(): KanjiText.Kun? {
        if (kanji.size != 1 || selection != kanji[0]) return null
        val k = KanjiStore.get(selection) ?: return null
        return Readings.kunBefore(k.kun, Readings.following(selection, context))
    }

    /** Background thread: the first of [Readings.lookupForms] that JMdict knows. */
    private fun findWord(): Word? {
        if (selection.isEmpty() || KanaHangul.kanaOnly(selection)) return null
        val (form, r) = Readings.findWord(selection, context, WordStore::readings) ?: return null
        val split = Readings.split(form, r[0]) { ch -> KanjiStore.get(ch)?.let(Readings::candidates) }
        return Word(form, r, split)
    }

    /**
     * How the selection itself is read here (貼 in 貼ってあります → は, not the whole はってあります): Claude's
     * per-kanji parts when it answered, else the dictionary word's split, else its readings when the word is
     * exactly the selection.
     */
    private fun selectionReadings(dictWord: Word?): List<String> {
        contextReading?.let { cr ->
            if (cr.word == selection) return listOf(cr.reading)
            Readings.fromParts(selection, cr.kanji)?.let { return listOf(it) }
        }
        if (dictWord != null) {
            if (dictWord.form == selection) return dictWord.readings
            dictWord.split?.let { Readings.prefixReading(selection, dictWord.form, it) }?.let { return listOf(it) }
        }
        return kunMatched?.let { listOf(it.stem) } ?: emptyList()
    }

    /** The word the tapped kanji sits in, in its basic form (貼る はる, 暖か あたたか), when it is more than the kanji. */
    private fun wordForm(dictWord: Word?): Pair<String, String>? {
        kunMatched?.let { return (selection + it.okurigana) to (it.stem + it.okurigana) }
        if (dictWord != null && dictWord.form != selection && dictWord.form.startsWith(selection)) {
            return dictWord.form to dictWord.readings[0]
        }
        return null
    }

    private fun headerText(dictWord: Word?): CharSequence {
        val s = SpannableStringBuilder(selection.ifEmpty { "(선택한 글자 없음)" })
        val kana = KanaHangul.kanaOnly(selection)
        val readings = if (kana) listOf(selection) else selectionReadings(dictWord)
        readings.forEachIndexed { i, r ->
            s.append(if (i == 0) "  " else "  ·  ")
            if (!kana) {
                val start = s.length
                s.append(r)
                s.setSpan(RelativeSizeSpan(0.8f), start, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val start = s.length
            s.append(if (!kana) "(" + KanaHangul.convert(r) + ")" else KanaHangul.convert(r))
            s.setSpan(ForegroundColorSpan(pal.accent), start, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            s.setSpan(RelativeSizeSpan(0.75f), start, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (!kana && readings.isNotEmpty()) wordForm(dictWord)?.let { (form, reading) ->
            val start = s.length
            s.append("   ").append(form).append(" ").append(reading)
            s.setSpan(ForegroundColorSpan(pal.sub), start, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            s.setSpan(RelativeSizeSpan(0.7f), start, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            s.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.NORMAL), start, s.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return s
    }

    /** The word itself, large, with its reading and Hangul pronunciation. */
    private fun header(word: Word?): TextView {
        return label(headerText(word), 22f, pal.text, bold = true).apply {
            japanese()
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, 0, 0, dp(8f))
        }
    }

    // ---- 자주 까먹는 단어 (VocabAdder) ----

    @Volatile
    private var vocabForm = ""
    @Volatile
    private var vocabReading = ""
    private var vocab: Button? = null

    private fun hasWord(): Boolean = selection.any { KanaHangul.isKana(it) || KanjiText.isKanji(it.code) }

    private fun vocabButton(): Button = Button(this).apply {
        text = "단어장"
        isAllCaps = false
        textSize = 14f
        setTextColor(pal.accent)
        background = rounded(pal.button, dp(12f))
        minHeight = dp(40f)
        minimumHeight = dp(40f)
        stateListAnimator = null
        setOnClickListener { addVocab() }
        vocab = this
    }.also {
        it.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(10f)
        }
    }

    private fun showAdded() {
        vocab?.apply {
            text = "단어장 ✓"
            isEnabled = false
        }
    }

    /** Background thread: counts this lookup; on the [VocabNote.AUTO_AFTER]th the word goes in by itself. */
    private fun vocabLookup(app: android.content.Context, target: String, sentence: String) {
        if (!hasWord()) return
        val key = VocabNote.key(vocabForm, target)
        if (Prefs.vocabAdded(app, key)) {
            main.post { if (!destroyed) showAdded() }
            return
        }
        if (!Prefs.vocabAuto(app)) return
        val n = Prefs.countLookup(app, key)
        if (n < VocabNote.AUTO_AFTER || !AnkiBridge.granted(app) || Prefs.apiKey(app).isEmpty()) return
        val form = vocabForm
        val reading = vocabReading
        main.post {
            listen()
            if (VocabService.start(app, target, form, reading, sentence, prefix = "자동 · ", quiet = true)) return@post
            Thread {
                val o = VocabAdder.add(app, target, form, reading, sentence)
                if (!o.added) return@Thread
                main.post {
                    if (destroyed) Toast.makeText(app, "자동 · " + o.message, Toast.LENGTH_SHORT).show()
                    else showAdded()
                }
            }.start()
        }
    }

    /** This card hears when its word is done in [VocabService], while it is open. */
    private var heard: ((String, VocabAdder.Outcome) -> Boolean)? = null

    private fun listen() {
        val key = VocabNote.key(vocabForm, selection)
        val l: (String, VocabAdder.Outcome) -> Boolean = { k, o ->
            val mine = !destroyed && k == key
            if (mine) {
                if (o.added || o.already) showAdded()
                else vocab?.apply {
                    text = "단어장"
                    isEnabled = true
                }
            }
            mine
        }
        heard = l
        VocabService.listener = l
    }

    private fun addVocab() {
        val b = vocab ?: return
        b.text = "넣는 중… (창을 닫아도 계속 넣습니다)"
        b.isEnabled = false
        val app = applicationContext
        val target = selection
        val form = vocabForm
        val reading = vocabReading
        val sentence = context
        listen()
        if (VocabService.start(app, target, form, reading, sentence)) return
        b.text = "넣는 중…"
        Thread {
            val o = VocabAdder.add(app, target, form, reading, sentence)
            main.post {
                if (destroyed || !(o.added || o.already)) Toast.makeText(app, o.message, Toast.LENGTH_SHORT).show()
                if (destroyed) return@post
                if (o.added || o.already) showAdded()
                else {
                    b.text = "단어장"
                    b.isEnabled = true
                }
            }
        }.start()
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
            maxLines = 1
            setPadding(dp(6f), 0, dp(6f), 0)
            setAutoSizeTextTypeUniformWithConfiguration(11, 14, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
            stateListAnimator = null
            setOnClickListener { onClick() }
            row.addView(this, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (row.childCount > 0) leftMargin = dp(8f)
            })
        }
        add("일본어사전") { open(KanjiText.naverJapaneseUrl(selection)) }
        add("한자사전") { open(KanjiText.naverHanjaUrl(kanji.firstOrNull() ?: selection)) }
        claudeButton = add("Claude 설명") { askClaude() }
        add("질문") { openChat() }
        if (selection.isEmpty()) for (i in 0 until row.childCount) row.getChildAt(i).isEnabled = false
        return row
    }

    private fun fill(word: Word?) {
        lastWord = word
        headerView.text = headerText(word)
        val inWord = HashMap<String, String>()
        val cr = contextReading
        wordNote.visibility = View.GONE
        if (cr != null) {
            // Claude read the sentence: its split wins over the dictionary's first reading.
            if (cr.whole) showWholeNote(cr.reading) else inWord.putAll(cr.kanji)
        } else if (word != null) {
            if (word.split == null && word.form.any { KanjiText.isKanji(it.code) }) {
                val r = word.readings[0]
                wordNote.text = "사전 읽기 $r(${KanaHangul.convert(r)}) · 한자별 읽기는 확인되지 않았습니다."
                wordNote.japanese()
                wordNote.visibility = View.VISIBLE
            }
            word.split?.let { parts ->
                var i = 0
                for (p in parts) {
                    val n = Character.charCount(word.form.codePointAt(i))
                    val ch = word.form.substring(i, i + n)
                    if (KanjiText.isKanji(ch.codePointAt(0))) inWord.putIfAbsent(ch, p)
                    i += n
                }
            }
        }
        if (kanji.isEmpty()) return
        entries.removeAllViews()
        kanji.forEachIndexed { i, ch ->
            if (i > 0) entries.addView(View(this).apply { setBackgroundColor(pal.line) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
            val k = KanjiStore.get(ch)
            val m = contextReading?.meanings?.get(ch)?.takeIf { ContextReading.acceptable(it, k?.hunJa?.ifEmpty { k.hun } ?: "") }
            entries.addView(entry(ch, k, inWord[ch], m))
        }
    }

    private fun showWholeNote(r: String) {
        wordNote.text = "$r(${KanaHangul.convert(r)})는 단어 전체에 붙는 특별한 읽기(숙자훈)라서 " +
            "한자 하나하나의 음·훈과 다릅니다."
        wordNote.japanese()
        wordNote.visibility = View.VISIBLE
    }

    /**
     * Background thread: asks Claude how the word is read in its sentence. Until an answer comes (or if none
     * does) the dictionary's readings stay; Claude's then replace them in the header and the kanji rows.
     */
    private fun askContext(client: ClaudeClient, target: String, sentence: String) {
        contextClient = client
        if (destroyed) {
            client.cancel()
            return
        }
        // Candidates only for the kanji the card shows (所 when 所 of 台所 was tapped): the model picks among
        // them, and meanings for other kanji would never be displayed, so they would only add tokens.
        val candidates = LinkedHashMap<String, String>()
        for (ch in kanji) {
            val k = KanjiStore.get(ch) ?: continue
            val hun = k.hunJa.ifEmpty { k.hun }
            if (hun.isNotEmpty()) candidates[ch] = ContextReading.candidate(hun, k.gloss)
        }
        val (json, _) = client.json(ContextReading.SYSTEM_PROMPT,
            ContextReading.userPrompt(target, sentence, candidates), ContextReading.SCHEMA)
        val cr = json?.let { ContextReading.parse(it, target, sentence) }
        if (!client.cancelled && cr != null) Prefs.cacheReading(applicationContext, target, sentence, json)
        main.post {
            if (destroyed || contextClient !== client) return@post
            contextClient = null
            if (cr != null) {
                contextReading = cr
                fill(lastWord)
            }
        }
    }

    /**
     * The 훈음 line: the 훈음 for this sentence ([ctxMeaning], from Claude: 곳 소) first in the accent colour, then
     * the 훈음 most natural for Japanese first (hunJa), else the old list.
     */
    private fun meaningText(k: Kanji?, ctxMeaning: String?): CharSequence {
        val s = SpannableStringBuilder()
        if (ctxMeaning != null) {
            s.append(ctxMeaning)
            s.setSpan(ForegroundColorSpan(pal.accent), 0, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val list = k?.hunJa?.ifEmpty { k.hun } ?: ""
        val items = list.split(", ", " / ").map { it.trim() }.filter { it.isNotEmpty() && it != ctxMeaning }
        for (item in items) {
            if (s.isNotEmpty()) s.append(", ")
            s.append(item)
        }
        if (s.isEmpty()) s.append(when {
            k == null -> "자료에 없는 한자입니다"
            k.kokuji -> "일본에서 만든 한자(국자)"
            else -> k.meanings.ifEmpty { "한국 훈음 자료 없음" }
        })
        return s
    }

    private fun entry(ch: String, k: Kanji?, inWord: String?, ctxMeaning: String?): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8f), 0, dp(8f))
            background = selectableBackground()
            setOnClickListener { open(KanjiText.naverHanjaUrl(ch)) }
        }
        val parts = PartsStore.parts(ch)
        if (parts != null && parts.strokes.isNotEmpty()) {
            // Drawn from its strokes: the radical in the accent colour, the sound part in blue.
            row.addView(KanjiStrokeView(this, parts.strokes, dp(68f)) { tag ->
                when (tag) {
                    Parts.RADICAL -> pal.accent
                    Parts.SOUND -> pal.sound
                    else -> pal.text
                }
            }.apply {
                contentDescription = ch
                setPadding(dp(2f), dp(4f), dp(2f), 0)
            }, LinearLayout.LayoutParams(dp(72f), dp(72f)).apply { gravity = Gravity.TOP })
        } else {
            row.addView(label(ch, 52f, pal.text).apply {
                gravity = Gravity.CENTER
                minWidth = dp(72f)
                japanese()
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP
            })
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14f), 0, 0, 0)
        }
        val meaning = meaningText(k, ctxMeaning)
        col.addView(label(meaning, 20f, pal.text, bold = true).apply { setLineSpacing(0f, 1.1f) })
        if (k != null) {
            // The plain-Korean meanings and the Korean traditional form, small, under the 훈음.
            val old = listOfNotNull(
                k.gloss.takeIf { it.isNotEmpty() }?.let { "뜻 $it" },
                k.variant.takeIf { it.isNotEmpty() }?.let { "정자 $it" },
                "일본에서 만든 한자".takeIf { k.kokuji },
            ).joinToString(DOT)
            if (old.isNotEmpty()) col.addView(label(old, 13f, pal.sub).apply {
                japanese()
                setPadding(0, dp(3f), 0, 0)
            })
        }
        if (inWord != null) {
            val s = SpannableStringBuilder(inWord)
            hangul(s, inWord)
            col.addView(readingRow("단어", s))
        }
        if (k != null) {
            val on = KanjiText.onReadings(k.on)
            if (on.isNotEmpty()) {
                val s = SpannableStringBuilder()
                on.forEachIndexed { i, r ->
                    if (i > 0) s.append(DOT)
                    s.append(r)
                    hangul(s, r)
                }
                col.addView(readingRow("음", s))
            }
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
                    hangul(s, r.stem + r.okurigana)
                }
                col.addView(readingRow("훈", s))
            }
        }
        addPartRows(col, ch, k, parts)
        if (k != null) {
            val meta = listOfNotNull(
                k.strokes.takeIf { it > 0 }?.let { "${it}획" },
                k.gradeLabel.takeIf { it.isNotEmpty() },
            ).joinToString(DOT)
            if (meta.isNotEmpty()) col.addView(label(meta, 13f, pal.sub).apply { setPadding(0, dp(6f), 0, 0) })
            if (k.meanings.isNotEmpty() && meaning.toString() != k.meanings) col.addView(englishLine(k))
        }
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    /** The English meanings; a tap swaps them for the short Korean ones and back. */
    private fun englishLine(k: Kanji): TextView {
        val en = k.meanings
        val ko = k.englishKo
        return label(en, 13f, pal.sub).apply {
            setPadding(0, dp(1f), 0, dp(2f))
            if (ko.isEmpty()) return@apply
            var korean = false
            fun show() {
                val s = SpannableStringBuilder(if (korean) ko else en)
                val start = s.length
                s.append(if (korean) "  ⇄ EN" else "  ⇄ 한")
                s.setSpan(ForegroundColorSpan(pal.accent), start, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                text = s
            }
            show()
            setOnClickListener {
                korean = !korean
                show()
            }
        }
    }

    /** Appends "(메이)" for [kana], smaller and in the accent colour. */
    private fun hangul(s: SpannableStringBuilder, kana: String) {
        val start = s.length
        s.append("(").append(KanaHangul.convert(kana)).append(")")
        s.setSpan(ForegroundColorSpan(pal.accent), start, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        s.setSpan(RelativeSizeSpan(0.82f), start, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    /** "음  メイ(메이) · ミョウ(묘우)": a small label column, then the readings in the Japanese font. */
    private fun readingRow(name: String, value: CharSequence, sp: Float = 17f): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(5f), 0, 0)
        addView(label(name, 13f, pal.accent, bold = true).apply {
            minWidth = dp(44f)
            setPadding(0, dp(if (sp >= 17f) 2f else 1f), 0, 0)
        })
        addView(label(value, sp, pal.text).apply {
            japanese()
            setLineSpacing(0f, 1.15f)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    /**
     * What helps the kanji stick: its radical (부수), its parts with the radical and the sound part in the colours of
     * the stroke drawing (구성), other kanji with the same sound part and reading (같은음) and, for the 상용한자,
     * how it came to its shape (자원) and a memory story (외우기).
     */
    private fun addPartRows(col: LinearLayout, ch: String, k: Kanji?, p: Parts?) {
        if (p != null) {
            PartsStore.radical(p)?.let { col.addView(readingRow("부수", radicalText(ch, p, it), 15f)) }
            partsText(p)?.let { col.addView(readingRow("구성", it, 15f)) }
            familyText(k, p)?.let { col.addView(readingRow("같은음", it, 15f)) }
        }
        PartsStore.origin(ch)?.let { o ->
            val s = SpannableStringBuilder(o.origin)
            if (o.type.isNotEmpty()) {
                val end = o.type.length + 1
                s.setSpan(ForegroundColorSpan(pal.accent), 0, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                s.setSpan(StyleSpan(Typeface.BOLD), 0, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            col.addView(readingRow("자원", s, 14.5f))
            if (o.memo.isNotEmpty()) col.addView(readingRow("외우기", o.memo, 14.5f))
        }
    }

    /** "氵 삼수변(물) · さんずい(산즈이)": the radical as it looks here in the accent colour, its Korean and Japanese names. */
    private fun radicalText(ch: String, p: Parts, r: Radical): CharSequence {
        val s = SpannableStringBuilder()
        // The shape drawn in this kanji when the table names it (亻), else the radical's main form.
        val shape = if (p.form.isNotEmpty() && p.form == r.form) p.form else r.form
        s.append(shape)
        s.setSpan(ForegroundColorSpan(pal.accent), 0, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        s.setSpan(StyleSpan(Typeface.BOLD), 0, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        s.append("  ").append(r.korean)
        if (r.meaning.isNotEmpty()) s.append("(").append(r.meaning).append(")")
        s.append(DOT).append(r.japanese)
        hangul(s, r.japanese)
        if (shape == ch) {
            val start = s.length
            s.append(DOT).append("글자 자체가 부수")
            s.setSpan(ForegroundColorSpan(pal.sub), start, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return s
    }

    /** "氵(물) + 毎(매양 매 · 소리)", the radical and the sound part coloured as in the stroke drawing; null for one part. */
    private fun partsText(p: Parts): CharSequence? {
        if (p.parts.size < 2) return null
        val s = SpannableStringBuilder()
        // A few parts are rare characters the phone's fonts lack (傷's 𬀷): their full form, else a word.
        val glyphs = android.graphics.Paint()
        fun drawable(x: String) = x.isNotEmpty() && glyphs.hasGlyph(x)
        p.parts.forEachIndexed { i, part ->
            if (i > 0) s.append(" + ")
            val start = s.length
            s.append(when {
                drawable(part.element) -> part.element
                drawable(part.original) -> part.original
                else -> "부품"
            })
            val color = when {
                part.radical -> pal.accent
                part.sound -> pal.sound
                else -> null
            }
            if (color != null) s.setSpan(ForegroundColorSpan(color), start, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val gloss = if (part.radical) PartsStore.radical(p)?.let { it.meaning.ifEmpty { it.korean } }.orEmpty()
            else firstHun(part.element).ifEmpty { firstHun(part.original) }
            val notes = listOfNotNull(gloss.ifEmpty { null }, "소리".takeIf { part.sound && !part.radical })
            if (notes.isNotEmpty()) {
                val from = s.length
                s.append("(").append(notes.joinToString(DOT)).append(")")
                s.setSpan(ForegroundColorSpan(pal.sub), from, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                s.setSpan(RelativeSizeSpan(0.85f), from, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return s
    }

    /** The first 훈음 of [ch] ("매양 매"), or empty. */
    private fun firstHun(ch: String): String {
        if (ch.isEmpty()) return ""
        val k = KanjiStore.get(ch) ?: return ""
        return k.hunJa.ifEmpty { k.hun }.split(", ", " / ").firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    }

    /**
     * "青 清 静 情 精  セイ(세이)": kanji with the same sound part that share an on reading with this one; each gets
     * its own reading when they do not all share the same one.
     */
    private fun familyText(k: Kanji?, p: Parts): CharSequence? {
        if (k == null || p.family.isEmpty()) return null
        val mine = KanjiText.onReadings(k.on, 10).map { Parts.unvoiced(it) }.toSet()
        val items = ArrayList<Pair<String, String>>()
        var i = 0
        while (i < p.family.length) {
            val n = Character.charCount(p.family.codePointAt(i))
            val m = p.family.substring(i, i + n)
            i += n
            val r = KanjiStore.get(m)?.let { mk -> KanjiText.onReadings(mk.on, 10).firstOrNull { Parts.unvoiced(it) in mine } }
            if (r != null) items.add(m to r)
        }
        if (items.isEmpty()) return null
        val s = SpannableStringBuilder()
        val same = items.map { it.second }.distinct().size == 1
        items.forEachIndexed { j, (m, r) ->
            if (j > 0) s.append(" ")
            s.append(m)
            if (!same) {
                val start = s.length
                s.append(r)
                s.setSpan(ForegroundColorSpan(pal.sub), start, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                s.setSpan(RelativeSizeSpan(0.8f), start, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        if (same) {
            val r = items[0].second
            s.append("  ").append(r)
            hangul(s, r)
        }
        return s
    }

    private fun open(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "주소를 열 앱이 없습니다", Toast.LENGTH_SHORT).show()
        }
    }

    /** "예문 보기 ▸": a tap fills the section (from the saved ones at once, else from Claude). */
    private fun examplesLink(): TextView = label("예문 보기  ▸", 15f, pal.accent, bold = true).apply {
        setPadding(0, dp(14f), 0, dp(6f))
        background = selectableBackground()
        setOnClickListener { loadExamples() }
    }

    /** Saved examples (read off the main thread) show at once; otherwise Claude writes them. */
    private fun loadExamples() {
        if (examplesBusy) return
        examplesBusy = true
        val link = examplesBox.getChildAt(0) as? TextView
        val word = selection
        val sentence = context
        val app = applicationContext
        Thread {
            val saved = Prefs.cachedExamples(app, word, sentence)?.let { Example.parse(it) }.orEmpty()
            if (saved.isNotEmpty()) {
                main.post { if (!destroyed) showExamples(saved) }
                return@Thread
            }
            val key = Prefs.apiKey(app)
            if (key.isEmpty()) {
                main.post {
                    examplesBusy = false
                    if (!destroyed) needKey()
                }
                return@Thread
            }
            val client = ClaudeClient(key, Prefs.chatModel(app))
            examplesClient = client
            if (destroyed) {
                client.cancel()
                return@Thread
            }
            main.post {
                link?.setTextColor(pal.sub)
                link?.text = "예문 만드는 중…"
            }
            val (json, problem) = client.json(Example.SYSTEM_PROMPT, Example.userPrompt(word, sentence), Example.SCHEMA, 2048)
            val list = json?.let { Example.parse(it) }.orEmpty()
            if (!client.cancelled && json != null && list.isNotEmpty()) Prefs.cacheExamples(app, word, sentence, json)
            main.post {
                if (destroyed || examplesClient !== client) return@post
                examplesClient = null
                examplesBusy = false
                if (list.isNotEmpty()) {
                    showExamples(list)
                } else {
                    link?.setTextColor(pal.accent)
                    link?.text = "예문을 만들지 못했습니다. 다시 누르기  ▸" + (problem?.let { "\n$it" } ?: "")
                }
            }
        }.start()
    }

    /** Without an API key: say so and open the settings screen. */
    private fun needKey() {
        Toast.makeText(this, "먼저 Claude API 키를 넣어 주세요", Toast.LENGTH_LONG).show()
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /**
     * Each example as three lines: the sentence (the word in the accent colour, bold), its reading in hiragana,
     * the Korean translation; a thin rule on the left groups them.
     */
    private fun showExamples(list: List<Example>) {
        examplesBox.removeAllViews()
        examplesBox.addView(label("예문", 15f, pal.accent, bold = true).apply { setPadding(0, dp(14f), 0, dp(2f)) })
        for (ex in list) {
            val ja = SpannableStringBuilder(ex.ja)
            val at = if (ex.word.isEmpty()) -1 else ex.ja.indexOf(ex.word)
            if (at >= 0) {
                ja.setSpan(ForegroundColorSpan(pal.accent), at, at + ex.word.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                ja.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), at, at + ex.word.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val lines = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12f), dp(2f), 0, dp(2f))
                addView(label(ja, 17f, pal.text).apply {
                    japanese()
                    setTextIsSelectable(true)
                    setLineSpacing(0f, 1.15f)
                })
                if (ex.reading.isNotEmpty()) {
                    addView(label(ex.reading, 13f, pal.sub).apply {
                        japanese()
                        setTextIsSelectable(true)
                        setPadding(0, dp(2f), 0, 0)
                    })
                }
                addView(label(ex.ko, 15f, pal.text).apply {
                    setTextIsSelectable(true)
                    setPadding(0, dp(3f), 0, 0)
                })
            }
            examplesBox.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(10f), 0, 0)
                addView(View(this@LookupActivity).apply { setBackgroundColor(pal.line) },
                    LinearLayout.LayoutParams(dp(3f), ViewGroup.LayoutParams.MATCH_PARENT))
                addView(lines, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            })
        }
    }

    /** The 질문 input: the dragged text is put in, the user types the rest and sends. */
    private fun chatInput(): View {
        input = EditText(this).apply {
            textSize = 16f
            setTextColor(pal.text)
            setHintTextColor(pal.sub)
            hint = "질문을 이어서 쓰세요"
            japanese()
            maxLines = 4
            background = rounded(pal.button, dp(12f))
            setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
        }
        sendButton = Button(this).apply {
            text = "보내기"
            isAllCaps = false
            textSize = 14f
            setTextColor(pal.card)
            background = rounded(pal.accent, dp(12f))
            stateListAnimator = null
            minHeight = dp(44f)
            minimumHeight = dp(44f)
            setOnClickListener { sendChat() }
        }
        inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(0, dp(10f), 0, 0)
            addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(sendButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(8f) })
        }
        return inputRow
    }

    /** Switches the card to the chat: the kanji rows make room, the input gets the selection and the keyboard. */
    private fun openChat() {
        if (inputRow.visibility != View.VISIBLE) {
            entries.visibility = View.GONE
            examplesBox.visibility = View.GONE
            answer.visibility = View.GONE
            chatLog.visibility = View.VISIBLE
            inputRow.visibility = View.VISIBLE
            if (input.text.isEmpty() && selection.isNotEmpty()) input.setText("$selection ")
        }
        input.setSelection(input.text.length)
        input.requestFocus()
        input.post {
            getSystemService(InputMethodManager::class.java)?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
        // While the question is typed, the connection to Claude gets ready.
        if (chatClient == null && Prefs.apiKey(this).isNotEmpty()) ClaudeClient.warmUp()
    }

    /** What the user sent: a rounded bubble on the right, at most 85% of the card's width. */
    private fun myBubble(text: CharSequence): View {
        val t = label(text, 15f, pal.text).apply {
            japanese()
            setTextIsSelectable(true)
            setLineSpacing(0f, 1.25f)
            background = rounded(pal.button, dp(18f))
            setPadding(dp(14f), dp(10f), dp(14f), dp(10f))
            maxWidth = (resources.displayMetrics.widthPixels * 0.85f).toInt()
        }
        return LinearLayout(this).apply {
            gravity = Gravity.END
            setPadding(0, dp(12f), 0, dp(4f))
            addView(t, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    /** Claude's answer: plain text across the card, like a chat reply. */
    private fun replyText(text: CharSequence): TextView = label(text, 15f, pal.text).apply {
        japanese()
        setTextIsSelectable(true)
        setLineSpacing(0f, 1.25f)
        setPadding(0, dp(10f), 0, dp(4f))
    }

    private fun scrollToEnd() {
        scroll.post { if (!destroyed) scroll.fullScroll(View.FOCUS_DOWN) }
    }

    /**
     * An AI answer in the card's look (Rich): 【】 labels in the accent colour, bullets with a hanging indent,
     * translation lines in grey, bold, and readings small and grey with the Hangul pronunciation in the accent.
     */
    private fun rich(text: String): CharSequence {
        val doc = Rich.parse(text)
        val s = SpannableStringBuilder(doc.text)
        val indent = dp(14f)
        fun span(what: Any, m: Rich.Mark) = s.setSpan(what, m.start, m.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        for (m in doc.marks) when (m.kind) {
            Rich.Kind.LABEL -> {
                span(ForegroundColorSpan(pal.accent), m)
                span(StyleSpan(Typeface.BOLD), m)
            }
            Rich.Kind.BOLD -> span(StyleSpan(Typeface.BOLD), m)
            Rich.Kind.BULLET -> span(LeadingMarginSpan.Standard(0, indent), m)
            Rich.Kind.NOTE -> span(ForegroundColorSpan(pal.sub), m)
            Rich.Kind.READING -> {
                span(RelativeSizeSpan(0.82f), m)
                span(ForegroundColorSpan(pal.sub), m)
            }
            Rich.Kind.HANGUL -> span(ForegroundColorSpan(pal.accent), m)
        }
        return s
    }

    /** Coalesces streaming tokens and never pulls the reader down while they inspect an earlier line. */
    private inner class StreamReply(private val view: TextView, private val active: () -> Boolean) {
        private val lock = Any()
        private val text = StringBuilder()
        private var queued = false
        private val render = Runnable {
            val value = synchronized(lock) { queued = false; text.toString() }
            if (!destroyed && active()) {
                val atEnd = !scroll.canScrollVertically(1)
                view.setTextColor(pal.text)
                view.text = rich(value)
                if (atEnd) scrollToEnd()
            }
        }
        fun append(piece: String) = synchronized(lock) {
            text.append(piece)
            if (!queued) {
                queued = true
                main.postDelayed(render, 50)
            }
        }
        fun finish(): String {
            main.removeCallbacks(render)
            return synchronized(lock) { queued = false; text.toString() }
        }
    }

    private fun sendChat() {
        val text = input.text.toString().trim()
        if (text.isEmpty() || chatClient != null) return
        val key = Prefs.apiKey(this)
        if (key.isEmpty()) {
            needKey()
            return
        }
        input.setText("")
        chatLog.addView(myBubble(text))
        // The bubble shows what was typed; the first question also carries the card's selection and sentence,
        // which stay out of the system prompt so that prompt is the same for every card (cacheable).
        val content = if (chatTurns.length() == 0) KanjiText.chatContext(selection, context) + text else text
        chatTurns.put(JSONObject().put("role", "user").put("content", content))
        val reply = replyText("…")
        chatLog.addView(reply)
        scrollToEnd()
        val client = ClaudeClient(key, Prefs.chatModel(this))
        chatClient = client
        sendButton.isEnabled = false
        val stream = StreamReply(reply) { chatClient === client }
        val turns = JSONArray(chatTurns.toString())
        val system = KanjiText.CHAT_SYSTEM_PROMPT
        Thread {
            // conversation: the history is cached, so a follow-up question does not re-read it in full.
            val problem = client.run(system, turns, Prefs.chatEffort(applicationContext), conversation = true, onText = stream::append)
            main.post {
                if (destroyed || chatClient !== client) return@post
                val got = stream.finish()
                reply.text = if (got.isNotEmpty()) rich(got) else problem ?: "응답이 비어 있습니다. 다시 보내 주세요."
                chatClient = null
                sendButton.isEnabled = true
                if (got.isNotEmpty()) {
                    chatTurns.put(JSONObject().put("role", "assistant").put("content", got))
                } else {
                    // Nothing came back: drop the question so the next one still alternates user/assistant.
                    chatTurns.remove(chatTurns.length() - 1)
                }
                if (problem != null) {
                    reply.setTextColor(if (got.isEmpty()) pal.accent else pal.text)
                    reply.text = if (got.isEmpty()) problem else rich("$got\n\n$problem")
                }
            }
        }.start()
    }

    private fun askClaude() {
        val key = Prefs.apiKey(this)
        if (key.isEmpty()) {
            needKey()
            return
        }
        claude?.cancel()
        val client = ClaudeClient(key, Prefs.chatModel(this))
        claude = client
        claudeButton.isEnabled = false
        answer.visibility = View.VISIBLE
        answer.setTextColor(pal.sub)
        answer.text = "Claude가 답하는 중…"
        val stream = StreamReply(answer) { claude === client }
        Thread {
            val turns = JSONArray().put(JSONObject().put("role", "user").put("content", KanjiText.userPrompt(selection, context)))
            val problem = client.run(KanjiText.SYSTEM_PROMPT, turns, Prefs.chatEffort(applicationContext), onText = stream::append)
            main.post {
                if (destroyed || claude !== client) return@post
                val text = stream.finish()
                answer.text = if (text.isNotEmpty()) rich(text) else problem ?: "응답이 비어 있습니다. 다시 눌러 주세요."
                claude = null
                claudeButton.isEnabled = true
                claudeButton.text = "다시 묻기"
                if (problem != null) {
                    if (text.isEmpty()) answer.setTextColor(pal.accent)
                    answer.text = if (text.isEmpty()) problem else rich("$text\n\n$problem")
                }
            }
        }.start()
    }
}
