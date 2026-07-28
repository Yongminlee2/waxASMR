package com.waxball.asmr.ar

/**
 * 손 관절 21개. 정규화 이미지 좌표(0~1)다.
 *
 * 인식기가 무엇이든 이 형태로만 넘겨받는다. 그래야 좌표를 볼로 바꾸는 계산을
 * 카메라 없이 PC에서 검증할 수 있다.
 */
class HandLandmarks(val x: FloatArray, val y: FloatArray) {

    companion object {
        const val COUNT = 21

        const val WRIST = 0
        const val INDEX_MCP = 5
        const val INDEX_TIP = 8
        const val MIDDLE_MCP = 9
        const val MIDDLE_TIP = 12
        const val RING_MCP = 13
        const val RING_TIP = 16
        const val PINKY_MCP = 17
        const val PINKY_TIP = 20
    }
}
