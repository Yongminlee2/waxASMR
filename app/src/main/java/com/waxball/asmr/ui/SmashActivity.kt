package com.waxball.asmr.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.waxball.asmr.R
import com.waxball.asmr.audio.AudioEngine
import com.waxball.asmr.audio.Haptics
import com.waxball.asmr.core.BreakModel
import com.waxball.asmr.core.PlaneCell
import com.waxball.asmr.core.PlaneShards
import com.waxball.asmr.core.PlaneSplitter
import com.waxball.asmr.core.Quat
import com.waxball.asmr.core.ShardState
import com.waxball.asmr.core.SmashProfile
import com.waxball.asmr.core.Vec3
import com.waxball.asmr.databinding.ActivitySmashBinding
import com.waxball.asmr.gl.Debris
import java.util.concurrent.Executors
import kotlin.random.Random

/**
 * 카메라로 찍은 한 장을 손가락으로 깨부수는 화면.
 *
 * 손 인식이 없다. 화면을 만지는 것이므로 기존 터치 처리와 같은 성격이고,
 * 손바닥 모드가 겪던 인식 문제가 끼어들 자리가 없다.
 *
 * 파괴·소리·부스러기는 기존 것을 그대로 쓴다. 이 화면이 하는 일은
 * 손가락 좌표를 조각 번호로 바꾸는 것뿐이다.
 */
class SmashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySmashBinding
    private lateinit var audio: AudioEngine
    private lateinit var haptics: Haptics

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())

    private var cells: List<PlaneCell> = emptyList()
    private var model: BreakModel? = null
    private var debris: Debris? = null
    private var nextPending = false

    /** 카메라에서 한 장만 받고 바로 놓는다. */
    @Volatile private var grabbed = false

    private val requestCamera = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) grabPhoto() else usePlaceholder()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySmashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Insets.applyBottom(binding.smashBottomBar)

        val progress = PrefsProgressStore(this).load()
        audio = AudioEngine(this).apply {
            setVolume(progress.volume)
            setProfile(SmashProfile.of())
        }
        haptics = Haptics(this).apply { enabled = progress.hapticsOn }

        binding.smashBackButton.setOnClickListener { finish() }
        binding.smashAgainButton.setOnClickListener { restart() }
        binding.smashView.renderer.onFrame = ::onFrame
        binding.smashView.setOnTouchListener { _, event -> onTouch(event) }

        restart()
    }

    /** 조각을 새로 나누고 사진을 새로 받는다. */
    private fun restart() {
        nextPending = false
        cells = PlaneSplitter.split(SHARD_COUNT, Random(System.nanoTime()))
        val set = PlaneShards.toShardSet(cells)
        val d = Debris(set.size, Random(1))
        model = BreakModel(set, SmashProfile.of(), audio.queue)
        debris = d
        centersScratch = FloatArray(0)
        binding.smashView.renderer.setScene(set, d)

        if (hasCameraPermission()) grabPhoto()
        else requestCamera.launch(Manifest.permission.CAMERA)
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * 첫 프레임 한 장만 받고 카메라를 놓는다.
     *
     * 실시간 영상을 쓰지 않으므로 카메라를 계속 열어 둘 이유가 없다.
     * 켜 둔 채로 두면 발열과 배터리만 먹는다.
     */
    private fun grabPhoto() {
        grabbed = false
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .apply {
                        setAnalyzer(cameraExecutor) { image ->
                            if (!grabbed) {
                                grabbed = true
                                val bitmap = rotate(image.toBitmap(), image.imageInfo.rotationDegrees)
                                runOnUiThread {
                                    if (!isFinishing) binding.smashView.renderer.setPhoto(bitmap)
                                    provider.unbindAll()
                                }
                            }
                            image.close()
                        }
                    }
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, analysis)
            } catch (e: Exception) {
                Log.w(TAG, "카메라를 못 열었다: ${e.message}")
                usePlaceholder()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * 카메라가 없어도 깰 수 있어야 한다.
     * 손바닥 모드는 카메라가 없으면 돌려보내지만, 여기는 깨는 것이 목적이다.
     */
    private fun usePlaceholder() {
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, 512f,
                0xFF2A2F3A.toInt(), 0xFF11141B.toInt(), Shader.TileMode.CLAMP,
            )
        }
        Canvas(bitmap).drawRect(0f, 0f, 512f, 512f, paint)
        binding.smashView.renderer.setPhoto(bitmap)
        Toast.makeText(this, R.string.smash_no_camera, Toast.LENGTH_SHORT).show()
    }

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = android.graphics.Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private var touching = false
    private var touchX = 0f
    private var touchY = 0f

    private fun onTouch(event: MotionEvent): Boolean {
        touchX = event.x / binding.smashView.width.coerceAtLeast(1)
        touchY = event.y / binding.smashView.height.coerceAtLeast(1)
        touching = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> true
            else -> false
        }
        if (touching) audio.markTouch()
        return true
    }

    private fun onFrame(dt: Float) {
        val m = model ?: return
        val d = debris ?: return
        val renderer = binding.smashView.renderer

        if (touching) {
            val before = m.detachedCount
            PlaneShards.pressAt(m, cells, touchX, touchY, BRUSH_RADIUS, TOUCH_FORCE, dt)

            for (c in cells) {
                if (m.state[c.id] >= ShardState.DETACHED && !d.isActive(c.id)) {
                    // 조각이 붙어 있던 자리에서 바깥으로 튕겨 나간다.
                    val outward = Vec3(c.centerX * 2f - 1f, 1f - c.centerY * 2f, 1f)
                    d.spawn(c.id, outward, c.areaFrac, Quat.IDENTITY)
                }
                renderer.setShrink(c.id, shrinkFor(m.state[c.id]))
            }

            val broken = m.detachedCount - before
            if (broken > 0) haptics.pulse(0.3f + (broken / 6f).coerceAtMost(0.7f))
        }

        d.update(dt, renderer.floorY, centersOf(m)) { id, pan, _ ->
            m.land(id, pan, m.shards.shards[id].areaFrac)
        }

        if (m.shellProgress >= 0.999f && !nextPending) {
            nextPending = true
            ui.postDelayed({ if (!isFinishing) restart() }, 2600)
        }
    }

    private var centersScratch = FloatArray(0)

    private fun centersOf(m: BreakModel): FloatArray {
        if (centersScratch.size != m.shards.size * 3) {
            centersScratch = FloatArray(m.shards.size * 3)
            for (s in m.shards.shards) {
                centersScratch[s.id * 3] = s.center.x * 2f - 1f
                centersScratch[s.id * 3 + 1] = 1f - s.center.y * 2f
                centersScratch[s.id * 3 + 2] = 0f
            }
        }
        return centersScratch
    }

    /** 금이 갈수록 조각이 제 중심으로 오므라들어 틈이 벌어진다. */
    private fun shrinkFor(state: Int): Float = when {
        state >= ShardState.DETACHED -> 0f
        state >= ShardState.LOOSE -> 0.06f
        state >= ShardState.CRACKED -> 0.035f
        state >= ShardState.HAIRLINE -> 0.015f
        else -> 0f
    }

    override fun onResume() {
        super.onResume()
        binding.smashView.onResume()
        audio.start()
    }

    override fun onPause() {
        super.onPause()
        audio.stop()
        haptics.cancel()
        binding.smashView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.smashView.renderer.onFrame = null
        ui.removeCallbacksAndMessages(null)
        cameraExecutor.shutdown()
    }

    private companion object {
        const val TAG = "WaxBall"

        /** 화면을 나누는 조각 수. 늘리면 잘게 깨지지만 프레임이 무거워진다. */
        const val SHARD_COUNT = 150

        /** 손가락 붓 반경(화면 정규 단위). */
        const val BRUSH_RADIUS = 0.09f

        /**
         * 터치 한 프레임에 들어가는 힘.
         * BreakModel이 기대하는 힘은 1~4다. 스트레스 해소용이라 센 쪽으로 잡는다.
         */
        const val TOUCH_FORCE = 3.5f
    }
}
