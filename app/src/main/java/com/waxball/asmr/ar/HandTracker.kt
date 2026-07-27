package com.waxball.asmr.ar

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlin.math.sqrt

/**
 * 카메라 프레임에서 손 관절을 뽑아낸다.
 *
 * 결과는 [HandLandmarks] 로만 넘긴다. 그래야 좌표를 볼로 바꾸는 계산([PalmPose])이
 * 카메라와 무관해지고 PC에서 검증된다.
 *
 * 모델을 못 읽어도 예외를 밖으로 던지지 않는다. [ready] 가 false면 호출자가
 * 안내하고 물러나면 된다. 앱이 죽는 것보다 낫다.
 */
class HandTracker(
    context: Context,
    private val onHand: (HandLandmarks?) -> Unit,
) {
    private var landmarker: HandLandmarker? = null

    val ready: Boolean get() = landmarker != null

    init {
        landmarker = try {
            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setModelAssetPath(MODEL)
                        .build()
                )
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumHands(2)
                .setMinHandDetectionConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setResultListener { result, _ -> deliver(result) }
                .setErrorListener { e -> Log.w(TAG, "손 인식 오류: ${e.message}") }
                .build()
            HandLandmarker.createFromOptions(context, options)
        } catch (e: Exception) {
            Log.e(TAG, "손 인식 모델을 못 읽음: ${e.message}")
            null
        }
    }

    /** 프레임 하나를 넘긴다. 처리 후 반드시 닫는다. */
    fun analyze(image: ImageProxy) {
        val engine = landmarker
        if (engine == null) {
            image.close()
            return
        }
        try {
            val bitmap = image.toBitmap()
            val rotated = rotate(bitmap, image.imageInfo.rotationDegrees)
            engine.detectAsync(BitmapImageBuilder(rotated).build(), System.currentTimeMillis())
        } catch (e: Exception) {
            Log.w(TAG, "프레임 처리 실패: ${e.message}")
        } finally {
            image.close()
        }
    }

    fun close() {
        try {
            landmarker?.close()
        } catch (e: Exception) {
            // 닫는 중 실패는 무시한다. 어차피 화면을 떠나는 중이다.
        }
        landmarker = null
    }

    /**
     * 손이 여럿이면 가장 큰 것 하나만 쓴다.
     * 여러 손을 동시에 다루면 볼이 어느 손을 따라갈지 매 프레임 흔들린다.
     */
    private fun deliver(result: HandLandmarkerResult) {
        val hands = result.landmarks()
        countFrame(hands.size)
        if (hands.isEmpty()) {
            onHand(null)
            return
        }

        var best = 0
        var bestSpan = -1f
        for (i in hands.indices) {
            val points = hands[i]
            if (points.size < HandLandmarks.COUNT) continue
            val dx = points[HandLandmarks.INDEX_MCP].x() - points[HandLandmarks.PINKY_MCP].x()
            val dy = points[HandLandmarks.INDEX_MCP].y() - points[HandLandmarks.PINKY_MCP].y()
            val span = sqrt(dx * dx + dy * dy)
            if (span > bestSpan) { bestSpan = span; best = i }
        }
        if (bestSpan < 0f) {
            onHand(null)
            return
        }

        val points = hands[best]
        val x = FloatArray(HandLandmarks.COUNT)
        val y = FloatArray(HandLandmarks.COUNT)
        for (i in 0 until HandLandmarks.COUNT) {
            x[i] = points[i].x()
            y[i] = points[i].y()
        }
        onHand(HandLandmarks(x, y))
    }

    /** 초당 몇 프레임을 처리했고 그중 몇 번 손을 잡았는지 남긴다. 화면만 봐서는 알 수 없다. */
    private var frames = 0
    private var detections = 0
    private var windowStart = 0L

    private fun countFrame(handCount: Int) {
        frames++
        if (handCount > 0) detections++
        val now = System.currentTimeMillis()
        if (windowStart == 0L) { windowStart = now; return }
        if (now - windowStart >= 1000L) {
            Log.i(TAG, "손 인식 ${frames}프레임 중 ${detections}회 검출")
            frames = 0; detections = 0; windowStart = now
        }
    }

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = android.graphics.Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private companion object {
        const val TAG = "WaxBall"
        const val MODEL = "hand_landmarker.task"
    }
}
