package com.scanpang.app.data

import com.scanpang.app.data.remote.PlaceDetailResponse

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

data class SubwayDetail(
    val line: String = "",
    val exitCount: Int = 0,
    val exits: List<SubwayExit> = emptyList(),
    val scheduleUp: SubwayScheduleDir? = null,
    val scheduleDown: SubwayScheduleDir? = null,
    val fastAlights: List<SubwayFastAlight> = emptyList(),
)


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
        line       = (d["line"] as? String).orEmpty(),
        exitCount  = (d["exit_count"] as? Number)?.toInt() ?: exits.size,
        exits      = exits,
        scheduleUp = mapDir("weekday_up"),
        scheduleDown = mapDir("weekday_down"),
        fastAlights = fastAlights,
    )
}
