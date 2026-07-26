package com.waxball.asmr.ui

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.waxball.asmr.R
import com.waxball.asmr.core.Progress

/**
 * 조작법 안내. 처음 플레이할 때 한 번 뜨고, 홈에서 언제든 다시 볼 수 있다.
 *
 * 깨기와 굴리기가 영역으로 갈리는 구조라 설명 없이는 굴리는 법을 못 찾는다.
 */
object ControlsGuide {

    fun show(context: Context) {
        AlertDialog.Builder(context)
            .setTitle(R.string.controls_title)
            .setMessage(R.string.controls_body)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    /**
     * 처음 한 번만 띄운다.
     *
     * 호출자가 이미 들고 있는 Progress를 받아서 고친다. 여기서 저장소를 새로 읽어
     * 저장해 버리면, 호출자가 나중에 자기 사본을 저장할 때 이 플래그가 도로 지워진다.
     * 그러면 매번 들어올 때마다 안내가 뜬다.
     */
    fun showOnce(context: Context, progress: Progress): Boolean {
        if (progress.seenControlsTip) return false
        progress.seenControlsTip = true
        show(context)
        return true
    }
}
