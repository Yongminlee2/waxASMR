package com.waxball.asmr.gl

import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import com.waxball.asmr.core.ShardSet
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * 사진 한 장을 조각내어 그린다.
 *
 * 붙어 있는 조각은 제자리에 그대로, 떨어진 조각은 [Debris] 가 준 변환을 따라 떨어진다.
 * 조각이 뒹굴다 뒤집히므로 **후면 컬링을 끄지 않으면 조각이 사라져 보인다.**
 *
 * 투영 행렬이 없다. 정점이 이미 클립 공간이라 사진이 화면에 1:1로 깔린다.
 */
class SmashRenderer : GLSurfaceView.Renderer {

    private var program = 0
    private var vboPos = 0
    private var vboUv = 0
    private var vboShard = 0
    private var ibo = 0
    private var indexCount = 0

    private var photoTexture = 0
    private var xformTexture = 0
    private var xformData = FloatArray(0)
    private var xformBuffer = GlUtil.allocFloatBuffer(4)
    private var shardCount = 0

    private val pendingScene = AtomicReference<Pair<ShardSet, Debris>?>(null)
    private val pendingPhoto = AtomicReference<Bitmap?>(null)

    private var set: ShardSet? = null
    private var debris: Debris? = null
    private var centers = FloatArray(0)
    private var shrink = FloatArray(0)
    private var shardCenter = FloatArray(0)

    /** 프레임마다 조각 수만큼 할당하지 않으려고 미리 잡아 둔다. */
    private val rowScratch = FloatArray(12)

    private var lastFrameNs = 0L

    var onFrame: ((dt: Float) -> Unit)? = null

    /** 부스러기가 쌓이는 높이(클립 공간). 화면 아래쪽이다. */
    val floorY = -0.92f

    fun setScene(next: ShardSet, nextDebris: Debris) {
        pendingScene.set(next to nextDebris)
    }

    fun setPhoto(bitmap: Bitmap) {
        pendingPhoto.set(bitmap)
    }

    /** 조각이 제 중심으로 오므라든 정도 0~1. 금이 벌어져 보인다. */
    fun setShrink(shardId: Int, amount: Float) {
        if (shardId in shrink.indices) shrink[shardId] = amount
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        program = GlUtil.buildProgram(SmashShaders.VERTEX, SmashShaders.FRAGMENT)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        // 조각이 뒹굴다 뒤집힌다. 컬링을 켜 두면 그때 사라진다.
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glClearColor(0f, 0f, 0f, 1f)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        pendingScene.getAndSet(null)?.let { adopt(it.first, it.second) }
        pendingPhoto.getAndSet(null)?.let { uploadPhoto(it) }

        val now = System.nanoTime()
        val dt = if (lastFrameNs == 0L) 0.016f else ((now - lastFrameNs) / 1e9f).coerceIn(0.001f, 0.05f)
        lastFrameNs = now
        onFrame?.invoke(dt)

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        val scene = set ?: return
        if (photoTexture == 0) return

        writeTransforms(scene)

        GLES30.glUseProgram(program)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, photoTexture)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "uPhoto"), 0)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, xformTexture)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "uXform"), 1)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uShardCount"), shardCount.toFloat())

        bindAttribute("aPos", vboPos, 3)
        bindAttribute("aUv", vboUv, 2)
        bindAttribute("aShard", vboShard, 1)

        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCount, GLES30.GL_UNSIGNED_INT, 0)
    }

    private fun bindAttribute(name: String, vbo: Int, size: Int) {
        val location = GLES30.glGetAttribLocation(program, name)
        if (location < 0) return
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glEnableVertexAttribArray(location)
        GLES30.glVertexAttribPointer(location, size, GLES30.GL_FLOAT, false, 0, 0)
    }

    private fun adopt(next: ShardSet, nextDebris: Debris) {
        GlUtil.deleteBuffers(vboPos, vboUv, vboShard, ibo)
        if (xformTexture != 0) GLES30.glDeleteTextures(1, intArrayOf(xformTexture), 0)

        val geometry = PlaneGeometry.build(next)
        vboPos = GlUtil.createFloatBuffer(geometry.positions)
        vboUv = GlUtil.createFloatBuffer(geometry.uvs)
        vboShard = GlUtil.createFloatBuffer(geometry.shardIds)
        ibo = GlUtil.createIndexBuffer(geometry.indices)
        indexCount = geometry.indices.size

        shardCount = next.size
        xformTexture = GlUtil.createFloatTexture(shardCount, XFORM_ROWS)
        xformData = FloatArray(shardCount * XFORM_ROWS * 4)
        xformBuffer = GlUtil.allocFloatBuffer(xformData.size)

        // Debris가 쓰는 조각 중심. 클립 공간으로 맞춘다.
        centers = FloatArray(shardCount * 3)
        shardCenter = FloatArray(shardCount * 2)
        shrink = FloatArray(shardCount)
        for (s in next.shards) {
            val cx = s.center.x * 2f - 1f
            val cy = 1f - s.center.y * 2f
            centers[s.id * 3] = cx
            centers[s.id * 3 + 1] = cy
            centers[s.id * 3 + 2] = 0f
            shardCenter[s.id * 2] = cx
            shardCenter[s.id * 2 + 1] = cy
        }

        set = next
        debris = nextDebris
    }

    private fun uploadPhoto(bitmap: Bitmap) {
        if (photoTexture != 0) GLES30.glDeleteTextures(1, intArrayOf(photoTexture), 0)
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        photoTexture = ids[0]
    }

    /**
     * 조각마다 3x4 변환 + 오므라듦·중심·불투명도를 텍스처에 밀어 넣는다.
     * 붙어 있는 조각은 단위 변환이고, 떨어진 조각은 Debris가 준 변환을 쓴다.
     */
    private fun writeTransforms(scene: ShardSet) {
        val d = debris ?: return
        val stride = shardCount * 4

        for (s in scene.shards) {
            val i = s.id
            val r0 = i * 4
            val row1 = stride + i * 4
            val row2 = stride * 2 + i * 4

            if (d.isActive(i)) {
                // writeMatrix는 3x4를 한 줄로 이어 쓴다. 행 단위 텍스처에 맞게 흩어 놓는다.
                d.writeMatrix(i, centers, rowScratch, 0)
                for (k in 0 until 4) {
                    xformData[r0 + k] = rowScratch[k]
                    xformData[row1 + k] = rowScratch[4 + k]
                    xformData[row2 + k] = rowScratch[8 + k]
                }
            } else {
                xformData[r0] = 1f; xformData[r0 + 1] = 0f; xformData[r0 + 2] = 0f; xformData[r0 + 3] = 0f
                xformData[row1] = 0f; xformData[row1 + 1] = 1f; xformData[row1 + 2] = 0f; xformData[row1 + 3] = 0f
                xformData[row2] = 0f; xformData[row2 + 1] = 0f; xformData[row2 + 2] = 1f; xformData[row2 + 3] = 0f
            }

            val meta = stride * 3 + i * 4
            xformData[meta] = shrink[i]
            xformData[meta + 1] = shardCenter[i * 2]
            xformData[meta + 2] = shardCenter[i * 2 + 1]
            xformData[meta + 3] = 1f
        }

        xformBuffer.position(0)
        xformBuffer.put(xformData).position(0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, xformTexture)
        GLES30.glTexSubImage2D(
            GLES30.GL_TEXTURE_2D, 0, 0, 0, shardCount, XFORM_ROWS,
            GLES30.GL_RGBA, GLES30.GL_FLOAT, xformBuffer,
        )
    }

    private companion object {
        const val XFORM_ROWS = 4
    }
}
