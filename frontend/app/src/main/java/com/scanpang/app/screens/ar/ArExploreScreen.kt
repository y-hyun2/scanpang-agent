package com.scanpang.app.screens.ar

import android.Manifest
import android.content.pm.PackageManager
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.scanpang.app.ar.ArExploreTtsController
import com.scanpang.app.ar.ArSpeechRecognizerHelper
import android.content.Intent
import com.scanpang.app.ar.ScanPangAgentService
import com.scanpang.app.ar.sendVoiceMessage
import com.scanpang.app.ar.explore.PlaceAugmentingActivity
import com.scanpang.app.data.remote.ScanPangViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import com.scanpang.app.components.ar.ArAgentChatMessage
import com.scanpang.app.components.ar.ArCameraBackdrop
import com.scanpang.app.components.ar.ArCircleIconButton
import com.scanpang.app.components.ar.ArExploreInteractiveChatSection
import com.scanpang.app.components.ar.ArFloorStoreGuideOverlay
import com.scanpang.app.components.ar.ArPoiFloatingDetailOverlay
import com.scanpang.app.components.ar.ArPoiTabBuilding
import com.scanpang.app.components.ar.ArExploreFilterPanelFigma
import com.scanpang.app.components.ar.ArExploreSideColumn
import com.scanpang.app.components.ar.arExploreCategoryChipSpecs
import com.scanpang.app.components.ar.ArPoiPinsLayer
import com.scanpang.app.navigation.AppRoutes
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangDimens
import com.scanpang.app.ui.theme.ScanPangShapes
import com.scanpang.app.ui.theme.ScanPangSpacing
import com.scanpang.app.ui.theme.ScanPangType
import kotlinx.coroutines.launch

private data class ArSearchHit(
    val title: String,
    val scoreLine: String,
    val distance: String,
)

/**
 * AR 탐색 단일 화면 — 필터·검색·고정·POI 시트·TTS 등 상태로 처리.
 */
@Composable
fun ArExploreScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: ScanPangViewModel = viewModel(),
) {
    val placeResult by viewModel.placeResult.collectAsState()
    val context = LocalContext.current

    // AR 탐색 Activity 실행 (ARCore VPS + building_raycast + place/query)
    LaunchedEffect(Unit) {
        context.startActivity(Intent(context, PlaceAugmentingActivity::class.java))
    }
    val appContext = context.applicationContext
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val chatListState = rememberLazyListState()
    var chatInput by remember { mutableStateOf("") }
    var chatMessages by remember {
        mutableStateOf(
            listOf(
                ArAgentChatMessage(
                    text = "안녕하세요! 스캔팡입니다. 주변 장소를 AR로 안내해 드릴게요.",
                    isUser = false,
                ),
                ArAgentChatMessage(
                    text = "아미나님, 오늘은 어떤 할랄 맛집을 찾으세요?",
                    isUser = true,
                ),
            ),
        )
    }

    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            chatListState.scrollToItem(chatMessages.lastIndex)
        }
    }

    var isFilterOpen by remember { mutableStateOf(false) }
    var categorySelection by remember { mutableStateOf(setOf<String>()) }
    var isSearchOpen by remember { mutableStateOf(false) }
    var showArSearchResults by remember { mutableStateOf(false) }

    var isFrozen by remember { mutableStateOf(false) }
    var isTtsOn by remember { mutableStateOf(true) }

    var isSttListening by remember { mutableStateOf(false) }
    val ttsPlayingState = remember { mutableStateOf(false) }
    val isTtsPlaying by ttsPlayingState
    var speechHelperRef by remember { mutableStateOf<ArSpeechRecognizerHelper?>(null) }
    var pendingMicAfterPermission by remember { mutableStateOf(false) }

    val agentService = remember { ScanPangAgentService() }
    val ttsController = remember(appContext) {
        ArExploreTtsController(appContext) { playing -> ttsPlayingState.value = playing }
    }

    DisposableEffect(ttsController) {
        ttsController.start()
        onDispose { ttsController.shutdown() }
    }

    LaunchedEffect(isTtsOn) {
        if (!isTtsOn) ttsController.stop()
    }

    val onSttResult: (String) -> Unit = { text ->
        chatInput = text
        scope.launch {
            val reply = sendVoiceMessage(text, agentService)
            chatMessages = chatMessages +
                ArAgentChatMessage(text = text, isUser = true) +
                ArAgentChatMessage(text = reply, isUser = false)
            chatInput = ""
            ttsController.speakIfEnabled(reply, isTtsOn)
        }
    }
    val latestOnSttResult = rememberUpdatedState(onSttResult)

    val latestSnackbar = rememberUpdatedState(snackbarHostState)
    val latestScope = rememberUpdatedState(scope)

    DisposableEffect(appContext) {
        val h = ArSpeechRecognizerHelper(
            context = appContext,
            onListeningChange = { isSttListening = it },
            onResult = { text -> latestOnSttResult.value(text) },
            onErrorCode = { code ->
                if (code != SpeechRecognizer.ERROR_NO_MATCH &&
                    code != SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                ) {
                    latestScope.value.launch {
                        latestSnackbar.value.showSnackbar("음성 인식 중 오류가 났어요")
                    }
                }
            },
        )
        speechHelperRef = h
        onDispose {
            h.destroy()
            speechHelperRef = null
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && pendingMicAfterPermission) {
            speechHelperRef?.startListening()
        } else if (!granted) {
            scope.launch { snackbarHostState.showSnackbar("마이크 권한이 필요해요") }
        }
        pendingMicAfterPermission = false
    }

    var selectedPoi by remember { mutableStateOf<String?>(null) }
    var activeDetailTab by remember { mutableStateOf(ArPoiTabBuilding) }
    var selectedStore by remember { mutableStateOf<String?>(null) }

    val categoryChipSpecs = remember { arExploreCategoryChipSpecs() }
    val recentQueries = remember {
        listOf("할랄 식당", "명동성당", "근처 환전소")
    }
    val suggestionTags = remember {
        listOf("할랄", "카페", "기도실", "환전소")
    }
    val searchHits = remember {
        listOf(
            ArSearchHit("할랄가든 명동점", "일치도 98%", "120m"),
            ArSearchHit("명동성당", "일치도 92%", "350m"),
            ArSearchHit("우리은행 환전소", "일치도 88%", "80m"),
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            ArCameraBackdrop(
                showFreezeTint = isFrozen,
                modifier = Modifier.fillMaxSize(),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                ScanPangColors.ArExploreScrimGradientTop,
                                ScanPangColors.ArExploreScrimGradientBottom,
                            ),
                        ),
                    )
                    .statusBarsPadding()
                    .padding(horizontal = ScanPangDimens.arTopBarHorizontal)
                    .padding(bottom = ScanPangDimens.arTopBarBottomPadding),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(
                            maxOf(
                                ScanPangDimens.arCircleBtn36,
                                ScanPangDimens.arStatusPillHeight,
                            ),
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArCircleIconButton(
                        icon = Icons.Rounded.Home,
                        contentDescription = "홈",
                        onClick = { navController.popBackStack() },
                        modifier = Modifier,
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = ScanPangSpacing.sm),
                        contentAlignment = Alignment.Center,
                    ) {
                        ArExploreStatusPill(
                            isFrozen = isFrozen,
                            selectedFilters = categorySelection,
                            onClick = {
                                if (isFrozen) {
                                    isFrozen = false
                                } else {
                                    isFilterOpen = true
                                }
                            },
                        )
                    }
                    ArCircleIconButton(
                        icon = Icons.Rounded.Search,
                        contentDescription = "검색",
                        onClick = { isSearchOpen = true },
                        modifier = Modifier,
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                ArPoiPinsLayer(
                    onPoiOneClick = {
                        selectedPoi = placeResult?.ar_overlay?.name ?: "눈스퀘어"
                        activeDetailTab = ArPoiTabBuilding
                        selectedStore = null
                        viewModel.queryPlace(heading = 0.0, lat = 37.5636, lng = 126.9822)
                    },
                    onPoiTwoClick = {
                        selectedPoi = "명동빌딩"
                        activeDetailTab = ArPoiTabBuilding
                        selectedStore = null
                        viewModel.queryPlace(heading = 90.0, lat = 37.5636, lng = 126.9822)
                    },
                )
                ArExploreSideColumn(
                    onTtsClick = {
                        isTtsOn = !isTtsOn
                        val msg = if (isTtsOn) "음성 안내 켜짐" else "음성 안내 꺼짐"
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                    },
                    onCameraClick = { isFrozen = !isFrozen },
                    isTtsOn = isTtsOn,
                    isFrozen = isFrozen,
                    isTtsPlaying = isTtsPlaying,
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding(),
            ) {
                ArExploreInteractiveChatSection(
                    messages = chatMessages,
                    inputText = chatInput,
                    onInputChange = { chatInput = it },
                    onSend = send@{
                        val q = chatInput.trim()
                        if (q.isEmpty()) return@send
                        scope.launch {
                            val reply = agentService.sendMessage(q)
                            chatMessages = chatMessages +
                                ArAgentChatMessage(text = q, isUser = true) +
                                ArAgentChatMessage(text = reply, isUser = false)
                            chatInput = ""
                            ttsController.speakIfEnabled(reply, isTtsOn)
                        }
                    },
                    isSttListening = isSttListening,
                    onMicClick = mic@{
                        val h = speechHelperRef
                        if (isSttListening) {
                            h?.stopListening()
                            return@mic
                        }
                        if (h == null) {
                            scope.launch {
                                snackbarHostState.showSnackbar("음성 입력을 준비하지 못했어요")
                            }
                            return@mic
                        }
                        if (!h.isRecognitionAvailable()) {
                            scope.launch {
                                snackbarHostState.showSnackbar("이 기기에서 음성 인식을 쓸 수 없어요")
                            }
                            return@mic
                        }
                        val hasMic = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasMic) {
                            h.startListening()
                        } else {
                            pendingMicAfterPermission = true
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    listState = chatListState,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            AnimatedVisibility(
                visible = isFilterOpen,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ScanPangColors.ArOverlayScrimDark)
                        .clickable { isFilterOpen = false },
                ) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(horizontal = ScanPangDimens.arFilterPanelHorizontal)
                            .padding(top = ScanPangSpacing.lg)
                            .clickable(enabled = false) { },
                        shape = ScanPangShapes.arFilterPanelTop,
                        color = ScanPangColors.Surface,
                        shadowElevation = ScanPangDimens.arPoiCardShadowElevation,
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(ScanPangDimens.arTopBarHorizontal)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            ArExploreFilterPanelFigma(
                                categorySpecs = categoryChipSpecs,
                                categorySelection = categorySelection,
                                onCategoryToggle = { label ->
                                    categorySelection =
                                        if (label in categorySelection) {
                                            categorySelection - label
                                        } else {
                                            categorySelection + label
                                        }
                                },
                                onReset = { categorySelection = emptySet() },
                                onApply = { isFilterOpen = false },
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = isSearchOpen,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ScanPangColors.ArOverlayScrimDark)
                        .clickable { isSearchOpen = false; showArSearchResults = false },
                ) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(horizontal = ScanPangDimens.arFilterPanelHorizontal)
                            .padding(top = ScanPangSpacing.lg)
                            .clickable(enabled = false) { },
                        shape = ScanPangShapes.arSearchPanel,
                        color = ScanPangColors.Surface,
                        shadowElevation = ScanPangDimens.arPoiCardShadowElevation,
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(ScanPangDimens.arTopBarHorizontal)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.md),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Search,
                                        contentDescription = null,
                                        tint = ScanPangColors.OnSurfaceMuted,
                                        modifier = Modifier.size(ScanPangDimens.icon20),
                                    )
                                    Text(
                                        text = "장소·메뉴 검색",
                                        style = ScanPangType.searchPlaceholderRegular,
                                        color = ScanPangColors.OnSurfacePlaceholder,
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        isSearchOpen = false
                                        showArSearchResults = false
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "닫기",
                                        tint = ScanPangColors.OnSurfaceStrong,
                                    )
                                }
                            }
                            Text(
                                text = "최근 검색",
                                style = ScanPangType.sectionTitle16,
                                color = ScanPangColors.OnSurfaceStrong,
                            )
                            recentQueries.forEach { q ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showArSearchResults = true
                                        }
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
                                    )
                                }
                            }
                            Text(
                                text = "추천 검색어",
                                style = ScanPangType.sectionTitle16,
                                color = ScanPangColors.OnSurfaceStrong,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
                            ) {
                                suggestionTags.forEach { tag ->
                                    Surface(
                                        shape = ScanPangShapes.badge6,
                                        color = ScanPangColors.ArRecommendTagHalalBackground,
                                        modifier = Modifier.clickable { showArSearchResults = true },
                                    ) {
                                        Text(
                                            text = tag,
                                            modifier = Modifier.padding(
                                                horizontal = ScanPangDimens.arSearchTagHorizontalPad,
                                                vertical = ScanPangDimens.arSearchTagVerticalPad,
                                            ),
                                            style = ScanPangType.tag11Medium,
                                            color = ScanPangColors.Primary,
                                        )
                                    }
                                }
                            }
                            if (showArSearchResults) {
                                HorizontalDivider(color = ScanPangColors.OutlineSubtle)
                                Text(
                                    text = "정확도 · 거리순",
                                    style = ScanPangType.meta11SemiBold,
                                    color = ScanPangColors.OnSurfaceMuted,
                                )
                                searchHits.forEach { hit ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = ScanPangSpacing.sm),
                                    ) {
                                        Text(
                                            text = hit.title,
                                            style = ScanPangType.title14,
                                            color = ScanPangColors.OnSurfaceStrong,
                                        )
                                        Text(
                                            text = "${hit.scoreLine} · ${hit.distance}",
                                            style = ScanPangType.caption12Medium,
                                            color = ScanPangColors.OnSurfaceMuted,
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
                                            modifier = Modifier.padding(top = ScanPangSpacing.sm),
                                        ) {
                                            TextButton(
                                                onClick = {
                                                    selectedPoi = hit.title
                                                    activeDetailTab = ArPoiTabBuilding
                                                    isSearchOpen = false
                                                    showArSearchResults = false
                                                },
                                            ) {
                                                Text(
                                                    text = "정보 보기",
                                                    color = ScanPangColors.Primary,
                                                    style = ScanPangType.body15Medium,
                                                )
                                            }
                                            TextButton(
                                                onClick = {
                                                    navController.navigate(AppRoutes.ArNavMap) {
                                                        launchSingleTop = true
                                                    }
                                                    isSearchOpen = false
                                                    showArSearchResults = false
                                                },
                                            ) {
                                                Text(
                                                    text = "길안내",
                                                    color = ScanPangColors.Primary,
                                                    style = ScanPangType.body15Medium,
                                                )
                                            }
                                        }
                                    }
                                    HorizontalDivider(color = ScanPangColors.OutlineSubtle)
                                }
                            }
                        }
                    }
                }
            }

            selectedPoi?.let { poi ->
                ArPoiFloatingDetailOverlay(
                    poiName = poi,
                    activeDetailTab = activeDetailTab,
                    onActiveDetailTabChange = { activeDetailTab = it },
                    onDismiss = {
                        selectedPoi = null
                        selectedStore = null
                        activeDetailTab = ArPoiTabBuilding
                    },
                    onFloorStoreClick = { selectedStore = it },
                    onSave = {
                        scope.launch { snackbarHostState.showSnackbar("저장되었습니다") }
                    },
                    modifier = Modifier.fillMaxSize(),
                    arOverlay = placeResult?.ar_overlay,
                    docent = placeResult?.docent,
                )
            }

            selectedStore?.let { store ->
                ArFloorStoreGuideOverlay(
                    storeName = store,
                    onDismiss = { selectedStore = null },
                    onStartNavigation = {
                        navController.navigate(AppRoutes.ArNavMap) { launchSingleTop = true }
                        selectedStore = null
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun ArExploreStatusPill(
    isFrozen: Boolean,
    selectedFilters: Set<String>,
    onClick: () -> Unit,
) {
    when {
        isFrozen -> {
            Surface(
                modifier = Modifier
                    .height(ScanPangDimens.arStatusPillHeight)
                    .clip(CircleShape)
                    .clickable(onClick = onClick),
                shape = CircleShape,
                color = ScanPangColors.Primary,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = ScanPangDimens.arStatusPillHorizontalPad),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.xs),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Pause,
                        contentDescription = null,
                        modifier = Modifier.size(ScanPangDimens.icon18),
                        tint = Color.White,
                    )
                    Text(
                        text = "화면 고정 중",
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
        selectedFilters.isEmpty() -> {
            Surface(
                modifier = Modifier
                    .height(ScanPangDimens.arStatusPillHeight)
                    .clip(CircleShape)
                    .clickable(onClick = onClick),
                shape = CircleShape,
                color = ScanPangColors.ArOverlayWhite80,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = ScanPangDimens.arStatusPillHorizontalPad),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.xs),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CropFree,
                        contentDescription = null,
                        modifier = Modifier.size(ScanPangDimens.icon18),
                        tint = ScanPangColors.OnSurfaceStrong,
                    )
                    Text(
                        text = "AR 탐색 중",
                        style = ScanPangType.arStatusPill15,
                        color = ScanPangColors.OnSurfaceStrong,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(ScanPangDimens.arNavDestinationChevron),
                        tint = ScanPangColors.OnSurfacePlaceholder,
                    )
                }
            }
        }
        else -> {
            val label = buildFilterPillLabel(selectedFilters)
            Surface(
                modifier = Modifier
                    .height(ScanPangDimens.arStatusPillHeight)
                    .clip(CircleShape)
                    .clickable(onClick = onClick),
                shape = CircleShape,
                color = ScanPangColors.Primary,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = ScanPangDimens.arStatusPillHorizontalPad),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.xs),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FilterList,
                        contentDescription = null,
                        modifier = Modifier.size(ScanPangDimens.icon18),
                        tint = Color.White,
                    )
                    Text(
                        text = label,
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
    }
}

private fun buildFilterPillLabel(selected: Set<String>): String {
    val list = selected.toList()
    if (list.isEmpty()) return ""
    if (list.size == 1) return list[0]
    return "${list[0]} 외 ${list.size - 1}개"
}

