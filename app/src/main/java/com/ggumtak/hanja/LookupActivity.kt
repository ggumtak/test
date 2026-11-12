package com.ggumtak.hanja

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.graphics.Rect
import android.graphics.Typeface
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.StyleSpan
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
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
    /**
     * A window wide and tall enough (CardLayout): the card splits into the text on the left and, on the right, a
     * pane that stays put while the text scrolls, showing one kanji's brush drawing and, on a tap, its stroke order.
     * Chosen again when the window changes (split screen, rotation) or the keyboard opens or closes.
     */
    private var tablet = false
    /** The side of the pane's drawing, dp. */
    private var paneDp = CardLayout.PANE_MIN
    private var keyboardShown = false
    private lateinit var strokePane: LinearLayout
    private lateinit var strokeHolder: FrameLayout
    private lateinit var strokeChips: LinearLayout
    /** The kanji the pane shows. */
    private var strokeChar = ""
    /** The parts and parsed stroke shapes of the kanji on this card: fill and every stroke view of a kanji share them. */
    private val partsMemo = HashMap<String, Parts?>()
    private val shapesMemo = HashMap<String, KanjiStrokeView.Shapes>()
    /** The kanji rows as built, so a refill changes only what changed (an opened stroke order keeps playing). */
    private val entryViews = ArrayList<EntryViews>()
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
        chooseLayout()
        setContentView(build())
        applyLayout()
        // The keyboard opening takes the room the pane had: watch for it (the window itself is resized).
        window.decorView.setOnApplyWindowInsetsListener { v, insets ->
            val out = v.onApplyWindowInsets(insets)
            val shown = if (Build.VERSION.SDK_INT >= 30) insets.isVisible(WindowInsets.Type.ime())
                else @Suppress("DEPRECATION") insets.systemWindowInsetBottom > dp(160f)
            if (shown != keyboardShown) {
                keyboardShown = shown
                v.post { if (!destroyed && chooseLayout()) applyLayout() }
            }
            out
        }
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
            runCatching { UsageStore.load(app) }
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

    /** The window changed (split screen resized, rotation): the card is not recreated, so it lays itself out again. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (chooseLayout()) applyLayout()
    }

    /** Sets [tablet] and [paneDp] for the window as it is now; true when they changed. */
    private fun chooseLayout(): Boolean {
        val c = resources.configuration
        val choice = CardLayout.choose(c.screenWidthDp, c.screenHeightDp, keyboardShown)
        val pane = if (choice.split) choice.paneDp else paneDp
        val changed = choice.split != tablet || pane != paneDp
        tablet = choice.split
        paneDp = pane
        return changed
    }

    /**
     * Puts the card in the layout [chooseLayout] chose: with the pane, the panels opened under the rows close and
     * the pane shows the kanji that was open (or the one it showed, or the first); without it, the pane hides and a
     * tap on a kanji opens its panel again.
     */
    private fun applyLayout() {
        strokeHolder.layoutParams = LinearLayout.LayoutParams(dp(paneDp.toFloat()), dp(paneDp.toFloat()))
        strokePane.layoutParams = (strokePane.layoutParams as LinearLayout.LayoutParams).apply { width = paneWidth() }
        if (!tablet) {
            strokePane.visibility = View.GONE
            return
        }
        val open = entryViews.firstOrNull { it.panel.visibility == View.VISIBLE }?.ch
        for (e in entryViews) e.panel.visibility = View.GONE
        if (entryViews.isEmpty()) return
        val first = open ?: strokeChar.takeIf { it in kanji } ?: kanji.firstOrNull { drawable(it) != null }
        if (first != null) showStrokes(first, play = false)
    }

    /** The pane: as wide as its drawing and padding, so the kanji buttons scroll under it rather than widen it. */
    private fun paneWidth(): Int = dp(paneDp.toFloat()) + dp(16f)

    /** [ch]'s parts, read once per card. */
    private fun partsOf(ch: String): Parts? {
        if (!partsMemo.containsKey(ch)) partsMemo[ch] = PartsStore.parts(ch)
        return partsMemo[ch]
    }

    /** [ch]'s parts when it can be drawn from strokes. */
    private fun drawable(ch: String): Parts? = partsOf(ch)?.takeIf { it.strokes.isNotEmpty() }

    private fun shapesOf(ch: String, p: Parts): KanjiStrokeView.Shapes = shapesMemo.getOrPut(ch) { KanjiStrokeView.Shapes(p) }

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
        // shrinks, so the question input and the buttons under it always stay visible. The text on the left, the
        // stroke-order pane on the right (it does not scroll with the text), hidden in the one-column layout.
        card.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(scroll, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(strokePane(), LinearLayout.LayoutParams(paneWidth(), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = dp(16f)
                gravity = Gravity.TOP
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (kanji.isNotEmpty()) entries.addView(label("불러오는 중…", 15f, pal.sub))
        card.addView(chatInput())
        if (hasWord()) card.addView(vocabButton())
        card.addView(buttons())
        return LinearLayout(this).apply {
            setPadding(dp(10f), 0, dp(10f), dp(10f))
            addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun strokeColors() = KanjiStrokeView.Colors(ghost = pal.line,
        labelFill = (pal.card and 0x00FFFFFF) or (0xEB shl 24), labelEdge = pal.sub, labelText = pal.text)

    /** The radical in the accent colour, the sound part in blue, the rest in the text colour. */
    private fun strokeColor(tag: Char): Int = when (tag) {
        Parts.RADICAL -> pal.accent
        Parts.SOUND -> pal.sound
        else -> pal.text
    }

    /** The right-hand pane: a drawing area, a hint and, for two or more kanji, one button per kanji. */
    private fun strokePane(): View {
        strokeHolder = FrameLayout(this)
        strokeChips = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        strokePane = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            visibility = View.GONE
            setPadding(dp(8f), dp(8f), dp(8f), dp(10f))
            background = rounded(pal.button, dp(16f))
            addView(strokeHolder, LinearLayout.LayoutParams(dp(paneDp.toFloat()), dp(paneDp.toFloat())))
            addView(label("그림을 누르면 획순", 12f, pal.sub).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(4f), 0, dp(6f))
            })
            addView(HorizontalScrollView(this@LookupActivity).apply {
                isHorizontalScrollBarEnabled = false
                addView(strokeChips)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        return strokePane
    }

    /**
     * With the pane: shows [ch]'s brush drawing in it (and writes its stroke order when [play]); the kanji buttons
     * under it mark which one is shown.
     */
    private fun showStrokes(ch: String, play: Boolean) {
        if (!tablet) return
        val parts = drawable(ch) ?: return
        if (ch != strokeChar || strokeHolder.childCount == 0) {
            strokeChar = ch
            strokeHolder.removeAllViews()
            strokeHolder.addView(KanjiStrokeView(this, shapesOf(ch, parts), dp(paneDp.toFloat()), strokeColors(),
                numbered = true, colorOf = ::strokeColor).apply {
                contentDescription = "$ch 획순, 누르면 다시"
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        strokePane.visibility = View.VISIBLE
        // One button per kanji that has a drawing, when there are two or more.
        val drawable = kanji.filter { drawable(it) != null }
        strokeChips.removeAllViews()
        if (drawable.size > 1) for (c in drawable) {
            strokeChips.addView(label(c, 22f, if (c == strokeChar) pal.accent else pal.text, bold = c == strokeChar).apply {
                japanese()
                gravity = Gravity.CENTER
                minWidth = dp(40f)
                setPadding(dp(8f), dp(2f), dp(8f), dp(2f))
                background = if (c == strokeChar) rounded(pal.card, dp(10f), dp(1f), pal.accent) else null
                setOnClickListener { showStrokes(c, play = true) }
            })
        }
        if (play) (strokeHolder.getChildAt(0) as? KanjiStrokeView)?.let { v -> v.post { v.play() } }
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
            s.append(if (!kana) "(" + wordHangul(selection, r) + ")" else KanaHangul.convert(r))
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
                wordNote.text = "사전 읽기 $r(${wordHangul(word.form, r)}) · 한자별 읽기는 확인되지 않았습니다."
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
        fun ctxMeaning(ch: String, k: Kanji?) = contextReading?.meanings?.get(ch)
            ?.takeIf { ContextReading.acceptable(it, k?.hunJa?.ifEmpty { k.hun } ?: "") }
        if (entryViews.size == kanji.size) {
            // A refill (the saved or the new context reading came): only the 훈음 line and the "단어" reading change,
            // so the rows stay as they are and a stroke order opened under one keeps playing.
            for (e in entryViews) updateEntry(e, inWord[e.ch], ctxMeaning(e.ch, e.k))
        } else {
            entries.removeAllViews()
            entryViews.clear()
            kanji.forEachIndexed { i, ch ->
                if (i > 0) entries.addView(View(this).apply { setBackgroundColor(pal.line) },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
                val k = KanjiStore.get(ch)
                entries.addView(entry(ch, k, inWord[ch], ctxMeaning(ch, k)))
            }
        }
        // With the pane: it keeps the kanji it shows across refills, else shows the first with a drawing.
        if (tablet) {
            val first = strokeChar.takeIf { it in kanji } ?: kanji.firstOrNull { drawable(it) != null }
            if (first != null) showStrokes(first, play = false)
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

    /** What a refill changes in a kanji row (the 훈음 line, the "단어" reading), and its stroke-order panel. */
    private class EntryViews(
        val ch: String,
        val k: Kanji?,
        val meaning: TextView,
        val english: TextView?,
        val wordSlot: LinearLayout,
        /** The stroke order under the row, without the pane; its drawing is made on the first tap. */
        val panel: LinearLayout,
    )

    private fun updateEntry(e: EntryViews, inWord: String?, ctxMeaning: String?) {
        val meaning = meaningText(e.k, ctxMeaning)
        e.meaning.text = meaning
        e.english?.visibility = if (meaning.toString() != e.k?.meanings) View.VISIBLE else View.GONE
        e.wordSlot.removeAllViews()
        if (inWord != null) {
            val s = SpannableStringBuilder(inWord)
            hangul(s, inWord)
            e.wordSlot.addView(readingRow("단어", s))
        }
    }

    private fun entry(ch: String, k: Kanji?, inWord: String?, ctxMeaning: String?): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8f), 0, dp(8f))
            background = selectableBackground()
            setOnClickListener { open(KanjiText.naverHanjaUrl(ch)) }
        }
        val parts = partsOf(ch)
        // Without the pane, the stroke-order panel under the row, opened by a tap on the kanji.
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            visibility = View.GONE
            setPadding(0, 0, 0, dp(10f))
        }
        if (parts != null && parts.strokes.isNotEmpty()) {
            // Drawn from its strokes (brush outlines for the 상용한자): the radical in the accent colour, the sound
            // part in blue. A tap on it shows the stroke order: in the pane on the right when the card has one, in
            // the panel under the row otherwise (a tap elsewhere on the row still opens the dictionary).
            val small = KanjiStrokeView(this, shapesOf(ch, parts), dp(68f), strokeColors(),
                colorOf = ::strokeColor).apply {
                contentDescription = "$ch, 누르면 획순"
                setPadding(dp(2f), dp(4f), dp(2f), 0)
            }
            small.setOnClickListener {
                if (tablet) showStrokes(ch, play = true) else togglePanel(ch, parts, panel)
            }
            // The kanji with a small "획순" under it, so it is clear that it can be tapped.
            row.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                addView(small, LinearLayout.LayoutParams(dp(72f), dp(72f)))
                addView(label("획순 ▸", 11f, pal.sub).apply {
                    gravity = Gravity.CENTER
                    setOnClickListener { small.performClick() }
                })
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { gravity = Gravity.TOP })
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
        val meaningView = label("", 20f, pal.text, bold = true).apply { setLineSpacing(0f, 1.1f) }
        col.addView(meaningView)
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
        val wordSlot = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(wordSlot)
        val usage = if (k != null) UsageStore.get(ch) else null
        if (k != null && usage != null) {
            // A 상용한자: the readings worth learning, each with a common word, and the rest behind "더 보기".
            addUsageRows(col, ch, usage.plan(k.on, k.kun))
        } else if (k != null) {
            val on = KanjiText.onReadings(k.on)
            if (on.isNotEmpty()) col.addView(readingRow("음", plainOn(on)))
            val kun = KanjiText.kunReadings(k.kun, distinctWords = true)
            if (kun.isNotEmpty()) col.addView(readingRow("훈", plainKun(kun)))
        }
        addPartRows(col, ch, k, parts)
        var english: TextView? = null
        if (k != null) {
            // The count the stroke order goes up to, when the drawing has its own (僅: 13, where KANJIDIC says 12).
            val meta = listOf(
                Parts.strokeLabel(k.strokes, parts?.strokes?.size ?: 0),
                k.gradeLabel,
            ).filter { it.isNotEmpty() }.joinToString(DOT)
            if (meta.isNotEmpty()) col.addView(label(meta, 13f, pal.sub).apply { setPadding(0, dp(6f), 0, 0) })
            if (k.meanings.isNotEmpty()) english = englishLine(k).also { col.addView(it) }
        }
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val e = EntryViews(ch, k, meaningView, english, wordSlot, panel)
        entryViews.add(e)
        updateEntry(e, inWord, ctxMeaning)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(row)
            addView(panel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    /**
     * Without the pane: opens [ch]'s stroke order under its row and writes it, or closes it. The big drawing is
     * made on the first open (most rows are never opened) and sized for the window as it is then: at most 200dp,
     * narrower than the card, and under half the height on a low window.
     */
    private fun togglePanel(ch: String, parts: Parts, panel: LinearLayout) {
        if (panel.visibility == View.VISIBLE) {
            panel.visibility = View.GONE
            return
        }
        val m = resources.displayMetrics
        val size = minOf(dp(200f), m.widthPixels - dp(100f), (m.heightPixels * 0.4f).toInt()).coerceAtLeast(dp(96f))
        var big = panel.getChildAt(0) as? KanjiStrokeView
        if (big == null) {
            big = KanjiStrokeView(this, shapesOf(ch, parts), size, strokeColors(), numbered = true,
                colorOf = ::strokeColor).apply { contentDescription = "$ch 획순, 누르면 다시" }
            panel.addView(big, LinearLayout.LayoutParams(size, size))
            panel.addView(label("획순 · 그림을 누르면 다시 씁니다", 12f, pal.sub).apply { setPadding(0, dp(4f), 0, 0) })
        } else if (big.layoutParams.width != size) {
            big.layoutParams = LinearLayout.LayoutParams(size, size)
        }
        panel.visibility = View.VISIBLE
        big.play()
        // Bring the panel into view: it opens below the row, often under the visible part.
        panel.post { panel.requestRectangleOnScreen(Rect(0, 0, panel.width, panel.height), false) }
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

    /** "メイ(메-) · ミョウ(묘-)": on readings in a row. */
    private fun plainOn(on: List<String>): CharSequence {
        val s = SpannableStringBuilder()
        on.forEachIndexed { i, r ->
            if (i > 0) s.append(DOT)
            s.append(r)
            hangul(s, r)
        }
        return s
    }

    /** "あか(るい)(아카루이) · あ(ける)(아케루)": kun readings in a row, the okurigana in grey. */
    private fun plainKun(kun: List<KanjiText.Kun>): CharSequence {
        val s = SpannableStringBuilder()
        kun.forEachIndexed { i, r ->
            if (i > 0) s.append(DOT)
            appendKun(s, r.stem, r.okurigana)
        }
        return s
    }

    /** Appends "あか" + grey "るい" + "(아카루이)". */
    private fun appendKun(s: SpannableStringBuilder, stem: String, okurigana: String) {
        s.append(stem)
        if (okurigana.isNotEmpty()) {
            val start = s.length
            s.append(okurigana)
            s.setSpan(ForegroundColorSpan(pal.sub), start, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        // The okurigana is a stretch of its own: 思う is 오모우 (the う is said), 大きい 오-키-.
        val start = s.length
        s.append("(").append(KanaHangul.convertParts(listOf(stem, okurigana))).append(")")
        s.setSpan(ForegroundColorSpan(pal.accent), start, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        s.setSpan(RelativeSizeSpan(0.82f), start, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    /**
     * The Hangul for [reading] of the word [form]: a long vowel only inside one kanji's reading or inside the kana
     * between kanji, never across them (子牛 こ + うし 코우시, 学校 がっ + こう 갓코-); as one stretch when the
     * reading cannot be split by character (今日 きょう 쿄-).
     */
    private fun wordHangul(form: String, reading: String): String =
        KanaHangul.ofWord(form, Readings.split(form, reading) { ch -> KanjiStore.get(ch)?.let(Readings::candidates) },
            reading)

    /**
     * The 음 and 훈 rows of a 상용한자 ([Usage.plan]): one reading per line, the ones a learner meets soonest first,
     * each followed by a common word that uses it, since a reading sticks through its words (セイ 学生 학생; a kun
     * reading's own word needs only its meaning: た(べる) 먹다). The remaining readings wait behind a "더 보기" line:
     * the rest of the 상용한자표's, then the dictionary's rare ones in grey.
     */
    private fun addUsageRows(col: LinearLayout, ch: String, plan: Usage.Plan) {
        if (plan.on.isNotEmpty()) col.addView(readingRow("음", usageText(ch, plan.on, kun = false)))
        if (plan.kun.isNotEmpty()) col.addView(readingRow("훈", usageText(ch, plan.kun, kun = true)))
        if (plan.hidden == 0) return
        val more = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        val closed = "읽기 더 보기 (${plan.hidden})  ▸"
        val link = readingRow("", closed, 13f)
        val linkText = link.getChildAt(1) as TextView
        linkText.setTextColor(pal.accent)
        link.background = selectableBackground()
        link.contentDescription = "이 한자의 나머지 읽기 ${plan.hidden}개, 누르면 펼침"
        link.setOnClickListener {
            if (more.visibility == View.VISIBLE) {
                more.visibility = View.GONE
                linkText.text = closed
                return@setOnClickListener
            }
            // Made on the first tap: most cards never open it.
            if (more.childCount == 0) {
                if (plan.moreOn.isNotEmpty()) more.addView(readingRow("음", usageText(ch, plan.moreOn, kun = false), 15f))
                if (plan.moreKun.isNotEmpty()) more.addView(readingRow("훈", usageText(ch, plan.moreKun, kun = true), 15f))
                if (plan.otherOn.isNotEmpty() || plan.otherKun.isNotEmpty()) {
                    // The dictionary's other readings: not in the 상용한자표 and in no common word.
                    val s = SpannableStringBuilder()
                    if (plan.otherOn.isNotEmpty()) s.append(plainOn(plan.otherOn))
                    if (plan.otherKun.isNotEmpty()) {
                        if (s.isNotEmpty()) s.append(DOT)
                        s.append(plainKun(plan.otherKun))
                    }
                    more.addView(readingRow("드묾", s, 14f).apply {
                        (getChildAt(1) as TextView).setTextColor(pal.sub)
                    })
                }
            }
            more.visibility = View.VISIBLE
            linkText.text = "읽기 접기  ▾"
        }
        col.addView(link)
        col.addView(more, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    /**
     * One line per reading: "セイ(세-)  学生(がくせい 가쿠세-) 학생 · 先生(せんせい 센세-) 선생님" or, for a kun reading
     * with its own word, "た(べる)(타베루)  먹다". A wrapped line hangs under the reading. A reading outside the
     * 상용한자표 says so.
     */
    private fun usageText(ch: String, readings: List<Usage.Reading>, kun: Boolean): CharSequence {
        val s = SpannableStringBuilder()
        val indent = dp(12f)
        for ((i, r) in readings.withIndex()) {
            if (i > 0) s.append("\n")
            val line = s.length
            if (kun) appendKun(s, r.stem, r.okurigana) else {
                s.append(r.reading)
                hangul(s, r.reading)
            }
            if (r.outside) {
                val at = s.length
                s.append(" 표 밖")
                s.setSpan(ForegroundColorSpan(pal.sub), at, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                s.setSpan(RelativeSizeSpan(0.72f), at, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val words = s.length
            for ((j, w) in r.words.withIndex()) {
                s.append(if (j == 0) "  " else DOT)
                if (!(kun && r.isOwn(ch, w))) {
                    s.append(w.form)
                    val at = s.length
                    s.append("(").append(w.kana).append(" ")
                    val h = s.length
                    s.append(wordHangul(w.form, w.kana))
                    s.setSpan(ForegroundColorSpan(pal.sub), at, h, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    s.setSpan(ForegroundColorSpan(pal.accent), h, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    s.append(")")
                    s.setSpan(ForegroundColorSpan(pal.sub), s.length - 1, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    s.setSpan(RelativeSizeSpan(0.85f), at, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    if (w.meaning.isNotEmpty()) s.append(" ")
                }
                s.append(w.meaning)
            }
            if (s.length > words) s.setSpan(RelativeSizeSpan(0.88f), words, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            s.setSpan(LeadingMarginSpan.Standard(0, indent), line, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return s
    }

    /** Appends "(메-)" for [kana], smaller and in the accent colour. */
    private fun hangul(s: SpannableStringBuilder, kana: String) {
        val start = s.length
        s.append("(").append(KanaHangul.convert(kana)).append(")")
        s.setSpan(ForegroundColorSpan(pal.accent), start, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        s.setSpan(RelativeSizeSpan(0.82f), start, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    /** "음  メイ(메-) · ミョウ(묘-)": a small label column, then the readings in the Japanese font. */
    private fun readingRow(name: String, value: CharSequence, sp: Float = 17f): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(5f), 0, 0)
        addView(label(name, 13f, pal.accent, bold = true).apply {
            minWidth = nameWidth(paint)
            setPadding(0, dp(if (sp >= 17f) 2f else 1f), 0, 0)
        })
        addView(label(value, sp, pal.text).apply {
            japanese()
            setLineSpacing(0f, 1.15f)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    /** The label column: as wide as its longest name ("소리 계열") and a gap, so every row's value starts in line. */
    private var nameWidthPx = 0

    private fun nameWidth(paint: android.graphics.Paint): Int {
        if (nameWidthPx == 0) nameWidthPx = maxOf(dp(44f), (paint.measureText("소리 계열") + dp(8f)).toInt())
        return nameWidthPx
    }

    /**
     * What helps the kanji stick: its radical (부수), its parts with the radical and the sound part in the colours of
     * the stroke drawing (구성), other kanji with the same sound part and reading (소리 계열, a tap compares them
     * drawn side by side) and, for the 상용한자, how it came to its shape in plain words (유래) and a memory story
     * (외우기).
     */
    private fun addPartRows(col: LinearLayout, ch: String, k: Kanji?, p: Parts?) {
        val origin = PartsStore.origin(ch)
        // The 구성 line as shown from the 유래's own words, when it is: the 유래 row then leaves out what it repeats.
        var shownKosei: List<Kosei.Unit>? = null
        if (p != null) {
            // The 구성 line written to match the 유래 (상용한자), else the one built from the parts; "-" is none at all
            // (凸: its KanjiVG parts are not what one sees).
            val none = origin?.kosei == "-"
            // A free line ("!") may give parts KanjiVG keeps as one (先: ⺧ + 儿).
            val kosei = origin?.kosei?.takeIf { !none }?.let(Kosei::parse)
                ?.takeIf { p.parts.size >= 2 || origin.kosei.startsWith("!") }
            PartsStore.radical(p)?.let { r ->
                // A tap lists other easy kanji with this radical, so its name becomes a rule (氵: 池 海 泳 …).
                val mates = PartsStore.sameRadical(p, ch)
                val row = readingRow("부수", radicalText(ch, p, r, kosei, more = mates.isNotEmpty()), 15f)
                col.addView(row)
                if (mates.isNotEmpty()) {
                    val list = readingRow("", "", 14.5f).apply { visibility = View.GONE }
                    row.background = selectableBackground()
                    row.contentDescription = "부수, 누르면 같은 부수의 한자"
                    row.setOnClickListener {
                        if (list.visibility == View.VISIBLE) {
                            list.visibility = View.GONE
                            return@setOnClickListener
                        }
                        val text = list.getChildAt(1) as TextView
                        if (text.text.isEmpty()) text.text = matesText(radicalShape(p, r), r, mates)
                        list.visibility = View.VISIBLE
                    }
                    col.addView(list)
                }
            }
            if (!none) (kosei?.let { koseiText(p, it, k) } ?: partsText(p))?.let { col.addView(readingRow("구성", it, 15f)) }
            if (!none) shownKosei = kosei
            val family = if (k == null) emptyList() else familyItems(k, p)
            familyText(family, familyHead(p, kosei))?.let { text ->
                val compare = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    visibility = View.GONE
                    // A tap between the drawings stays here rather than open the dictionary like the rest of the row.
                    isClickable = true
                }
                col.addView(readingRow("소리 계열", text, 15f).apply {
                    background = selectableBackground()
                    contentDescription = "같은 소리 부분의 한자, 누르면 나란히 비교"
                    setOnClickListener { toggleCompare(ch, k!!, p, family, compare) }
                })
                col.addView(compare, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            // 닮은 한자: the kanji mistaken for this one by shape (持: 待 特), whatever their reading, each with its
            // 훈음 so the difference in meaning shows at once; a tap draws two of them side by side.
            val alike = PartsStore.similar(ch).filter { drawable(it) != null }
            if (alike.isNotEmpty() && drawable(ch) != null) {
                val box = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    visibility = View.GONE
                    isClickable = true
                }
                col.addView(readingRow("닮은 한자", similarText(alike), 15f).apply {
                    background = selectableBackground()
                    contentDescription = "모양이 닮은 한자, 누르면 나란히 비교"
                    setOnClickListener {
                        if (box.visibility == View.VISIBLE) {
                            box.visibility = View.GONE
                            return@setOnClickListener
                        }
                        if (box.childCount == 0) buildSimilar(ch, alike, box)
                        box.visibility = View.VISIBLE
                        box.post { box.requestRectangleOnScreen(Rect(0, 0, box.width, box.height), false) }
                    }
                })
                col.addView(box, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT))
            }
        }
        origin?.let { o ->
            // "뜻+소리: 뜻은 氵(물), 소리는 毎(マイ). …": the type in plain words, then the note.
            // Without what the 구성 line just said ("뜻은 氵(물), 소리는 毎(マイ).") and without a bare repeat of the 훈음.
            val body = o.shown(shownKosei, listOfNotNull(k?.hunJa, k?.hun, k?.gloss))
            if (body.isNotEmpty()) {
                val s = SpannableStringBuilder()
                if (o.label.isNotEmpty()) {
                    s.append(o.label).append(":")
                    s.setSpan(ForegroundColorSpan(pal.accent), 0, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    s.setSpan(StyleSpan(Typeface.BOLD), 0, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    s.append(" ")
                }
                s.append(body)
                col.addView(readingRow("유래", s, 14.5f))
            }
            if (o.memo.isNotEmpty()) col.addView(readingRow("외우기", o.memo, 14.5f))
        }
    }

    /**
     * The radical as it is drawn in this kanji when the table names that shape (亻), else the radical's main form.
     * 曰 (가로 왈, 73) stays 曰 although KanjiVG draws it as 日, which is another radical (날 일, 72): 書, 書's 유래
     * and the 구성 line all call it 曰.
     */
    private fun radicalShape(p: Parts, r: Radical): String = when {
        r.number == 73 -> "曰"
        p.form.isNotEmpty() && p.form == r.form -> p.form
        else -> r.form
    }

    /**
     * "氵 삼수변(물) · さんずい(산즈이)": the radical as it looks here in the accent colour, its Korean and Japanese
     * names. When the radical is the sound part or lies inside it (到's 刂, 修's 亻), it is only where dictionaries
     * file the kanji, not the part that gives the meaning: the line says so.
     */
    private fun radicalText(ch: String, p: Parts, r: Radical, kosei: List<Kosei.Unit>?, more: Boolean = false): CharSequence {
        val s = SpannableStringBuilder()
        val shape = radicalShape(p, r)
        s.append(shape)
        s.setSpan(ForegroundColorSpan(pal.accent), 0, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        s.setSpan(StyleSpan(Typeface.BOLD), 0, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        s.append("  ").append(r.korean)
        if (r.meaning.isNotEmpty()) s.append("(").append(r.meaning).append(")")
        s.append(DOT).append(r.japanese)
        hangul(s, r.japanese)
        // The radical only gives the sound here (or sits inside the sound part): it just files the kanji in
        // dictionaries. When the 구성 line calls the radical's part something else (会's 人 = 뚜껑), say so here,
        // so the two rows do not seem to disagree.
        val inSound = kosei?.let(Kosei::radicalOnlySound) ?: p.radicalInSound
        val here = kosei?.let { Kosei.radicalHere(it, r.korean, r.meaning) }
        val note = when {
            shape == ch -> "글자 자체가 부수"
            inSound -> "사전 분류용(여기서는 소리 쪽)"
            here != null -> "여기서는 '$here'"
            else -> null
        }
        if (note != null) {
            val start = s.length
            s.append(DOT).append(note)
            s.setSpan(ForegroundColorSpan(pal.sub), start, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (more) {
            val start = s.length
            s.append("  같은 부수 ▸")
            s.setSpan(ForegroundColorSpan(pal.accent), start, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            s.setSpan(RelativeSizeSpan(0.8f), start, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return s
    }

    /**
     * "같은 부수 氵(물)의 한자 / 池 못 지 · 海 바다 해 · 泳 헤엄칠 영": the kanji that share the radical, each with its 훈음,
     * so the radical's meaning is seen at work in several of them.
     */
    private fun matesText(shape: String, r: Radical, mates: List<String>): CharSequence {
        val s = SpannableStringBuilder("같은 부수 ")
        val at = s.length
        s.append(shape)
        val head = s.length
        s.append("(").append(r.meaning.ifEmpty { r.korean }).append(")의 한자\n")
        s.setSpan(ForegroundColorSpan(pal.sub), 0, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        s.setSpan(ForegroundColorSpan(pal.accent), at, head, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        s.setSpan(RelativeSizeSpan(0.88f), 0, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        mates.forEachIndexed { i, c ->
            if (i > 0) s.append(DOT)
            s.append(c)
            val hun = firstHun(c)
            if (hun.isNotEmpty()) {
                val start = s.length
                s.append(" ").append(hun)
                s.setSpan(ForegroundColorSpan(pal.sub), start, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                s.setSpan(RelativeSizeSpan(0.82f), start, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return s
    }

    /**
     * "氵(물) + 毎(매양 매 · 소리)", the radical and the sound part coloured as in the stroke drawing; null for one part.
     * Each part keeps its own meaning: a part that only holds the radical or the sound part says so after it
     * ("旦(아침 단 · 안에 부수 日)", "涂(… · 안에 소리 余)") instead of taking the radical's name, and a part that is
     * both the radical and the sound part says both ("刂(칼 · 부수 · 소리)"), as the colour can show only one.
     */
    private fun partsText(p: Parts): CharSequence? {
        if (p.parts.size < 2) return null
        val s = SpannableStringBuilder()
        // A few parts are rare characters the phone's fonts lack (傷's 𬀷): their full form or the pieces they are
        // drawn with (氐 → 氏+一), else a word.
        val glyphs = android.graphics.Paint()
        fun drawable(x: String) = x.isNotEmpty() && glyphs.hasGlyph(x)
        val radical = PartsStore.radical(p)
        val radShape = radical?.let { radicalShape(p, it) } ?: p.form
        p.parts.forEachIndexed { i, part ->
            if (i > 0) s.append(" + ")
            val start = s.length
            val pieces = part.original.split('+').filter { it.isNotEmpty() }
            s.append(when {
                drawable(part.element) -> part.element
                pieces.size == 1 && drawable(part.original) -> part.original
                pieces.size > 1 && pieces.all(::drawable) -> pieces.joinToString("+")
                else -> "부품"
            })
            val color = when {
                part.radical -> pal.accent
                part.sound -> pal.sound
                else -> null
            }
            if (color != null) s.setSpan(ForegroundColorSpan(color), start, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val gloss = if (part.radical) radical?.let { it.meaning.ifEmpty { it.korean } }.orEmpty()
            // A part shaped like a radical gets that radical's plain meaning (辶 가다·길, 疒 병) rather than its 훈
            // (책받침, 병질), which a learner does not know.
            else PartsStore.meaningOf(part.element)
                ?: firstHun(part.element).ifEmpty { if (pieces.size == 1) firstHun(part.original) else "" }
            // Each note, with the part of it to colour (the radical or the sound part named inside).
            val notes = ArrayList<Pair<String, Pair<String, Int>?>>()
            if (gloss.isNotEmpty()) notes.add(gloss to null)
            if (part.radical && part.sound) notes.add("부수" to null)
            if (part.sound) notes.add("소리" to null)
            if (part.radicalInside && drawable(radShape)) notes.add("안에 부수 $radShape" to (radShape to pal.accent))
            if (part.soundInside) {
                if (drawable(part.inner)) notes.add("안에 소리 ${part.inner}" to (part.inner to pal.sound))
                else notes.add("일부가 소리" to null)
            }
            if (notes.isNotEmpty()) {
                val from = s.length
                s.append("(")
                notes.forEachIndexed { j, (text, mark) ->
                    if (j > 0) s.append(DOT)
                    val at = s.length
                    s.append(text)
                    if (mark != null) {
                        val m = at + text.lastIndexOf(mark.first)
                        s.setSpan(ForegroundColorSpan(mark.second), m, m + mark.first.length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
                s.append(")")
                s.setSpan(ForegroundColorSpan(pal.sub), from, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                s.setSpan(RelativeSizeSpan(0.85f), from, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return s
    }

    /**
     * The 구성 line written to match the 유래: "聿(붓 · 뜻) + 曰(者를 줄인 꼴 · 소리 シャ)", the radical's part in the
     * accent colour and sound parts in blue, as in the stroke drawing. A part that is both the radical and the sound
     * part also says 부수; a part that only holds the radical says so when its note does not.
     */
    private fun koseiText(p: Parts, units: List<Kosei.Unit>, k: Kanji?): CharSequence {
        val s = SpannableStringBuilder()
        // This kanji's own on readings: a sound part whose reading is not among them shows the one it has now.
        val on = k?.let { KanjiText.onReadings(it.on, 10) }.orEmpty()
        val mainOn = k?.let { UsageStore.get(it.ch) }?.on?.firstOrNull()?.reading
        val glyphs = android.graphics.Paint()
        fun drawable(x: String) = x.isNotEmpty() && glyphs.hasGlyph(x)
        val radShape = PartsStore.radical(p)?.let { radicalShape(p, it) } ?: p.form
        units.forEachIndexed { i, u ->
            if (i > 0) s.append(" + ")
            val start = s.length
            val shown = u.pieces.all(::drawable)
            s.append(if (shown) u.pieces.joinToString("+") else "부품")
            if (u.sound) s.setSpan(ForegroundColorSpan(pal.sound), start, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (u.radical) {
                // Only the radical's piece when the unit is several (丸's 九+丶: 丶), else all of it.
                var at = start
                val ranges = ArrayList<Pair<Int, Int>>()
                if (shown && u.pieces.size > 1) for (piece in u.pieces) {
                    if (piece == radShape || piece == p.form) ranges.add(at to at + piece.length)
                    at += piece.length + 1
                }
                if (ranges.isEmpty()) ranges.add(start to s.length)
                for ((a, b) in ranges) s.setSpan(ForegroundColorSpan(pal.accent), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            // Each note with the part of it to colour (the radical named inside).
            val notes = ArrayList<Pair<String, Pair<String, Int>?>>()
            if (u.note.isNotEmpty()) notes.add(u.note to null)
            // The role stands out from the grey note: 소리 in blue like the drawing, the others in the text colour.
            if (u.role.isNotEmpty()) {
                // "소리 ジ→トク": the part gave ジ, the kanji is read トク now.
                val now = if (u.sound) Kosei.soundNow(u.reading, on, mainOn) else null
                val reading = if (now != null) u.reading + "→" + now else u.reading
                val role = listOf(u.role, reading).filter { it.isNotEmpty() }.joinToString(" ")
                notes.add(role to (role to if (u.sound) pal.sound else pal.text))
            }
            if (u.radical && u.sound) notes.add("부수" to null)
            if (u.holdsRadical && drawable(radShape) && radShape !in u.note) {
                notes.add("안에 부수 $radShape" to (radShape to pal.accent))
            }
            if (notes.isNotEmpty()) {
                val from = s.length
                s.append("(")
                notes.forEachIndexed { j, (text, mark) ->
                    if (j > 0) s.append(DOT)
                    val at = s.length
                    s.append(text)
                    if (mark != null) {
                        val m = at + text.lastIndexOf(mark.first)
                        s.setSpan(ForegroundColorSpan(mark.second), m, m + mark.first.length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
                s.append(")")
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
     * The 소리 계열: kanji with the same sound part that share an on reading with this one, voicing aside (反 ハン
     * and 板 バン), each with that reading.
     */
    private fun familyItems(k: Kanji, p: Parts): List<Pair<String, String>> {
        if (p.family.isEmpty()) return emptyList()
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
        return items
    }

    /**
     * "青(푸를 청) 계열: ": the sound part the 소리 계열 shares, in the sound colour, with the name the 구성 line gives
     * it (so the rows agree: 基's 其 is 곡식 키 there, not 그 기; only a short name without kanji), or for a kanji
     * without a 구성 line its 훈, and its shape for the list to leave out; null when it cannot be drawn.
     */
    private fun familyHead(p: Parts, kosei: List<Kosei.Unit>?): Pair<String, CharSequence>? {
        val glyphs = android.graphics.Paint()
        val pieces = ArrayList<String>()
        for (part in p.parts) {
            if (!part.sound) continue
            pieces.add(when {
                glyphs.hasGlyph(part.element) -> part.element
                part.original.isNotEmpty() && '+' !in part.original && glyphs.hasGlyph(part.original) -> part.original
                else -> return null
            })
        }
        if (pieces.isEmpty()) return null
        val shape = pieces.joinToString("+")
        val s = SpannableStringBuilder(shape)
        s.setSpan(ForegroundColorSpan(pal.sound), 0, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val hun = if (kosei != null) Kosei.soundName(kosei, shape) else if (pieces.size == 1) firstHun(shape) else ""
        if (hun.isNotEmpty()) s.append("(").append(hun).append(")")
        s.append(" 계열: ")
        return shape to s
    }

    /**
     * "青(푸를 청) 계열: 清 静 情 精  セイ(세-)  비교 ▸": the sound part they share ([head]), the 소리 계열 without
     * that part itself, each kanji with its own reading when they do not all share one; null when there is none.
     */
    private fun familyText(items: List<Pair<String, String>>, head: Pair<String, CharSequence>?): CharSequence? {
        if (items.isEmpty()) return null
        val s = SpannableStringBuilder()
        head?.let { s.append(it.second) }
        val shown = items.filter { head == null || it.first != head.first }.ifEmpty { items }
        val same = shown.map { it.second }.distinct().size == 1
        shown.forEachIndexed { j, (m, r) ->
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
            val r = shown[0].second
            s.append("  ").append(r)
            hangul(s, r)
        }
        val start = s.length
        s.append("  비교 ▸")
        s.setSpan(ForegroundColorSpan(pal.accent), start, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        s.setSpan(RelativeSizeSpan(0.8f), start, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return s
    }

    /** "待 기다릴 대 · 特 특별할 특  비교 ▸": each look-alike with its 훈음, small and grey. */
    private fun similarText(alike: List<String>): CharSequence {
        val s = SpannableStringBuilder()
        alike.forEachIndexed { i, c ->
            if (i > 0) s.append(DOT)
            s.append(c)
            val hun = firstHun(c)
            if (hun.isNotEmpty()) {
                val start = s.length
                s.append(" ").append(hun)
                s.setSpan(ForegroundColorSpan(pal.sub), start, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                s.setSpan(RelativeSizeSpan(0.82f), start, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        val start = s.length
        s.append("  비교 ▸")
        s.setSpan(ForegroundColorSpan(pal.accent), start, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        s.setSpan(RelativeSizeSpan(0.8f), start, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return s
    }

    /**
     * The 닮은 한자 side by side: this kanji and one look-alike drawn next to each other in the card's colours (the
     * radical in the accent colour, the sound part in blue), each with its 훈음 and its first on and kun readings,
     * so shape, meaning and sound are told apart together; the buttons under them switch the one compared.
     */
    private fun buildSimilar(ch: String, alike: List<String>, box: LinearLayout) {
        box.setPadding(0, dp(6f), 0, dp(4f))
        val width = (box.parent as? View)?.width?.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val size = minOf(dp(120f), (width - dp(36f)) / 2).coerceAtLeast(dp(64f))
        fun cell(c: String): View = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val parts = drawable(c)!!
            addView(KanjiStrokeView(this@LookupActivity, shapesOf(c, parts), size, strokeColors(),
                colorOf = ::strokeColor).apply { contentDescription = c }, LinearLayout.LayoutParams(size, size))
            val cap = SpannableStringBuilder(firstHun(c).ifEmpty { c })
            val usage = UsageStore.get(c)
            val on = usage?.on?.firstOrNull()
            val kun = usage?.kun?.firstOrNull()
            if (on != null || kun != null) cap.append("\n")
            if (on != null) {
                cap.append(on.reading)
                hangul(cap, on.reading)
            }
            if (kun != null) {
                if (on != null) cap.append(DOT)
                appendKun(cap, kun.stem, kun.okurigana)
            }
            addView(label(cap, 12.5f, pal.text).apply {
                japanese()
                gravity = Gravity.CENTER
                setPadding(0, dp(2f), 0, 0)
            }, LinearLayout.LayoutParams(size + dp(24f), ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val pair = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun select(index: Int) {
            pair.removeAllViews()
            pair.addView(cell(ch))
            pair.addView(label("↔", 18f, pal.sub).apply {
                gravity = Gravity.CENTER
                setPadding(dp(2f), 0, dp(2f), dp(36f))
            })
            pair.addView(cell(alike[index]))
            for (j in 0 until chips.childCount) {
                val chip = chips.getChildAt(j) as TextView
                val on = j == index
                chip.setTextColor(if (on) pal.accent else pal.text)
                chip.paint.isFakeBoldText = on
                chip.background = if (on) rounded(pal.card, dp(10f), dp(1f), pal.accent) else null
            }
        }
        alike.forEachIndexed { j, c ->
            chips.addView(label(c, 20f, pal.text).apply {
                japanese()
                gravity = Gravity.CENTER
                minWidth = dp(40f)
                setPadding(dp(8f), dp(2f), dp(8f), dp(2f))
                contentDescription = "$c 과 비교"
                setOnClickListener { select(j) }
            })
        }
        box.addView(pair, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        if (alike.size > 1) box.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(chips)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(4f)
        })
        box.addView(label("모양이 닮아 헷갈리기 쉬운 글자입니다. 어디가 다른지, 뜻과 읽기가 어떻게 다른지 함께 보세요.", 12f,
            pal.sub).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(4f), 0, 0)
        })
        select(0)
    }

    /** Opens (made on the first tap) or closes the 소리 계열 comparison under the row. */
    private fun toggleCompare(ch: String, k: Kanji, p: Parts, family: List<Pair<String, String>>, box: LinearLayout) {
        if (box.visibility == View.VISIBLE) {
            box.visibility = View.GONE
            return
        }
        if (box.childCount == 0) buildCompare(ch, k, p, family, box)
        box.visibility = View.VISIBLE
        box.post { box.requestRectangleOnScreen(Rect(0, 0, box.width, box.height), false) }
    }

    /**
     * The 소리 계열 side by side: this kanji and one of the series drawn next to each other, the sound part they share
     * in blue and the rest, what differs, in the text colour (板 ↔ 飯: 反 stays, 木 becomes 飠), each with its 훈음 and
     * reading; the buttons under them switch the one compared.
     */
    private fun buildCompare(ch: String, k: Kanji, p: Parts, family: List<Pair<String, String>>, box: LinearLayout) {
        val members = family.filter { drawable(it.first) != null }
        val mine = drawable(ch)
        box.setPadding(0, dp(6f), 0, dp(4f))
        if (members.isEmpty() || mine == null) {
            box.addView(label("나란히 볼 그림 자료가 없습니다.", 12f, pal.sub))
            return
        }
        // Two drawings and the arrow between them in the width the row has now.
        val width = (box.parent as? View)?.width?.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val size = minOf(dp(120f), (width - dp(36f)) / 2).coerceAtLeast(dp(64f))
        val sound = p.soundElement
        fun colorsFor(c: String): (Char) -> Int = { tag ->
            // The sound part's own kanji (青 among 晴's series) is the sound part all through.
            if (tag == Parts.SOUND || (c == sound && c != ch)) pal.sound else pal.text
        }
        fun cell(c: String, reading: String): View = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val parts = drawable(c)!!
            addView(KanjiStrokeView(this@LookupActivity, shapesOf(c, parts), size, strokeColors(),
                colorOf = colorsFor(c)).apply { contentDescription = c }, LinearLayout.LayoutParams(size, size))
            val cap = SpannableStringBuilder(firstHun(c).ifEmpty { c })
            if (reading.isNotEmpty()) {
                cap.append("\n").append(reading)
                hangul(cap, reading)
            }
            addView(label(cap, 12.5f, pal.text).apply {
                japanese()
                gravity = Gravity.CENTER
                setPadding(0, dp(2f), 0, 0)
            })
        }
        val pair = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun select(index: Int) {
            val (m, r) = members[index]
            // This kanji's reading that the one compared shares (晴 セイ with 清 セイ).
            val myReading = KanjiText.onReadings(k.on, 10).firstOrNull { Parts.unvoiced(it) == Parts.unvoiced(r) } ?: ""
            pair.removeAllViews()
            pair.addView(cell(ch, myReading))
            pair.addView(label("↔", 18f, pal.sub).apply {
                gravity = Gravity.CENTER
                setPadding(dp(6f), 0, dp(6f), dp(24f))
            })
            pair.addView(cell(m, r))
            for (j in 0 until chips.childCount) {
                val chip = chips.getChildAt(j) as TextView
                val on = j == index
                chip.setTextColor(if (on) pal.accent else pal.text)
                chip.paint.isFakeBoldText = on
                chip.background = if (on) rounded(pal.card, dp(10f), dp(1f), pal.accent) else null
            }
        }
        members.forEachIndexed { j, (m, _) ->
            chips.addView(label(m, 20f, pal.text).apply {
                japanese()
                gravity = Gravity.CENTER
                minWidth = dp(40f)
                setPadding(dp(8f), dp(2f), dp(8f), dp(2f))
                contentDescription = "$m 과 비교"
                setOnClickListener { select(j) }
            })
        }
        box.addView(pair, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        if (members.size > 1) box.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(chips)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(4f)
        })
        box.addView(label("파란 부분이 함께 쓰는 소리 부분, 나머지가 달라지는 부분입니다.", 12f, pal.sub).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(4f), 0, 0)
        })
        select(0)
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
            Rich.Kind.SUM -> {
                span(ForegroundColorSpan(pal.accent), m)
                span(StyleSpan(Typeface.BOLD), m)
            }
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
