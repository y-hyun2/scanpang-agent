package com.scanpang.app.ui

/**
 * Figma MCP에서 제공된 원격 에셋 URL (만료 전 교체 가능)
 */
object ScanPangFigmaAssets {
    const val HomePlaceCard1 =
        "https://www.figma.com/api/mcp/asset/78e2278d-3435-447c-9062-ad855e6b86a9"
    const val HomePlaceCard2 =
        "https://www.figma.com/api/mcp/asset/6cc996c9-4b7d-497b-b0dd-2b8990b72ebf"
    const val ProfileAvatar =
        "https://www.figma.com/api/mcp/asset/d8de5102-e4d6-48d1-99bd-6ad1b49ce9e7"
    /** AR 길안내 하단 지도 탭 (Figma Map Content) */
    const val ArNavigationMap =
        "https://www.figma.com/api/mcp/asset/97e05b57-acf2-455f-97d5-aa63f5191efb"
    /** 식당 상세 히어로(캐러셀) — Figma 원본 URL 만료 시 교체 */
    const val RestaurantDetailHero =
        "https://www.figma.com/api/mcp/asset/78e2278d-3435-447c-9062-ad855e6b86a9"

    val RestaurantDetailGallery: List<String> = listOf(
        RestaurantDetailHero,
        HomePlaceCard1,
        HomePlaceCard2,
        ArNavigationMap,
    )
}
