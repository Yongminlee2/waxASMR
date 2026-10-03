package com.waxball.asmr.ui

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.widget.FrameLayout
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.waxball.asmr.BuildConfig

/**
 * 광고 세 가지가 어디에 뜨는지 한곳에 모아 둔다.
 *
 * - 배너: 홈 맨 아래. 놀이 화면에는 넣지 않는다. 손을 쥐었다 폈다 하는 화면이라
 *   잘못 누르기 쉽고, 애드몹은 잘못 누른 클릭을 정책 위반으로 본다
 * - 전면: 놀이를 마치고 홈으로 돌아올 때. 공을 부수는 도중에는 뜨지 않는다
 * - 보상형: 잠긴 볼을 열 때 ([RewardedUnlock])
 *
 * 광고 ID 는 BuildConfig 에서 온다. 디버그는 구글 테스트 ID, 릴리스만 실제 ID 다.
 */
object Ads {

    /** 전면·보상형을 합쳐 이 간격 안에는 전면 광고를 다시 띄우지 않는다. */
    private const val FULL_SCREEN_GAP_MS = 3 * 60_000L

    /** 앱을 켜자마자 전면 광고가 뜨지 않도록 시작 시각부터 센다. */
    private var lastFullScreenAt = SystemClock.elapsedRealtime()

    /** 초기화는 수백 ms 걸릴 수 있어 메인 스레드를 막지 않게 따로 돌린다. */
    fun init(context: Context) {
        Thread { MobileAds.initialize(context) {} }.start()
    }

    fun markFullScreenShown() {
        lastFullScreenAt = SystemClock.elapsedRealtime()
    }

    fun fullScreenDue(): Boolean =
        SystemClock.elapsedRealtime() - lastFullScreenAt >= FULL_SCREEN_GAP_MS

    /**
     * [slot] 폭에 맞춘 적응형 배너를 넣는다. 구글이 지금 권하는 Large 크기를 쓴다(이전 함수들은 지원 종료 예정).
     * 자리의 폭을 알아야 하므로 레이아웃이 끝난 뒤에 부를 것 (`slot.post { ... }`).
     */
    fun banner(activity: Activity, slot: FrameLayout): AdView {
        val density = activity.resources.displayMetrics.density
        val widthPx = slot.width.takeIf { it > 0 } ?: activity.resources.displayMetrics.widthPixels
        return AdView(activity).apply {
            adUnitId = BuildConfig.AD_BANNER
            setAdSize(AdSize.getLargeAnchoredAdaptiveBannerAdSize(activity, (widthPx / density).toInt()))
            slot.removeAllViews()
            slot.addView(this)
            loadAd(AdRequest.Builder().build())
        }
    }
}

/**
 * 놀이를 마치고 돌아올 때 뜨는 전면 광고. 미리 하나 받아 두고, 간격이 찼을 때만 띄운다.
 */
class Interstitial(private val activity: Activity) {

    private var ad: InterstitialAd? = null
    private var loading = false

    fun load() {
        if (ad != null || loading) return
        loading = true
        InterstitialAd.load(activity, BuildConfig.AD_INTERSTITIAL, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(loaded: InterstitialAd) {
                    ad = loaded
                    loading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                }
            })
    }

    /** 간격이 찼고 받아 둔 광고가 있으면 띄운다. 없으면 다음 번을 위해 받아 둔다. */
    fun showIfDue() {
        val ready = ad ?: run { load(); return }
        if (!Ads.fullScreenDue()) return
        ad = null
        Ads.markFullScreenShown()
        ready.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = load()
            override fun onAdFailedToShowFullScreenContent(error: AdError) = load()
        }
        ready.show(activity)
    }
}

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
        RewardedAd.load(activity, BuildConfig.AD_REWARDED, AdRequest.Builder().build(),
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
        // 방금 광고를 본 사람에게 곧바로 전면 광고를 또 보여 주지 않는다.
        Ads.markFullScreenShown()
        ready.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = load()
            override fun onAdFailedToShowFullScreenContent(error: AdError) = load()
        }
        ready.show(activity) { onReward() }
        return true
    }
}
