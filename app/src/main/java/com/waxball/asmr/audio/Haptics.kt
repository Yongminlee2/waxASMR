package com.waxball.asmr.audio

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * 파열에 맞춰 짧은 진동을 쏜다. 손끝에 자글자글한 감촉이 생겨 소리만 들을 때보다
 * 훨씬 진짜 같다.
 *
 * 초당 40회를 넘기면 사람 손은 개별 진동으로 못 느끼고 그냥 웅웅거리는 소음이 된다.
 * 그래서 최소 간격을 강제한다.
 */
class Haptics(context: Context) {

    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (e: Exception) {
        null
    }

    private val supported = vibrator?.hasVibrator() == true
    private var lastPulseNs = 0L

    var enabled = true

    fun pulse(intensity: Float) {
        if (!enabled || !supported) return

        val now = System.nanoTime()
        if (now - lastPulseNs < MIN_INTERVAL_NS) return
        lastPulseNs = now

        val amplitude = (intensity.coerceIn(0f, 1f) * 200f).toInt().coerceIn(1, 255)
        try {
            vibrator?.vibrate(VibrationEffect.createOneShot(DURATION_MS, amplitude))
        } catch (e: Exception) {
            // 진동이 안 되는 기기는 그냥 무시한다. 소리가 본체다.
        }
    }

    /**
     * 큰 사건용. 간격 제한을 무시하고 길고 세게 준다.
     *
     * 넓은 판이 떨어질 때까지 자잘한 진동과 똑같으면 손끝에서는 아무 차이가 없다.
     * 이 한 방이 있어야 "큰 게 떨어졌다"가 느껴진다.
     */
    fun thud(intensity: Float) {
        if (!enabled || !supported) return
        lastPulseNs = System.nanoTime()
        val amplitude = (0.35f + intensity.coerceIn(0f, 1f) * 0.65f).let { (it * 255f).toInt() }
            .coerceIn(1, 255)
        val duration = (18L + (intensity.coerceIn(0f, 1f) * 34f).toLong())
        try {
            vibrator?.vibrate(VibrationEffect.createOneShot(duration, amplitude))
        } catch (e: Exception) {
        }
    }

    fun cancel() {
        if (!supported) return
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
        }
    }

    private companion object {
        const val MIN_INTERVAL_NS = 25_000_000L   // 초당 40회
        const val DURATION_MS = 8L
    }
}
