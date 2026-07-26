package com.waxball.asmr.audio

import com.waxball.asmr.core.BreakModel
import com.waxball.asmr.core.EventQueue
import com.waxball.asmr.core.Icosphere
import com.waxball.asmr.core.ShardSplitter
import com.waxball.asmr.core.ShardState
import com.waxball.asmr.core.SoundProfile
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.random.Random

/**
 * 귀로 확인하기 위한 산출물을 만든다. `app/build/audition/`에 WAV가 떨어진다.
 *
 * 합성 파라미터가 수치 기준을 통과해도 실제로 왁뿌볼처럼 들리는지는 사람이 판단해야 한다.
 * 이 테스트는 그 판단 재료를 매 빌드마다 자동으로 만들어 둔다.
 */
class SynthAuditionTest {

    private val sr = 48000
    private val outDir = File("build/audition")

    @Test
    fun writeMaterialSamples() {
        outDir.mkdirs()
        val materials = listOf(
            "1-굳은왁스" to SoundProfile.hardWax(),
            "2-무른왁스" to SoundProfile.softWax(),
            "3-반짝이" to SoundProfile.glitter(),
            "4-알갱이" to SoundProfile.crunchBeads(),
            "5-설탕유리" to SoundProfile.sugarGlass(),
        )
        for ((name, profile) in materials) {
            val buf = playSession(profile, seconds = 6f, seed = 7)
            writeWav(File(outDir, "$name.wav"), buf)
            assertTrue("$name 이 무음", Spectrum.rms(buf) > 1e-4f)
        }
    }

    @Test
    fun writeGestureSamples() {
        outDir.mkdirs()
        val p = SoundProfile.hardWax()

        writeWav(File(outDir, "동작-살살탭.wav"), burst(p) { s ->
            repeat(6) { s.on(com.waxball.asmr.core.EventKind.CRACK, it, 1, 0.15f, 0f, 0.02f) }
        })
        writeWav(File(outDir, "동작-꾹누르기.wav"), burst(p) { s ->
            repeat(6) { s.on(com.waxball.asmr.core.EventKind.CRACK, it, 2, 1f, 0f, 0.02f) }
        })
        writeWav(File(outDir, "동작-큰조각분리.wav"), burst(p) { s ->
            s.on(com.waxball.asmr.core.EventKind.DETACH, 0, 4, 1f, 0f, 0.18f)
        })
        writeWav(File(outDir, "동작-코어누르기.wav"), burst(p) { s ->
            repeat(4) { s.on(com.waxball.asmr.core.EventKind.CORE, -1, 0, 0.8f, 0f, 0f) }
        })
    }

    /** 이벤트를 넣고 3초를 렌더한다. */
    private fun burst(profile: SoundProfile, feed: (Synth) -> Unit): FloatArray {
        val s = Synth(sr).apply { setProfile(profile) }
        feed(s)
        return s.renderOffline(3f)
    }

    /**
     * 실제 플레이를 흉내낸다. 손가락이 볼 위를 문지르며 조각을 차례로 깨고,
     * 떨어진 조각이 바닥에 닿는 소리까지 섞는다.
     */
    private fun playSession(profile: SoundProfile, seconds: Float, seed: Int): FloatArray {
        val queue = EventQueue(8192)
        val shards = ShardSplitter.split(Icosphere.build(3), 120, Random(seed.toLong()))
        val model = BreakModel(shards, profile, queue)
        val synth = Synth(sr).apply { setProfile(profile) }

        val frames = (seconds * sr).toInt()
        val chunk = FloatArray(512 * 2)
        val result = FloatArray(frames * 2)
        val rng = Random(seed.toLong() + 1)

        var done = 0
        var target = 0
        var elapsed = 0f
        while (done < frames) {
            val n = minOf(512, frames - done)
            val dt = n.toFloat() / sr
            elapsed += dt

            // 0.35초마다 다음 조각으로 손가락을 옮기며 문지른다.
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
    private fun writeWav(file: File, stereo: FloatArray) {
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
        le32(sr); le32(sr * 2 * 2); le16(4); le16(16)
        ascii("data"); le32(dataBytes)

        for (v in stereo) {
            val clamped = v.coerceIn(-1f, 1f)
            le16((clamped * 32767f).toInt())
        }
        file.writeBytes(out.toByteArray())

        val peak = stereo.maxOf { abs(it) }
        assertTrue("${file.name} 이 무음", peak > 1e-4f)
    }
}
