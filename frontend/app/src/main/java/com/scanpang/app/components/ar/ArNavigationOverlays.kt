package com.scanpang.app.components.ar

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LocalMall
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.scanpang.app.ui.ScanPangFigmaAssets
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangDimens
import com.scanpang.app.ui.theme.ScanPangShapes
import com.scanpang.app.ui.theme.ScanPangSpacing
import com.scanpang.app.ui.theme.ScanPangType

@Composable
fun ArNavTopHud(
    modifier: Modifier = Modifier,
    onCameraClick: () -> Unit,
    isCameraFrozen: Boolean = false,
    onSearchClick: () -> Unit,
    destinationPill: @Composable () -> Unit,
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
            .padding(top = 8.dp)
            .padding(horizontal = ScanPangDimens.arTopBarHorizontal)
            .padding(bottom = ScanPangDimens.arTopBarBottomPadding),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(
                    min = maxOf(
                        ScanPangDimens.arNavTopFab40,
                        ScanPangDimens.arStatusPillHeight,
                    ),
                ),
        ) {
            ArNavWhiteFab(
                icon = Icons.Rounded.CameraAlt,
                contentDescription = "화면 캡처",
                onClick = onCameraClick,
                isActive = isCameraFrozen,
                modifier = Modifier.align(Alignment.CenterStart),
            )
            Box(Modifier.align(Alignment.Center)) {
                destinationPill()
            }
            ArNavWhiteFab(
                icon = Icons.Rounded.Search,
                contentDescription = "검색",
                onClick = onSearchClick,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

@Composable
fun ArNavWhiteFab(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
) {
    Surface(
        modifier = modifier
            .size(ScanPangDimens.arNavTopFab40)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = if (isActive) ScanPangColors.ArPrimaryTranslucent else ScanPangColors.ArOverlayWhite80,
        shadowElevation = ScanPangDimens.arPoiCardShadowElevation,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(ScanPangDimens.arNavTopFabIcon),
                tint = if (isActive) Color.White else ScanPangColors.OnSurfaceStrong,
            )
        }
    }
}

@Composable
fun ArNavDestinationPill(
    text: String,
    containerColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val m = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Surface(
        modifier = m.heightIn(min = ScanPangDimens.arStatusPillHeight),
        shape = ScanPangShapes.filterChip,
        color = containerColor,
        shadowElevation = ScanPangDimens.arPoiCardShadowElevation,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = ScanPangDimens.arStatusPillHorizontalPad,
                vertical = ScanPangDimens.chipPadVertical,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ScanPangDimens.stackGap6),
        ) {
            Icon(
                imageVector = Icons.Rounded.Flag,
                contentDescription = null,
                modifier = Modifier.size(ScanPangDimens.arNavDestinationFlagIcon),
                tint = Color.White,
            )
            Text(
                text = text,
                style = ScanPangType.arStatusPill15,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(ScanPangDimens.arNavDestinationChevron),
                tint = Color.White,
            )
        }
    }
}

@Composable
fun BoxScope.ArNavSideVolumeCamera(
    onVolumeClick: () -> Unit,
    isTtsOn: Boolean = true,
) {
    Column(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(
                end = ScanPangDimens.arSideColumnEnd,
                top = ScanPangDimens.arNavSideVolumeTop,
            ),
        verticalArrangement = Arrangement.spacedBy(ScanPangDimens.arSideIconGap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArNavWhiteFab(
            icon = if (isTtsOn) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
            contentDescription = if (isTtsOn) "음성 안내 켜짐" else "음성 안내 꺼짐",
            onClick = onVolumeClick,
        )
    }
}

@Composable
fun BoxScope.ArNavTurnBadge(
    icon: ImageVector,
    iconSize: Dp,
    badgeColor: Color,
    iconTint: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .align(Alignment.Center)
            .size(ScanPangDimens.arNavTurnBadgeSize),
        shape = CircleShape,
        color = badgeColor,
        shadowElevation = ScanPangDimens.arPoiCardShadowElevation,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = iconTint,
            )
        }
    }
}

@Composable
fun BoxScope.ArNavActionCardCluster(
    showNextStep: Boolean,
    nextDistance: String,
    nextManeuverIcon: ImageVector,
    currentManeuverIcon: ImageVector,
    currentDistance: String,
    currentInstruction: String,
    isArrived: Boolean = false,
) {
    // 메인 카드는 본문 줄 수에 따라 자유 높이를 갖고, 서브 카드는 그 바로 밑에 살짝 겹쳐서 붙음.
    // (zIndex로 메인 카드를 위로 올려, 겹치는 부분은 메인 카드가 가림)
    Column(
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(
                start = ScanPangDimens.arNavActionClusterStart,
                top = ScanPangDimens.arNavActionClusterTop,
            )
            .width(ScanPangDimens.arNavActionCardWidth),
    ) {
        Surface(
            modifier = Modifier.zIndex(1f),
            shape = ScanPangShapes.radius16,
            color = ScanPangColors.ArOverlayWhite93,
            shadowElevation = ScanPangDimens.arPoiCardShadowElevation,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = ScanPangDimens.arNavActionCardPadH,
                        vertical = ScanPangDimens.arNavActionCardPadV,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.md),
            ) {
                Surface(
                    modifier = Modifier.size(ScanPangDimens.arNavActionIconSquare),
                    shape = if (isArrived) CircleShape else ScanPangShapes.radius14,
                    color = if (isArrived) ScanPangColors.Success else ScanPangColors.Primary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isArrived) Icons.Rounded.CheckCircle else currentManeuverIcon,
                            contentDescription = null,
                            modifier = Modifier.size(ScanPangDimens.arNavActionIconInner),
                            tint = Color.White,
                        )
                    }
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(ScanPangDimens.icon5),
                ) {
                    Text(
                        text = if (isArrived) "도착했어요!" else currentDistance,
                        style = ScanPangType.arNavDistance26,
                        color = if (isArrived) ScanPangColors.Success else ScanPangColors.OnSurfaceStrong,
                    )
                    if (!isArrived) {
                        Text(
                            text = currentInstruction,
                            style = ScanPangType.arNavStepCaption12,
                            color = ScanPangColors.OnSurfaceMuted,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        if (showNextStep && !isArrived) {
            Row(
                modifier = Modifier
                    .padding(start = ScanPangDimens.arNavNextStepOffsetStart)
                    .offset(y = (-8).dp)
                    .width(ScanPangDimens.arNavNextStepWidth)
                    .height(ScanPangDimens.arNavNextStepHeight)
                    .clip(ScanPangShapes.arNavNextStepChip)
                    .background(ScanPangColors.ArNavNextStepBackground),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
            ) {
                Icon(
                    imageVector = nextManeuverIcon,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(start = ScanPangDimens.cardPadding)
                        .size(ScanPangDimens.icon20),
                    tint = ScanPangColors.ArNavNextStepTextMuted,
                )
                Text(
                    text = nextDistance,
                    style = ScanPangType.arNavNextDistance14,
                    color = ScanPangColors.ArNavNextStepTextMuted,
                )
            }
        }
    }
}

@Composable
fun BoxScope.ArNavPoiFab(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val clickMod = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Surface(
        modifier = modifier
            .align(Alignment.TopStart)
            .size(ScanPangDimens.arNavPoiFab)
            .then(clickMod),
        shape = CircleShape,
        color = ScanPangColors.Surface,
        shadowElevation = ScanPangDimens.arPoiCardShadowElevation,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(ScanPangDimens.arPoiIcon24),
                tint = tint,
            )
        }
    }
}

@Composable
fun ArNavBottomSheet(
    mapTabSelected: Boolean,
    onSelectMap: () -> Unit,
    onSelectAgent: () -> Unit,
    modifier: Modifier = Modifier,
    mapContent: @Composable () -> Unit,
    agentContent: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val minH = ScanPangDimens.arNavBottomSheetDragH + ScanPangDimens.arNavTabRowHeight
    val maxH = ScanPangDimens.arChatAreaMaxHeight
    var contentHeight by remember { mutableStateOf(minH) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ScanPangShapes.arNavBottomSheetTop,
        color = ScanPangColors.ArNavBottomSheetBackground,
        shadowElevation = ScanPangDimens.arPoiCardShadowElevation,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(contentHeight + ScanPangDimens.bottomBarContainerHeight),
        ) {
            // 드래그 핸들 영역 — 위아래로 드래그해 시트 높이 조절
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ScanPangDimens.arNavBottomSheetDragH)
                    .pointerInput(minH, maxH, density) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, delta ->
                                val dpDelta = with(density) { delta.toDp() }
                                contentHeight = (contentHeight - dpDelta).coerceIn(minH, maxH)
                            },
                            onDragEnd = {
                                contentHeight = if (contentHeight < (minH + maxH) / 2) minH else maxH
                            },
                            onDragCancel = {
                                contentHeight = if (contentHeight < (minH + maxH) / 2) minH else maxH
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(ScanPangDimens.arNavDragBarWidth)
                        .height(ScanPangDimens.arNavDragBarHeight)
                        .clip(ScanPangShapes.arNavDragBar)
                        .background(ScanPangColors.ArNavDragHandle),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ScanPangDimens.arNavTabRowHeight)
                    .padding(horizontal = ScanPangDimens.arTopBarHorizontal),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArNavTabTrack(
                    mapSelected = mapTabSelected,
                    onSelectMap = onSelectMap,
                    onSelectAgent = onSelectAgent,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true)
                    .clipToBounds(),
            ) {
                if (mapTabSelected) {
                    mapContent()
                } else {
                    agentContent()
                }
            }
            Spacer(Modifier.height(ScanPangDimens.bottomBarContainerHeight))
        }
    }
}

@Composable
private fun ArNavTabTrack(
    mapSelected: Boolean,
    onSelectMap: () -> Unit,
    onSelectAgent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(ScanPangDimens.arNavTabTrackHeight),
        shape = ScanPangShapes.filterChip,
        color = ScanPangColors.Background,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(ScanPangDimens.arNavTabInset),
            horizontalArrangement = Arrangement.spacedBy(ScanPangDimens.arNavTabSegmentGap),
        ) {
            ArNavTabSegment(
                label = "지도",
                selected = mapSelected,
                onClick = onSelectMap,
                modifier = Modifier.weight(1f),
            )
            ArNavTabSegment(
                label = "AI 가이드",
                selected = !mapSelected,
                onClick = onSelectAgent,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ArNavTabSegment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) ScanPangColors.Primary else ScanPangColors.Surface
    val fg = if (selected) Color.White else ScanPangColors.OnSurfaceMuted
    val style = if (selected) ScanPangType.arNavTab13 else ScanPangType.arNavTab13Inactive
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .clip(ScanPangShapes.radius14)
            .clickable(onClick = onClick),
        shape = ScanPangShapes.radius14,
        color = bg,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(text = label, style = style, color = fg, maxLines = 1)
        }
    }
}

@Composable
fun ArNavMapImageContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(ScanPangFigmaAssets.ArNavigationMap)
            .crossfade(true)
            .build(),
        contentDescription = null,
        modifier = modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
    )
}

@Composable
fun ArNavAgentPanelContent(
    userMessage: String,
    agentMessage: String,
    inputPlaceholder: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = ScanPangDimens.arTopBarHorizontal)
            .padding(bottom = ScanPangDimens.arChatAreaBottomPad),
        verticalArrangement = Arrangement.spacedBy(ScanPangDimens.arChatBubbleGap),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.md),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Surface(
                        shape = ScanPangShapes.arBubbleUser,
                        color = ScanPangColors.Primary,
                        shadowElevation = ScanPangDimens.arPoiCardShadowElevation,
                    ) {
                        Text(
                            text = userMessage,
                            modifier = Modifier.padding(
                                horizontal = ScanPangDimens.arTopBarHorizontal,
                                vertical = ScanPangDimens.icon10,
                            ),
                            style = ScanPangType.arNavTab13Inactive,
                            color = Color.White,
                            maxLines = 3,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                ) {
                    Surface(
                        shape = ScanPangShapes.arBubbleAgent,
                        color = ScanPangColors.ArOverlayWhite93,
                        shadowElevation = ScanPangDimens.arPoiCardShadowElevation,
                    ) {
                        Text(
                            text = agentMessage,
                            modifier = Modifier.padding(
                                horizontal = ScanPangDimens.arTopBarHorizontal,
                                vertical = ScanPangDimens.icon10,
                            ),
                            style = ScanPangType.arNavTab13Inactive,
                            color = ScanPangColors.OnSurfaceStrong,
                            maxLines = 4,
                        )
                    }
                }

            }
        }
        ArNavGuideInputBar(placeholder = inputPlaceholder)
    }
}

private val ArNavAgentUserBubbleBlue = Color(0xFF1A73E8)

@Composable
fun ArNavAiGuideTabWithTextField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSend: (String) -> Unit,
    messages: List<ArAgentChatMessage>,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ScanPangColors.ArBottomChatScrim)
            .padding(horizontal = ScanPangDimens.arTopBarHorizontal)
            .padding(bottom = ScanPangDimens.arChatAreaBottomPad),
        verticalArrangement = Arrangement.spacedBy(ScanPangDimens.arChatBubbleGap),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(ScanPangDimens.arChatBubbleGap),
        ) {
            items(messages) { msg ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start,
                ) {
                    Surface(
                        shape = if (msg.isUser) ScanPangShapes.arBubbleUser else ScanPangShapes.arBubbleAgent,
                        color = if (msg.isUser) ArNavAgentUserBubbleBlue else Color.White,
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
                .clip(ScanPangShapes.arInputPill)
                .background(ScanPangColors.ArOverlayWhite93)
                .padding(horizontal = ScanPangDimens.arInputInnerPadH, vertical = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
        ) {
            ArMicSttButton(isListening = false, onClick = {})
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = placeholder,
                        style = ScanPangType.searchPlaceholderRegular,
                        color = ScanPangColors.OnSurfacePlaceholder,
                    )
                },
                textStyle = ScanPangType.body15Medium.copy(color = ScanPangColors.OnSurfaceStrong),
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSend = { if (query.isNotBlank()) onSend(query) }
                ),
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
                onClick = { if (query.isNotBlank()) onSend(query) },
                enabled = query.isNotBlank(),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Send,
                    contentDescription = "전송",
                    modifier = Modifier.size(ScanPangDimens.icon16),
                    tint = if (query.isNotBlank()) ScanPangColors.Primary else ScanPangColors.OnSurfaceMuted,
                )
            }
        }
    }
}

@Composable
fun ArNavGuideInputBar(
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(ScanPangDimens.arNavGuideInputHeight),
        shape = ScanPangShapes.arInputPill,
        color = ScanPangColors.ArOverlayWhite85,
        shadowElevation = ScanPangDimens.arPoiCardShadowElevation,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = ScanPangDimens.chipPadHorizontal),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
        ) {
            Surface(
                modifier = Modifier.size(ScanPangDimens.arNavGuideMicBtn),
                shape = CircleShape,
                color = ScanPangColors.Primary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(ScanPangDimens.arMicSendIcon),
                        tint = Color.White,
                    )
                }
            }
            Text(
                text = placeholder,
                modifier = Modifier.weight(1f),
                style = ScanPangType.arNavGuideInput13,
                color = ScanPangColors.OnSurfacePlaceholder,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Surface(
                modifier = Modifier.size(ScanPangDimens.arNavGuideSendBtn),
                shape = CircleShape,
                color = ScanPangColors.ArSendChipBackground,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowUpward,
                        contentDescription = "전송",
                        modifier = Modifier.size(ScanPangDimens.arMicSendIcon),
                        tint = ScanPangColors.OnSurfaceMuted,
                    )
                }
            }
        }
    }
}

@Composable
fun ArNavStandaloneChatBlock(
    userMessage: String,
    agentMessage: String,
    inputPlaceholder: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(ScanPangDimens.arChatAreaMaxHeight)
            .background(ScanPangColors.ArBottomChatScrim)
            .padding(horizontal = ScanPangDimens.arTopBarHorizontal)
            .padding(bottom = ScanPangDimens.arChatAreaBottomPad),
        verticalArrangement = Arrangement.spacedBy(ScanPangDimens.arChatBubbleGap),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.md),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Surface(
                        shape = ScanPangShapes.arBubbleUser,
                        color = ScanPangColors.Primary,
                        shadowElevation = ScanPangDimens.arPoiCardShadowElevation,
                    ) {
                        Text(
                            text = userMessage,
                            modifier = Modifier.padding(
                                horizontal = ScanPangDimens.arTopBarHorizontal,
                                vertical = ScanPangDimens.icon10,
                            ),
                            style = ScanPangType.arNavTab13Inactive,
                            color = Color.White,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                ) {
                    Surface(
                        shape = ScanPangShapes.arBubbleAgent,
                        color = ScanPangColors.ArOverlayWhite93,
                        shadowElevation = ScanPangDimens.arPoiCardShadowElevation,
                    ) {
                        Text(
                            text = agentMessage,
                            modifier = Modifier.padding(
                                horizontal = ScanPangDimens.arTopBarHorizontal,
                                vertical = ScanPangDimens.icon10,
                            ),
                            style = ScanPangType.arNavTab13Inactive,
                            color = ScanPangColors.OnSurfaceStrong,
                        )
                    }
                }
            }
        }
        ArNavGuideInputBar(placeholder = inputPlaceholder)
    }
}

@Composable
fun BoxScope.ArArrivalBadgeStack(
    showCheckIcon: Boolean,
    arrivalLabel: String,
    badgeColor: Color,
) {
    Column(
        modifier = Modifier.align(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ScanPangDimens.arArrivalStackGap),
    ) {
        Surface(
            modifier = Modifier.size(ScanPangDimens.arNavTurnBadgeSize),
            shape = CircleShape,
            color = badgeColor,
            shadowElevation = ScanPangDimens.arPoiCardShadowElevation,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (showCheckIcon) Icons.Rounded.CheckCircle else Icons.Rounded.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(ScanPangDimens.arNavLocationBadgeIcon),
                    tint = Color.White,
                )
            }
        }
        Surface(
            shape = ScanPangShapes.radius12,
            color = ScanPangColors.ArOverlayWhite93,
            shadowElevation = ScanPangDimens.arPoiCardShadowElevation,
        ) {
            Text(
                text = arrivalLabel,
                modifier = Modifier.padding(
                    horizontal = ScanPangDimens.arArrivalLabelPadH,
                    vertical = ScanPangDimens.arArrivalLabelPadV,
                ),
                style = ScanPangType.arArrivalTitle16,
                color = ScanPangColors.OnSurfaceStrong,
            )
        }
    }
}

/** 길안내 POI: 쇼핑(왼쪽)·환전(오른쪽) — Figma 위치 */
@Composable
fun BoxScope.ArNavDefaultPoiMarkers(
    onShoppingPoiClick: () -> Unit = {},
    onExchangePoiClick: () -> Unit = {},
) {
    ArNavPoiFab(
        icon = Icons.Rounded.LocalMall,
        tint = ScanPangColors.CategoryMall,
        modifier = Modifier.padding(
            start = ScanPangDimens.arNavPoiOneStart,
            top = ScanPangDimens.arNavPoiOneTop,
        ),
        onClick = onShoppingPoiClick,
    )
    ArNavPoiFab(
        icon = Icons.Rounded.CurrencyExchange,
        tint = ScanPangColors.CategoryExchange,
        modifier = Modifier.padding(
            start = ScanPangDimens.arNavPoiTwoStart,
            top = ScanPangDimens.arNavPoiTwoTop,
        ),
        onClick = onExchangePoiClick,
    )
}

@Composable
fun ArNavStopNavigationSheet(
    destinationName: String,
    onDismiss: () -> Unit,
    onStopNavigation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onDismiss,
                ),
        )
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = ScanPangDimens.arNavTopFab40 + ScanPangDimens.arTopBarBottomPadding)
                .padding(horizontal = ScanPangDimens.arTopBarHorizontal)
                .fillMaxWidth()
                .clickable(enabled = false, onClick = {}),
            shape = ScanPangShapes.radius16,
            color = ScanPangColors.Surface,
            shadowElevation = ScanPangDimens.arPoiCardShadowElevation,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(ScanPangSpacing.md),
                verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ScanPangDimens.arNavGuideInputHeight),
                    shape = ScanPangShapes.filterChip,
                    color = ScanPangColors.DangerStrong,
                    onClick = onStopNavigation,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "길안내 종료",
                            style = ScanPangType.title16SemiBold,
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ArNavStopConfirmDialog(
    onNavigateToExplore: () -> Unit,
    onNavigateToHome: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "길안내를 종료할까요?",
                style = ScanPangType.arFilterTitle16,
                color = ScanPangColors.OnSurfaceStrong,
            )
        },
        text = {
            Text(
                text = "이동할 위치를 선택해주세요.",
                style = ScanPangType.body14Regular,
                color = ScanPangColors.OnSurfaceMuted,
            )
        },
        confirmButton = {
            TextButton(onClick = onNavigateToExplore) {
                Text(
                    text = "탐색으로 돌아가기",
                    style = ScanPangType.title14,
                    color = ScanPangColors.Primary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onNavigateToHome) {
                Text(
                    text = "홈으로 돌아가기",
                    style = ScanPangType.title14,
                    color = ScanPangColors.OnSurfaceMuted,
                )
            }
        },
        containerColor = ScanPangColors.Surface,
        shape = ScanPangShapes.radius16,
    )
}
