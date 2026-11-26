package com.ggumtak.hanja

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.graphics.Rect
import android.graphics.Typeface
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.StyleSpan
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.view.KeyEvent
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
import java.util.Locale

/**
 * A card at the bottom of the screen over the current app: the selected word (with its Hangul pronunciation when it
 * is all kana), every kanji in it (Korean 훈음, readings with Hangul, strokes), links to the Naver dictionaries and
 * a Claude explanation. Opened from the text selection menu ("한자"), the share sheet, or a hanja://lookup link
 * (the Anki card script, assets/anki-tap.js). Tapping outside or back closes it.
 */
class LookupActivity : Activity() {
    private companion object {
        const val DOT = " · "
        /** Set on a card opened from another card (a kanji tapped in 확장 or 닮은꼴): the header shows "←". */
        const val EXTRA_FROM_CARD = "com.ggumtak.hanja.FROM_CARD"
        /** The scrolling part's share of the screen: the kanji rows, and with an AI answer or the chat open. */
        const val ROWS_FRACTION = 0.62f
        const val TALK_FRACTION = 0.82f
    }

    private val main = Handler(Looper.getMainLooper())
    private lateinit var pal: Palette
    private lateinit var entries: LinearLayout
    private lateinit var headerView: TextView
    private lateinit var wordNote: TextView
    private lateinit var answer: TextView
    private lateinit var claudeButton: Button
    private lateinit var chatButton: Button
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
    private var savedCardScrollY = 0
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
    /** The Hangul pronunciation after kana (Prefs.showHangul): off by default, so the kana are read unaided. */
    private var showHangul = false
    private lateinit var hangulToggle: TextView
    /** Under the header while an AI answer or chat is open: chips to jump back to kanji rows or the answer. */
    private lateinit var jumpBar: LinearLayout
    private lateinit var body: LinearLayout
    /** The AI answer as written, to draw again when the Hangul setting changes. */
    private var answerText = ""
    private var answerProblem: String? = null
    private var tts: TextToSpeech? = null
    private var speakButton: View? = null
    /** 일본어사전, 한자사전, AI 설명, 질문: keys 1–4 (CardKeys). */
    private val bottomButtons = ArrayList<Button>()
    /** The kanji whose stroke order R shows next. */
    private var strokeKey = 0
    private var ttsReady = false
    private var ttsPending = ""
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
        showHangul = Prefs.showHangul(this)
        chooseLayout()
        setContentView(build())
        applyLayout()
        focusRing()
        // The keyboard opening takes the room the pane had: watch for it (the window itself is resized), and keep
        // any unconsumed system bar / cutout insets outside the card frame.
        window.decorView.setOnApplyWindowInsetsListener { v, insets ->
            val out = v.onApplyWindowInsets(insets)
            if (Build.VERSION.SDK_INT >= 30) {
                val b = out.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                focusSink.setPadding(dp(10f) + b.left, dp(10f) + b.top, dp(10f) + b.right, dp(10f) + b.bottom)
            } else {
                @Suppress("DEPRECATION")
                focusSink.setPadding(
                    dp(10f) + out.systemWindowInsetLeft,
                    dp(10f) + out.systemWindowInsetTop,
                    dp(10f) + out.systemWindowInsetRight,
                    dp(10f) + out.systemWindowInsetBottom
                )
            }
            val shown = if (Build.VERSION.SDK_INT >= 30) insets.isVisible(WindowInsets.Type.ime())
                else @Suppress("DEPRECATION") insets.systemWindowInsetBottom > dp(160f)
            if (shown != keyboardShown) {
                keyboardShown = shown
                v.post { if (!destroyed && chooseLayout()) applyLayout() }
            }
            out
        }
        // After setContentView: installing the decor resets a floating window to WRAP_CONTENT.
        applyWindowWidth()
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
            // Anki taps carry the sentence; a selection of two or more characters is its own context.
            val wantContext = Prefs.autoContext(app) && kanji.isNotEmpty() &&
                (sentence.isNotEmpty() || target.codePointCount(0, target.length) >= 2)
            // A reviewed card is answered from the phone (even offline or without a key), in the very first frame.
            // Only the one-time move of earlier builds' answers after an update is slow: the dictionary shows first.
            val ready = !wantContext || Prefs.answersReady(app)
            val earlyCached = if (ready && wantContext) Prefs.cachedReading(app, target, sentence)
                ?.let { ContextReading.parse(it, target, sentence) } else null
            if (earlyCached != null) syncVocabReading(earlyCached)
            vocabReady = true
            main.post {
                if (!destroyed && pendingManualAdd) {
                    pendingManualAdd = false
                    startManualVocabAdd()
                }
            }
            vocabLookup(app, target, sentence)
            fun show(cr: ContextReading?) = main.post {
                kunMatched = kun
                if (destroyed) return@post
                if (cr != null) {
                    contextReading = cr
                    syncVocabReading(cr)
                }
                fill(word)
            }
            if (!ready) show(null)
            val cached = earlyCached ?: if (!wantContext) null else Prefs.cachedReading(app, target, sentence)
                ?.let { ContextReading.parse(it, target, sentence) }
            if (ready || cached != null) show(cached)
            if (key.isEmpty()) return@Thread
            if (wantContext && cached == null) askContext(ClaudeClient(key), target, sentence)
            else ClaudeClient.warmUp()
        }.start()
    }

    private fun cardWidthDp(cfg: Configuration = resources.configuration): Int =
        if (cfg.screenWidthDp > 920) 880 else cfg.screenWidthDp

    private fun applyWindowWidth(cfg: Configuration = resources.configuration) {
        val width = if (cfg.screenWidthDp > 920) dp(880f) else ViewGroup.LayoutParams.MATCH_PARENT
        window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        window.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
    }

    /** The window changed (split screen resized, rotation): the card is not recreated, so it lays itself out again. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyWindowWidth(newConfig)
        if (chooseLayout(newConfig)) applyLayout()
    }

    /** Sets [tablet] and [paneDp] for the window as it is now; true when they changed. */
    private fun chooseLayout(c: Configuration = resources.configuration): Boolean {
        val choice = CardLayout.choose(cardWidthDp(c), c.screenHeightDp, keyboardShown)
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
            syncLeftStates()
            return
        }
        val open = entryViews.firstOrNull { it.panel.visibility == View.VISIBLE }?.ch
        for (e in entryViews) e.panel.visibility = View.GONE
        if (entryViews.isEmpty()) {
            syncLeftStates()
            return
        }
        val first = open ?: strokeChar.takeIf { it in kanji } ?: kanji.firstOrNull { drawable(it) != null }
        if (first != null) showStrokes(first, play = false) else syncLeftStates()
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

    override fun onResume() {
        super.onResume()
        heard?.let { VocabService.listeners.add(it) }
        if (vocabReady) {
            val key = VocabNote.key(vocabForm, selection)
            if (key.isNotEmpty() && vocab?.text == "…" && !VocabAdder.isBusy(key)) {
                if (Prefs.vocabAdded(this, key)) showAdded()
                else vocab?.apply {
                    text = "☆"
                    setTextColor(pal.accent)
                    isEnabled = true
                }
            }
        }
    }

    override fun onStop() {
        heard?.let { VocabService.listeners.remove(it) }
        super.onStop()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (isChatOpen()) closeChat() else super.onBackPressed()
    }

    // ---- A hardware keyboard (CardKeys) ----

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code == KeyEvent.KEYCODE_TAB || code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
            // Moving between items: everything that can be tapped can be reached, with a ring on the one chosen.
            keyboardFocus()
            return super.dispatchKeyEvent(event)
        }
        val typing = currentFocus is EditText
        val action = CardKeys.action(code, event.isShiftPressed,
            event.isCtrlPressed || event.isAltPressed || event.isMetaPressed, typing)
            ?: return super.dispatchKeyEvent(event)
        // The key's up event is taken too, so a press never reaches the item that has the focus as well.
        if (event.action == KeyEvent.ACTION_DOWN && (event.repeatCount == 0 || CardKeys.repeats(action))) onKey(action, code)
        return true
    }

    private fun onKey(action: CardKeys.Action, keyCode: Int) {
        fun press(v: View?) {
            if (v != null && v.isShown && v.isEnabled) v.performClick()
        }
        val step = scroll.height / 4
        when (action) {
            CardKeys.Action.CLOSE -> if (keyCode == KeyEvent.KEYCODE_ESCAPE && isChatOpen()) closeChat() else finish()
            CardKeys.Action.JAPANESE_DICT -> press(bottomButtons.getOrNull(0))
            CardKeys.Action.HANJA_DICT -> press(bottomButtons.getOrNull(1))
            CardKeys.Action.AI -> press(bottomButtons.getOrNull(2))
            CardKeys.Action.ASK -> press(bottomButtons.getOrNull(3))
            CardKeys.Action.EXAMPLES -> if (examplesBox.childCount > 0) {
                if (isChatOpen()) closeChat()
                if (examplesBox.isShown) {
                    loadExamples()
                    scroll.post { scroll.smoothScrollTo(0, offsetIn(examplesBox, body)) }
                }
            }
            CardKeys.Action.SPEAK -> press(speakButton)
            CardKeys.Action.HANGUL -> press(hangulToggle)
            CardKeys.Action.VOCAB -> press(vocab)
            CardKeys.Action.STROKES -> strokesByKey()
            CardKeys.Action.SCROLL_UP -> scroll.smoothScrollBy(0, -step)
            CardKeys.Action.SCROLL_DOWN -> scroll.smoothScrollBy(0, step)
            CardKeys.Action.PAGE_UP -> scroll.pageScroll(View.FOCUS_UP)
            CardKeys.Action.PAGE_DOWN -> scroll.pageScroll(View.FOCUS_DOWN)
            CardKeys.Action.HELP -> AlertDialog.Builder(this)
                .setTitle("키보드 단축키")
                .setMessage(CardKeys.HELP)
                .setPositiveButton("확인", null)
                .show()
            CardKeys.Action.SEND -> sendChat()
            CardKeys.Action.LEAVE_INPUT -> {
                getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(input.windowToken, 0)
                // The focus goes to the card itself, not to the next button: Enter must not press ☆ unseen.
                focusSink.isFocusable = true
                focusSink.requestFocus()
            }
        }
    }

    /** R: the stroke order of the card's kanji, the next one on each press when there are several. */
    private fun strokesByKey() {
        if (tablet && strokePane.visibility == View.VISIBLE) {
            val drawableKanji = kanji.filter { drawable(it) != null }
            if (drawableKanji.isEmpty()) return
            val ch = drawableKanji[strokeKey.mod(drawableKanji.size)]
            strokeKey++
            showStrokes(ch, play = true)
            return
        }
        val drawn = entryViews.filter { it.box.isShown && it.hasStrokes }
        if (drawn.isEmpty()) return
        val e = drawn[strokeKey.mod(drawn.size)]
        strokeKey++
        val parts = drawable(e.ch) ?: return
        if (e.panel.visibility == View.VISIBLE) {
            (e.panel.getChildAt(0) as? KanjiStrokeView)?.play()
        } else {
            openPanel(e.ch, parts, e.panel, play = true)
        }
        scroll.post { scroll.smoothScrollTo(0, offsetIn(e.box, body)) }
    }

    /** The card's outer frame: holds the focus after the question input lets it go (Esc), without a ring. */
    private lateinit var focusSink: LinearLayout

    /**
     * Lets Tab reach every tappable part of the card (rows, 유래·확장, 획순, 예문 보기), not only the buttons, and draws
     * a ring around the one chosen. Touch is unchanged: these parts do not take the focus when tapped.
     */
    private fun keyboardFocus() {
        fun walk(v: View) {
            if (v.isClickable && v !is EditText) v.isFocusable = true
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(window.decorView)
    }

    private var focusedRing: android.graphics.drawable.Drawable? = null

    /** A ring around the part chosen with the keyboard (none while the screen is being touched). */
    private fun focusRing() {
        window.decorView.viewTreeObserver.addOnGlobalFocusChangeListener { old, new ->
            if (old != null && old.foreground != null && old.foreground === focusedRing) {
                old.foreground = null
                focusedRing = null
            }
            if (old === focusSink) focusSink.isFocusable = false
            if (new != null && new !is EditText && new !== focusSink && !new.isInTouchMode && new.foreground == null) {
                val radius = if (new.minimumHeight >= dp(96f)) dp(14f) else dp(10f)
                val ring = rounded(android.graphics.Color.TRANSPARENT, radius, dp(2f), pal.accent)
                focusedRing = ring
                new.foreground = ring
            }
        }
    }

    override fun onDestroy() {
        destroyed = true
        heard?.let { VocabService.listeners.remove(it) }
        claude?.cancel()
        contextClient?.cancel()
        chatClient?.cancel()
        examplesClient?.cancel()
        tts?.shutdown()
        tts = null
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
        card.addView(headerRow())
        jumpBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        card.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            visibility = View.GONE
            addView(jumpBar)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(6f)
        })
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
            setLineSpacing(0f, 1.35f)
            setPadding(0, dp(12f), 0, dp(4f))
        }
        chatLog = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        examplesBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            if (hasJapanese(selection)) addView(examplesLink(), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8f) })
        }
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // Room between the text and the scroll bar, which is drawn over this padding.
            setPadding(0, 0, dp(10f), 0)
            addView(entries)
            addView(examplesBox)
            addView(answer)
            addView(chatLog)
        }
        scroll = CappedScrollView(this, ROWS_FRACTION).apply {
            isVerticalScrollBarEnabled = true
            addView(body)
            setOnScrollChangeListener { _, _, y, _, _ -> stick(y) }
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
        body.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> stick(scroll.scrollY) }
        card.addView(buttons())
        focusSink = LinearLayout(this).apply {
            descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
        }
        return focusSink.apply {
            setPadding(dp(10f), dp(10f), dp(10f), dp(10f))
            addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun strokeColors() = KanjiStrokeView.Colors(ghost = pal.ghostStroke,
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
        val replayBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, dp(4f))
            background = rippleRounded(pal.card, dp(12f), dp(1f), pal.line)
            contentDescription = "획순 재생"
            setOnClickListener { (strokeHolder.getChildAt(0) as? KanjiStrokeView)?.play() }
            addView(strokeHolder, LinearLayout.LayoutParams(dp(paneDp.toFloat()), dp(paneDp.toFloat())))
            addView(label("↻ 탭하면 획순 재생", 12.5f, pal.sub).apply {
                gravity = Gravity.CENTER
                minHeight = dp(28f)
            })
        }
        strokePane = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            visibility = View.GONE
            setPadding(dp(8f), dp(8f), dp(8f), dp(10f))
            background = rounded(pal.button, dp(16f))
            addView(replayBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
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
                setPadding(dp(6f), dp(6f), dp(6f), dp(6f))
                isClickable = false
                isFocusable = false
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            (strokeHolder.parent as? View)?.contentDescription = "$ch 획순 다시 보기"
        }
        strokePane.visibility = View.VISIBLE
        // One button per kanji that has a drawing, when there are two or more.
        val drawable = kanji.filter { drawable(it) != null }
        strokeChips.removeAllViews()
        if (drawable.size > 1) for (c in drawable) {
            val selected = c == strokeChar
            strokeChips.addView(label(c, 20f, if (selected) pal.accent else pal.text, bold = selected).apply {
                japanese()
                gravity = Gravity.CENTER
                minWidth = dp(48f)
                minHeight = dp(44f)
                setPadding(dp(10f), dp(6f), dp(10f), dp(6f))
                background = rippleRounded(
                    pal.card, dp(10f),
                    dp(if (selected) 1.5f else 1f),
                    if (selected) pal.accent else pal.line
                )
                contentDescription = "$c 획순 보기"
                setOnClickListener { showStrokes(c, play = true) }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = dp(3f)
                rightMargin = dp(3f)
                topMargin = dp(6f)
            })
        }
        syncLeftStates()
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
            if (!cr.whole && cr.kanji.isEmpty()) {
                Readings.split(cr.word, cr.reading) { ch -> KanjiStore.get(ch)?.let(Readings::candidates) }
                    ?.let { Readings.prefixReading(selection, cr.word, it) }
                    ?.let { return listOf(it) }
            }
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
        val s = SpannableStringBuilder(selection.ifEmpty { "선택된 글자 없음" })
        // Each kanji of the word: a tap scrolls to its row.
        var i = 0
        while (i < selection.length) {
            val n = Character.charCount(selection.codePointAt(i))
            val ch = selection.substring(i, i + n)
            if (ch in kanji) s.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) = jumpTo(ch)
                override fun updateDrawState(ds: TextPaint) {
                    ds.isUnderlineText = false
                }
            }, i, i + n, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            i += n
        }
        val kana = KanaHangul.kanaOnly(selection)
        val readings = if (kana) listOf(selection) else selectionReadings(dictWord)
        readings.forEachIndexed { i, r ->
            if (!kana) {
                s.append(if (i == 0) "  " else "  ·  ")
                val start = s.length
                s.append(r)
                s.setSpan(RelativeSizeSpan(0.8f), start, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (!showHangul) return@forEachIndexed
            if (kana) s.append(if (i == 0) "  " else "  ·  ")
            val start = s.length
            s.append(if (!kana) "(" + wordHangul(selection, r) + ")" else KanaHangul.convert(r))
            s.setSpan(ForegroundColorSpan(pal.sub), start, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
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
            gravity = Gravity.CENTER_VERTICAL
            minHeight = dp(44f)
            movementMethod = LinkMovementMethod.getInstance()
            highlightColor = android.graphics.Color.TRANSPARENT
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
    }

    // ---- 자주 까먹는 단어 (VocabAdder) ----

    @Volatile
    private var vocabForm = ""
    @Volatile
    private var vocabReading = ""
    @Volatile
    private var vocabReady = false
    private var pendingManualAdd = false
    private var vocab: Button? = null

    private fun hasJapanese(s: String): Boolean =
        s.codePoints().anyMatch { KanjiText.isKanji(it) || (it <= 0xFFFF && KanaHangul.isKana(it.toChar())) }

    private fun hasWord(): Boolean = hasJapanese(selection)

    /**
     * The header: "←" on a card opened from another card, the word and its reading, then 🔊 (the reading read
     * aloud), 한 (the Hangul pronunciation on or off) and ☆ (into the 단어장), each at least 44dp.
     */
    private fun headerRow(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        // Centred, not aligned on the text baseline: the baseline pushed the smaller 한 box down by the difference
        // in font size, out of the row, and its bottom edge was cut off (2026-11-24, 사용자 스크린샷).
        isBaselineAligned = false
        gravity = Gravity.CENTER_VERTICAL
        if (intent.getBooleanExtra(EXTRA_FROM_CARD, false)) {
            addView(iconButton("←", "이전 카드로 돌아가기") { finish() })
        }
        addView(headerView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (hasWord()) addView(iconButton("🔊", "발음 듣기") { speak() }.also { speakButton = it })
        hangulToggle = iconButton("한", "한글 발음 켜기/끄기") { toggleHangul() }
        styleHangulToggle()
        addView(hangulToggle, LinearLayout.LayoutParams(dp(44f), dp(44f)).apply { leftMargin = dp(4f) })
        if (hasWord()) addView(vocabButton(), LinearLayout.LayoutParams(dp(44f), dp(44f)).apply { leftMargin = dp(4f) })
    }

    private fun iconButton(text: String, description: String, onClick: () -> Unit): TextView =
        label(text, 17f, pal.text).apply {
            gravity = Gravity.CENTER
            minWidth = dp(44f)
            minHeight = dp(44f)
            contentDescription = description
            background = selectableBackground()
            setOnClickListener { onClick() }
        }

    private fun styleHangulToggle() {
        val color = if (showHangul) pal.accent else pal.sub
        val borderColor = if (showHangul) pal.accent else pal.line
        hangulToggle.setTextColor(color)
        hangulToggle.textSize = 14f
        hangulToggle.background = android.graphics.drawable.InsetDrawable(
            rippleRounded(pal.card, dp(10f), dp(1f), borderColor), dp(4f)
        )
    }

    /** Turns the Hangul pronunciation on or off everywhere on the card, the open answers included. */
    private fun toggleHangul() {
        showHangul = !showHangul
        Prefs.setShowHangul(this, showHangul)
        styleHangulToggle()
        headerView.text = headerText(lastWord)
        if (entryViews.isNotEmpty()) {
            // The rows are made again with or without the Hangul; the place read and any open panels stay where they were.
            val y = scroll.scrollY
            val openPanels = entryViews.filter { it.panel.visibility == View.VISIBLE }.map { it.ch }.toSet()
            entries.removeAllViews()
            entryViews.clear()
            fill(lastWord)
            if (!tablet && openPanels.isNotEmpty()) {
                for (e in entryViews) {
                    if (e.ch in openPanels) {
                        val parts = drawable(e.ch) ?: continue
                        openPanel(e.ch, parts, e.panel, play = false)
                    }
                }
            }
            scroll.post {
                scroll.scrollTo(0, y)
                stick(scroll.scrollY)
            }
        }
        if (answerText.isNotEmpty() && claude == null) answer.text = renderAi(answerText, answerProblem)
        for (i in 0 until chatLog.childCount) {
            val v = chatLog.getChildAt(i)
            val saved = v.tag as? SavedReply ?: continue
            if (v is TextView) v.text = renderAi(saved.body, saved.problem)
        }
    }

    /** Reads the word aloud with the phone's Japanese voice: its reading when known, else as written. */
    private fun speak() {
        val text = (if (KanaHangul.kanaOnly(selection)) null else selectionReadings(lastWord).firstOrNull()) ?: selection
        if (text.isEmpty()) return
        val engine = tts
        if (engine != null) {
            if (ttsReady) engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "word") else ttsPending = text
            return
        }
        ttsPending = text
        // The engine may answer inside the constructor (no engine at all), before the field is set: the callback
        // takes the instance from a holder filled right after.
        val holder = arrayOfNulls<TextToSpeech>(1)
        var early = Int.MIN_VALUE
        fun ready(status: Int) {
            val t = holder[0] ?: return
            if (destroyed || tts !== t) return
            val lang = if (status == TextToSpeech.SUCCESS) t.setLanguage(Locale.JAPANESE) else TextToSpeech.LANG_NOT_SUPPORTED
            if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(applicationContext, "일본어 음성(TTS) 데이터가 설치되어 있지 않습니다.", Toast.LENGTH_SHORT).show()
                t.shutdown()
                tts = null
                return
            }
            ttsReady = true
            t.speak(ttsPending, TextToSpeech.QUEUE_FLUSH, null, "word")
        }
        val created = TextToSpeech(applicationContext) { status ->
            if (holder[0] == null) early = status else ready(status)
        }
        holder[0] = created
        tts = created
        if (early != Int.MIN_VALUE) ready(early)
    }

    /** Scrolls the rows to [ch]'s (a tap on it in the header or in the chips over an answer). */
    private fun jumpTo(ch: String) {
        val fromChat = isChatOpen()
        if (fromChat) closeChat()
        val e = entryViews.firstOrNull { it.ch == ch } ?: return
        if (entries.visibility != View.VISIBLE) return
        if (fromChat) {
            scroll.post {
                scroll.scrollTo(0, offsetIn(e.box, body))
                stick(scroll.scrollY)
            }
        } else {
            scroll.smoothScrollTo(0, offsetIn(e.box, body))
        }
    }

    /** [v]'s top in [ancestor]'s coordinates (its layout position, without translation). */
    private fun offsetIn(v: View, ancestor: View): Int {
        var y = 0
        var c: View = v
        while (c !== ancestor) {
            y += c.top
            c = c.parent as? View ?: break
        }
        return y
    }

    /**
     * Keeps each row's big kanji and its "획순 ▸" in view while that row scrolls: it rides down the row with the top of
     * the visible part and stops at the row's bottom, so the drawing stays next to the text it explains.
     */
    private fun stick(scrollY: Int) {
        for (e in entryViews) {
            val left = e.left ?: continue
            val maxShift = (e.row.height - e.row.paddingBottom - left.bottom).coerceAtLeast(0)
            left.translationY = (scrollY - offsetIn(e.row, body)).coerceIn(0, maxShift).toFloat()
        }
    }

    /** Jump chips under the header: returns from chat to the card, or jumps between kanji rows and an open AI answer. */
    private fun updateJumpBar() {
        val holder = jumpBar.parent as View
        jumpBar.removeAllViews()
        fun chip(text: String, accent: Boolean = false, onClick: () -> Unit) =
            jumpBar.addView(label(text, 14f, if (accent) pal.accent else pal.text, bold = accent).apply {
                japanese()
                gravity = Gravity.CENTER
                minWidth = dp(44f)
                minHeight = dp(44f)
                setPadding(dp(14f), dp(6f), dp(14f), dp(6f))
                background = rippleRounded(pal.button, dp(10f), dp(1f), pal.line)
                setOnClickListener { onClick() }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                rightMargin = dp(8f)
            })
        when {
            isChatOpen() -> {
                chip("← 한자 카드로 돌아가기", accent = true) { closeChat() }
                holder.visibility = View.VISIBLE
            }
            answer.visibility == View.VISIBLE -> {
                for (ch in kanji) chip(ch) { jumpTo(ch) }
                chip("AI 설명") { scroll.smoothScrollTo(0, offsetIn(answer, body)) }
                holder.visibility = View.VISIBLE
            }
            else -> holder.visibility = View.GONE
        }
    }

    /** Opens [ch] in a card of its own over this one; "←" or back returns here as it was. */
    private fun openKanji(ch: String) {
        if (ch == selection) return
        startActivity(Intent(this, LookupActivity::class.java)
            .putExtra(Intent.EXTRA_PROCESS_TEXT, ch)
            .putExtra(EXTRA_FROM_CARD, true))
    }

    /** ☆ in the header: puts the word in the 단어장; … while it goes in, ★ once it is there. */
    private fun vocabButton(): Button = Button(this).apply {
        text = "☆"
        isAllCaps = false
        textSize = 20f
        setTextColor(pal.accent)
        background = selectableBackground()
        minWidth = dp(44f)
        minimumWidth = dp(44f)
        minHeight = dp(44f)
        minimumHeight = dp(44f)
        setPadding(0, 0, 0, 0)
        stateListAnimator = null
        contentDescription = "단어장에 추가"
        setOnClickListener { addVocab() }
        vocab = this
    }

    private fun showAdded() {
        vocab?.apply {
            text = "★"
            setTextColor(pal.accent)
            isEnabled = false
            contentDescription = "단어장에 추가됨"
        }
    }

    private fun syncVocabReading(cr: ContextReading) {
        val key = vocabForm.ifEmpty { selection }
        if (cr.word == key && cr.reading.isNotEmpty()) {
            vocabReading = cr.reading
        } else if (key == selection && !cr.whole) {
            Readings.fromParts(selection, cr.kanji)?.takeIf { it.isNotEmpty() }?.let { vocabReading = it }
        }
    }

    /** Background thread: counts this lookup; on the [VocabNote.AUTO_AFTER]th the word goes in by itself. */
    private fun vocabLookup(app: android.content.Context, target: String, sentence: String) {
        if (!hasWord()) return
        val form = vocabForm
        val reading = vocabReading
        val key = VocabNote.key(form, target)
        if (Prefs.vocabAdded(app, key)) {
            if (AnkiBridge.installed(app) && AnkiBridge.granted(app) &&
                runCatching { VocabAdder.inDeck(app, key, reading) }.getOrNull() == false) {
                Prefs.unmarkVocabAdded(app, key)
                main.post {
                    if (!destroyed && !VocabAdder.isBusy(key)) vocab?.apply {
                        text = "☆"
                        setTextColor(pal.accent)
                        isEnabled = true
                        contentDescription = "단어장에 추가"
                    }
                }
            } else {
                main.post { if (!destroyed) showAdded() }
            }
            return
        }
        if (VocabAdder.isBusy(key)) {
            main.post {
                if (!destroyed) {
                    vocab?.apply {
                        text = "…"
                        isEnabled = false
                    }
                    listen()
                }
            }
            return
        }
        if (!Prefs.vocabAuto(app)) return
        val n = Prefs.countLookup(app, key)
        if (n < VocabNote.AUTO_AFTER || !AnkiBridge.granted(app)) return
        main.post {
            if (destroyed || pendingManualAdd || VocabAdder.isBusy(key)) return@post
            vocab?.apply {
                text = "…"
                isEnabled = false
            }
            listen()
            if (VocabService.start(app, target, form, reading, sentence, prefix = "자동 · ", quiet = true)) return@post
            Thread {
                val o = VocabAdder.add(app, target, form, reading, sentence, auto = true)
                main.post {
                    if (destroyed) {
                        if (o.added) Toast.makeText(app, "자동 · " + o.message, Toast.LENGTH_SHORT).show()
                        return@post
                    }
                    if (o.added || o.already) showAdded()
                    else vocab?.apply {
                        text = "☆"
                        isEnabled = true
                    }
                }
            }.start()
        }
    }

    /** This card hears when its word is done in [VocabService], while it is open. */
    private var heard: ((String, VocabAdder.Outcome) -> Boolean)? = null

    private fun listen() {
        heard?.let { VocabService.listeners.remove(it) }
        val key = VocabNote.key(vocabForm, selection)
        val l: (String, VocabAdder.Outcome) -> Boolean = { k, o ->
            val mine = !destroyed && k == key
            if (mine) {
                if (o.added || o.already) showAdded()
                else vocab?.apply {
                    text = "☆"
                    setTextColor(pal.accent)
                    isEnabled = true
                }
            }
            mine
        }
        heard = l
        VocabService.listeners.add(l)
    }

    private fun addVocab() {
        val b = vocab ?: return
        b.text = "…"
        b.isEnabled = false
        if (!vocabReady) {
            pendingManualAdd = true
            return
        }
        startManualVocabAdd()
    }

    private fun startManualVocabAdd() {
        val b = vocab ?: return
        b.text = "…"
        b.isEnabled = false
        Toast.makeText(this, "단어장에 추가 중… (창을 닫아도 계속됩니다)", Toast.LENGTH_SHORT).show()
        val app = applicationContext
        val target = selection
        val form = vocabForm
        val reading = vocabReading
        val sentence = context
        listen()
        if (VocabService.start(app, target, form, reading, sentence)) return
        Thread {
            val o = VocabAdder.add(app, target, form, reading, sentence)
            main.post {
                if (destroyed) {
                    Toast.makeText(app, o.message, Toast.LENGTH_SHORT).show()
                    return@post
                }
                if (o.added || o.already) showAdded()
                else {
                    // The reason after the fixed sentence: a missing key or Anki permission is fixed differently.
                    Toast.makeText(app, "단어장 추가 실패\n" + o.message, Toast.LENGTH_LONG).show()
                    b.text = "☆"
                    b.isEnabled = true
                }
            }
        }.start()
    }

    private fun openHanjaDict() {
        if (kanji.size <= 1) {
            open(KanjiText.naverHanjaUrl(kanji.firstOrNull() ?: selection))
            return
        }
        val locale = android.os.LocaleList(Locale.JAPANESE, Locale.KOREAN)
        val items = Array<CharSequence>(kanji.size + 1) { idx ->
            if (idx < kanji.size) {
                val ch = kanji[idx]
                val k = KanjiStore.get(ch)
                val known = k?.hunStd?.ifEmpty { k.hun } ?: ""
                val hun = contextReading?.meanings?.get(ch)?.takeIf { ContextReading.acceptable(it, known) }
                    ?: firstHun(ch)
                SpannableStringBuilder(ch).apply {
                    setSpan(android.text.style.LocaleSpan(locale), 0, ch.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    if (hun.isNotEmpty()) append("  ·  ").append(hun)
                }
            } else {
                SpannableStringBuilder("전체 검색 ($selection)").apply {
                    setSpan(android.text.style.LocaleSpan(locale), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }
        val dlg = AlertDialog.Builder(this)
            .setTitle("네이버 한자사전 열기")
            .setItems(items) { _, which ->
                val query = if (which in kanji.indices) kanji[which] else selection
                open(KanjiText.naverHanjaUrl(query))
            }
            .setNegativeButton("취소", null)
            .show()
        if (tablet) {
            val idx = kanji.indexOf(strokeChar)
            if (idx >= 0) dlg.listView?.post { dlg.listView?.setSelection(idx) }
        }
    }

    private fun buttons(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12f), 0, 0)
        }
        fun add(text: String, ai: Boolean = false, onClick: () -> Unit): Button = Button(this).apply {
            this.text = text
            isAllCaps = false
            textSize = 14f
            // The two that ask the AI stand apart a little from the two dictionary links.
            setTextColor(if (ai) pal.accent else pal.text)
            background = if (ai) rippleRounded(pal.button, dp(12f), dp(1f), (pal.accent and 0x00FFFFFF) or (0x66 shl 24))
                else rippleRounded(pal.button, dp(12f))
            minHeight = dp(48f)
            minimumHeight = dp(48f)
            maxLines = 1
            setPadding(dp(6f), 0, dp(6f), 0)
            setAutoSizeTextTypeUniformWithConfiguration(11, 14, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
            stateListAnimator = null
            setOnClickListener { onClick() }
            bottomButtons += this
            row.addView(this, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (row.childCount > 0) leftMargin = dp(8f)
            })
        }
        add("일본어사전") { open(KanjiText.naverJapaneseUrl(selection)) }
        add("한자사전") { openHanjaDict() }
        claudeButton = add("AI 설명", ai = true) { askClaude() }
        chatButton = add("질문", ai = true) { if (isChatOpen()) closeChat() else openChat() }
        if (selection.isEmpty()) for (i in 0 until row.childCount) row.getChildAt(i).isEnabled = false
        return row
    }

    private fun putSplitReadings(target: MutableMap<String, String>, form: String, parts: List<String>) {
        var i = 0
        for (p in parts) {
            if (i >= form.length) break
            val n = Character.charCount(form.codePointAt(i))
            val ch = form.substring(i, i + n)
            if (KanjiText.isKanji(ch.codePointAt(0))) target.putIfAbsent(ch, p)
            i += n
        }
    }

    private fun fill(word: Word?) {
        lastWord = word
        headerView.text = headerText(word)
        val inWord = HashMap<String, String>()
        val cr = contextReading
        wordNote.visibility = View.GONE
        if (cr != null) {
            // Claude read the sentence: its split wins over the dictionary's first reading.
            when {
                cr.whole -> showWholeNote(cr.reading)
                cr.kanji.isNotEmpty() -> inWord.putAll(cr.kanji)
                else -> Readings.split(cr.word, cr.reading) { ch -> KanjiStore.get(ch)?.let(Readings::candidates) }
                    ?.let { putSplitReadings(inWord, cr.word, it) }
            }
        } else if (word != null) {
            if (word.split == null && KanjiText.kanjiIn(word.form, 1).isNotEmpty()) {
                val r = word.readings[0]
                wordNote.text = "사전 읽기 $r" + (if (showHangul) "(${wordHangul(word.form, r)})" else "") +
                    " · 글자별 분리 불가"
                wordNote.japanese()
                wordNote.visibility = View.VISIBLE
            }
            word.split?.let { putSplitReadings(inWord, word.form, it) }
        }
        if (kanji.isEmpty()) return
        fun ctxMeaning(ch: String, k: Kanji?) = contextReading?.meanings?.get(ch)
            ?.takeIf { ContextReading.acceptable(it, k?.hunStd?.ifEmpty { k.hun } ?: "") }
        if (entryViews.size == kanji.size) {
            // A refill (the saved or the new context reading came): only the 훈음 line and the "단어" reading change,
            // so the rows stay as they are and a stroke order opened under one keeps playing.
            for (e in entryViews) updateEntry(e, inWord[e.ch], ctxMeaning(e.ch, e.k))
        } else {
            entries.removeAllViews()
            entryViews.clear()
            kanji.forEachIndexed { i, ch ->
                if (i > 0) entries.addView(View(this).apply { setBackgroundColor(pal.line) },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1f)))
                val k = KanjiStore.get(ch)
                entries.addView(entry(ch, k, inWord[ch], ctxMeaning(ch, k)))
            }
        }
        // With the pane: it keeps the kanji it shows across refills, else shows the first with a drawing.
        if (tablet) {
            val first = strokeChar.takeIf { it in kanji } ?: kanji.firstOrNull { drawable(it) != null }
            if (first != null) showStrokes(first, play = false)
        }
        syncLeftStates()
    }

    private fun showWholeNote(r: String) {
        wordNote.text = "숙자훈 · $r" + (if (showHangul) "(${KanaHangul.convert(r)})" else "") + " (단어 전체를 통째로 읽는 예외 발음)"
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
        // Candidates only for the kanji the card shows (楽 when 楽 of 音楽 was tapped) that have several 훈음: the
        // model picks among them, and meanings for other kanji would never be displayed, so they would only add tokens.
        val candidates = LinkedHashMap<String, String>()
        for (ch in kanji) {
            val k = KanjiStore.get(ch) ?: continue
            val hun = k.hunStd.ifEmpty { k.hun }
            if (ContextReading.choice(hun)) candidates[ch] = ContextReading.candidate(hun, k.gloss)
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
                syncVocabReading(cr)
                fill(lastWord)
            }
        }
    }

    /**
     * The 훈음 line: the standard 훈음 (hunStd: 消 → 사라질 소), else libhangul's list. When there are several (樂 →
     * 즐길 락, 노래 악), the one for this sentence ([ctxMeaning], picked by Claude among them) comes first in the
     * accent colour.
     */
    private fun meaningText(k: Kanji?, ctxMeaning: String?): CharSequence {
        val s = SpannableStringBuilder()
        if (ctxMeaning != null) {
            s.append(ctxMeaning)
            s.setSpan(ForegroundColorSpan(pal.accent), 0, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val list = k?.hunStd?.ifEmpty { k.hun } ?: ""
        val items = list.split(", ", " / ").map { it.trim() }.filter { it.isNotEmpty() && it != ctxMeaning }
        for (item in items) {
            if (s.isNotEmpty()) s.append(", ")
            s.append(item)
        }
        if (s.isEmpty()) s.append(when {
            k == null -> "사전에 없는 한자"
            k.kokuji -> "일본 국자"
            // 捗, 丼: no Korean 훈음 in either list. The 뜻 line below says what it means, so no English here.
            k.gloss.isNotEmpty() -> "한국 훈음 없음"
            else -> k.meanings.ifEmpty { "한국 훈음 없음" }
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
        /** The whole row with its panel (for jumping to it), the row itself and its big kanji column (kept in view). */
        val box: View,
        val row: View,
        val left: View?,
        val hasStrokes: Boolean,
        val leftCaption: TextView?,
        var leftActive: Boolean? = null,
    )

    private fun syncLeftStates() {
        for (e in entryViews) {
            if (!e.hasStrokes) continue
            val box = e.left as? LinearLayout ?: continue
            val cap = e.leftCaption ?: continue
            val active = if (tablet && strokePane.visibility == View.VISIBLE) {
                e.ch == strokeChar
            } else {
                !tablet && e.panel.visibility == View.VISIBLE
            }
            cap.text = when {
                tablet && active -> "획순 ↻"
                !tablet && active -> "획순 ▾"
                else -> "획순 ▸"
            }
            if (e.leftActive != active) {
                e.leftActive = active
                box.background = rippleRounded(
                    pal.button, dp(14f),
                    dp(if (active) 1.5f else 1f),
                    if (active) pal.accent else pal.line
                )
            }
        }
    }

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
            gravity = Gravity.TOP
            setPadding(0, dp(12f), 0, dp(12f))
        }
        val parts = partsOf(ch)
        // Without the pane, the stroke-order panel under the row, opened by a tap on the kanji card.
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            visibility = View.GONE
            background = rounded(pal.button, dp(14f), dp(1f), pal.line)
            setPadding(dp(12f), dp(12f), dp(12f), dp(10f))
        }
        val hasStrokes = parts != null && parts.strokes.isNotEmpty()
        val left: View
        var leftCaption: TextView? = null
        if (hasStrokes) {
            // Drawn from its strokes (brush outlines for the 상용한자): the radical in the accent colour, the sound
            // part in blue. A tap on the card shows the stroke order: in the pane on the right when the card has one,
            // in the panel under the row otherwise.
            val small = KanjiStrokeView(this, shapesOf(ch, parts!!), dp(72f), strokeColors(),
                colorOf = ::strokeColor).apply {
                setPadding(dp(4f), dp(4f), dp(4f), dp(4f))
                background = rounded(pal.card, dp(10f))
                isClickable = false
                isFocusable = false
            }
            val caption = label("획순 ▸", 12.5f, pal.accent, bold = true).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(6f), 0, 0)
            }
            leftCaption = caption
            // Unified 88dp × 112dp hit target for the kanji drawing and its caption.
            left = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                minimumHeight = dp(112f)
                setPadding(dp(8f), dp(8f), dp(8f), dp(8f))
                background = rippleRounded(pal.button, dp(14f), dp(1f), pal.line)
                contentDescription = "$ch 획순 보기"
                setOnClickListener {
                    if (tablet) showStrokes(ch, play = true) else togglePanel(ch, parts, panel)
                }
                addView(small, LinearLayout.LayoutParams(dp(72f), dp(72f)))
                addView(caption)
            }
        } else {
            left = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                minimumHeight = dp(96f)
                isClickable = false
                setPadding(dp(8f), dp(8f), dp(8f), dp(8f))
                background = rounded(pal.button, dp(14f), dp(1f), pal.line)
                addView(label(ch, 44f, pal.text).apply {
                    gravity = Gravity.CENTER
                    japanese()
                })
                addView(label("획순 없음", 11f, pal.sub).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, dp(4f), 0, 0)
                })
            }
        }
        row.addView(left, LinearLayout.LayoutParams(dp(88f), ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { gravity = Gravity.TOP })
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14f), 0, 0, 0)
        }
        val meaningView = label("", 20f, pal.text, bold = true).apply {
            setLineSpacing(0f, 1.1f)
            setPadding(0, dp(4f), 0, 0)
        }
        val dictPill = label("사전 ↗", 12.5f, pal.sub).apply {
            maxLines = 1
            gravity = Gravity.CENTER
            minWidth = dp(60f)
            minHeight = dp(40f)
            setPadding(dp(10f), dp(6f), dp(10f), dp(6f))
            background = rippleRounded(pal.button, dp(10f), dp(1f), pal.line)
            contentDescription = "$ch 네이버 한자사전 열기"
            setOnClickListener { open(KanjiText.naverHanjaUrl(ch)) }
        }
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            addView(meaningView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(dictPill, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(8f) })
        })
        if (k != null) {
            // The plain-Korean meanings and the Korean traditional form, small, under the 훈음.
            val old = listOfNotNull(
                k.gloss.takeIf { it.isNotEmpty() }?.let { "뜻 $it" },
                k.variant.takeIf { it.isNotEmpty() }?.let { "정자 $it" },
                "일본 국자".takeIf { k.kokuji && (k.hunStd.isNotEmpty() || k.hun.isNotEmpty()) },
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
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(row)
            addView(panel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8f) })
        }
        val e = EntryViews(ch, k, meaningView, english, wordSlot, panel, box, row, left, hasStrokes, leftCaption)
        entryViews.add(e)
        updateEntry(e, inWord, ctxMeaning)
        return box
    }

    /**
     * Without the pane: opens [ch]'s stroke order under its row and writes it, or closes it. The big drawing is
     * made on the first open (most rows are never opened) and sized for the window as it is then: at most 200dp,
     * narrower than the card, and under half the height on a low window.
     */
    private fun togglePanel(ch: String, parts: Parts, panel: LinearLayout) {
        if (panel.visibility == View.VISIBLE) {
            panel.visibility = View.GONE
            syncLeftStates()
            return
        }
        openPanel(ch, parts, panel, play = true)
    }

    private fun openPanel(ch: String, parts: Parts, panel: LinearLayout, play: Boolean) {
        val m = resources.displayMetrics
        val hostWidth = (panel.parent as? View)?.width?.takeIf { it > 0 } ?: (dp(cardWidthDp().toFloat()) - dp(66f))
        val size = minOf(dp(200f), hostWidth - dp(32f), (m.heightPixels * 0.4f).toInt()).coerceAtLeast(dp(96f))
        var big = panel.getChildAt(0) as? KanjiStrokeView
        if (big == null) {
            val strokeView = KanjiStrokeView(this, shapesOf(ch, parts), size, strokeColors(), numbered = true,
                colorOf = ::strokeColor).apply {
                contentDescription = "$ch 획순 다시 보기"
                background = rounded(pal.card, dp(10f))
                setPadding(dp(6f), dp(6f), dp(6f), dp(6f))
                foreground = selectableBackground()
            }
            big = strokeView
            panel.addView(strokeView, LinearLayout.LayoutParams(size, size))
            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                addView(label("↻ 다시 재생", 13f, pal.text, bold = true).apply {
                    gravity = Gravity.CENTER
                    minHeight = dp(44f)
                    setPadding(dp(16f), 0, dp(16f), 0)
                    background = rippleRounded(pal.card, dp(10f), dp(1f), pal.line)
                    setOnClickListener { strokeView.play() }
                })
                addView(label("접기 ▴", 13f, pal.sub, bold = true).apply {
                    gravity = Gravity.CENTER
                    minHeight = dp(44f)
                    setPadding(dp(16f), 0, dp(16f), 0)
                    background = rippleRounded(pal.card, dp(10f), dp(1f), pal.line)
                    setOnClickListener {
                        panel.visibility = View.GONE
                        syncLeftStates()
                    }
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(8f) })
            }
            panel.addView(actions, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8f) })
        } else if (big.layoutParams.width != size) {
            big.layoutParams = LinearLayout.LayoutParams(size, size)
        }
        panel.visibility = View.VISIBLE
        syncLeftStates()
        if (play) {
            big.play()
            // Bring the panel into view: it opens below the row, often under the visible part.
            panel.post { panel.requestRectangleOnScreen(Rect(0, 0, panel.width, panel.height), false) }
        }
    }

    /** The English meanings; a tap swaps them for the short Korean ones and back. */
    private fun englishLine(k: Kanji): TextView {
        val en = k.meanings
        val ko = k.englishKo
        return label(en, 13f, pal.sub).apply {
            if (ko.isEmpty()) {
                setPadding(0, dp(2f), 0, dp(2f))
                return@apply
            }
            minHeight = dp(42f)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8f), 0, dp(8f))
            background = selectableBackground()
            var korean = false
            fun show() {
                val s = SpannableStringBuilder(if (korean) ko else en)
                val start = s.length
                // A no-break space: the arrow and its letter never part at a line end.
                s.append(if (korean) "  ⇄\u00A0영" else "  ⇄\u00A0한")
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
        if (!showHangul) return
        // The okurigana is a stretch of its own: 思う is 오모우 (the う is said), 大きい 오-키-.
        val start = s.length
        s.append("(").append(KanaHangul.convertParts(listOf(stem, okurigana))).append(")")
        s.setSpan(ForegroundColorSpan(pal.sub), start, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
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
        val link = readingRow("", closed, 13.5f).apply {
            minimumHeight = dp(44f)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8f), 0, dp(8f))
        }
        val linkText = link.getChildAt(1) as TextView
        linkText.setTextColor(pal.accent)
        linkText.paint.isFakeBoldText = true
        link.background = selectableBackground()
        link.contentDescription = "나머지 읽기 ${plan.hidden}개 펼치기"
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
            if (r.limited) {
                val at = s.length
                s.append(" 한정")
                s.setSpan(ForegroundColorSpan(pal.sub), at, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                s.setSpan(RelativeSizeSpan(0.72f), at, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
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
                    s.append("(").append(w.kana)
                    if (showHangul) s.append(" ").append(wordHangul(w.form, w.kana))
                    s.setSpan(ForegroundColorSpan(pal.sub), at, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
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

    /** Appends "(메-)" for [kana], smaller and in light grey, when the Hangul pronunciation is on. */
    private fun hangul(s: SpannableStringBuilder, kana: String) {
        if (!showHangul) return
        val start = s.length
        s.append("(").append(KanaHangul.convert(kana)).append(")")
        s.setSpan(ForegroundColorSpan(pal.sub), start, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        s.setSpan(RelativeSizeSpan(0.82f), start, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    /** "음  メイ(메-) · ミョウ(묘-)": a small label column, then the readings in the Japanese font. */
    private fun readingRow(name: String, value: CharSequence, sp: Float = 17f): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(6f), 0, dp(2f))
        addView(label(name, 13f, pal.accent, bold = true).apply {
            minWidth = nameWidth(paint)
            setPadding(0, dp(if (sp >= 17f) 2f else 1f), 0, 0)
        })
        addView(label(value, sp, pal.text).apply {
            japanese()
            setLineSpacing(0f, 1.22f)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    /** The label column: as wide as its longest name ("외우기", "유래 ▸") and a gap, so every row's value starts in line. */
    private var nameWidthPx = 0

    private fun nameWidth(paint: android.graphics.Paint): Int {
        if (nameWidthPx == 0) nameWidthPx = maxOf(dp(44f),
            (listOf("외우기", "유래 ▸", "닮은꼴").maxOf { paint.measureText(it) } + dp(8f)).toInt())
        return nameWidthPx
    }

    /**
     * What helps the kanji stick, in the order a learner uses it: its parts with the radical and the sound part in the
     * colours of the drawing and the radical's name after them (구성; a tap lists other kanji with that radical), the
     * memory story (외우기), the kanji to learn with it (확장) and those mistaken for it (닮은꼴), each compared side by
     * side on a tap, and last, folded, how it really came to be (유래).
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
            if (!none) shownKosei = kosei
            val baseParts = if (!none) (kosei?.let { koseiText(p, it, k) } ?: partsText(p)) else null
            val r = PartsStore.radical(p)
            // A tap lists other easy kanji with this radical, so its name becomes a rule (氵: 池 海 泳 …).
            val mates = if (r != null) PartsStore.sameRadical(p, ch) else emptyList()
            fun buildLine(open: Boolean): SpannableStringBuilder {
                val s = SpannableStringBuilder()
                if (baseParts != null) s.append(baseParts)
                if (r != null) {
                    if (s.isNotEmpty()) s.append("  ")
                    s.append(radicalNote(ch, p, r, kosei, full = s.isEmpty(), more = mates.isNotEmpty(), open = open))
                }
                return s
            }
            val closedLine = buildLine(open = false)
            if (closedLine.isNotEmpty()) {
                val row = readingRow("구성", closedLine, 15f)
                col.addView(row)
                if (mates.isNotEmpty()) {
                    val openLine = buildLine(open = true)
                    val valueView = row.getChildAt(1) as TextView
                    val list = readingRow("", "", 14.5f).apply { visibility = View.GONE }
                    row.minimumHeight = dp(44f)
                    row.setPadding(0, dp(8f), 0, dp(8f))
                    row.background = selectableBackground()
                    row.contentDescription = "같은 부수의 한자 보기"
                    row.setOnClickListener {
                        if (list.visibility == View.VISIBLE) {
                            list.visibility = View.GONE
                            valueView.text = closedLine
                            return@setOnClickListener
                        }
                        val text = list.getChildAt(1) as TextView
                        if (text.text.isEmpty()) text.text = matesText(radicalShape(p, r!!), r, mates)
                        list.visibility = View.VISIBLE
                        valueView.text = openLine
                    }
                    col.addView(list)
                }
            }
        }
        if (origin != null && origin.memo.isNotEmpty()) col.addView(readingRow("외우기", origin.memo, 14.5f))
        if (p != null && k != null) addExpandRow(col, ch, k, p)
        if (p != null) {
            // 닮은꼴: the kanji mistaken for this one by shape (持: 待 特), whatever their reading, each with its
            // 훈음 so the difference in meaning shows at once; a tap draws two of them side by side.
            val alike = PartsStore.similar(ch).filter { PartsStore.hasStrokes(it) }
            if (alike.isNotEmpty() && PartsStore.hasStrokes(ch)) {
                val box = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    visibility = View.GONE
                    isClickable = true
                }
                val closedSimilar = similarText(alike, open = false)
                val openSimilar = similarText(alike, open = true)
                col.addView(readingRow("닮은꼴", closedSimilar, 15f).apply {
                    minimumHeight = dp(44f)
                    setPadding(0, dp(8f), 0, dp(8f))
                    background = selectableBackground()
                    contentDescription = "닮은 한자 비교하기"
                    val valueView = getChildAt(1) as TextView
                    setOnClickListener {
                        if (box.visibility == View.VISIBLE) {
                            box.visibility = View.GONE
                            valueView.text = closedSimilar
                            return@setOnClickListener
                        }
                        if (box.childCount == 0) buildSimilar(ch, alike, box)
                        box.visibility = View.VISIBLE
                        valueView.text = openSimilar
                        box.post { box.requestRectangleOnScreen(Rect(0, 0, box.width, box.height), false) }
                    }
                })
                col.addView(box, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT))
            }
        }
        origin?.let { o ->
            // "뜻+소리: …": the type in plain words, then the note, without what the 구성 line just said ("뜻은
            // 氵(물), 소리는 毎(マイ).") and without a bare repeat of the 훈음. Folded: a tap opens it.
            val body = o.shown(shownKosei, listOfNotNull(k?.hunStd, k?.hun, k?.gloss))
            if (body.isEmpty()) return@let
            val s = SpannableStringBuilder()
            if (o.label.isNotEmpty()) {
                s.append(o.label).append(":")
                s.setSpan(ForegroundColorSpan(pal.accent), 0, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                s.setSpan(StyleSpan(Typeface.BOLD), 0, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                s.append(" ")
            }
            s.append(body)
            val arrowAt = s.length
            s.append("  ▾")
            s.setSpan(ForegroundColorSpan(pal.accent), arrowAt, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val collapsed = "펼쳐 보기 ▸"
            val row = readingRow("유래", collapsed, 13.5f).apply {
                minimumHeight = dp(44f)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(8f), 0, dp(8f))
            }
            val text = row.getChildAt(1) as TextView
            text.setTextColor(pal.sub)
            var open = false
            row.background = selectableBackground()
            row.contentDescription = "유래 펼치기"
            row.setOnClickListener {
                open = !open
                row.gravity = if (open) Gravity.TOP else Gravity.CENTER_VERTICAL
                text.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, if (open) 14.5f else 13.5f)
                text.setTextColor(if (open) pal.text else pal.sub)
                text.text = if (open) s else collapsed
            }
            col.addView(row)
        }
    }

    /**
     * 확장: up to three 상용한자 to learn with this one (PartsStore.expand: the same sound part and reading, those with
     * this kanji as their sound part, …), "慎(삼갈 신 · シン · 慎重) / 鎮(진압할 진 · チン · 鎮圧)  ▸"; a tap draws them
     * side by side with this one, and a tap on a drawing there opens that kanji.
     */
    private fun addExpandRow(col: LinearLayout, ch: String, k: Kanji, p: Parts) {
        val members = PartsStore.expand(ch).ifEmpty { familyItems(k, p).map { it.first } }.filter { it != ch }.take(3)
        if (members.isEmpty()) return
        val usages = members.associateWith { UsageStore.get(it) }
        val items = members.map { c ->
            c to (usages[c]?.on?.firstOrNull()?.reading
                ?: KanjiStore.get(c)?.let { KanjiText.onReadings(it.on).firstOrNull() } ?: "")
        }
        val base = SpannableStringBuilder()
        items.forEachIndexed { i, (c, reading) ->
            if (i > 0) base.append(" / ")
            base.append(c)
            val word = usages[c]?.on?.firstOrNull()?.words?.firstOrNull()?.form
            val inside = listOf(firstHun(c), reading, word.orEmpty()).filter { it.isNotEmpty() }.joinToString(DOT)
            if (inside.isNotEmpty()) {
                val start = base.length
                base.append("(").append(inside).append(")")
                base.setSpan(ForegroundColorSpan(pal.sub), start, base.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                base.setSpan(RelativeSizeSpan(0.82f), start, base.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        val compare = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            // A tap between the drawings stays here rather than open the dictionary like the rest of the row.
            isClickable = true
        }
        val drawn = PartsStore.hasStrokes(ch) && items.any { PartsStore.hasStrokes(it.first) }
        fun expandText(open: Boolean): CharSequence {
            if (!drawn) return base
            return SpannableStringBuilder(base).apply {
                val start = length
                append(if (open) "  ▾" else "  ▸")
                setSpan(ForegroundColorSpan(pal.accent), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        val closedExpand = expandText(open = false)
        val openExpand = if (drawn) expandText(open = true) else closedExpand
        col.addView(readingRow("확장", closedExpand, 15f).apply {
            if (!drawn) return@apply
            minimumHeight = dp(44f)
            setPadding(0, dp(8f), 0, dp(8f))
            background = selectableBackground()
            contentDescription = "관련 한자 비교하기"
            val valueView = getChildAt(1) as TextView
            setOnClickListener {
                toggleCompare(ch, k, p, items, compare)
                valueView.text = if (compare.visibility == View.VISIBLE) openExpand else closedExpand
            }
        })
        col.addView(compare, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT))
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
     * "부수 冖 민갓머리 ▸" after the 구성 line (or alone, with the radical's meaning, for a kanji without one): the
     * radical as it looks here in the accent colour and its Korean name, small and grey. When the radical is the
     * sound part or lies inside it (到's 刂, 修's 亻), it only files the kanji in dictionaries: "(찾기용)".
     */
    private fun radicalNote(
        ch: String, p: Parts, r: Radical, kosei: List<Kosei.Unit>?, full: Boolean, more: Boolean, open: Boolean = false
    ): CharSequence {
        val s = SpannableStringBuilder("부수 ")
        val shape = radicalShape(p, r)
        val at = s.length
        s.append(shape)
        s.setSpan(ForegroundColorSpan(pal.accent), at, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        s.setSpan(StyleSpan(Typeface.BOLD), at, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        s.append(" ").append(r.korean)
        if (full && r.meaning.isNotEmpty()) s.append("(").append(r.meaning).append(")")
        val inSound = kosei?.let(Kosei::radicalOnlySound) ?: p.radicalInSound
        // When the 구성 line calls the radical's part something else (会's 人 = 뚜껑), say so, so the two do not seem
        // to disagree.
        val here = kosei?.let { Kosei.radicalHere(it, r.korean, r.meaning) }
        when {
            shape == ch -> s.append(" (글자 자체)")
            inSound -> s.append(" (찾기용)")
            here != null -> s.append(" (여기서는 '$here')")
        }
        if (more) {
            val start = s.length
            s.append(if (open) " ▾" else " ▸")
            s.setSpan(ForegroundColorSpan(pal.accent), start, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (!full) {
            s.setSpan(ForegroundColorSpan(pal.sub), 0, at, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            s.setSpan(ForegroundColorSpan(pal.sub), at + shape.length, s.length - (if (more) 2 else 0),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            s.setSpan(RelativeSizeSpan(0.85f), 0, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
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
        s.append("(").append(r.meaning.ifEmpty { r.korean }).append(") 한자\n")
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
        val glyphs = headerView.paint
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
            if (part.radicalInside && drawable(radShape)) notes.add("부수 $radShape 포함" to (radShape to pal.accent))
            if (part.soundInside) {
                if (drawable(part.inner)) notes.add("소리 ${part.inner} 포함" to (part.inner to pal.sound))
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
        val glyphs = headerView.paint
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
                notes.add("부수 $radShape 포함" to (radShape to pal.accent))
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
        return k.hunStd.ifEmpty { k.hun }.split(", ", " / ").firstOrNull { it.isNotBlank() }?.trim().orEmpty()
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

    /** "待(기다릴 대) / 特(특별할 특)  ▸": each look-alike with its 훈음, small and grey. */
    private fun similarText(alike: List<String>, open: Boolean = false): CharSequence {
        val s = SpannableStringBuilder()
        alike.forEachIndexed { i, c ->
            if (i > 0) s.append(" / ")
            s.append(c)
            val hun = firstHun(c)
            if (hun.isNotEmpty()) {
                val start = s.length
                s.append("(").append(hun).append(")")
                s.setSpan(ForegroundColorSpan(pal.sub), start, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                s.setSpan(RelativeSizeSpan(0.82f), start, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        val start = s.length
        s.append(if (open) "  ▾" else "  ▸")
        s.setSpan(ForegroundColorSpan(pal.accent), start, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return s
    }

    /**
     * The 닮은 한자 side by side: this kanji and one look-alike drawn next to each other in the card's colours (the
     * radical in the accent colour, the sound part in blue), each with its 훈음 and its first on and kun readings,
     * so shape, meaning and sound are told apart together; the buttons under them switch the one compared.
     */
    private fun buildSimilar(ch: String, alike: List<String>, box: LinearLayout) {
        val members = alike.filter { drawable(it) != null }
        val mine = drawable(ch)
        box.setPadding(0, dp(6f), 0, dp(4f))
        if (members.isEmpty() || mine == null) {
            box.addView(label("비교할 획 그림이 없습니다.", 12f, pal.sub))
            return
        }
        val width = (box.parent as? View)?.width?.takeIf { it > 0 } ?: dp(cardWidthDp().toFloat())
        val size = minOf(dp(120f), (width - dp(36f)) / 2).coerceAtLeast(dp(64f))
        fun cell(c: String): View = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val parts = drawable(c)!!
            addView(KanjiStrokeView(this@LookupActivity, shapesOf(c, parts), size, strokeColors(),
                colorOf = ::strokeColor).apply {
                contentDescription = if (c == ch) c else "$c 카드로 이동"
                if (c != ch) setOnClickListener { openKanji(c) }
            }, LinearLayout.LayoutParams(size, size))
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
            pair.addView(cell(members[index]))
            for (j in 0 until chips.childCount) {
                val chip = chips.getChildAt(j) as TextView
                val on = j == index
                chip.setTextColor(if (on) pal.accent else pal.text)
                chip.paint.isFakeBoldText = on
                chip.background = rippleRounded(
                    pal.card, dp(10f),
                    dp(if (on) 1.5f else 1f),
                    if (on) pal.accent else pal.line
                )
            }
        }
        members.forEachIndexed { j, c ->
            chips.addView(label(c, 20f, pal.text).apply {
                japanese()
                gravity = Gravity.CENTER
                minWidth = dp(48f)
                minHeight = dp(44f)
                setPadding(dp(10f), dp(6f), dp(10f), dp(6f))
                contentDescription = "$c 비교"
                setOnClickListener { select(j) }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = dp(3f)
                rightMargin = dp(3f)
            })
        }
        box.addView(pair, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        if (members.size > 1) box.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(chips)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(6f)
        })
        box.addView(label("모양·뜻·읽기 차이를 비교해 보세요 · 한자를 누르면 이동합니다", 12f,
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
            box.addView(label("비교할 획 그림이 없습니다.", 12f, pal.sub))
            return
        }
        // Two drawings and the arrow between them in the width the row has now.
        val width = (box.parent as? View)?.width?.takeIf { it > 0 } ?: dp(cardWidthDp().toFloat())
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
                colorOf = colorsFor(c)).apply {
                contentDescription = if (c == ch) c else "$c 카드로 이동"
                if (c != ch) setOnClickListener { openKanji(c) }
            }, LinearLayout.LayoutParams(size, size))
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
                chip.background = rippleRounded(
                    pal.card, dp(10f),
                    dp(if (on) 1.5f else 1f),
                    if (on) pal.accent else pal.line
                )
            }
        }
        members.forEachIndexed { j, (m, _) ->
            chips.addView(label(m, 20f, pal.text).apply {
                japanese()
                gravity = Gravity.CENTER
                minWidth = dp(48f)
                minHeight = dp(44f)
                setPadding(dp(10f), dp(6f), dp(10f), dp(6f))
                contentDescription = "$m 비교"
                setOnClickListener { select(j) }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = dp(3f)
                rightMargin = dp(3f)
            })
        }
        box.addView(pair, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        if (members.size > 1) box.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(chips)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(6f)
        })
        box.addView(label("파란색이 소리 부품 · 한자를 누르면 이동합니다", 12f, pal.sub).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(4f), 0, 0)
        })
        select(0)
    }

    private fun open(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "사전을 열 브라우저 앱이 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    /** "예문 보기 ▸": a tap fills the section (from the saved ones at once, else from Claude). */
    private fun examplesLink(): TextView = label("예문 보기  ▸", 15f, pal.accent, bold = true).apply {
        minHeight = dp(48f)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
        background = rippleRounded(pal.button, dp(10f), dp(1f), pal.line)
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
                link?.text = "예문 생성 중…"
            }
            val (json, problem) = client.json(Example.SYSTEM_PROMPT, Example.userPrompt(word, sentence), Example.SCHEMA, 2048)
            val list = json?.let { Example.parse(it) }.orEmpty()
            if (!client.cancelled && json != null && list.isNotEmpty()) Prefs.cacheExamples(app, word, sentence, json)
            main.post {
                if (destroyed || examplesClient !== client) return@post
                examplesClient = null
                if (list.isNotEmpty()) {
                    showExamples(list)
                } else {
                    examplesBusy = false
                    link?.setTextColor(pal.accent)
                    link?.text = "예문 생성 실패 · 다시 시도  ▸" + (problem?.let { "\n$it" } ?: "")
                }
            }
        }.start()
    }

    /** Without an API key: say so and open the settings screen. */
    private fun needKey() {
        Toast.makeText(this, "먼저 Claude API 키를 등록해 주세요.", Toast.LENGTH_LONG).show()
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
            hint = "질문을 입력하세요"
            japanese()
            maxLines = 4
            minHeight = dp(48f)
            background = rounded(pal.button, dp(12f))
            setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
        }
        sendButton = Button(this).apply {
            text = "보내기"
            isAllCaps = false
            textSize = 14f
            setTextColor(pal.card)
            background = rippleRounded(pal.accent, dp(12f))
            stateListAnimator = null
            minHeight = dp(48f)
            minimumHeight = dp(48f)
            setPadding(dp(16f), 0, dp(16f), 0)
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

    private fun isChatOpen(): Boolean = inputRow.visibility == View.VISIBLE

    private fun updateChatButton() {
        if (!::chatButton.isInitialized) return
        if (isChatOpen()) {
            chatButton.text = "카드 보기"
            chatButton.setTextColor(pal.card)
            chatButton.background = rippleRounded(pal.accent, dp(12f))
        } else {
            val turns = (chatTurns.length() + 1) / 2
            chatButton.text = if (turns > 0) "질문 ($turns)" else "질문"
            chatButton.setTextColor(pal.accent)
            chatButton.background = rippleRounded(
                pal.button, dp(12f), dp(1f),
                (pal.accent and 0x00FFFFFF) or (0x66 shl 24)
            )
        }
    }

    /** Switches the card to the chat: the kanji rows make room, the input gets the selection and the keyboard. */
    private fun openChat() {
        if (!isChatOpen()) {
            savedCardScrollY = scroll.scrollY
            entries.visibility = View.GONE
            examplesBox.visibility = View.GONE
            answer.visibility = View.GONE
            chatLog.visibility = View.VISIBLE
            inputRow.visibility = View.VISIBLE
            scroll.fraction = TALK_FRACTION
            updateChatButton()
            updateJumpBar()
            if (input.text.isEmpty() && selection.isNotEmpty()) input.setText("$selection ")
            scrollToEnd()
        }
        input.setSelection(input.text.length)
        input.requestFocus()
        input.post {
            getSystemService(InputMethodManager::class.java)?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
        // While the question is typed, the connection to Claude gets ready.
        if (chatClient == null && Prefs.apiKey(this).isNotEmpty()) ClaudeClient.warmUp()
    }

    /** Returns from the chat to the kanji rows while preserving [chatLog] and [chatTurns]. */
    private fun closeChat() {
        if (!isChatOpen()) return
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(input.windowToken, 0)
        if (currentFocus === input) {
            focusSink.isFocusable = true
            focusSink.requestFocus()
        }
        chatLog.visibility = View.GONE
        inputRow.visibility = View.GONE
        entries.visibility = View.VISIBLE
        examplesBox.visibility = View.VISIBLE
        val hasAnswer = answerText.isNotEmpty() || answerProblem != null || claude != null
        answer.visibility = if (hasAnswer) View.VISIBLE else View.GONE
        scroll.fraction = if (hasAnswer) TALK_FRACTION else ROWS_FRACTION
        updateChatButton()
        updateJumpBar()
        val y = savedCardScrollY
        scroll.post {
            if (!destroyed && !isChatOpen()) {
                scroll.scrollTo(0, y)
                stick(scroll.scrollY)
            }
        }
    }

    /** What the user sent: a rounded bubble on the right, at most 85% of the card's width. */
    private fun myBubble(text: CharSequence): View {
        val avail = chatLog.width.takeIf { it > 0 }
            ?: (scroll.width - body.paddingRight).takeIf { it > 0 }
            ?: (dp(cardWidthDp().toFloat()) - dp(66f))
        val t = label(text, 15f, pal.text).apply {
            japanese()
            setTextIsSelectable(true)
            setLineSpacing(0f, 1.25f)
            background = rounded(pal.button, dp(18f))
            setPadding(dp(14f), dp(10f), dp(14f), dp(10f))
            maxWidth = (avail * 0.85f).toInt().coerceAtLeast(dp(160f))
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
        setLineSpacing(0f, 1.35f)
        setPadding(0, dp(10f), 0, dp(4f))
    }

    private fun scrollToEnd() {
        scroll.post { if (!destroyed) scroll.scrollTo(0, body.height) }
    }

    private class SavedReply(val body: String, val problem: String?)

    private fun renderAi(body: String, problem: String?): CharSequence {
        val s = SpannableStringBuilder(rich(body))
        if (!problem.isNullOrEmpty()) {
            s.append("\n\n")
            val start = s.length
            s.append(problem)
            s.setSpan(ForegroundColorSpan(pal.sub), start, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return s
    }

    /**
     * An AI answer in the card's look (Rich): 【】 labels in the accent colour, bullets with a hanging indent,
     * translation lines in grey, bold, and readings small and grey with the Hangul pronunciation in grey.
     */
    private fun rich(text: String): CharSequence {
        val doc = Rich.parse(text, showHangul)
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
            Rich.Kind.HANGUL -> span(ForegroundColorSpan(pal.sub), m)
            Rich.Kind.HEAD -> span(StyleSpan(Typeface.BOLD), m)
            Rich.Kind.TIP -> {
                span(ForegroundColorSpan(pal.accent), m)
                span(StyleSpan(Typeface.BOLD), m)
            }
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
                val atEnd = view.isShown && !scroll.canScrollVertically(1)
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
        val content = if (chatTurns.length() == 0) KanjiText.chatContext(selection, context, cardFacts()) + text else text
        chatTurns.put(JSONObject().put("role", "user").put("content", content))
        updateChatButton()
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
            // conversation: cache growing history once follow-up turns exist, avoiding +25% cache write cost on single-shot questions.
            val problem = client.run(system, turns, Prefs.chatEffort(applicationContext), conversation = turns.length() >= 3, onText = stream::append)
            main.post {
                if (destroyed || chatClient !== client) return@post
                val got = stream.finish()
                chatClient = null
                sendButton.isEnabled = true
                if (got.isNotEmpty()) {
                    reply.tag = SavedReply(got, problem)
                    reply.setTextColor(pal.text)
                    reply.text = renderAi(got, problem)
                } else {
                    reply.setTextColor(pal.accent)
                    reply.text = problem ?: "응답을 받지 못했습니다. 다시 보내 주세요."
                }
                if (problem == null && got.isNotEmpty()) {
                    chatTurns.put(JSONObject().put("role", "assistant").put("content", got))
                } else {
                    // Nothing came back or the stream broke: drop the question so the next one still alternates user/assistant.
                    if (chatTurns.length() > 0) chatTurns.remove(chatTurns.length() - 1)
                }
                updateChatButton()
            }
        }.start()
    }

    /** What this card shows for the selection's kanji, for the chat (KanjiText.cardFacts); "" when the data is not loaded. */
    private fun cardFacts(): String = runCatching {
        KanjiText.cardFacts(KanjiText.kanjiIn(selection).distinct().mapNotNull { KanjiStore.get(it) }) { PartsStore.origin(it) }
    }.getOrDefault("")

    private fun askClaude() {
        val key = Prefs.apiKey(this)
        if (key.isEmpty()) {
            needKey()
            return
        }
        if (isChatOpen()) closeChat()
        claude?.cancel()
        val client = ClaudeClient(key, Prefs.chatModel(this))
        claude = client
        claudeButton.isEnabled = false
        answerText = ""
        answerProblem = null
        answer.visibility = View.VISIBLE
        answer.setTextColor(pal.sub)
        answer.text = "AI 답변 생성 중…"
        scroll.fraction = TALK_FRACTION
        updateJumpBar()
        val stream = StreamReply(answer) { claude === client }
        Thread {
            val turns = JSONArray().put(JSONObject().put("role", "user").put("content", KanjiText.userPrompt(selection, context)))
            val problem = client.run(KanjiText.SYSTEM_PROMPT, turns, Prefs.chatEffort(applicationContext), onText = stream::append)
            main.post {
                if (destroyed || claude !== client) return@post
                val text = stream.finish()
                claude = null
                claudeButton.isEnabled = true
                claudeButton.text = "다시 설명"
                if (text.isNotEmpty()) {
                    answerText = text
                    answerProblem = problem
                    answer.setTextColor(pal.text)
                    answer.text = renderAi(text, problem)
                } else {
                    answerText = ""
                    answerProblem = problem
                    answer.setTextColor(pal.accent)
                    answer.text = problem ?: "응답을 받지 못했습니다. 다시 시도해 주세요."
                }
            }
        }.start()
    }
}
