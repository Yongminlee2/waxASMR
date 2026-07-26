package com.waxball.asmr.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.waxball.asmr.R
import com.waxball.asmr.audio.AudioEngine
import com.waxball.asmr.audio.Haptics
import com.waxball.asmr.core.BallCatalog
import com.waxball.asmr.core.BallSpec
import com.waxball.asmr.core.BreakModel
import com.waxball.asmr.core.Gesture
import com.waxball.asmr.core.Icosphere
import com.waxball.asmr.core.Mission
import com.waxball.asmr.core.Missions
import com.waxball.asmr.core.Progress
import com.waxball.asmr.core.QualityProbe
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
 * 플레이 화면. 샌드박스든 미션이든 여기 하나를 쓴다.
 * 미션 모드는 판정기와 HUD를 얹을 뿐이고 파괴·소리 경로는 손대지 않는다.
 */
class PlayActivity : AppCompatActivity(), InputRouter.Listener {

    companion object {
        const val EXTRA_BALL_ID = "ballId"
        const val EXTRA_MISSION_ID = "missionId"
    }

    private lateinit var binding: ActivityPlayBinding
    private lateinit var audio: AudioEngine
    private lateinit var haptics: Haptics
    private lateinit var router: InputRouter
    private lateinit var store: PrefsProgressStore
    private lateinit var progressState: Progress

    private var spec: BallSpec = BallCatalog.all[0]
    private var scene: BallScene? = null
    private var mission: Mission? = null
    private val gesture = Gesture()
    private val probe = QualityProbe()

    private var quality = 1
    private var resultShown = false
    private var nextBallPending = false
    private var uiVisible = true

    private val ray = FloatArray(6)
    private val ui = Handler(Looper.getMainLooper())
    private var hideRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        store = PrefsProgressStore(this)
        progressState = store.load()

        spec = BallCatalog.byId(intent.getIntExtra(EXTRA_BALL_ID, 0))
        quality = if (progressState.qualitySetting >= 0) progressState.qualitySetting else probe.tier

        audio = AudioEngine(this)
        audio.setVolume(progressState.volume)
        haptics = Haptics(this).apply { enabled = progressState.hapticsOn }

        router = binding.playView.attach(this)
        router.orbitLocked = progressState.orbitLocked

        val missionId = intent.getIntExtra(EXTRA_MISSION_ID, -1)
        if (missionId >= 0) {
            mission = Missions.create(missionId)
            binding.missionTitle.visibility = View.VISIBLE
        }

        binding.ballName.text = spec.nameKo
        binding.backButton.setOnClickListener { finish() }
        binding.lockButton.setOnClickListener { toggleOrbitLock() }
        updateLockLabel()

        binding.playView.renderer.onFrame = ::onFrame
        loadBall(Random.nextLong())
        scheduleHide()

        // 깨기와 굴리기가 영역으로 갈려서, 설명 없이는 굴리는 법을 못 찾는다.
        ControlsGuide.showOnce(this, store)
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
        store.save(progressState)
    }

    // --- 볼 만들기 ---

    /**
     * 조각 분할과 지오메트리 생성은 수십 밀리초가 걸린다. 화면이 멈추면 안 되니 딴 스레드에서 만든다.
     * 시드가 매번 달라 같은 볼을 다시 깨도 갈라지는 모양이 다르다.
     */
    private fun loadBall(seed: Long) {
        nextBallPending = false
        resultShown = false
        gesture.reset()
        val target = spec
        val tier = quality

        Thread({
            val base = Icosphere.build(target.baseSubdivision(tier))
            val shards = ShardSplitter.split(base, target.shardCount(tier), Random(seed))
            val geometry = BallGeometry.build(shards, target.shellThickness, target.shape::warp)
            val model = BreakModel(shards, target.soundProfile(), audio.queue)
            val debris = Debris(shards.size, Random(seed + 1))
            val next = BallScene(target, shards, geometry, model, debris)

            runOnUiThread {
                scene = next
                binding.playView.renderer.setScene(next)
                updateHud(0f)
            }
        }, "BallBuilder").start()
    }

    // --- 매 프레임 (GL 스레드) ---

    private fun onFrame(dt: Float) {
        val s = scene ?: return

        if (progressState.qualitySetting < 0 && !probe.ready) {
            probe.feed(dt * 1000f)
            if (probe.ready) quality = probe.tier
        }

        s.debris.update(dt, binding.playView.renderer.floorY, s.geometry.shardCenters) { id, pan, _ ->
            s.model.land(id, pan, s.shards.shards[id].areaFrac)
        }

        mission?.update(dt, s.model, gesture)

        val shell = s.model.shellProgress
        if (shell >= 0.999f && !nextBallPending) {
            nextBallPending = true
            ui.post { onBallCleared(s) }
        }

        ui.post { updateHud(shell) }
    }

    private fun onBallCleared(s: BallScene) {
        val earned = progressState.awardForRun(s.model.detachedCount, true)
        progressState.coins += earned
        progressState.markCompleted(spec.id)
        store.save(progressState)

        val m = mission
        if (m == null) {
            // 샌드박스는 잠깐 부스러기를 감상할 시간을 주고 새 볼을 굴려 넣는다.
            ui.postDelayed({ if (!isFinishing) loadBall(Random.nextLong()) }, 2600)
        } else if (m.state == Mission.RUNNING) {
            showResult(false, earned)
        }
    }

    private fun updateHud(shell: Float) {
        if (isFinishing) return
        binding.shellProgress.progress = (shell * 1000).roundToInt()
        binding.progressLabel.text = getString(R.string.progress_percent, (shell * 100).roundToInt())

        val m = mission ?: return
        binding.missionTitle.text = if (m.timeLimitSeconds > 0f) {
            getString(R.string.mission_with_time, m.titleKo, m.remainingSeconds.roundToInt(), (m.progress * 100).roundToInt())
        } else {
            getString(R.string.mission_plain, m.titleKo, (m.progress * 100).roundToInt())
        }

        if (m.state != Mission.RUNNING && !resultShown) {
            showResult(m.state == Mission.CLEARED, if (m.state == Mission.CLEARED) m.reward else 0)
        }
    }

    private fun showResult(cleared: Boolean, reward: Int) {
        if (resultShown || isFinishing) return
        resultShown = true

        if (cleared && reward > 0) {
            progressState.coins += reward
            mission?.let { progressState.missionDone.add(it.id) }
            store.save(progressState)
        }

        AlertDialog.Builder(this)
            .setTitle(if (cleared) R.string.mission_cleared else R.string.mission_failed)
            .setMessage(
                if (cleared) getString(R.string.reward_coins, reward)
                else getString(R.string.mission_try_again)
            )
            .setPositiveButton(R.string.retry) { _, _ ->
                mission = mission?.let { Missions.create(it.id) }
                loadBall(Random.nextLong())
            }
            .setNegativeButton(R.string.back) { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    // --- 터치 ---

    override fun onBreak(x: Float, y: Float, force: Float, speed: Float, dt: Float) {
        val s = scene ?: return
        val renderer = binding.playView.renderer

        gesture.touching = true
        gesture.force = force
        gesture.speed = speed
        gesture.strokeId = router.strokeId

        renderer.screenToRay(x, y, ray)
        val origin = Vec3(ray[0], ray[1], ray[2])
        val dir = Vec3(ray[3], ray[4], ray[5])
        val id = Picker.pick(origin, dir, s.shards, s.model.state)

        val pan = ((x / binding.playView.width.coerceAtLeast(1)) * 2f - 1f).coerceIn(-1f, 1f)
        val step = if (dt <= 0f) 0.016f else dt

        audio.markTouch()

        when {
            id >= 0 -> {
                s.model.press(id, force, step, pan)
                if (speed > 0.5f) s.model.rub(speed / 6f, pan)
                spawnFreshlyDetached()
                haptics.pulse(0.3f + force * 0.15f)
                renderer.setPress(Vec3.ZERO, 0f)
            }

            id == Picker.CORE -> {
                gesture.coreTouches++
                s.model.squeezeCore(force / 4f, pan)
                val hit = (origin + dir.normalized() * 3f).normalized()
                renderer.setPress(hit, 0.09f + force * 0.04f)
                haptics.pulse(0.18f)
            }
        }
    }

    /** 금이 번져 한꺼번에 여러 조각이 떨어질 수 있어, 상태를 훑어 새로 떨어진 것만 낙하시킨다. */
    private fun spawnFreshlyDetached() {
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
        gesture.touching = false
        gesture.force = 0f
        gesture.speed = 0f
        binding.playView.renderer.setPress(Vec3.ZERO, 0f)
        scheduleHide()
    }

    // --- UI ---

    private fun toggleOrbitLock() {
        router.orbitLocked = !router.orbitLocked
        progressState.orbitLocked = router.orbitLocked
        store.save(progressState)
        updateLockLabel()
        showUi()
    }

    private fun updateLockLabel() {
        binding.lockButton.setText(
            if (router.orbitLocked) R.string.orbit_locked else R.string.orbit_unlocked
        )
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
            binding.topBar.animate().alpha(0.14f).setDuration(500).start()
            binding.bottomBar.animate().alpha(0.14f).setDuration(500).start()
        }
        hideRunnable = r
        ui.postDelayed(r, 3000)
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.playView.renderer.onFrame = null
        ui.removeCallbacksAndMessages(null)
    }
}
