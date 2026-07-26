package com.waxball.asmr.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.waxball.asmr.R
import com.waxball.asmr.audio.AudioEngine
import com.waxball.asmr.audio.Haptics
import com.waxball.asmr.core.BallCatalog
import com.waxball.asmr.core.BallSpec
import com.waxball.asmr.core.BreakModel
import com.waxball.asmr.core.Icosphere
import com.waxball.asmr.core.ShardSplitter
import com.waxball.asmr.core.ShardState
import com.waxball.asmr.core.Vec3
import com.waxball.asmr.databinding.ActivityPlayBinding
import com.waxball.asmr.gl.BallGeometry
import com.waxball.asmr.gl.BallScene
import com.waxball.asmr.gl.Debris
import com.waxball.asmr.gl.Picker
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * 플레이 화면. 모드와 상관없이 여기 하나를 쓴다.
 * 미션은 이 위에 판정기를 얹을 뿐이고 파괴·소리 경로는 건드리지 않는다.
 */
class PlayActivity : AppCompatActivity(), InputRouter.Listener {

    companion object {
        const val EXTRA_BALL_ID = "ballId"
        const val EXTRA_MODE = "mode"
        const val MODE_SANDBOX = "sandbox"
    }

    private lateinit var binding: ActivityPlayBinding
    private lateinit var audio: AudioEngine
    private lateinit var haptics: Haptics
    private lateinit var router: InputRouter

    private var spec: BallSpec = BallCatalog.all[0]
    private var scene: BallScene? = null
    private var quality = 2

    private val ray = FloatArray(6)
    private val ui = Handler(Looper.getMainLooper())
    private var nextBallPending = false
    private var uiVisible = true
    private var hideRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        spec = BallCatalog.byId(intent.getIntExtra(EXTRA_BALL_ID, 0))
        audio = AudioEngine(this)
        haptics = Haptics(this)
        router = binding.playView.attach(this)

        binding.ballName.text = spec.nameKo
        binding.backButton.setOnClickListener { finish() }
        binding.lockButton.setOnClickListener { toggleOrbitLock() }

        binding.playView.renderer.onFrame = ::onFrame
        loadBall(newSeed())
        scheduleHide()
    }

    override fun onResume() {
        super.onResume()
        binding.playView.onResume()
        audio.setProfile(spec.soundProfile())
        audio.start()
    }

    override fun onPause() {
        super.onPause()
        audio.stop()
        haptics.cancel()
        binding.playView.onPause()
    }

    // --- 볼 만들기 ---

    private fun newSeed() = Random.nextLong()

    /**
     * 조각 분할과 지오메트리 생성은 수십 밀리초가 걸린다. 화면이 멈추면 안 되니 딴 스레드에서 만든다.
     * 시드가 매번 달라서 같은 볼을 다시 깨도 갈라지는 모양이 다르다.
     */
    private fun loadBall(seed: Long) {
        nextBallPending = false
        val target = spec
        Thread {
            val base = Icosphere.build(target.baseSubdivision(quality))
            val shards = ShardSplitter.split(base, target.shardCount(quality), Random(seed))
            val geometry = BallGeometry.build(shards, target.shellThickness, target.shape::warp)
            val model = BreakModel(shards, target.soundProfile(), audio.queue)
            val debris = Debris(shards.size, Random(seed + 1))
            val next = BallScene(target, shards, geometry, model, debris)

            runOnUiThread {
                scene = next
                binding.playView.renderer.setScene(next)
                updateProgressLabel(0f)
            }
        }.apply { name = "BallBuilder" }.start()
    }

    // --- 매 프레임 ---

    private fun onFrame(dt: Float) {
        val s = scene ?: return

        s.debris.update(dt, binding.playView.renderer.floorY, s.geometry.shardCenters) { id, pan, _ ->
            s.model.land(id, pan, s.shards.shards[id].areaFrac)
        }

        val progress = s.model.shellProgress
        if (progress >= 0.999f && !nextBallPending) {
            nextBallPending = true
            ui.postDelayed({ loadBall(newSeed()) }, 2600)
        }
        ui.post { updateProgressLabel(progress) }
    }

    private fun updateProgressLabel(progress: Float) {
        binding.shellProgress.progress = (progress * 1000).roundToInt()
        binding.progressLabel.text = getString(R.string.progress_percent, (progress * 100).roundToInt())
    }

    // --- 터치 ---

    override fun onBreak(x: Float, y: Float, force: Float, speed: Float, dt: Float) {
        val s = scene ?: return
        val renderer = binding.playView.renderer

        renderer.screenToRay(x, y, ray)
        val origin = Vec3(ray[0], ray[1], ray[2])
        val dir = Vec3(ray[3], ray[4], ray[5])
        val id = Picker.pick(origin, dir, s.shards, s.model.state)

        val pan = ((x / binding.playView.width) * 2f - 1f).coerceIn(-1f, 1f)
        val step = if (dt <= 0f) 0.016f else dt

        audio.markTouch()

        when {
            id >= 0 -> {
                s.model.press(id, force, step, pan)
                if (speed > 0.5f) s.model.rub(speed / 6f, pan)
                spawnFreshlyDetached(pan)
                haptics.pulse(0.35f + force * 0.15f)
                renderer.setPress(Vec3.ZERO, 0f)
            }

            id == Picker.CORE -> {
                s.model.squeezeCore(force / 4f, pan)
                // 누른 지점이 실제로 눌리게 코어 변형 위치를 넘긴다.
                val hit = (origin + dir.normalized() * 3f).normalized()
                renderer.setPress(hit, 0.09f + force * 0.04f)
                haptics.pulse(0.2f)
            }
        }
    }

    /**
     * 금이 번져서 한꺼번에 여러 조각이 떨어질 수 있다. 상태를 훑어 새로 떨어진 것만 낙하시킨다.
     */
    private fun spawnFreshlyDetached(pan: Float) {
        val s = scene ?: return
        val rotation = binding.playView.renderer.ballRotation
        for (i in s.model.state.indices) {
            if (s.model.state[i] >= ShardState.DETACHED && !s.debris.isActive(i)) {
                s.debris.spawn(i, s.shards.shards[i].center, s.shards.shards[i].areaFrac, rotation)
            }
        }
    }

    override fun onOrbit(dx: Float, dy: Float) {
        binding.playView.renderer.rotate(dx, dy)
        showUi()
    }

    override fun onZoom(scale: Float) {
        binding.playView.renderer.zoom(scale)
    }

    override fun onRelease() {
        binding.playView.renderer.setPress(Vec3.ZERO, 0f)
        scheduleHide()
    }

    // --- UI 자동 숨김 ---

    private fun toggleOrbitLock() {
        router.orbitLocked = !router.orbitLocked
        binding.lockButton.setText(
            if (router.orbitLocked) R.string.orbit_locked else R.string.orbit_unlocked
        )
        showUi()
    }

    private fun showUi() {
        if (!uiVisible) {
            uiVisible = true
            binding.topBar.animate().alpha(1f).setDuration(160).start()
            binding.bottomBar.animate().alpha(1f).setDuration(160).start()
        }
        scheduleHide()
    }

    private fun scheduleHide() {
        hideRunnable?.let { ui.removeCallbacks(it) }
        val r = Runnable {
            uiVisible = false
            binding.topBar.animate().alpha(0.12f).setDuration(500).start()
            binding.bottomBar.animate().alpha(0.12f).setDuration(500).start()
        }
        hideRunnable = r
        ui.postDelayed(r, 3000)
    }

    override fun onDestroy() {
        super.onDestroy()
        hideRunnable?.let { ui.removeCallbacks(it) }
        ui.removeCallbacksAndMessages(null)
    }
}
