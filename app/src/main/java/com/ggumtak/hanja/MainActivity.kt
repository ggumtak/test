package com.ggumtak.hanja

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.ggumtak.hanja.update.AppUpdater
import com.ggumtak.hanja.update.UpdateInfo

/** The app icon: compact settings dashboard, with tutorials and data sources tucked into the '?' help dialog. */
class MainActivity : Activity() {
    private companion object {
        const val ANKI_PERMISSION = 7
        const val VOCAB_PERMISSION = 8
        const val PICK_APKG = 9

        /** Instead of the template tools while myAmgi is the chosen app: it loads the card code itself. */
        const val NATIVE_TAP_NOTE =
            "myAmgi는 한자 바로보기의 카드 코드를 직접 불러오므로 노트 유형에 코드를 넣을 필요가 없습니다(‘Anki 카드에 적용’ 불필요). " +
                "myAmgi 설정에서 ‘한자 바로보기 자동 주입’이 켜져 있으면 카드의 한자를 탭해 바로 열 수 있습니다. " +
                "단어장 추가와 카드 읽기는 myAmgi로 그대로 됩니다."
    }

    private fun ankiPermissionDenied(): String {
        val app = AnkiBridge.label(this)
        val tip = if (AnkiBridge.target(this)?.isMyAmgi == true) {
            " myAmgi를 이 앱보다 나중에 설치했다면 이 앱의 업데이트를 한 번 설치하거나 이 앱을 다시 설치해 주세요."
        } else ""
        return "${app} 접근 권한이 필요합니다. 권한 창이 뜨지 않으면 기기 설정 → 앱 → 한자 바로보기 → 권한에서 허용해 주세요.$tip"
    }

    private lateinit var pal: Palette
    private lateinit var keyState: TextView
    private lateinit var apkgButton: Button
    private var patchingApkg = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pal = Palette(this)
        val pad = dp(20f)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(24f), pad, dp(28f))
        }

        // Top header: title + '?' tutorial button.
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(label("한자 바로보기", 26f, pal.text, bold = true),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(label("?", 18f, pal.accent, bold = true).apply {
                gravity = Gravity.CENTER
                background = rounded(pal.button, dp(22f), dp(1f), pal.line)
                contentDescription = "사용법 보기"
                setOnClickListener { showHelpDialog() }
            }, LinearLayout.LayoutParams(dp(44f), dp(44f)))
        }
        col.addView(header)
        col.addView(updateRow())

        col.addView(button("시험해 보기") {
            startActivity(Intent(this, LookupActivity::class.java)
                .putExtra(Intent.EXTRA_PROCESS_TEXT, "母は電気をつけて"))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(16f)
        })

        col.addView(CheckBox(this).apply {
            text = "한글 발음 함께 보기"
            textSize = 14.5f
            setTextColor(pal.text)
            isChecked = Prefs.showHangul(this@MainActivity)
            setOnCheckedChangeListener { _, on -> Prefs.setShowHangul(this@MainActivity, on) }
            setPadding(dp(4f), dp(8f), 0, dp(4f))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(6f)
        })

        section(col, "Anki 연동")
        // Which Anki app the 단어장 and card reading use (AnkiTarget): AnkiDroid, myAmgi or myAmgi 코멧.
        targetState = body("").apply { setPadding(0, 0, 0, dp(4f)) }
        col.addView(targetState)
        targetNote = label("", 13f, pal.sub).apply {
            setLineSpacing(0f, 1.3f)
            setPadding(0, 0, 0, dp(6f))
        }
        col.addView(targetNote)
        targetButton = button("연결할 앱 바꾸기") { chooseTarget() }
        col.addView(targetButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(10f)
        })
        nativeNote = body(NATIVE_TAP_NOTE).apply {
            setTextColor(pal.accent)
            setPadding(0, 0, 0, dp(4f))
        }
        col.addView(nativeNote)
        ankiState = body("").apply {
            setTextColor(pal.accent)
            visibility = View.GONE
            setPadding(0, 0, 0, dp(8f))
        }
        col.addView(ankiState)
        ankiButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(button("Anki 카드에 적용") { chooseNoteTypes() },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(button("로더 코드 복사") {
                val cm = getSystemService(ClipboardManager::class.java)
                cm.setPrimaryClip(ClipData.newPlainText("한자 바로보기 로더 코드", ankiCode()))
                Toast.makeText(this@MainActivity,
                    "로더 코드를 복사했습니다. 앞면·뒷면 템플릿 맨 앞에 붙여 넣은 뒤 이 앱으로 돌아오세요.", Toast.LENGTH_LONG).show()
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(8f) })
        }
        col.addView(ankiButtons)
        apkgButton = button("큰 템플릿 파일로 자동 적용 (.apkg)") { showApkgGuide() }
        col.addView(apkgButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8f)
        })
        showTarget()

        section(col, "단어장")
        vocabState = body("").apply { setPadding(0, 0, 0, dp(8f)) }
        col.addView(vocabState)
        showVocab()
        col.addView(button("설정 확인") { setUpVocab() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        col.addView(CheckBox(this).apply {
            text = "${VocabNote.AUTO_AFTER}번째 찾아보면 자동 추가"
            textSize = 14.5f
            setTextColor(pal.text)
            isChecked = Prefs.vocabAuto(this@MainActivity)
            setOnCheckedChangeListener { _, on -> Prefs.setVocabAuto(this@MainActivity, on) }
            setPadding(dp(4f), dp(8f), 0, dp(4f))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(6f)
        })

        section(col, "AI 설정 (Claude)")
        keyState = body("").apply { setPadding(0, 0, 0, dp(6f)) }
        col.addView(keyState)
        val input = EditText(this).apply {
            hint = "API 키 입력 (sk-ant-…)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
            textSize = 14.5f
            setTextColor(pal.text)
            setHintTextColor(pal.sub)
            background = rounded(pal.button, dp(10f))
            setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
        }
        col.addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8f), 0, 0)
        }
        row.addView(button("저장") {
            val v = input.text.toString().trim()
            if (!v.startsWith("sk-ant-")) {
                Toast.makeText(this, "sk-ant-로 시작하는 키를 입력해 주세요.", Toast.LENGTH_SHORT).show()
                return@button
            }
            Prefs.setApiKey(this, v)
            input.setText("")
            showKey()
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(button("삭제") {
            Prefs.setApiKey(this, "")
            showKey()
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(8f) })
        col.addView(row)
        showKey()

        col.addView(label("AI 모델", 14f, pal.text, bold = true).apply { setPadding(0, dp(14f), 0, dp(4f)) })
        val chosen = Prefs.chatModel(this)
        col.addView(RadioGroup(this).apply {
            for ((i, m) in ClaudeClient.CHAT_MODELS.withIndex()) {
                addView(RadioButton(this@MainActivity).apply {
                    id = 1000 + i
                    text = m.second
                    textSize = 14f
                    setTextColor(pal.text)
                    isChecked = m.first == chosen
                })
            }
            setOnCheckedChangeListener { _, checkedId ->
                ClaudeClient.CHAT_MODELS.getOrNull(checkedId - 1000)?.let { Prefs.setChatModel(this@MainActivity, it.first) }
            }
        })

        col.addView(label("추론 깊이", 14f, pal.text, bold = true).apply { setPadding(0, dp(10f), 0, dp(4f)) })
        val effort = Prefs.chatEffort(this)
        col.addView(RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            for ((i, e) in Prefs.EFFORTS.withIndex()) {
                addView(RadioButton(this@MainActivity).apply {
                    id = 2000 + i
                    text = e.second
                    textSize = 13.5f
                    setTextColor(pal.text)
                    setPadding(dp(2f), 0, dp(10f), 0)
                    isChecked = e.first == effort
                })
            }
            setOnCheckedChangeListener { _, checkedId ->
                Prefs.EFFORTS.getOrNull(checkedId - 2000)?.let { Prefs.setChatEffort(this@MainActivity, it.first) }
            }
        })

        col.addView(CheckBox(this).apply {
            text = "문맥 발음 자동 판정"
            textSize = 14.5f
            setTextColor(pal.text)
            isChecked = Prefs.autoContext(this@MainActivity)
            setOnCheckedChangeListener { _, on -> Prefs.setAutoContext(this@MainActivity, on) }
            setPadding(dp(4f), dp(8f), 0, dp(4f))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(6f)
        })

        col.addView(divider(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
            topMargin = dp(18f)
            bottomMargin = dp(8f)
        })
        col.addView(label("사용법 · 단축키 · 자료 출처 보기  ▸", 14f, pal.sub).apply {
            gravity = Gravity.CENTER_VERTICAL
            minHeight = dp(44f)
            background = selectableBackground()
            setOnClickListener { showHelpDialog() }
        })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(pal.card)
            // In a wide window (a tablet, not in split screen) the text would run across the whole screen: keep it
            // to a readable column in the middle. The window's width, not the device's: this screen is recreated
            // when the window changes size.
            val maxWidth = dp(720f)
            val narrow = resources.displayMetrics.widthPixels > maxWidth + dp(48f)
            addView(col, FrameLayout.LayoutParams(
                if (narrow) maxWidth else ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL))
        })
        if (savedInstanceState == null) handleSendApkg(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSendApkg(intent)
    }

    override fun onResume() {
        super.onResume()
        showTarget()
        showVocab()
        if (!patchingApkg) refreshAnki()
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_APKG && resultCode == RESULT_OK) {
            data?.data?.let { patchApkg(it) }
        }
    }

    // ---- Help / tutorial dialog ('?') ----

    private fun showHelpDialog() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(8f), dp(20f), dp(16f))
        }
        fun addSection(title: String, content: String, first: Boolean = false) {
            if (!first) {
                box.addView(divider(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
                    topMargin = dp(14f)
                    bottomMargin = dp(12f)
                })
            }
            box.addView(label(title, 15f, pal.accent, bold = true).apply { setPadding(0, 0, 0, dp(6f)) })
            box.addView(label(content, 13.5f, pal.text).apply { setLineSpacing(0f, 1.35f) })
        }

        addSection("기본 사용법",
            "• 아무 앱에서 한자나 단어를 길게 눌러 선택한 뒤 메뉴에서 '한자'를 누르세요. (메뉴에 없으면 ⋮ 또는 공유 → 한자 사용)\n" +
                "• 상단 버튼: 🔊 발음 듣기 · 한 한글 발음('-'는 장음) · ☆ Anki 단어장에 추가\n" +
                "• 한자 카드: 붉은색은 부수, 파란색은 소리(음) 부품입니다.\n" +
                "  - 큰 한자 탭: 획순 애니메이션 재생\n" +
                "  - 구성 줄 탭: 같은 부수의 쉬운 한자 보기\n" +
                "  - 확장·닮은꼴 탭: 글자 나란히 비교 (그림 탭 시 해당 한자로 이동)\n" +
                "  - 유래 · 읽기 더 보기 탭: 펼쳐 보기\n" +
                "  - 한자 줄 빈 곳 탭: 네이버 한자사전 열기\n" +
                "• 하단 버튼: 일본어사전 · 한자사전(네이버) · AI 설명 · 질문(Claude 대화)",
            first = true)

        addSection("Anki 연동",
            "• 연결할 앱: AnkiDroid와 myAmgi 중 설치된 앱에 연결합니다. 둘 다 있으면 myAmgi가 기본이고, 'Anki 연동'의 '연결할 앱 바꾸기'로 바꿀 수 있습니다. 단어장과 카드 읽기는 고른 앱으로 갑니다.\n" +
                "• myAmgi: 한자 바로보기의 카드 코드를 직접 불러오므로 'Anki 카드에 적용'이 필요 없습니다. 아래 적용 방법은 AnkiDroid용입니다.\n" +
                "• 적용한 Anki 카드에서 한자·가타카나·획순 그림을 한 번 탭하면 바로 카드가 열립니다. 드래그 후 뜨는 '한자' 버튼도 사용할 수 있습니다.\n" +
                "• 자동 적용: 'Anki 카드에 적용'에서 원하는 노트 유형을 선택하세요. 실제 코드는 스타일(CSS)에 저장되어 앱 업데이트 시 자동으로 최신화됩니다.\n" +
                "• 큰 템플릿(JLPT MAX덱 등): 용량이 커서 직접 적용이 안 되는 카드는 '큰 템플릿 파일로 자동 적용 (.apkg)'을 이용하세요. AnkiDroid에서 미디어 끄고 .apkg로 내보내 공유하거나 파일을 선택하면 1초 만에 자동 패치됩니다. (기존 카드·복습 기록 유지)\n" +
                "• 보안 차단 안내가 뜰 때: AnkiDroid 설정 → 고급 설정 → '카드에서 다른 앱으로 이동 허용'을 켜세요. (출처를 모르는 공유 덱 사용 시 주의)")

        addSection("블루투스 키보드 단축키",
            "[Anki에서] AnkiDroid 설정 → 컨트롤(학습 화면) → 사용자 행동 1·2·3에 키를 추가하고, 이어서 뜨는 면 선택 창에서 반드시 '앞면과 뒷면'을 고르세요.\n" +
                "(컨트롤 목록에 'Q:'·'A:'가 붙어 있으면 한쪽 면에서만 작동합니다. 한영 상태와 무관하며, 미리보기가 아닌 실제 학습 화면에서 써야 합니다.)\n" +
                CardKeys.ANKI_HELP + "\n\n" +
                "※ 안 열릴 때:\n" +
                "• '사용자 행동 1이 설정되지 않았습니다' 창 → 큰 템플릿은 '큰 템플릿 파일로 자동 적용 (.apkg)'으로 먼저 로더를 넣은 뒤 덱을 나갔다가 다시 들어가세요.\n" +
                "• 아무 반응 없음 → (1) 컨트롤에서 '앞면과 뒷면' 등록 확인, (2) AnkiDroid 2.22 새 학습 화면에서 카드 안 버튼(획순 리플레이·음성)을 누른 직후에는 카드 빈 곳을 한 번 탭, (3) AnkiDroid 2.25 이상에서는 '카드에서 다른 앱으로 이동 허용' 켜기.\n\n" +
                "[한자 카드에서] 별도 설정 없이 바로 사용:\n" +
                CardKeys.HELP)

        addSection("단어장 · AI 설정",
            "• 단어장: 카드의 ☆를 누르면 Anki의 '자주 까먹는 단어' 덱에 추가됩니다. 기존 어휘 덱에 있는 단어는 음성·악센트까지 무료로 복사하고, 없는 단어만 Claude(Sonnet)가 기존 카드 형식에 맞춰 생성합니다(일본어→한국어 카드만 활성). 같은 단어를 ${VocabNote.AUTO_AFTER}번째 찾아보면 자동 추가되며, 10분 내 재조회는 1회로 계산합니다.\n" +
                "• AI 설정: API 키는 이 기기 내부에만 저장되며 요청 시에만 Anthropic으로 전송됩니다. 문맥 발음 판정은 Haiku 5.5(약 0.1원, 같은 문장은 캐시)를, AI 설명·질문·예문은 선택한 모델과 추론 깊이를 사용합니다. 추론 깊이 '낮음'은 바로 답변을 시작해 가장 빠르고(Opus는 최소 추론 유지), '중간·높음'은 더 꼼꼼하지만 응답이 다소 느려집니다.")

        box.addView(label(
            "[자료 출처]\n" +
                "• 음독·훈독·획수·영문 뜻: KANJIDIC2 (EDRDG, CC BY-SA 4.0)\n" +
                "• 단어 읽기: JMdict (EDRDG, CC BY-SA 4.0)\n" +
                "• 읽기 순서·예시 단어: 상용한자표(2010), JMdict, Jonathan Waller JLPT 단어 목록 (CC BY), 한국어 뜻 작성·검토\n" +
                "• 한국 훈음: 한국어문회 배정한자 대표 훈음, 미수록 한자는 libhangul (BSD)\n" +
                "• 획 그림(붓 외곽선): AnimCJK (Arphic PL KaitiM 파생, Arphic Public License)\n" +
                "• 부품·소리·획순: KanjiVG (Ulrich Apel, CC BY-SA 3.0)\n" +
                "• 부수 분류·이름: KANJIDIC2, Kanjium (CC BY-SA 4.0)\n" +
                "• 상용한자 쉬운 뜻·유래·외우기: 자체 작성·검토 (참고: makemeahanzi, LGPL)\n" +
                "• 닮은 한자: 획 그림 겹침 비교 계산 + 선별 보완\n" +
                "• 신자체·정자 대응: Unicode Unihan (Unicode License)",
            12.5f, pal.sub).apply {
            setLineSpacing(0f, 1.3f)
            setPadding(0, dp(12f), 0, 0)
        })

        val scroll = ScrollView(this).apply { addView(box) }
        AlertDialog.Builder(this)
            .setTitle("사용법 및 안내")
            .setView(scroll)
            .setPositiveButton("닫기", null)
            .show()
    }

    // ---- The Anki app (AnkiTarget) ----

    private lateinit var targetState: TextView
    private lateinit var targetNote: TextView
    private lateinit var targetButton: Button
    private lateinit var nativeNote: TextView
    private lateinit var ankiButtons: LinearLayout

    /** The chosen app and why; the template tools only for AnkiDroid (myAmgi runs the card code itself). */
    private fun showTarget() {
        val choice = AnkiBridge.choice(this)
        val t = choice.target
        targetState.setTextColor(if (t == null) pal.accent else pal.text)
        targetState.text = if (t == null) choice.note else "연결할 앱: ${t.label}"
        targetNote.text = if (t == null) "" else choice.note
        targetNote.visibility = if (targetNote.text.isEmpty()) View.GONE else View.VISIBLE
        targetButton.visibility = if (AnkiBridge.installedPackages(this).size > 1) View.VISIBLE else View.GONE
        val native = t?.needsTemplateCode == false
        nativeNote.visibility = if (native) View.VISIBLE else View.GONE
        ankiButtons.visibility = if (native) View.GONE else View.VISIBLE
        apkgButton.visibility = if (native) View.GONE else View.VISIBLE
        if (native) setAnkiState("")
    }

    /** The installed Anki apps to pick from; the choice is kept (Prefs.ankiTarget). */
    private fun chooseTarget() {
        val installed = AnkiBridge.installedPackages(this)
        val options = AnkiTarget.entries.filter { it.pkg in installed }
        if (options.isEmpty()) return
        val current = AnkiBridge.target(this)
        val names = options.map { if (it.isMyAmgi) "${it.label} (카드 코드 자동)" else it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("연결할 Anki 앱")
            .setSingleChoiceItems(names, options.indexOf(current)) { d, i ->
                Prefs.setAnkiTarget(this, options[i].pkg)
                d.dismiss()
                setAnkiState("")
                showTarget()
                showVocab()
                if (!patchingApkg) refreshAnki()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    // ---- Anki card templates (AnkiTemplate, AnkiBridge), AnkiDroid only ----

    private lateinit var ankiState: TextView

    private fun setAnkiState(msg: String) {
        ankiState.text = msg
        ankiState.visibility = if (msg.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun ankiCode(): String = AnkiSync.code(this)

    /** AnkiDroid work off the main thread; [done] runs on it, unless this screen has closed. */
    private fun <T> inBackground(work: () -> T, done: (Result<T>) -> Unit) {
        Thread {
            val r = runCatching(work)
            runOnUiThread { if (!isFinishing && !isDestroyed) done(r) }
        }.start()
    }

    private fun ankiError(e: Throwable): String {
        val app = AnkiBridge.label(this)
        val ask = if (AnkiBridge.target(this)?.isMyAmgi == true) "단어장의 '설정 확인'" else "'Anki 카드에 적용'"
        return when (e) {
            is SecurityException -> "${app} 접근 권한이 없습니다. ${ask}을 눌러 권한을 허용해 주세요."
            else -> "${app}에 연결할 수 없습니다. ${app}를 한 번 연 뒤 다시 시도해 주세요."
        }
    }

    /** [missed]: changes that are not in Anki after writing, with what AnkiDroid answered for them. */
    private fun describe(
        plan: AnkiTemplate.Plan,
        types: List<AnkiTemplate.NoteType>,
        missed: List<Pair<AnkiTemplate.Change, List<String>>> = emptyList(),
    ): String {
        fun name(c: AnkiTemplate.Change) = types.first { it.id == c.typeId }.name
        val failedNames = missed.map { name(it.first) }.distinct()
        val names = plan.changes.map { name(it) }.distinct() - failedNames.toSet()
        val parts = mutableListOf<String>()
        if (names.isNotEmpty()) parts += "${names.joinToString(", ")}에 적용했습니다. 보던 덱은 나갔다가 다시 들어가면 반영됩니다."
        if (missed.isNotEmpty()) {
            parts += "적용 실패(AnkiDroid 거부): " + missed.joinToString(" / ") { (c, notes) ->
                val t = types.first { it.id == c.typeId }
                val card = t.templates.firstOrNull { it.ord == c.ord }?.name ?: "카드 ${c.ord + 1}"
                val detail = notes.ifEmpty {
                    listOfNotNull(c.front?.let { "앞면 ${"%,d".format(it.length)}자" }, c.back?.let { "뒷면 ${"%,d".format(it.length)}자" }) +
                        "저장 후 확인되지 않음"
                }
                "${t.name} › $card (${detail.joinToString(", ")})"
            }
        }
        if (plan.manual.isNotEmpty()) {
            parts += "큰 템플릿 자동 적용 필요 (${plan.manual.size}개): " +
                plan.manual.joinToString(" / ") { "${it.typeName} › ${it.cardName}" } + "\n" +
                "아래 '큰 템플릿 파일로 자동 적용 (.apkg)'을 누르면 직접 붙여 넣기 없이 1초 만에 적용됩니다."
        }
        if (plan.broken.isNotEmpty()) parts += "${plan.broken.joinToString(", ")}: 기존 코드가 손상되어 건너뛰었습니다. " +
            "템플릿에서 기존 코드를 지운 뒤 다시 적용해 주세요."
        return parts.joinToString("\n")
    }

    /**
     * The code into the CSS of the note types in [keep], then the loader into their templates (only where the CSS
     * took the code), and a report of what did not go in.
     */
    private fun applyPlan(plan: AnkiTemplate.Plan, types: List<AnkiTemplate.NoteType>, keep: Set<Long>, quiet: Boolean) {
        highlightApkgButton(plan.manual.isNotEmpty())
        if (plan.changes.isEmpty() && keep.isEmpty()) {
            if (plan.broken.isNotEmpty()) setAnkiState(describe(plan, types))
            else if (!quiet) setAnkiState("변경할 항목이 없습니다.")
            return
        }
        val code = ankiCode()
        inBackground({
            val css = AnkiSync.writeCss(this, types, keep)
            val safe = AnkiSync.safe(plan, css)
            val anki = AnkiBridge.open(this)
            val notes = safe.changes.associateWith { AnkiBridge.write(anki, it) }
            // AnkiDroid can drop a write without an error: read the templates again and report what is really there.
            val missed = AnkiTemplate.missed(safe, AnkiBridge.noteTypes(anki), code).map { it to notes.getValue(it) }
            Triple(safe, missed, css.failed)
        }) { r ->
            if (patchingApkg) return@inBackground
            r.onSuccess { (safe, missed, cssFailed) ->
                highlightApkgButton(safe.manual.isNotEmpty())
                var msg = describe(safe, types, missed)
                if (cssFailed.isNotEmpty()) msg = (msg + "\n").trimStart() + "스타일(CSS) 적용 실패: " +
                    cssFailed.entries.joinToString(" / ") { (id, why) -> (types.firstOrNull { it.id == id }?.name ?: "$id") + " ($why)" }
                if (msg.isEmpty() && !quiet) msg = "선택한 노트 유형에 이미 최신 코드가 적용되어 있습니다."
                if (msg.isEmpty() && quiet) {
                    val on = types.filter { it.id in keep }
                    msg = if (on.isNotEmpty()) "적용됨: " + on.joinToString(", ") { it.name } else ""
                }
                if (msg.isNotEmpty() || !quiet) setAnkiState(msg)
                if (safe.changes.isNotEmpty() && missed.size < safe.changes.size) {
                    Toast.makeText(this, msg.substringBefore("\n").substringBefore(" 보던"), Toast.LENGTH_SHORT).show()
                }
            }.onFailure { setAnkiState(ankiError(it)) }
        }
    }

    /** On every visit: note types that already have the code get the current one, silently when nothing changes. */
    private fun refreshAnki() {
        if (AnkiBridge.target(this)?.needsTemplateCode != true || !AnkiBridge.granted(this)) return
        val code = ankiCode()
        inBackground({ AnkiBridge.noteTypes(AnkiBridge.open(this)) }) { r ->
            if (patchingApkg) return@inBackground
            r.onSuccess { types ->
                // Note types that had the code before this build count as chosen too.
                val keep = AnkiTemplate.keep(types, Prefs.ankiTapTypes(this))
                Prefs.setAnkiTapTypes(this, keep)
                val on = types.filter { it.id in keep }
                if (on.isNotEmpty() && ankiState.text.isEmpty()) setAnkiState("적용됨: " + on.joinToString(", ") { it.name })
                applyPlan(AnkiTemplate.plan(types, keep, code), types, keep, quiet = true)
            }
        }
    }

    private fun highlightApkgButton(highlight: Boolean) {
        apkgButton.setTextColor(if (highlight) pal.card else pal.text)
        apkgButton.background = rounded(if (highlight) pal.accent else pal.button, dp(12f))
    }

    private fun handleSendApkg(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val uri: Uri? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        } ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
        if (uri != null) {
            // Consume once so configuration changes do not re-trigger patching.
            intent.action = null
            patchApkg(uri)
        }
    }

    private fun showApkgGuide() {
        val builder = AlertDialog.Builder(this)
            .setTitle("큰 템플릿 파일로 자동 적용 (.apkg)")
            .setMessage(
                "JLPT MAX덱처럼 용량이 큰 카드는 .apkg 파일로 1초 만에 안전하게 적용할 수 있으며 기존 카드와 복습 기록은 건드리지 않습니다.\n\n" +
                    "1. AnkiDroid 우측 상단 ⋮ → 내보내기 → 'Anki 덱 패키지(.apkg)', '미디어 포함' 끄기 → 내보내기\n" +
                    "2. 완료 창에서 '공유 → 한자 바로보기'를 누르거나, 가지고 있는 .apkg 파일을 아래 버튼으로 선택하세요.\n" +
                    "3. AnkiDroid 가져오기 화면에서 '노트 유형 업데이트 → 항상', '노트 유형 병합 → 켜기' 확인 후 '가져오기'를 누르세요."
            )
            .setPositiveButton("파일 선택 (.apkg)") { _, _ -> pickApkgFile() }
            .setNegativeButton("닫기", null)
        if (AnkiBridge.installed(this, AnkiTarget.ANKIDROID)) {
            builder.setNeutralButton("AnkiDroid 열기") { _, _ ->
                packageManager.getLaunchIntentForPackage(AnkiBridge.ANKIDROID_PACKAGE)?.let { startActivity(it) }
            }
        }
        builder.show()
    }

    @Suppress("DEPRECATION")
    private fun pickApkgFile() {
        val pick = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        try {
            startActivityForResult(pick, PICK_APKG)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "파일 선택 앱을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun patchApkg(uri: Uri) {
        if (patchingApkg) return
        patchingApkg = true
        setAnkiState("덱 파일(.apkg)을 패치하는 중…")
        val app = applicationContext
        val loader = AnkiCode.loader(this)
        val cssBlock = AnkiCode.cssBlock(this)
        inBackground({
            val chosen = Prefs.ankiTapTypes(app)
            // The .apkg goes back into AnkiDroid (its import screen), whichever app is chosen for the 단어장.
            val ad = AnkiTarget.ANKIDROID
            val knownIds = if (AnkiBridge.installed(app, ad) && AnkiBridge.granted(app, ad)) {
                runCatching { AnkiBridge.noteTypeNames(AnkiBridge.open(app, ad)).map { it.first }.toSet() }.getOrDefault(emptySet())
            } else {
                emptySet()
            }
            val result = contentResolver.openInputStream(uri)?.use { src ->
                ApkgPatcher.patchStream(src, ApkgProvider.file(app), loader, cssBlock, chosen, knownIds)
            } ?: throw IllegalStateException("선택한 파일을 읽을 수 없습니다.")
            if (result.patched.isNotEmpty()) {
                Prefs.setAnkiTapTypes(app, chosen + result.patched.map { it.id })
            }
            result
        }) { r ->
            patchingApkg = false
            r.onSuccess { res ->
                if (res.patched.isEmpty()) {
                    setAnkiState("이미 최신 코드가 적용되어 있습니다: ${res.alreadyCurrent.joinToString(", ")}")
                    highlightApkgButton(false)
                    return@onSuccess
                }
                val names = res.patched.joinToString(", ") { it.name }
                val cards = res.patched.sumOf { it.cardCount }
                val warn = if (res.unmatched.isNotEmpty()) {
                    "\n\n※ 주의: 이 파일의 노트 유형(${res.unmatched.joinToString(", ")}) ID가 현재 AnkiDroid와 다릅니다. " +
                        "가져오기 후에도 적용되지 않으면 AnkiDroid에서 '⋮ → 내보내기(미디어 끄기)'로 방금 내보낸 .apkg 파일을 사용해 주세요."
                } else ""
                setAnkiState("패치 완료 (${cards}개 카드: $names)\nAnkiDroid에서 '노트 유형 업데이트: 항상'으로 가져오면 적용됩니다.")
                AlertDialog.Builder(this)
                    .setTitle("패치 완료 (${cards}개 카드)")
                    .setMessage(
                        "대상: $names\n\n" +
                            "AnkiDroid 가져오기 화면이 열리면 아래 옵션을 확인한 뒤 '가져오기'를 눌러 주세요.\n" +
                            "• 노트 유형 업데이트: 항상\n" +
                            "• 노트 유형 병합: 켜기\n" +
                            "(기존 카드·복습 기록은 바뀌지 않으며, '0개 추가됨'으로 표시되는 것이 정상입니다.)$warn"
                    )
                    .setPositiveButton("AnkiDroid로 가져오기") { _, _ -> openPatchedInAnki() }
                    .setNegativeButton("취소", null)
                    .show()
            }.onFailure { e ->
                val msg = if (e is IllegalStateException && e.message?.any { it in '가'..'힣' } == true) e.message!!
                else "덱 파일(.apkg)을 처리하지 못했습니다: ${e.message ?: e.javaClass.simpleName}"
                setAnkiState(msg)
            }
        }
    }

    private fun openPatchedInAnki() {
        if (!AnkiBridge.installed(this, AnkiTarget.ANKIDROID)) {
            Toast.makeText(this, "AnkiDroid가 설치되어 있지 않습니다.", Toast.LENGTH_LONG).show()
            return
        }
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(ApkgProvider.URI, ApkgProvider.MIME)
            setPackage(AnkiBridge.ANKIDROID_PACKAGE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri(ApkgProvider.FILE_NAME, ApkgProvider.URI)
        }
        try {
            startActivity(view)
            Toast.makeText(this, "AnkiDroid에서 '가져오기'를 눌러 적용을 마무리하세요.", Toast.LENGTH_LONG).show()
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "AnkiDroid에서 덱 파일을 열 수 없습니다.", Toast.LENGTH_LONG).show()
        }
    }

    private fun chooseNoteTypes() {
        val target = AnkiBridge.target(this)
        if (target == null) {
            setAnkiState("AnkiDroid가 설치되어 있지 않습니다.")
            return
        }
        if (!target.needsTemplateCode) {
            showTarget()
            return
        }
        if (!AnkiBridge.granted(this)) {
            requestPermissions(arrayOf(AnkiBridge.permission(this)), ANKI_PERMISSION)
            return
        }
        setAnkiState("노트 유형을 불러오는 중…")
        val code = ankiCode()
        inBackground({ AnkiBridge.noteTypes(AnkiBridge.open(this)) }) { r ->
            r.onFailure { setAnkiState(ankiError(it)) }
            r.onSuccess { types ->
                setAnkiState("")
                if (types.isEmpty()) {
                    setAnkiState("AnkiDroid에 노트 유형이 없습니다.")
                    return@onSuccess
                }
                val chosen = Prefs.ankiTapTypes(this)
                val checked = BooleanArray(types.size) { types[it].installed || types[it].id in chosen }
                val names = types.map { t ->
                    when {
                        t.current(code) && t.installed -> "${t.name}  (적용됨)"
                        t.installed -> "${t.name}  (업데이트 필요)"
                        t.id in chosen -> "${t.name}  (다시 적용 필요)"
                        else -> t.name
                    }
                }.toTypedArray()
                AlertDialog.Builder(this)
                    .setTitle("적용할 노트 유형 선택")
                    .setMultiChoiceItems(names, checked) { _, i, on -> checked[i] = on }
                    .setPositiveButton("적용") { _, _ ->
                        val wanted = types.filterIndexed { i, _ -> checked[i] }.map { it.id }.toSet()
                        Prefs.setAnkiTapTypes(this, wanted)
                        applyPlan(AnkiTemplate.plan(types, wanted, code), types, wanted, quiet = false)
                    }
                    .setNegativeButton("취소", null)
                    .show()
            }
        }
    }

    private lateinit var vocabState: TextView

    private fun showVocab() {
        val v = Prefs.vocab(this)
        if (v == null) {
            vocabState.setTextColor(pal.sub)
            vocabState.text = "설정되지 않음"
        } else {
            vocabState.setTextColor(pal.accent)
            vocabState.text = "연결됨(${AnkiBridge.label(this)}): ${v.deck.replace("::", " › ")} (${v.typeName})"
        }
    }

    private fun setUpVocab() {
        if (!AnkiBridge.installed(this)) {
            vocabState.setTextColor(pal.accent)
            vocabState.text = "AnkiDroid나 myAmgi가 설치되어 있지 않습니다."
            return
        }
        if (!AnkiBridge.granted(this)) {
            requestPermissions(arrayOf(AnkiBridge.permission(this)), VOCAB_PERMISSION)
            return
        }
        vocabState.setTextColor(pal.sub)
        vocabState.text = "${AnkiBridge.label(this)}에서 단어장 덱을 찾는 중…"
        val app = applicationContext
        inBackground({ VocabAdder.setUp(app) }) { r ->
            r.onSuccess { showVocab() }
            r.onFailure { e ->
                vocabState.setTextColor(pal.accent)
                vocabState.text = if (e is IllegalStateException && e.message?.any { it in '가'..'힣' } == true) e.message
                else ankiError(e)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == VOCAB_PERMISSION) {
            if (AnkiBridge.granted(this)) setUpVocab()
            else {
                vocabState.setTextColor(pal.accent)
                vocabState.text = ankiPermissionDenied()
            }
            return
        }
        if (requestCode != ANKI_PERMISSION) return
        if (AnkiBridge.granted(this)) chooseNoteTypes()
        else setAnkiState(ankiPermissionDenied())
    }

    private lateinit var updateState: TextView
    private lateinit var updateButton: Button
    private var latest: UpdateInfo.Latest? = null
    private var installing = false

    /** Compact 1-line update bar: "빌드 24 · 최신 버전" on the left, pill button on the right. */
    private fun updateRow(): View {
        updateState = label("빌드 ${AppUpdater.current}", 13.5f, pal.sub)
        updateButton = Button(this).apply {
            text = "업데이트 확인"
            isAllCaps = false
            textSize = 13f
            maxLines = 1
            setTextColor(pal.text)
            background = rounded(pal.button, dp(10f))
            stateListAnimator = null
            gravity = Gravity.CENTER
            minHeight = dp(38f)
            minimumHeight = dp(38f)
            setPadding(dp(14f), 0, dp(14f), 0)
            setOnClickListener { onUpdateButton() }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10f), 0, 0)
            addView(updateState, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = dp(8f)
            })
            addView(updateButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        checkUpdate(manual = false)
        return row
    }

    private fun checkUpdate(manual: Boolean) {
        if (manual) updateState.text = "빌드 ${AppUpdater.current} · 확인 중…"
        AppUpdater.check { found ->
            if (isFinishing || isDestroyed || installing) return@check
            latest = found?.takeIf { UpdateInfo.isNewer(it, AppUpdater.current) }
            val l = latest
            when {
                found == null -> if (manual) updateState.text = "빌드 ${AppUpdater.current} · 확인 실패"
                l == null -> updateState.text = "빌드 ${AppUpdater.current} · 최신 버전"
                else -> {
                    updateState.text = "빌드 ${AppUpdater.current} · 새 버전(빌드 ${l.build})"
                    updateButton.text = installText(l)
                    updateButton.setTextColor(pal.card)
                    updateButton.background = rounded(pal.accent, dp(10f))
                }
            }
        }
    }

    /** "빌드 256 설치 (변경분 0.1MB)" when only the change is downloaded, else the whole APK's size. */
    private fun installText(l: UpdateInfo.Latest): String {
        val patch = UpdateInfo.patchFor(l, AppUpdater.current)
        val size = when {
            patch != null -> " (변경분 ${UpdateInfo.sizeText(patch.size)})"
            l.size > 0 -> " (${UpdateInfo.sizeText(l.size)})"
            else -> ""
        }
        return "빌드 ${l.build} 설치$size"
    }

    private fun onUpdateButton() {
        val l = latest
        if (l == null) {
            checkUpdate(manual = true)
            return
        }
        if (installing) return
        val started = AppUpdater.install(this, l,
            progress = { p -> updateButton.text = UpdateInfo.progressText(p) },
            failed = { msg ->
                installing = false
                updateButton.text = installText(l)
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            })
        if (!started) {
            Toast.makeText(this, "'알 수 없는 앱 설치' 권한을 허용한 뒤 다시 시도해 주세요.", Toast.LENGTH_LONG).show()
            return
        }
        installing = true
        updateButton.text = UpdateInfo.progressText(-1)
    }

    private fun showKey() {
        val key = Prefs.apiKey(this)
        if (key.isEmpty()) {
            keyState.setTextColor(pal.sub)
            keyState.text = "저장된 키 없음"
        } else {
            keyState.setTextColor(pal.accent)
            keyState.text = "저장됨: " + Prefs.masked(key)
        }
    }

    private fun divider(): View = View(this).apply { setBackgroundColor(pal.line) }

    private fun section(col: LinearLayout, title: String) {
        col.addView(divider(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
            topMargin = dp(20f)
            bottomMargin = dp(16f)
        })
        col.addView(label(title, 16f, pal.accent, bold = true).apply { setPadding(0, 0, 0, dp(6f)) })
    }

    private fun body(text: String): TextView = label(text, 14f, pal.text).apply { setLineSpacing(0f, 1.3f) }

    private fun button(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 14f
        maxLines = 1
        setPadding(dp(10f), 0, dp(10f), 0)
        setAutoSizeTextTypeUniformWithConfiguration(11, 14, 1, TypedValue.COMPLEX_UNIT_SP)
        setTextColor(pal.text)
        background = rounded(pal.button, dp(12f))
        stateListAnimator = null
        gravity = Gravity.CENTER
        minHeight = dp(48f)
        setOnClickListener { onClick() }
    }
}
