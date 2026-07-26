package com.waxball.asmr.core

/**
 * 파괴 이벤트 종류. 소리와 그림이 공유하는 유일한 접점이다.
 * 객체 할당을 피하려고 sealed class 대신 정수 상수를 쓴다.
 * 이 값들은 오디오 스레드가 매 프레임 읽는다.
 */
object EventKind {
    const val CRACK = 0
    const val DETACH = 1
    const val LAND = 2
    const val RUB = 3
    const val CORE = 4
}

/** 조각 상태. 되돌아가지 않는다. */
object ShardState {
    const val INTACT = 0
    const val HAIRLINE = 1
    const val CRACKED = 2
    const val LOOSE = 3
    const val DETACHED = 4
}
