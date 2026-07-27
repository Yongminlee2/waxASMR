package com.waxball.asmr.ar

import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import com.waxball.asmr.gl.BallRenderer

/**
 * 카메라 화면 위에 볼만 그리는 투명 GL 화면.
 *
 * 카메라 영상을 GL로 직접 그리지 않는다. 직접 그리면 색공간 변환과 회전 처리를
 * 전부 떠안아야 하는데 얻는 것이 없다. 미리보기를 아래 깔고 그 위에 겹친다.
 */
class ArPlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : GLSurfaceView(context, attrs) {

    val renderer = BallRenderer().apply { transparentBackground = true }

    init {
        setEGLContextClientVersion(3)
        // 알파 8비트가 있어야 아래 카메라 화면이 비친다.
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        setZOrderMediaOverlay(true)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }
}
