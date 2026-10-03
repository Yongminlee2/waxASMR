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
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.waxball.asmr.BuildConfig

/**
 * 광고 세 가지가 어디에 뜨는지 한곳에 모아 둔다.
 *
 * - 배너: 홈 맨 위. 놀이 화면에는 넣지 않는다. 손을 쥐었다 폈다 하는 화면이라
 *   잘못 누르기 쉽고, 애드몹은 잘못 누른 클릭을 정책 위반으로 본다
 * - 전면: 놀이를 마치고 홈으로 돌아올 때. 공을 부수는 도중에는 뜨지 않는다
 * - 보상형: 잠긴 볼을 열 때. 홈과 놀이 화면 양쪽에서 ([RewardedUnlock])
 *
 * 광고 ID 는 BuildConfig 에서 온다. 디버그는 구글 테스트 ID, 릴리스만 실제 ID 다.
 */
object Ads {

    /** 전면·보상형을 합쳐 이 간격 안에는 전면 광고를 다시 띄우지 않는다. */
    private const val FULL_SCREEN_GAP_MS = 3 * 60_000L

    /** 앱을 켜자마자 전면 광고가 뜨지 않도록 시작 시각부터 센다. */
    private var lastFullScreenAt = SystemClock.elapsedRealtime()

    private var initialized = false

    /** 홈 맨 위 배너의 최대 높이(dp). 일반 배너 한 줄 정도. */
    private const val BANNER_MAX_HEIGHT_DP = 60

    /**
     * 동의를 먼저 확인하고, 광고를 보내도 되면 [onReady] 를 부른다.
     *
     * 유럽(EEA·영국·스위스)은 구글 인증 동의 창(UMP)을 거쳐야 광고를 제대로 내보낼 수
     * 있다. 다른 지역에서는 창이 뜨지 않고 바로 넘어간다. 창의 문구는 애드몹 콘솔 →
     * 개인 정보 보호 및 메시지에서 만든다. 만들기 전에는 어디서도 창이 뜨지 않는다.
     *
     * 지난번에 이미 답했으면 정보 갱신을 기다리지 않고 곧바로 시작한다.
     */
    fun start(activity: Activity, onReady: () -> Unit) {
        val consent = UserMessagingPlatform.getConsentInformation(activity)
        var ready = false
        fun goIfAllowed() {
            if (ready || !consent.canRequestAds()) return
            ready = true
            if (!initialized) {
                initialized = true
                // 초기화는 수백 ms 걸릴 수 있어 메인 스레드를 막지 않게 따로 돌린다.
                val app = activity.applicationContext
                Thread { MobileAds.initialize(app) {} }.start()
            }
            onReady()
        }
        consent.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            { UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { goIfAllowed() } },
            { goIfAllowed() },
        )
        goIfAllowed()
    }

    /** 유럽 사용자처럼 동의를 다시 고칠 수 있어야 하는 경우에만 true. 설정 화면 버튼에 쓴다. */
    fun privacyOptionsRequired(context: Context): Boolean =
        UserMessagingPlatform.getConsentInformation(context).privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) {}
    }

    fun markFullScreenShown() {
        lastFullScreenAt = SystemClock.elapsedRealtime()
    }

    fun fullScreenDue(): Boolean =
        SystemClock.elapsedRealtime() - lastFullScreenAt >= FULL_SCREEN_GAP_MS

    /**
     * [slot] 폭에 맞춘 배너를 넣는다. 높이는 [BANNER_MAX_HEIGHT_DP] 까지로 묶는다.
     *
     * 처음엔 구글이 권하는 Large 고정형을 썼는데 홈 위쪽을 6분의 1쯤 차지해서 줄였다.
     * 고정형의 작은 크기 함수(현재 방향·세로용)는 SDK 25 에서 지원 종료 예정이라,
     * 높이 상한을 줄 수 있는 인라인 적응형을 쓴다.
     * 자리의 폭을 알아야 하므로 레이아웃이 끝난 뒤에 부를 것 (`slot.post { ... }`).
     */
    fun banner(activity: Activity, slot: FrameLayout): AdView {
        val density = activity.resources.displayMetrics.density
        val widthPx = slot.width.takeIf { it > 0 } ?: activity.resources.displayMetrics.widthPixels
        return AdView(activity).apply {
            adUnitId = BuildConfig.AD_BANNER
            setAdSize(AdSize.getInlineAdaptiveBannerAdSize((widthPx / density).toInt(), BANNER_MAX_HEIGHT_DP))
            slot.removeAllViews()
            slot.addView(this)
            loadAd(AdRequest.Builder().build())
        }
    }
}

/**
 * 놀이를 마치고 돌아올 때 뜨는 전면 광고. 미리 하나 받아 두고, 간격이 찼을 때만 띄운다.
 */
object Interstitial {

    private var ad: InterstitialAd? = null
    private var loading = false

    fun load(context: Context) {
        if (ad != null || loading) return
        loading = true
        InterstitialAd.load(context.applicationContext, BuildConfig.AD_INTERSTITIAL, AdRequest.Builder().build(),
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
    fun showIfDue(activity: Activity) {
        val ready = ad ?: run { load(activity); return }
        if (!Ads.fullScreenDue()) return
        ad = null
        Ads.markFullScreenShown()
        ready.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = load(activity)
            override fun onAdFailedToShowFullScreenContent(error: AdError) = load(activity)
        }
        ready.show(activity)
    }
}

/**
 * 광고를 끝까지 보면 볼 하나를 열어 주는 보상형 광고. 홈과 놀이 화면이 함께 쓴다.
 *
 * 전면 광고가 아니라 보상형이어야 한다. 구글 정책상 전면 광고에는 보상을 걸 수 없다.
 *
 * 광고는 미리 하나 받아 둔다. 누른 뒤에 받기 시작하면 몇 초씩 빈 화면을 보게 된다.
 * 화면마다 따로 받지 않고 하나를 같이 쓴다 — 따로 받으면 안 볼 광고까지 받아 온다.
 */
object RewardedUnlock {

    private var ad: RewardedAd? = null
    private var loading = false

    fun load(context: Context) {
        if (ad != null || loading) return
        loading = true
        RewardedAd.load(context.applicationContext, BuildConfig.AD_REWARDED, AdRequest.Builder().build(),
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
    fun show(activity: Activity, onReward: () -> Unit): Boolean {
        val ready = ad ?: run { load(activity); return false }
        ad = null
        // 방금 광고를 본 사람에게 곧바로 전면 광고를 또 보여 주지 않는다.
        Ads.markFullScreenShown()
        ready.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = load(activity)
            override fun onAdFailedToShowFullScreenContent(error: AdError) = load(activity)
        }
        ready.show(activity) { onReward() }
        return true
    }
}
