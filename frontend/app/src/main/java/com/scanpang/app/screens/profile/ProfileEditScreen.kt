package com.scanpang.app.screens.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.scanpang.app.data.OnboardingPreferences
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangDimens
import com.scanpang.app.ui.theme.ScanPangShapes
import com.scanpang.app.ui.theme.ScanPangSpacing
import com.scanpang.app.ui.theme.ScanPangType
import kotlinx.coroutines.launch

private fun containsKorean(text: String) = text.any { it in '가'..'힣' }
private fun maxNameLength(text: String) = if (containsKorean(text)) 6 else 12

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = remember { OnboardingPreferences(context) }
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    var photoUri by remember { mutableStateOf(prefs.getProfilePhotoUri()) }
    var nameInput by remember { mutableStateOf(prefs.getDisplayName().orEmpty()) }
    var showPhotoSheet by remember { mutableStateOf(false) }
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            // content URI를 영구 권한으로 접근 가능하도록 persistence 부여
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            photoUri = uri.toString()
        }
    }

    val maxLen = maxNameLength(nameInput)
    val nameIsValid = nameInput.isNotBlank()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ScanPangColors.Surface,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ScanPangColors.Surface)
                .statusBarsPadding(),
        ) {
            // ── 타이틀 바 ──────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(start = 4.dp, end = ScanPangDimens.screenHorizontal),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "뒤로",
                        tint = ScanPangColors.OnSurfaceStrong,
                    )
                }
                Text(
                    text = "프로필 편집",
                    style = ScanPangType.profileName18,
                    color = ScanPangColors.OnSurfaceStrong,
                    modifier = Modifier.weight(1f),
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = ScanPangDimens.screenHorizontal)
                    .padding(top = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // ── 프로필 사진 ─────────────────────────────────────────
                Box(
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(ScanPangColors.Background)
                            .border(2.dp, ScanPangColors.OutlineSubtle, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (photoUri != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(photoUri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "프로필 사진",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.AccountCircle,
                                contentDescription = null,
                                tint = ScanPangColors.OnSurfacePlaceholder,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(ScanPangColors.Primary)
                            .clickable { showPhotoSheet = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CameraAlt,
                            contentDescription = "사진 변경",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // ── 이름 입력 ───────────────────────────────────────────
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "이름",
                        style = ScanPangType.sectionLabelSemiBold13,
                        color = ScanPangColors.OnSurfaceMuted,
                    )
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { input ->
                            val limit = maxNameLength(input)
                            if (input.length <= limit) nameInput = input
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = {
                            Text(
                                text = "이름을 입력하세요",
                                style = ScanPangType.body15Medium,
                                color = ScanPangColors.OnSurfacePlaceholder,
                            )
                        },
                        textStyle = ScanPangType.body15Medium.copy(color = ScanPangColors.OnSurfaceStrong),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        shape = ScanPangShapes.radius12,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ScanPangColors.Primary,
                            unfocusedBorderColor = ScanPangColors.OutlineSubtle,
                            focusedContainerColor = ScanPangColors.Surface,
                            unfocusedContainerColor = ScanPangColors.Surface,
                        ),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        val hasKorean = containsKorean(nameInput)
                        Text(
                            text = when {
                                nameInput.isEmpty() -> "한글 최대 6자 · 영문 최대 12자"
                                hasKorean -> "한글 최대 6자"
                                else -> "영문 최대 12자"
                            },
                            style = ScanPangType.caption12,
                            color = ScanPangColors.OnSurfacePlaceholder,
                        )
                        Text(
                            text = "${nameInput.length} / $maxLen",
                            style = ScanPangType.caption12,
                            color = if (nameInput.length >= maxLen) ScanPangColors.DangerStrong
                                    else ScanPangColors.OnSurfacePlaceholder,
                        )
                    }
                }
            }

            // ── 저장 버튼 ───────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScanPangDimens.screenHorizontal)
                    .padding(bottom = ScanPangSpacing.xl),
            ) {
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        prefs.setDisplayName(nameInput)
                        prefs.setProfilePhotoUri(photoUri)
                        navController.popBackStack()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = nameIsValid,
                    shape = ScanPangShapes.radius12,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ScanPangColors.Primary,
                        disabledContainerColor = ScanPangColors.OutlineSubtle,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = "저장",
                        style = ScanPangType.body15Medium,
                    )
                }
            }
        }
    }

    // ── 사진 선택 바텀시트 ─────────────────────────────────────────────
    if (showPhotoSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPhotoSheet = false },
            sheetState = bottomSheetState,
            containerColor = ScanPangColors.Surface,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
            ) {
                Text(
                    text = "프로필 사진 변경",
                    style = ScanPangType.sectionLabelSemiBold13,
                    color = ScanPangColors.OnSurfaceMuted,
                    modifier = Modifier.padding(
                        horizontal = ScanPangDimens.screenHorizontal,
                        vertical = 12.dp,
                    ),
                )
                HorizontalDivider(color = ScanPangColors.OutlineSubtle)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch { bottomSheetState.hide() }.invokeOnCompletion {
                                showPhotoSheet = false
                            }
                            photoUri = null
                        }
                        .padding(horizontal = ScanPangDimens.screenHorizontal, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(ScanPangColors.Background),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = null,
                            tint = ScanPangColors.OnSurfaceMuted,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "기본 사진 사용",
                            style = ScanPangType.body15Medium,
                            color = ScanPangColors.OnSurfaceStrong,
                        )
                        Text(
                            text = "기본 프로필 이미지로 변경합니다",
                            style = ScanPangType.caption12,
                            color = ScanPangColors.OnSurfacePlaceholder,
                        )
                    }
                }
                HorizontalDivider(color = ScanPangColors.OutlineSubtle)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch { bottomSheetState.hide() }.invokeOnCompletion {
                                showPhotoSheet = false
                            }
                            galleryLauncher.launch("image/*")
                        }
                        .padding(horizontal = ScanPangDimens.screenHorizontal, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(ScanPangColors.PrimarySoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Image,
                            contentDescription = null,
                            tint = ScanPangColors.Primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "갤러리에서 선택",
                            style = ScanPangType.body15Medium,
                            color = ScanPangColors.OnSurfaceStrong,
                        )
                        Text(
                            text = "휴대폰 갤러리에서 사진을 불러옵니다",
                            style = ScanPangType.caption12,
                            color = ScanPangColors.OnSurfacePlaceholder,
                        )
                    }
                }
            }
        }
    }
}
