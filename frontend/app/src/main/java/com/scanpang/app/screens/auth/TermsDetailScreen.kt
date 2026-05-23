package com.scanpang.app.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scanpang.app.i18n.LocalStrings
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangSpacing
import com.scanpang.app.ui.theme.ScanPangType

private data class TermsContent(val title: String, val body: String)

private val TERMS_CONTENT = mapOf(
    "service" to TermsContent(
        title = "서비스 이용약관",
        body = """
제1조 (목적)
본 약관은 스캔팡(이하 "회사")이 제공하는 AR 기반 여행 정보 서비스(이하 "서비스")의 이용과 관련하여 회사와 이용자 간의 권리·의무 및 책임사항을 규정함을 목적으로 합니다.

제2조 (정의)
① "서비스"란 회사가 제공하는 AR 장소 탐색, 검색, 저장, 길안내 등 일체의 기능을 의미합니다.
② "이용자"란 본 약관에 동의하고 서비스를 이용하는 자를 말합니다.
③ "계정"이란 이용자가 서비스 이용을 위해 소셜 로그인으로 생성한 식별 단위를 말합니다.

제3조 (서비스 내용)
회사는 다음의 서비스를 제공합니다.
• AR 카메라 기반 주변 장소 탐색
• 할랄·비건 음식점 정보 제공
• 장소 검색 및 즐겨찾기
• AR 길안내

제4조 (이용자 의무)
이용자는 다음 행위를 하여서는 안 됩니다.
• 타인의 정보를 도용하거나 허위 정보를 입력하는 행위
• 서비스를 이용한 상업적 광고 또는 스팸 행위
• 회사의 사전 승인 없이 서비스를 복제·배포하는 행위
• 기타 관련 법령 및 회사 정책에 위반하는 행위

제5조 (서비스 이용 제한)
회사는 이용자가 본 약관을 위반한 경우 사전 통보 없이 서비스 이용을 제한하거나 계정을 해지할 수 있습니다.

제6조 (면책 조항)
① 회사는 천재지변, 불가항력적 사유로 인한 서비스 중단에 대해 책임을 지지 않습니다.
② 회사는 이용자 간 또는 이용자와 제3자 간의 분쟁에 개입하지 않으며, 그 결과로 인한 손해를 배상할 책임이 없습니다.
③ 제공되는 장소 정보는 외부 데이터를 활용하며, 정보의 정확성에 대해 완전한 보증을 하지 않습니다.

제7조 (약관 변경)
회사는 합리적인 이유가 발생한 경우 약관을 변경할 수 있으며, 변경 시 앱 공지 또는 이메일을 통해 사전 고지합니다.

부칙
본 약관은 2025년 1월 1일부터 시행됩니다.
        """.trimIndent(),
    ),
    "privacy" to TermsContent(
        title = "개인정보 처리방침",
        body = """
스캔팡(이하 "회사")은 이용자의 개인정보를 소중히 여기며, 「개인정보 보호법」 등 관련 법령에 따라 이용자의 개인정보를 보호하기 위해 최선을 다합니다.

1. 수집하는 개인정보 항목
• 소셜 로그인 정보: 이름, 이메일 주소, 프로필 사진 (선택)
• 앱 내 입력 정보: 표시 이름, 선호 언어, 여행 선호 유형
• 자동 수집 정보: 기기 식별자, 앱 사용 로그, 위치 정보 (허용 시)

2. 개인정보의 이용 목적
• 서비스 제공 및 계정 관리
• AR 기반 주변 장소 탐색 및 거리 계산
• 서비스 개선 및 통계 분석
• 마케팅 정보 발송 (동의 시에 한함)

3. 개인정보 보유 및 이용 기간
• 회원 탈퇴 시 즉시 삭제
• 단, 관련 법령에 따라 일정 기간 보관이 필요한 경우 해당 기간 동안 보관

4. 개인정보의 제3자 제공
회사는 이용자의 동의 없이 개인정보를 제3자에게 제공하지 않습니다. 다만, 법률에 의한 요청이 있는 경우 예외로 합니다.

5. 이용자의 권리
이용자는 언제든지 자신의 개인정보를 열람, 수정, 삭제를 요청할 수 있습니다. 회원탈퇴를 통해 모든 데이터를 삭제할 수 있습니다.

6. 개인정보 보호책임자
개인정보 관련 문의는 앱 내 문의하기를 통해 접수하실 수 있습니다.

시행일: 2025년 1월 1일
        """.trimIndent(),
    ),
    "location" to TermsContent(
        title = "위치정보 이용약관",
        body = """
제1조 (목적)
본 약관은 스캔팡(이하 "회사")이 위치정보의 보호 및 이용 등에 관한 법률에 따라 이용자의 위치정보를 수집·이용·제공하는 것에 관한 사항을 규정합니다.

제2조 (위치정보의 수집 및 이용 목적)
회사는 다음 목적으로 이용자의 위치정보를 수집·이용합니다.
• AR 카메라에 주변 장소 및 방향 표시
• 현재 위치로부터의 거리 계산 및 표시
• 주변 음식점·편의시설 검색
• AR 길안내 서비스 제공

제3조 (위치정보 수집 방법)
기기의 GPS, Wi-Fi, 셀룰러 네트워크를 통해 수집합니다. 이용자가 위치 권한을 거부할 경우 일부 기능이 제한될 수 있습니다.

제4조 (위치정보 보유 기간)
위치정보는 서비스 제공 목적 달성 후 즉시 폐기됩니다. 이용 기록은 최대 1개월 보관 후 삭제됩니다.

제5조 (위치정보 제3자 제공)
이용자의 동의 없이 위치정보를 제3자에게 제공하지 않습니다. 단, 법원·수사기관 등 법적 요청이 있는 경우 예외입니다.

제6조 (이용자의 권리)
이용자는 앱 설정 또는 기기 설정에서 언제든지 위치정보 수집을 거부할 수 있습니다.

시행일: 2025년 1월 1일
        """.trimIndent(),
    ),
    "marketing" to TermsContent(
        title = "마케팅 정보 수신 동의",
        body = """
스캔팡의 마케팅 정보 수신에 동의하시면 다음과 같은 혜택을 받으실 수 있습니다.

1. 수신 동의 내용
• 새로운 서비스 및 기능 안내
• 주변 음식점·여행 관련 특별 프로모션 정보
• 할랄·비건 관련 이벤트 및 혜택 알림

2. 발송 방법 및 주기
• 발송 방법: 앱 푸시 알림
• 발송 주기: 월 2회 이내 (이벤트 기간 예외 적용 가능)

3. 동의 철회 방법
• 앱 내 프로필 → 알림 설정에서 언제든지 철회 가능
• 마케팅 수신 거부 시에도 서비스 관련 필수 알림은 수신됩니다

4. 유의 사항
본 동의는 선택 사항이며, 동의하지 않아도 서비스 이용에 제한이 없습니다.
        """.trimIndent(),
    ),
)

@Composable
fun TermsDetailScreen(
    termId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val content = TERMS_CONTENT[termId] ?: return

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ScanPangColors.Surface,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ScanPangColors.Surface)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = ScanPangSpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.md),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBackIos,
                    contentDescription = s.back,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(onClick = onBack),
                    tint = ScanPangColors.OnSurfaceStrong,
                )
                Text(
                    text = content.title,
                    style = ScanPangType.profileName18,
                    color = ScanPangColors.OnSurfaceStrong,
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ScanPangSpacing.lg)
                    .padding(top = ScanPangSpacing.md, bottom = ScanPangSpacing.xl),
            ) {
                Text(
                    text = content.body,
                    style = ScanPangType.body14Regular,
                    color = ScanPangColors.OnSurfaceMuted,
                )
            }
        }
    }
}
