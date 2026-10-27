package com.ggumtak.hanja

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

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
        section(col, "쓰는 법")
        col.addView(body(
            "1. 아무 앱에서 한자를 꾹 눌러 선택합니다.\n" +
                "2. 뜨는 메뉴에서 '한자'를 누릅니다. 메뉴에 없으면 ⋮ 안에 있습니다.\n" +
                "   ⋮에도 없으면 ⋮ → '앱 관리'에서 '한자 바로보기'를 켜거나, '공유' → '한자'를 누릅니다.\n" +
                "3. 화면 아래 카드에 한국 훈음, 일본어 음독·훈독, 획수가 바로 나옵니다.\n" +
                "4. 한자 줄을 누르면 네이버 한자사전, 'Claude 설명'을 누르면 Claude가 단어와 예시까지 설명합니다.",
        ))
        col.addView(button("시험해 보기 (母は電気をつけて)") {
            startActivity(Intent(this, LookupActivity::class.java)
                .putExtra(Intent.EXTRA_PROCESS_TEXT, "母は電気をつけて"))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(12f)
        })

        section(col, "Claude API 키")
        col.addView(body(
            "'Claude 설명'에 쓰입니다. 키는 이 휴대폰의 이 앱 안에만 저장되고, 설명을 요청할 때만 Anthropic으로 보내집니다. " +
                "요금은 요청마다 API 계정에 청구됩니다(모델 Haiku 5.5).",
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

        section(col, "자료 출처")
        col.addView(body(
            "일본어 음독·훈독·획수·영어 뜻: KANJIDIC2 (EDRDG, CC BY-SA 4.0)\n" +
                "한국 훈음: libhangul 한자 사전 (BSD)\n" +
                "신자체·정자 대응: Unicode Unihan (Unicode License)",
        ).apply { textSize = 13f })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(pal.card)
            addView(col)
        })
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
