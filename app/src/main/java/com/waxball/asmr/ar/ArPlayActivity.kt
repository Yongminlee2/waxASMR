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

        /** 손에 쥐면 닿는 면 전체가 으스러진다. 카메라 쪽 반구를 통째로 누른다. */
        private val FACING = Vec3(0f, 0f, 1f)
        private const val SQUEEZE_CONTACT_COS = 0.1f

        /** 볼을 손 너비의 이 비율로 놓는다. 1을 넘으면 손 밖으로 삐져나온다. */
        private const val BALL_TO_HAND = 0.9f
    }

    private lateinit var binding: ActivityArPlayBinding
    private lateinit var tracker: HandTracker
    private lateinit var audio: AudioEngine
    private lateinit var haptics: Haptics

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val pose = PalmPose()
    private val ui = Handler(Looper.getMainLooper())

    private var spec: BallSpec = BallCatalog.all[0]
    private var scene: BallScene? = null
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
        loadBall(Random.nextLong())

        if (hasCameraPermission()) startCamera()
        else requestCamera.launch(Manifest.permission.CAMERA)
    }

    override fun onResume() {
        super.onResume()
        binding.arView.onResume()
        audio.setProfile(spec.soundProfile())
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

    /** 볼 만들기는 기존 모드와 똑같다. */
    private fun loadBall(seed: Long) {
        nextBallPending = false
        val target = spec
        Thread({
            val base = Icosphere.build(target.baseSubdivision(1))
            val shards = ShardSplitter.split(base, target.shardCount(1), Random(seed))
            val geometry = BallGeometry.build(shards, target.shellThickness, target.shape::warp)
            val model = BreakModel(shards, target.soundProfile(), audio.queue)
            val debris = Debris(shards.size, Random(seed + 1))
            val next = BallScene(target, shards, geometry, model, debris)
            runOnUiThread {
                scene = next
                binding.arView.renderer.setScene(next)
            }
        }, "ArBallBuilder").start()
    }

    private fun onFrame(dt: Float) {
        val s = scene ?: return
        val renderer = binding.arView.renderer

        pose.update(latestHand, dt)

        if (pose.hasHand) {
            val width = binding.arView.width.coerceAtLeast(1)
            val height = binding.arView.height.coerceAtLeast(1)
            renderer.placeAt(
                pose.centerX * width,
                pose.centerY * height,
                pose.span * width * BALL_TO_HAND,
            )

            if (pose.force > 0f) {
                audio.markTouch()
                s.model.pressArea(FACING, SQUEEZE_CONTACT_COS, pose.force, dt, 0f)

                val broken = DebrisSpawner.spawnFreshlyDetached(s, Quat.IDENTITY)
                if (broken > 0f) {
                    val magnitude = (broken / 0.03f).coerceIn(0f, 1f)
                    renderer.shake(magnitude)
                    if (magnitude > 0.35f) haptics.thud(magnitude) else haptics.pulse(0.3f + magnitude)
                }
            }
        } else {
            renderer.hideBall()
        }

        s.debris.update(dt, renderer.floorY, s.geometry.shardCenters) { id, pan, _ ->
            s.model.land(id, pan, s.shards.shards[id].areaFrac)
        }

        if (s.model.shellProgress >= 0.999f && !nextBallPending) {
            nextBallPending = true
            ui.postDelayed({ if (!isFinishing) loadBall(Random.nextLong()) }, 2600)
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
