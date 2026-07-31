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
    /** 깨진 조각은 떨어지지 않고 고무풍선 안에 갇힌다. */
    val debris: TrappedShards,
    /** 실제 표면 지도. GL 스레드가 텍스처로 올린다. 없으면 절차적 무늬. */
    val photo: android.graphics.Bitmap? = null,
)

class BallRenderer : GLSurfaceView.Renderer {

    /**
     * GL에 올라간 볼 하나. 버퍼와 텍스처를 자기가 들고 있다.
     *
     * 여러 개를 동시에 그리려면 볼마다 자기 자원이 필요하다. 하나만 두고 매 프레임
     * 바꿔 올리면 그때마다 수십만 개 정점을 다시 보내야 해서 못 쓴다.
     */
    private class LoadedBall(val scene: BallScene) {
        var vboPos = 0
        var vboNormal = 0
        var vboShrink = 0
        var vboShard = 0
        var ibo = 0
        var indexCount = 0

        var xformTexture = 0
        var xformWidth = 0
        var xformData = FloatArray(0)
        var xformBuffer = GlUtil.allocFloatBuffer(4)

        // 풍선은 볼 모양을 따라야 한다. 각진 볼에 동그란 풍선을 씌우면
        // 안에 든 것과 겉이 따로 논다. 그래서 볼마다 warp를 먹인 메시를 만든다.
        var balloonVbo = 0
        var balloonIbo = 0
        var balloonIndexCount = 0

        /** 실제 표면 지도 텍스처. 0이면 절차적 무늬를 그린다. */
        var planetTex = 0

        /** 세계 좌표에서의 자리. 여러 개를 흩어 놓을 때 쓴다. */
        var offsetX = 0f
        var offsetY = 0f
        var offsetZ = 0f

        /** 그릴 때의 크기 배율. */
        var drawScale = 1f

        /** 손을 놓쳤을 때처럼 잠시 감출 때. */
        var visible = true

        fun upload() {
            val g = scene.geometry
            vboPos = GlUtil.createFloatBuffer(g.positions)
            vboNormal = GlUtil.createFloatBuffer(g.normals)
            vboShrink = GlUtil.createFloatBuffer(g.shrink)
            vboShard = GlUtil.createFloatBuffer(g.shardAndFace)
            ibo = GlUtil.createIndexBuffer(g.indices)
            indexCount = g.indices.size

            xformWidth = scene.shards.size
            xformTexture = GlUtil.createFloatTexture(xformWidth, XFORM_ROWS)
            xformData = FloatArray(xformWidth * XFORM_ROWS * 4)
            xformBuffer = GlUtil.allocFloatBuffer(xformData.size)
            drawScale = scene.spec.size.radius

            val sphere = Icosphere.build(3)
            val warped = FloatArray(sphere.positions.size)
            for (v in 0 until sphere.vertexCount) {
                val dir = sphere.vertex(v).normalized()
                val r = scene.spec.shape.warp(dir)
                warped[v * 3] = dir.x * r
                warped[v * 3 + 1] = dir.y * r
                warped[v * 3 + 2] = dir.z * r
            }
            balloonVbo = GlUtil.createFloatBuffer(warped)
            balloonIbo = GlUtil.createIndexBuffer(sphere.indices)
            balloonIndexCount = sphere.indices.size

            scene.photo?.let { bitmap ->
                val ids = IntArray(1)
                GLES30.glGenTextures(1, ids, 0)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0])
                // 밉맵을 만들면 경도 이음새에서 줄이 생긴다. 선형 필터만 쓴다.
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
                // 지도는 가로로 이어진다. 세로는 극에서 늘어나므로 끝을 잡는다.
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
                android.opengl.GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
                planetTex = ids[0]
            }
        }

        fun release() {
            GlUtil.deleteBuffers(vboPos, vboNormal, vboShrink, vboShard, ibo, balloonVbo, balloonIbo)
            if (xformTexture != 0) GLES30.glDeleteTextures(1, intArrayOf(xformTexture), 0)
            if (planetTex != 0) GLES30.glDeleteTextures(1, intArrayOf(planetTex), 0)
            planetTex = 0
            vboPos = 0; vboNormal = 0; vboShrink = 0; vboShard = 0; ibo = 0; xformTexture = 0
            balloonVbo = 0; balloonIbo = 0
        }
    }

    /** GL 스레드 밖에서 넘겨받아 다음 프레임에 반영한다. */
    private val pendingScenes = AtomicReference<List<BallScene>?>(null)
    private val balls = ArrayList<LoadedBall>()

    private var shellProgram = 0
    private var coreProgram = 0

    private var coreVbo = 0
    private var coreIbo = 0
    private var coreIndexCount = 0

    private val viewMatrix = FloatArray(16)
    private val projMatrix = FloatArray(16)
    private val viewProj = FloatArray(16)
    private val rotMatrix = FloatArray(9)
    private val scratchMatrix = FloatArray(12)
    private val clayScratch = FloatArray(12)

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

    /** AR 화면은 카메라 영상 위에 겹치므로 배경을 비워야 한다. */
    var transparentBackground = false

    /** AR에서는 볼을 세계 좌표에 흩어 놓는다. 일반 모드는 원점 하나뿐이다. */
    @Volatile private var arPlacement = false

    /**
     * AR 화면임을 미리 알린다. 손 위에 자리를 잡기 전까지 아무것도 그리지 않는다.
     *
     * 이게 없으면 [placeAt] 이 처음 불릴 때까지 일반 모드로 떨어져, 손을 대기도 전에
     * 볼이 화면 한가운데 큼직하게 떠 있다. 손 안내선과 겹쳐서 어디에 손을 대라는
     * 그림인지 알 수 없게 된다.
     */
    fun enableArPlacement() {
        arPlacement = true
    }

    private class Placement(var screenX: Float, var screenY: Float, var radiusPx: Float, var visible: Boolean)

    private val placements = ArrayList<Placement>()

    /** 마지막 프레임에서 잰 화면상 볼 반지름(픽셀). 터치 라우팅이 쓴다. */
    @Volatile var ballScreenRadius = 1f
        private set

    var onFrame: ((dt: Float) -> Unit)? = null

    private var lastFrameNs = 0L
    @Volatile private var shakeAmount = 0f
    private var shakePhase = 0f

    /** 용암 틈과 항성 표면이 천천히 움직이는 데 쓴다. 한 시간마다 되감아 정밀도를 지킨다. */
    private var surfaceTime = 0f

    /**
     * 쥔 정도 0~1. 깊이로 납작해지고 옆으로 살짝 부푼다.
     * 껍질·속살·풍선·갇힌 조각이 이 하나의 변환을 공유해야 한 몸으로 보인다.
     */
    @Volatile private var squashAmount = 0f

    /** 납작해지는 축(월드 XY, 정규화). 손바닥이 접히는 방향. 기본은 세로다. */
    @Volatile private var squashDirX = 0f
    @Volatile private var squashDirY = 1f

    fun setSquash(amount: Float, dirX: Float = 0f, dirY: Float = 1f) {
        squashAmount = amount.coerceIn(0f, 1f)
        val len = kotlin.math.sqrt(dirX * dirX + dirY * dirY)
        if (len > 1e-4f) {
            squashDirX = dirX / len
            squashDirY = dirY / len
        }
    }

    private val squashX: Float get() = 1f + 0.10f * squashAmount
    private val squashZ: Float get() = 1f - 0.30f * squashAmount

    fun setScene(next: BallScene) {
        pendingScenes.set(listOf(next))
    }

    /** 여러 개를 한꺼번에 올린다. AR에서 공 여러 개를 쥘 때 쓴다. */
    fun setScenes(next: List<BallScene>) {
        pendingScenes.set(next.toList())
    }

    /**
     * [index] 번째 볼을 화면의 특정 자리에 특정 크기로 놓는다. AR 모드가 쓴다.
     *
     * 볼 하나였을 때는 카메라를 옮겨서 해결했지만, 여러 개를 각자 다른 자리에 놓으려면
     * 카메라 하나로는 안 된다. 그래서 볼을 세계 좌표에 직접 놓는다.
     */
    fun placeAt(index: Int, screenX: Float, screenY: Float, radiusPx: Float) {
        arPlacement = true
        while (placements.size <= index) placements.add(Placement(0f, 0f, 100f, false))
        placements[index].apply {
            this.screenX = screenX
            this.screenY = screenY
            this.radiusPx = radiusPx.coerceAtLeast(8f)
            visible = true
        }
    }

    /**
     * 볼을 전부 감춘다.
     *
     * 손이 없는데 볼만 허공에 떠 있으면 "손 위에 올려놓은 것"이 아니라
     * 그냥 화면에 붙은 그림으로 보인다.
     */
    fun hideBalls() {
        for (p in placements) p.visible = false
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

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        if (transparentBackground) GLES30.glClearColor(0f, 0f, 0f, 0f)
        else GLES30.glClearColor(0.027f, 0.031f, 0.043f, 1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glCullFace(GLES30.GL_BACK)

        shellProgram = GlUtil.buildProgram(Shaders.SHELL_VERTEX, Shaders.SHELL_FRAGMENT)
        coreProgram = GlUtil.buildProgram(Shaders.CORE_VERTEX, Shaders.CORE_FRAGMENT)
        buildCoreMesh()

        // 표면이 다시 만들어지면 GL 자원이 전부 날아간다. 장면을 다시 올려야 한다.
        if (balls.isNotEmpty()) pendingScenes.set(balls.map { it.scene })
        balls.clear()
        lastFrameNs = 0L
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = width.coerceAtLeast(1)
        viewportHeight = height.coerceAtLeast(1)
        GLES30.glViewport(0, 0, viewportWidth, viewportHeight)
        Mat4.perspective(projMatrix, FOV_DEG, viewportWidth.toFloat() / viewportHeight, 0.1f, 40f)
    }

    override fun onDrawFrame(gl: GL10?) {
        pendingScenes.getAndSet(null)?.let { adopt(it) }

        val now = System.nanoTime()
        val dt = if (lastFrameNs == 0L) 0.016f else ((now - lastFrameNs) / 1e9f).coerceIn(0.001f, 0.05f)
        lastFrameNs = now
        surfaceTime += dt
        if (surfaceTime > 3600f) surfaceTime = 0f
        onFrame?.invoke(dt)

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        if (balls.isEmpty()) return

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

        val tanHalf = tan(Math.toRadians(FOV_DEG / 2.0)).toFloat()
        val aspect = viewportWidth.toFloat() / viewportHeight

        if (arPlacement) {
            cameraDistance = AR_CAMERA_DISTANCE
            placeForAr(tanHalf, aspect)
            // 손 위에 올라간 볼은 부스러기가 바닥에 쌓일 자리가 없다. 화면 밖으로 떨군다.
            floorY = -(AR_CAMERA_DISTANCE + 6f)
        } else {
            // 세로 화면에서는 가로가 먼저 잘린다. 짧은 축을 기준으로 볼을 맞춰야
            // 좌우가 안 잘리고, 굴리기용 여백도 남는다.
            val scale = balls[0].scene.spec.size.radius
            val shortAxisLimit = tanHalf * minOf(1f, aspect)
            val fitDistance = scale * 1.05f / (BALL_SCREEN_FILL * shortAxisLimit)
            cameraDistance = fitDistance * zoomFactor
            balls[0].drawScale = scale
            balls[0].offsetX = 0f; balls[0].offsetY = 0f; balls[0].offsetZ = 0f
            balls[0].visible = true
            // 부스러기가 쌓이는 높이. 화면 맨 아래에 두면 도구 줄과 버튼에 가려서
            // 문질러 뭉갤 수가 없다. 볼 바로 아래, 하단 바 위쪽에 앉힌다.
            floorY = -(scale + 0.85f)
            ballScreenRadius = viewportHeight * 0.5f * (scale * 1.02f) / (cameraDistance * tanHalf)
        }

        Mat4.lookAt(viewMatrix, Vec3(shakeX, shakeY, cameraDistance), Vec3(shakeX, shakeY, 0f), Vec3.UP)
        Mat4.multiply(viewProj, projMatrix, viewMatrix)

        ballRotation.toMatrix3(rotMatrix, 0)

        for (ball in balls) {
            if (!ball.visible) continue
            updateTransforms(ball)
            drawShell(ball)
            if (ball.scene.model.shellProgress > 0.02f) drawCore(ball)
            // 풍선은 반투명이라 맨 마지막에 그려야 안이 제대로 비친다.
            drawBalloon(ball)
        }
    }

    /** 화면 위치와 원하는 크기를 세계 좌표 자리와 배율로 바꾼다. */
    private fun placeForAr(tanHalf: Float, aspect: Float) {
        val halfHeight = viewportHeight * 0.5f
        for (i in balls.indices) {
            val ball = balls[i]
            val p = placements.getOrNull(i)
            if (p == null || !p.visible) { ball.visible = false; continue }

            ball.visible = true
            val ndcX = 2f * p.screenX / viewportWidth - 1f
            val ndcY = 1f - 2f * p.screenY / viewportHeight
            ball.offsetX = ndcX * AR_CAMERA_DISTANCE * tanHalf * aspect
            ball.offsetY = ndcY * AR_CAMERA_DISTANCE * tanHalf
            ball.offsetZ = 0f
            ball.drawScale = p.radiusPx * AR_CAMERA_DISTANCE * tanHalf / (halfHeight * 1.02f)

            if (i == 0) ballScreenRadius = p.radiusPx
        }
    }

    private fun adopt(next: List<BallScene>) {
        for (ball in balls) ball.release()
        balls.clear()

        for (scene in next) {
            balls.add(LoadedBall(scene).apply { upload() })
        }

        ballRotation = Quat.IDENTITY
        zoomFactor = 1f
        pressAmount = 0f
        if (placements.size > balls.size) placements.subList(balls.size, placements.size).clear()
    }

    /**
     * 조각별 3x4 변환과 (수축량, 투명도)를 텍스처에 밀어 넣는다.
     * 붙어 있는 조각은 볼 회전을 그대로, 떨어진 조각은 자기 낙하 상태를 쓴다.
     */
    private fun updateTransforms(ball: LoadedBall) {
        val s = ball.scene
        val n = s.shards.size
        val rowStride = n * 4
        val data = ball.xformData

        // 셰이더가 (M·p)에 배율을 곱하므로, 옮길 거리는 배율로 나눠서 넣어야 한다.
        val scale = ball.drawScale.coerceAtLeast(1e-4f)
        val tx = ball.offsetX / scale
        val ty = ball.offsetY / scale
        val tz = ball.offsetZ / scale

        for (i in 0 until n) {
            val detached = s.model.state[i] >= ShardState.DETACHED && s.debris.isActive(i)

            if (detached) {
                s.debris.writeMatrix(i, s.geometry.shardCenters, scratchMatrix, 0)
                writeRow(data, 0, i, rowStride, scratchMatrix, 0, tx)
                writeRow(data, 1, i, rowStride, scratchMatrix, 4, ty)
                writeRow(data, 2, i, rowStride, scratchMatrix, 8, tz)
            } else {
                writeRotationRow(data, 0, i, rowStride, tx)
                writeRotationRow(data, 1, i, rowStride, ty)
                writeRotationRow(data, 2, i, rowStride, tz)
            }

            // 금이 갈수록 조각이 자기 중심으로 조금씩 줄어들어 틈이 벌어진다.
            val knead = s.debris.knead
            // 조각은 반죽 후반에야 스며든다. 반죽 진행과 같은 속도로 지우면
            // 색이 반쯤 섞였을 때 조각이 이미 다 사라져 버린다 — 껍질 조각은
            // 색이 다 섞일 때쯤에 맞춰 마지막으로 녹아 없어져야 한다.
            val melt = ((knead - 0.55f) / 0.45f).coerceIn(0f, 1f)
            val shrink = when (s.model.state[i]) {
                ShardState.HAIRLINE -> 0.008f
                ShardState.CRACKED -> 0.018f
                ShardState.LOOSE -> 0.032f
                // 떨어진 뒤에는 치댈수록 속살에 반죽되어 작아진다.
                else -> s.debris.shrinkOf(i) + melt * 0.5f
            }
            val alpha = when {
                s.model.state[i] >= ShardState.DETACHED && !s.debris.isActive(i) -> 0f
                // 반죽 후반에 조각이 말랑이에 섞여 옅어진다. 그 색은 속살이 대신 머금는다.
                s.model.state[i] >= ShardState.DETACHED -> 1f - melt * 0.95f
                else -> 1f
            }

            val base = 3 * rowStride + i * 4
            data[base] = shrink
            data[base + 1] = alpha
            data[base + 2] = 0f
            data[base + 3] = 0f
        }

        GlUtil.uploadFloatTexture(ball.xformTexture, ball.xformWidth, XFORM_ROWS, data, ball.xformBuffer)
    }

    private fun writeRow(
        data: FloatArray,
        row: Int,
        shard: Int,
        rowStride: Int,
        src: FloatArray,
        srcOffset: Int,
        translate: Float,
    ) {
        val base = row * rowStride + shard * 4
        data[base] = src[srcOffset]
        data[base + 1] = src[srcOffset + 1]
        data[base + 2] = src[srcOffset + 2]
        data[base + 3] = src[srcOffset + 3] + translate
    }

    private fun writeRotationRow(data: FloatArray, row: Int, shard: Int, rowStride: Int, translate: Float) {
        val base = row * rowStride + shard * 4
        data[base] = rotMatrix[row * 3]
        data[base + 1] = rotMatrix[row * 3 + 1]
        data[base + 2] = rotMatrix[row * 3 + 2]
        data[base + 3] = translate
    }

    private fun drawShell(ball: LoadedBall) {
        val spec = ball.scene.spec
        GLES30.glUseProgram(shellProgram)

        bindAttrib(ball.vboPos, 0, 3)
        bindAttrib(ball.vboNormal, 1, 3)
        bindAttrib(ball.vboShrink, 2, 3)
        bindAttrib(ball.vboShard, 3, 2)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ball.xformTexture)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(shellProgram, "uXform"), 0)

        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(shellProgram, "uViewProj"), 1, false, viewProj, 0)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(shellProgram, "uScale"), ball.drawScale)
        GLES30.glUniform3f(
            GLES30.glGetUniformLocation(shellProgram, "uShellColor"),
            GlUtil.red(spec.shellColor), GlUtil.green(spec.shellColor), GlUtil.blue(spec.shellColor),
        )
        GLES30.glUniform3f(
            GLES30.glGetUniformLocation(shellProgram, "uFleshColor"),
            GlUtil.red(spec.fleshColor), GlUtil.green(spec.fleshColor), GlUtil.blue(spec.fleshColor),
        )
        GLES30.glUniform3f(
            GLES30.glGetUniformLocation(shellProgram, "uAccentColor"),
            GlUtil.red(spec.accentColor), GlUtil.green(spec.accentColor), GlUtil.blue(spec.accentColor),
        )
        GLES30.glUniform1i(GLES30.glGetUniformLocation(shellProgram, "uSurface"), spec.surface.code)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(shellProgram, "uTime"), surfaceTime)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ball.planetTex)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(shellProgram, "uPlanetTex"), 1)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(shellProgram, "uUseTex"), if (ball.planetTex != 0) 1 else 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(shellProgram, "uSquash"), squashX, squashX, squashZ)
        GLES30.glUniform2f(GLES30.glGetUniformLocation(shellProgram, "uSquashDir"), squashDirX, squashDirY)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(shellProgram, "uWobble"), squashAmount)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(shellProgram, "uVertTime"), surfaceTime)
        GLES30.glUniform3f(
            GLES30.glGetUniformLocation(shellProgram, "uCenter"),
            ball.offsetX, ball.offsetY, ball.offsetZ,
        )
        GLES30.glUniform3f(GLES30.glGetUniformLocation(shellProgram, "uLightDir"), 0.45f, 0.8f, 0.6f)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(shellProgram, "uCamPos"), 0f, 0f, cameraDistance)

        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ball.ibo)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, ball.indexCount, GLES30.GL_UNSIGNED_INT, 0)

        for (i in 0 until 4) GLES30.glDisableVertexAttribArray(i)
    }

    private fun drawCore(ball: LoadedBall) {
        val spec = ball.scene.spec
        val knead = ball.scene.debris.knead
        // 속은 겉이 아니라 반죽이다. 재료색이 여러 개면 셰이더가 마블 덩어리로 그리고,
        // 주무를수록 덩어리 경계가 풀려 한 색으로 섞인다. 단색 반죽은 속색에서 그 색으로
        // 천천히 물들 뿐이다. 흡수한 만큼 조금 자라는 것은 같다.
        val palette = spec.kneadColors
        val single =
            if (palette.size >= 2) 0
            else blendColor(spec.coreColor, palette.firstOrNull() ?: spec.coreColor, knead * 0.6f)
        drawSphere(
            ball,
            radius = ball.drawScale * (1f - spec.shellThickness - 0.03f) * (1f + 0.16f * knead),
            color = if (palette.size >= 2) palette[0] else single,
            alpha = 1f,
            // 반죽될수록 무르다. 같은 힘으로 쥐어도 더 깊게 눌리고 더 크게 출렁여야
            // "주무르는 대로 변한다"가 눈에 보인다.
            squashMul = 1f + 0.9f * knead,
            wobbleMul = 1f + 2.5f * knead,
            clayColors = if (palette.size >= 2) palette else null,
            // 소프트맥스 경계는 후반에야 확 풀린다. 조금 앞당겨야 첫 조각부터
            // 섞이는 게 보이되, 제곱근(0.5제곱)은 너무 앞당겨서 다 부수기도 전에
            // 색이 절반 넘게 변해 버렸다. 0.7제곱이 그 사이다.
            clayMix = Math.pow(knead.toDouble(), 0.7).toFloat(),
            claySeed = spec.id * 1.7f,
            // 다 섞인 뒤에도 계속 치대면 색이 조금씩 계속 달라진다.
            clayHue = (ball.scene.debris.kneadTotal - 1f).coerceAtLeast(0f) * HUE_PER_KNEAD,
        )
    }

    /** ARGB 두 색을 t만큼 섞는다. */
    private fun blendColor(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        fun ch(shift: Int): Int {
            val x = (a shr shift) and 0xFF
            val y = (b shr shift) and 0xFF
            return (x + ((y - x) * k)).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /**
     * 왁스 바깥에 씌워진 고무풍선.
     *
     * 진짜 왁뿌볼은 풍선 안에 왁스가 들어 있어서, 껍질이 깨져도 조각이 밖으로 나오지
     * 못하고 안에서 밀려다닌다. 그 껍질이 눈에 보여야 조각이 왜 안 떨어지는지 이해된다.
     *
     * 깊이 쓰기를 끄고 맨 마지막에 그린다. 켜 두면 풍선이 안의 조각을 가려 버린다.
     */
    private fun drawBalloon(ball: LoadedBall) {
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDepthMask(false)
        // 안쪽 면도 보여야 주머니처럼 보인다.
        GLES30.glDisable(GLES30.GL_CULL_FACE)

        drawSphere(
            ball,
            radius = ball.drawScale * BALLOON_SCALE,
            color = ball.scene.spec.shellColor,
            alpha = BALLOON_ALPHA,
            vbo = ball.balloonVbo,
            indexBuffer = ball.balloonIbo,
            indexCount = ball.balloonIndexCount,
        )

        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    private fun drawSphere(
        ball: LoadedBall,
        radius: Float,
        color: Int,
        alpha: Float,
        vbo: Int = coreVbo,
        indexBuffer: Int = coreIbo,
        indexCount: Int = coreIndexCount,
        squashMul: Float = 1f,
        wobbleMul: Float = 1f,
        clayColors: List<Int>? = null,
        clayMix: Float = 0f,
        claySeed: Float = 0f,
        clayHue: Float = 0f,
    ) {
        GLES30.glUseProgram(coreProgram)
        bindAttrib(vbo, 0, 3)

        // 속 반죽 마블. 풍선 등 다른 호출은 개수 0으로 지나간다.
        val clayCount = (clayColors?.size ?: 0).coerceAtMost(4)
        for (i in 0 until 4) {
            val c = if (i < clayCount) clayColors!![i] else 0
            clayScratch[i * 3] = GlUtil.red(c)
            clayScratch[i * 3 + 1] = GlUtil.green(c)
            clayScratch[i * 3 + 2] = GlUtil.blue(c)
        }
        GLES30.glUniform3fv(GLES30.glGetUniformLocation(coreProgram, "uClayColors"), 4, clayScratch, 0)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(coreProgram, "uClayCount"), clayCount)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(coreProgram, "uClayMix"), clayMix)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(coreProgram, "uClaySeed"), claySeed)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(coreProgram, "uClayHue"), clayHue)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(coreProgram, "uClayStir"), squashAmount)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(coreProgram, "uClayTime"), surfaceTime)
        // 배율은 반죽된 속살에만 1보다 크다. 납작해지는 쪽은 밑이 남게 잘라 둔다.
        val sx = 1f + 0.10f * squashAmount * squashMul
        val sz = (1f - 0.30f * squashAmount * squashMul).coerceAtLeast(0.45f)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(coreProgram, "uSquash"), sx, sx, sz)
        GLES30.glUniform2f(GLES30.glGetUniformLocation(coreProgram, "uSquashDir"), squashDirX, squashDirY)
        GLES30.glUniform1f(
            GLES30.glGetUniformLocation(coreProgram, "uWobble"),
            (squashAmount * wobbleMul).coerceAtMost(1.6f),
        )
        GLES30.glUniform1f(GLES30.glGetUniformLocation(coreProgram, "uVertTime"), surfaceTime)

        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(coreProgram, "uViewProj"), 1, false, viewProj, 0)
        GLES30.glUniformMatrix3fv(GLES30.glGetUniformLocation(coreProgram, "uRot"), 1, true, rotMatrix, 0)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(coreProgram, "uRadius"), radius)
        GLES30.glUniform3f(
            GLES30.glGetUniformLocation(coreProgram, "uOffset"),
            ball.offsetX, ball.offsetY, ball.offsetZ,
        )
        GLES30.glUniform3f(
            GLES30.glGetUniformLocation(coreProgram, "uColor"),
            GlUtil.red(color), GlUtil.green(color), GlUtil.blue(color),
        )
        GLES30.glUniform1f(GLES30.glGetUniformLocation(coreProgram, "uAlpha"), alpha)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(coreProgram, "uLightDir"), 0.45f, 0.8f, 0.6f)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(coreProgram, "uCamPos"), 0f, 0f, cameraDistance)
        val p = pressPoint
        GLES30.glUniform3f(GLES30.glGetUniformLocation(coreProgram, "uPressPoint"), p.x, p.y, p.z)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(coreProgram, "uPressAmount"), pressAmount)

        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, indexBuffer)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCount, GLES30.GL_UNSIGNED_INT, 0)
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

    /**
     * 화면 좌표를 볼 좌표계 광선으로 바꾼다. 결과는 [out]에 원점 3개 + 방향 3개.
     * GL 스레드가 아니어도 호출할 수 있게 마지막 프레임 값을 그대로 쓴다.
     */
    fun screenToRay(x: Float, y: Float, out: FloatArray) {
        val ball = balls.firstOrNull() ?: return
        val scale = ball.drawScale.coerceAtLeast(1e-4f)
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
        /**
         * 다 섞인 뒤 반죽을 한 번 더 치댈 때 색이 도는 각도(라디안).
         * 한 번 쥐었다 폈다가 약 0.14이므로, 서너 번이면 눈에 띄게 다른 색이 된다.
         */
        const val HUE_PER_KNEAD = 2.2f

        const val FOV_DEG = 42f
        const val XFORM_ROWS = 4

        /** 화면 짧은 축을 볼이 차지하는 비율. 나머지는 굴리기용 여백이다. */
        const val BALL_SCREEN_FILL = 0.82f

        /** 흔들림 최대 진폭(볼 좌표계). 이보다 크면 화면이 요동쳐서 거슬린다. */
        const val MAX_SHAKE = 0.075f

        /** AR에서 카메라를 고정해 두는 거리. 볼을 세계 좌표로 옮겨 배치한다. */
        const val AR_CAMERA_DISTANCE = 6f

        /** 고무풍선이 왁스보다 이만큼 크다. 행성이 비쳐 보이려면 딱 붙어야 한다. */
        const val BALLOON_SCALE = 1.02f

        /**
         * 풍선 기본 투명도. 가장자리에서는 셰이더가 더 진하게 만든다.
         * 0.30으로 뒀더니 뿌연 막이 행성 표면을 가렸다.
         */
        const val BALLOON_ALPHA = 0.12f
    }
}
