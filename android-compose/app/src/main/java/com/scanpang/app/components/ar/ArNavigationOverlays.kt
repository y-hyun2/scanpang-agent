package com.scanpang.app.components.ar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.statusBarsPadding
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
    onHomeClick: () -> Unit,
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
                icon = Icons.Rounded.Home,
                contentDescription = "홈",
                onClick = onHomeClick,
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
) {
    Surface(
        modifier = modifier
            .size(ScanPangDimens.arNavTopFab40)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = ScanPangColors.ArOverlayWhite80,
        shadowElevation = ScanPangDimens.arPoiCardShadowElevation,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(ScanPangDimens.arNavTopFabIcon),
                tint = ScanPangColors.OnSurfaceStrong,
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
    onCameraClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(
                end = ScanPangDimens.arSideColumnEnd,
                top = ScanPangDimens.arSideColumnTop,
            ),
        verticalArrangement = Arrangement.spacedBy(ScanPangDimens.arSideIconGap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArNavWhiteFab(
            icon = Icons.AutoMirrored.Rounded.VolumeUp,
            contentDescription = "볼륨",
            onClick = onVolumeClick,
        )
        ArNavWhiteFab(
            icon = Icons.Rounded.CameraAlt,
            contentDescription = "촬영",
            onClick = onCameraClick,
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
    currentManeuverIcon: ImageVector,
    currentDistance: String,
    currentInstruction: String,
) {
    Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(
                start = ScanPangDimens.arNavActionClusterStart,
                top = ScanPangDimens.arNavActionClusterTop,
            )
            .width(ScanPangDimens.arNavActionCardWidth)
            .height(ScanPangDimens.arNavActionStackHeight),
    ) {
            if (showNextStep) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = ScanPangDimens.arNavNextStepOffsetStart)
                        .width(ScanPangDimens.arNavNextStepWidth)
                        .height(ScanPangDimens.arNavNextStepHeight)
                        .clip(ScanPangShapes.arNavNextStepChip)
                        .background(ScanPangColors.ArNavNextStepBackground),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowUpward,
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
            Surface(
                modifier = Modifier.align(Alignment.TopStart),
                shape = ScanPangShapes.radius16,
                color = ScanPangColors.ArOverlayWhite93,
                shadowElevation = ScanPangDimens.arPoiCardShadowElevation,
            ) {
                Row(
                    modifier = Modifier
                        .width(ScanPangDimens.arNavActionCardWidth)
                        .padding(
                            horizontal = ScanPangDimens.arNavActionCardPadH,
                            vertical = ScanPangDimens.arNavActionCardPadV,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.md),
                ) {
                    Surface(
                        modifier = Modifier.size(ScanPangDimens.arNavActionIconSquare),
                        shape = ScanPangShapes.radius14,
                        color = ScanPangColors.Primary,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = currentManeuverIcon,
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
                            text = currentDistance,
                            style = ScanPangType.arNavDistance26,
                            color = ScanPangColors.OnSurfaceStrong,
                        )
                        Text(
                            text = currentInstruction,
                            style = ScanPangType.arNavStepCaption12,
                            color = ScanPangColors.OnSurfaceMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
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
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ScanPangShapes.arNavBottomSheetTop,
        color = ScanPangColors.ArNavBottomSheetBackground,
        shadowElevation = ScanPangDimens.arPoiCardShadowElevation,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(ScanPangDimens.arChatAreaMaxHeight),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ScanPangDimens.arNavBottomSheetDragH),
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
                    .weight(1f, fill = true),
            ) {
                if (mapTabSelected) {
                    mapContent()
                } else {
                    agentContent()
                }
            }
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

@Composable
fun ArNavAiGuideTabWithTextField(
    query: String,
    onQueryChange: (String) -> Unit,
    userMessage: String,
    agentMessage: String,
    placeholder: String,
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
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(ScanPangDimens.arNavGuideInputHeight),
            placeholder = {
                Text(
                    text = placeholder,
                    style = ScanPangType.arNavGuideInput13,
                    color = ScanPangColors.OnSurfacePlaceholder,
                )
            },
            textStyle = ScanPangType.arNavGuideInput13.copy(color = ScanPangColors.OnSurfaceStrong),
            singleLine = true,
            shape = ScanPangShapes.arInputPill,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = ScanPangColors.OnSurfaceStrong,
                unfocusedTextColor = ScanPangColors.OnSurfaceStrong,
                focusedBorderColor = ScanPangColors.Primary,
                unfocusedBorderColor = ScanPangColors.OutlineSubtle,
                focusedContainerColor = ScanPangColors.ArOverlayWhite85,
                unfocusedContainerColor = ScanPangColors.ArOverlayWhite85,
                cursorColor = ScanPangColors.Primary,
            ),
        )
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
