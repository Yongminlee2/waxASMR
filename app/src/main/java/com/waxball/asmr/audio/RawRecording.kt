package com.waxball.asmr.audio

import android.content.res.AssetManager
import android.util.Log
import java.io.DataInputStream

/**
 * 녹음을 자르지 않고 통째로 튼다. 비교용 모드다.
 *
 * 평소에는 녹음을 파편으로 잘라 흩뿌린다. 그래야 같은 소리가 반복되지 않고
 * 손가락 힘에 따라 달라진다. 다만 그 과정에서 원본과 달라질 여지가 생긴다.
 *
 * 이 모드는 그 여지를 없애고 원본을 그대로 들려준다.
 * 소리가 이상할 때 원인이 뿌리는 방식에 있는지 원본 자체에 있는지 가른다.
 *
 * 부술 때만 흐르고 멈추면 멎는다. 손을 대는 동안만 녹음이 재생되는 셈이다.
 */
class RawRecording private constructor(private val samples: ShortArray) {

    private var cursor = 0
    private var quietFrames = 0

    /** 부수는 중이라는 신호. 이벤트가 올 때마다 갱신된다. */
    @Volatile private var active = false

    val size: Int get() = samples.size

    fun keepAlive() {
        active = true
        quietFrames = 0
    }

    fun reset() {
        cursor = 0
        active = false
        quietFrames = 0
    }

    /**
     * out에 스테레오 인터리브로 더한다.
     *
     * 부수기를 멈추면 재생도 멈추되, 커서는 그대로 둔다. 다시 부수면 이어서 흐른다.
     * 매번 처음으로 되감으면 같은 앞머리만 반복해서 들린다.
     */
    fun render(out: FloatArray, frames: Int, gain: Float) {
        if (!active || samples.isEmpty()) return

        for (i in 0 until frames) {
            val s = samples[cursor] / 32768f * gain
            out[i * 2] += s
            out[i * 2 + 1] += s
            cursor++
            if (cursor >= samples.size) cursor = 0
        }

        // 한동안 새 이벤트가 없으면 멎는다.
        quietFrames += frames
        if (quietFrames > SILENCE_FRAMES) active = false
    }

    companion object {
        private const val TAG = "WaxBall"
        private const val ASSET = "original.pcm"

        /** 이 프레임 수만큼 새 이벤트가 없으면 멈춘다. 48kHz에서 약 0.15초. */
        private const val SILENCE_FRAMES = 7200

        /** 없으면 null. 호출자는 파편 방식으로 그대로 간다. */
        fun load(assets: AssetManager): RawRecording? = try {
            assets.open(ASSET).use { input ->
                val bytes = input.readBytes()
                val count = bytes.size / 2
                val samples = ShortArray(count)
                for (i in 0 until count) {
                    samples[i] = ((bytes[i * 2].toInt() and 0xFF) or (bytes[i * 2 + 1].toInt() shl 8)).toShort()
                }
                Log.i(TAG, "원본 녹음 ${count / 48000}초 로드")
                RawRecording(samples)
            }
        } catch (e: Exception) {
            Log.w(TAG, "원본 녹음을 못 읽음: ${e.message}")
            null
        }

        /** 테스트에서 직접 만들 때. */
        fun of(samples: ShortArray) = RawRecording(samples)
    }
}
