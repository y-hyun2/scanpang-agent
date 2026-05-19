package com.scanpang.app.screens.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangDimens
import com.scanpang.app.ui.theme.ScanPangShapes
import com.scanpang.app.ui.theme.ScanPangSpacing
import com.scanpang.app.ui.theme.ScanPangType

private val faqItems = listOf(
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

@Composable
fun HelpScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ScanPangColors.Background,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ScanPangColors.Background)
                .statusBarsPadding(),
        ) {
            SettingsTitleBar(
                title = "도움말",
                onBack = { navController.popBackStack() },
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = ScanPangDimens.screenHorizontal),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = ScanPangSpacing.lg,
                    bottom = ScanPangDimens.mainTabContentBottomInset + ScanPangSpacing.xl,
                ),
            ) {
                item {
                    Text(
                        text = "자주 묻는 질문",
                        style = ScanPangType.sectionTitle,
                        color = ScanPangColors.OnSurfaceStrong,
                    )
                    Spacer(modifier = Modifier.height(ScanPangSpacing.lg))
                }
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ScanPangShapes.radius16)
                            .border(
                                ScanPangDimens.borderHairline,
                                ScanPangColors.OutlineSubtle,
                                ScanPangShapes.radius16,
                            )
                            .background(ScanPangColors.Surface),
                    ) {
                        faqItems.forEachIndexed { index, (q, a) ->
                            FaqItem(question = q, answer = a)
                            if (index < faqItems.lastIndex) {
                                HorizontalDivider(
                                    thickness = ScanPangDimens.borderHairline,
                                    color = ScanPangColors.OutlineSubtle,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FaqItem(question: String, answer: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(horizontal = ScanPangSpacing.lg, vertical = ScanPangSpacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = "Q",
                    style = ScanPangType.sectionLabelSemiBold13,
                    color = ScanPangColors.Primary,
                )
                Text(
                    text = question,
                    style = ScanPangType.body15Medium,
                    color = ScanPangColors.OnSurfaceStrong,
                    modifier = Modifier.weight(1f),
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = ScanPangColors.OnSurfaceMuted,
                modifier = Modifier
                    .size(20.dp)
                    .padding(top = 2.dp),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column {
                Spacer(modifier = Modifier.height(ScanPangSpacing.sm))
                HorizontalDivider(
                    thickness = ScanPangDimens.borderHairline,
                    color = ScanPangColors.OutlineSubtle,
                )
                Spacer(modifier = Modifier.height(ScanPangSpacing.sm))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = "A",
                        style = ScanPangType.sectionLabelSemiBold13,
                        color = ScanPangColors.Success,
                    )
                    Text(
                        text = answer,
                        style = ScanPangType.body14Regular,
                        color = ScanPangColors.OnSurfaceMuted,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
