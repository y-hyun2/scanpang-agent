package com.scanpang.app.data

import com.scanpang.app.data.remote.PlaceDetailResponse
import java.util.Calendar

/**
 * 지하철역 카테고리(`category_key="subway"`) 매장의 상세 화면 전용 모델.
 *
 * 백엔드 `/place/detail` 응답의 `details` Map(JSONB)에서 [toSubwayDetail] 로 변환됨.
 * 백엔드 fetcher: `tools/details_fetchers/seoul_metro.py` — 4개 소스 통합
 *   - subway_exits(CSV) → exits[].facilities
 *   - 카카오 로컬 → exits[].lat/lng (UI 미사용)
 *   - TAGO → schedule.weekday_up/down
 *   - getFstExit(빠른하차) → fast_alights[]
 */
data class SubwayExit(
    val exitNo: String,
    val facilities: List<String>,
)

data class SubwayScheduleDir(
    val first: String,
    val last: String,
    val toward: String,
)

data class SubwayFastAlight(
    val direction: String,
    val updown: String,
    val door: String,
    val fac: String,
    val walkPos: String,
    val facPos: String = "",
)

/**
 * 요일 분류 — TAGO `dailyTypeCode`와 1:1 매핑.
 * - WEEKDAY: 평일 (01)
 * - SATURDAY: 토요일 (02)
 * - HOLIDAY: 일·공휴일 (03)
 */
enum class ScheduleDay { WEEKDAY, SATURDAY, HOLIDAY }

data class SubwayDetail(
    val line: String = "",
    val exitCount: Int = 0,
    val exits: List<SubwayExit> = emptyList(),
    // 평일 (기존 호환용 — 매퍼가 weekday_* 키에서 채움)
    val scheduleUp: SubwayScheduleDir? = null,
    val scheduleDown: SubwayScheduleDir? = null,
    // 토요일 / 일·공휴일
    val saturdayUp: SubwayScheduleDir? = null,
    val saturdayDown: SubwayScheduleDir? = null,
    val holidayUp: SubwayScheduleDir? = null,
    val holidayDown: SubwayScheduleDir? = null,
    val fastAlights: List<SubwayFastAlight> = emptyList(),
) {
    /** 오늘 날짜 기준 요일 분류 — UI 표시용. (한국 공휴일 별도 데이터 없으니 일요일=HOLIDAY) */
    fun todayKind(): ScheduleDay = when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
        Calendar.SATURDAY -> ScheduleDay.SATURDAY
        Calendar.SUNDAY   -> ScheduleDay.HOLIDAY
        else              -> ScheduleDay.WEEKDAY
    }

    /** 요일 분류 → (상행, 하행) 시간표. 해당 요일이 비면 평일로 fallback (예: 토요일 데이터 누락 시). */
    fun scheduleFor(day: ScheduleDay): Pair<SubwayScheduleDir?, SubwayScheduleDir?> = when (day) {
        ScheduleDay.WEEKDAY  -> scheduleUp to scheduleDown
        ScheduleDay.SATURDAY -> (saturdayUp ?: scheduleUp) to (saturdayDown ?: scheduleDown)
        ScheduleDay.HOLIDAY  -> (holidayUp  ?: scheduleUp) to (holidayDown  ?: scheduleDown)
    }
}


// ── 매퍼 ────────────────────────────────────────────────────────────────

/**
 * `/place/detail` 응답의 `details` Map → 지하철 전용 모델.
 * `category_key`가 subway/subway_station이 아니면 null.
 * details에 필수 키가 없어도 부분 필드만 채워서 반환.
 */
fun PlaceDetailResponse.toSubwayDetail(): SubwayDetail? {
    if (category_key !in setOf("subway", "subway_station")) return null
    val d = details

    val exits = (d["exits"] as? List<*>)?.mapNotNull { exit ->
        val m = exit as? Map<*, *> ?: return@mapNotNull null
        val exitNo = (m["exit_no"] as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        SubwayExit(
            exitNo = exitNo,
            facilities = (m["facilities"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        )
    } ?: emptyList()

    fun mapDir(key: String): SubwayScheduleDir? {
        val schedule = d["schedule"] as? Map<*, *> ?: return null
        val dir = schedule[key] as? Map<*, *> ?: return null
        val first  = (dir["first"]  as? String).orEmpty()
        val last   = (dir["last"]   as? String).orEmpty()
        val toward = (dir["toward"] as? String).orEmpty()
        if (first.isBlank() && last.isBlank() && toward.isBlank()) return null
        return SubwayScheduleDir(first = first, last = last, toward = toward)
    }

    val fastAlights = (d["fast_alights"] as? List<*>)?.mapNotNull { fa ->
        val m = fa as? Map<*, *> ?: return@mapNotNull null
        SubwayFastAlight(
            direction = (m["direction"] as? String).orEmpty(),
            updown    = (m["updown"]    as? String).orEmpty(),
            door      = (m["door"]      as? String).orEmpty(),
            fac       = (m["fac"]       as? String).orEmpty(),
            walkPos   = (m["walk_pos"]  as? String).orEmpty(),
            facPos    = (m["fac_pos"]   as? String).orEmpty(),
        )
    } ?: emptyList()

    return SubwayDetail(
        line         = (d["line"] as? String).orEmpty(),
        exitCount    = (d["exit_count"] as? Number)?.toInt() ?: exits.size,
        exits        = exits,
        scheduleUp   = mapDir("weekday_up"),
        scheduleDown = mapDir("weekday_down"),
        saturdayUp   = mapDir("saturday_up"),
        saturdayDown = mapDir("saturday_down"),
        holidayUp    = mapDir("holiday_up"),
        holidayDown  = mapDir("holiday_down"),
        fastAlights  = fastAlights,
    )
}
