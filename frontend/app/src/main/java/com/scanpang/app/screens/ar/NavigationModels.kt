package com.scanpang.app.screens.ar

/**
 * AR 길안내에서 사용하는 회전 방향.
 *
 * 도착(Arrive) 상태는 [NavigationPhase.Arrived]로 표현하므로 enum에 포함하지 않는다.
 */
enum class TurnDirection {
    Left,
    Right,
    Straight,
    UTurn,
}

/**
 * 길안내 진행 단계.
 *
 * - [Cruising]    : 일반 주행 — 다음 회전까지 거리/지시를 카드로 안내한다. (스크린샷 1)
 * - [Approaching] : 목적지 임박 — "목적지가 바로 앞에 있습니다!" 단일 카드. (스크린샷 2)
 * - [Arrived]     : 도착 완료 — 상단 pill이 초록으로 바뀌고 가운데 체크 배지가 표시된다. (스크린샷 3)
 */
sealed class NavigationPhase {
    object Cruising : NavigationPhase()
    object Approaching : NavigationPhase()
    object Arrived : NavigationPhase()
}

/**
 * 한 번의 회전/주행 지시.
 *
 * @property direction    회전 방향. Approaching 단계에서는 [TurnDirection.Straight]를 사용한다.
 * @property distanceLabel 사용자에게 노출할 거리 라벨 (예: "80m").
 * @property message      카드에 표기할 자연어 안내 문구
 *                        (예: "스타벅스에서 좌회전", "목적지가 바로 앞에 있습니다!").
 */
data class TurnInstruction(
    val direction: TurnDirection,
    val distanceLabel: String,
    val message: String,
)

/**
 * AR 길안내 화면 단일 진실 공급원(SSOT) UI 상태.
 *
 * @property phase              현재 안내 단계.
 * @property destinationName    목적지 이름 (예: "명동성당"). 상단 pill 텍스트의 베이스가 된다.
 * @property currentInstruction 현재 사용자에게 보여줄 회전/주행 지시.
 *                              [NavigationPhase.Arrived] 상태에서는 `null` 이다.
 */
data class NavigationUiState(
    val phase: NavigationPhase,
    val destinationName: String,
    val currentInstruction: TurnInstruction?,
)

/**
 * 디버그/Compose Preview 용 샘플 상태 모음.
 *
 * 첨부된 피그마 시안의 3가지 상태와 1:1로 대응한다.
 */
object NavigationSamples {

    /** 스크린샷 1 — Cruising: 좌회전 안내 ("명동성당 안내 중"). */
    val Cruising: NavigationUiState = NavigationUiState(
        phase = NavigationPhase.Cruising,
        destinationName = "명동성당",
        currentInstruction = TurnInstruction(
            direction = TurnDirection.Left,
            distanceLabel = "80m",
            message = "스타벅스에서 좌회전",
        ),
    )

    /** 스크린샷 2 — Approaching: 목적지 임박, 직진 화살표. */
    val Approaching: NavigationUiState = NavigationUiState(
        phase = NavigationPhase.Approaching,
        destinationName = "명동성당",
        currentInstruction = TurnInstruction(
            direction = TurnDirection.Straight,
            distanceLabel = "80m",
            message = "목적지가 바로 앞에 있습니다!",
        ),
    )

    /** 스크린샷 3 — Arrived: "명동성당 도착", 가운데 체크 배지. */
    val Arrived: NavigationUiState = NavigationUiState(
        phase = NavigationPhase.Arrived,
        destinationName = "명동성당",
        currentInstruction = null,
    )

    /** 세 단계를 순서대로 묶은 리스트 — Preview 파라미터 등으로 활용. */
    val All: List<NavigationUiState> = listOf(Cruising, Approaching, Arrived)
}
