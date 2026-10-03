package com.waxball.asmr.ui

import android.content.Context
import com.waxball.asmr.core.Progress
import com.waxball.asmr.core.ProgressStore

class PrefsProgressStore(private val context: Context) : ProgressStore {

    private val prefs = context.getSharedPreferences("waxball", Context.MODE_PRIVATE)

    override fun load(): Progress = try {
        val text = prefs.getString(KEY, null)
        val progress = Progress.parse(text, legacyInstall = text == null && updatedFromOlderVersion())
        // 처음 켤 때 바로 저장해 둔다. 그래야 광고 해금 뒤에 깐 사람이 나중에 업데이트할 때
        // "저장 파일 없음 + 업데이트" 로 보여 예전 이용자처럼 전부 열리는 일이 없다.
        if (text == null) save(progress)
        progress
    } catch (e: Exception) {
        // 저장이 깨졌으면 새로 시작한다. 앱이 안 죽는 것이 우선이다.
        Progress.fresh()
    }

    /**
     * 새로 깐 게 아니라 예전 버전에서 업데이트된 것인가. 새로 깔면 두 시각이 같고,
     * 업데이트하면 마지막 업데이트 시각만 뒤로 간다.
     */
    private fun updatedFromOlderVersion(): Boolean = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.lastUpdateTime > info.firstInstallTime
    } catch (e: Exception) {
        false
    }

    override fun save(progress: Progress) {
        prefs.edit().putString(KEY, progress.serialize()).apply()
    }

    private companion object {
        const val KEY = "state"
    }
}
