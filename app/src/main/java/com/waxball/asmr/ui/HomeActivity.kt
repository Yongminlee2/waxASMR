package com.waxball.asmr.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.waxball.asmr.R
import com.waxball.asmr.core.BallCatalog
import com.waxball.asmr.core.BallSpec
import com.waxball.asmr.core.Missions
import com.waxball.asmr.core.Progress
import com.waxball.asmr.databinding.ActivityHomeBinding
import java.util.concurrent.TimeUnit

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var store: PrefsProgressStore
    private lateinit var progress: Progress

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        store = PrefsProgressStore(this)
        progress = store.load()

        binding.collectionButton.setOnClickListener {
            startActivity(Intent(this, CollectionActivity::class.java))
        }
        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        progress = store.load()
        progress.rollMissionsIfNeeded(today()) { Missions.dailyIdsFor(it) }
        store.save(progress)

        binding.coinLabel.text = getString(R.string.home_coins, progress.coins)
        buildMissions()
        buildBalls()
        maybeShowHeadphoneTip()
    }

    private fun today(): Long =
        TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis())

    /** 이어폰을 껴야 좌우 이동이 들린다. 이 앱에서 공간감은 음색만큼 중요하다. */
    private fun maybeShowHeadphoneTip() {
        if (progress.seenHeadphoneTip) return
        progress.seenHeadphoneTip = true
        store.save(progress)
        AlertDialog.Builder(this)
            .setTitle(R.string.headphone_tip_title)
            .setMessage(R.string.headphone_tip_message)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun buildMissions() {
        binding.missionList.removeAllViews()
        for (id in progress.missionIds) {
            val mission = Missions.create(id)
            val done = id in progress.missionDone

            val card = TextView(this).apply {
                text = if (done) {
                    "✓  ${mission.titleKo}"
                } else {
                    "${mission.titleKo}   +${mission.reward}"
                }
                setTextColor(if (done) resources.getColor(R.color.text_muted, theme) else resources.getColor(R.color.text_primary, theme))
                textSize = 15f
                background = getDrawable(R.drawable.bg_card)
                setPadding(dp(16), dp(14), dp(16), dp(14))
                isEnabled = !done
                alpha = if (done) 0.55f else 1f
                setOnClickListener {
                    if (!done) startPlay(pickBallForMission(), id)
                }
            }
            binding.missionList.addView(card, rowParams())
        }
    }

    /** 미션은 이미 열어 둔 볼 중에서 무작위로 하나 골라 진행한다. */
    private fun pickBallForMission(): Int =
        progress.unlocked.random()

    private fun buildBalls() {
        binding.ballList.removeAllViews()
        for (spec in BallCatalog.all) {
            binding.ballList.addView(ballCard(spec), ballParams())
        }
    }

    private fun ballCard(spec: BallSpec): View {
        val unlocked = progress.isUnlocked(spec.id)

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = getDrawable(R.drawable.bg_card)
            setPadding(dp(12), dp(14), dp(12), dp(14))
            setOnClickListener { if (unlocked) startPlay(spec.id, -1) else confirmBuy(spec) }
        }

        val swatch = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (unlocked) spec.shellColor else Color.parseColor("#20242C"))
                setStroke(dp(1), if (unlocked) spec.fleshColor else Color.parseColor("#2C323C"))
            }
        }
        column.addView(swatch, LinearLayout.LayoutParams(dp(56), dp(56)))

        column.addView(TextView(this).apply {
            text = if (unlocked) spec.nameKo else getString(R.string.collection_unknown)
            setTextColor(resources.getColor(R.color.text_primary, theme))
            textSize = 13f
            gravity = Gravity.CENTER
        }, rowParams(top = 8))

        column.addView(TextView(this).apply {
            text = if (unlocked) spec.material.labelKo else getString(R.string.locked_price, spec.price)
            setTextColor(resources.getColor(if (unlocked) R.color.text_muted else R.color.accent_warm, theme))
            textSize = 11f
            gravity = Gravity.CENTER
        }, rowParams(top = 2))

        return column
    }

    private fun confirmBuy(spec: BallSpec) {
        if (progress.coins < spec.price) {
            AlertDialog.Builder(this)
                .setMessage(R.string.not_enough_coins)
                .setPositiveButton(R.string.ok, null)
                .show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.buy_title, spec.nameKo))
            .setMessage(getString(R.string.buy_message, spec.price, progress.coins))
            .setPositiveButton(R.string.buy_confirm) { _, _ ->
                if (progress.buy(spec)) {
                    store.save(progress)
                    binding.coinLabel.text = getString(R.string.home_coins, progress.coins)
                    buildBalls()
                    startPlay(spec.id, -1)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun startPlay(ballId: Int, missionId: Int) {
        startActivity(
            Intent(this, PlayActivity::class.java)
                .putExtra(PlayActivity.EXTRA_BALL_ID, ballId)
                .putExtra(PlayActivity.EXTRA_MISSION_ID, missionId)
        )
    }

    private fun rowParams(top: Int = 8) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = dp(top) }

    private fun ballParams() = LinearLayout.LayoutParams(dp(96), LinearLayout.LayoutParams.WRAP_CONTENT)
        .apply { marginEnd = dp(10) }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
