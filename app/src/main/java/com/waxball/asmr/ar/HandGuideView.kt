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
import kotlin.math.atan2
import kotlin.math.sqrt

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
     * 손가락 하나. 뿌리 (x1,y1) 에서 끝 (x2,y2) 로 뻗는 둥근 캡슐이다.
     *
     * 손가락마다 뻗는 방향이 다르다 — 새끼는 바깥으로 벌어지고 엄지는 비스듬히
     * 올라간다. 세로 캡슐만으로는 그 각도가 안 나와서 두 점을 잇는 방식으로 만든다.
     *
     * 뿌리는 손바닥 안에 묻는다. 가장자리에 걸치면 합칠 때 떨어져 나가
     * 허공에 뜬 점선이 된다.
     */
    private fun limb(x1: Float, y1: Float, x2: Float, y2: Float, r: Float): Path {
        val dx = x2 - x1
        val dy = y2 - y1
        val length = sqrt(dx * dx + dy * dy)
        val p = Path()
        // 원점에서 아래(+y)로 뻗는 캡슐을 만든 뒤 원하는 방향으로 돌린다.
        p.addRoundRect(RectF(-r, 0f, r, length), r, r, Path.Direction.CW)
        val deg = Math.toDegrees(atan2(-dx.toDouble(), dy.toDouble())).toFloat()
        p.transform(Matrix().apply { setRotate(deg); postTranslate(x1, y1) })
        return p
    }

    /** 설계 좌표계에서 손을 만든다. 실제 크기와 자리는 그릴 때 맞춘다. */
    private fun buildHand() {
        handPath.reset()

        // 손바닥과 손목.
        handPath.addRoundRect(RectF(26f, 58f, 84f, 108f), 22f, 22f, Path.Direction.CW)
        handPath.op(limb(48f, 96f, 48f, 128f, 13f), Path.Op.UNION)

        // 새끼·약지·중지·검지. 중지가 가장 길고 새끼는 바깥으로 벌어진다.
        handPath.op(limb(30f, 76f, 16f, 30f, 6.0f), Path.Op.UNION)
        handPath.op(limb(42f, 70f, 36f, 16f, 6.2f), Path.Op.UNION)
        handPath.op(limb(55f, 68f, 55f, 8f, 6.4f), Path.Op.UNION)
        handPath.op(limb(68f, 70f, 76f, 18f, 6.2f), Path.Op.UNION)

        // 엄지는 오른쪽 위로 비스듬히. 손바닥을 편 왼손을 카메라에 보이는 모양이다.
        handPath.op(limb(70f, 96f, 100f, 58f, 7.0f), Path.Op.UNION)

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
        // 점 사이를 점 길이보다 넓게 띄운다. 촘촘하면 실선처럼 뭉쳐 보인다.
        val dash = handWidth * 0.022f
        val gap = handWidth * 0.040f
        if (startedAt == 0L) startedAt = SystemClock.uptimeMillis()
        // 점선이 천천히 흐르면 "여기에 대라"는 신호로 읽힌다. 빠르면 눈이 피곤하다.
        val phase = ((SystemClock.uptimeMillis() - startedAt) % 4000L) / 4000f * (dash + gap)
        val effect = DashPathEffect(floatArrayOf(dash, gap), -phase)

        outline.strokeWidth = handWidth * 0.020f
        outline.pathEffect = effect
        canvas.drawPath(scaled, outline)

        dashes.strokeWidth = handWidth * 0.011f
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
