package com.scanpang.app.data

data class Place(
    val id: String,
    val name: String,
    val category: String,
    val subCategory: String = "",
    val distance: String,
    val address: String,
    val phone: String = "",
    val openHours: String = "",
    val isOpen: Boolean = true,
    val description: String = "",
    val tags: List<String> = emptyList(),
    val images: List<Int> = emptyList(),
    val rating: Float = 0f,
    val latitude: Double = 37.5636,
    val longitude: Double = 126.9869,
    // ── 통합 PlaceDetailScreen 에서 본문 자동 표시에 쓰이는 추가 필드 ──
    val categoryKey: String = "",
    val floor: String = "",
    val parking: String = "",
    val website: String = "",
    val convenienceServices: String = "",
    val departments: String = "",
    // ── 화장실 카테고리 — backend details(male_toilt_cnt 등) → Place 로 평탄화 ──
    val toiletMale: String = "",      // "5"
    val toiletFemale: String = "",    // "11"
    val facilityTags: String = "",    // "장애인 화장실, 유아 화장실, 기저귀 교환대"
    val safetyTags: String = "",      // "CCTV, 비상벨"
)

data class MenuItem(
    val name: String,
    val price: String,
)

data class RestaurantPlace(
    val place: Place,
    val halalCategory: String,
    val menuItems: List<MenuItem> = emptyList(),
    val lastOrder: String = "",
    val isMoslemChef: Boolean = false,
    val noAlcohol: Boolean = false,
)

data class ExchangeRate(
    val currency: String,
    val rate: String,
    val flag: String,
)
