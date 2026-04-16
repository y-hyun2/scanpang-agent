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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
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
                        ScanPangDimens.arCircleBtn36,
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
) {
    Surface(
        modifier = modifier
            .size(ScanPangDimens.arCircleBtn36)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = ScanPangColors.ArOverlayWhite80,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(ScanPangDimens.icon20),
                tint = ScanPangColors.OnSurfaceStrong,
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
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val clickMod = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Surface(
        modifier = modifier
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
            Surface(
                modifier = Modifier.size(ScanPangDimens.arPoiIcon24),
                shape = CircleShape,
                color = ScanPangColors.PrimarySoft,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        modifier = Modifier.size(ScanPangDimens.icon14),
                        tint = ScanPangColors.Primary,
                    )
                }
            }
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
}

private val ArAgentUserBubbleBlue = Color(0xFF1A73E8)
private val ArSttMicIdleBlue = Color(0xFF1A73E8)
private val ArSttMicRecordingRed = Color(0xFFE53935)

@Composable
private fun ArMicSttButton(
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
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ScanPangColors.ArBottomChatScrim)
            .padding(horizontal = ScanPangDimens.arTopBarHorizontal)
            .padding(bottom = ScanPangDimens.arChatAreaBottomPad),
        verticalArrangement = Arrangement.spacedBy(ScanPangDimens.arChatBubbleGap),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp),
            verticalArrangement = Arrangement.spacedBy(ScanPangDimens.arChatBubbleGap),
        ) {
            itemsIndexed(messages, key = { index, msg -> "$index-${msg.isUser}-${msg.text}" }) { _, msg ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start,
                ) {
                    Surface(
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
                .heightIn(min = ScanPangDimens.arInputBarMinHeight)
                .clip(ScanPangShapes.arInputPill)
                .background(ScanPangColors.ArOverlayWhite93)
                .padding(
                    horizontal = ScanPangDimens.arInputInnerPadH,
                    vertical = ScanPangDimens.arInputInnerPadV,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
        ) {
            ArMicSttButton(
                isListening = isSttListening,
                onClick = onMicClick,
            )
            TextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = "무엇이든 물어보세요",
                        style = ScanPangType.searchPlaceholderRegular,
                        color = ScanPangColors.OnSurfacePlaceholder,
                    )
                },
                textStyle = ScanPangType.body15Medium.copy(color = ScanPangColors.OnSurfaceStrong),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    cursorColor = ScanPangColors.Primary,
                ),
            )
            IconButton(
                onClick = onSend,
                enabled = inputText.isNotBlank(),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Send,
                    contentDescription = "전송",
                    modifier = Modifier.size(ScanPangDimens.icon16),
                    tint = if (inputText.isNotBlank()) ScanPangColors.Primary else ScanPangColors.OnSurfaceMuted,
                )
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

data class ArExploreCategoryChipSpec(
    val label: String,
    val icon: ImageVector,
    val iconTintUnselected: Color,
)

/**
 * AR 탐색 필터 패널 — Figma(아이콘+텍스트 칩, 상단 필터/초기화, 하단 필터 적용).
 */
fun arExploreCategoryChipSpecs(): List<ArExploreCategoryChipSpec> = listOf(
    ArExploreCategoryChipSpec("쇼핑", Icons.Rounded.LocalMall, ScanPangColors.CategoryMall),
    ArExploreCategoryChipSpec("편의점", Icons.Rounded.Store, ScanPangColors.CategoryMall),
    ArExploreCategoryChipSpec("식당", Icons.Rounded.Restaurant, ScanPangColors.CategoryRestaurant),
    ArExploreCategoryChipSpec("카페", Icons.Rounded.Coffee, ScanPangColors.CategoryCafe),
    ArExploreCategoryChipSpec("환전소", Icons.Rounded.CurrencyExchange, ScanPangColors.CategoryExchange),
    ArExploreCategoryChipSpec("은행", Icons.Rounded.AccountBalance, ScanPangColors.CategoryExchange),
    ArExploreCategoryChipSpec("ATM", Icons.Rounded.LocalAtm, ScanPangColors.CategoryExchange),
    ArExploreCategoryChipSpec("병원", Icons.Rounded.LocalHospital, ScanPangColors.CategoryMedical),
    ArExploreCategoryChipSpec("지하철역", Icons.Rounded.DirectionsTransit, ScanPangColors.Success),
    ArExploreCategoryChipSpec("화장실", Icons.Rounded.Wc, Color(0xFF0D9488)),
    ArExploreCategoryChipSpec("물품보관함", Icons.Rounded.Luggage, Color(0xFF0D9488)),
    ArExploreCategoryChipSpec("약국", Icons.Rounded.Medication, ScanPangColors.CategoryMedical),
)

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
                    style = ScanPangType.body14Regular,
                    color = ScanPangColors.OnSurfaceMuted,
                )
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
        ) {
            categorySpecs.forEach { spec ->
                val selected = spec.label in categorySelection
                Surface(
                    modifier = Modifier
                        .clip(ScanPangShapes.pill36)
                        .clickable { onCategoryToggle(spec.label) },
                    shape = ScanPangShapes.pill36,
                    color = if (selected) ScanPangColors.Primary else ScanPangColors.Surface,
                    shadowElevation = if (selected) 0.dp else 2.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = ScanPangSpacing.md,
                            vertical = ScanPangDimens.chipPadVertical + 2.dp,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.xs),
                    ) {
                        Icon(
                            imageVector = spec.icon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (selected) Color.White else spec.iconTintUnselected,
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
            shape = ScanPangShapes.radius12,
            colors = ButtonDefaults.buttonColors(
                containerColor = ScanPangColors.Primary,
                contentColor = Color.White,
            ),
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                modifier = Modifier.size(ScanPangDimens.icon20),
            )
            Spacer(modifier = Modifier.width(ScanPangSpacing.sm))
            Text(
                text = "필터 적용",
                style = ScanPangType.body15Medium,
            )
        }
        Spacer(modifier = Modifier.height(ScanPangDimens.arFilterApplyBottom))
    }
}

@Composable
fun BoxScope.ArExploreSideColumn(
    onTtsClick: () -> Unit,
    onCameraClick: () -> Unit,
    isTtsOn: Boolean,
    isFrozen: Boolean,
    isTtsPlaying: Boolean = false,
) {
    Column(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(
                end = ScanPangDimens.arSideColumnEnd,
                top = ScanPangDimens.arSideColumnTop,
            )
            .width(ScanPangDimens.arSideColumnWidth),
        verticalArrangement = Arrangement.spacedBy(ScanPangDimens.arSideIconGap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArExploreRoundSideButton(
            icon = Icons.Rounded.Headset,
            contentDescription = "음성 안내",
            onClick = onTtsClick,
            surfaceColor = ScanPangColors.ArOverlayWhite85,
            iconTint = if (isTtsOn) ScanPangColors.OnSurfaceStrong else ScanPangColors.ArTtsOffIconTint,
            modifier = Modifier.headsetPulseIfTtsPlaying(isTtsPlaying && isTtsOn),
        )
        ArExploreRoundSideButton(
            icon = Icons.Rounded.CameraAlt,
            contentDescription = "화면 고정",
            onClick = onCameraClick,
            surfaceColor = if (isFrozen) ScanPangColors.ArPrimaryTranslucent else ScanPangColors.ArOverlayWhite93,
            iconTint = if (isFrozen) Color.White else ScanPangColors.OnSurfaceStrong,
        )
    }
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
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.md),
    ) {
        ArExploreSearchEditableBar(
            query = query,
            onQueryChange = onQueryChange,
            placeholder = "장소, 건물, 매장 검색",
            onSearchIme = onSubmitSearch,
        )
        if (!showResultList && recentQueries.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "최근 검색",
                    style = ScanPangType.sectionTitle16,
                    color = ScanPangColors.OnSurfaceStrong,
                )
                TextButton(
                    onClick = onRecentClearAll,
                    contentPadding = PaddingValues(horizontal = ScanPangSpacing.sm, vertical = ScanPangSpacing.xs),
                ) {
                    Text(
                        text = "전체 삭제",
                        style = ScanPangType.caption12Medium,
                        color = ScanPangColors.OnSurfaceMuted,
                    )
                }
            }
            recentQueries.forEach { q ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onRecentQueryClick(q) }
                        .padding(vertical = ScanPangSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        tint = ScanPangColors.OnSurfaceMuted,
                        modifier = Modifier.size(ScanPangDimens.icon18),
                    )
                    Text(
                        text = q,
                        style = ScanPangType.body14Regular,
                        color = ScanPangColors.OnSurfaceStrong,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { onRecentQueryRemove(q) },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "삭제",
                            tint = ScanPangColors.OnSurfaceMuted,
                            modifier = Modifier.size(ScanPangDimens.icon18),
                        )
                    }
                }
            }
        }
        if (showResultList) {
            HorizontalDivider(color = ScanPangColors.OutlineSubtle)
            Text(
                text = "정확도 · 거리순",
                style = ScanPangType.meta11SemiBold,
                color = ScanPangColors.OnSurfaceMuted,
            )
            searchHits.forEach { hit ->
                ArExploreSearchResultCard(
                    hit = hit,
                    onViewInfo = { onHitViewInfo(hit) },
                    onStartNav = { onHitStartNav(hit) },
                    modifier = Modifier.padding(bottom = ScanPangSpacing.sm),
                )
            }
        }
    }
}

@Composable
private fun ArExploreSearchEditableBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    onSearchIme: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ScanPangDimens.searchBarHeightActive)
            .clip(ScanPangShapes.radius14)
            .background(ScanPangColors.Background)
            .padding(horizontal = ScanPangDimens.searchBarInnerHorizontal),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = ScanPangColors.OnSurfaceMuted,
            modifier = Modifier.size(ScanPangDimens.icon20),
        )
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = ScanPangType.body15Medium.copy(color = ScanPangColors.OnSurfaceStrong),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearchIme() }),
            modifier = Modifier.weight(1f),
            decorationBox = { innerTextField ->
                Box {
                    if (query.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = ScanPangType.searchPlaceholderRegular,
                            color = ScanPangColors.OnSurfacePlaceholder,
                        )
                    }
                    innerTextField()
                }
            },
        )
        if (query.isNotEmpty()) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "지우기",
                modifier = Modifier
                    .size(ScanPangDimens.icon18)
                    .clickable { onQueryChange("") },
                tint = ScanPangColors.OnSurfacePlaceholder,
            )
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
        shape = ScanPangShapes.radius14,
        color = ScanPangColors.Surface,
        border = BorderStroke(ScanPangDimens.borderHairline, ScanPangColors.OutlineSubtle),
        shadowElevation = ScanPangDimens.arPoiCardShadowElevation,
    ) {
        Column(
            modifier = Modifier.padding(ScanPangSpacing.md),
            verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.md),
            ) {
                Surface(
                    shape = CircleShape,
                    color = ScanPangColors.PrimarySoft,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = Icons.Rounded.Place,
                            contentDescription = null,
                            tint = ScanPangColors.Primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = hit.title,
                        style = ScanPangType.title16SemiBold,
                        color = ScanPangColors.OnSurfaceStrong,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${hit.category} · ${hit.distance}",
                        style = ScanPangType.caption12Medium,
                        color = ScanPangColors.OnSurfaceMuted,
                    )
                    if (hit.badgeLabel != null) {
                        Spacer(modifier = Modifier.height(ScanPangSpacing.xs))
                        Surface(
                            shape = ScanPangShapes.badge6,
                            color = ArExploreHalalBadgeBg,
                        ) {
                            Text(
                                text = hit.badgeLabel,
                                modifier = Modifier.padding(
                                    horizontal = ScanPangSpacing.sm,
                                    vertical = ScanPangDimens.badgePadVertical,
                                ),
                                style = ScanPangType.tag11Medium,
                                color = ArExploreHalalBadgeFg,
                            )
                        }
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = ScanPangColors.OnSurfaceMuted,
                    modifier = Modifier.size(ScanPangDimens.icon20),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm, Alignment.End),
            ) {
                OutlinedButton(
                    onClick = onViewInfo,
                    shape = ScanPangShapes.radius14,
                    border = BorderStroke(ScanPangDimens.borderHairline, ScanPangColors.OutlineSubtle),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ScanPangColors.OnSurfaceStrong),
                ) {
                    Text("정보 보기", style = ScanPangType.body15Medium)
                }
                Button(
                    onClick = onStartNav,
                    shape = ScanPangShapes.radius14,
                    colors = ButtonDefaults.buttonColors(containerColor = ScanPangColors.Primary),
                ) {
                    Text("길안내", style = ScanPangType.body15Medium, color = Color.White)
                }
            }
        }
    }
}
