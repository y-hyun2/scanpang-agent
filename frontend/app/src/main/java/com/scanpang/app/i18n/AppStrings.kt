package com.scanpang.app.i18n

import androidx.compose.runtime.compositionLocalOf

interface AppStrings {
    // ── Onboarding ──────────────────────────────────────────────────────────
    val onboardingLanguageTitle: String
    val onboardingLanguageSubtitle: String
    val onboardingNameTitle: String
    val onboardingNameSubtitle: String
    val onboardingNameLabel: String
    val onboardingNamePlaceholder: String
    val onboardingPreferenceTitle: String
    val onboardingPreferenceSubtitle: String
    val onboardingPreferenceHalal: String
    val onboardingPreferenceHalalDesc: String
    val onboardingPreferenceVegan: String
    val onboardingPreferenceVeganDesc: String
    val onboardingPreferenceGeneral: String
    val onboardingStart: String

    // ── Common ───────────────────────────────────────────────────────────────
    val next: String
    val back: String
    val save: String
    val cancel: String
    val delete: String
    val more: String
    val required: String
    val optional: String

    // ── Categories (shared across Home / Search / Saved) ─────────────────────
    val catHalalRestaurant: String
    val catPrayerRoom: String
    val catTouristSpot: String
    val catVeganRestaurant: String
    val catVeganCafe: String
    val catRestaurant: String
    val catCafe: String
    val catShopping: String
    val catHospital: String
    val catPharmacy: String
    val catExchange: String
    val catConvenienceStore: String
    val catAtm: String
    val catBank: String
    val catSubwayStation: String
    val catRestroom: String
    val catLocker: String
    val catAll: String

    // ── Home ─────────────────────────────────────────────────────────────────
    val homeGreetingDefault: String
    val homeGreetingWithName: (String) -> String
    val homeSubtitle: String
    val homeSearchPlaceholder: String
    val homeRecentlyViewed: String
    val homeMoreButton: String
    val homeNoRecentlyViewed: String
    val homeNextPrayer: String
    val homeQiblaDirection: String

    // ── Search ───────────────────────────────────────────────────────────────
    val searchPlaceholder: String
    val searchRecentLabel: String
    val searchClearAll: String
    val searchNoRecent: String
    val searchRecommendedCategory: String
    val searchResultCount: (String, Int) -> String
    val searchNoResults: String
    val searchNoSubway: String
    val searchNoRestroom: String
    val searchNoPrayerRoom: String
    val searchNoLocker: String
    val searchNoExchange: String
    val searchNoHalal: String
    val searchNoVegan: String
    val searchNoAtm: String

    // ── Saved ─────────────────────────────────────────────────────────────────
    val savedTitle: String
    val savedNoPlaces: String
    val savedSortDistance: String
    val savedSortRecent: String
    val savedPlaceCount: (Int) -> String

    // ── Recently Viewed ───────────────────────────────────────────────────────
    val recentTitle: String
    val recentClear: String
    val recentSelectAll: String
    val recentEmpty: String
    val recentDeleteCount: (Int) -> String

    // ── Profile ───────────────────────────────────────────────────────────────
    val profileMyInfo: String
    val profileEditProfile: String
    val profileTravelSettings: String
    val profileLanguageSettings: String
    val profileValueAddedSettings: String
    val profileTtsGuidance: String
    val profileAppSettings: String
    val profileNotificationSettings: String
    val profileOther: String
    val profileHelp: String
    val profileInquiry: String
    val profileWithdrawal: String
    val profileLogout: String
    val profileDefaultName: String

    // ── Profile Edit ──────────────────────────────────────────────────────────
    val profileEditTitle: String
    val profileEditNameLabel: String
    val profileEditNamePlaceholder: String
    val profileEditNameHint: String
    val profileEditNameHintKo: String
    val profileEditNameHintEn: String
    val profileEditChangePhoto: String
    val profileEditUseDefault: String
    val profileEditUseDefaultConfirm: String
    val profileEditChooseGallery: String
    val profileEditChooseGalleryDesc: String
    val settingsLanguageDesc: String
    val settingsValueAddedDesc: String
    val notifReceive: String
    val notifPush: String
    val notifPushDesc: String
    val notifTypes: String
    val notifPromo: String
    val notifDnd: String
    val notifDndMode: String
    val notifDndDesc: String

    // ── Help ──────────────────────────────────────────────────────────────────
    val helpFaqTitle: String
    val helpFaqItems: List<Pair<String, String>>

    // ── Contact ───────────────────────────────────────────────────────────────
    val contactFormTitle: String
    val contactCategoryLabel: String
    val contactCategoryDefault: String
    val contactCategories: List<String>
    val contactTitleLabel: String
    val contactTitlePlaceholder: String
    val contactBodyLabel: String
    val contactBodyPlaceholder: String
    val contactPhotoLabel: String
    val contactPhotoHint: String
    val contactSubmit: String
    val contactSubmitting: String
    val contactNotLoggedIn: String
    val contactSubmitError: String
    val contactSuccessTitle: String
    val contactSuccessDesc: String
    val contactNewInquiry: String
    val contactPhotoAdd: String
    val contactPhotoRemove: String

    // ── Auth – Login ──────────────────────────────────────────────────────────
    val loginTermsPrefix: String
    val loginTermsOfService: String
    val loginTermsConnector: String
    val loginPrivacyPolicy: String
    val loginTermsSuffix: String

    // ── Auth – Error ──────────────────────────────────────────────────────────
    val loginFailed: String
    val loginFailedDesc: String
    val loginRetry: String
    val loginOtherMethod: String

    // ── Auth – Loading ────────────────────────────────────────────────────────
    val authLoadingDefault: String
    val authLoadingKakao: String
    val authLoadingGoogle: String
    val authLoadingGeneric: String

    // ── Auth – Terms Agreement ────────────────────────────────────────────────
    val termsAge: String
    val termsService: String
    val termsPrivacy: String
    val termsLocation: String
    val termsMarketing: String
    val termsRequiredNotice: String
    val termsAgreeAll: String
    val termsScreenTitle: String
    val termsViewMore: String
    val termsAgreeAndContinue: String

    // ── Auth – Withdrawal ─────────────────────────────────────────────────────
    val withdrawalTitle: String
    val withdrawalDesc: String
    val withdrawalDataTitle: String
    val withdrawalData1: String
    val withdrawalData2: String
    val withdrawalData3: String
    val withdrawalData4: String
    val withdrawalConfirmCheck: String
    val withdrawalButton: String

    // ── AR ────────────────────────────────────────────────────────────────────
    val arInitializing: String
    val arLocating: String
    val arVoiceError: String
    val arMicPermission: String
    val arTtsOff: String
    val arTtsOn: String
    val arScreenFreezing: String
    val arVpsScanning: String
    val arExploring: String
    val arScanning: String
    val arUnknownBuilding: String
    val arBuilding: String
    val arAnalyzing: String
    val arSaved: String
    val arVoiceNotReady: String
    val arVoiceNotAvailable: String
    val arInitialGreeting: String
    val arFilterScanning: (String) -> String
    val arFilterScanningMultiple: (String, Int) -> String
    val arFilterStoreCount: (String, Int) -> String

    // ── Qibla ─────────────────────────────────────────────────────────────────
    val qiblaPermissionRequired: String
    val qiblaLocating: String
    val qiblaLocationFormat: (Double, Double) -> String
    val qiblaLocationUnavailable: String
    val qiblaLocationFailed: String
    val qiblaDistanceFormat: (Double) -> String
    val qiblaTitle: String
    val qiblaNextPrayer: String
    val dirN: String
    val dirNE: String
    val dirE: String
    val dirSE: String
    val dirS: String
    val dirSW: String
    val dirW: String
    val dirNW: String

    // ── Tab Bar ───────────────────────────────────────────────────────────────
    val tabHome: String
    val tabSearch: String
    val tabSaved: String
    val tabProfile: String
    val tabExplore: String

    // ── Common UI ─────────────────────────────────────────────────────────────
    val close: String
    val send: String
    val viewAll: String

    // ── Place / POI ───────────────────────────────────────────────────────────
    val placeOpen: String
    val placeClosed: String
    val placeToday: (String) -> String
    val placeLastOrder: (String) -> String
    val placeFeaturedMenu: String
    val placeTodayExchangeRate: String
    val placeMaleStalls: (Int) -> String
    val placeFemaleStalls: (Int) -> String
    val placeDisabledRestroom: String
    val placeChildRestroom: String
    val placeDiaperTable: String
    val placeWudu: String
    val placeGenderSeparated: String
    val placePrayerMat: String
    val placeQuran: String
    val placeEmergencyBell: String
    val placeMuslimChef: String
    val placeNoAlcohol: String
    val placeBuildingInfo: String
    val placeFloorInfo: String
    val placeBookmarked: String

    // ── Place Detail ──────────────────────────────────────────────────────────
    val detailCollapse: String
    val detailExpandMore: (Int) -> String
    val detailIntro: String
    val detailInfo: String
    val detailOpenHours: String
    val detailAddress: String
    val detailPhone: String
    val detailFloor: String
    val detailParking: String
    val detailWebsite: String
    val detailToiletStalls: String
    val detailFacility: String
    val detailSafety: String
    val detailDepartments: String
    val detailSubwaySchedule: String
    val detailSubwayFastAlight: String
    val detailSubwayExits: String
    val detailAtm24h: String
    val detailAtmHourly: String
    val detailSubwayUp: String
    val detailSubwayDown: String
    val detailSubwayWeekday: String
    val detailSubwaySaturday: String
    val detailSubwayHoliday: String
    val detailSubwayFirst: String
    val detailSubwayLast: String
    val detailSubwayToward: (String) -> String
    val detailSubwayExitLabel: (String) -> String
    val detailSubwayExitAround: (String) -> String

    // ── Navigation ────────────────────────────────────────────────────────────
    val navInitialMessage: String
    val navDestination: String
    val navGoStraight: String
    val navLocating: String
    val navArrivedBadge: String
    val navGuiding: String
    val navChatPlaceholder: String
    val navArrivedMessage: String
    val navMap: String
    val navAiGuide: String
    val navStop: String
    val navStopConfirm: String
    val navSelectDestination: String
    val navBackToExplore: String
    val navBackToHome: String
    val navVoiceGuide: String

    // ── AR Explore ────────────────────────────────────────────────────────────
    val arFilter: String
    val arFilterReset: String
    val arFilterApply: String
    val arSearchPlaceholder: String
    val arViewInfo: String
    val arNavigate: String
    val arRecentSearches: String
    val arClearAll: String
}

// ─────────────────────────────────────────────────────────────────────────────
// Korean
// ─────────────────────────────────────────────────────────────────────────────
val KoStrings: AppStrings = object : AppStrings {
    override val onboardingLanguageTitle = "사용 언어를 선택해주세요"
    override val onboardingLanguageSubtitle = "선택한 언어로 음성 안내와 텍스트가 제공됩니다"
    override val onboardingNameTitle = "어떻게 불러드릴까요?"
    override val onboardingNameSubtitle = "AR 가이드와 인사할 때 사용됩니다"
    override val onboardingNameLabel = "이름"
    override val onboardingNamePlaceholder = "이름"
    override val onboardingPreferenceTitle = "여행 중 무엇을 우선하시나요?"
    override val onboardingPreferenceSubtitle = "안내와 알림이 이 선택에 맞춰집니다."
    override val onboardingPreferenceHalal = "할랄"
    override val onboardingPreferenceHalalDesc = "할랄 식당, 기도실, 키블라 방향 등"
    override val onboardingPreferenceVegan = "비건"
    override val onboardingPreferenceVeganDesc = "비건 식당, 채식 메뉴 등"
    override val onboardingPreferenceGeneral = "괜찮아요"
    override val onboardingStart = "시작하기"

    override val next = "다음"
    override val back = "뒤로"
    override val save = "저장"
    override val cancel = "취소"
    override val delete = "삭제"
    override val more = "더보기"
    override val required = "필수"
    override val optional = "선택"

    override val catHalalRestaurant = "할랄 식당"
    override val catPrayerRoom = "기도실"
    override val catTouristSpot = "관광명소"
    override val catVeganRestaurant = "비건 식당"
    override val catVeganCafe = "비건 카페"
    override val catRestaurant = "식당"
    override val catCafe = "카페"
    override val catShopping = "쇼핑"
    override val catHospital = "병원"
    override val catPharmacy = "약국"
    override val catExchange = "환전소"
    override val catConvenienceStore = "편의점"
    override val catAtm = "ATM"
    override val catBank = "은행"
    override val catSubwayStation = "지하철역"
    override val catRestroom = "화장실"
    override val catLocker = "물품보관함"
    override val catAll = "전체"

    override val homeGreetingDefault = "안녕하세요!"
    override val homeGreetingWithName: (String) -> String = { name -> "안녕하세요, ${name}님!" }
    override val homeSubtitle = "오늘 명동을 탐험해볼까요?"
    override val homeSearchPlaceholder = "목적지 검색"
    override val homeRecentlyViewed = "최근 본 장소"
    override val homeMoreButton = "더보기"
    override val homeNoRecentlyViewed = "아직 본 장소가 없어요"
    override val homeNextPrayer = "다음 기도"
    override val homeQiblaDirection = "키블라 방향"

    override val searchPlaceholder = "장소, 식당, 카테고리 검색"
    override val searchRecentLabel = "최근 검색"
    override val searchClearAll = "전체 삭제"
    override val searchNoRecent = "최근 검색 기록이 없어요"
    override val searchRecommendedCategory = "추천 카테고리"
    override val searchResultCount: (String, Int) -> String = { query, count -> "'$query' 검색 결과 ${count}개" }
    override val searchNoResults = "조건에 맞는 장소가 없습니다. 다른 검색어를 시도해 보세요."
    override val searchNoSubway = "주변 1.5km 이내에 지하철역이 없어요."
    override val searchNoRestroom = "주변 2km 이내에 화장실이 없어요."
    override val searchNoPrayerRoom = "주변 2km 이내에 기도실이 없어요."
    override val searchNoLocker = "주변 1.5km 이내에 물품보관함이 없어요."
    override val searchNoExchange = "주변에 환전소가 없어요."
    override val searchNoHalal = "근처에 할랄 식당이 없어요."
    override val searchNoVegan = "근처에 비건 식당/카페가 없어요."
    override val searchNoAtm = "주변에 ATM 이 없어요."

    override val savedTitle = "저장한 장소"
    override val savedNoPlaces = "저장한 장소가 없어요"
    override val savedSortDistance = "가까운 순"
    override val savedSortRecent = "최근 저장 순"
    override val savedPlaceCount: (Int) -> String = { n -> "${n}개의 장소" }

    override val recentTitle = "최근 본 장소"
    override val recentClear = "지우기"
    override val recentSelectAll = "전체선택"
    override val recentEmpty = "아직 본 장소가 없어요"
    override val recentDeleteCount: (Int) -> String = { n -> "삭제 (${n}개)" }

    override val profileMyInfo = "내 정보"
    override val profileEditProfile = "프로필 편집"
    override val profileTravelSettings = "여행 설정"
    override val profileLanguageSettings = "언어 설정"
    override val profileValueAddedSettings = "부가가치 설정"
    override val profileTtsGuidance = "TTS 음성 안내"
    override val profileAppSettings = "앱 설정"
    override val profileNotificationSettings = "알림 설정"
    override val profileOther = "기타"
    override val profileHelp = "도움말"
    override val profileInquiry = "문의하기"
    override val profileWithdrawal = "회원탈퇴"
    override val profileLogout = "로그아웃"
    override val profileDefaultName = "여행자"

    override val profileEditTitle = "프로필 편집"
    override val profileEditNameLabel = "이름"
    override val profileEditNamePlaceholder = "이름을 입력하세요"
    override val profileEditNameHint = "한글 최대 6자 · 영문 최대 12자"
    override val profileEditNameHintKo = "한글 최대 6자"
    override val profileEditNameHintEn = "한글 최대 6자 · 영문 최대 12자"
    override val profileEditChangePhoto = "사진 변경"
    override val profileEditUseDefault = "기본 사진 사용"
    override val profileEditUseDefaultConfirm = "기본 프로필 이미지로 변경합니다"
    override val profileEditChooseGallery = "갤러리에서 선택"
    override val profileEditChooseGalleryDesc = "휴대폰 갤러리에서 사진을 불러옵니다"
    override val settingsLanguageDesc = "앱에서 사용할 언어를 선택하세요. 선택한 언어로 음성 안내와 텍스트가 제공됩니다."
    override val settingsValueAddedDesc = "여행 중 우선할 항목을 선택하세요. 안내와 알림이 이 선택에 맞춰집니다."
    override val notifReceive = "알림 받기"
    override val notifPush = "푸시 알림"
    override val notifPushDesc = "모든 알림을 한 번에 켜고 끕니다"
    override val notifTypes = "알림 종류"
    override val notifPromo = "이벤트 및 프로모션"
    override val notifDnd = "방해 금지"
    override val notifDndMode = "방해 금지 모드"
    override val notifDndDesc = "22:00 - 07:00 동안 알림을 받지 않습니다"

    override val helpFaqTitle = "자주 묻는 질문"
    override val helpFaqItems: List<Pair<String, String>> = listOf(
        "할랄 인증 정보는 어떻게 확인하나요?" to
            "ScanPang은 각 장소의 할랄 인증 여부를 직접 확인하거나 공신력 있는 기관 데이터를 기반으로 제공합니다. " +
            "장소 상세 페이지에서 인증 뱃지와 함께 관련 정보를 확인할 수 있습니다. " +
            "인증 정보가 잘못되었다고 생각되면 1:1 문의를 통해 알려주세요.",
        "기도실(무살라) 정보는 얼마나 정확한가요?" to
            "기도실 정보는 운영시간, 위치 등이 사전에 수집된 데이터를 기반으로 합니다. " +
            "실제와 다를 수 있으니 방문 전 해당 시설에 직접 확인하시길 권장합니다. " +
            "정보 오류 발견 시 1:1 문의로 제보해 주시면 빠르게 반영하겠습니다.",
        "앱이 지원하는 언어는 무엇인가요?" to
            "현재 한국어(한국어)와 영어(English)를 지원합니다. " +
            "'내 정보 → 언어 설정'에서 언제든지 변경할 수 있으며, " +
            "음성 안내(TTS)도 선택한 언어로 제공됩니다.",
        "즐겨찾기 데이터는 어디에 저장되나요?" to
            "즐겨찾기는 현재 기기 내 로컬에 저장됩니다. " +
            "앱 삭제 또는 기기 변경 시 데이터가 초기화될 수 있으니 유의하세요. " +
            "클라우드 동기화 기능은 추후 업데이트 예정입니다.",
        "장소 추가 또는 정보 수정을 요청할 수 있나요?" to
            "네, '내 정보 → 문의하기'에서 '장소 정보 수정 요청' 카테고리를 선택하여 요청해 주세요. " +
            "장소명, 주소, 수정이 필요한 내용을 자세히 작성해 주시면 검토 후 반영하겠습니다.",
        "AR 탐색 기능이 정확하지 않아요." to
            "AR 기능은 GPS와 센서 데이터를 활용하므로 실내나 GPS 신호가 약한 환경에서는 정확도가 떨어질 수 있습니다. " +
            "위치 권한이 '항상 허용'으로 설정되어 있는지 확인하고, " +
            "지속적으로 문제가 발생하면 1:1 문의를 남겨주세요.",
        "앱 회원탈퇴 후 데이터는 어떻게 되나요?" to
            "회원탈퇴 시 앱에 저장된 모든 개인 정보와 설정이 즉시 삭제됩니다. " +
            "탈퇴 후에는 데이터 복구가 불가능하므로 신중하게 결정해 주세요.",
    )

    override val contactFormTitle = "문의 내용을 남겨주시면\n빠르게 답변드리겠습니다."
    override val contactCategoryLabel = "카테고리"
    override val contactCategoryDefault = "카테고리 선택"
    override val contactCategories: List<String> = listOf("오류 신고", "장소 정보 수정 요청", "장소 추가 요청", "기능 제안", "기타")
    override val contactTitleLabel = "제목"
    override val contactTitlePlaceholder = "문의 제목을 입력하세요 (최대 60자)"
    override val contactBodyLabel = "내용"
    override val contactBodyPlaceholder = "문의 내용을 자세히 입력해 주세요 (최대 500자)"
    override val contactPhotoLabel = "사진 첨부 (선택)"
    override val contactPhotoHint = "오류 화면이나 관련 사진을 첨부하면 더 빠른 처리에 도움이 됩니다."
    override val contactSubmit = "문의 제출하기"
    override val contactSubmitting = "전송 중..."
    override val contactNotLoggedIn = "로그인이 필요합니다"
    override val contactSubmitError = "전송 실패: 잠시 후 다시 시도해주세요"
    override val contactSuccessTitle = "문의가 접수되었습니다"
    override val contactSuccessDesc = "빠른 시일 내에 답변 드리겠습니다.\n답변은 가입하신 이메일로 발송됩니다."
    override val contactNewInquiry = "새 문의 작성"
    override val contactPhotoAdd = "사진 추가"
    override val contactPhotoRemove = "사진 제거"

    override val loginTermsPrefix = "로그인하면 "
    override val loginTermsOfService = "이용약관"
    override val loginTermsConnector = " 및 "
    override val loginPrivacyPolicy = "개인정보 처리방침"
    override val loginTermsSuffix = "에 동의합니다"

    override val loginFailed = "로그인에 실패했어요"
    override val loginFailedDesc = "네트워크 연결을 확인하고\n다시 시도해주세요"
    override val loginRetry = "다시 시도"
    override val loginOtherMethod = "다른 방법으로 로그인"

    override val authLoadingDefault = "잠시만 기다려주세요"
    override val authLoadingKakao = "카카오 인증 중입니다..."
    override val authLoadingGoogle = "구글 인증 중입니다..."
    override val authLoadingGeneric = "인증 중입니다..."

    override val termsAge = "만 14세 이상입니다"
    override val termsService = "서비스 이용약관"
    override val termsPrivacy = "개인정보 처리방침"
    override val termsLocation = "위치정보 이용약관"
    override val termsMarketing = "마케팅 정보 수신"
    override val termsRequiredNotice = "서비스 이용을 위해 동의가 필요합니다"
    override val termsAgreeAll = "전체 동의합니다"
    override val termsScreenTitle = "이용 약관"
    override val termsViewMore = "더보기"
    override val termsAgreeAndContinue = "동의하고 계속"

    override val withdrawalTitle = "정말 탈퇴하시겠어요?"
    override val withdrawalDesc = "탈퇴 시 모든 데이터가 삭제되며\n복구할 수 없습니다."
    override val withdrawalDataTitle = "삭제되는 정보"
    override val withdrawalData1 = "프로필 및 계정 정보 (이름, 프로필 사진)"
    override val withdrawalData2 = "저장한 장소 목록"
    override val withdrawalData3 = "최근 검색어 및 방문 기록"
    override val withdrawalData4 = "여행 선호 설정 (할랄 · 비건 · 언어)"
    override val withdrawalConfirmCheck = "위 내용을 모두 확인했습니다"
    override val withdrawalButton = "탈퇴하기"

    override val arInitializing = "ARCore 초기화 중..."
    override val arLocating = "위치 파악 중..."
    override val arVoiceError = "음성 인식 중 오류가 났어요"
    override val arMicPermission = "마이크 권한이 필요해요"
    override val arTtsOff = "음성 안내 꺼짐"
    override val arTtsOn = "음성 안내 켜짐"
    override val arScreenFreezing = "화면 고정 중"
    override val arVpsScanning = "VPS 탐색 중..."
    override val arExploring = "AR 탐색 중"
    override val arScanning = "탐색 중"
    override val arUnknownBuilding = "이름 없는 건물"
    override val arBuilding = "건물"
    override val arAnalyzing = "분석 중..."
    override val arSaved = "저장되었습니다"
    override val arVoiceNotReady = "음성 입력을 준비하지 못했어요"
    override val arVoiceNotAvailable = "이 기기에서 음성 인식을 쓸 수 없어요"
    override val arInitialGreeting = "안녕하세요! 스캔팡입니다. 주변 장소를 AR로 안내해 드릴게요."
    override val arFilterScanning: (String) -> String = { cat -> "${cat} 탐색 중" }
    override val arFilterScanningMultiple: (String, Int) -> String = { cat, n -> "${cat} 외 ${n}개 탐색 중" }
    override val arFilterStoreCount: (String, Int) -> String = { cat, n -> "${cat} ${n}개" }

    override val qiblaPermissionRequired = "위치 권한을 허용해 주세요"
    override val qiblaLocating = "위치를 가져오는 중…"
    override val qiblaLocationFormat: (Double, Double) -> String = { lat, lng -> "현재 위치: 위도 %.5f, 경도 %.5f".format(lat, lng) }
    override val qiblaLocationUnavailable = "현재 위치: 일시적으로 확인할 수 없습니다"
    override val qiblaLocationFailed = "현재 위치: 가져오기에 실패했습니다"
    override val qiblaDistanceFormat: (Double) -> String = { dist -> "메카까지 거리: %,.0f km".format(dist) }
    override val qiblaTitle = "키블라 방향"
    override val qiblaNextPrayer = "다음 기도 시간"
    override val dirN = "북"
    override val dirNE = "북동"
    override val dirE = "동"
    override val dirSE = "남동"
    override val dirS = "남"
    override val dirSW = "남서"
    override val dirW = "서"
    override val dirNW = "북서"

    override val tabHome = "홈"
    override val tabSearch = "검색"
    override val tabSaved = "저장"
    override val tabProfile = "내 정보"
    override val tabExplore = "탐색"

    override val close = "닫기"
    override val send = "전송"
    override val viewAll = "전체 보기"

    override val placeOpen = "영업 중"
    override val placeClosed = "영업 종료"
    override val placeToday: (String) -> String = { hours -> "오늘 $hours" }
    override val placeLastOrder: (String) -> String = { t -> "라스트오더 $t" }
    override val placeFeaturedMenu = "대표 메뉴"
    override val placeTodayExchangeRate = "오늘의 환율"
    override val placeMaleStalls: (Int) -> String = { n -> "남성 ${n}칸" }
    override val placeFemaleStalls: (Int) -> String = { n -> "여성 ${n}칸" }
    override val placeDisabledRestroom = "장애인 화장실"
    override val placeChildRestroom = "유아 화장실"
    override val placeDiaperTable = "기저귀 교환대"
    override val placeWudu = "우두 시설"
    override val placeGenderSeparated = "남녀 분리"
    override val placePrayerMat = "기도 매트"
    override val placeQuran = "꾸란 비치"
    override val placeEmergencyBell = "비상벨"
    override val placeMuslimChef = "무슬림 조리사"
    override val placeNoAlcohol = "주류 미판매"
    override val placeBuildingInfo = "건물 정보"
    override val placeFloorInfo = "층별 정보"
    override val placeBookmarked = "저장됨"

    override val detailCollapse = "접기"
    override val detailExpandMore: (Int) -> String = { n -> "더보기 (${n}개)" }
    override val detailIntro = "소개"
    override val detailInfo = "상세 정보"
    override val detailOpenHours = "영업시간"
    override val detailAddress = "주소"
    override val detailPhone = "전화"
    override val detailFloor = "매장 층수"
    override val detailParking = "주차 가능 여부"
    override val detailWebsite = "웹사이트"
    override val detailToiletStalls = "칸 수"
    override val detailFacility = "편의시설"
    override val detailSafety = "안전시설"
    override val detailDepartments = "진료과목"
    override val detailSubwaySchedule = "열차 시간표"
    override val detailSubwayFastAlight = "빠른 하차"
    override val detailSubwayExits = "출구 정보"
    override val detailAtm24h = "24시간"
    override val detailAtmHourly = "시간제"
    override val detailSubwayUp = "상행"
    override val detailSubwayDown = "하행"
    override val detailSubwayWeekday = "평일"
    override val detailSubwaySaturday = "토요일"
    override val detailSubwayHoliday = "일·공휴일"
    override val detailSubwayFirst = "첫차"
    override val detailSubwayLast = "막차"
    override val detailSubwayToward: (String) -> String = { dir -> "${dir} 방면" }
    override val detailSubwayExitLabel: (String) -> String = { no -> "${no}번" }
    override val detailSubwayExitAround: (String) -> String = { no -> "${no}번 출구 주변" }

    override val navInitialMessage = "길찾기 중 궁금한 점을 물어보세요!"
    override val navDestination = "목적지"
    override val navGoStraight = "직진"
    override val navLocating = "위치 잡는 중..."
    override val navArrivedBadge = "도착"
    override val navGuiding = "안내 중"
    override val navChatPlaceholder = "무엇이든 물어보세요"
    override val navArrivedMessage = "도착했어요!"
    override val navMap = "지도"
    override val navAiGuide = "AI 가이드"
    override val navStop = "길안내 종료"
    override val navStopConfirm = "길안내를 종료할까요?"
    override val navSelectDestination = "이동할 위치를 선택해주세요."
    override val navBackToExplore = "탐색으로 돌아가기"
    override val navBackToHome = "홈으로 돌아가기"
    override val navVoiceGuide = "음성 안내"

    override val arFilter = "필터"
    override val arFilterReset = "초기화"
    override val arFilterApply = "필터 적용"
    override val arSearchPlaceholder = "장소, 건물, 매장 검색"
    override val arViewInfo = "정보 보기"
    override val arNavigate = "길안내"
    override val arRecentSearches = "최근 검색"
    override val arClearAll = "전체 삭제"
}

// ─────────────────────────────────────────────────────────────────────────────
// English
// ─────────────────────────────────────────────────────────────────────────────
val EnStrings: AppStrings = object : AppStrings {
    override val onboardingLanguageTitle = "Select your language"
    override val onboardingLanguageSubtitle = "Voice guidance and text will be provided in the selected language"
    override val onboardingNameTitle = "What should we call you?"
    override val onboardingNameSubtitle = "Used when greeting you in the AR guide"
    override val onboardingNameLabel = "Name"
    override val onboardingNamePlaceholder = "Name"
    override val onboardingPreferenceTitle = "What do you prioritize while traveling?"
    override val onboardingPreferenceSubtitle = "Guidance and notifications will be tailored to your choice."
    override val onboardingPreferenceHalal = "Halal"
    override val onboardingPreferenceHalalDesc = "Halal restaurants, prayer rooms, Qibla direction, etc."
    override val onboardingPreferenceVegan = "Vegan"
    override val onboardingPreferenceVeganDesc = "Vegan restaurants, vegetarian menus, etc."
    override val onboardingPreferenceGeneral = "No preference"
    override val onboardingStart = "Get Started"

    override val next = "Next"
    override val back = "Back"
    override val save = "Save"
    override val cancel = "Cancel"
    override val delete = "Delete"
    override val more = "More"
    override val required = "Required"
    override val optional = "Optional"

    override val catHalalRestaurant = "Halal\nRestaurant"
    override val catPrayerRoom = "Prayer Room"
    override val catTouristSpot = "Tourist Spot"
    override val catVeganRestaurant = "Vegan\nRestaurant"
    override val catVeganCafe = "Vegan Cafe"
    override val catRestaurant = "Restaurant"
    override val catCafe = "Cafe"
    override val catShopping = "Shopping"
    override val catHospital = "Hospital"
    override val catPharmacy = "Pharmacy"
    override val catExchange = "Currency\nExchange"
    override val catConvenienceStore = "Convenience\nStore"
    override val catAtm = "ATM"
    override val catBank = "Bank"
    override val catSubwayStation = "Subway"
    override val catRestroom = "Restroom"
    override val catLocker = "Locker"
    override val catAll = "All"

    override val homeGreetingDefault = "Hello!"
    override val homeGreetingWithName: (String) -> String = { name -> "Hello, $name!" }
    override val homeSubtitle = "Explore Myeongdong today?"
    override val homeSearchPlaceholder = "Search destination"
    override val homeRecentlyViewed = "Recently Viewed"
    override val homeMoreButton = "More"
    override val homeNoRecentlyViewed = "No recently viewed places"
    override val homeNextPrayer = "Next Prayer"
    override val homeQiblaDirection = "Qibla Direction"

    override val searchPlaceholder = "Search places & categories"
    override val searchRecentLabel = "Recent Searches"
    override val searchClearAll = "Clear All"
    override val searchNoRecent = "No recent searches"
    override val searchRecommendedCategory = "Recommended Categories"
    override val searchResultCount: (String, Int) -> String = { query, count -> "$count results for \"$query\"" }
    override val searchNoResults = "No places found. Try a different search term."
    override val searchNoSubway = "No subway stations within 1.5km."
    override val searchNoRestroom = "No restrooms within 2km."
    override val searchNoPrayerRoom = "No prayer rooms within 2km."
    override val searchNoLocker = "No lockers within 1.5km."
    override val searchNoExchange = "No currency exchange nearby."
    override val searchNoHalal = "No halal restaurants nearby."
    override val searchNoVegan = "No vegan restaurants/cafes nearby."
    override val searchNoAtm = "No ATMs nearby."

    override val savedTitle = "Saved Places"
    override val savedNoPlaces = "No saved places"
    override val savedSortDistance = "Nearest"
    override val savedSortRecent = "Recently Saved"
    override val savedPlaceCount: (Int) -> String = { n -> "$n places" }

    override val recentTitle = "Recently Viewed"
    override val recentClear = "Clear"
    override val recentSelectAll = "Select All"
    override val recentEmpty = "No recently viewed places"
    override val recentDeleteCount: (Int) -> String = { n -> "Delete ($n)" }

    override val profileMyInfo = "My Profile"
    override val profileEditProfile = "Edit Profile"
    override val profileTravelSettings = "Travel Settings"
    override val profileLanguageSettings = "Language"
    override val profileValueAddedSettings = "Preferences"
    override val profileTtsGuidance = "TTS Voice Guide"
    override val profileAppSettings = "App Settings"
    override val profileNotificationSettings = "Notifications"
    override val profileOther = "Other"
    override val profileHelp = "Help"
    override val profileInquiry = "Contact Us"
    override val profileWithdrawal = "Delete Account"
    override val profileLogout = "Log Out"
    override val profileDefaultName = "Traveler"

    override val profileEditTitle = "Edit Profile"
    override val profileEditNameLabel = "Name"
    override val profileEditNamePlaceholder = "Enter your name"
    override val profileEditNameHint = "Up to 12 characters"
    override val profileEditNameHintKo = "Max 6 Korean chars"
    override val profileEditNameHintEn = "Up to 12 characters"
    override val profileEditChangePhoto = "Change Photo"
    override val profileEditUseDefault = "Use Default Photo"
    override val profileEditUseDefaultConfirm = "Change to default profile image"
    override val profileEditChooseGallery = "Choose from Gallery"
    override val profileEditChooseGalleryDesc = "Import a photo from your phone gallery"
    override val settingsLanguageDesc = "Select the language to use in the app. Voice guidance and text will be provided in the selected language."
    override val settingsValueAddedDesc = "Select your travel priority. Guidance and notifications will be tailored to your choice."
    override val notifReceive = "Receive Notifications"
    override val notifPush = "Push Notifications"
    override val notifPushDesc = "Turn all notifications on or off at once"
    override val notifTypes = "Notification Types"
    override val notifPromo = "Events & Promotions"
    override val notifDnd = "Do Not Disturb"
    override val notifDndMode = "Do Not Disturb Mode"
    override val notifDndDesc = "No notifications between 22:00 - 07:00"

    override val helpFaqTitle = "Frequently Asked Questions"
    override val helpFaqItems: List<Pair<String, String>> = listOf(
        "How do I check Halal certification?" to
            "ScanPang verifies each place's Halal certification directly or based on data from accredited institutions. " +
            "You can find the certification badge and related information on the place detail page. " +
            "If you believe the certification info is incorrect, please let us know via the Contact Us form.",
        "How accurate is the prayer room (musalla) information?" to
            "Prayer room information is based on pre-collected data including operating hours and location. " +
            "It may differ from actual conditions, so we recommend confirming directly with the facility before visiting. " +
            "If you find any errors, please report them via Contact Us and we'll update them promptly.",
        "What languages does the app support?" to
            "We currently support Korean (한국어) and English. " +
            "You can change the language anytime in 'My Profile → Language'. " +
            "Voice guidance (TTS) is also provided in the selected language.",
        "Where is my saved places data stored?" to
            "Saved places are currently stored locally on your device. " +
            "Note that data may be cleared if you uninstall the app or switch devices. " +
            "Cloud sync is planned for a future update.",
        "Can I request a place to be added or its info corrected?" to
            "Yes! Go to 'My Profile → Contact Us' and select the 'Place Info Correction' or 'Add Place' category. " +
            "Please include the place name, address, and details of what needs to be changed, and we'll review it promptly.",
        "The AR feature isn't accurate." to
            "The AR feature uses GPS and sensor data, so accuracy may decrease indoors or in areas with weak GPS signal. " +
            "Make sure location permission is set to 'Always Allow'. " +
            "If the issue persists, please leave a message via Contact Us.",
        "What happens to my data after I delete my account?" to
            "All personal information and settings stored in the app are immediately deleted upon account deletion. " +
            "Data cannot be recovered after deletion, so please decide carefully.",
    )

    override val contactFormTitle = "Leave us a message\nand we'll get back to you soon."
    override val contactCategoryLabel = "Category"
    override val contactCategoryDefault = "Select a category"
    override val contactCategories: List<String> = listOf("Bug Report", "Place Info Correction", "Add a Place", "Feature Request", "Other")
    override val contactTitleLabel = "Title"
    override val contactTitlePlaceholder = "Enter inquiry title (max 60 characters)"
    override val contactBodyLabel = "Details"
    override val contactBodyPlaceholder = "Please describe your inquiry in detail (max 500 characters)"
    override val contactPhotoLabel = "Attach Photos (optional)"
    override val contactPhotoHint = "Attaching screenshots or related photos helps us resolve your issue faster."
    override val contactSubmit = "Submit Inquiry"
    override val contactSubmitting = "Submitting..."
    override val contactNotLoggedIn = "You must be logged in"
    override val contactSubmitError = "Submission failed: please try again later"
    override val contactSuccessTitle = "Inquiry submitted"
    override val contactSuccessDesc = "We'll get back to you as soon as possible.\nA reply will be sent to your registered email."
    override val contactNewInquiry = "New Inquiry"
    override val contactPhotoAdd = "Add Photo"
    override val contactPhotoRemove = "Remove Photo"

    override val loginTermsPrefix = "로그인하면 "
    override val loginTermsOfService = "이용약관"
    override val loginTermsConnector = " 및 "
    override val loginPrivacyPolicy = "개인정보 처리방침"
    override val loginTermsSuffix = "에 동의합니다"

    override val loginFailed = "Login failed"
    override val loginFailedDesc = "Please check your network connection\nand try again"
    override val loginRetry = "Try again"
    override val loginOtherMethod = "Use another login method"

    override val authLoadingDefault = "Please wait"
    override val authLoadingKakao = "Signing in with Kakao..."
    override val authLoadingGoogle = "Signing in with Google..."
    override val authLoadingGeneric = "Signing in..."

    override val termsAge = "만 14세 이상입니다"
    override val termsService = "서비스 이용약관"
    override val termsPrivacy = "개인정보 처리방침"
    override val termsLocation = "위치정보 이용약관"
    override val termsMarketing = "마케팅 정보 수신"
    override val termsRequiredNotice = "서비스 이용을 위해 동의가 필요합니다"
    override val termsAgreeAll = "전체 동의합니다"
    override val termsScreenTitle = "이용 약관"
    override val termsViewMore = "더보기"
    override val termsAgreeAndContinue = "동의하고 계속"

    override val withdrawalTitle = "정말 탈퇴하시겠어요?"
    override val withdrawalDesc = "탈퇴 시 모든 데이터가 삭제되며\n복구할 수 없습니다."
    override val withdrawalDataTitle = "삭제되는 정보"
    override val withdrawalData1 = "프로필 및 계정 정보 (이름, 프로필 사진)"
    override val withdrawalData2 = "저장한 장소 목록"
    override val withdrawalData3 = "최근 검색어 및 방문 기록"
    override val withdrawalData4 = "여행 선호 설정 (할랄 · 비건 · 언어)"
    override val withdrawalConfirmCheck = "위 내용을 모두 확인했습니다"
    override val withdrawalButton = "탈퇴하기"

    override val arInitializing = "Initializing ARCore..."
    override val arLocating = "Locating..."
    override val arVoiceError = "Voice recognition error"
    override val arMicPermission = "Microphone permission required"
    override val arTtsOff = "Voice guide off"
    override val arTtsOn = "Voice guide on"
    override val arScreenFreezing = "Screen frozen"
    override val arVpsScanning = "Scanning VPS..."
    override val arExploring = "AR Exploring"
    override val arScanning = "Scanning"
    override val arUnknownBuilding = "Unknown Building"
    override val arBuilding = "Building"
    override val arAnalyzing = "Analyzing..."
    override val arSaved = "Saved"
    override val arVoiceNotReady = "Voice input not ready"
    override val arVoiceNotAvailable = "Voice recognition not available on this device"
    override val arInitialGreeting = "Hello! I'm ScanPang. Let me guide you to nearby places in AR."
    override val arFilterScanning: (String) -> String = { cat -> "Scanning $cat" }
    override val arFilterScanningMultiple: (String, Int) -> String = { cat, n -> "Scanning $cat +$n more" }
    override val arFilterStoreCount: (String, Int) -> String = { cat, n -> "$cat $n places" }

    override val qiblaPermissionRequired = "Please allow location permission"
    override val qiblaLocating = "Getting location…"
    override val qiblaLocationFormat: (Double, Double) -> String = { lat, lng -> "Location: lat %.5f, lng %.5f".format(lat, lng) }
    override val qiblaLocationUnavailable = "Location: temporarily unavailable"
    override val qiblaLocationFailed = "Location: failed to retrieve"
    override val qiblaDistanceFormat: (Double) -> String = { dist -> "Distance to Mecca: %,.0f km".format(dist) }
    override val qiblaTitle = "Qibla Direction"
    override val qiblaNextPrayer = "Next Prayer Time"
    override val dirN = "N"
    override val dirNE = "NE"
    override val dirE = "E"
    override val dirSE = "SE"
    override val dirS = "S"
    override val dirSW = "SW"
    override val dirW = "W"
    override val dirNW = "NW"

    override val tabHome = "Home"
    override val tabSearch = "Search"
    override val tabSaved = "Saved"
    override val tabProfile = "Profile"
    override val tabExplore = "Explore"

    override val close = "Close"
    override val send = "Send"
    override val viewAll = "View All"

    override val placeOpen = "Open"
    override val placeClosed = "Closed"
    override val placeToday: (String) -> String = { hours -> "Today $hours" }
    override val placeLastOrder: (String) -> String = { t -> "Last order $t" }
    override val placeFeaturedMenu = "Featured Menu"
    override val placeTodayExchangeRate = "Today's Exchange Rate"
    override val placeMaleStalls: (Int) -> String = { n -> "Male $n stalls" }
    override val placeFemaleStalls: (Int) -> String = { n -> "Female $n stalls" }
    override val placeDisabledRestroom = "Accessible Restroom"
    override val placeChildRestroom = "Family Restroom"
    override val placeDiaperTable = "Diaper Table"
    override val placeWudu = "Wudu Facility"
    override val placeGenderSeparated = "Gender Separated"
    override val placePrayerMat = "Prayer Mat"
    override val placeQuran = "Quran Available"
    override val placeEmergencyBell = "Emergency Bell"
    override val placeMuslimChef = "Muslim Chef"
    override val placeNoAlcohol = "No Alcohol"
    override val placeBuildingInfo = "Building Info"
    override val placeFloorInfo = "Floor Info"
    override val placeBookmarked = "Saved"

    override val detailCollapse = "Collapse"
    override val detailExpandMore: (Int) -> String = { n -> "Show more ($n)" }
    override val detailIntro = "Introduction"
    override val detailInfo = "Details"
    override val detailOpenHours = "Hours"
    override val detailAddress = "Address"
    override val detailPhone = "Phone"
    override val detailFloor = "Floor"
    override val detailParking = "Parking"
    override val detailWebsite = "Website"
    override val detailToiletStalls = "Stalls"
    override val detailFacility = "Facilities"
    override val detailSafety = "Safety"
    override val detailDepartments = "Departments"
    override val detailSubwaySchedule = "Train Schedule"
    override val detailSubwayFastAlight = "Quick Exit"
    override val detailSubwayExits = "Exit Info"
    override val detailAtm24h = "24 Hours"
    override val detailAtmHourly = "By Schedule"
    override val detailSubwayUp = "Upbound"
    override val detailSubwayDown = "Downbound"
    override val detailSubwayWeekday = "Weekdays"
    override val detailSubwaySaturday = "Saturday"
    override val detailSubwayHoliday = "Sun & Holidays"
    override val detailSubwayFirst = "First"
    override val detailSubwayLast = "Last"
    override val detailSubwayToward: (String) -> String = { dir -> "Toward $dir" }
    override val detailSubwayExitLabel: (String) -> String = { no -> "Exit $no" }
    override val detailSubwayExitAround: (String) -> String = { no -> "Around Exit $no" }

    override val navInitialMessage = "Ask me anything during navigation!"
    override val navDestination = "Destination"
    override val navGoStraight = "Go straight"
    override val navLocating = "Locating..."
    override val navArrivedBadge = "Arrived"
    override val navGuiding = "Guiding"
    override val navChatPlaceholder = "Ask me anything"
    override val navArrivedMessage = "You have arrived!"
    override val navMap = "Map"
    override val navAiGuide = "AI Guide"
    override val navStop = "Stop Navigation"
    override val navStopConfirm = "Stop navigation?"
    override val navSelectDestination = "Select a destination."
    override val navBackToExplore = "Back to Explore"
    override val navBackToHome = "Back to Home"
    override val navVoiceGuide = "Voice Guide"

    override val arFilter = "Filter"
    override val arFilterReset = "Reset"
    override val arFilterApply = "Apply Filter"
    override val arSearchPlaceholder = "Search places, buildings, shops"
    override val arViewInfo = "View Info"
    override val arNavigate = "Navigate"
    override val arRecentSearches = "Recent Searches"
    override val arClearAll = "Clear All"
}

val LocalStrings = compositionLocalOf<AppStrings> { KoStrings }
