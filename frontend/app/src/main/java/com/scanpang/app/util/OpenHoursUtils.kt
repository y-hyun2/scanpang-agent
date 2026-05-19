package com.scanpang.app.util

import java.util.Calendar

/**
 * 영업 시간 원문(open_hours) 파싱 유틸.
 *
 * - todayHoursText: "오늘 방문 가능 여부" 섹션용 — 오늘 요일 시간만 추출
 * - groupedHoursText: "상세 정보" 섹션용 — 전 요일을 같은 시간끼리 묶어 반환
 *
 * 지원 입력 형식:
 *   "월-일 11:00-22:00"  /  "월-금 09:00-18:00\n토-일 10:00-17:00"
 *   "09:00-22:00"  /  "24시간"  /  "연중무휴"  /  "월 휴무"
 */
object OpenHoursUtils {

    private val DAY_SHORT = listOf("월", "화", "수", "목", "금", "토", "일")
    private val DAY_FULL  = listOf("월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일")

    private val ALWAYS_OPEN      = listOf("24시간", "연중무휴")
    // "휴무일"을 "휴무" 보다 먼저 처리해 "일" 잔류 방지
    private val CLOSED_KEYWORDS  = listOf("휴무일", "휴무", "휴업", "closed")

    private val TIME_RANGE = Regex("""(\d{1,2}:\d{2})\s*[-~–—]\s*(\d{1,2}:\d{2})""")
    private val DAY_RANGE  = Regex("""([월화수목금토일])\s*[-~–—]\s*([월화수목금토일])""")

    /** 0=월 … 6=일 */
    fun todayIndex(): Int = when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY    -> 0
        Calendar.TUESDAY   -> 1
        Calendar.WEDNESDAY -> 2
        Calendar.THURSDAY  -> 3
        Calendar.FRIDAY    -> 4
        Calendar.SATURDAY  -> 5
        else               -> 6  // SUNDAY
    }

    /**
     * 오늘 방문 가능 여부 섹션에 표시할 시간 텍스트.
     * - "월-일 11:00-22:00" → (오늘이 화요일이면) "11:00-22:00"
     * - "24시간" → "24시간"
     * - 오늘 휴무이면 "" 반환
     * - 파싱 불가 시 원문 반환
     */
    fun todayHoursText(openHours: String): String {
        if (openHours.isBlank()) return ""
        val text = openHours.trim()
        if (ALWAYS_OPEN.any { text.contains(it) }) return "24시간"

        val schedule = parseSchedule(text)
        if (schedule.isEmpty()) return text          // 파싱 실패 → 원문 fallback

        return schedule[todayIndex()] ?: ""          // 오늘 휴무이면 빈 문자열
    }

    /**
     * 상세 정보 섹션에 표시할 전 요일 영업 시간.
     * 같은 시간인 연속 요일은 "월요일~금요일", 7일 모두 같으면 "매일"로 묶음.
     * - "월-일 11:00-22:00" → "매일  11:00-22:00"
     * - "월-금 09:00-18:00\n토-일 10:00-17:00"
     *       → "월요일~금요일  09:00-18:00\n토요일~일요일  10:00-17:00"
     * - 파싱 불가 시 원문 반환
     */
    fun groupedHoursText(openHours: String): String {
        if (openHours.isBlank()) return ""
        val text = openHours.trim()
        if (ALWAYS_OPEN.any { text.contains(it) }) return "매일  24시간"

        val schedule = parseSchedule(text)
        if (schedule.isEmpty()) return text

        val filled = Array(7) { i -> schedule[i] ?: "휴무" }

        val sb = StringBuilder()
        var i = 0
        while (i < 7) {
            val hours = filled[i]
            var j = i + 1
            while (j < 7 && filled[j] == hours) j++

            val dayLabel = when {
                j - i == 7 -> "매일"
                j - i == 1 -> DAY_FULL[i]
                else        -> "${DAY_FULL[i]}~${DAY_FULL[j - 1]}"
            }
            if (sb.isNotEmpty()) sb.append("\n")
            sb.append("$dayLabel  $hours")
            i = j
        }
        return sb.toString()
    }

    // ── 내부 파싱 ──────────────────────────────────────────────────────────────

    /** 원문 → 요일 인덱스(0=월…6=일) → 시간 문자열 맵 */
    private fun parseSchedule(text: String): Map<Int, String> {
        val result = mutableMapOf<Int, String>()
        // "월 11:00-19:00 / 화 11:00-19:00 / ..." 형식과 줄바꿈 형식 모두 지원
        val lines = text.split(Regex("""\n|\s*/\s*""")).map { it.trim() }.filter { it.isNotEmpty() }

        for (line in lines) {
            val timeMatch = TIME_RANGE.find(line)
            if (timeMatch != null) {
                val timeStr = "${timeMatch.groupValues[1]}-${timeMatch.groupValues[2]}"
                val before  = line.substring(0, timeMatch.range.first).trim()
                val days    = if (before.isEmpty()) (0..6).toList() else parseDays(before)
                days.forEach { d -> result[d] = timeStr }
            } else if (CLOSED_KEYWORDS.any { line.contains(it, ignoreCase = true) }) {
                val dayPart = CLOSED_KEYWORDS
                    .fold(line) { acc, kw -> acc.replace(kw, "", ignoreCase = true) }.trim()
                val days = if (dayPart.isEmpty()) (0..6).toList() else parseDays(dayPart)
                days.forEach { d -> result[d] = "휴무" }
            }
        }
        return result
    }

    /** "월-금" / "월" / "월·화·수" / "매일" → 인덱스 리스트 */
    private fun parseDays(spec: String): List<Int> {
        if (spec.contains("매일")) return (0..6).toList()

        // 범위: "월-금", "월~일" 등
        val rangeMatch = DAY_RANGE.find(spec)
        if (rangeMatch != null) {
            val s = DAY_SHORT.indexOf(rangeMatch.groupValues[1])
            val e = DAY_SHORT.indexOf(rangeMatch.groupValues[2])
            if (s >= 0 && e >= 0) {
                return if (s <= e) (s..e).toList()
                else (s..6).toList() + (0..e).toList()
            }
        }

        // 개별 요일: "월", "월요일", "월·화", "월,화" 등
        return DAY_SHORT.mapIndexedNotNull { idx, d ->
            if (spec.contains(d)) idx else null
        }
    }
}