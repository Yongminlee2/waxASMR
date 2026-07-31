package com.waxball.asmr.ar

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View

/**
 * "여기에 손을 대세요" 점선 손 모양.
 *
 * 카메라를 켜면 검은 화면에 안내 문구만 뜨는데, 손을 어디에 어느 크기로 두라는
 * 것인지 알 수가 없다. 손 윤곽을 점선으로 그려 두면 거기에 맞추면 된다.
 *
 * 손바닥과 손가락을 따로 만들어 [Path.Op.UNION] 으로 합친다. 따로 그리면
 * 손가락이 손바닥에 박히는 자리에 안쪽 선이 남아서 손이 아니라 도형 무더기로 보인다.
 *
 * 어두운 방과 밝은 창가 둘 다에서 보여야 하므로 검은 테두리를 먼저 깔고
 * 그 위에 흰 점선을 얹는다. 한 겹만 쓰면 배경이 비슷한 밝기일 때 사라진다.
 */
class HandGuideView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val handPath = Path()
    private val scaled = Path()
    private val matrix = Matrix()

    /** 손이 실제로 차지하는 범위. 설계 좌표계의 빈 여백까지 세면 손이 한쪽으로 쏠린다. */
    private val bounds = RectF()

    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x66000000
        strokeCap = Paint.Cap.ROUND
    }
    private val dashes = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xF2FFFFFF.toInt()
        strokeCap = Paint.Cap.ROUND
    }

    private var startedAt = 0L

    init {
        buildHand()
    }

    /**
     * 설계 좌표계(120 x 135)에서 손을 만든다. 실제 크기는 그릴 때 맞춘다.
     * 왼쪽 여백은 엄지가 뻗어 나갈 자리다.
     */
    private fun buildHand() {
        handPath.reset()

        fun capsule(l: Float, t: Float, r: Float, b: Float, radius: Float): Path =
            Path().apply { addRoundRect(RectF(l, t, r, b), radius, radius, Path.Direction.CW) }

        // 손바닥. 손목 쪽이 더 둥글다.
        handPath.addRoundRect(RectF(30f, 56f, 102f, 122f), 22f, 22f, Path.Direction.CW)

        // 검지·중지·약지·새끼. 중지가 가장 길고 새끼가 가장 짧다.
        //
        // 손가락 사이를 넉넉히 벌린다. 붙여 놓으면 틈이 실오라기처럼 좁아서, 점선
        // 두 줄이 나란히 붙어 손가락이 아니라 지저분한 세로줄로 보인다.
        // 손가락 폭 13에 사이 6이면 대시 굵기(폭의 1.8%)를 빼고도 틈이 남는다.
        handPath.op(capsule(31f, 22f, 44f, 70f, 6.5f), Path.Op.UNION)
        handPath.op(capsule(50f, 12f, 63f, 70f, 6.5f), Path.Op.UNION)
        handPath.op(capsule(69f, 20f, 82f, 70f, 6.5f), Path.Op.UNION)
        handPath.op(capsule(88f, 36f, 101f, 70f, 6.5f), Path.Op.UNION)

        // 엄지는 손바닥 왼쪽에서 비스듬히 뻗는다. 세워 만든 뒤 붙는 자리를 축으로 돌린다.
        // 축이 손바닥 안쪽(42)이라야 돌린 뒤에도 겹쳐서 하나로 합쳐진다. 가장자리에
        // 두면 엄지가 떨어져 나가 허공에 뜬 점선이 된다.
        val thumb = capsule(20f, 68f, 42f, 120f, 11f)
        thumb.transform(Matrix().apply { setRotate(32f, 42f, 74f) })
        handPath.op(thumb, Path.Op.UNION)

        handPath.computeBounds(bounds, true)
    }

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return

        // 손이 화면 폭의 절반쯤 되게. 아래 볼 고르기 줄을 피해 살짝 위로 올린다.
        val scale = minOf(width * 0.52f / bounds.width(), height * 0.52f / bounds.height())
        matrix.setTranslate(-bounds.left, -bounds.top)
        matrix.postScale(scale, scale)
        matrix.postTranslate(
            (width - bounds.width() * scale) / 2f,
            (height - bounds.height() * scale) / 2f - height * 0.06f,
        )
        handPath.transform(matrix, scaled)

        val handWidth = bounds.width() * scale
        val dash = handWidth * 0.035f
        if (startedAt == 0L) startedAt = SystemClock.uptimeMillis()
        // 점선이 천천히 흐르면 "여기에 대라"는 신호로 읽힌다. 빠르면 눈이 피곤하다.
        val phase = ((SystemClock.uptimeMillis() - startedAt) % 4000L) / 4000f * (dash * 2f)
        val effect = DashPathEffect(floatArrayOf(dash, dash), -phase)

        outline.strokeWidth = handWidth * 0.030f
        outline.pathEffect = effect
        canvas.drawPath(scaled, outline)

        dashes.strokeWidth = handWidth * 0.018f
        dashes.pathEffect = effect
        canvas.drawPath(scaled, dashes)

        postInvalidateOnAnimation()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        // 안 보이는 동안에도 매 프레임 다시 그리면 카메라·인식과 GPU를 두고 다툰다.
        if (visibility == VISIBLE) postInvalidateOnAnimation() else startedAt = 0L
    }
}
