package com.scanpang.app.components.ar

import android.location.Location
import com.hufs.arnavigation_com.ArRouteNode
import com.hufs.arnavigation_com.NodeType
import com.scanpang.arnavigation.data.remote.dto.NavRouteResponse
import com.scanpang.arnavigation.data.remote.dto.TmapFeature

/**
 * AR 길안내용 라우트 파싱 + 기하 헬퍼.
 *
 * 백엔드가 `ar_command.tmap_features`로 T-map raw GeoJSON을 통과시켜주면,
 * 원본 `MainActivity.parseTmapRoute`와 **동등한 로직**으로 처리.
 * (Point/LineString이 인터리브된 시간 순서를 그대로 따라가며, segment마다 bearing 리셋)
 *
 * 그 후 enriched turn_points에서 LLM speech를 lat/lng 매칭으로 채워줌 (TTS 음성 안내용).
 */

internal fun parseNavResponse(response: NavRouteResponse): List<ArRouteNode> {
    val arCommand = response.ar_command ?: return emptyList()
    if (arCommand.tmap_features.isEmpty()) return emptyList()

    val parsedNodes = parseTmapFeatures(arCommand.tmap_features).toMutableList()
    if (parsedNodes.isEmpty()) return parsedNodes

    // START / END 마킹 (원본 parseTmapRoute 동등)
    parsedNodes[0] = parsedNodes[0].copy(type = NodeType.START)
    parsedNodes[parsedNodes.size - 1] =
        parsedNodes[parsedNodes.size - 1].copy(type = NodeType.END)

    // LLM speech 매칭: turn_points의 lat/lng로 가까운 ArRouteNode에 speech 주입
    arCommand.turn_points.forEach { tp ->
        val matchIdx = parsedNodes.indexOfFirst { node ->
            node.type == NodeType.TURN_POINT &&
                kotlin.math.abs(node.lat - tp.lat) < 0.00005 &&
                kotlin.math.abs(node.lng - tp.lng) < 0.00005
        }
        if (matchIdx != -1 && tp.speech.isNotBlank()) {
            parsedNodes[matchIdx] = parsedNodes[matchIdx].copy(speech = tp.speech)
        }
    }

    return parsedNodes
}

/**
 * 원본 `MainActivity.parseTmapRoute`의 핵심 로직을 top-level 함수로 포팅.
 * (START/END 마킹과 디버깅 로그는 호출자가 처리)
 */
private fun parseTmapFeatures(features: List<TmapFeature>): List<ArRouteNode> {
    val parsedNodes = mutableListOf<ArRouteNode>()
    var lastLat = 0.0
    var lastLng = 0.0
    val distRes = FloatArray(1)
    var lastTurnLat: Double? = null
    var lastTurnLng: Double? = null
    var prevBearing: Float? = null

    features.forEach { feature ->
        if (feature.geometry.type == "Point") {
            val coords = feature.geometry.coordinates as? List<*>
            if (coords != null && coords.size >= 2) {
                val lng = (coords[0] as? Number)?.toDouble() ?: 0.0
                val lat = (coords[1] as? Number)?.toDouble() ?: 0.0
                val turnType = feature.properties.turnType ?: 0

                if (lastTurnLat != null && lastTurnLng != null) {
                    val distArr = FloatArray(1)
                    Location.distanceBetween(lastTurnLat!!, lastTurnLng!!, lat, lng, distArr)
                    if (distArr[0] < 5f) {
                        val lastIdx = parsedNodes.indexOfLast { it.type == NodeType.TURN_POINT }
                        if (lastIdx != -1 && parsedNodes[lastIdx].isCalculated) {
                            parsedNodes[lastIdx] = ArRouteNode(
                                lat, lng, NodeType.TURN_POINT, turnType, isCalculated = false,
                            )
                        }
                        lastTurnLat = lat; lastTurnLng = lng
                        lastLat = lat; lastLng = lng
                        return@forEach
                    }
                }

                lastTurnLat = lat
                lastTurnLng = lng
                parsedNodes.add(ArRouteNode(lat, lng, NodeType.TURN_POINT, turnType, isCalculated = false))
                lastLat = lat; lastLng = lng
            }
        } else if (feature.geometry.type == "LineString") {
            prevBearing = null
            (feature.geometry.coordinates as? List<*>)?.forEachIndexed { idx, item ->
                val coords = item as? List<*>
                if (coords != null && coords.size >= 2) {
                    val lng = (coords[0] as? Number)?.toDouble() ?: 0.0
                    val lat = (coords[1] as? Number)?.toDouble() ?: 0.0
                    if (lastLat == 0.0 || idx == 0) {
                        lastLat = lat; lastLng = lng
                    } else {
                        Location.distanceBetween(lastLat, lastLng, lat, lng, distRes)

                        val curBearingArr = FloatArray(2)
                        Location.distanceBetween(lastLat, lastLng, lat, lng, curBearingArr)
                        val curBearing = curBearingArr[1]

                        if (prevBearing != null) {
                            var angleDiff = curBearing - prevBearing!!
                            while (angleDiff > 180f) angleDiff -= 360f
                            while (angleDiff < -180f) angleDiff += 360f

                            if (distRes[0] >= 3f && kotlin.math.abs(angleDiff) > 45f) {
                                if (parsedNodes.isEmpty() ||
                                    parsedNodes.last().lat != lastLat ||
                                    parsedNodes.last().lng != lastLng
                                ) {
                                    parsedNodes.add(
                                        ArRouteNode(
                                            lastLat, lastLng,
                                            NodeType.TURN_POINT,
                                            turnType = if (angleDiff > 0) 13 else 12,
                                            isCalculated = true,
                                        ),
                                    )
                                }
                                prevBearing = curBearing
                                lastLat = lat; lastLng = lng
                                return@forEachIndexed
                            }
                        }
                        prevBearing = curBearing

                        if (distRes[0] >= 10.0f) {
                            val segs = (distRes[0] / 10.0f).toInt()
                            for (j in 1..segs) {
                                val f = (j * 10.0f) / distRes[0]
                                parsedNodes.add(
                                    ArRouteNode(
                                        lastLat + (lat - lastLat) * f,
                                        lastLng + (lng - lastLng) * f,
                                        NodeType.PATH_POINT,
                                    ),
                                )
                            }
                            lastLat = lat; lastLng = lng
                        }
                    }
                }
            }
        }
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
