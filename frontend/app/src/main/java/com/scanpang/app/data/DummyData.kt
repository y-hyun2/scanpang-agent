package com.scanpang.app.data

import com.scanpang.app.R
import com.scanpang.app.data.remote.Facility
import com.scanpang.app.data.remote.GeneralRestaurantDetail
import com.scanpang.app.data.remote.HalalRestaurant
import com.scanpang.app.data.remote.NavCandidate
import com.scanpang.app.data.remote.PrayerRoomDetail

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

data class LockerTier(
    val label: String,
    val price: String,
    val available: Boolean,
)

/** drawable이 없을 때 Coil용 URL 폴백과 병합 */
fun Place.galleryModels(fallbackUrls: List<String>): List<Any> =
    if (images.isEmpty()) fallbackUrls else images

private val placeholderImage = listOf(R.drawable.ic_launcher_foreground)

object DummyData {

    val halalRestaurants = listOf(
        RestaurantPlace(
            place = Place(
                id = "r1",
                name = "봉추찜닭 명동점",
                category = "식당",
                distance = "120m",
                address = "서울 중구 명동길 26",
                phone = "02-318-0000",
                openHours = "월-일 11:00-22:00",
                isOpen = true,
                description = "명동 한복판에서 한우와 전통 한식을 할랄 기준으로 즐길 수 있는 공간입니다.",
                tags = listOf("할랄 인증", "무슬림 조리사", "주류 미판매"),
                images = placeholderImage,
            ),
            halalCategory = "HALAL MEAT",
            menuItems = listOf(
                MenuItem("한우 불고기 정식", "15,000원"),
                MenuItem("된장찌개 세트", "9,000원"),
                MenuItem("비빔밥", "10,000원"),
            ),
            lastOrder = "21:20",
            isMoslemChef = true,
            noAlcohol = true,
        ),
        RestaurantPlace(
            place = Place(
                id = "r2",
                name = "레팍라식당",
                category = "식당",
                distance = "500m",
                address = "서울 중구 을지로 12",
                phone = "02-777-1234",
                openHours = "월-일 10:00-21:00",
                isOpen = true,
                description = "말레이시아 전통 요리와 한식을 함께 즐길 수 있는 할랄 식당입니다.",
                tags = listOf("할랄 인증", "말레이시아 요리", "주류 미판매"),
                images = placeholderImage,
            ),
            halalCategory = "HALAL MEAT",
            menuItems = listOf(
                MenuItem("나시르막", "12,000원"),
                MenuItem("사테꼬치", "8,000원"),
            ),
            lastOrder = "20:30",
            isMoslemChef = true,
            noAlcohol = true,
        ),
        RestaurantPlace(
            place = Place(
                id = "r3",
                name = "명동해산물",
                category = "식당",
                distance = "350m",
                address = "서울 중구 명동8나길 5",
                phone = "02-318-5678",
                openHours = "월-일 11:00-22:00",
                isOpen = true,
                description = "신선한 해산물 요리를 제공하는 할랄 인증 식당입니다.",
                tags = listOf("해산물", "주류 미판매"),
                images = placeholderImage,
            ),
            halalCategory = "SEAFOOD",
            menuItems = listOf(
                MenuItem("해물파전", "13,000원"),
                MenuItem("조개찜", "18,000원"),
            ),
            lastOrder = "21:00",
            noAlcohol = true,
        ),
        RestaurantPlace(
            place = Place(
                id = "r4",
                name = "그린가든 명동",
                category = "식당",
                distance = "280m",
                address = "서울 중구 충무로 15",
                phone = "02-318-9999",
                openHours = "월-일 10:00-21:00",
                isOpen = true,
                description = "채식주의자를 위한 비건/채식 메뉴를 제공합니다.",
                tags = listOf("채식", "비건", "주류 미판매"),
                images = placeholderImage,
            ),
            halalCategory = "VEGGIE",
            menuItems = listOf(
                MenuItem("채식 비빔밥", "11,000원"),
                MenuItem("두부 된장찌개", "9,000원"),
            ),
            lastOrder = "20:30",
            noAlcohol = true,
        ),
        RestaurantPlace(
            place = Place(
                id = "r5",
                name = "살람서울 레스토랑",
                category = "식당",
                distance = "450m",
                address = "서울 중구 명동길 40",
                phone = "02-318-7777",
                openHours = "월-일 11:00-22:00",
                isOpen = true,
                description = "무슬림 여행자를 위한 한국-중동 퓨전 요리 레스토랑입니다.",
                tags = listOf("살람서울 인증", "할랄", "주류 미판매"),
                images = placeholderImage,
            ),
            halalCategory = "SALAM SEOUL",
            menuItems = listOf(
                MenuItem("후무스 플레이트", "14,000원"),
                MenuItem("케밥 라이스", "13,000원"),
            ),
            lastOrder = "21:00",
            isMoslemChef = true,
            noAlcohol = true,
        ),
    )

    val prayerRooms = listOf(
        Place(
            id = "p1",
            name = "서울중앙성원 기도실",
            category = "기도실",
            distance = "350m",
            address = "서울 용산구 이태원로 255",
            phone = "02-792-1234",
            openHours = "24시간",
            isOpen = true,
            description = "한국 최초의 이슬람 사원으로 1976년에 건립되었습니다.",
            tags = listOf("남녀분리", "우두시설", "기도매트", "주차가능"),
            images = placeholderImage,
        ),
        Place(
            id = "p2",
            name = "명동 무슬림 기도공간",
            category = "기도실",
            distance = "520m",
            address = "서울 중구 명동길 14 3F",
            phone = "",
            openHours = "09:00-21:00",
            isOpen = true,
            description = "명동 쇼핑 중 기도할 수 있는 무슬림 친화 공간입니다.",
            tags = listOf("남녀분리", "우두시설", "기도매트"),
            images = placeholderImage,
        ),
        Place(
            id = "p3",
            name = "남산타워 기도실",
            category = "기도실",
            distance = "1.2km",
            address = "서울 용산구 남산공원길 105",
            phone = "02-3455-9277",
            openHours = "10:00-23:00",
            isOpen = false,
            description = "남산타워 내 무슬림 여행자를 위한 기도 공간입니다.",
            tags = listOf("기도매트"),
            images = placeholderImage,
        ),
    )

    val cafes = listOf(
        Place(
            id = "c1",
            name = "스타벅스 명동점",
            category = "카페",
            distance = "80m",
            address = "서울 중구 명동길 14",
            phone = "02-318-1234",
            openHours = "07:00-22:00",
            isOpen = true,
            description = "명동 중심에 위치한 스타벅스입니다.",
            tags = listOf("와이파이", "콘센트", "테이크아웃"),
            images = placeholderImage,
        ),
        Place(
            id = "c2",
            name = "투썸플레이스 명동",
            category = "카페",
            distance = "150m",
            address = "서울 중구 을지로 10",
            phone = "02-777-5678",
            openHours = "08:00-22:00",
            isOpen = true,
            description = "케이크와 커피를 즐길 수 있는 카페입니다.",
            tags = listOf("와이파이", "케이크", "테이크아웃"),
            images = placeholderImage,
        ),
    )

    val cafeRepresentativeMenus: Map<String, List<MenuItem>> = mapOf(
        "c1" to listOf(
            MenuItem("아메리카노", "4,500원"),
            MenuItem("카페 라떼", "5,000원"),
            MenuItem("자허블 크림 프라푸치노", "6,300원"),
        ),
        "c2" to listOf(
            MenuItem("스트로베리 초콜릿 생크림", "7,500원"),
            MenuItem("아이스 밀크티", "5,500원"),
        ),
    )

    val shoppingPlaces = listOf(
        Place(
            id = "s1",
            name = "눈스퀘어",
            category = "쇼핑",
            distance = "15m",
            address = "서울 중구 명동 중앙로 26",
            phone = "02-778-1234",
            openHours = "10:00-22:00",
            isOpen = true,
            description = "명동 중심 대형 복합 쇼핑몰. 지하2층~지상8층, 패션·뷰티·F&B 입점.",
            tags = listOf("주차가능", "무료입장", "ATM"),
            images = placeholderImage,
        ),
        Place(
            id = "s2",
            name = "롯데백화점 명동본점",
            category = "쇼핑",
            distance = "300m",
            address = "서울 중구 남대문로 81",
            phone = "02-771-2500",
            openHours = "10:30-20:00",
            isOpen = true,
            description = "국내 최대 규모의 백화점 중 하나입니다.",
            tags = listOf("주차가능", "무슬림 친화", "ATM", "환전"),
            images = placeholderImage,
        ),
    )

    val convenienceStores = listOf(
        Place(
            id = "cv1",
            name = "CU 명동중앙점",
            category = "편의점",
            distance = "50m",
            address = "서울 중구 명동길 26",
            phone = "02-318-0001",
            openHours = "24시간",
            isOpen = true,
            description = "24시간 운영 편의점입니다.",
            tags = listOf("24시간", "ATM", "택배"),
            images = placeholderImage,
        ),
        Place(
            id = "cv2",
            name = "GS25 명동점",
            category = "편의점",
            distance = "120m",
            address = "서울 중구 명동8나길 3",
            phone = "02-318-0002",
            openHours = "24시간",
            isOpen = true,
            description = "24시간 운영 편의점입니다.",
            tags = listOf("24시간", "무인계산대"),
            images = placeholderImage,
        ),
    )

    val atmPlaces = listOf(
        Place(
            id = "a1",
            name = "KEB하나은행 ATM 명동점",
            category = "ATM",
            distance = "80m",
            address = "서울 중구 명동길 14 1F",
            openHours = "24시간",
            isOpen = true,
            description = "외국 카드 사용 가능한 ATM입니다.",
            tags = listOf("VISA", "MASTERCARD", "UnionPay", "24시간"),
            images = placeholderImage,
        ),
        Place(
            id = "a2",
            name = "신한은행 ATM 명동중앙점",
            category = "ATM",
            distance = "200m",
            address = "서울 중구 을지로 12 1F",
            openHours = "24시간",
            isOpen = true,
            description = "외국 카드 사용 가능한 ATM입니다.",
            tags = listOf("VISA", "MASTERCARD", "24시간"),
            images = placeholderImage,
        ),
    )

    val bankPlaces = listOf(
        Place(
            id = "b1",
            name = "KEB하나은행 명동점",
            category = "은행",
            distance = "200m",
            address = "서울 중구 을지로 35",
            phone = "02-777-1000",
            openHours = "09:00-16:00",
            isOpen = true,
            description = "외국인 전용 창구 운영 중입니다.",
            tags = listOf("환전", "외국인계좌", "ATM"),
            images = placeholderImage,
        ),
    )

    val exchangePlaces = listOf(
        Place(
            id = "e1",
            name = "명동 외환센터",
            category = "환전소",
            distance = "180m",
            address = "서울 중구 명동길 26 2F",
            phone = "02-318-2000",
            openHours = "09:00-20:00",
            isOpen = true,
            description = "명동 최고 환율의 환전소입니다.",
            tags = listOf("수수료 없음", "현장환전"),
            images = placeholderImage,
        ),
    )

    val exchangeRates = listOf(
        ExchangeRate("USD", "1,320원", "🇺🇸"),
        ExchangeRate("MYR", "285원", "🇲🇾"),
        ExchangeRate("SAR", "352원", "🇸🇦"),
        ExchangeRate("EUR", "1,430원", "🇪🇺"),
        ExchangeRate("JPY", "8.9원", "🇯🇵"),
    )

    val subwayPlaces = listOf(
        Place(
            id = "sub1",
            name = "명동역",
            category = "지하철역",
            distance = "250m",
            address = "서울 중구 퇴계로 지하 163",
            phone = "02-6110-4314",
            openHours = "05:30-24:00",
            isOpen = true,
            description = "4호선 명동역입니다. 6번 출구: 명동 거리. 7번: 남대문·회현. 8번: 남산 케이블카 연결.",
            tags = listOf("4호선", "엘리베이터", "에스컬레이터", "화장실"),
            images = placeholderImage,
        ),
    )

    val restroomPlaces = listOf(
        Place(
            id = "rest1",
            name = "명동 공중화장실",
            category = "화장실",
            distance = "100m",
            address = "서울 중구 명동길 26 인근",
            openHours = "24시간",
            isOpen = true,
            description = "명동 중심가 공중화장실입니다.",
            tags = listOf("남녀분리", "장애인화장실", "기저귀교환대"),
            images = placeholderImage,
        ),
    )

    val lockerPlaces = listOf(
        Place(
            id = "l1",
            name = "명동역 물품보관함",
            category = "물품보관함",
            distance = "250m",
            address = "서울 중구 명동역 지하 1층",
            openHours = "05:30-24:00",
            isOpen = true,
            description = "명동역 내 물품보관함입니다.",
            tags = listOf("소형 2,000원", "중형 3,000원", "대형 4,000원", "카드결제"),
            images = placeholderImage,
        ),
    )

    val lockerTiers: Map<String, List<LockerTier>> = mapOf(
        "l1" to listOf(
            LockerTier("소형", "2,000원 / 4시간", true),
            LockerTier("중형", "3,000원 / 4시간", true),
            LockerTier("대형", "4,000원 / 4시간", false),
        ),
    )

    val hospitalPlaces = listOf(
        Place(
            id = "h1",
            name = "을지병원 명동",
            category = "병원",
            distance = "400m",
            address = "서울 중구 을지로 170",
            phone = "02-2760-1114",
            openHours = "09:00-18:00",
            isOpen = true,
            description = "외국인 환자 전용 창구 운영 중입니다.",
            tags = listOf("외국어 가능", "내과", "외과", "응급실"),
            images = placeholderImage,
        ),
    )

    val pharmacyPlaces = listOf(
        Place(
            id = "ph1",
            name = "명동 온누리약국",
            category = "약국",
            distance = "90m",
            address = "서울 중구 명동길 14",
            phone = "02-318-3000",
            openHours = "09:00-22:00",
            isOpen = true,
            description = "외국어 가능한 약사가 상주합니다.",
            tags = listOf("영어가능", "외국약품"),
            images = placeholderImage,
        ),
    )

    val touristPlaces = listOf(
        Place(
            id = "t1",
            name = "N서울타워",
            category = "관광지",
            subCategory = "전망대 성인 21,000원~ (현장 기준)",
            distance = "1.8km",
            address = "서울 용산구 남산공원길 105",
            phone = "02-3455-9277",
            openHours = "10:00-22:00",
            isOpen = true,
            description = "서울 전경을 한눈에 담을 수 있는 랜드마크입니다. 케이블카·산책로와 함께 둘러보기 좋습니다.",
            tags = listOf("야경", "필수 코스"),
            images = placeholderImage,
        ),
    )

    /**
     * 통합 PlaceDetailScreen 에서 categoryKey + placeId 로 더미 데이터를 조회.
     * categoryKey 는 백엔드 store_details.category_key 또는 SavedPlaceNavTarget 매핑값.
     * 매칭 없으면 null — 화면은 pop back 으로 fallback.
     *
     * 주의: restaurant 카테고리는 RestaurantPlace.place 를 풀어서 반환. menuItems/lastOrder 같은
     * 식당 전용 필드는 PlaceDetailScreen 에서 별도로 halalRestaurants 를 재조회한다.
     */
    fun findPlaceById(categoryKey: String, placeId: String): Place? {
        val pool: List<Place> = when (categoryKey) {
            "restaurant", "halal_restaurant" -> halalRestaurants.map { it.place }
            "prayer_room" -> prayerRooms
            "tourist", "tourist_spot", "attraction" -> touristPlaces
            "shopping", "mall" -> shoppingPlaces
            "convenience", "convenience_store" -> convenienceStores
            "cafe" -> cafes
            "atm" -> atmPlaces
            "bank" -> bankPlaces
            "exchange" -> exchangePlaces
            "subway", "subway_station" -> subwayPlaces
            "restroom", "public_restroom" -> restroomPlaces
            "lockers", "locker" -> lockerPlaces
            "hospital" -> hospitalPlaces
            "pharmacy" -> pharmacyPlaces
            else -> emptyList()
        }
        return pool.firstOrNull { it.id == placeId } ?: pool.firstOrNull()
    }

    /**
     * 통합 검색·결과 화면에서 더미에 어떤 목록이 들어 있는지 표시할 때 사용.
     * 각 줄: `카테고리명 개수 — 이름들`
     */
    fun searchDummySourceCatalogLines(): List<String> {
        fun joinNames(places: List<Place>): String = places.joinToString(separator = ", ") { it.name }
        fun joinRestaurantNames(list: List<RestaurantPlace>): String =
            list.joinToString(separator = ", ") { it.place.name }
        return listOf(
            "할랄 식당 ${halalRestaurants.size} — ${joinRestaurantNames(halalRestaurants)}",
            "기도실 ${prayerRooms.size} — ${joinNames(prayerRooms)}",
            "카페 ${cafes.size} — ${joinNames(cafes)}",
            "쇼핑 ${shoppingPlaces.size} — ${joinNames(shoppingPlaces)}",
            "편의점 ${convenienceStores.size} — ${joinNames(convenienceStores)}",
            "ATM ${atmPlaces.size} — ${joinNames(atmPlaces)}",
            "은행 ${bankPlaces.size} — ${joinNames(bankPlaces)}",
            "환전소 ${exchangePlaces.size} — ${joinNames(exchangePlaces)}",
            "지하철역 ${subwayPlaces.size} — ${joinNames(subwayPlaces)}",
            "화장실 ${restroomPlaces.size} — ${joinNames(restroomPlaces)}",
            "물품보관함 ${lockerPlaces.size} — ${joinNames(lockerPlaces)}",
            "병원 ${hospitalPlaces.size} — ${joinNames(hospitalPlaces)}",
            "약국 ${pharmacyPlaces.size} — ${joinNames(pharmacyPlaces)}",
            "관광지 ${touristPlaces.size} — ${joinNames(touristPlaces)}",
            "환율표(검색 제외) ${exchangeRates.size}종 — ${exchangeRates.joinToString { it.currency }}",
        )
    }
}

// ── API → UI Model Converters ──

fun HalalRestaurant.toRestaurantPlace(): RestaurantPlace = RestaurantPlace(
    place = Place(
        id = restaurant_id.ifBlank { name_ko },
        name = name_ko,
        category = "식당",
        subCategory = cuisine_type.joinToString(", ").ifBlank { halal_type },
        distance = if (distance_m > 0) "${distance_m.toInt()}m" else "",
        address = address,
        phone = phone,
        openHours = opening_hours,
        isOpen = opening_hours.isNotBlank() && !opening_hours.contains("closed", ignoreCase = true),
        description = short_description_ko.ifBlank {
            buildString {
                append(name_ko)
                if (name_en.isNotBlank()) append(" ($name_en)")
                if (cuisine_type.isNotEmpty()) append(" - ${cuisine_type.joinToString(", ")}")
                append(" 할랄 식당")
            }
        },
        tags = buildList {
            if (halal_type.isNotBlank()) add("할랄 인증")
            if (muslim_cooks_available == true) add("무슬림 조리사")
            if (no_alcohol_sales == true) add("주류 미판매")
        },
        latitude = lat,
        longitude = lng,
    ),
    halalCategory = halal_type,
    menuItems = menu_examples.map { MenuItem(it, "") },
    lastOrder = last_order,
    isMoslemChef = muslim_cooks_available == true,
    noAlcohol = no_alcohol_sales == true,
)

fun PrayerRoomDetail.toPlace(): Place = Place(
    id = name,
    name = name,
    category = "기도실",
    distance = if (distance_m > 0) "${distance_m.toInt()}m" else "",
    address = address,
    openHours = open_hours,
    isOpen = availability_status != "unavailable",
    description = buildString {
        append(name)
        if (floor.isNotBlank()) append(" ($floor)")
    },
    tags = facilities.keys.toList().ifEmpty { listOf("기도실") },
    latitude = lat,
    longitude = lng,
)

fun Facility.toPlace(categoryLabel: String): Place = Place(
    id = name,
    name = name,
    category = categoryLabel,
    distance = if (distance_m > 0) "${distance_m.toInt()}m" else "",
    address = address,
    phone = phone,
    openHours = open_hours,
    isOpen = true,
    description = extra.entries.joinToString(", ") { "${it.key}: ${it.value}" }.ifBlank { name },
    tags = extra.keys.toList(),
    latitude = lat,
    longitude = lng,
)

fun GeneralRestaurantDetail.toRestaurantPlace(): RestaurantPlace = RestaurantPlace(
    place = Place(
        id = restaurant_id.ifBlank { name_ko },
        name = name_ko,
        category = "식당",
        subCategory = cuisine_type.joinToString(", "),
        distance = "",
        address = address,
        phone = phone,
        openHours = opening_hours,
        isOpen = is_open_today,
        description = short_description_ko.ifBlank { name_ko },
        tags = food_keywords.take(3),
        latitude = latitude ?: 37.5636,
        longitude = longitude ?: 126.9869,
    ),
    halalCategory = if (source == "halal") "HALAL MEAT" else "",
    menuItems = menu_examples.map {
        MenuItem(
            name = it.name_ko,
            price = if (it.price_krw > 0) "%,d원".format(it.price_krw) else "",
        )
    },
    lastOrder = last_order,
)

fun NavCandidate.toPlace(): Place = Place(
    id = poi_id,
    name = name,
    category = "",
    distance = "",
    address = address,
    latitude = pns_lat,
    longitude = pns_lon,
)
