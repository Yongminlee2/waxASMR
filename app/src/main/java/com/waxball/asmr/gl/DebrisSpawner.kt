package com.waxball.asmr.gl

import com.waxball.asmr.core.Quat
import com.waxball.asmr.core.ShardState

/**
 * 새로 떨어져 나간 조각을 낙하시킨다.
 *
 * 기존 화면과 손바닥 모드가 똑같이 필요한 일이라 한 곳에 둔다.
 * 같은 코드를 두 곳에 두면 한쪽만 고치게 된다.
 */
object DebrisSpawner {

    /**
     * 금이 번져 한꺼번에 여러 조각이 떨어질 수 있어, 상태를 훑어 새로 떨어진 것만 낙하시킨다.
     *
     * 연쇄로 떨어진 것들은 시차를 두고 놓아 준다. 동시에 우르르 사라지면 한 덩어리가
     * 지워진 것처럼 보이는데, 몇 프레임씩 어긋나면 옆으로 번져 무너지는 게 눈에 보인다.
     *
     * @return 이번에 떨어져 나간 넓이 합
     */
    fun spawnFreshlyDetached(scene: BallScene, rotation: Quat): Float {
        var area = 0f
        var order = 0
        for (i in scene.model.state.indices) {
            if (scene.model.state[i] >= ShardState.DETACHED && !scene.debris.isActive(i)) {
                val shard = scene.shards.shards[i]
                scene.debris.spawn(i, shard.center, shard.areaFrac, rotation, hangFrames = order * 3)
                area += shard.areaFrac
                order++
            }
        }
        return area
    }
}
