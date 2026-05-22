package com.scanpang.app.components.ar

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.DirectionsTransit
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.LocalAtm
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.LocalMall
import androidx.compose.material.icons.rounded.Luggage
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Store
import androidx.compose.material.icons.rounded.Wc
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Headset
import androidx.compose.material.icons.rounded.HeadsetOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangDimens
import com.scanpang.app.ui.theme.ScanPangShapes
import com.scanpang.app.ui.theme.ScanPangSpacing
import com.scanpang.app.ui.theme.ScanPangType

@Composable
fun ArTopGradientBar(
    modifier: Modifier = Modifier,
    onHomeClick: () -> Unit,
    onSearchClick: () -> Unit,
    centerContent: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        ScanPangColors.ArTopGradientStart,
                        ScanPangColors.ArTopGradientEnd,
                    ),
                ),
            )
            .statusBarsPadding()
            .padding(horizontal = ScanPangDimens.arTopBarHorizontal)
            .padding(bottom = ScanPangDimens.arTopBarBottomPadding),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(
                    maxOf(
                        ScanPangDimens.arSideFab44,
                        ScanPangDimens.arStatusPillHeight,
                    ),
                ),
        ) {
            ArCircleIconButton(
                icon = Icons.Rounded.Home,
                contentDescription = "홈",
                onClick = onHomeClick,
                modifier = Modifier.align(Alignment.CenterStart),
            )
            Box(
                modifier = Modifier.align(Alignment.Center),
                contentAlignment = Alignment.Center,
            ) {
                centerContent()
            }
            ArCircleIconButton(
                icon = Icons.Rounded.Search,
                contentDescription = "검색",
                onClick = onSearchClick,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

@Composable
fun ArCircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    surfaceColor: Color = ScanPangColors.ArOverlayWhite80,
    iconTint: Color = ScanPangColors.OnSurfaceStrong,
) {
    Surface(
        modifier = modifier
            .size(ScanPangDimens.arSideFab44)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = surfaceColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(ScanPangDimens.icon20),
                tint = iconTint,
            )
        }
    }
}

@Composable
fun ArStatusPillNeutral(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val mod = if (onClick != null) {
        modifier.clickable(onClick = onClick)
    } else {
        modifier
    }
    Surface(
        modifier = mod.height(ScanPangDimens.arStatusPillHeight),
        shape = CircleShape,
        color = ScanPangColors.ArOverlayWhite80,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = ScanPangDimens.arStatusPillHorizontalPad),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
        ) {
            Icon(
                imageVector = Icons.Rounded.CropFree,
                contentDescription = null,
                modifier = Modifier.size(ScanPangDimens.icon18),
                tint = ScanPangColors.OnSurfaceStrong,
            )
            Text(
                text = text,
                style = ScanPangType.arStatusPill15,
                color = ScanPangColors.OnSurfaceStrong,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun ArStatusPillPrimary(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val mod = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Surface(
        modifier = mod.height(ScanPangDimens.arStatusPillHeight),
        shape = CircleShape,
        color = ScanPangColors.Primary,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = ScanPangDimens.arStatusPillHorizontalPad),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(ScanPangDimens.icon18),
                tint = Color.White,
            )
            Text(
                text = text,
                style = ScanPangType.arStatusPill15,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun ArSideActionColumn(
    onVolumeClick: () -> Unit,
    onCameraClick: () -> Unit,
    modifier: Modifier = Modifier,
    cameraSurfaceColor: Color = ScanPangColors.ArOverlayWhite93,
    cameraIconTint: Color = ScanPangColors.OnSurfaceStrong,
) {
    Column(
        modifier = modifier.width(ScanPangDimens.arSideColumnWidth),
        verticalArrangement = Arrangement.spacedBy(ScanPangDimens.arSideIconGap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArSideFab(
            icon = Icons.AutoMirrored.Rounded.VolumeUp,
            contentDescription = "볼륨",
            onClick = onVolumeClick,
            surfaceColor = ScanPangColors.ArOverlayWhite85,
        )
        ArSideFab(
            icon = Icons.Rounded.CameraAlt,
            contentDescription = "촬영",
            onClick = onCameraClick,
            surfaceColor = cameraSurfaceColor,
            iconTint = cameraIconTint,
        )
    }
}

@Composable
private fun ArSideFab(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    surfaceColor: Color,
    iconTint: Color = ScanPangColors.OnSurfaceStrong,
) {
    Surface(
        modifier = Modifier
            .size(ScanPangDimens.arSideFab44)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = surfaceColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(ScanPangDimens.icon20),
                tint = iconTint,
            )
        }
    }
}

@Composable
fun ArPoiCard(
    title: String,
    subtitle: String,
    category: String = "",
    extraCount: Int = 0,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Box(modifier = modifier) {
        val clickMod = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        Surface(
            modifier = Modifier
                .wrapContentWidth()
                .heightIn(min = ScanPangDimens.arPoiCardHeight)
                .then(clickMod),
            shape = ScanPangShapes.arPoiCard,
            color = ScanPangColors.Surface,
            shadowElevation = ScanPangDimens.arPoiCardShadowElevation,
        ) {
            Row(
                modifier = Modifier
                    .padding(
                        horizontal = ScanPangDimens.arPoiCardHorizontalPad,
                        vertical = ScanPangDimens.arPoiCardVerticalPad,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
            ) {
                ArCategoryIconBadge(
                    category = category,
                    badgeSize = ScanPangDimens.arPoiIcon24.value.toInt(),
                    iconSize = ScanPangDimens.icon14.value.toInt(),
                )
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    Text(
                        text = title,
                        style = ScanPangType.chip13SemiBold,
                        color = ScanPangColors.ArPoiTitle,
                    )
                    Text(
                        text = subtitle,
                        style = ScanPangType.meta11Medium,
                        color = ScanPangColors.ArPoiSubtitle,
                    )
                }
            }
        }
        if (extraCount > 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-6).dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE53935)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = extraCount.toString(),
                    style = ScanPangType.meta11Medium,
                    color = Color.White,
                )
            }
        }
    }
}

fun categoryIcon(category: String): ImageVector = when {
    category.contains("쇼핑") || category.contains("의류") -> Icons.Rounded.LocalMall
    category.contains("편의점") -> Icons.Rounded.Store
    category.contains("카페") || category.contains("커피") -> Icons.Rounded.Coffee
    category.contains("식당") || category.contains("음식") || category.contains("한식") ||
        category.contains("패스트") -> Icons.Rounded.Restaurant
    category.contains("환전") -> Icons.Rounded.CurrencyExchange
    category.contains("은행") -> Icons.Rounded.AccountBalance
    category.contains("ATM") || category.contains("atm") -> Icons.Rounded.LocalAtm
    category.contains("병원") -> Icons.Rounded.LocalHospital
    category.contains("약국") -> Icons.Rounded.Medication
    category.contains("지하철") -> Icons.Rounded.DirectionsTransit
    category.contains("화장실") -> Icons.Rounded.Wc
    category.contains("물품") || category.contains("보관") -> Icons.Rounded.Luggage
    category.contains("숙박") || category.contains("호텔") || category.contains("accommodation") -> Icons.Rounded.Hotel
    else -> Icons.Rounded.Place
}

fun categoryIconTint(category: String): Color = when {
    category.contains("쇼핑") || category.contains("의류") -> ScanPangColors.CategoryMall
    category.contains("편의점") -> ScanPangColors.CategoryMall
    category.contains("카페") || category.contains("커피") -> ScanPangColors.CategoryCafe
    category.contains("식당") || category.contains("음식") || category.contains("한식") ||
        category.contains("패스트") -> ScanPangColors.CategoryRestaurant
    category.contains("환전") -> ScanPangColors.CategoryExchange
    category.contains("은행") -> ScanPangColors.CategoryExchange
    category.contains("ATM") || category.contains("atm") -> ScanPangColors.CategoryExchange
    category.contains("병원") -> ScanPangColors.CategoryMedical
    category.contains("약국") -> ScanPangColors.CategoryMedical
    category.contains("지하철") -> ScanPangColors.Success
    category.contains("화장실") || category.contains("물품") || category.contains("보관") -> Color(0xFF0D9488)
    category.contains("숙박") || category.contains("호텔") || category.contains("accommodation") -> Color(0xFFF59E0B)
    else -> ScanPangColors.Primary
}

@Composable
fun ArCategoryIconBadge(
    category: String,
    modifier: Modifier = Modifier,
    badgeSize: Int = 24,
    iconSize: Int = 14,
    tint: Color = categoryIconTint(category),
) {
    Surface(
        modifier = modifier.size(badgeSize.dp),
        shape = CircleShape,
        color = tint.copy(alpha = 0.12f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = categoryIcon(category),
                contentDescription = null,
                modifier = Modifier.size(iconSize.dp),
                tint = tint,
            )
        }
    }
}

private val ArAgentUserBubbleBlue = Color(0xFF1A73E8)
private val ArSttMicIdleBlue = Color(0xFF1A73E8)
private val ArSttMicRecordingRed = Color(0xFFE53935)

@Composable
internal fun ArMicSttButton(
    isListening: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (isListening) {
            val transition = rememberInfiniteTransition(label = "arMicPulse")
            val pulse by transition.animateFloat(
                initialValue = 0.88f,
                targetValue = 1.22f,
                animationSpec = infiniteRepeatable(
                    animation = tween(650, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "pulse",
            )
            Box(
                modifier = Modifier
                    .size((36f * pulse).dp)
                    .clip(CircleShape)
                    .background(ArSttMicRecordingRed.copy(alpha = 0.35f)),
            )
        }
        Surface(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick),
            shape = CircleShape,
            color = if (isListening) ArSttMicRecordingRed else ArSttMicIdleBlue,
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = Icons.Rounded.Mic,
                    contentDescription = "음성 입력",
                    modifier = Modifier.size(ScanPangDimens.arMicSendIcon),
                    tint = Color.White,
                )
            }
        }
    }
}

private fun Modifier.headsetPulseIfTtsPlaying(enabled: Boolean): Modifier = composed {
    if (!enabled) return@composed this
    val transition = rememberInfiniteTransition(label = "arHeadsetTts")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(480, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "headsetPulse",
    )
    this.scale(scale)
}

data class ArAgentChatMessage(
    val text: String,
    val isUser: Boolean,
)

@Composable
fun ArExploreInteractiveChatSection(
    messages: List<ArAgentChatMessage>,
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    isSttListening: Boolean,
    onMicClick: () -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    // 전송 진행 중일 때 버튼을 spinner 로 바꾸고 클릭 차단 — 연타 방지의 시각 보강.
    isSending: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ScanPangDimens.arTopBarHorizontal)
            .padding(bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(ScanPangDimens.arChatBubbleGap),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 172.dp),
            verticalArrangement = Arrangement.spacedBy(ScanPangDimens.arChatBubbleGap),
        ) {
            itemsIndexed(messages, key = { index, msg -> "$index-${msg.isUser}-${msg.text}" }) { _, msg ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start,
                ) {
                    Surface(
                        modifier = Modifier.widthIn(max = 300.dp),
                        shape = if (msg.isUser) ScanPangShapes.arBubbleUser else ScanPangShapes.arBubbleAgent,
                        color = if (msg.isUser) ArAgentUserBubbleBlue else Color.White,
                        shadowElevation = if (msg.isUser) 0.dp else 2.dp,
                    ) {
                        Text(
                            text = msg.text,
                            modifier = Modifier.padding(ScanPangSpacing.md),
                            style = ScanPangType.arChatBody14,
                            color = if (msg.isUser) Color.White else ScanPangColors.OnSurfaceStrong,
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ScanPangDimens.arInputBarMinHeight, max = 120.dp)
                .clip(ScanPangShapes.arInputPill)
                .background(ScanPangColors.ArOverlayWhite93)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
        ) {
            ArMicSttButton(
                isListening = isSttListening,
                onClick = onMicClick,
            )
            BasicTextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 6.dp),
                singleLine = false,
                maxLines = 4,
                textStyle = ScanPangType.body15Medium.copy(color = ScanPangColors.OnSurfaceStrong),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                cursorBrush = SolidColor(ScanPangColors.Primary),
                decorationBox = { innerTextField ->
                    Box {
                        if (inputText.isEmpty()) {
                            Text(
                                text = "무엇이든 물어보세요",
                                style = ScanPangType.searchPlaceholderRegular,
                                color = ScanPangColors.OnSurfacePlaceholder,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            // 보내기 버튼 — 전송 진행 중이면 spinner + 클릭 차단, 입력 빈 상태도 차단.
            val canSend = inputText.isNotBlank() && !isSending
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF6B7280).copy(alpha = 0.1f))
                    .clickable(enabled = canSend) { onSend() },
                contentAlignment = Alignment.Center,
            ) {
                if (isSending) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(ScanPangDimens.icon16),
                        strokeWidth = 2.dp,
                        color = ScanPangColors.Primary,
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "전송",
                        modifier = Modifier.size(ScanPangDimens.icon16),
                        tint = if (canSend) ScanPangColors.Primary else ScanPangColors.OnSurfaceMuted,
                    )
                }
            }
        }
    }
}

@Composable
fun ArChatBottomSection(
    userMessage: String,
    agentMessage: String,
    inputPlaceholder: String,
    modifier: Modifier = Modifier,
    agentTag: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ScanPangColors.ArBottomChatScrim)
            .padding(horizontal = ScanPangDimens.arTopBarHorizontal)
            .padding(bottom = ScanPangDimens.arChatAreaBottomPad)
            .heightIn(max = ScanPangDimens.arChatAreaMaxHeight),
        verticalArrangement = Arrangement.spacedBy(ScanPangDimens.arChatBubbleGap),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            Column(horizontalAlignment = Alignment.Start) {
                if (agentTag != null) {
                    agentTag()
                    Spacer(modifier = Modifier.height(ScanPangSpacing.xs))
                }
                Surface(
                    shape = ScanPangShapes.arBubbleAgent,
                    color = ScanPangColors.ArOverlayWhite85,
                ) {
                    Text(
                        text = agentMessage,
                        modifier = Modifier.padding(ScanPangSpacing.md),
                        style = ScanPangType.arChatBody14,
                        color = ScanPangColors.OnSurfaceStrong,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(
                shape = ScanPangShapes.arBubbleUser,
                color = ScanPangColors.ArOverlayWhite80,
            ) {
                Text(
                    text = userMessage,
                    modifier = Modifier.padding(ScanPangSpacing.md),
                    style = ScanPangType.arChatBody14,
                    color = ScanPangColors.OnSurfaceStrong,
                )
            }
        }
        ArChatInputBar(placeholder = inputPlaceholder)
    }
}

@Composable
fun ArChatInputBar(
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ScanPangDimens.arInputBarMinHeight),
        shape = ScanPangShapes.arInputPill,
        color = ScanPangColors.ArOverlayWhite93,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = ScanPangDimens.arInputInnerPadH,
                vertical = ScanPangDimens.arInputInnerPadV,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
        ) {
            Icon(
                imageVector = Icons.Rounded.Mic,
                contentDescription = null,
                modifier = Modifier.size(ScanPangDimens.arMicSendIcon),
                tint = ScanPangColors.OnSurfaceMuted,
            )
            Text(
                text = placeholder,
                modifier = Modifier.weight(1f),
                style = ScanPangType.searchPlaceholderRegular,
                color = ScanPangColors.OnSurfacePlaceholder,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Surface(
                shape = CircleShape,
                color = ScanPangColors.ArSendChipBackground,
                modifier = Modifier.size(ScanPangDimens.arMicSendIcon),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "전송",
                        modifier = Modifier.size(ScanPangDimens.icon16),
                        tint = ScanPangColors.OnSurfaceMuted,
                    )
                }
            }
        }
    }
}

@Composable
fun ArRecommendHalalTag(text: String) {
    Surface(
        shape = ScanPangShapes.badge6,
        color = ScanPangColors.ArRecommendTagHalalBackground,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = ScanPangDimens.arSearchTagHorizontalPad,
                vertical = ScanPangDimens.arSearchTagVerticalPad,
            ),
            style = ScanPangType.tag11Medium,
            color = ScanPangColors.Primary,
        )
    }
}

private val KeyboardRow1 = listOf("ㅂ", "ㅈ", "ㄷ", "ㄱ", "ㅅ", "ㅛ", "ㅕ", "ㅑ", "ㅐ", "ㅔ")
private val KeyboardRow2 = listOf("ㅁ", "ㄴ", "ㅇ", "ㄹ", "ㅎ", "ㅗ", "ㅓ", "ㅏ", "ㅣ")
private val KeyboardRow3 = listOf("ㅋ", "ㅌ", "ㅊ", "ㅍ", "ㅠ", "ㅜ", "ㅡ")

@Composable
fun ArIosStyleKeyboardPanel(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ScanPangColors.ArKeyboardIosBackground)
            .navigationBarsPadding()
            .padding(ScanPangSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(ScanPangDimens.arKeyboardKeyGap),
    ) {
        ArKeyboardRow(keys = KeyboardRow1)
        ArKeyboardRow(keys = KeyboardRow2, indent = true)
        ArKeyboardRowWithShiftDelete(keys = KeyboardRow3)
        ArKeyboardFunctionRow()
    }
}

@Composable
private fun ArKeyboardRow(
    keys: List<String>,
    indent: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (indent) ScanPangSpacing.lg else ScanPangSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.xs),
    ) {
        keys.forEach { key ->
            ArKeyboardLetterKey(
                label = key,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ArKeyboardRowWithShiftDelete(keys: List<String>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArKeyboardFunctionKey(
            modifier = Modifier.width(ScanPangDimens.arSideFab44),
            content = {
                Text("⇧", style = ScanPangType.arKeyboardKey22, color = ScanPangColors.OnSurfaceStrong)
            },
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.xs),
        ) {
            keys.forEach { key ->
                ArKeyboardLetterKey(label = key, modifier = Modifier.weight(1f))
            }
        }
        ArKeyboardFunctionKey(
            modifier = Modifier.width(ScanPangDimens.arSideFab44),
            content = {
                Text("⌫", style = ScanPangType.arKeyboardKey22, color = ScanPangColors.OnSurfaceStrong)
            },
        )
    }
}

@Composable
private fun ArKeyboardFunctionRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ScanPangDimens.arKeyboardKeyGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArKeyboardFunctionKey(
            modifier = Modifier.size(ScanPangDimens.arKeyboardKeyHeight),
            content = {
                Text("12", style = ScanPangType.caption12Medium, color = ScanPangColors.OnSurfaceStrong)
            },
        )
        ArKeyboardFunctionKey(
            modifier = Modifier.size(ScanPangDimens.arKeyboardKeyHeight),
            content = {
                Text("😀", style = ScanPangType.caption12Medium, color = ScanPangColors.OnSurfaceStrong)
            },
        )
        Surface(
            modifier = Modifier
                .weight(1f)
                .height(ScanPangDimens.arKeyboardKeyHeight),
            shape = ScanPangShapes.arKeyboardKey,
            color = ScanPangColors.Surface,
        ) {}
        ArKeyboardFunctionKey(
            modifier = Modifier
                .width(ScanPangDimens.arInputBarMinHeight + ScanPangSpacing.lg)
                .height(ScanPangDimens.arKeyboardKeyHeight),
            content = {
                Text("↵", style = ScanPangType.arKeyboardKey22, color = ScanPangColors.OnSurfaceStrong)
            },
        )
    }
}

@Composable
private fun ArKeyboardLetterKey(label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.height(ScanPangDimens.arKeyboardKeyHeight),
        shape = ScanPangShapes.arKeyboardKey,
        color = ScanPangColors.Surface,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = label, style = ScanPangType.arKeyboardKey22, color = ScanPangColors.OnSurfaceStrong)
        }
    }
}

@Composable
private fun ArKeyboardFunctionKey(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = ScanPangShapes.arKeyboardKey,
        color = ScanPangColors.ArKeyboardIosFunctionKey,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}

@Composable
fun BoxScope.ArPoiPinsLayer(
    onPoiOneClick: (() -> Unit)? = null,
    onPoiTwoClick: (() -> Unit)? = null,
) {
    ArPoiCard(
        title = "눈스퀘어",
        subtitle = "쇼핑 · 10m",
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(
                start = ScanPangDimens.arPoiOneStart,
                top = ScanPangDimens.arPoiOneTop,
            ),
        onClick = onPoiOneClick,
    )
    ArPoiCard(
        title = "명동빌딩",
        subtitle = "쇼핑 · 10m",
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(
                start = ScanPangDimens.arPoiTwoStart,
                top = ScanPangDimens.arPoiTwoTop,
            ),
        onClick = onPoiTwoClick,
    )
}

@Composable
fun BoxScope.ArSideButtonsLayer(
    onVolumeClick: () -> Unit,
    onCameraClick: () -> Unit,
    cameraSurfaceColor: Color = ScanPangColors.ArOverlayWhite93,
    cameraIconTint: Color = ScanPangColors.OnSurfaceStrong,
) {
    ArSideActionColumn(
        onVolumeClick = onVolumeClick,
        onCameraClick = onCameraClick,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(
                end = ScanPangDimens.arSideColumnEnd,
                top = ScanPangDimens.arSideColumnTop,
            ),
        cameraSurfaceColor = cameraSurfaceColor,
        cameraIconTint = cameraIconTint,
    )
}

@Composable
fun ArFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) ScanPangColors.PrimarySoft else ScanPangColors.Surface
    val fg = if (selected) ScanPangColors.Primary else ScanPangColors.OnSurfaceStrong
    val borderColor = if (selected) ScanPangColors.Primary else ScanPangColors.OutlineSubtle
    Surface(
        modifier = modifier
            .height(ScanPangDimens.arFilterChipHeight)
            .clip(ScanPangShapes.filterChip)
            .clickable(onClick = onClick)
            .border(ScanPangDimens.borderHairline, borderColor, ScanPangShapes.filterChip),
        shape = ScanPangShapes.filterChip,
        color = bg,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = ScanPangSpacing.md, vertical = ScanPangSpacing.xs),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = label, style = ScanPangType.chip12Medium, color = fg, maxLines = 1)
        }
    }
}

@Composable
fun ArFilterChipRow(
    labels: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
    ) {
        labels.forEach { label ->
            ArFilterChip(
                label = label,
                selected = label == selected,
                onClick = { onSelect(label) },
            )
        }
    }
}

@Composable
fun ArFilterChipRowMulti(
    labels: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
    ) {
        labels.forEach { label ->
            ArFilterChip(
                label = label,
                selected = label in selected,
                onClick = { onToggle(label) },
            )
        }
    }
}

data class ArExploreCategoryChipSpec(val label: String)

fun arExploreCategoryChipSpecs(): List<ArExploreCategoryChipSpec> = listOf(
    "쇼핑", "편의점", "식당", "카페",
    "환전소", "은행", "ATM", "병원",
    "지하철역", "화장실", "물품보관함", "약국",
).map { ArExploreCategoryChipSpec(it) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ArExploreFilterPanelFigma(
    categorySpecs: List<ArExploreCategoryChipSpec>,
    categorySelection: Set<String>,
    onCategoryToggle: (String) -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "필터",
                style = ScanPangType.arFilterTitle16,
                color = ScanPangColors.OnSurfaceStrong,
            )
            TextButton(
                onClick = onReset,
                contentPadding = PaddingValues(
                    horizontal = ScanPangSpacing.sm,
                    vertical = ScanPangSpacing.xs,
                ),
            ) {
                Surface(
                    shape = CircleShape,
                    color = ScanPangColors.Background,
                    modifier = Modifier.size(28.dp),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(ScanPangDimens.icon16),
                            tint = ScanPangColors.OnSurfacePlaceholder,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(ScanPangSpacing.xs))
                Text(
                    text = "초기화",
                    style = ScanPangType.chip13Medium,
                    color = ScanPangColors.OnSurfaceMuted,
                )
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            categorySpecs.forEach { spec ->
                val selected = spec.label in categorySelection
                Surface(
                    modifier = Modifier
                        .clip(ScanPangShapes.radius12)
                        .clickable { onCategoryToggle(spec.label) },
                    shape = ScanPangShapes.radius12,
                    color = if (selected) ScanPangColors.Primary else ScanPangColors.Surface,
                    shadowElevation = if (selected) 0.dp else 2.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = 14.dp,
                            vertical = 5.dp,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.xs),
                    ) {
                        Icon(
                            imageVector = categoryIcon(spec.label),
                            contentDescription = null,
                            modifier = Modifier.size(ScanPangDimens.icon16),
                            tint = if (selected) Color.White else categoryIconTint(spec.label),
                        )
                        Text(
                            text = spec.label,
                            style = ScanPangType.chip13SemiBold,
                            color = if (selected) Color.White else ScanPangColors.OnSurfaceStrong,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Button(
            onClick = onApply,
            modifier = Modifier
                .fillMaxWidth()
                .height(ScanPangDimens.searchBarHeightDefault),
            shape = ScanPangShapes.radius14,
            colors = ButtonDefaults.buttonColors(
                containerColor = ScanPangColors.Primary,
                contentColor = Color.White,
            ),
        ) {
            Text(
                text = "필터 적용",
                style = ScanPangType.body15Medium.copy(fontWeight = FontWeight.Bold),
            )
        }
        Spacer(modifier = Modifier.height(ScanPangDimens.arFilterApplyBottom))
    }
}

@Composable
fun BoxScope.ArExploreSideColumn(
    onTtsClick: () -> Unit,
    isTtsOn: Boolean,
    isTtsPlaying: Boolean = false,
) {
    ArExploreRoundSideButton(
        icon = if (isTtsOn) Icons.Rounded.Headset else Icons.Rounded.HeadsetOff,
        contentDescription = "음성 안내",
        onClick = onTtsClick,
        surfaceColor = ScanPangColors.ArOverlayWhite85,
        iconTint = if (isTtsOn) ScanPangColors.OnSurfaceStrong else ScanPangColors.ArTtsOffIconTint,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(
                end = ScanPangDimens.arSideColumnEnd,
                top = ScanPangDimens.arSideColumnTop,
            )
            .headsetPulseIfTtsPlaying(isTtsPlaying && isTtsOn),
    )
}

@Composable
private fun ArExploreRoundSideButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    surfaceColor: Color,
    iconTint: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .size(ScanPangDimens.arSideFab44)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = surfaceColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(ScanPangDimens.icon20),
                tint = iconTint,
            )
        }
    }
}

private val ArExploreHalalBadgeBg = Color(0xFFE8F5E9)
private val ArExploreHalalBadgeFg = Color(0xFF2E7D32)

data class ArExploreSearchHitUi(
    val title: String,
    val category: String,
    val distance: String,
    val badgeLabel: String?,
)

@Composable
fun ArExploreSearchPanelContent(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmitSearch: () -> Unit,
    recentQueries: List<String>,
    onRecentQueryClick: (String) -> Unit,
    onRecentQueryRemove: (String) -> Unit,
    onRecentClearAll: () -> Unit,
    showResultList: Boolean,
    searchHits: List<ArExploreSearchHitUi>,
    onHitViewInfo: (ArExploreSearchHitUi) -> Unit,
    onHitStartNav: (ArExploreSearchHitUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(CircleShape)
                .background(ScanPangColors.ArOverlayWhite93)
                .border(ScanPangDimens.borderHairline, ScanPangColors.OutlineSubtle, CircleShape)
                .padding(start = 14.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                modifier = Modifier.size(ScanPangDimens.icon20),
                tint = ScanPangColors.OnSurfaceMuted,
            )
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = ScanPangType.body14Regular.copy(color = ScanPangColors.OnSurfaceStrong),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmitSearch() }),
                cursorBrush = SolidColor(ScanPangColors.Primary),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                text = "장소, 건물, 매장 검색",
                                style = ScanPangType.body14Regular,
                                color = ScanPangColors.OnSurfacePlaceholder,
                            )
                        }
                        inner()
                    }
                },
            )
            if (query.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "지우기",
                    modifier = Modifier
                        .size(ScanPangDimens.icon20)
                        .clickable { onQueryChange("") },
                    tint = ScanPangColors.OnSurfaceMuted,
                )
            }
        }

        if (!showResultList) {
            if (recentQueries.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = ScanPangColors.ArOverlayWhite93,
                    border = BorderStroke(ScanPangDimens.borderHairline, ScanPangColors.OutlineSubtle),
                ) {
                    Column(modifier = Modifier.padding(vertical = 14.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp)
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "최근 검색",
                                style = ScanPangType.caption12Medium,
                                color = ScanPangColors.OnSurfaceMuted,
                            )
                            Text(
                                text = "전체 삭제",
                                modifier = Modifier.clickable { onRecentClearAll() },
                                style = ScanPangType.meta11Medium,
                                color = ScanPangColors.OnSurfaceMuted,
                            )
                        }
                        Column(
                            modifier = Modifier
                                .heightIn(max = 270.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                        recentQueries.take(30).forEach { q ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onRecentQueryClick(q) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.History,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = ScanPangColors.OnSurfaceMuted,
                                )
                                Text(
                                    text = q,
                                    modifier = Modifier.weight(1f),
                                    style = ScanPangType.chip13Medium,
                                    color = ScanPangColors.OnSurfaceStrong,
                                )
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "삭제",
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { onRecentQueryRemove(q) },
                                    tint = ScanPangColors.OnSurfaceMuted,
                                )
                            }
                        }
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 270.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                searchHits.forEach { hit ->
                    ArExploreSearchResultCard(
                        hit = hit,
                        onViewInfo = { onHitViewInfo(hit) },
                        onStartNav = { onHitStartNav(hit) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ArExploreSearchResultCard(
    hit: ArExploreSearchHitUi,
    onViewInfo: () -> Unit,
    onStartNav: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = ScanPangColors.ArOverlayWhite93,
        border = BorderStroke(ScanPangDimens.borderHairline, ScanPangColors.OutlineSubtle),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    modifier = Modifier.size(30.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFD9D9D9).copy(alpha = 0.5f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = categoryIcon(hit.category),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = categoryIconTint(hit.category),
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = hit.title,
                            style = ScanPangType.chip13SemiBold,
                            color = ScanPangColors.OnSurfaceStrong,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (hit.badgeLabel != null) {
                            Surface(
                                shape = ScanPangShapes.badge6,
                                color = ArExploreHalalBadgeBg,
                            ) {
                                Text(
                                    text = hit.badgeLabel,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                    style = ScanPangType.tag11Medium,
                                    color = ArExploreHalalBadgeFg,
                                )
                            }
                        }
                    }
                    Text(
                        text = "${hit.category} · ${hit.distance}",
                        style = ScanPangType.meta11Medium,
                        color = ScanPangColors.OnSurfaceMuted,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedButton(
                    onClick = onViewInfo,
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(0.dp),
                    border = BorderStroke(ScanPangDimens.borderHairline, ScanPangColors.OutlineSubtle),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ScanPangColors.OnSurfaceStrong),
                ) {
                    Text("정보 보기", style = ScanPangType.meta11Medium)
                }
                Button(
                    onClick = onStartNav,
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ScanPangColors.Primary),
                ) {
                    Text("길안내", style = ScanPangType.meta11Medium, color = Color.White)
                }
            }
        }
    }
}
