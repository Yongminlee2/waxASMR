package com.waxball.asmr.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 합성 결과를 귀 대신 수치로 확인하기 위한 테스트 전용 분석기.
 * 앱에는 들어가지 않는다.
 */
object Spectrum {

    private const val N = 1024

    /** 스테레오 인터리브 버퍼에서 왼쪽 채널만 뽑는다. */
    fun left(stereo: FloatArray): FloatArray {
        val mono = FloatArray(stereo.size / 2)
        for (i in mono.indices) mono[i] = stereo[i * 2]
        return mono
    }

    fun rms(buf: FloatArray): Float {
        if (buf.isEmpty()) return 0f
        var sum = 0.0
        for (v in buf) sum += v.toDouble() * v
        return sqrt(sum / buf.size).toFloat()
    }

    /**
     * 스펙트럼 무게중심(Hz). 소리의 "밝기"를 한 숫자로 나타낸다.
     * 재질별 음색 차이를 자동 검증하는 데 쓴다.
     */
    fun centroid(stereo: FloatArray, sampleRate: Int): Float {
        val mono = left(stereo)
        if (mono.size < N) return 0f

        val window = FloatArray(N) { 0.5f - 0.5f * cos(2.0 * PI * it / (N - 1)).toFloat() }
        val re = FloatArray(N)
        val im = FloatArray(N)

        var weighted = 0.0
        var total = 0.0
        var pos = 0
        while (pos + N <= mono.size) {
            for (i in 0 until N) { re[i] = mono[pos + i] * window[i]; im[i] = 0f }
            fft(re, im)
            for (k in 1 until N / 2) {
                val mag = sqrt((re[k] * re[k] + im[k] * im[k]).toDouble())
                weighted += mag * k * sampleRate / N
                total += mag
            }
            pos += N / 2
        }
        return if (total <= 0.0) 0f else (weighted / total).toFloat()
    }

    /** 제자리 radix-2 FFT. */
    private fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j or bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wr = cos(ang).toFloat()
            val wi = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var cr = 1f; var ci = 0f
                for (k in 0 until len / 2) {
                    val ur = re[i + k]; val ui = im[i + k]
                    val vr = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                    val vi = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                    re[i + k] = ur + vr; im[i + k] = ui + vi
                    re[i + k + len / 2] = ur - vr; im[i + k + len / 2] = ui - vi
                    val ncr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
    }
}
