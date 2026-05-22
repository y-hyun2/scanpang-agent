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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.scanpang.app.i18n.LocalStrings
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangDimens
import com.scanpang.app.ui.theme.ScanPangShapes
import com.scanpang.app.ui.theme.ScanPangSpacing
import com.scanpang.app.ui.theme.ScanPangType

@Composable
fun HelpScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val faqItems = remember(s) { s.helpFaqItems }
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
                title = s.profileHelp,
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
                        text = s.helpFaqTitle,
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
