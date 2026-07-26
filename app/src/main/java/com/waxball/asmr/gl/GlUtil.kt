package com.waxball.asmr.gl

import android.opengl.GLES30
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer

object GlUtil {

    private const val TAG = "WaxBall"

    fun buildProgram(vertexSrc: String, fragmentSrc: String): Int {
        val vs = compile(GLES30.GL_VERTEX_SHADER, vertexSrc)
        val fs = compile(GLES30.GL_FRAGMENT_SHADER, fragmentSrc)
        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vs)
        GLES30.glAttachShader(program, fs)
        GLES30.glLinkProgram(program)

        val status = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(program)
            GLES30.glDeleteProgram(program)
            throw RuntimeException("셰이더 링크 실패: $log")
        }
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
        return program
    }

    private fun compile(type: Int, src: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, src)
        GLES30.glCompileShader(shader)

        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            throw RuntimeException("셰이더 컴파일 실패: $log")
        }
        return shader
    }

    fun createFloatBuffer(data: FloatArray, target: Int = GLES30.GL_ARRAY_BUFFER): Int {
        val ids = IntArray(1)
        GLES30.glGenBuffers(1, ids, 0)
        val buffer = ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        buffer.put(data).position(0)
        GLES30.glBindBuffer(target, ids[0])
        GLES30.glBufferData(target, data.size * 4, buffer, GLES30.GL_STATIC_DRAW)
        return ids[0]
    }

    fun createIndexBuffer(data: IntArray): Int {
        val ids = IntArray(1)
        GLES30.glGenBuffers(1, ids, 0)
        val buffer = ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder()).asIntBuffer()
        buffer.put(data).position(0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ids[0])
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, data.size * 4, buffer, GLES30.GL_STATIC_DRAW)
        return ids[0]
    }

    fun deleteBuffers(vararg ids: Int) {
        val live = ids.filter { it != 0 }.toIntArray()
        if (live.isNotEmpty()) GLES30.glDeleteBuffers(live.size, live, 0)
    }

    /**
     * 조각별 변환을 담을 부동소수점 텍스처.
     * 유니폼 배열은 ES 3.0이 224 vec4까지만 보장해서 조각 300개를 못 담는다.
     * 텍스처로 넘기면 개수 제한에서 자유롭고 갱신도 한 번에 끝난다.
     */
    fun createFloatTexture(width: Int, height: Int): Int {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA32F, width, height, 0,
            GLES30.GL_RGBA, GLES30.GL_FLOAT, null,
        )
        return ids[0]
    }

    fun uploadFloatTexture(texture: Int, width: Int, height: Int, data: FloatArray, buffer: FloatBuffer) {
        buffer.position(0)
        buffer.put(data, 0, width * height * 4)
        buffer.position(0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        GLES30.glTexSubImage2D(
            GLES30.GL_TEXTURE_2D, 0, 0, 0, width, height,
            GLES30.GL_RGBA, GLES30.GL_FLOAT, buffer,
        )
    }

    fun allocFloatBuffer(floats: Int): FloatBuffer =
        ByteBuffer.allocateDirect(floats * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

    fun supportsEs3(): Boolean {
        val version = GLES30.glGetString(GLES30.GL_VERSION) ?: return false
        return version.contains("OpenGL ES 3")
    }

    fun checkError(where: String) {
        var err = GLES30.glGetError()
        while (err != GLES30.GL_NO_ERROR) {
            Log.e(TAG, "GL 오류 0x${Integer.toHexString(err)} ($where)")
            err = GLES30.glGetError()
        }
    }

    fun red(color: Int) = ((color shr 16) and 0xFF) / 255f
    fun green(color: Int) = ((color shr 8) and 0xFF) / 255f
    fun blue(color: Int) = (color and 0xFF) / 255f

    @Suppress("unused")
    private val unusedIntBuffer: IntBuffer? = null
}
