package com.waxball.asmr.ar

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.waxball.asmr.R
import com.waxball.asmr.databinding.ActivityArPlayBinding
import com.waxball.asmr.ui.Insets

/**
 * 손바닥 위에 볼을 올려놓고 쥐어서 부수는 화면.
 *
 * 기존 화면은 화면 속 볼을 손가락으로 문질러 깬다. 여기서는 진짜 손 위에 올려놓고
 * 진짜로 쥐어서 으스러뜨린다. 실제 왁뿌볼을 쥐는 동작 그대로다.
 *
 * 파괴·소리·볼 생성은 기존 것을 그대로 쓴다. 이 화면이 하는 일은
 * 손 좌표를 볼의 위치·크기·힘으로 번역하는 것뿐이다.
 */
class ArPlayActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BALL_ID = "ballId"
        private const val TAG = "WaxBall"
    }

    private lateinit var binding: ActivityArPlayBinding
    private lateinit var tracker: HandTracker
    private val cameraExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    /** 인식 스레드가 쓰고 화면 스레드가 읽는다. */
    @Volatile private var latestHand: HandLandmarks? = null

    private val requestCamera = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera() else refuse(R.string.ar_permission_needed)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityArPlayBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Insets.applyBottom(binding.arBottomBar)

        binding.arBackButton.setOnClickListener { finish() }

        tracker = HandTracker(this) { hand -> latestHand = hand }
        if (!tracker.ready) {
            refuse(R.string.ar_no_camera)
            return
        }

        if (hasCameraPermission()) startCamera()
        else requestCamera.launch(Manifest.permission.CAMERA)
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                val preview = Preview.Builder().build().apply {
                    setSurfaceProvider(binding.cameraPreview.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .apply { setAnalyzer(cameraExecutor) { tracker.analyze(it) } }

                provider.unbindAll()
                provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
                startHintLoop()
            } catch (e: Exception) {
                Log.e(TAG, "카메라를 열지 못함: ${e.message}")
                refuse(R.string.ar_no_camera)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /** 손이 잡히면 안내를 숨기고, 놓치면 다시 보인다. */
    private fun startHintLoop() {
        val ui = android.os.Handler(android.os.Looper.getMainLooper())
        val tick = object : Runnable {
            override fun run() {
                binding.hint.visibility = if (latestHand == null) android.view.View.VISIBLE
                else android.view.View.GONE
                ui.postDelayed(this, 200)
            }
        }
        ui.post(tick)
        hintLoop = tick
        hintHandler = ui
    }

    private var hintLoop: Runnable? = null
    private var hintHandler: android.os.Handler? = null

    override fun onDestroy() {
        super.onDestroy()
        hintLoop?.let { hintHandler?.removeCallbacks(it) }
        tracker.close()
        cameraExecutor.shutdown()
    }

    /** AR을 강요하지 않는다. 안 되면 안내하고 기존 모드로 돌려보낸다. */
    private fun refuse(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_LONG).show()
        finish()
    }
}
