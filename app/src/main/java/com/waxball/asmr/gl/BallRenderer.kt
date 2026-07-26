package com.waxball.asmr.gl

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import com.waxball.asmr.core.BallSpec
import com.waxball.asmr.core.BreakModel
import com.waxball.asmr.core.Icosphere
import com.waxball.asmr.core.Mat4
import com.waxball.asmr.core.Quat
import com.waxball.asmr.core.ShardSet
import com.waxball.asmr.core.ShardState
import com.waxball.asmr.core.Vec3
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.tan

/** 한 판에 필요한 것 전부. 볼이 바뀔 때 통째로 교체된다. */
class BallScene(
    val spec: BallSpec,
    val shards: ShardSet,
    val geometry: GeometryBuffers,
    val model: BreakModel,
    val debris: Debris,
)

class BallRenderer : GLSurfaceView.Renderer {

    /** GL 스레드 밖에서 넘겨받아 다음 프레임에 반영한다. */
    private val pendingScene = AtomicReference<BallScene?>(null)
    private var scene: BallScene? = null

    private var shellProgram = 0
    private var coreProgram = 0

    private var vboPos = 0
    private var vboNormal = 0
    private var vboShrink = 0
    private var vboShard = 0
    private var ibo = 0
    private var indexCount = 0

    private var coreVbo = 0
    private var coreIbo = 0
    private var coreIndexCount = 0

    private var xformTexture = 0
    private var xformData = FloatArray(0)
    private var xformBuffer = GlUtil.allocFloatBuffer(4)
    private var xformWidth = 0

    private val viewMatrix = FloatArray(16)
    private val projMatrix = FloatArray(16)
    private val viewProj = FloatArray(16)
    private val rotMatrix = FloatArray(9)

    private var viewportWidth = 1
    private var viewportHeight = 1

    /** 볼 자체의 회전. 카메라는 고정이고 볼이 구른다. */
    @Volatile var ballRotation: Quat = Quat.IDENTITY
        private set

    @Volatile var cameraDistance = 4.2f
        private set

    /** 1이 기본 거리. 핀치로만 바뀐다. */
    @Volatile private var zoomFactor = 1f

    /** 코어를 누르는 지점과 깊이. 셰이더가 그만큼 눌러 준다. */
    @Volatile private var pressPoint = Vec3.ZERO
    @Volatile private var pressAmount = 0f

    var floorY = -2.4f

    /** 마지막 프레임에서 잰 화면상 볼 반지름(픽셀). 터치 라우팅이 쓴다. */
    @Volatile var ballScreenRadius = 1f
        private set

    var onFrame: ((dt: Float) -> Unit)? = null

    private var lastFrameNs = 0L

    fun setScene(next: BallScene) {
        pendingScene.set(next)
    }

    fun rotate(dx: Float, dy: Float) {
        val yaw = Quat.axisAngle(Vec3(0f, 1f, 0f), dx)
        val pitch = Quat.axisAngle(Vec3(1f, 0f, 0f), dy)
        ballRotation = (yaw * pitch * ballRotation).normalized()
    }

    fun zoom(scale: Float) {
        if (scale <= 0f) return
        zoomFactor = (zoomFactor / scale).coerceIn(0.55f, 1.7f)
    }

    fun setPress(point: Vec3, amount: Float) {
        pressPoint = point
        pressAmount = amount.coerceIn(0f, 0.35f)
    }

    /**
     * 화면을 흔든다. 떨어져 나간 넓이에 비례해 세게 준다.
     *
     * 조각이 떨어지는데 화면이 조용하면 아무 일도 안 일어난 것처럼 느껴진다.
     * 작은 부스러기는 거의 안 흔들리고 넓은 판이 갈 때만 쿵 하도록 폭을 크게 벌린다.
     */
    fun shake(strength: Float) {
        val s = strength.coerceIn(0f, 1f)
        shakeAmount = maxOf(shakeAmount, s * MAX_SHAKE)
    }

    @Volatile private var shakeAmount = 0f
    private var shakePhase = 0f

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.027f, 0.031f, 0.043f, 1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glCullFace(GLES30.GL_BACK)

        shellProgram = GlUtil.buildProgram(Shaders.SHELL_VERTEX, Shaders.SHELL_FRAGMENT)
        coreProgram = GlUtil.buildProgram(Shaders.CORE_VERTEX, Shaders.CORE_FRAGMENT)
        buildCoreMesh()

        // 표면이 다시 만들어지면 GL 자원이 전부 날아간다. 장면을 다시 올려야 한다.
        scene?.let { pendingScene.set(it) }
        scene = null
        lastFrameNs = 0L
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = width.coerceAtLeast(1)
        viewportHeight = height.coerceAtLeast(1)
        GLES30.glViewport(0, 0, viewportWidth, viewportHeight)
        Mat4.perspective(projMatrix, FOV_DEG, viewportWidth.toFloat() / viewportHeight, 0.1f, 40f)
    }

    override fun onDrawFrame(gl: GL10?) {
        pendingScene.getAndSet(null)?.let { adopt(it) }

        val now = System.nanoTime()
        val dt = if (lastFrameNs == 0L) 0.016f else ((now - lastFrameNs) / 1e9f).coerceIn(0.001f, 0.05f)
        lastFrameNs = now
        onFrame?.invoke(dt)

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        val s = scene ?: return

        // 흔들림은 빠르게 진동하다 잦아든다. 오래 끌면 멀미가 난다.
        var shakeX = 0f
        var shakeY = 0f
        if (shakeAmount > 1e-4f) {
            shakePhase += dt * 46f
            shakeX = kotlin.math.sin(shakePhase) * shakeAmount
            shakeY = kotlin.math.cos(shakePhase * 1.37f) * shakeAmount * 0.7f
            shakeAmount *= kotlin.math.exp(-dt * 11f)
            if (shakeAmount < 1e-4f) shakeAmount = 0f
        }

        Mat4.lookAt(viewMatrix, Vec3(shakeX, shakeY, cameraDistance), Vec3(shakeX, shakeY, 0f), Vec3.UP)
        Mat4.multiply(viewProj, projMatrix, viewMatrix)

        val scale = s.spec.size.radius
        val tanHalf = tan(Math.toRadians(FOV_DEG / 2.0)).toFloat()

        // 세로 화면에서는 가로가 먼저 잘린다. 짧은 축을 기준으로 볼을 맞춰야
        // 좌우가 안 잘리고, 굴리기용 여백도 남는다.
        val aspect = viewportWidth.toFloat() / viewportHeight
        val shortAxisLimit = tanHalf * minOf(1f, aspect)
        val fitDistance = scale * 1.05f / (BALL_SCREEN_FILL * shortAxisLimit)
        cameraDistance = fitDistance * zoomFactor
        // 부스러기가 쌓이는 높이. 화면 맨 아래에 두면 도구 줄과 버튼에 가려서
        // 문질러 뭉갤 수가 없다. 볼 바로 아래, 하단 바 위쪽에 앉힌다.
        floorY = -(scale + 0.85f)

        ballScreenRadius = viewportHeight * 0.5f * (scale * 1.02f) / (cameraDistance * tanHalf)

        updateTransforms(s)
        drawShell(s, scale)
        if (s.model.shellProgress > 0.02f) drawCore(s, scale)
    }

    private fun adopt(next: BallScene) {
        releaseSceneBuffers()
        scene = next

        val g = next.geometry
        vboPos = GlUtil.createFloatBuffer(g.positions)
        vboNormal = GlUtil.createFloatBuffer(g.normals)
        vboShrink = GlUtil.createFloatBuffer(g.shrink)
        vboShard = GlUtil.createFloatBuffer(g.shardAndFace)
        ibo = GlUtil.createIndexBuffer(g.indices)
        indexCount = g.indices.size

        xformWidth = next.shards.size
        xformTexture = GlUtil.createFloatTexture(xformWidth, XFORM_ROWS)
        xformData = FloatArray(xformWidth * XFORM_ROWS * 4)
        xformBuffer = GlUtil.allocFloatBuffer(xformData.size)

        ballRotation = Quat.IDENTITY
        zoomFactor = 1f
        pressAmount = 0f
    }

    /**
     * 조각별 3x4 변환과 (수축량, 투명도)를 텍스처에 밀어 넣는다.
     * 붙어 있는 조각은 볼 회전을 그대로, 떨어진 조각은 자기 낙하 상태를 쓴다.
     */
    private fun updateTransforms(s: BallScene) {
        val n = s.shards.size
        ballRotation.toMatrix3(rotMatrix, 0)

        val rowStride = n * 4
        for (i in 0 until n) {
            val detached = s.model.state[i] >= ShardState.DETACHED && s.debris.isActive(i)

            if (detached) {
                s.debris.writeMatrix(i, s.geometry.shardCenters, scratchMatrix, 0)
                writeRow(0, i, rowStride, scratchMatrix, 0)
                writeRow(1, i, rowStride, scratchMatrix, 4)
                writeRow(2, i, rowStride, scratchMatrix, 8)
            } else {
                writeRotationRow(0, i, rowStride)
                writeRotationRow(1, i, rowStride)
                writeRotationRow(2, i, rowStride)
            }

            // 금이 갈수록 조각이 자기 중심으로 조금씩 줄어들어 틈이 벌어진다.
            val level = s.model.state[i]
            val shrink = when (level) {
                ShardState.HAIRLINE -> 0.008f
                ShardState.CRACKED -> 0.018f
                ShardState.LOOSE -> 0.032f
                // 떨어진 뒤에는 뭉갠 만큼 작아진다. 비빌수록 가루가 되어 간다.
                else -> s.debris.shrinkOf(i)
            }
            val alpha = if (s.model.state[i] >= ShardState.DETACHED && !s.debris.isActive(i)) 0f else 1f

            val base = 3 * rowStride + i * 4
            xformData[base] = shrink
            xformData[base + 1] = alpha
            xformData[base + 2] = 0f
            xformData[base + 3] = 0f
        }

        GlUtil.uploadFloatTexture(xformTexture, xformWidth, XFORM_ROWS, xformData, xformBuffer)
    }

    private val scratchMatrix = FloatArray(12)

    private fun writeRow(row: Int, shard: Int, rowStride: Int, src: FloatArray, srcOffset: Int) {
        val base = row * rowStride + shard * 4
        xformData[base] = src[srcOffset]
        xformData[base + 1] = src[srcOffset + 1]
        xformData[base + 2] = src[srcOffset + 2]
        xformData[base + 3] = src[srcOffset + 3]
    }

    private fun writeRotationRow(row: Int, shard: Int, rowStride: Int) {
        val base = row * rowStride + shard * 4
        xformData[base] = rotMatrix[row * 3]
        xformData[base + 1] = rotMatrix[row * 3 + 1]
        xformData[base + 2] = rotMatrix[row * 3 + 2]
        xformData[base + 3] = 0f
    }

    private fun drawShell(s: BallScene, scale: Float) {
        GLES30.glUseProgram(shellProgram)

        bindAttrib(vboPos, 0, 3)
        bindAttrib(vboNormal, 1, 3)
        bindAttrib(vboShrink, 2, 3)
        bindAttrib(vboShard, 3, 2)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, xformTexture)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(shellProgram, "uXform"), 0)

        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(shellProgram, "uViewProj"), 1, false, viewProj, 0)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(shellProgram, "uScale"), scale)
        GLES30.glUniform3f(
            GLES30.glGetUniformLocation(shellProgram, "uShellColor"),
            GlUtil.red(s.spec.shellColor), GlUtil.green(s.spec.shellColor), GlUtil.blue(s.spec.shellColor),
        )
        GLES30.glUniform3f(
            GLES30.glGetUniformLocation(shellProgram, "uFleshColor"),
            GlUtil.red(s.spec.fleshColor), GlUtil.green(s.spec.fleshColor), GlUtil.blue(s.spec.fleshColor),
        )
        GLES30.glUniform3f(GLES30.glGetUniformLocation(shellProgram, "uLightDir"), 0.45f, 0.8f, 0.6f)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(shellProgram, "uCamPos"), 0f, 0f, cameraDistance)

        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCount, GLES30.GL_UNSIGNED_INT, 0)

        for (i in 0 until 4) GLES30.glDisableVertexAttribArray(i)
    }

    private fun drawCore(s: BallScene, scale: Float) {
        GLES30.glUseProgram(coreProgram)
        bindAttrib(coreVbo, 0, 3)

        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(coreProgram, "uViewProj"), 1, false, viewProj, 0)
        GLES30.glUniformMatrix3fv(GLES30.glGetUniformLocation(coreProgram, "uRot"), 1, true, rotMatrix, 0)
        GLES30.glUniform1f(
            GLES30.glGetUniformLocation(coreProgram, "uRadius"),
            scale * (1f - s.spec.shellThickness - 0.03f),
        )
        GLES30.glUniform3f(
            GLES30.glGetUniformLocation(coreProgram, "uColor"),
            GlUtil.red(s.spec.coreColor), GlUtil.green(s.spec.coreColor), GlUtil.blue(s.spec.coreColor),
        )
        GLES30.glUniform3f(GLES30.glGetUniformLocation(coreProgram, "uLightDir"), 0.45f, 0.8f, 0.6f)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(coreProgram, "uCamPos"), 0f, 0f, cameraDistance)
        val p = pressPoint
        GLES30.glUniform3f(GLES30.glGetUniformLocation(coreProgram, "uPressPoint"), p.x, p.y, p.z)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(coreProgram, "uPressAmount"), pressAmount)

        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, coreIbo)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, coreIndexCount, GLES30.GL_UNSIGNED_INT, 0)
        GLES30.glDisableVertexAttribArray(0)
    }

    private fun bindAttrib(vbo: Int, location: Int, size: Int) {
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glEnableVertexAttribArray(location)
        GLES30.glVertexAttribPointer(location, size, GLES30.GL_FLOAT, false, 0, 0)
    }

    private fun buildCoreMesh() {
        val mesh = Icosphere.build(3)
        coreVbo = GlUtil.createFloatBuffer(mesh.positions)
        coreIbo = GlUtil.createIndexBuffer(mesh.indices)
        coreIndexCount = mesh.indices.size
    }

    private fun releaseSceneBuffers() {
        GlUtil.deleteBuffers(vboPos, vboNormal, vboShrink, vboShard, ibo)
        if (xformTexture != 0) GLES30.glDeleteTextures(1, intArrayOf(xformTexture), 0)
        vboPos = 0; vboNormal = 0; vboShrink = 0; vboShard = 0; ibo = 0; xformTexture = 0
    }

    /**
     * 화면 좌표를 볼 좌표계 광선으로 바꾼다. 결과는 [out]에 원점 3개 + 방향 3개.
     * GL 스레드가 아니어도 호출할 수 있게 마지막 프레임 값을 그대로 쓴다.
     */
    fun screenToRay(x: Float, y: Float, out: FloatArray) {
        val s = scene ?: return
        val scale = s.spec.size.radius
        val tanHalf = tan(Math.toRadians(FOV_DEG / 2.0)).toFloat()
        val aspect = viewportWidth.toFloat() / viewportHeight

        val ndcX = (2f * x / viewportWidth - 1f) * tanHalf * aspect
        val ndcY = (1f - 2f * y / viewportHeight) * tanHalf

        val worldOrigin = Vec3(0f, 0f, cameraDistance)
        val worldDir = Vec3(ndcX, ndcY, -1f)

        // 볼이 회전한 만큼 광선을 거꾸로 돌려 볼 좌표계로 옮긴다.
        val inv = Quat(-ballRotation.x, -ballRotation.y, -ballRotation.z, ballRotation.w)
        val o = inv.rotate(worldOrigin) * (1f / scale)
        val d = inv.rotate(worldDir)

        out[0] = o.x; out[1] = o.y; out[2] = o.z
        out[3] = d.x; out[4] = d.y; out[5] = d.z
    }

    private companion object {
        const val FOV_DEG = 42f
        const val XFORM_ROWS = 4

        /** 화면 짧은 축을 볼이 차지하는 비율. 나머지는 굴리기용 여백이다. */
        const val BALL_SCREEN_FILL = 0.82f

        /** 흔들림 최대 진폭(볼 좌표계). 이보다 크면 화면이 요동쳐서 거슬린다. */
        const val MAX_SHAKE = 0.075f
    }
}
