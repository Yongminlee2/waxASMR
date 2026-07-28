package com.waxball.asmr.ar

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.waxball.asmr.R
import com.waxball.asmr.audio.AudioEngine
import com.waxball.asmr.audio.Haptics
import com.waxball.asmr.core.BallCatalog
import com.waxball.asmr.core.BallSpec
import com.waxball.asmr.core.BreakModel
import com.waxball.asmr.core.Icosphere
import com.waxball.asmr.core.Quat
import com.waxball.asmr.core.ShardSplitter
import com.waxball.asmr.core.Vec3
import com.waxball.asmr.databinding.ActivityArPlayBinding
import com.waxball.asmr.gl.BallGeometry
import com.waxball.asmr.gl.BallScene
import com.waxball.asmr.gl.Debris
import com.waxball.asmr.gl.DebrisSpawner
import com.waxball.asmr.ui.Insets
import com.waxball.asmr.ui.PrefsProgressStore
import java.util.concurrent.Executors
import kotlin.random.Random

/**
 * 손바닥 위에 볼을 올려놓고 쥐어서 부수는 화면.
 *
 * 기존 화면은 화면 속 볼을 손가락으로 문질러 깬다. 여기서는 진짜 손 위에 올려놓고
 * 진짜로 쥐어서 으스러뜨린다. 실제 왁뿌볼을 쥐는 동작 그대로다.
 *
 * 파괴·소리·볼 생성은 기존 것을 그대로 쓴다. 이 화면이 하는 일은
 * 손 좌표를 볼의 위치·크기·힘으로 번역하는 것뿐이다.
 */
class ArPlayActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BALL_ID = "ballId"
        private const val TAG = "WaxBall"

        /**
         * 손에 쥐면 손가락이 볼을 감싸므로 뒷면까지 전부 으스러진다.
         *
         * 처음에는 카메라 쪽 반구만 눌렀는데(0.1), 그러면 앞쪽 뚜껑만 깨지고
         * 가장자리는 멀쩡히 남아서 쥐는 게 아니라 파는 것처럼 보였다.
         * -1이면 구 전체가 대상이 되고, 닿는 세기만 앞쪽이 조금 더 강하다.
         */
        private val FACING = Vec3(0f, 0f, 1f)
        private const val SQUEEZE_CONTACT_COS = -1f

    }

    private lateinit var binding: ActivityArPlayBinding
    private lateinit var tracker: HandTracker
    private lateinit var audio: AudioEngine
    private lateinit var haptics: Haptics

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val pose = PalmPose()
    private val ui = Handler(Looper.getMainLooper())

    private var spec: BallSpec = BallCatalog.all[0]

    /** 손 위에 올라간 공들. 쥐면 전부 한꺼번에 으스러진다. */
    private val scenes = ArrayList<BallScene>()
    private var ballCount = 1
    private var nextBallPending = false
    private var lostSince = 0L

    /** 인식 스레드가 쓰고 화면 스레드가 읽는다. */
    @Volatile private var latestHand: HandLandmarks? = null

    private val requestCamera = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera() else refuse(R.string.ar_permission_needed)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityArPlayBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Insets.applyBottom(binding.arBottomBar)

        binding.arBackButton.setOnClickListener { finish() }

        val progress = PrefsProgressStore(this).load()
        spec = BallCatalog.byId(intent.getIntExtra(EXTRA_BALL_ID, 0))

        audio = AudioEngine(this).apply { setVolume(progress.volume) }
        haptics = Haptics(this).apply { enabled = progress.hapticsOn }

        tracker = HandTracker(this) { hand -> latestHand = hand }
        if (!tracker.ready) {
            refuse(R.string.ar_no_camera)
            return
        }

        binding.arView.renderer.onFrame = ::onFrame
        buildBallPicker(progress)
        updateCountLabel()
        binding.arCountButton.setOnClickListener { cycleBallCount() }
        loadBalls(Random.nextLong())

        if (hasCameraPermission()) startCamera()
        else requestCamera.launch(Manifest.permission.CAMERA)
    }

    /**
     * 카메라를 켠 채로 공을 바꾼다.
     *
     * 공을 바꾸려고 화면을 나갔다 들어오면 카메라가 다시 뜨는 동안 흐름이 끊긴다.
     * 손을 든 채로 바로 바꿀 수 있어야 한다.
     */
    private fun buildBallPicker(progress: com.waxball.asmr.core.Progress) {
        binding.arBallList.removeAllViews()
        val unlocked = BallCatalog.all.filter { progress.isUnlocked(it.id) }

        for (candidate in unlocked) {
            val swatch = android.view.View(this).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(candidate.shellColor)
                    setStroke(dp(2), if (candidate.id == spec.id) 0xFFFFFFFF.toInt() else 0x40FFFFFF)
                }
                contentDescription = candidate.nameKo
                setOnClickListener { switchBall(candidate, progress) }
            }
            val params = android.widget.LinearLayout.LayoutParams(dp(40), dp(40))
            params.marginEnd = dp(10)
            binding.arBallList.addView(swatch, params)
        }
    }

    private fun switchBall(next: BallSpec, progress: com.waxball.asmr.core.Progress) {
        if (next.id == spec.id) return
        spec = next
        audio.setProfile(spec.soundProfile())
        buildBallPicker(progress)
        loadBalls(Random.nextLong())
    }

    /** 손바닥 위에 올릴 공 개수를 1~3으로 돌린다. */
    private fun cycleBallCount() {
        ballCount = ArLayout.nextCount(ballCount)
        updateCountLabel()
        loadBalls(Random.nextLong())
    }

    private fun updateCountLabel() {
        binding.arCountButton.text = getString(R.string.ar_count, ballCount)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onResume() {
        super.onResume()
        binding.arView.onResume()
        audio.setProfile(spec.soundProfile())
        audio.setRawPlayback(PrefsProgressStore(this).load().rawPlayback)
        audio.start()
    }

    override fun onPause() {
        super.onPause()
        audio.stop()
        haptics.cancel()
        binding.arView.onPause()
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                val preview = Preview.Builder().build().apply {
                    setSurfaceProvider(binding.cameraPreview.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .apply { setAnalyzer(cameraExecutor) { tracker.analyze(it) } }

                provider.unbindAll()
                provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            } catch (e: Exception) {
                Log.e(TAG, "카메라를 열지 못함: ${e.message}")
                refuse(R.string.ar_no_camera)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * 볼을 [ballCount] 개 만든다. 볼 하나하나는 기존 모드와 똑같이 만든다.
     *
     * 개수가 늘면 조각도 그만큼 늘어난다. 손바닥 위라 볼 하나가 작게 보이므로
     * 개수에 따라 조각 수를 줄여도 티가 안 나고, 프레임이 버틴다.
     */
    private fun loadBalls(seed: Long) {
        nextBallPending = false
        val target = spec
        val count = ballCount
        val quality = ArLayout.qualityFor(count)

        Thread({
            val built = ArrayList<BallScene>(count)
            for (i in 0 until count) {
                val ballSeed = seed + i * 7919L
                val base = Icosphere.build(target.baseSubdivision(quality))
                val shards = ShardSplitter.split(base, target.shardCount(quality), Random(ballSeed))
                val geometry = BallGeometry.build(shards, target.shellThickness, target.shape::warp)
                val model = BreakModel(shards, target.soundProfile(), audio.queue)
                val debris = Debris(shards.size, Random(ballSeed + 1))
                built.add(BallScene(target, shards, geometry, model, debris))
            }
            runOnUiThread {
                scenes.clear()
                scenes.addAll(built)
                binding.arView.renderer.setScenes(built)
            }
        }, "ArBallBuilder").start()
    }

    private val offsetScratch = FloatArray(2)

    private fun onFrame(dt: Float) {
        if (scenes.isEmpty()) return
        val renderer = binding.arView.renderer

        pose.update(latestHand, dt)

        if (pose.hasHand) {
            val width = binding.arView.width.coerceAtLeast(1)
            val height = binding.arView.height.coerceAtLeast(1)

            val handSpanPx = pose.span * width
            val radiusPx = ArLayout.radiusPx(handSpanPx, scenes.size)

            for (i in scenes.indices) {
                ArLayout.offsetPx(i, scenes.size, handSpanPx, offsetScratch)
                renderer.placeAt(
                    i,
                    pose.centerX * width + offsetScratch[0],
                    pose.centerY * height + offsetScratch[1],
                    radiusPx,
                )
            }

            if (pose.force > 0f) {
                audio.markTouch()
                var broken = 0f
                for (s in scenes) {
                    s.model.pressArea(FACING, SQUEEZE_CONTACT_COS, pose.force, dt, 0f)
                    broken += DebrisSpawner.spawnFreshlyDetached(s, Quat.IDENTITY)
                }
                if (broken > 0f) {
                    val magnitude = (broken / 0.03f).coerceIn(0f, 1f)
                    renderer.shake(magnitude)
                    if (magnitude > 0.35f) haptics.thud(magnitude) else haptics.pulse(0.3f + magnitude)
                }
            }
        } else {
            renderer.hideBalls()
        }

        for (s in scenes) {
            s.debris.update(dt, renderer.floorY, s.geometry.shardCenters) { id, pan, _ ->
                s.model.land(id, pan, s.shards.shards[id].areaFrac)
            }
        }

        // 전부 다 부서져야 새로 깐다. 하나만 남아도 계속 만질 거리가 있다.
        if (scenes.all { it.model.shellProgress >= 0.999f } && !nextBallPending) {
            nextBallPending = true
            ui.postDelayed({ if (!isFinishing) loadBalls(Random.nextLong()) }, 2600)
        }

        ui.post { updateHint() }
    }

    /** 손을 놓치면 안내를 되살리고, 잡았는데 안 쥐고 있으면 쥐라고 알려 준다. */
    private fun updateHint() {
        if (isFinishing) return
        val now = System.currentTimeMillis()
        if (!pose.hasHand) {
            if (lostSince == 0L) lostSince = now
            if (now - lostSince > 500L) {
                binding.hint.setText(R.string.ar_show_palm)
                binding.hint.visibility = View.VISIBLE
            }
            return
        }

        lostSince = 0L
        if (pose.squeeze < 0.1f) {
            binding.hint.setText(R.string.ar_squeeze)
            binding.hint.visibility = View.VISIBLE
        } else {
            binding.hint.visibility = View.GONE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.arView.renderer.onFrame = null
        ui.removeCallbacksAndMessages(null)
        tracker.close()
        cameraExecutor.shutdown()
    }

    /** AR을 강요하지 않는다. 안 되면 안내하고 기존 모드로 돌려보낸다. */
    private fun refuse(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_LONG).show()
        finish()
    }
}
