package com.scanpang.arnavigation.data.remote.dto

data class KakaoSearchResponse(
    val documents: List<KakaoDocument>
)

data class KakaoDocument(
    val place_name: String,
    val x: String,
    val y: String
)