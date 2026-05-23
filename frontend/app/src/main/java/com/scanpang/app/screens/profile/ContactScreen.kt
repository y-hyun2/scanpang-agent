package com.scanpang.app.screens.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Image
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.scanpang.app.data.auth.AuthRepository
import com.scanpang.app.data.remote.InquirySubmitRequest
import com.scanpang.app.data.remote.RetrofitClient
import com.scanpang.app.i18n.LocalStrings
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangDimens
import com.scanpang.app.ui.theme.ScanPangShapes
import com.scanpang.app.ui.theme.ScanPangSpacing
import kotlinx.coroutines.launch
import com.scanpang.app.ui.theme.ScanPangType

private const val MAX_PHOTOS = 3

@Composable
fun ContactScreen(
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
                title = LocalStrings.current.profileInquiry,
                onBack = { navController.popBackStack() },
            )
            InquiryTab()
        }
    }
}

// ─── 1:1 문의 ─────────────────────────────────────────────────────────────

@Composable
private fun InquiryTab() {
    val s = LocalStrings.current
    val context = LocalContext.current
    var selectedCategory by rememberSaveable { mutableStateOf(s.contactCategoryDefault) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var titleValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var bodyValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var attachedPhotos by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        val remaining = MAX_PHOTOS - attachedPhotos.size
        val newUris = uris.take(remaining)
        newUris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        attachedPhotos = attachedPhotos + newUris
    }

    if (submitted) {
        InquirySuccessBanner(onReset = {
            submitted = false
            selectedCategory = s.contactCategoryDefault
            titleValue = TextFieldValue("")
            bodyValue = TextFieldValue("")
            attachedPhotos = emptyList()
        })
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = ScanPangDimens.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.md),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            top = ScanPangSpacing.lg,
            bottom = ScanPangDimens.mainTabContentBottomInset + ScanPangSpacing.xl,
        ),
    ) {
        item {
            Text(
                text = s.contactFormTitle,
                style = ScanPangType.sectionTitle,
                color = ScanPangColors.OnSurfaceStrong,
            )
        }
        item { Spacer(modifier = Modifier.height(4.dp)) }
        item {
            InquiryFieldLabel(s.contactCategoryLabel)
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ScanPangShapes.radius12)
                    .border(ScanPangDimens.borderHairline, ScanPangColors.OutlineSubtle, ScanPangShapes.radius12)
                    .background(ScanPangColors.Surface)
                    .clickable { showCategoryPicker = !showCategoryPicker }
                    .padding(horizontal = ScanPangSpacing.lg, vertical = 14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = selectedCategory,
                        style = ScanPangType.body15Medium,
                        color = if (selectedCategory == s.contactCategoryDefault)
                            ScanPangColors.OnSurfacePlaceholder
                        else ScanPangColors.OnSurfaceStrong,
                    )
                    Icon(
                        imageVector = if (showCategoryPicker) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        tint = ScanPangColors.OnSurfaceMuted,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            AnimatedVisibility(
                visible = showCategoryPicker,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ScanPangShapes.radius12)
                        .border(ScanPangDimens.borderHairline, ScanPangColors.OutlineSubtle, ScanPangShapes.radius12)
                        .background(ScanPangColors.Surface)
                        .padding(vertical = ScanPangSpacing.sm),
                ) {
                    s.contactCategories.forEach { cat ->
                        Text(
                            text = cat,
                            style = ScanPangType.body15Medium,
                            color = if (cat == selectedCategory) ScanPangColors.Primary
                            else ScanPangColors.OnSurfaceStrong,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedCategory = cat
                                    showCategoryPicker = false
                                }
                                .padding(horizontal = ScanPangSpacing.lg, vertical = 12.dp),
                        )
                    }
                }
            }
        }
        item {
            InquiryFieldLabel(s.contactTitleLabel)
            Spacer(modifier = Modifier.height(6.dp))
            InquiryTextField(
                value = titleValue,
                onValueChange = { if (it.text.length <= 60) titleValue = it },
                placeholder = s.contactTitlePlaceholder,
                singleLine = true,
            )
        }
        item {
            InquiryFieldLabel(s.contactBodyLabel)
            Spacer(modifier = Modifier.height(6.dp))
            InquiryTextField(
                value = bodyValue,
                onValueChange = { if (it.text.length <= 500) bodyValue = it },
                placeholder = s.contactBodyPlaceholder,
                singleLine = false,
                minHeight = 140.dp,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${bodyValue.text.length}/500",
                style = ScanPangType.caption12,
                color = ScanPangColors.OnSurfacePlaceholder,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                InquiryFieldLabel(s.contactPhotoLabel)
                Text(
                    text = "${attachedPhotos.size}/$MAX_PHOTOS",
                    style = ScanPangType.caption12,
                    color = ScanPangColors.OnSurfacePlaceholder,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
            ) {
                attachedPhotos.forEachIndexed { index, uri ->
                    PhotoThumbnail(
                        uri = uri,
                        onRemove = {
                            attachedPhotos = attachedPhotos.toMutableList().also { it.removeAt(index) }
                        },
                    )
                }
                if (attachedPhotos.size < MAX_PHOTOS) {
                    PhotoAddButton(onClick = { photoPickerLauncher.launch("image/*") })
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = s.contactPhotoHint,
                style = ScanPangType.caption12,
                color = ScanPangColors.OnSurfacePlaceholder,
            )
        }
        item {
            val canSubmit = !submitting &&
                selectedCategory != s.contactCategoryDefault &&
                titleValue.text.isNotBlank() &&
                bodyValue.text.isNotBlank()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(ScanPangShapes.radius16)
                    .background(if (canSubmit) ScanPangColors.Primary else ScanPangColors.DisabledSurface)
                    .clickable(enabled = canSubmit) {
                        // 백엔드 POST /user/inquiry → inquiries 테이블 INSERT.
                        // user_id 는 Supabase Auth uid. 비로그인 케이스는 차단(보통
                        // ProfileScreen 까지 들어오면 로그인 상태지만 방어적으로).
                        val uid = AuthRepository.currentUserId() ?: run {
                            submitError = s.contactNotLoggedIn
                            return@clickable
                        }
                        submitting = true
                        submitError = null
                        scope.launch {
                            try {
                                RetrofitClient.api.submitInquiry(
                                    InquirySubmitRequest(
                                        user_id  = uid,
                                        category = selectedCategory,
                                        title    = titleValue.text.trim(),
                                        content  = bodyValue.text.trim(),
                                    ),
                                )
                                submitted = true
                            } catch (e: Exception) {
                                android.util.Log.e("ContactScreen", "submitInquiry failed", e)
                                submitError = s.contactSubmitError
                            } finally {
                                submitting = false
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (submitting) s.contactSubmitting else s.contactSubmit,
                    style = ScanPangType.body15Medium,
                    color = Color.White,
                )
            }
            submitError?.let { msg ->
                Text(
                    text = msg,
                    style = ScanPangType.caption12,
                    color = ScanPangColors.Error,
                    modifier = Modifier.padding(top = ScanPangSpacing.xs),
                )
            }
        }
    }
}

@Composable
private fun PhotoThumbnail(uri: Uri, onRemove: () -> Unit) {
    val context = LocalContext.current
    val s = LocalStrings.current
    Box(modifier = Modifier.size(80.dp)) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(uri).crossfade(true).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(ScanPangShapes.radius12)
                .border(ScanPangDimens.borderHairline, ScanPangColors.OutlineSubtle, ScanPangShapes.radius12),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(ScanPangColors.OnSurfaceStrong.copy(alpha = 0.7f))
                .clickable { onRemove() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = s.contactPhotoRemove,
                tint = Color.White,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

@Composable
private fun PhotoAddButton(onClick: () -> Unit) {
    val s = LocalStrings.current
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(ScanPangShapes.radius12)
            .border(ScanPangDimens.borderHairline, ScanPangColors.OutlineSubtle, ScanPangShapes.radius12)
            .background(ScanPangColors.Surface)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Image,
                contentDescription = s.contactPhotoAdd,
                tint = ScanPangColors.OnSurfacePlaceholder,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = s.contactPhotoAdd,
                style = ScanPangType.caption12,
                color = ScanPangColors.OnSurfacePlaceholder,
            )
        }
    }
}

@Composable
private fun InquiryFieldLabel(text: String) {
    Text(
        text = text,
        style = ScanPangType.sectionLabelSemiBold13,
        color = ScanPangColors.OnSurfaceMuted,
    )
}

@Composable
private fun InquiryTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    singleLine: Boolean,
    minHeight: androidx.compose.ui.unit.Dp = 52.dp,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        textStyle = ScanPangType.body15Medium.copy(color = ScanPangColors.OnSurfaceStrong),
        cursorBrush = SolidColor(ScanPangColors.Primary),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(ScanPangShapes.radius12)
            .border(ScanPangDimens.borderHairline, ScanPangColors.OutlineSubtle, ScanPangShapes.radius12)
            .background(ScanPangColors.Surface)
            .padding(horizontal = ScanPangSpacing.lg, vertical = 14.dp),
        decorationBox = { inner ->
            if (value.text.isEmpty()) {
                Text(
                    text = placeholder,
                    style = ScanPangType.body15Medium,
                    color = ScanPangColors.OnSurfacePlaceholder,
                )
            }
            inner()
        },
    )
}

@Composable
private fun InquirySuccessBanner(onReset: () -> Unit) {
    val s = LocalStrings.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = ScanPangDimens.screenHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = ScanPangColors.Success,
            modifier = Modifier.size(64.dp),
        )
        Spacer(modifier = Modifier.height(ScanPangSpacing.lg))
        Text(
            text = s.contactSuccessTitle,
            style = ScanPangType.sectionTitle,
            color = ScanPangColors.OnSurfaceStrong,
        )
        Spacer(modifier = Modifier.height(ScanPangSpacing.sm))
        Text(
            text = s.contactSuccessDesc,
            style = ScanPangType.meta13,
            color = ScanPangColors.OnSurfaceMuted,
        )
        Spacer(modifier = Modifier.height(ScanPangSpacing.xl))
        Box(
            modifier = Modifier
                .clip(ScanPangShapes.radius12)
                .border(ScanPangDimens.borderHairline, ScanPangColors.OutlineSubtle, ScanPangShapes.radius12)
                .clickable { onReset() }
                .padding(horizontal = ScanPangSpacing.xl, vertical = ScanPangSpacing.md),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = s.contactNewInquiry,
                style = ScanPangType.body15Medium,
                color = ScanPangColors.OnSurfaceStrong,
            )
        }
    }
}
