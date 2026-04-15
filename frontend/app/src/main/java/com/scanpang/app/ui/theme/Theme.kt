package com.scanpang.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 브랜드·시맨틱 색상 (요청 스펙 + Figma 홈 화면) */
object ScanPangColors {
    val Primary = Color(0xFF1A73E8)
    val Background = Color(0xFFF5F6F8)
    val Surface = Color(0xFFFFFFFF)
    val Success = Color(0xFF34A853)
    val Error = Color(0xFFEA4335)
    val Warning = Color(0xFFFBBC04)

    /** 본문 강조 텍스트 (#1C1C1E) */
    val OnSurfaceStrong = Color(0xFF1C1C1E)
    /** 보조·메타 텍스트 (#6B7280) */
    val OnSurfaceMuted = Color(0xFF6B7280)
    /** 플레이스홀더·비활성 탭 (#9CA3AF) */
    val OnSurfacePlaceholder = Color(0xFF9CA3AF)
    /** 카드·탭 바 테두리 (#E5E7EB) */
    val OutlineSubtle = Color(0xFFE5E7EB)
    /** 키블라 카드·최근 항목 아이콘 배경 (#E8F0FE) */
    val PrimarySoft = Color(0xFFE8F0FE)
    /** 프라이머리 버튼 위 보조 텍스트 (흰색 80%) */
    val OnPrimaryMuted = Color(0xCCFFFFFF)
    /** HALAL MEAT 뱃지 배경 rgba(123,30,30,0.07) */
    val HalalMeatBadgeBackground = Color(0x127B1E1E)
    val HalalMeatBadgeText = Color(0xFF7B1E1E)
    /** SEAFOOD 뱃지 배경 rgba(26,115,232,0.07) */
    val SeafoodBadgeBackground = Color(0x121A73E8)
    /** VEGGIE 뱃지 */
    val VeggieBadgeBackground = Color(0x1234A853)
    val VeggieBadgeText = Color(0xFF15803D)
    /** SALAM SEOUL 뱃지 */
    val SalamSeoulBadgeBackground = Color(0x127C3AED)
    val SalamSeoulBadgeText = Color(0xFF6D28D9)
    /** 영업 중·오픈 상태 (#10B981) */
    val StatusOpen = Color(0xFF10B981)
    /** 신뢰 태그 배경 rgba(16,185,129,0.06) */
    val TrustPillBackground = Color(0x0F10B981)
    val TrustPillText = Color(0xFF065F46)
    /** 인기·필수방문 등 앰버 강조 (#F59E0B) */
    val AccentAmber = Color(0xFFF59E0B)
    /** 로그아웃 등 (Figma #EF4444, 브랜드 에러와 구분 시) */
    val DangerStrong = Color(0xFFEF4444)
    /** 추천 카테고리 아이콘 틴트 */
    val CategoryRestaurant = Color(0xFFEA580C)
    val CategoryCafe = Color(0xFFEA580C)
    val CategoryMall = Color(0xFF9333EA)
    val CategoryMedical = Color(0xFFDC2626)
    val CategoryExchange = Color(0xFFE11D48)

    /** AR 오버레이·버블 (Figma rgba(255,255,255,0.8~0.93)) */
    val ArOverlayWhite80 = Color(0xCCFFFFFF)
    val ArOverlayWhite85 = Color(0xD9FFFFFF)
    val ArOverlayWhite93 = Color(0xEDFFFFFF)
    /** AR 상단 그라데이션 (from 50% white → 투명) */
    val ArTopGradientStart = Color(0x80FFFFFF)
    val ArTopGradientEnd = Color(0x00FFFFFF)
    /** AR 탐색 카메라 상단 스크림 (흰색 70% → 투명, Edge-to-edge 오버레이) */
    val ArExploreScrimGradientTop = Color(0xB3FFFFFF)
    val ArExploreScrimGradientBottom = Color(0x00FFFFFF)
    /** AR 검색·필터 바깥 스크림 */
    val ArOverlayScrimDark = Color(0x66000000)
    /** TTS 꺼짐 시 아이콘 틴트 */
    val ArTtsOffIconTint = Color(0xFF9CA3AF)
    /** AR iOS 스타일 키보드 패널 배경 */
    val ArKeyboardIosBackground = Color(0xFFD2D3D8)
    val ArKeyboardIosFunctionKey = Color(0xFFABB0BC)
    /** AR POI 카드 텍스트 */
    val ArPoiTitle = Color(0xCC1C1B1F)
    val ArPoiSubtitle = Color(0x991C1B1F)
    /** AR 입력창 전송 칩 배경 rgba(107,114,128,0.1) */
    val ArSendChipBackground = Color(0x1A6B7280)
    /** AR 화면 고정 틴트 rgba(232,240,254,0.09) */
    val ArFreezeTint = Color(0x17E8F0FE)
    /** AR 동결 시 카메라 FAB (Primary ~80%) */
    val ArPrimaryTranslucent = Color(0xCC1A73E8)
    /** AR 추천 태그(할랄) 배경 */
    val ArRecommendTagHalalBackground = Color(0xFFF0F4FF)
    /** AR 검색 패널 테두리 */
    val ArSearchPanelStroke = Color(0xFFF3F4F6)
    /** AR 하단 채팅 영역 은은한 스크림 */
    val ArBottomChatScrim = Color(0x26FFFFFF)
    /** AR 길안내 중앙 턴 배지 (Primary 90%) */
    val ArNavPrimaryBadge90 = Color(0xE61A73E8)
    /** AR 도착 배지 (StatusOpen 90%, Figma #10B981) */
    val ArNavSuccessBadge90 = Color(0xE610B981)
    /** AR 다음 안내 스텝 칩 배경 rgba(255,255,255,0.53) */
    val ArNavNextStepBackground = Color(0x87FFFFFF)
    /** AR 다음 스텝 거리 텍스트 rgba(28,28,30,0.5) */
    val ArNavNextStepTextMuted = Color(0x801C1C1E)
    /** AR 하단 시트 드래그 핸들 */
    val ArNavDragHandle = Color(0xFFD1D5DB)
    /** AR 길안내 하단 시트 배경 */
    val ArNavBottomSheetBackground = Color(0xCCFFFFFF)
    /** AR 장소 상세 플로팅 패널 rgba(255,255,255,0.9) */
    val DetailArPanelSurface = Color(0xE6FFFFFF)
    /** AR 매장 카드 상단 rgba(255,255,255,0.93) */
    val DetailArStoreCardSurface = Color(0xEDFFFFFF)
    /** 상세 캐러셀·플레이스홀더 */
    val DetailCarouselPlaceholder = Color(0xFFD4D8DE)
    val DetailHeroImagePlaceholder = Color(0xFFE6E6E6)
    /** 메뉴·리스트 행 배경 */
    val DetailMenuRowBackground = Color(0xFFF8F9FB)
    /** AI 팁 배경 rgba(245,158,11,0.06) */
    val DetailTipBackground = Color(0x0FF59E0B)
    val DetailTipText = Color(0xFF92400E)
    /** 방문 가능 카드 배경·테두리 */
    val DetailVisitOpenSurface = Color(0x0810B981)
    val DetailVisitOpenBorder = Color(0x2110B981)
    /** 오버레이 카운트 배지 */
    val DetailImageCountScrim = Color(0x4D000000)
}

/** 간격 토큰 */
object ScanPangSpacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
    /** 검색 행·카테고리 열 간격 */
    val rowGap10: Dp = 10.dp
}

/** 레이아웃·컴포넌트 치수 (Figma 스펙) */
object ScanPangDimens {
    val screenHorizontal: Dp = 20.dp
    val headerTopPadding: Dp = 16.dp
    val bottomBarContainerHeight: Dp = 95.dp
    val bottomPillHeight: Dp = 62.dp
    /** FAB·라벨 묶음을 위로 띄우는 오프셋 */
    val tabBarFabCenterOffsetUp: Dp = 28.dp
    /** 메인 탭(ScanPangTabBar) 위 콘텐츠 하단 여백 */
    val mainTabContentBottomInset: Dp = bottomBarContainerHeight + tabBarFabCenterOffsetUp
    /** AR 탐색 화면 상단 그라데이션 오버레이 높이 */
    val arExploreGradientOverlayHeight: Dp = 200.dp
    /** POI 바텀시트 최대 높이 */
    val arPoiSheetMaxHeight: Dp = 400.dp
    val bottomPillHorizontalInset: Dp = 21.dp
    val bottomPillInnerPadding: Dp = 4.dp
    val fabSize: Dp = 56.dp
    val fabIcon: Dp = 32.dp
    val tabIcon: Dp = 24.dp
    val compassSize: Dp = 220.dp
    val compassStroke: Dp = 3.dp
    val compassNavIcon: Dp = 48.dp
    val compassCenterDot: Dp = 12.dp
    val searchBarHeightDefault: Dp = 48.dp
    val searchBarHeightActive: Dp = 44.dp
    val searchBarInnerHorizontal: Dp = 14.dp
    val categoryCellHeight: Dp = 76.dp
    val categoryIconLabelGap: Dp = 6.dp
    val profileAvatar: Dp = 48.dp
    val placeImageHeight: Dp = 80.dp
    val cardPadding: Dp = 12.dp
    val borderHairline: Dp = 1.dp
    val icon5: Dp = 5.dp
    val icon10: Dp = 10.dp
    val icon14: Dp = 14.dp
    val icon16: Dp = 16.dp
    val icon18: Dp = 18.dp
    val chipPadHorizontal: Dp = 6.dp
    val chipPadVertical: Dp = 2.dp
    val badgePadVertical: Dp = 3.dp
    val stackGap6: Dp = 6.dp
    val cuisineBadgeHorizontal: Dp = 7.dp
    val trustChipHorizontal: Dp = 6.dp
    val trustChipVertical: Dp = 2.dp
    val trustIconGap: Dp = 3.dp
    val suggestionRowVertical: Dp = 14.dp
    val chevronSmall: Dp = 12.dp
    val settingsLeadingIcon: Dp = 20.dp
    val sortButtonPaddingStart: Dp = 12.dp
    val sortButtonPaddingEnd: Dp = 10.dp
    val sortButtonPaddingVertical: Dp = 6.dp
    val profileCardPadding: Dp = 14.dp
    val profileTagIcon: Dp = 14.dp
    val homeSectionGap: Dp = 5.dp
    val homeHeaderInset: Dp = 5.dp
    val homeHeaderTop: Dp = 10.dp
    val homeSearchBarHeight: Dp = 52.dp
    val homeQiblaRowVertical: Dp = 14.dp
    val homeMetaGap: Dp = 3.dp
    val homeQuickChipHorizontal: Dp = 12.dp
    val recentRowVertical: Dp = 14.dp
    val recentIconCircle: Dp = 40.dp
    val icon20: Dp = 20.dp
    val chevronEnd: Dp = 18.dp
    val bottomSectionBottom: Dp = 8.dp
    val sectionHeaderGap: Dp = 10.dp
    val recommendSectionGap: Dp = 14.dp
    val listBlockGap: Dp = 12.dp
    val cardRadiusLarge: Dp = 16.dp
    val qiblaCompassSectionGap: Dp = 40.dp

    val arTopBarBottomPadding: Dp = 12.dp
    val arTopBarHorizontal: Dp = 16.dp
    val arCircleBtn36: Dp = 36.dp
    val arStatusPillHeight: Dp = 30.dp
    val arStatusPillHorizontalPad: Dp = 12.dp
    val arSideColumnEnd: Dp = 16.dp
    val arSideColumnWidth: Dp = 40.dp
    val arSideColumnTop: Dp = 115.dp
    val arSideIconGap: Dp = 12.dp
    val arSideFab44: Dp = 44.dp
    val arPoiCardHeight: Dp = 51.dp
    val arPoiCardHorizontalPad: Dp = 14.dp
    val arPoiCardVerticalPad: Dp = 10.dp
    val arPoiIcon24: Dp = 24.dp
    val arPoiOneStart: Dp = 21.dp
    val arPoiOneTop: Dp = 222.dp
    val arPoiTwoStart: Dp = 272.dp
    val arPoiTwoTop: Dp = 309.dp
    val arChatAreaMaxHeight: Dp = 250.dp
    val arChatAreaBottomPad: Dp = 20.dp
    val arChatBubbleGap: Dp = 10.dp
    val arInputBarMinHeight: Dp = 60.dp
    val arInputBarRadius: Dp = 30.dp
    val arInputInnerPadH: Dp = 10.dp
    val arInputInnerPadV: Dp = 8.dp
    val arMicSendIcon: Dp = 24.dp
    val arFilterPanelTopOffset: Dp = 110.dp
    val arFilterPanelHorizontal: Dp = 16.dp
    val arFilterChipHeight: Dp = 28.dp
    val arFilterSectionTitleTop: Dp = 16.dp
    val arFilterApplyBottom: Dp = 24.dp
    val arKeyboardPanelHeight: Dp = 291.dp
    val arKeyboardKeyHeight: Dp = 42.dp
    val arKeyboardKeyGap: Dp = 6.dp
    val arKeyboardKeyRadius: Dp = 5.dp
    val arPoiCardShadowElevation: Dp = 5.dp
    val arBubbleRadiusLarge: Dp = 20.dp
    val arBubbleRadiusSmall: Dp = 4.dp
    val arSearchPanelTopPad: Dp = 12.dp
    val arSearchTagHorizontalPad: Dp = 10.dp
    val arSearchTagVerticalPad: Dp = 6.dp

    val arNavTopFab40: Dp = 40.dp
    val arNavTopFabIcon: Dp = 24.dp
    val arNavDestinationFlagIcon: Dp = 20.dp
    val arNavDestinationChevron: Dp = 16.dp
    val arNavActionCardWidth: Dp = 270.dp
    val arNavActionStackHeight: Dp = 105.dp
    val arNavActionCardPadH: Dp = 16.dp
    val arNavActionCardPadV: Dp = 12.dp
    val arNavActionIconSquare: Dp = 48.dp
    val arNavActionIconInner: Dp = 32.dp
    val arNavNextStepWidth: Dp = 160.dp
    val arNavNextStepHeight: Dp = 38.dp
    val arNavNextStepOffsetStart: Dp = 5.dp
    val arNavNextStepTop: Dp = 66.dp
    val arNavActionClusterTop: Dp = 120.dp
    val arNavActionClusterStart: Dp = 16.dp
    val arNavTurnBadgeSize: Dp = 72.dp
    val arNavTurnBadgeIcon: Dp = 50.dp
    val arNavLocationBadgeIcon: Dp = 40.dp
    val arNavPoiFab: Dp = 50.dp
    val arNavPoiOneStart: Dp = 39.dp
    val arNavPoiOneTop: Dp = 410.dp
    val arNavPoiTwoStart: Dp = 310.dp
    val arNavPoiTwoTop: Dp = 455.dp
    val arNavBottomSheetDragH: Dp = 20.dp
    val arNavDragBarWidth: Dp = 36.dp
    val arNavDragBarHeight: Dp = 4.dp
    val arNavTabRowHeight: Dp = 40.dp
    val arNavTabTrackHeight: Dp = 34.dp
    val arNavTabInnerRadius: Dp = 15.dp
    val arNavTabSegmentGap: Dp = 5.dp
    val arNavTabInset: Dp = 5.dp
    val arNavGuideInputHeight: Dp = 48.dp
    val arNavGuideMicBtn: Dp = 36.dp
    val arNavGuideSendBtn: Dp = 36.dp
    val arNavGuideMicRadius: Dp = 18.dp
    val arNavGuideSendRadius: Dp = 16.dp
    val arArrivalLabelPadH: Dp = 14.dp
    val arArrivalLabelPadV: Dp = 6.dp
    val arArrivalStackGap: Dp = 4.dp

    val detailArPanelTop: Dp = 230.dp
    val detailArPanelWidth: Dp = 361.dp
    val detailArPanelHeight: Dp = 352.dp
    val detailArCarouselSmallHeight: Dp = 90.dp
    val detailPhotoHeroHeight: Dp = 240.dp
    val detailBookmarkBtn: Dp = 28.dp
    val detailBookmarkIcon: Dp = 20.dp
    val detailThreeTabHeight: Dp = 24.dp
    val detailThreeTabTrackPad: Dp = 2.dp
    val detailThreeTabInnerRadius: Dp = 6.dp
    val detailInfoIcon15: Dp = 15.dp
    val detailGridIcon12: Dp = 12.dp
    val detailFloorChevron: Dp = 24.dp
    val detailAiRobotIcon: Dp = 20.dp
    val detailAiPointIcon: Dp = 14.dp
    val detailCtaHeight: Dp = 48.dp
    val detailCtaSide: Dp = 48.dp
    val detailSectionSpacing: Dp = 20.dp
    val detailContentBottomPad: Dp = 24.dp
    val detailListCardRadius: Dp = 14.dp
    val detailArStorePanelHeight: Dp = 420.dp
    val detailArStoreHeroHeight: Dp = 168.dp
    val detailArCarouselItemWidth: Dp = 120.dp
}

/** 모서리 반경 (Compose Shape) */
object ScanPangShapes {
    val radius12 = RoundedCornerShape(12.dp)
    val radius14 = RoundedCornerShape(14.dp)
    val radius16 = RoundedCornerShape(16.dp)
    val pill36 = RoundedCornerShape(36.dp)
    val filterChip = RoundedCornerShape(20.dp)
    val badge6 = RoundedCornerShape(6.dp)
    val tag4 = RoundedCornerShape(4.dp)
    val sortButton = RoundedCornerShape(8.dp)
    val profileTag = RoundedCornerShape(20.dp)
    val arPoiCard = RoundedCornerShape(12.dp)
    val arInputPill = RoundedCornerShape(30.dp)
    val arKeyboardKey = RoundedCornerShape(5.dp)
    val arFilterPanelTop = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    val arSearchPanel = RoundedCornerShape(16.dp)
    val arBubbleUser = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomStart = 20.dp,
        bottomEnd = 4.dp,
    )
    val arBubbleAgent = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomStart = 4.dp,
        bottomEnd = 20.dp,
    )
    val arNavBottomSheetTop = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    val arNavNextStepChip = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
    val arNavDragBar = RoundedCornerShape(2.dp)
    val detailArStoreCard = RoundedCornerShape(20.dp)
    val detailMenuRow = RoundedCornerShape(10.dp)
    val detailVisitCard = RoundedCornerShape(12.dp)
    val detailArStoreImageTop = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
}

/** 타이포 스케일 토큰 (Material3 Type scale 기준) */
object ScanPangType {
    val displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
    )
    val headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    )
    val titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    )
    val bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    )
    val bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    )
    val labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )
    val labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    )

    /** Figma: 홈 인사 22 Bold */
    val homeGreeting = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    )
    /** Figma: 섹션 타이틀 17 Bold */
    val sectionTitle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
    )
    /** Figma: 위치·더보기 13 */
    val meta13 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )
    val link13 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )
    /** Figma: 검색 플레이스홀더 15 Medium */
    val searchPlaceholder = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    )
    /** 검색 기본 화면 플레이스홀더 15 Regular */
    val searchPlaceholderRegular = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    )
    /** Figma: 키블라 제목·카드 제목 14 SemiBold */
    val title14 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    )
    /** Figma: 부가 12 */
    val caption12 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )
    val caption12Medium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )
    /** Figma: 퀵 액션 라벨 12 SemiBold */
    val quickLabel12 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )
    /** Figma: 하단 탭 10 */
    val tabLabelActive = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.5.sp,
    )
    val tabLabelInactive = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.5.sp,
    )
    /** 검색·저장 화면 섹션 타이틀 16 Bold */
    val sectionTitle16 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    )
    val body14Regular = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )
    val body15Medium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    )
    val title16SemiBold = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    )
    val prayerTimeLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    )
    val directionDegree = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    )
    val badge9Bold = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 9.sp,
        lineHeight = 12.sp,
    )
    val badge9SemiBold = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 9.sp,
        lineHeight = 12.sp,
    )
    val meta11SemiBold = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    )
    val meta11Medium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    )
    val trust10SemiBold = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        lineHeight = 14.sp,
    )
    val category11SemiBold = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    )
    val chip12Medium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )
    val chip13SemiBold = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )
    val chip13Medium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )
    val sectionLabelSemiBold13 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )
    val sort12SemiBold = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )
    val tag10Medium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
    )
    val tag11Medium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
    )
    val profileName18 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    )
    val compassLabel12 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )
    /** AR 중앙 상태 필 15 SemiBold */
    val arStatusPill15 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    )
    /** AR iOS 키 캡 22 Regular */
    val arKeyboardKey22 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 26.sp,
    )
    val arChatBody14 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )
    val arFilterTitle16 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    )
    /** AR 길안내 현재 스텝 거리 26 Bold */
    val arNavDistance26 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.3).sp,
    )
    /** AR 길안내 스텝 부가 12 Medium */
    val arNavStepCaption12 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
    )
    /** AR 다음 스텝 거리 14 Bold */
    val arNavNextDistance14 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    )
    /** AR 길안내 탭·가이드 말풍선 13 */
    val arNavTab13 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )
    val arNavTab13Inactive = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )
    val arNavGuideInput13 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )
    val arArrivalTitle16 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    )
    val detailPlaceTitle18 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    )
    val detailSubtitleEn9 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 9.sp,
        lineHeight = 12.sp,
    )
    val detailThreeTab11 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    )
    val detailThreeTab11Inactive = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    )
    val detailBody12Loose = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    )
    val detailGrid10 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
    )
    val detailBadge9 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 9.sp,
        lineHeight = 12.sp,
    )
    val detailFloorTitle14 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    )
    val detailImageCount9 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 9.sp,
        lineHeight = 12.sp,
    )
    val detailScreenTitle22 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    )
    val detailMetaSubtitle13 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )
    val detailRestaurantTitle24 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    )
    val detailSectionTitle15 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    )
    val detailIntro13 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 21.sp,
    )
    val detailMenuPrice14 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    )
}

private val ScanPangTypography = Typography(
    displayLarge = ScanPangType.displayLarge,
    headlineMedium = ScanPangType.headlineMedium,
    titleLarge = ScanPangType.titleLarge,
    bodyLarge = ScanPangType.bodyLarge,
    bodyMedium = ScanPangType.bodyMedium,
    labelLarge = ScanPangType.labelLarge,
    labelSmall = ScanPangType.labelSmall,
)

/** Material3 `ColorScheme`에 없는 success / warning */
data class ScanPangSemanticColors(
    val success: Color = ScanPangColors.Success,
    val warning: Color = ScanPangColors.Warning,
)

val LocalScanPangSemanticColors = staticCompositionLocalOf { ScanPangSemanticColors() }

private val LightColorScheme = lightColorScheme(
    primary = ScanPangColors.Primary,
    onPrimary = Color.White,
    background = ScanPangColors.Background,
    onBackground = Color(0xFF1F1F1F),
    surface = ScanPangColors.Surface,
    onSurface = Color(0xFF1F1F1F),
    surfaceVariant = Color(0xFFE8EAED),
    onSurfaceVariant = Color(0xFF5F6368),
    error = ScanPangColors.Error,
    onError = Color.White,
)

private val DarkColorScheme = darkColorScheme(
    primary = ScanPangColors.Primary,
    onPrimary = Color.White,
    background = Color(0xFF121212),
    onBackground = Color(0xFFE3E3E3),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE3E3E3),
    surfaceVariant = Color(0xFF444746),
    onSurfaceVariant = Color(0xFFC4C7C5),
    error = ScanPangColors.Error,
    onError = Color.White,
)

@Composable
fun ScanPangTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    CompositionLocalProvider(
        LocalScanPangSemanticColors provides ScanPangSemanticColors(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ScanPangTypography,
            content = content,
        )
    }
}
