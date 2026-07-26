package com.waxball.asmr.ui

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * 상태바와 내비게이션 바를 피해서 여백을 준다.
 *
 * targetSdk 35부터 앱이 화면 끝까지 그려지기 때문에, 그냥 두면 버튼이
 * 시스템 바 밑에 깔린다. 제스처 내비게이션 기기에서는 잘 안 보이지만
 * 3버튼 내비게이션을 쓰는 기기에서는 그대로 겹쳐 버린다.
 *
 * 원래 여백을 기억해 두고 거기에 더한다. 안 그러면 인셋이 다시 올 때마다
 * 여백이 계속 쌓인다.
 */
object Insets {

    fun applyTop(view: View) = apply(view, top = true, bottom = false)

    fun applyBottom(view: View) = apply(view, top = false, bottom = true)

    fun applyBoth(view: View) = apply(view, top = true, bottom = true)

    private fun apply(view: View, top: Boolean, bottom: Boolean) {
        val startLeft = view.paddingLeft
        val startTop = view.paddingTop
        val startRight = view.paddingRight
        val startBottom = view.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(
                startLeft + bars.left,
                startTop + if (top) bars.top else 0,
                startRight + bars.right,
                startBottom + if (bottom) bars.bottom else 0,
            )
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }
}
