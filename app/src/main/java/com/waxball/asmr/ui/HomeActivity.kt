package com.waxball.asmr.ui

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdView
import com.waxball.asmr.R
import com.waxball.asmr.ar.ArPlayActivity
import com.waxball.asmr.core.BallCatalog
import com.waxball.asmr.core.BallLocalization
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
    private var banner: AdView? = null

    /** 놀이 화면에 들어갔다 돌아오는 중인가. 돌아올 때만 전면 광고를 고려한다. */
    private var backFromPlay = false

    private var picked: BallSpec = BallCatalog.all[0]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Insets.applyBoth(binding.homeRoot)

        store = PrefsProgressStore(this)
        progress = store.load()
        picked = pickable(progress.lastBallId)

        // 동의를 먼저 받고(유럽만 창이 뜬다) 광고를 받기 시작한다.
        Ads.start(this) {
            RewardedUnlock.load(this)
            Interstitial.load(this)
            // 배너는 자리의 폭을 알아야 크기를 정할 수 있어서 레이아웃이 끝난 뒤에 넣는다.
            binding.bannerSlot.post { banner = Ads.banner(this, binding.bannerSlot) }
        }

        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.languageButton.setOnClickListener { LanguagePicker.show(this) }
        binding.startButton.setOnClickListener {
            backFromPlay = true
            startActivity(
                Intent(this, ArPlayActivity::class.java)
                    .putExtra(ArPlayActivity.EXTRA_BALL_ID, picked.id)
            )
        }
    }

    override fun onResume() {
        super.onResume()
        progress = store.load()
        // 놀이 화면에서 볼을 바꾸거나 열었으면 그 볼로 돌아와 있어야 한다.
        picked = pickable(progress.lastBallId)
        buildBallList()
        showPicked()
        banner?.resume()

        if (backFromPlay) {
            backFromPlay = false
            Interstitial.showIfDue(this)
        }
    }

    override fun onPause() {
        banner?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        banner?.destroy()
        super.onDestroy()
    }

    private fun buildBallList() {
        binding.ballList.removeAllViews()
        for (spec in BallCatalog.displayOrder) {
            // 색 원이 아니라 손 위에 올라올 모습 그대로. 볼이 전부 텍스처를 입은 뒤로는
            // 색만 보고는 무슨 볼인지 알 수 없다.
            val open = progress.isUnlocked(spec.id)
            val thumb = ImageView(this).apply {
                tag = spec.id
                background = ring(spec.id == picked.id)
                setImageDrawable(placeholder(spec))
                val pad = dp(5)
                setPadding(pad, pad, pad, pad)
                LockedBalls.mark(this, locked = !open, badgePx = dp(22))
                val name = BallLocalization.name(this@HomeActivity, spec)
                contentDescription = if (open) name else "$name, ${getString(R.string.ball_locked)}"
                setOnClickListener {
                    if (open) choose(spec)
                    else LockedBalls.ask(this@HomeActivity, spec) {
                        progress.unlocked.add(spec.id)
                        choose(spec)   // 저장까지 한다
                    }
                }
            }
            BallThumbs.into(thumb, spec, dp(56))
            val params = LinearLayout.LayoutParams(dp(60), dp(60))
            params.marginEnd = dp(10)
            binding.ballList.addView(thumb, params)
        }
    }

    private fun ring(selected: Boolean) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(0x00000000)
        setStroke(
            if (selected) dp(3) else dp(1),
            if (selected) 0xFFE87CA0.toInt() else 0x22000000,
        )
    }

    /** 썸네일이 도착하기 전 잠깐 보이는 껍질색 원. */
    private fun placeholder(spec: BallSpec) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(spec.shellColor)
    }

    private fun choose(spec: BallSpec) {
        picked = spec
        progress.lastBallId = spec.id
        store.save(progress)
        buildBallList()
        showPicked()
    }

    /** 저장된 볼이 잠겨 있으면 무료 볼로 돌린다. 잠긴 볼로 시작하면 안 된다. */
    private fun pickable(id: Int): BallSpec =
        BallCatalog.byId(id).takeIf { progress.isUnlocked(it.id) } ?: BallCatalog.byId(BallCatalog.free[0])

    private fun showPicked() {
        binding.ballName.text = BallLocalization.name(this, picked)
        binding.ballDesc.text =
            "${BallLocalization.summary(this, picked)} · ${BallLocalization.soundDesc(this, picked)}"
        binding.ballPreview.tag = picked.id
        binding.ballPreview.setImageDrawable(placeholder(picked))
        BallThumbs.into(binding.ballPreview, picked, dp(176))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
