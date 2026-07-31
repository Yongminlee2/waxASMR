package com.waxball.asmr.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import com.waxball.asmr.core.BallSpec
import java.util.concurrent.Executors
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * 볼 고르기 동그라미에 쓰는 "실제 볼 모습" 썸네일.
 *
 * 전에는 껍질색 원만 그렸는데, 볼이 전부 텍스처를 입은 뒤로는 색 원만 보고
 * 무슨 볼인지 알 수 없었다. 셰이더와 같은 등장방형 UV로 앞반구를 CPU에서
 * 그대로 그려서, 고르는 동그라미가 손 위에 올라올 모습과 일치하게 한다.
 *
 * GL 셰이더의 UV: u = atan2(z,x)/(2π)+0.5, v = 0.5-asin(y)/π. 정면은 +Z.
 * 조명도 셰이더와 같은 방향(0.45, 0.8, 0.6)으로 눌러 준다.
 */
object BallThumbs {

    private val cache = HashMap<String, Bitmap>()
    private val worker = Executors.newSingleThreadExecutor { r ->
        Thread(r, "BallThumbs").apply { isDaemon = true }
    }
    private val main = Handler(Looper.getMainLooper())

    /**
     * 뷰에 썸네일을 건다. 준비 전에는 껍질색 원이 보이게 두면 된다.
     * 호출 전에 `view.tag = spec.id`를 넣어 둘 것 — 뒤늦게 도착한 그림이
     * 다른 볼로 바뀐 뷰를 덮지 않게 가려내는 데 쓴다.
     */
    fun into(view: ImageView, spec: BallSpec, sizePx: Int) {
        val key = "${spec.id}@$sizePx"
        cache[key]?.let { view.setImageBitmap(it); return }
        val appContext = view.context.applicationContext
        worker.execute {
            val bmp = render(appContext, spec, sizePx) ?: return@execute
            main.post {
                cache[key] = bmp
                if (view.tag == spec.id) view.setImageBitmap(bmp)
            }
        }
    }

    private fun render(context: Context, spec: BallSpec, sizePx: Int): Bitmap? {
        val asset = spec.textureAsset ?: return null
        val tex = try {
            context.assets.open("planets/$asset").use { input ->
                BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply {
                    inSampleSize = 2   // 2048폭 지도도 절반이면 썸네일에 충분하다
                })
            }
        } catch (e: Exception) {
            null
        } ?: return null

        val tw = tex.width
        val th = tex.height
        val texels = IntArray(tw * th)
        tex.getPixels(texels, 0, tw, 0, 0, tw, th)
        tex.recycle()

        val out = IntArray(sizePx * sizePx)
        val half = sizePx / 2f
        // 셰이더의 uLightDir와 같은 방향
        val lx = 0.45f / 1.093f
        val ly = 0.8f / 1.093f
        val lz = 0.6f / 1.093f

        for (py in 0 until sizePx) {
            for (px in 0 until sizePx) {
                val nx = (px - half + 0.5f) / half
                val ny = (half - py - 0.5f) / half
                val rr = nx * nx + ny * ny
                if (rr > 1f) continue
                val nz = sqrt(1f - rr)

                val uCoord = (atan2(nz.toDouble(), nx.toDouble()) * 0.15915494 + 0.5).toFloat()
                val vCoord = (0.5 - asin(ny.toDouble()) * 0.31830989).toFloat()
                val txp = (uCoord * tw).toInt().coerceIn(0, tw - 1)
                val typ = (vCoord * th).toInt().coerceIn(0, th - 1)
                val c = texels[typ * tw + txp]

                // 구 느낌이 나게 셰이더와 같은 방향으로 눌러 준다.
                val lambert = (nx * lx + ny * ly + nz * lz).coerceAtLeast(0f)
                val shade = 0.52f + 0.48f * lambert
                val r = (((c shr 16) and 0xFF) * shade).toInt().coerceAtMost(255)
                val g = (((c shr 8) and 0xFF) * shade).toInt().coerceAtMost(255)
                val b = ((c and 0xFF) * shade).toInt().coerceAtMost(255)
                out[py * sizePx + px] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return Bitmap.createBitmap(out, sizePx, sizePx, Bitmap.Config.ARGB_8888)
    }
}
