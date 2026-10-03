package com.waxball.asmr.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.waxball.asmr.R
import com.waxball.asmr.core.BallLocalization
import com.waxball.asmr.core.BallSpec
import com.waxball.asmr.databinding.DialogUnlockBinding

/**
 * 잠긴 볼을 어떻게 보이고, 누르면 무엇이 뜨는지. 홈과 놀이 화면이 똑같이 쓴다.
 */
object LockedBalls {

    /**
     * 잠긴 볼 썸네일은 그림만 흐리게 하고 구석에 자물쇠를 붙인다. 뷰 전체를 흐리게
     * 하면 자물쇠까지 흐려진다. 무슨 볼인지는 보여야 열고 싶어진다.
     *
     * @param badgePx 자물쇠 크기. 썸네일 크기마다 다르게 준다 (홈 22dp, 놀이 화면 18dp)
     */
    fun mark(view: ImageView, locked: Boolean, badgePx: Int) {
        if (!locked) {
            view.imageAlpha = 255
            view.foreground = null
            return
        }
        view.imageAlpha = 110
        val badge = ContextCompat.getDrawable(view.context, R.drawable.ic_lock_badge) ?: return
        view.foreground = LayerDrawable(arrayOf(badge)).apply { setLayerSize(0, badgePx, badgePx) }
        view.foregroundGravity = Gravity.BOTTOM or Gravity.END
    }

    /**
     * 잠긴 볼을 눌렀을 때 뜨는 팝업. 광고를 끝까지 보면 [onUnlocked] 를 부른다.
     *
     * 해금을 저장하는 것은 부르는 쪽이 한다. 화면마다 들고 있는 진행 상태가 따로라,
     * 여기서 저장하면 부르는 쪽이 나중에 자기 낡은 상태로 덮어써 해금이 사라진다.
     *
     * 광고가 화면을 덮는 동안 부르는 화면은 onPause 를 탔다가 돌아온다. 보상이 그보다
     * 먼저 오든 나중에 오든 부르는 쪽이 저장까지 하면 해금이 사라지지 않는다.
     */
    fun ask(activity: Activity, spec: BallSpec, onUnlocked: () -> Unit) {
        val view = DialogUnlockBinding.inflate(activity.layoutInflater)
        view.unlockName.text = BallLocalization.name(activity, spec)
        view.unlockBall.setImageDrawable(GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(spec.shellColor)
        })
        val density = activity.resources.displayMetrics.density
        BallThumbs.into(view.unlockBall, spec, (104 * density).toInt())

        val dialog = Dialog(activity).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(view.root)
            // 기본 창 배경(어두운 사각형)을 걷어내야 둥근 카드만 보인다.
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        view.unlockCancel.setOnClickListener { dialog.dismiss() }
        view.unlockWatch.setOnClickListener {
            dialog.dismiss()
            if (!RewardedUnlock.show(activity) { onUnlocked() }) {
                Toast.makeText(activity, R.string.unlock_not_ready, Toast.LENGTH_SHORT).show()
            }
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
}
