package com.waxball.asmr.audio

import com.waxball.asmr.core.BreakModel
import com.waxball.asmr.core.EventQueue
import com.waxball.asmr.core.Icosphere
import com.waxball.asmr.core.ShardSplitter
import com.waxball.asmr.core.ShardState
import com.waxball.asmr.core.SoundProfile
import java.io.File
import kotlin.random.Random

/**
 * 귀로 확인할 산출물을 만드는 테스트 전용 도구. 앱에는 들어가지 않는다.
 *
 * 수치가 다 통과해도 진짜 왁뿌볼처럼 들리는지는 사람이 판단해야 한다.
 */
object Audition {

    const val SAMPLE_RATE = 48000

    /**
     * 실제 플레이를 흉내낸다. 손가락이 볼 위를 옮겨다니며 조각을 깨고,
     * 문지르는 마찰음과 떨어진 조각의 착지음까지 섞는다.
     */
    fun playSession(
        profile: SoundProfile,
        seconds: Float,
        seed: Int,
        /** 실제 출시 경로(파편 재생)로 재려면 뱅크를 넘긴다. */
        bank: GrainBank? = null,
    ): FloatArray {
        val queue = EventQueue(8192)
        val shards = ShardSplitter.split(Icosphere.build(3), 120, Random(seed.toLong()))
        val model = BreakModel(shards, profile, queue)
        val synth = Synth(SAMPLE_RATE, bank = bank).apply { setProfile(profile) }

        val frames = (seconds * SAMPLE_RATE).toInt()
        val chunk = FloatArray(512 * 2)
        val result = FloatArray(frames * 2)
        val rng = Random(seed.toLong() + 1)

        var done = 0
        var target = 0
        var elapsed = 0f
        while (done < frames) {
            val n = minOf(512, frames - done)
            val dt = n.toFloat() / SAMPLE_RATE
            elapsed += dt

            // 0.35초마다 옆 조각으로 손가락을 옮기며 문지른다.
            if (elapsed > 0.35f) {
                elapsed = 0f
                target = (target + 1 + rng.nextInt(3)) % shards.size
            }
            val pan = shards.shards[target].center.x.coerceIn(-1f, 1f)
            model.press(target, 1.6f + rng.nextFloat() * 2.4f, dt, pan)
            if (rng.nextFloat() < 0.35f) model.rub(0.4f + rng.nextFloat() * 0.5f, pan)
            if (model.state[target] == ShardState.DETACHED && rng.nextFloat() < 0.25f) {
                model.land(target, pan, shards.shards[target].areaFrac)
            }

            queue.drain(synth)
            synth.render(chunk, n)
            System.arraycopy(chunk, 0, result, done * 2, n * 2)
            done += n
        }
        return result
    }

    /** 16bit 스테레오 PCM WAV. */
    fun writeWav(file: File, stereo: FloatArray) {
        val dataBytes = stereo.size * 2
        val out = java.io.ByteArrayOutputStream(44 + dataBytes)

        fun ascii(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
        fun le32(v: Int) {
            out.write(v and 0xFF); out.write((v shr 8) and 0xFF)
            out.write((v shr 16) and 0xFF); out.write((v shr 24) and 0xFF)
        }
        fun le16(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF) }

        ascii("RIFF"); le32(36 + dataBytes); ascii("WAVE")
        ascii("fmt "); le32(16); le16(1); le16(2)
        le32(SAMPLE_RATE); le32(SAMPLE_RATE * 2 * 2); le16(4); le16(16)
        ascii("data"); le32(dataBytes)

        for (v in stereo) le16((v.coerceIn(-1f, 1f) * 32767f).toInt())

        file.parentFile?.mkdirs()
        file.writeBytes(out.toByteArray())
    }
}
