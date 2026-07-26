package com.waxball.asmr.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import android.util.Log
import com.waxball.asmr.core.EventQueue
import com.waxball.asmr.core.SoundProfile

/**
 * 합성기를 실시간으로 돌려 스피커로 내보낸다.
 *
 * ASMR 앱에서 지연은 음질보다 중요하다. 손가락 동작과 소리가 어긋나면 뇌가 가짜로
 * 인식해서 효과가 통째로 사라진다. 그래서 기기 네이티브 샘플레이트·버퍼 크기를 그대로 쓰고,
 * 오디오 루프 안에서는 객체를 하나도 만들지 않는다. GC가 콜백을 멈추면 소리가 끊긴다.
 */
class AudioEngine(context: Context) {

    companion object {
        private const val TAG = "WaxBall"
        private const val FALLBACK_SAMPLE_RATE = 48000
        private const val FALLBACK_FRAMES = 192
    }

    val sampleRate: Int
    private val framesPerBuffer: Int

    val queue = EventQueue(4096)
    private val synth: Synth

    private var track: AudioTrack? = null
    private var thread: Thread? = null
    @Volatile private var running = false
    @Volatile private var bufferGrown = false

    /** 소리가 나지 않는 상태로 동작 중인지. 초기화에 실패해도 앱은 죽지 않는다. */
    @Volatile var silent = false
        private set

    /** 추정 지연(ms). 이벤트가 오디오 스레드에 잡히기까지 + 버퍼가 재생될 때까지. */
    @Volatile var measuredLatencyMs = 0f
        private set

    private var touchMarkNs = 0L
    private var lastLatencyLogNs = 0L

    init {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        sampleRate = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull()
            ?: FALLBACK_SAMPLE_RATE
        framesPerBuffer = am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull()
            ?: FALLBACK_FRAMES
        val bank = GrainBank.load(context.assets)
        synth = Synth(sampleRate, bank = bank)
        Log.i(
            TAG,
            "오디오 sr=$sampleRate framesPerBuffer=$framesPerBuffer " +
                if (synth.usingRecordedGrains) "파편재생" else "노이즈합성",
        )
    }

    fun setProfile(p: SoundProfile) = synth.setProfile(p)

    fun setVolume(v: Float) { synth.masterGain = v.coerceIn(0f, 1f) }

    /** 터치 순간을 표시해 두면 지연을 측정할 수 있다. */
    fun markTouch() { touchMarkNs = System.nanoTime() }

    fun start() {
        if (running) return
        val t = createTrack(lowLatency = true) ?: createTrack(lowLatency = false)
        if (t == null) {
            silent = true
            Log.e(TAG, "AudioTrack 생성 실패. 무음으로 진행한다")
            return
        }
        track = t
        silent = false
        running = true
        t.play()

        thread = Thread({ loop(t) }, "WaxBallAudio").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
        track?.runCatching {
            pause()
            flush()
            release()
        }
        track = null
        synth.reset()
        queue.clear()
    }

    private fun loop(t: AudioTrack) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

        // 루프에서 쓰는 버퍼는 여기서 딱 한 번만 만든다.
        var buffer = FloatArray(framesPerBuffer * 2)
        var frames = framesPerBuffer
        var lastUnderrun = 0

        while (running) {
            if (!queue.isEmpty) {
                if (touchMarkNs != 0L) {
                    val queueDelayMs = (System.nanoTime() - touchMarkNs) / 1_000_000f
                    val bufferMs = frames * 1000f / sampleRate
                    measuredLatencyMs = queueDelayMs + bufferMs * 2f
                    touchMarkNs = 0L

                    // 손가락과 소리가 어긋나면 뇌가 가짜로 인식한다. 실측값을 남겨 둔다.
                    val now = System.nanoTime()
                    if (now - lastLatencyLogNs > 2_000_000_000L) {
                        lastLatencyLogNs = now
                        Log.i(TAG, "지연 %.1fms (큐 %.1f + 버퍼 %.1f×2)".format(measuredLatencyMs, queueDelayMs, bufferMs))
                    }
                }
                queue.drain(synth)
            }

            synth.render(buffer, frames)

            val written = t.write(buffer, 0, frames * 2, AudioTrack.WRITE_BLOCKING)
            if (written < 0) {
                Log.e(TAG, "AudioTrack.write 실패: $written")
                break
            }

            // 언더런이 계속 나면 버퍼를 한 번만 키운다. 지연은 늘지만 끊기는 것보단 낫다.
            val underruns = t.underrunCount
            if (!bufferGrown && underruns > lastUnderrun + 8) {
                bufferGrown = true
                frames = framesPerBuffer * 2
                buffer = FloatArray(frames * 2)
                Log.w(TAG, "언더런 $underruns 회. 버퍼를 $frames 프레임으로 키움")
            }
            lastUnderrun = underruns
        }
    }

    private fun createTrack(lowLatency: Boolean): AudioTrack? = try {
        val minBytes = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        val wanted = framesPerBuffer * 2 * 4 * 2   // 프레임 × 채널 × float × 2겹
        val builder = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBytes, wanted))
            .setTransferMode(AudioTrack.MODE_STREAM)

        if (lowLatency) builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)

        val t = builder.build()
        if (t.state != AudioTrack.STATE_INITIALIZED) {
            t.release()
            null
        } else {
            t
        }
    } catch (e: Exception) {
        Log.w(TAG, "AudioTrack 생성 실패 (lowLatency=$lowLatency): ${e.message}")
        null
    }
}
