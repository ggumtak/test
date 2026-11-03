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
                "3. 화면 아래 카드에 한자별 한국 훈음, 음독·훈독(한글 발음), 획수가 나옵니다.\n" +
                "4. '일본어사전'·'한자사전'은 네이버 사전을 열고, 'Claude 설명'은 Claude가 단어와 예시까지 설명합니다. " +
                "'질문'을 누르면 고른 글자가 입력창에 들어가고, 뒤에 질문을 이어 써서 보내면 Claude가 답합니다.",
        ))
        col.addView(button("시험해 보기 (母は電気をつけて)") {
            startActivity(Intent(this, LookupActivity::class.java)
                .putExtra(Intent.EXTRA_PROCESS_TEXT, "母は電気をつけて"))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(12f)
        })

        section(col, "Anki에서 한 번 탭으로 보기")
        col.addView(body(
            "Anki 카드의 한자·가타카나를 탭하거나, 글자를 드래그한 뒤 아래에 뜨는 '한자' 버튼을 누르면 바로 카드가 뜨게 합니다. " +
                "처음 한 번만 하면 됩니다. 예전 한자 바로보기 코드는 지우고 새 코드로 바꿔 주세요(앞면·뒷면 모두).\n" +
                "1. 아래 'Anki용 코드 복사'를 누릅니다.\n" +
                "2. Anki에서 그 카드를 보다가 ⋮ → 노트 편집 → 화면 맨 아래까지 내려 '카드: …' 버튼을 누릅니다.\n" +
                "3. 아래쪽 '앞면 템플릿' 탭 내용 맨 끝에 붙여 넣고, '뒷면 템플릿' 탭 맨 끝에도 붙여 넣은 뒤 위의 ✓로 저장합니다. " +
                "노트 유형(문법, 단어 등)마다 템플릿이 따로라서 유형마다 한 번씩 합니다.\n" +
                "4. 탭했을 때 '보안 차단' 안내가 뜨면 Anki 설정 → 고급 설정 → '카드에서 다른 앱으로 이동 허용'을 켭니다. " +
                "이 설정은 내 컬렉션의 모든 카드에 적용되므로, 출처를 모르는 공유 덱을 받을 때는 주의하세요.",
        ))
        col.addView(button("Anki용 코드 복사") {
            val code = assets.open("anki-tap.html").use { it.readBytes().toString(Charsets.UTF_8) }
            val cm = getSystemService(ClipboardManager::class.java)
            cm.setPrimaryClip(ClipData.newPlainText("한자 바로보기 Anki 코드", code))
            Toast.makeText(this, "복사했습니다. Anki 템플릿 맨 아래에 붙여 넣으세요", Toast.LENGTH_LONG).show()
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(12f)
        })

        section(col, "Claude API 키")
        col.addView(body(
            "'Claude 설명'에 쓰입니다. 키는 이 휴대폰의 이 앱 안에만 저장되고, 설명을 요청할 때만 Anthropic으로 보내집니다. " +
                "요금은 요청마다 API 계정에 청구됩니다. 읽기 판정은 Haiku 5.5, 질문·설명은 아래에서 고른 모델을 씁니다.",
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
        col.addView(label("질문·Claude 설명에 쓸 모델", 15f, pal.text, bold = true).apply { setPadding(0, dp(14f), 0, dp(4f)) })
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
        col.addView(label("높을수록 답이 꼼꼼하지만 몇 초 더 걸리고 요금이 조금 늘어납니다.", 13f, pal.sub))
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
                "상용한자 2,136자의 쉬운 뜻: KANJIDIC2와 옛 훈음을 바탕으로 Claude가 쓰고 따로 검토\n" +
                "한국 훈음: libhangul 한자 사전 (BSD)\n" +
                "신자체·정자 대응: Unicode Unihan (Unicode License)",
        ).apply { textSize = 13f })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(pal.card)
            addView(col)
        })
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
