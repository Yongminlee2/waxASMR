package com.waxball.asmr.ui

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.waxball.asmr.R
import com.waxball.asmr.ar.ArPlayActivity
import com.waxball.asmr.core.BallCatalog
import com.waxball.asmr.core.BallLocalization
import com.waxball.asmr.core.BallSpec
import com.waxball.asmr.core.Progress
import com.waxball.asmr.databinding.ActivityHomeBinding
import com.waxball.asmr.databinding.DialogUnlockBinding

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
    private lateinit var ads: RewardedUnlock

    private var picked: BallSpec = BallCatalog.all[0]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Insets.applyBoth(binding.homeRoot)

        store = PrefsProgressStore(this)
        progress = store.load()
        picked = pickable(progress.lastBallId)

        RewardedUnlock.init(applicationContext)
        ads = RewardedUnlock(this).also { it.load() }

        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.languageButton.setOnClickListener { LanguagePicker.show(this) }
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
        picked = pickable(picked.id)
        buildBallList()
        showPicked()
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
                // 잠긴 볼은 그림만 흐리게 하고 구석에 자물쇠를 붙인다. 뷰 전체를 흐리게 하면
                // 자물쇠까지 흐려진다. 무슨 볼인지는 보여야 열고 싶어진다.
                if (!open) {
                    imageAlpha = 110
                    foreground = ContextCompat.getDrawable(this@HomeActivity, R.drawable.ic_lock_badge)
                    foregroundGravity = Gravity.BOTTOM or Gravity.END
                }
                val name = BallLocalization.name(this@HomeActivity, spec)
                contentDescription = if (open) name else "$name, ${getString(R.string.ball_locked)}"
                setOnClickListener { if (open) choose(spec) else askUnlock(spec) }
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

    /**
     * 잠긴 볼을 눌렀을 때. 광고를 끝까지 보면 열고 바로 고른 상태로 만든다.
     *
     * 광고가 화면을 덮는 동안 이 화면은 onPause 를 탔다가 돌아오며 저장소에서 다시 읽는다.
     * 보상이 그보다 먼저 오든 나중에 오든 choose() 가 저장까지 하므로 해금이 사라지지 않는다.
     */
    private fun askUnlock(spec: BallSpec) {
        val view = DialogUnlockBinding.inflate(layoutInflater)
        view.unlockName.text = BallLocalization.name(this, spec)
        view.unlockBall.setImageDrawable(placeholder(spec))
        BallThumbs.into(view.unlockBall, spec, dp(104))

        val dialog = Dialog(this).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(view.root)
            // 기본 창 배경(어두운 사각형)을 걷어내야 둥근 카드만 보인다.
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        view.unlockCancel.setOnClickListener { dialog.dismiss() }
        view.unlockWatch.setOnClickListener {
            dialog.dismiss()
            val shown = ads.show {
                progress.unlocked.add(spec.id)
                choose(spec)
            }
            if (!shown) Toast.makeText(this, R.string.unlock_not_ready, Toast.LENGTH_SHORT).show()
        }

        // 톡 튀어나오듯 뜬다. 기기의 "애니메이션 줄이기" 설정을 따른다.
        dialog.setOnShowListener {
            view.unlockCard.apply {
                scaleX = 0.8f; scaleY = 0.8f; alpha = 0f
                animate().scaleX(1f).scaleY(1f).alpha(1f)
                    .setDuration(280).setInterpolator(OvershootInterpolator(1.8f)).start()
            }
        }
        dialog.show()
    }

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
