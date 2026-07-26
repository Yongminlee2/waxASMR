package com.waxball.asmr.ui

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.waxball.asmr.R

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

    /** 처음 플레이할 때 한 번만. */
    fun showOnce(context: Context, store: PrefsProgressStore) {
        val progress = store.load()
        if (progress.seenControlsTip) return
        progress.seenControlsTip = true
        store.save(progress)
        show(context)
    }
}
