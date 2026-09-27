package com.waxball.asmr.ui

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * 광고를 끝까지 보면 볼 하나를 열어 주는 보상형 광고.
 *
 * 전면 광고가 아니라 보상형이어야 한다. 구글 정책상 전면 광고에는 보상을 걸 수 없다.
 *
 * 광고는 미리 하나 받아 둔다. 누른 뒤에 받기 시작하면 몇 초씩 빈 화면을 보게 된다.
 * 하나를 보여 주면 곧바로 다음 것을 받아 둔다.
 */
class RewardedUnlock(private val activity: Activity) {

    private var ad: RewardedAd? = null
    private var loading = false

    fun load() {
        if (ad != null || loading) return
        loading = true
        RewardedAd.load(activity, UNIT_ID, AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(loaded: RewardedAd) {
                    ad = loaded
                    loading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                }
            })
    }

    /**
     * 광고를 보여 주고, 끝까지 보면 [onReward] 를 부른다.
     * 아직 받아 둔 광고가 없으면 받기 시작하고 false 를 돌려준다.
     */
    fun show(onReward: () -> Unit): Boolean {
        val ready = ad ?: run { load(); return false }
        ad = null
        ready.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = load()
            override fun onAdFailedToShowFullScreenContent(error: AdError) = load()
        }
        ready.show(activity) { onReward() }
        return true
    }

    companion object {
        /** 구글이 공개한 보상형 테스트 광고 단위. 출시 전에 애드몹 콘솔의 실제 ID로 바꿀 것. */
        private const val UNIT_ID = "ca-app-pub-3940256099942544/5224354917"

        /** 초기화는 수백 ms 걸릴 수 있어 메인 스레드를 막지 않게 따로 돌린다. */
        fun init(context: Context) {
            Thread { MobileAds.initialize(context) {} }.start()
        }
    }
}
