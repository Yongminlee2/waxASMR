package com.waxball.asmr.ui

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.MotionEvent
import com.waxball.asmr.gl.BallRenderer

/**
 * 3D 표면과 터치 입력을 담당한다. 게임 규칙은 전혀 모른다.
 */
class PlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : GLSurfaceView(context, attrs) {

    val renderer = BallRenderer()
    private var router: InputRouter? = null

    private val xs = FloatArray(2)
    private val ys = FloatArray(2)

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 0, 16, 0)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun attach(listener: InputRouter.Listener): InputRouter {
        val r = InputRouter(listener)
        router = r
        return r
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val r = router ?: return false

        val count = minOf(event.pointerCount, 2)
        for (i in 0 until count) {
            xs[i] = event.getX(i)
            ys[i] = event.getY(i)
        }

        val action = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> TouchAction.DOWN
            MotionEvent.ACTION_MOVE -> TouchAction.MOVE
            MotionEvent.ACTION_UP -> TouchAction.UP
            MotionEvent.ACTION_POINTER_DOWN -> TouchAction.POINTER_DOWN
            MotionEvent.ACTION_POINTER_UP -> TouchAction.POINTER_UP
            MotionEvent.ACTION_CANCEL -> TouchAction.CANCEL
            else -> return false
        }

        r.ballCenterX = width * 0.5f
        r.ballCenterY = height * 0.5f
        r.ballRadius = renderer.ballScreenRadius

        r.onTouch(action, count, xs, ys, System.nanoTime())
        return true
    }
}
