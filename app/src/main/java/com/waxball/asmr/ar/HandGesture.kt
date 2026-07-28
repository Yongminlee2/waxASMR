package com.waxball.asmr.ar

import com.waxball.asmr.core.Weapon

/**
 * 손이 지금 무엇을 하고 있는가.
 *
 * 손바닥 모드는 오래 "쥐면 부서진다" 하나뿐이었다. 진짜 왁뿌볼은 손톱으로 뜯고
 * 긁으면서 노는데 그게 없어서 금방 지루해졌다.
 */
enum class Grip { NONE, PINCH, SCRATCH, SQUEEZE }

/** 손 모양에 맞는 도구. 잡은 게 없으면 null. */
fun Grip.weapon(): Weapon? = when (this) {
    Grip.PINCH -> Weapon.NAIL
    Grip.SCRATCH -> Weapon.FINGER
    Grip.SQUEEZE -> Weapon.FIST
    Grip.NONE -> null
}

/**
 * 손 모양을 판정한다. 판정 자체보다 **안 바뀌게 하는 것**이 어렵다.
 *
 * 인식은 프레임마다 미세하게 떨린다. 원시 판정을 그대로 쓰면 손을 가만히 두고
 * 있어도 도구가 초당 몇 번씩 바뀌어서, 뭘 쥐고 있는지 알 수 없게 된다.
 * 그래서 새 판정이 [ENTER_FRAMES] 프레임 연속으로 나와야 바뀐다.
 * 손을 놓는 쪽은 더 늦게 놓아 준다([LEAVE_FRAMES]). 도구가 순간순간 사라지는 것이
 * 잘못된 도구를 잠깐 쥐고 있는 것보다 거슬린다.
 */
class HandGesture {

    var grip = Grip.NONE
        private set

    private var candidate = Grip.NONE
    private var candidateFrames = 0

    fun update(pose: PalmPose) {
        val raw = classify(pose)
        if (raw == grip) {
            candidate = grip
            candidateFrames = 0
            return
        }
        if (raw == candidate) candidateFrames++ else { candidate = raw; candidateFrames = 1 }

        val needed = if (raw == Grip.NONE) LEAVE_FRAMES else ENTER_FRAMES
        if (candidateFrames >= needed) {
            grip = raw
            candidateFrames = 0
        }
    }

    /** 손을 놓쳤을 때. 여기서는 미루지 않고 즉시 놓는다. */
    fun reset() {
        grip = Grip.NONE
        candidate = Grip.NONE
        candidateFrames = 0
    }

    /**
     * 우선순위는 집기 > 쥐기 > 긁기다.
     *
     * 집으면서 손을 오므리는 일이 흔한데, 그럴 때 의도는 좁게 뜯는 쪽이다.
     *
     * 쥐기가 긁기보다 앞이다. 처음에는 반대로 뒀는데, **손가락을 굽히는 동작 자체가
     * 손끝을 손바닥 쪽으로 크게 움직이기 때문에** 손끝 속도만 보면 쥐는 것도 긁는 것으로
     * 잡혔다. 오므리는 중인지 아닌지가 둘을 가르는 진짜 기준이다.
     */
    private fun classify(pose: PalmPose): Grip {
        if (!pose.hasHand) return Grip.NONE
        if (pose.pinchRatio < PINCH_RATIO) return Grip.PINCH
        if (pose.force > 0f) return Grip.SQUEEZE
        if (pose.squeeze < SCRATCH_CURL_MAX && pose.tipSpeed > SCRATCH_SPEED) return Grip.SCRATCH
        return Grip.NONE
    }

    private companion object {
        /** 엄지 끝과 검지 끝이 손 폭의 이만큼 안으로 들어오면 집은 것으로 본다. */
        const val PINCH_RATIO = 0.35f

        /** 꽉 쥔 손은 긁는 게 아니다. 그 상태의 손끝 움직임은 인식 떨림뿐이다. */
        const val SCRATCH_CURL_MAX = 0.85f

        /** 손 폭을 1로 봤을 때 초당 이만큼 넘게 움직이면 긁는 것으로 본다. */
        const val SCRATCH_SPEED = 1.2f

        const val ENTER_FRAMES = 3
        const val LEAVE_FRAMES = 5
    }
}
