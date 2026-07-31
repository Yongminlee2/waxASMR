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
import com.waxball.asmr.gl.TrappedShards
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

        /**
         * 손 이동 → 볼 회전 배율(화면 폭 기준 라디안). 손을 좌우·앞뒤로 흔들면
         * 볼이 딸려 돌아간다. 쥐고 있을 때는 줄여서 부수는 감각을 지킨다.
         */
        private const val ROLL_GAIN = 4.5f
        private const val SQUEEZE_CONTACT_COS = -1f

        /**
         * 쥠 변화량 1(완전히 쥐거나 완전히 펴는 것)마다 쌓이는 반죽.
         * 한 번 쥐었다 폈다 = 변화량 약 2. 부서진 바닥값 0.3에서 시작해
         * 대여섯 번 주무르면 다 섞인다. 펴는 동작도 치대는 것으로 친다.
         */
        private const val KNEAD_PER_GRIP = 0.07f

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
    private var lostSince = 0L

    /** 설정의 "손 따라 굴리기". 기본 켜짐. */
    private var rollingEnabled = true


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
        binding.arRefreshButton.setOnClickListener { loadBalls(Random.nextLong()) }
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
        val unlocked = BallCatalog.displayOrder.filter { progress.isUnlocked(it.id) }

        for (candidate in unlocked) {
            // 홈과 같은 "실제 볼 모습" 썸네일. 색 원만으로는 무슨 볼인지 안 보인다.
            val swatch = android.widget.ImageView(this).apply {
                tag = candidate.id
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(0x00000000)
                    setStroke(dp(2), if (candidate.id == spec.id) 0xFFFFFFFF.toInt() else 0x40FFFFFF)
                }
                setImageDrawable(android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(candidate.shellColor)
                })
                val pad = dp(3)
                setPadding(pad, pad, pad, pad)
                contentDescription = com.waxball.asmr.core.BallLocalization.name(this@ArPlayActivity, candidate)
                setOnClickListener { switchBall(candidate, progress) }
            }
            com.waxball.asmr.ui.BallThumbs.into(swatch, candidate, dp(40))
            val params = android.widget.LinearLayout.LayoutParams(dp(44), dp(44))
            params.marginEnd = dp(10)
            binding.arBallList.addView(swatch, params)
        }
    }

    private fun switchBall(next: BallSpec, progress: com.waxball.asmr.core.Progress) {
        if (next.id == spec.id) return
        spec = next
        audio.setProfile(spec.soundProfile())
        audio.setMaterial(spec.material.bank)
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
        audio.setMaterial(spec.material.bank)
        rollingEnabled = PrefsProgressStore(this).load().rollingOn
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
        val target = spec
        val count = ballCount
        val quality = ArLayout.qualityFor(count)

        Thread({
            // 사진은 한 번만 읽어 공 여러 개가 나눠 쓴다. 2K 지도라 볼마다 읽으면 아깝다.
            val photo = target.textureAsset?.let { name ->
                try {
                    assets.open("planets/" + name).use { android.graphics.BitmapFactory.decodeStream(it) }
                } catch (e: Exception) {
                    Log.w(TAG, "표면 지도를 못 읽음: " + name + " " + e.message)
                    null
                }
            }
            val built = ArrayList<BallScene>(count)
            for (i in 0 until count) {
                val ballSeed = seed + i * 7919L
                val base = Icosphere.build(target.baseSubdivision(quality))
                val shards = ShardSplitter.split(base, target.shardCount(quality), Random(ballSeed))
                val geometry = BallGeometry.build(shards, target.shellThickness, target.shape::warp)
                val model = BreakModel(shards, target.soundProfile(), audio.queue)
                val debris = TrappedShards(shards.size, rng = Random(ballSeed + 1))
                built.add(BallScene(target, shards, geometry, model, debris, photo))
            }
            runOnUiThread {
                scenes.clear()
                scenes.addAll(built)
                binding.arView.renderer.setScenes(built)
            }
        }, "ArBallBuilder").start()
    }

    private val offsetScratch = FloatArray(2)

    // 손 이동으로 볼을 굴릴 때 쓰는 지난 프레임 위치
    private var prevHandX = 0f
    private var prevHandY = 0f
    private var prevHandValid = false
    private var prevGrip = 0f

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

            // 쥔 만큼 볼 전체(껍질·풍선·조각)가 함께 눌리고 일렁인다.
            val grip = ((pose.squeeze - 0.08f) / 0.92f).coerceIn(0f, 1f)
            // 손바닥이 접히는 축으로 찌그러진다. 화면 y는 아래가 +라 GL로 뒤집는다.
            renderer.setSquash(grip, pose.foldX, -pose.foldY)

            // 손을 좌우·앞뒤로 움직이면 볼이 딸려 돌아간다. 쥔 채로는 거의 안 돌게
            // 눌러 둔다 — 부수는 도중에 볼이 핑핑 돌면 조준이 안 된다.
            if (prevHandValid && rollingEnabled) {
                val damp = 1f - grip * 0.8f
                renderer.rotate(
                    (pose.centerX - prevHandX) * ROLL_GAIN * damp,
                    (pose.centerY - prevHandY) * ROLL_GAIN * damp,
                )
            }
            // 쥐었다 폈다 두 방향 모두 치대는 동작이다. 쥘 때만 세면
            // "주물럭거리는데 색이 안 변한다"가 된다. 손을 다시 잡은 첫 프레임은
            // 이전 값이 낡아서 건너뛴다.
            val gripDelta = if (prevHandValid) kotlin.math.abs(grip - prevGrip) else 0f
            prevGrip = grip
            for (s in scenes) {
                if (s.model.detachedCount > 0) s.debris.addKnead(gripDelta * KNEAD_PER_GRIP)
            }

            prevHandX = pose.centerX
            prevHandY = pose.centerY
            prevHandValid = true
            for (s in scenes) s.debris.setCage(1f - 0.18f * grip)

            if (pose.force > 0f) {
                audio.markTouch()
                var broken = 0f
                // 카메라 쪽이 조금 더 세게 눌리는 치우침은 볼 좌표로 옮겨서 준다.
                // 조각 중심이 볼 좌표라, 세계 좌표 방향을 그대로 주면 치우침이 볼과 함께 돈다.
                val r = renderer.ballRotation
                val facing = Quat(-r.x, -r.y, -r.z, r.w).rotate(FACING)
                for (s in scenes) {
                    s.model.pressArea(facing, SQUEEZE_CONTACT_COS, pose.force, dt, 0f)
                    // 쥐면 풍선이 눌리고 안의 조각도 같이 밀린다.
                    s.debris.squeeze(pose.force)
                    // 주무르는 동안에는 늘 소리가 흘러야 한다. 문지름 이벤트가
                    // 깔개 녹음을 살리고, 조용할 때는 바스락 덩어리도 하나 얹는다.
                    s.model.rub(pose.force / 4f, 0f)
                    broken += DebrisSpawner.spawnFreshlyDetached(s, renderer.ballRotation)
                }
                if (broken > 0f) {
                    val magnitude = (broken / 0.03f).coerceIn(0f, 1f)
                    renderer.shake(magnitude)
                    if (magnitude > 0.35f) haptics.thud(magnitude) else haptics.pulse(0.3f + magnitude)
                } else if (scenes.any { it.model.detachedCount > 0 }) {
                    haptics.pulse(0.15f + pose.force * 0.05f)
                }
            }
        } else {
            renderer.setSquash(0f)
            renderer.hideBalls()
            prevHandValid = false
        }

        for (s in scenes) {
            // 부수는 것 자체가 치대는 것이다. 부서진 비율만큼 반죽이 바로 따라 올라
            // 첫 조각부터 색이 조금씩 섞이기 시작한다. 나머지 2/3는 계속 주물러야 한다 —
            // 0.45로 뒀더니 다 부수면 색이 벌써 반 넘게 섞여 "몇 번 더 주무르면
            // 바뀐다"는 맛이 없었다.
            s.debris.raiseKneadTo(s.model.detachedCount.toFloat() / s.shards.size * 0.3f)
            // 조각은 풍선 안에 갇혀 있다. 바닥도 착지음도 없다.
            s.debris.update(dt, s.geometry.shardCenters)
        }


        // 새 볼은 새로고침 버튼으로만 깐다. 다 부순 뒤에도 반죽 덩어리를 계속
        // 주무를 수 있어야 한다 — 진짜 왁뿌볼도 부순 다음이 본편이다.

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
                // 손 놓을 자리를 알려 주는 것이라 "손을 보여주세요"와 운명을 같이한다.
                binding.handGuide.visibility = View.VISIBLE
            }
            return
        }

        lostSince = 0L
        binding.handGuide.visibility = View.GONE
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
