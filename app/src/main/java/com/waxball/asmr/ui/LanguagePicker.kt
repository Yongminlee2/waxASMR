package com.waxball.asmr.ui

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.waxball.asmr.R

/**
 * 화면 오른쪽 위 지구본 버튼이 여는 언어 선택 대화상자.
 *
 * [AppCompatDelegate.setApplicationLocales]는 androidx.appcompat 1.6부터
 * API 33 미만에서도 자체적으로 앱별 언어를 저장·적용해 준다. 고르면
 * 열려 있는 액티비티들이 알아서 다시 그려지므로 recreate()를 따로 부를 필요가 없다.
 *
 * 첫 실행처럼 사용자가 아직 고른 적이 없으면 시스템 로케일을 그대로 따르고,
 * 그게 곧 "기기 언어(=대개 다운받은 나라 말)로 기본 설정"이다. 스토어가 국가
 * 코드를 앱에 넘겨주지는 않지만, 실제로는 기기 언어 설정이 그 역할을 한다.
 */
object LanguagePicker {

    /** (표시 이름, BCP-47 언어 태그). 인도네시아어는 자바 레거시 코드 "in"이 아니라 "id"를 쓴다. */
    private val LANGUAGES = listOf(
        "한국어" to "ko",
        "English" to "en",
        "中文" to "zh",
        "日本語" to "ja",
        "Español" to "es",
        "Português" to "pt",
        "Deutsch" to "de",
        "Bahasa Indonesia" to "id",
        "Tiếng Việt" to "vi",
        "ภาษาไทย" to "th",
    )

    fun show(activity: Activity) {
        val followSystem = activity.getString(R.string.language_follow_system)
        val labels = arrayOf(followSystem) + LANGUAGES.map { it.first }.toTypedArray()

        AlertDialog.Builder(activity)
            .setTitle(R.string.language_picker_title)
            .setItems(labels) { _, index ->
                if (index == 0) {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
                } else {
                    val tag = LANGUAGES[index - 1].second
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                }
            }
            .show()
    }
}
