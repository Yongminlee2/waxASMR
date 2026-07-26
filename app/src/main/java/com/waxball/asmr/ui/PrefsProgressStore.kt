package com.waxball.asmr.ui

import android.content.Context
import com.waxball.asmr.core.Progress
import com.waxball.asmr.core.ProgressStore

class PrefsProgressStore(context: Context) : ProgressStore {

    private val prefs = context.getSharedPreferences("waxball", Context.MODE_PRIVATE)

    override fun load(): Progress = try {
        Progress.parse(prefs.getString(KEY, null))
    } catch (e: Exception) {
        // 저장이 깨졌으면 새로 시작한다. 앱이 안 죽는 것이 우선이다.
        Progress.fresh()
    }

    override fun save(progress: Progress) {
        prefs.edit().putString(KEY, progress.serialize()).apply()
    }

    private companion object {
        const val KEY = "state"
    }
}
