package com.ggumtak.hanja

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.view.View
import com.ggumtak.hanja.update.AppUpdater
import com.ggumtak.hanja.update.UpdateInfo

/** The app icon: how to use the selection menu entry, the Claude API key, a test card and the data sources. */
class MainActivity : Activity() {
    private companion object {
        const val ANKI_PERMISSION = 7
        const val VOCAB_PERMISSION = 8
    }

    private lateinit var pal: Palette
    private lateinit var keyState: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pal = Palette(this)
        val pad = dp(20f)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(28f), pad, dp(28f))
        }
        col.addView(label("한자 바로보기", 26f, pal.text, bold = true))
        col.addView(updateRow())
        section(col, "쓰는 법")
        col.addView(body(
            "1. 아무 앱에서 한자를 꾹 눌러 선택합니다.\n" +
                "2. 뜨는 메뉴에서 '한자'를 누릅니다. 메뉴에 없으면 ⋮ 안에 있습니다.\n" +
                "   ⋮에도 없으면 ⋮ → '앱 관리'에서 '한자 바로보기'를 켜거나, '공유' → '한자'를 누릅니다.\n" +
                "3. 화면 아래 카드에 한자별 한국 훈음, 음독·훈독(한글 발음), 획수가 나옵니다. 상용한자의 음독·훈독은 " +
                "자주 만나는 순서로 한 줄씩 나오고, 줄마다 그 읽기를 쓰는 흔한 단어와 뜻이 붙습니다(セイ → 学生 학생). " +
                "훈독 뒤의 뜻은 그 읽기 자체의 뜻입니다(た(べる) 먹다). 덜 쓰는 읽기는 '읽기 더 보기'를 누르면 나오고, " +
                "'표 밖'은 상용한자표에는 없지만 흔한 단어에 쓰이는 읽기, '드묾'은 사전에만 있는 읽기입니다. " +
                "한글 발음에서 '-'는 앞 소리를 길게 끈다는 표시입니다(こう 코-, せい 세-). '닮은 한자'는 모양이 비슷해 " +
                "헷갈리기 쉬운 글자이고, 누르면 두 글자를 나란히 그려 뜻·읽기와 함께 비교합니다. 큰 한자는 획으로 그려 " +
                "부수를 붉은색, 소리 부분을 파란색으로 칠하고, 부수 이름(일본식·한국식), 구성, 같은 소리 부분을 가진 한자, " +
                "상용한자는 유래(글자가 이런 모양이 된 까닭)와 외우기 이야기도 보여 줍니다. 부수 줄을 누르면 같은 부수를 " +
                "같은 뜻으로 쓰는 쉬운 한자가 나옵니다(氵: 池 海 泳). 구성에서 소리 부분 뒤의 가타카나는 그 부분이 글자에 준 " +
                "소리이고, 지금의 음독과 다르면 'ジン→カン'처럼 지금 음을 이어 보여 줍니다(勘의 甚). 유래 줄은 구성 줄이 " +
                "이미 말한 것은 되풀이하지 않습니다. " +
                "큰 한자를 누르면 큰 그림에서 " +
                "획순대로 한 획씩 번호와 함께 써집니다(폰에서는 아래에 열리고 다시 누르면 닫힘, 태블릿처럼 넓은 화면에서는 " +
                "오른쪽에 늘 보임). 한자 줄의 다른 곳을 누르면 한자사전이 열립니다.\n" +
                "4. '일본어사전'·'한자사전'은 네이버 사전을 열고, 'Claude 설명'은 카드에 없는 것(이 문장 속 뜻, 한국어 한자어와의 연결, " +
                "외우는 법, 읽기 주의점, 자주 쓰는 말)을 짧은 항목으로 알려 줍니다. " +
                "'질문'을 누르면 고른 글자가 입력창에 들어가고, 뒤에 질문을 이어 써서 보내면 Claude가 답합니다.",
        ))
        col.addView(button("시험해 보기 (母は電気をつけて)") {
            startActivity(Intent(this, LookupActivity::class.java)
                .putExtra(Intent.EXTRA_PROCESS_TEXT, "母は電気をつけて"))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(12f)
        })

        col.addView(CheckBox(this).apply {
            text = "전에 찾아본 말은 읽기와 뜻을 가리고 먼저 떠올리게 하기. 가림막을 누르면 바로 보입니다 " +
                "(처음 찾는 말과, 10분 안에 다시 연 말은 가리지 않습니다)"
            textSize = 14f
            setTextColor(pal.text)
            isChecked = Prefs.recall(this@MainActivity)
            setOnCheckedChangeListener { _, on -> Prefs.setRecall(this@MainActivity, on) }
            setPadding(dp(4f), dp(8f), 0, dp(8f))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8f)
        })

        section(col, "Anki에서 한 번 탭으로 보기")
        col.addView(body(
            "Anki 카드의 한자·가타카나를 탭하면 바로 카드가 뜨고, 꾹 눌러 드래그한 뒤 아래에 뜨는 '한자' 버튼을 눌러도 됩니다.\n" +
                "1. 아래 'Anki 카드에 적용'을 누르고, 처음 한 번 AnkiDroid 접근을 허용합니다.\n" +
                "2. 넣을 노트 유형(문법, 단어 등)을 고르면 앞면·뒷면에 앱이 직접 넣습니다. 예전 코드는 앱이 지웁니다.\n" +
                "3. 그 뒤로는 앱을 업데이트하고 이 화면을 열면 Anki 카드의 코드도 자동으로 새 버전으로 바뀝니다. " +
                "덱 업데이트로 템플릿이 바뀌어 코드가 사라져도 다시 넣습니다. 빼고 싶은 노트 유형은 'Anki 카드에 적용'에서 체크를 해제하세요.\n" +
                "탭했을 때 '보안 차단' 안내가 뜨면 Anki 설정 → 고급 설정 → '카드에서 다른 앱으로 이동 허용'을 켭니다. " +
                "이 설정은 내 컬렉션의 모든 카드에 적용되므로, 출처를 모르는 공유 덱을 받을 때는 주의하세요.",
        ))
        ankiState = body("").apply { setTextColor(pal.accent) }
        col.addView(ankiState)
        col.addView(button("Anki 카드에 적용") { chooseNoteTypes() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(12f)
            })
        col.addView(button("코드만 복사 (직접 붙여 넣을 때)") {
            val cm = getSystemService(ClipboardManager::class.java)
            cm.setPrimaryClip(ClipData.newPlainText("한자 바로보기 Anki 코드", ankiCode()))
            Toast.makeText(this, "복사했습니다. Anki 템플릿 맨 아래에 붙여 넣으세요", Toast.LENGTH_LONG).show()
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8f)
        })

        section(col, "단어장 (Anki의 자주 까먹는 단어 덱)")
        col.addView(body(
            "한자 카드의 '단어장'을 누르면 그 단어가 Anki의 '자주 까먹는 단어' 덱에 들어갑니다. " +
                "카드 형식은 'JLPT MAX덱 어휘' 노트 유형의 기존 카드를 Claude(Haiku)가 그대로 따라 채웁니다" +
                "(단어 하나에 약 10~30원, 추정). 일본어→한국어 카드만 남기고, 한국어→일본어·음성 카드는 일시 중지합니다.\n" +
                "처음 한 번 아래 '설정 확인'을 눌러 노트 유형과 덱을 찾게 합니다.",
        ))
        vocabState = body("").apply { setTextColor(pal.accent) }
        col.addView(vocabState)
        showVocab()
        col.addView(button("설정 확인") { setUpVocab() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(12f)
            })
        col.addView(CheckBox(this).apply {
            text = "같은 단어를 ${VocabNote.AUTO_AFTER}번째 찾아보면 묻지 않고 넣기 (10분 안에 다시 연 것은 한 번으로 셉니다)"
            textSize = 14f
            setTextColor(pal.text)
            isChecked = Prefs.vocabAuto(this@MainActivity)
            setOnCheckedChangeListener { _, on -> Prefs.setVocabAuto(this@MainActivity, on) }
            setPadding(dp(4f), dp(8f), 0, dp(8f))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(6f)
        })

        section(col, "Claude API 키")
        col.addView(body(
            "'Claude 설명'에 쓰입니다. 키는 이 휴대폰의 이 앱 안에만 저장되고, 설명을 요청할 때만 Anthropic으로 보내집니다. " +
                "요금은 요청마다 API 계정에 청구됩니다. 읽기 판정은 Haiku 5.5(생각 없이 바로), 질문·설명·예문은 아래에서 고른 모델을 씁니다.",
        ))
        keyState = body("").apply { setTextColor(pal.accent) }
        col.addView(keyState)
        val input = EditText(this).apply {
            hint = "sk-ant-로 시작하는 키"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
            setTextColor(pal.text)
            setHintTextColor(pal.sub)
        }
        col.addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8f), 0, 0)
        }
        row.addView(button("저장") {
            val v = input.text.toString().trim()
            if (!v.startsWith("sk-ant-")) {
                Toast.makeText(this, "sk-ant-로 시작하는 키를 넣어 주세요", Toast.LENGTH_SHORT).show()
                return@button
            }
            Prefs.setApiKey(this, v)
            input.setText("")
            showKey()
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(button("지우기") {
            Prefs.setApiKey(this, "")
            showKey()
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(8f) })
        col.addView(row)
        showKey()
        col.addView(label("질문·Claude 설명·예문에 쓸 모델", 15f, pal.text, bold = true).apply { setPadding(0, dp(14f), 0, dp(4f)) })
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
        col.addView(label("생각하는 정도", 15f, pal.text, bold = true).apply { setPadding(0, dp(10f), 0, dp(4f)) })
        val effort = Prefs.chatEffort(this)
        col.addView(RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            for ((i, e) in Prefs.EFFORTS.withIndex()) {
                addView(RadioButton(this@MainActivity).apply {
                    id = 2000 + i
                    text = e.second.substringBefore(" —")
                    textSize = 14f
                    setTextColor(pal.text)
                    isChecked = e.first == effort
                })
            }
            setOnCheckedChangeListener { _, checkedId ->
                Prefs.EFFORTS.getOrNull(checkedId - 2000)?.let { Prefs.setChatEffort(this@MainActivity, it.first) }
            }
        })
        col.addView(label("낮음: 생각하는 단계 없이 바로 답을 쓰기 시작해 가장 빠릅니다(Opus는 생각을 끌 수 없어 짧게만 생각). " +
            "중간·높음: 먼저 생각한 뒤 답해 더 꼼꼼하지만 첫 글자가 몇 초 늦어지고 요금이 조금 늘어납니다. " +
            "가장 빠르게 쓰려면 모델은 Haiku, 생각은 낮음을 고르세요.", 13f, pal.sub))
        col.addView(CheckBox(this).apply {
            text = "카드를 열 때 Claude가 문장을 읽고 그 자리의 발음으로 바꿔 보여 주기 (Anki에서 탭한 단어, 두 글자 이상 선택). " +
                "같은 문장은 저장해 두어 다시 묻지 않습니다. 한 번에 약 0.1원(추정)."
            textSize = 14f
            setTextColor(pal.text)
            isChecked = Prefs.autoContext(this@MainActivity)
            setOnCheckedChangeListener { _, on -> Prefs.setAutoContext(this@MainActivity, on) }
            setPadding(dp(4f), dp(8f), 0, dp(8f))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(10f)
        })

        section(col, "자료 출처")
        col.addView(body(
            "일본어 음독·훈독·획수·영어 뜻: KANJIDIC2 (EDRDG, CC BY-SA 4.0)\n" +
                "단어 읽기: JMdict (EDRDG, CC BY-SA 4.0)\n" +
                "읽기 순서와 읽기별 단어: 상용한자표(2010)의 음훈, JMdict, Jonathan Waller의 JLPT 단어 목록 (CC BY). " +
                "단어의 한국어 뜻은 Claude가 쓰고 따로 검토\n" +
                "상용한자 2,136자의 쉬운 뜻: KANJIDIC2와 옛 훈음을 바탕으로 Claude가 쓰고 따로 검토\n" +
                "한국 훈음: libhangul 한자 사전 (BSD)\n" +
                "획 그림(붓 외곽선): AnimCJK (Arphic PL KaitiM 글꼴에서 파생, Arphic Public License)\n" +
                "부품·소리 부분·획 순서: KanjiVG (Ulrich Apel, CC BY-SA 3.0)\n" +
                "닮은 한자: 위 획 그림을 서로 겹쳐 비교해 계산하고, 손으로 고른 묶음을 보탬\n" +
                "부수 분류: KANJIDIC2 (일본 사전 기준), 부수 이름: Kanjium (CC BY-SA 4.0)을 참고해 정리\n" +
                "상용한자 유래·외우기: Claude가 쓰고 따로 검토 (참고: makemeahanzi, LGPL)\n" +
                "신자체·정자 대응: Unicode Unihan (Unicode License)",
        ).apply { textSize = 13f })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(pal.card)
            // In a wide window (a tablet, not in split screen) the text would run across the whole screen: keep it
            // to a readable column in the middle. The window's width, not the device's: this screen is recreated
            // when the window changes size.
            val maxWidth = dp(720f)
            val narrow = resources.displayMetrics.widthPixels > maxWidth + dp(48f)
            addView(col, android.widget.FrameLayout.LayoutParams(
                if (narrow) maxWidth else ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL))
        })
    }

    override fun onResume() {
        super.onResume()
        refreshAnki()
    }

    // ---- Anki card templates (AnkiTemplate, AnkiBridge) ----

    private lateinit var ankiState: TextView

    private fun ankiCode(): String = AnkiSync.code(this)

    /** AnkiDroid work off the main thread; [done] runs on it, unless this screen has closed. */
    private fun <T> inBackground(work: () -> T, done: (Result<T>) -> Unit) {
        Thread {
            val r = runCatching(work)
            runOnUiThread { if (!isFinishing && !isDestroyed) done(r) }
        }.start()
    }

    private fun ankiError(e: Throwable): String = when (e) {
        is SecurityException -> "AnkiDroid 접근이 허용되지 않았습니다. 'Anki 카드에 적용'을 다시 눌러 허용해 주세요."
        else -> "AnkiDroid에 연결하지 못했습니다. AnkiDroid를 한 번 열어 컬렉션을 불러온 뒤 다시 눌러 주세요."
    }

    private fun describe(plan: AnkiTemplate.Plan, types: List<AnkiTemplate.NoteType>): String {
        val names = plan.changes.map { c -> types.first { it.id == c.typeId }.name }.distinct()
        val parts = mutableListOf<String>()
        if (names.isNotEmpty()) parts += "Anki 노트 유형 ${names.joinToString("·")}의 코드를 바꿨습니다. 보던 덱은 나갔다가 다시 들어가면 적용됩니다."
        if (plan.broken.isNotEmpty()) parts += "${plan.broken.joinToString("·")}: 코드 일부가 잘려 있거나 표시 주석이 없어 건드리지 않았습니다. " +
            "그 유형의 템플릿에서 한자 바로보기 코드를 지운 뒤 다시 적용해 주세요."
        return parts.joinToString("\n")
    }

    private fun applyPlan(plan: AnkiTemplate.Plan, types: List<AnkiTemplate.NoteType>, quiet: Boolean) {
        if (plan.changes.isEmpty()) {
            if (plan.broken.isNotEmpty()) ankiState.text = describe(plan, types)
            else if (!quiet) ankiState.text = "바꿀 것이 없습니다. 고른 노트 유형은 이미 최신 코드입니다."
            return
        }
        inBackground({ plan.changes.forEach { AnkiBridge.write(contentResolver, it) } }) { r ->
            r.onSuccess {
                val msg = describe(plan, types)
                ankiState.text = msg
                Toast.makeText(this, msg.substringBefore(" 보던"), Toast.LENGTH_SHORT).show()
            }.onFailure { ankiState.text = ankiError(it) }
        }
    }

    /** On every visit: note types that already have the code get the current one, silently when nothing changes. */
    private fun refreshAnki() {
        if (!AnkiBridge.installed(this) || !AnkiBridge.granted(this)) return
        val code = ankiCode()
        inBackground({ AnkiBridge.noteTypes(contentResolver) }) { r ->
            r.onSuccess { types ->
                // Note types that had the code before this build count as chosen too.
                val keep = AnkiTemplate.keep(types, Prefs.ankiTapTypes(this))
                Prefs.setAnkiTapTypes(this, keep)
                val on = types.filter { it.id in keep }
                if (on.isNotEmpty() && ankiState.text.isEmpty()) ankiState.text = "적용됨: " + on.joinToString("·") { it.name }
                applyPlan(AnkiTemplate.plan(types, keep, code), types, quiet = true)
            }
        }
    }

    private fun chooseNoteTypes() {
        if (!AnkiBridge.installed(this)) {
            ankiState.text = "AnkiDroid가 설치되어 있지 않습니다."
            return
        }
        if (!AnkiBridge.granted(this)) {
            requestPermissions(arrayOf(AnkiBridge.PERMISSION), ANKI_PERMISSION)
            return
        }
        ankiState.text = "AnkiDroid에서 노트 유형을 읽는 중…"
        val code = ankiCode()
        inBackground({ AnkiBridge.noteTypes(contentResolver) }) { r ->
            r.onFailure { ankiState.text = ankiError(it) }
            r.onSuccess { types ->
                ankiState.text = ""
                if (types.isEmpty()) {
                    ankiState.text = "AnkiDroid에 노트 유형이 없습니다."
                    return@onSuccess
                }
                val chosen = Prefs.ankiTapTypes(this)
                val checked = BooleanArray(types.size) { types[it].installed || types[it].id in chosen }
                val names = types.map { t ->
                    when {
                        t.current(code) && t.installed -> "${t.name}  (적용됨)"
                        t.installed -> "${t.name}  (예전 코드 → 새 코드로)"
                        t.id in chosen -> "${t.name}  (코드가 사라짐 → 다시 넣기)"
                        else -> t.name
                    }
                }.toTypedArray()
                android.app.AlertDialog.Builder(this)
                    .setTitle("한자 바로보기를 넣을 노트 유형")
                    .setMultiChoiceItems(names, checked) { _, i, on -> checked[i] = on }
                    .setPositiveButton("적용") { _, _ ->
                        val wanted = types.filterIndexed { i, _ -> checked[i] }.map { it.id }.toSet()
                        Prefs.setAnkiTapTypes(this, wanted)
                        applyPlan(AnkiTemplate.plan(types, wanted, code), types, quiet = false)
                    }
                    .setNegativeButton("취소", null)
                    .show()
            }
        }
    }

    private lateinit var vocabState: TextView

    private fun showVocab() {
        val v = Prefs.vocab(this)
        vocabState.text = if (v == null) "아직 설정하지 않았습니다." else "노트 유형: ${v.typeName}\n덱: ${v.deck.replace("::", " › ")}"
    }

    private fun setUpVocab() {
        if (!AnkiBridge.installed(this)) {
            vocabState.text = "AnkiDroid가 설치되어 있지 않습니다."
            return
        }
        if (!AnkiBridge.granted(this)) {
            requestPermissions(arrayOf(AnkiBridge.PERMISSION), VOCAB_PERMISSION)
            return
        }
        vocabState.text = "AnkiDroid에서 노트 유형과 덱을 찾는 중…"
        val app = applicationContext
        inBackground({ VocabAdder.setUp(app) }) { r ->
            r.onSuccess { showVocab() }
            r.onFailure { e ->
                vocabState.text = if (e is IllegalStateException && e.message?.any { it in '가'..'힣' } == true) e.message
                else ankiError(e)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == VOCAB_PERMISSION) {
            if (AnkiBridge.granted(this)) setUpVocab()
            else vocabState.text = "AnkiDroid 접근을 허용해야 단어를 넣을 수 있습니다. " +
                "다시 물어보지 않으면 설정 → 애플리케이션 → 한자 바로보기 → 권한에서 AnkiDroid 항목을 허용해 주세요."
            return
        }
        if (requestCode != ANKI_PERMISSION) return
        if (AnkiBridge.granted(this)) chooseNoteTypes()
        else ankiState.text = "AnkiDroid 접근을 허용해야 앱이 카드에 코드를 넣을 수 있습니다. " +
            "다시 물어보지 않으면 설정 → 애플리케이션 → 한자 바로보기 → 권한에서 AnkiDroid 항목을 허용해 주세요."
    }

    private lateinit var updateState: TextView
    private lateinit var updateButton: Button
    private var latest: UpdateInfo.Latest? = null
    private var installing = false

    /** "빌드 24 · 최신 버전입니다" and a button: 업데이트 확인, or 빌드 N 설치 once GitHub has a newer one. */
    private fun updateRow(): View {
        updateState = label("빌드 ${AppUpdater.current}", 14f, pal.sub).apply { setPadding(0, dp(6f), 0, 0) }
        updateButton = button("업데이트 확인") { onUpdateButton() }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(updateState)
            addView(updateButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8f)
            })
        }
        checkUpdate(manual = false)
        return box
    }

    private fun checkUpdate(manual: Boolean) {
        if (manual) updateState.text = "빌드 ${AppUpdater.current} · 확인 중…"
        AppUpdater.check { found ->
            if (isFinishing || isDestroyed || installing) return@check
            latest = found?.takeIf { UpdateInfo.isNewer(it, AppUpdater.current) }
            val l = latest
            when {
                found == null -> if (manual) updateState.text = "빌드 ${AppUpdater.current} · 업데이트를 확인하지 못했습니다"
                l == null -> updateState.text = "빌드 ${AppUpdater.current} · 최신 버전입니다"
                else -> {
                    updateState.text = "빌드 ${AppUpdater.current} · 새 버전(빌드 ${l.build})이 있습니다"
                    updateButton.text = "빌드 ${l.build} 설치"
                    updateButton.setTextColor(pal.card)
                    updateButton.background = rounded(pal.accent, dp(12f))
                }
            }
        }
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
                updateButton.text = "빌드 ${l.build} 설치"
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            })
        if (!started) {
            Toast.makeText(this, "이 앱의 '알 수 없는 앱 설치'를 허용한 뒤 다시 눌러 주세요", Toast.LENGTH_LONG).show()
            return
        }
        installing = true
        updateButton.text = UpdateInfo.progressText(-1)
    }

    private fun showKey() {
        val key = Prefs.apiKey(this)
        keyState.text = if (key.isEmpty()) "저장된 키 없음" else "저장됨: " + Prefs.masked(key)
    }

    private fun section(col: LinearLayout, title: String) {
        col.addView(label(title, 17f, pal.accent, bold = true).apply { setPadding(0, dp(26f), 0, dp(8f)) })
    }

    private fun body(text: String): TextView = label(text, 15f, pal.text).apply { setLineSpacing(0f, 1.3f) }

    private fun button(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        isAllCaps = false
        setTextColor(pal.text)
        background = rounded(pal.button, dp(12f))
        stateListAnimator = null
        gravity = Gravity.CENTER
        minHeight = dp(48f)
        setOnClickListener { onClick() }
    }
}
