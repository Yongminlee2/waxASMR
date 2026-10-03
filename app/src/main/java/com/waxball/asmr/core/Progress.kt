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

    var hapticsOn: Boolean = false
    var volume: Float = 0.85f

    /** -1이면 기기 성능을 재서 자동으로 정한다. 0=낮음 1=보통 2=높음. */
    var qualitySetting: Int = -1

    /** 손을 흔들면 볼이 딸려 도는 것. 기본 켜짐이고, 어지러우면 끈다. */
    var rollingOn: Boolean = true
    var seenHeadphoneTip: Boolean = false
    var seenControlsTip: Boolean = false

    /** 마지막에 고른 볼. 앱을 다시 켜도 그대로 두면 매번 다시 고르지 않아도 된다. */
    var lastBallId: Int = 0

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
        append("v=").append(SAVE_VERSION).append('\n')
        append("coins=").append(coins).append('\n')
        append("unlocked=").append(unlocked.sorted().joinToString(",")).append('\n')
        append("completed=").append(completed.sorted().joinToString(",")).append('\n')
        append("missionDay=").append(missionDay).append('\n')
        append("missions=").append(missionIds.joinToString(",")).append('\n')
        append("missionDone=").append(missionDone.sorted().joinToString(",")).append('\n')
        append("haptics=").append(if (hapticsOn) 1 else 0).append('\n')
        append("volume=").append(volume).append('\n')
        append("quality=").append(qualitySetting).append('\n')
        append("rolling=").append(if (rollingOn) 1 else 0).append('\n')
        append("headphoneTip=").append(if (seenHeadphoneTip) 1 else 0).append('\n')
        append("controlsTip=").append(if (seenControlsTip) 1 else 0).append('\n')
        append("lastBall=").append(lastBallId).append('\n')
    }

    companion object {
        private const val CLEAR_BONUS = 20

        /**
         * 저장 형식 번호. 2부터는 광고 해금이 있는 버전의 저장이다.
         *
         * 광고 해금 전(1)에는 42개를 전부 열어 줬고 그 목록이 저장에 들어 있다. 기존 이용자도
         * 업데이트하면 다시 잠그고 광고로 열게 하기로 했다 (2026-10-04 결정). 다만 1 이라는 번호는
         * 광고 해금이 처음 들어간 몇 개의 시험 빌드도 똑같이 썼다. 그래서 "번호가 1 이고 목록에
         * 42개가 전부 있으면" 광고 해금 전 기록으로 본다. 광고로 몇 개 연 기록은 그대로 둔다.
         */
        const val SAVE_VERSION = 2

        /** 처음에는 [BallCatalog.free] 만 열려 있다. 나머지는 광고를 보고 하나씩 연다. */
        fun fresh(): Progress = Progress().apply {
            unlocked.addAll(BallCatalog.free)
        }

        /**
         * 못 읽는 값이 있으면 그 항목만 기본값으로 두고 나머지는 살린다.
         * 저장이 깨졌다고 앱이 죽거나 진행이 통째로 날아가면 안 된다.
         */
        fun parse(text: String?): Progress {
            val p = fresh()
            if (text.isNullOrBlank()) return p
            var version = 1

            for (line in text.lineSequence()) {
                val sep = line.indexOf('=')
                if (sep <= 0) continue
                val key = line.substring(0, sep)
                val value = line.substring(sep + 1)
                when (key) {
                    "v" -> value.toIntOrNull()?.let { version = it }
                    "coins" -> value.toIntOrNull()?.let { p.coins = it.coerceAtLeast(0) }
                    // 무료 볼은 fresh() 가 이미 넣었다. 저장된 것은 거기에 더한다.
                    "unlocked" -> p.unlocked.addAll(intList(value))
                    "completed" -> { p.completed.clear(); p.completed.addAll(intList(value)) }
                    "missionDay" -> value.toLongOrNull()?.let { p.missionDay = it }
                    "missions" -> { p.missionIds.clear(); p.missionIds.addAll(intList(value)) }
                    "missionDone" -> { p.missionDone.clear(); p.missionDone.addAll(intList(value)) }
                    "haptics" -> p.hapticsOn = value != "0"
                    "volume" -> value.toFloatOrNull()?.let { p.volume = it.coerceIn(0f, 1f) }
                    "quality" -> value.toIntOrNull()?.let { p.qualitySetting = it.coerceIn(-1, 2) }
                    "rolling" -> p.rollingOn = value != "0"
                    "headphoneTip" -> p.seenHeadphoneTip = value == "1"
                    "controlsTip" -> p.seenControlsTip = value == "1"
                    "lastBall" -> value.toIntOrNull()?.let { p.lastBallId = it.coerceAtLeast(0) }
                }
            }
            // 광고 해금 전 기록(전부 열림)이면 무료 볼만 남기고 다시 잠근다. 설정 값은 그대로 둔다.
            if (version < SAVE_VERSION && p.unlocked.containsAll(BallCatalog.all.map { it.id })) {
                p.unlocked.clear()
                p.unlocked.addAll(BallCatalog.free)
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
