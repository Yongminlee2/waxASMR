package com.waxball.asmr.ui

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.waxball.asmr.ar.ArPlayActivity
import com.waxball.asmr.core.BallCatalog
import com.waxball.asmr.core.BallSpec
import com.waxball.asmr.core.Progress
import com.waxball.asmr.databinding.ActivityHomeBinding

/**
 * 볼을 고르고 시작하는 화면.
 *
 * 앱을 켜자마자 카메라가 켜지면 어느 볼을 만질지 고를 틈이 없다. 여기서 고르고 들어간다.
 *
 * 예전에는 미션·도감·코인·조작법이 여기 다 붙어 있었는데, 손바닥 모드 하나만
 * 남기기로 하면서 전부 걷어냈다. 볼을 고르고 시작하는 것 말고는 할 일이 없다.
 */
class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var store: PrefsProgressStore
    private lateinit var progress: Progress

    private var picked: BallSpec = BallCatalog.all[0]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Insets.applyBoth(binding.homeRoot)

        store = PrefsProgressStore(this)
        progress = store.load()
        picked = BallCatalog.byId(progress.lastBallId)

        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.startButton.setOnClickListener {
            startActivity(
                Intent(this, ArPlayActivity::class.java)
                    .putExtra(ArPlayActivity.EXTRA_BALL_ID, picked.id)
            )
        }
    }

    override fun onResume() {
        super.onResume()
        progress = store.load()
        buildBallList()
        showPicked()
    }

    private fun buildBallList() {
        binding.ballList.removeAllViews()
        for (spec in BallCatalog.all) {
            val swatch = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    colors = intArrayOf(spec.shellColor, spec.fleshColor)
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    gradientRadius = dp(26).toFloat()
                    setStroke(dp(2), if (spec.id == picked.id) 0xFFFFFFFF.toInt() else 0x33FFFFFF)
                }
                contentDescription = spec.nameKo
                setOnClickListener { choose(spec) }
            }
            val params = LinearLayout.LayoutParams(dp(52), dp(52))
            params.marginEnd = dp(10)
            binding.ballList.addView(swatch, params)
        }
    }

    private fun choose(spec: BallSpec) {
        picked = spec
        progress.lastBallId = spec.id
        store.save(progress)
        buildBallList()
        showPicked()
    }

    private fun showPicked() {
        binding.ballName.text = picked.nameKo
        binding.ballDesc.text = "${picked.summaryKo} · ${picked.soundDesc}"
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
