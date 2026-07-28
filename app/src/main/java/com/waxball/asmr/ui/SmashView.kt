package com.waxball.asmr.ui

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import com.waxball.asmr.gl.SmashRenderer

/** 사진 조각을 그리는 GL 화면. 카메라 미리보기를 깔지 않으므로 투명이 아니다. */
class SmashView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : GLSurfaceView(context, attrs) {

    val renderer = SmashRenderer()

    init {
        setEGLContextClientVersion(3)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }
}
