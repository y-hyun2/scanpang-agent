package com.scanpang.app.components.ar

import android.location.Location
import com.hufs.arnavigation_com.ArRouteNode
import com.hufs.arnavigation_com.NodeType
import com.scanpang.arnavigation.data.remote.dto.NavRouteResponse

/**
 * AR 길안내용 라우트 파싱 + 기하 헬퍼.
 * (`ArNavigationActivity`의 private 메서드를 Compose에서 재사용 가능하게 top-level로 추출)
 */

internal fun parseNavResponse(response: NavRouteResponse): List<ArRouteNode> {
    val arCommand = response.ar_command ?: return emptyList()
    val parsedNodes = mutableListOf<ArRouteNode>()
    val distRes = FloatArray(2)        // [0]=거리(m), [1]=bearing(°)
    var lastLat = 0.0
    var lastLng = 0.0
    var prevBearing: Float? = null

    // 1) route_line 순회: bearing 변화로 누락된 turn 자동 검출 + path_point 보간
    arCommand.route_line.forEachIndexed { idx, point ->
        if (idx == 0) {
            lastLat = point.lat
            lastLng = point.lng
            return@forEachIndexed
        }
        Location.distanceBetween(lastLat, lastLng, point.lat, point.lng, distRes)
        val curBearing = distRes[1]

        if (prevBearing != null) {
            var angleDiff = curBearing - prevBearing!!
            while (angleDiff > 180f) angleDiff -= 360f
            while (angleDiff < -180f) angleDiff += 360f

            // 거리 ≥ 3m + 각도 변화 > 45° → T-map이 마킹하지 않은 누락된 turn으로 판정
            if (distRes[0] >= 3f && kotlin.math.abs(angleDiff) > 45f) {
                if (parsedNodes.isEmpty() ||
                    parsedNodes.last().lat != lastLat ||
                    parsedNodes.last().lng != lastLng
                ) {
                    parsedNodes.add(
                        ArRouteNode(
                            lastLat, lastLng, NodeType.TURN_POINT,
                            turnType = if (angleDiff > 0) 13 else 12,
                            isCalculated = true,
                        ),
                    )
                }
                prevBearing = curBearing
                lastLat = point.lat
                lastLng = point.lng
                return@forEachIndexed
            }
        }
        prevBearing = curBearing

        // 10m 간격 path_point 보간
        if (distRes[0] >= 10.0f) {
            val segs = (distRes[0] / 10.0f).toInt()
            for (j in 1..segs) {
                val f = (j * 10.0f) / distRes[0]
                parsedNodes.add(
                    ArRouteNode(
                        lastLat + (point.lat - lastLat) * f,
                        lastLng + (point.lng - lastLng) * f,
                        NodeType.PATH_POINT,
                    ),
                )
            }
        }
        lastLat = point.lat
        lastLng = point.lng
    }

    // 2) T-map turn_points 매칭: 가까운 노드(calculated turn 포함) 발견 시 정확한 데이터로 교체
    arCommand.turn_points.forEach { tp ->
        var bestIdx = -1
        var bestDist = Float.MAX_VALUE
        parsedNodes.forEachIndexed { idx, node ->
            Location.distanceBetween(tp.lat, tp.lng, node.lat, node.lng, distRes)
            if (distRes[0] < bestDist) {
                bestDist = distRes[0]
                bestIdx = idx
            }
        }
        if (bestIdx >= 0 && bestDist < 20f) {
            parsedNodes[bestIdx] = ArRouteNode(
                tp.lat, tp.lng, NodeType.TURN_POINT, tp.turnType,
                isCalculated = false, speech = tp.speech,
            )
        } else {
            parsedNodes.add(
                ArRouteNode(
                    tp.lat, tp.lng, NodeType.TURN_POINT, tp.turnType,
                    isCalculated = false, speech = tp.speech,
                ),
            )
        }
    }

    // 3) START / END 마킹
    if (parsedNodes.isNotEmpty()) {
        parsedNodes[0] = parsedNodes[0].copy(type = NodeType.START)
        // 도착 안내(EP) speech가 turn_points에 있으면 가져와서 END 노드에 부착
        val epSpeech = arCommand.turn_points.firstOrNull { it.pointType == "EP" }?.speech.orEmpty()
        parsedNodes.add(
            ArRouteNode(
                arCommand.destination.lat, arCommand.destination.lng, NodeType.END,
                speech = epSpeech,
            ),
        )
    }
    return parsedNodes
}

internal fun bearingBetween(from: ArRouteNode, to: ArRouteNode): Float {
    val r = FloatArray(2)
    Location.distanceBetween(from.lat, from.lng, to.lat, to.lng, r)
    return r[1]
}

internal fun calcTurnAngle(incoming: Float, outgoing: Float): Float {
    var d = outgoing - incoming
    while (d > 180f) d -= 360f
    while (d < -180f) d += 360f
    return d
}
