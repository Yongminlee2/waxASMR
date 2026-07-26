package com.waxball.asmr.audio

import java.io.File

/** 분석용으로 읽어들인 소리. */
class LoadedWav(
    val samples: FloatArray,   // 모노로 합친 것
    val sampleRate: Int,
    val channels: Int,
)

/**
 * 16/24/32bit PCM과 32bit float WAV를 읽는다. 테스트 전용이고 앱에는 들어가지 않는다.
 *
 * 실제 왁뿌볼을 녹음한 파일에서 합성 파라미터를 뽑기 위한 도구다.
 */
object WavReader {

    fun read(file: File): LoadedWav {
        val bytes = file.readBytes()
        require(bytes.size > 44) { "${file.name}: 너무 짧다" }
        require(String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF") { "${file.name}: RIFF가 아니다" }
        require(String(bytes, 8, 4, Charsets.US_ASCII) == "WAVE") { "${file.name}: WAVE가 아니다" }

        var pos = 12
        var format = 1
        var channels = 1
        var sampleRate = 48000
        var bits = 16
        var dataStart = -1
        var dataLength = 0

        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4, Charsets.US_ASCII)
            val size = le32(bytes, pos + 4)
            val body = pos + 8
            when (id) {
                "fmt " -> {
                    format = le16(bytes, body)
                    channels = le16(bytes, body + 2)
                    sampleRate = le32(bytes, body + 4)
                    bits = le16(bytes, body + 14)
                }
                "data" -> {
                    dataStart = body
                    dataLength = size
                }
            }
            pos = body + size + (size and 1)   // 청크는 짝수 바이트 정렬
            if (dataStart >= 0 && id == "data") break
        }

        require(dataStart >= 0) { "${file.name}: data 청크가 없다" }
        require(format == 1 || format == 3) { "${file.name}: 압축된 WAV는 못 읽는다(format=$format)" }

        val bytesPerSample = bits / 8
        val frames = dataLength / (bytesPerSample * channels)
        val mono = FloatArray(frames)

        for (f in 0 until frames) {
            var sum = 0f
            for (c in 0 until channels) {
                val at = dataStart + (f * channels + c) * bytesPerSample
                if (at + bytesPerSample > bytes.size) break
                sum += when {
                    format == 3 && bits == 32 -> Float.fromBits(le32(bytes, at))
                    bits == 16 -> le16Signed(bytes, at) / 32768f
                    bits == 24 -> le24Signed(bytes, at) / 8388608f
                    bits == 32 -> le32(bytes, at) / 2147483648f
                    bits == 8 -> ((bytes[at].toInt() and 0xFF) - 128) / 128f
                    else -> 0f
                }
            }
            mono[f] = sum / channels
        }

        return LoadedWav(mono, sampleRate, channels)
    }

    private fun le16(b: ByteArray, at: Int) =
        (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)

    private fun le16Signed(b: ByteArray, at: Int) = le16(b, at).toShort().toInt()

    private fun le24Signed(b: ByteArray, at: Int): Int {
        val v = (b[at].toInt() and 0xFF) or
            ((b[at + 1].toInt() and 0xFF) shl 8) or
            ((b[at + 2].toInt() and 0xFF) shl 16)
        return if (v and 0x800000 != 0) v or -0x1000000 else v
    }

    private fun le32(b: ByteArray, at: Int) =
        (b[at].toInt() and 0xFF) or
            ((b[at + 1].toInt() and 0xFF) shl 8) or
            ((b[at + 2].toInt() and 0xFF) shl 16) or
            ((b[at + 3].toInt() and 0xFF) shl 24)
}
