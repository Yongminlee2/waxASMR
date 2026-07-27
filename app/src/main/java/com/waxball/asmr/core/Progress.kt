package com.waxball.asmr.core

/**
 * 저장되는 것 전부. 안드로이드 API를 쓰지 않아 PC에서 그대로 검증할 수 있다.
 *
 * 직렬화는 JSON 대신 한 줄에 하나씩 쓰는 key=value 형식이다.
 * 손으로 쓴 JSON 파서는 따옴표·이스케이프에서 조용히 틀리기 쉬운데,
 * 여기 저장할 것은 정수와 정수 목록뿐이라 그 위험을 질 이유가 없다.
 */
class Progress private constructor() {

    var coins: Int = 0
    val unlocked: MutableSet<Int> = HashSet()
    val completed: MutableSet<Int> = HashSet()

    var missionDay: Long = -1
    val missionIds: MutableList<Int> = ArrayList()
    val missionDone: MutableSet<Int> = HashSet()

    var hapticsOn: Boolean = true
    var volume: Float = 0.85f

    /** -1이면 기기 성능을 재서 자동으로 정한다. 0=낮음 1=보통 2=높음. */
    var qualitySetting: Int = -1
    var orbitLocked: Boolean = false
    var seenHeadphoneTip: Boolean = false
    var seenControlsTip: Boolean = false

    /** 마지막에 고른 뿌시는 도구. */
    var weaponId: Int = 0

    /** 켜면 파편을 뿌리지 않고 녹음을 통째로 튼다. 소리 비교용. */
    var rawPlayback: Boolean = false

    /** 조각 수와 완파 여부로 코인을 준다. 소모품이 없으니 용처는 볼 해금뿐이다. */
    fun awardForRun(detachedShards: Int, cleared: Boolean): Int =
        detachedShards / 10 + if (cleared) CLEAR_BONUS else 0

    fun isUnlocked(id: Int) = id in unlocked

    fun markCompleted(id: Int) {
        completed.add(id)
    }

    /** 날짜가 바뀌면 오늘의 미션을 새로 뽑는다. */
    fun rollMissionsIfNeeded(today: Long, pick: (Long) -> List<Int>) {
        if (missionDay == today && missionIds.isNotEmpty()) return
        missionDay = today
        missionIds.clear()
        missionIds.addAll(pick(today))
        missionDone.clear()
    }

    fun serialize(): String = buildString {
        append("v=1\n")
        append("coins=").append(coins).append('\n')
        append("unlocked=").append(unlocked.sorted().joinToString(",")).append('\n')
        append("completed=").append(completed.sorted().joinToString(",")).append('\n')
        append("missionDay=").append(missionDay).append('\n')
        append("missions=").append(missionIds.joinToString(",")).append('\n')
        append("missionDone=").append(missionDone.sorted().joinToString(",")).append('\n')
        append("haptics=").append(if (hapticsOn) 1 else 0).append('\n')
        append("volume=").append(volume).append('\n')
        append("quality=").append(qualitySetting).append('\n')
        append("orbitLocked=").append(if (orbitLocked) 1 else 0).append('\n')
        append("headphoneTip=").append(if (seenHeadphoneTip) 1 else 0).append('\n')
        append("controlsTip=").append(if (seenControlsTip) 1 else 0).append('\n')
        append("weapon=").append(weaponId).append('\n')
        append("rawPlayback=").append(if (rawPlayback) 1 else 0).append('\n')
        append("controlsTip=").append(if (seenControlsTip) 1 else 0).append('\n')
    }

    companion object {
        private const val CLEAR_BONUS = 20

        /**
         * 볼은 처음부터 전부 열려 있다.
         *
         * 코인을 모아 여는 구조를 뒀었는데, 소리를 들으려고 켜는 앱에서
         * 듣고 싶은 소리를 막아 두는 게 아무 의미가 없었다.
         * 코인은 미션 보상 기록으로만 남긴다.
         */
        fun fresh(): Progress = Progress().apply {
            unlocked.addAll(BallCatalog.all.map { it.id })
        }

        /**
         * 못 읽는 값이 있으면 그 항목만 기본값으로 두고 나머지는 살린다.
         * 저장이 깨졌다고 앱이 죽거나 진행이 통째로 날아가면 안 된다.
         */
        fun parse(text: String?): Progress {
            val p = fresh()
            if (text.isNullOrBlank()) return p

            for (line in text.lineSequence()) {
                val sep = line.indexOf('=')
                if (sep <= 0) continue
                val key = line.substring(0, sep)
                val value = line.substring(sep + 1)
                when (key) {
                    "coins" -> value.toIntOrNull()?.let { p.coins = it.coerceAtLeast(0) }
                    // 예전에 저장된 기록이 있어도 전부 열어 준다.
                    "unlocked" -> p.unlocked.addAll(intList(value))
                    "completed" -> { p.completed.clear(); p.completed.addAll(intList(value)) }
                    "missionDay" -> value.toLongOrNull()?.let { p.missionDay = it }
                    "missions" -> { p.missionIds.clear(); p.missionIds.addAll(intList(value)) }
                    "missionDone" -> { p.missionDone.clear(); p.missionDone.addAll(intList(value)) }
                    "haptics" -> p.hapticsOn = value != "0"
                    "volume" -> value.toFloatOrNull()?.let { p.volume = it.coerceIn(0f, 1f) }
                    "quality" -> value.toIntOrNull()?.let { p.qualitySetting = it.coerceIn(-1, 2) }
                    "orbitLocked" -> p.orbitLocked = value == "1"
                    "headphoneTip" -> p.seenHeadphoneTip = value == "1"
                    "controlsTip" -> p.seenControlsTip = value == "1"
                    "rawPlayback" -> p.rawPlayback = value == "1"
                    "weapon" -> value.toIntOrNull()?.let { p.weaponId = it.coerceIn(0, Weapon.entries.size - 1) }
                }
            }
            return p
        }

        private fun intList(value: String): List<Int> =
            value.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it >= 0 }
    }
}

/** 저장소는 인터페이스로 떼어 둔다. 그래야 진행 로직을 PC에서 검증할 수 있다. */
interface ProgressStore {
    fun load(): Progress
    fun save(progress: Progress)
}
