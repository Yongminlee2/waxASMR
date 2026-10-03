package com.waxball.asmr.ui

import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.snackbar.Snackbar
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.waxball.asmr.R

/**
 * 플레이스토어에 새 버전이 있으면 앱 안에서 받게 한다 (유연한 업데이트).
 *
 * 구글 플레이 창이 떠서 업데이트할지 묻고, 받는 동안에도 앱을 계속 쓸 수 있다. 다 받으면
 * 아래에 "다시 시작"이 뜨고, 누르면 새 버전으로 다시 켜진다. 즉시(강제) 업데이트는 앱을
 * 통째로 막아서 쓰지 않는다.
 *
 * 플레이스토어에서 설치한 앱에서만 동작한다. adb 로 깐 앱은 확인에 실패하거나 새 버전이
 * 없다고 나오고, 그때는 아무것도 띄우지 않고 넘어간다. 시험하려면 내부 테스트 트랙에 올려
 * 스토어에서 받은 뒤, 번호를 올린 버전을 하나 더 올리면 된다.
 *
 * onCreate 에서 만들 것 — 결과를 받는 창구는 화면이 시작되기 전에 등록해야 한다.
 */
class AppUpdates(private val activity: ComponentActivity, private val anchor: View) {

    private val manager = AppUpdateManagerFactory.create(activity)

    private val launcher =
        activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {}

    private val listener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) showRestart()
    }

    /** 앱을 켤 때 한 번 묻는다. 거절하면 그 실행 동안은 다시 묻지 않는다. */
    fun check() {
        manager.registerListener(listener)
        manager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
            ) {
                manager.startUpdateFlowForResult(
                    info, launcher, AppUpdateOptions.defaultOptions(AppUpdateType.FLEXIBLE),
                )
            }
        }
    }

    /** 받는 중에 앱을 나갔다 와도 "다시 시작"을 놓치지 않게, 돌아올 때마다 확인한다. */
    fun resume() {
        manager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.installStatus() == InstallStatus.DOWNLOADED) showRestart()
        }
    }

    fun release() {
        manager.unregisterListener(listener)
    }

    private fun showRestart() {
        Snackbar.make(anchor, R.string.update_ready, Snackbar.LENGTH_INDEFINITE)
            .setAction(R.string.update_restart) { manager.completeUpdate() }
            .show()
    }
}
