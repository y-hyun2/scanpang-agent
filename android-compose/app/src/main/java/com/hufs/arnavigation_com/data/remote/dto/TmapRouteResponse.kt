package com.scanpang.arnavigation.data.remote.dto

data class TmapRouteResponse(
    val type: String,
    val features: List<TmapFeature>
)

data class TmapFeature(
    val type: String,
    val geometry: TmapGeometry,
    val properties: TmapProperties
)

data class TmapGeometry(
    val type: String,
    val coordinates: List<Any>
)

data class TmapProperties(
    val pointType: String?,
    val lineIndex: Int?,
    val description: String?,
    val turnType: Int?  // ✅ 추가

)