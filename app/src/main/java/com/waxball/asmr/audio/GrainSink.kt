package com.waxball.asmr.audio

/**
 * 그레인을 예약받아 소리로 만드는 것. 두 가지 구현이 있다.
 *
 * - [SampleGrainPool] : 실제 왁뿌볼 파열음 파편을 재생한다. 기본
 * - [GrainPool]       : 노이즈를 필터에 통과시켜 합성한다. 파편 뱅크를 못 읽었을 때의 대비책
 *
 * 둘 다 오디오 스레드의 안쪽 루프라 객체를 하나도 만들지 않는다.
 */
interface GrainSink {

    /** 동시에 울릴 수 있는 그레인 수. */
    val capacity: Int

    /**
     * @param freq 크랙의 목표 음높이. 파편 방식에서는 이 밝기에 가까운 파편을 고르는 기준이 된다
     * @param decayMs 이 시간이 지나면 잦아든다. 파편이 더 길면 잘라 낸다
     * @param attackMs 앞머리가 서는 시간. 파열음은 짧고 마찰음은 길다
     */
    fun spawn(
        delayFrames: Int,
        freq: Float,
        q: Float,
        decayMs: Float,
        amplitude: Float,
        pan: Float,
        resonance: Float,
        attackMs: Float = 0.4f,
    )

    /** out에 스테레오 인터리브로 더한다. 호출자가 미리 0으로 채운다. */
    fun render(out: FloatArray, frames: Int)

    fun reset()

    val activeCount: Int
}
