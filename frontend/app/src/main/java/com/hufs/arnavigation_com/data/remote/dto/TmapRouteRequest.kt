package com.scanpang.arnavigation.data.remote.dto

data class TmapRouteRequest(
    val startX: String,
    val startY: String,
    val endX: String,
    val endY: String,
    val startName: String,
    val endName: String,
    val reqCoordType: String = "WGS84GEO",
    val resCoordType: String = "WGS84GEO"
)