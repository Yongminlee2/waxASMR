package com.waxball.asmr.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
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
import com.waxball.asmr.core.Weapon
import com.waxball.asmr.databinding.ActivityPlayBinding
import com.waxball.asmr.gl.BallGeometry
import com.waxball.asmr.gl.BallScene
import com.waxball.asmr.gl.Debris
import com.waxball.asmr.gl.DebrisSpawner
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

        /** 손가락이 닿는 넓이. 구면에서 내적이 이 값 이상인 조각이 함께 눌린다. */
        private const val BRUSH_RADIUS_COS = 0.955f

        /** 부스러기를 뭉갤 때 손가락이 닿는 가로 범위(볼 좌표계). */
        private const val CRUSH_RADIUS = 0.5f
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
    private var weapon = Weapon.FINGER
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

        // 3버튼 내비게이션 기기에서 아래 버튼들과 겹치지 않게 한다.
        Insets.applyTop(binding.topBar)
        Insets.applyBottom(binding.bottomBar)

        weapon = Weapon.byId(progressState.weaponId)
        buildWeaponBar()

        binding.playView.renderer.onFrame = ::onFrame
        loadBall(Random.nextLong())
        scheduleHide()

        // 깨기와 굴리기가 영역으로 갈려서, 설명 없이는 굴리는 법을 못 찾는다.
        if (ControlsGuide.showOnce(this, progressState)) store.save(progressState)
    }

    override fun onResume() {
        super.onResume()
        binding.playView.onResume()
        audio.setProfile(spec.soundProfile())
        audio.setMaterial(spec.material.bank)
        audio.setToolBrightness(weapon.brightness)
        audio.setRawPlayback(progressState.rawPlayback)
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
                // 조각 하나만 콕 누르는 건 손가락이 아니라 바늘이다. 닿은 면적만큼 한꺼번에 누른다.
                // 찍는 도구(망치·주먹)는 문지르는 동안 계속 먹히면 안 된다.
                // 손가락을 처음 댄 순간(dt=0)만 들어간다.
                val fresh = dt <= 0f
                if (!weapon.continuous && !fresh) return

                val hit = Picker.hitDirection(origin, dir)
                if (hit == null) {
                    s.model.press(id, force * weapon.forceScale, step, pan)
                } else if (weapon.continuous) {
                    s.model.pressArea(hit, weapon.contactCos, force * weapon.forceScale, step, pan)
                } else {
                    // 타격은 시간에 비례하지 않는다. 정해진 충격량이 한꺼번에 들어간다.
                    s.model.strikeArea(hit, weapon.contactCos, weapon.strikeDamage, pan)
                }
                if (speed > 0.5f) s.model.rub(speed / 6f, pan)

                val broken = spawnFreshlyDetached()
                if (broken > 0f) reactToBreak(broken) else haptics.pulse(0.25f + force * 0.1f)
                renderer.setPress(Vec3.ZERO, 0f)
            }

            // 볼을 빗나간 손가락은 바닥에 쌓인 부스러기를 뭉갠다.
            // 다 깨고 나서도 계속 만질 거리가 남아야 한다. 영상에서 제일 재미있던 구간이다.
            id == Picker.MISS -> crushDebris(x, pan, step)

            id == Picker.CORE -> {
                gesture.coreTouches++
                s.model.squeezeCore(force / 4f, pan)
                val hit = (origin + dir.normalized() * 3f).normalized()
                renderer.setPress(hit, 0.09f + force * 0.04f)
                haptics.pulse(0.18f)
            }
        }
    }

    /**
     * 금이 번져 한꺼번에 여러 조각이 떨어질 수 있어, 상태를 훑어 새로 떨어진 것만 낙하시킨다.
     *
     * 연쇄로 떨어진 것들은 시차를 두고 놓아 준다. 동시에 우르르 사라지면 한 덩어리가
     * 지워진 것처럼 보이는데, 몇 프레임씩 어긋나면 옆으로 번져 무너지는 게 눈에 보인다.
     *
     * @return 이번에 떨어져 나간 넓이 합
     */
    private fun spawnFreshlyDetached(): Float {
        val s = scene ?: return 0f
        return DebrisSpawner.spawnFreshlyDetached(s, binding.playView.renderer.ballRotation)
    }

    /**
     * 바닥에 쌓인 부스러기를 문질러 더 잘게 부순다.
     *
     * 손가락 아래 있는 것만 부수도록 가로 위치로 고른다. 화면 어디를 눌러도 다 부서지면
     * 무더기를 비비는 느낌이 안 난다.
     */
    private fun crushDebris(screenX: Float, pan: Float, dt: Float) {
        val s = scene ?: return
        if (!s.debris.hasCrushableDebris()) return

        val width = binding.playView.width.coerceAtLeast(1)
        val worldX = ((screenX / width) * 2f - 1f) * s.spec.size.radius * 1.4f

        val crushed = s.debris.crushNear(
            worldX = worldX,
            radius = CRUSH_RADIUS,
            maxCount = 3,
            centers = s.geometry.shardCenters,
        ) { id, crunchPan, sizeHint ->
            s.model.land(id, crunchPan, sizeHint * 0.02f)
        }

        if (crushed > 0) {
            haptics.pulse(0.35f)
            binding.playView.renderer.shake(0.12f)
        }
    }

    /** 떨어진 넓이에 맞춰 화면과 손끝에 되돌려 준다. 작은 부스러기와 넓은 판이 달라야 한다. */
    private fun reactToBreak(area: Float) {
        if (area <= 0f) return
        // 조각 하나가 전체의 3%만 돼도 큰 판이다. 그 지점에서 최대가 되도록 잡는다.
        val magnitude = ((area / 0.03f) * weapon.impactScale).coerceIn(0f, 1f)
        binding.playView.renderer.shake(magnitude)
        if (magnitude > 0.35f) haptics.thud(magnitude) else haptics.pulse(0.3f + magnitude)
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

    /** 도구 고르는 줄. 화면 안에서 바로 바꿔야 이것저것 시도하게 된다. */
    private fun buildWeaponBar() {
        binding.weaponBar.removeAllViews()
        for (w in Weapon.entries) {
            val chip = TextView(this).apply {
                text = "${w.icon}\n${w.labelKo}"
                gravity = Gravity.CENTER
                textSize = 11f
                setPadding(dp(10), dp(8), dp(10), dp(8))
                setOnClickListener { selectWeapon(w) }
            }
            binding.weaponBar.addView(
                chip,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
        }
        refreshWeaponBar()
    }

    private fun selectWeapon(w: Weapon) {
        weapon = w
        progressState.weaponId = w.ordinal
        store.save(progressState)
        audio.setToolBrightness(w.brightness)
        refreshWeaponBar()
        Toast.makeText(this, "${w.icon} ${w.labelKo} — ${w.descKo}", Toast.LENGTH_SHORT).show()
        showUi()
    }

    private fun refreshWeaponBar() {
        for (i in 0 until binding.weaponBar.childCount) {
            val chip = binding.weaponBar.getChildAt(i) as TextView
            val selected = i == weapon.ordinal
            chip.background = if (selected) getDrawable(R.drawable.bg_card) else null
            chip.alpha = if (selected) 1f else 0.45f
            chip.setTextColor(
                resources.getColor(
                    if (selected) R.color.accent else R.color.text_primary, theme,
                )
            )
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

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
