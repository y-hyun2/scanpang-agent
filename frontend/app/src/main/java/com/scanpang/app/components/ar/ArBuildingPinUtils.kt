package com.scanpang.app.components.ar

import android.location.Location
import com.scanpang.app.data.remote.GeoJsonMultiPolygon

// ── 건물 핀 공통 유틸 ─────────────────────────────────────────────────────────
// ArExploreScreen(탐색)과 ArRealSceneView(길찾기) 양쪽에서 동일하게 사용.

internal fun computeCentroid(geom: GeoJsonMultiPolygon): Pair<Double, Double>? {
    val ring = geom.coordinates.firstOrNull()?.firstOrNull() ?: return null
    if (ring.isEmpty()) return null
    var sumLat = 0.0; var sumLng = 0.0
    for (point in ring) { sumLng += point[0]; sumLat += point[1] }
    return Pair(sumLat / ring.size, sumLng / ring.size)
}

internal fun computeAngularFootprint(
    geom: GeoJsonMultiPolygon,
    userLat: Double,
    userLng: Double,
): Pair<Double, Double>? {
    val ring = geom.coordinates.firstOrNull()?.firstOrNull() ?: return null
    if (ring.size < 3) return null
    val bearings = ring.map { point ->
        val r = FloatArray(3)
        Location.distanceBetween(userLat, userLng, point[1], point[0], r)
        ((r[1] + 360f) % 360f).toDouble()
    }
    val minB = bearings.min(); val maxB = bearings.max(); val range = maxB - minB
    return if (range > 180.0) {
        val wrapped = bearings.map { if (it < 180) it + 360 else it }
        val wMin = wrapped.min(); val wMax = wrapped.max()
        Pair(((wMin + wMax) / 2.0) % 360.0, (wMax - wMin) / 2.0)
    } else {
        Pair((minB + maxB) / 2.0, range / 2.0)
    }
}

internal fun buildFovPolygon(
    userLat: Double,
    userLng: Double,
    heading: Double,
    fovDeg: Double = 60.0,
    maxDistM: Double = 200.0,
): List<Pair<Double, Double>> {
    val halfFov = fovDeg / 2.0
    val numArcPoints = 7
    val result = mutableListOf(Pair(userLng, userLat))
    val angleStep = fovDeg / (numArcPoints - 1)
    for (i in 0 until numArcPoints) {
        val bearingRad = Math.toRadians((heading + (-halfFov + i * angleStep) + 360) % 360)
        val dLat = maxDistM * Math.cos(bearingRad) / 111_320.0
        val dLng = maxDistM * Math.sin(bearingRad) / (111_320.0 * Math.cos(Math.toRadians(userLat)))
        result.add(Pair(userLng + dLng, userLat + dLat))
    }
    result.add(Pair(userLng, userLat))
    return result
}

internal fun clipPolygon(
    subject: List<Pair<Double, Double>>,
    clip: List<Pair<Double, Double>>,
): List<Pair<Double, Double>> {
    if (subject.size < 3 || clip.size < 3) return emptyList()
    val subjectClean = if (subject.first() == subject.last()) subject.dropLast(1) else subject
    val clipClean = if (clip.first() == clip.last()) clip.dropLast(1) else clip
    var output = subjectClean.toMutableList()
    for (i in clipClean.indices) {
        if (output.isEmpty()) break
        val input = output.toList(); output = mutableListOf()
        val edgeStart = clipClean[i]; val edgeEnd = clipClean[(i + 1) % clipClean.size]
        for (j in input.indices) {
            val current = input[j]; val previous = input[(j - 1 + input.size) % input.size]
            val cIn = isInsideEdge(current, edgeStart, edgeEnd)
            val pIn = isInsideEdge(previous, edgeStart, edgeEnd)
            when {
                pIn && cIn -> output.add(current)
                pIn && !cIn -> output.add(computeLineIntersection(previous, current, edgeStart, edgeEnd))
                !pIn && cIn -> { output.add(computeLineIntersection(previous, current, edgeStart, edgeEnd)); output.add(current) }
            }
        }
    }
    return output
}

private fun isInsideEdge(
    point: Pair<Double, Double>,
    edgeStart: Pair<Double, Double>,
    edgeEnd: Pair<Double, Double>,
): Boolean {
    val cross = (edgeEnd.first - edgeStart.first) * (point.second - edgeStart.second) -
            (edgeEnd.second - edgeStart.second) * (point.first - edgeStart.first)
    return cross <= 0
}

private fun computeLineIntersection(
    p1: Pair<Double, Double>, p2: Pair<Double, Double>,
    edgeStart: Pair<Double, Double>, edgeEnd: Pair<Double, Double>,
): Pair<Double, Double> {
    val x1 = p1.first; val y1 = p1.second; val x2 = p2.first; val y2 = p2.second
    val x3 = edgeStart.first; val y3 = edgeStart.second; val x4 = edgeEnd.first; val y4 = edgeEnd.second
    val denom = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4)
    if (denom == 0.0) return p2
    val t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / denom
    return Pair(x1 + t * (x2 - x1), y1 + t * (y2 - y1))
}

internal fun computeFrontEdgeMidpoint(
    visiblePolygon: List<Pair<Double, Double>>,
    userLat: Double,
    userLng: Double,
): Pair<Double, Double>? {
    if (visiblePolygon.isEmpty()) return null
    if (visiblePolygon.size == 1) { val p = visiblePolygon[0]; return Pair(p.second, p.first) }
    var bestMidLng = 0.0; var bestMidLat = 0.0; var bestDist = Double.MAX_VALUE
    val n = visiblePolygon.size
    for (i in 0 until n) {
        val p1 = visiblePolygon[i]; val p2 = visiblePolygon[(i + 1) % n]
        val midLng = (p1.first + p2.first) / 2.0; val midLat = (p1.second + p2.second) / 2.0
        val r = FloatArray(1)
        Location.distanceBetween(userLat, userLng, midLat, midLng, r)
        if (r[0] < bestDist) { bestDist = r[0].toDouble(); bestMidLat = midLat; bestMidLng = midLng }
    }
    return Pair(bestMidLat, bestMidLng)
}

internal fun isRayBlockedByPolygon(
    userLng: Double, userLat: Double,
    targetLng: Double, targetLat: Double,
    occluderPolygon: List<Pair<Double, Double>>,
): Boolean {
    if (occluderPolygon.size < 3) return false
    val n = occluderPolygon.size
    for (i in 0 until n) {
        val p1 = occluderPolygon[i]; val p2 = occluderPolygon[(i + 1) % n]
        if (segmentsIntersect(userLng, userLat, targetLng, targetLat, p1.first, p1.second, p2.first, p2.second)) return true
    }
    return false
}

private fun segmentsIntersect(
    x1: Double, y1: Double, x2: Double, y2: Double,
    x3: Double, y3: Double, x4: Double, y4: Double,
): Boolean {
    val d1 = direction(x3, y3, x4, y4, x1, y1); val d2 = direction(x3, y3, x4, y4, x2, y2)
    val d3 = direction(x1, y1, x2, y2, x3, y3); val d4 = direction(x1, y1, x2, y2, x4, y4)
    return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
}

private fun direction(x1: Double, y1: Double, x2: Double, y2: Double, x3: Double, y3: Double): Double =
    (x3 - x1) * (y2 - y1) - (y3 - y1) * (x2 - x1)
