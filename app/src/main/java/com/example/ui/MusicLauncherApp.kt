package com.example.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.AppInfo
import com.example.data.toImageBitmap
import kotlinx.coroutines.launch
import androidx.compose.animation.*
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import android.graphics.BitmapFactory
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.drive.DriveRoot
import com.example.ui.components.EmptyLibraryState
import com.example.ui.components.RedAlertColor
import com.example.ui.drive.GoogleDriveAuthDialog
import com.example.ui.screens.FolderTreeScreen
import com.example.ui.screens.MusicHubScreen
import com.example.ui.screens.buildCombinedStorageTree
import com.example.ui.theme.AntiqueGold
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs

/**
 * 漆黒（AMOLEDブラック）のカラー定義
 */
val DarkPureBackground = Color(0xFF000000)
val DarkSurface = Color(0xFF121212)
val DarkSurfaceVariant = Color(0xFF1E1E1E)
val AccentGreen = Color(0xFF1DB954) // DAPらしい鮮やかなアクセントカラー

@Composable
fun MusicLauncherApp(
    viewModel: MusicLauncherViewModel,
    modifier: Modifier = Modifier
) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val isHapticsEnabled by viewModel.isHapticsEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showDriveAuthDialog by remember { mutableStateOf(false) }

    if (showDriveAuthDialog) {
        val currentRoot by viewModel.driveRoot.collectAsStateWithLifecycle()
        GoogleDriveAuthDialog(
            currentRoot = currentRoot,
            onDismiss = { showDriveAuthDialog = false },
            onTokenObtained = { token, root ->
                viewModel.connectDriveWithToken(token, root)
                showDriveAuthDialog = false
            }
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(DarkPureBackground)
            .testTag("music_launcher_root"),
        containerColor = DarkPureBackground,
        contentColor = Color.White
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    when {
                        // Play -> Storage (Storage slides in from right to left)
                        initialState == MusicTab.PLAY && targetState == MusicTab.STORAGE -> {
                            slideInHorizontally(animationSpec = tween(280)) { it } + fadeIn(tween(200)) togetherWith
                                    slideOutHorizontally(animationSpec = tween(280)) { -it } + fadeOut(tween(200))
                        }
                        // Storage -> Play (Play slides in from left to right)
                        initialState == MusicTab.STORAGE && targetState == MusicTab.PLAY -> {
                            slideInHorizontally(animationSpec = tween(280)) { -it } + fadeIn(tween(200)) togetherWith
                                    slideOutHorizontally(animationSpec = tween(280)) { it } + fadeOut(tween(200))
                        }
                        // Play -> Hub (Hub slides in from left to right)
                        initialState == MusicTab.PLAY && targetState == MusicTab.HUB -> {
                            slideInHorizontally(animationSpec = tween(280)) { -it } + fadeIn(tween(200)) togetherWith
                                    slideOutHorizontally(animationSpec = tween(280)) { it } + fadeOut(tween(200))
                        }
                        // Hub -> Play (Play slides in from right to left)
                        initialState == MusicTab.HUB && targetState == MusicTab.PLAY -> {
                            slideInHorizontally(animationSpec = tween(280)) { it } + fadeIn(tween(200)) togetherWith
                                    slideOutHorizontally(animationSpec = tween(280)) { -it } + fadeOut(tween(200))
                        }
                        // Play -> Settings (Settings slides in from left)
                        initialState == MusicTab.PLAY && targetState == MusicTab.SETTINGS -> {
                            slideInHorizontally(animationSpec = tween(280)) { -it } + fadeIn(tween(200)) togetherWith
                                    slideOutHorizontally(animationSpec = tween(280)) { it } + fadeOut(tween(200))
                        }
                        // Settings -> Play (Play slides in from right)
                        initialState == MusicTab.SETTINGS && targetState == MusicTab.PLAY -> {
                            slideInHorizontally(animationSpec = tween(280)) { it } + fadeIn(tween(200)) togetherWith
                                    slideOutHorizontally(animationSpec = tween(280)) { -it } + fadeOut(tween(200))
                        }
                        else -> {
                            fadeIn(tween(250)) togetherWith fadeOut(tween(200))
                        }
                    }
                },
                label = "TabContent"
            ) { targetTab ->
                when (targetTab) {
                    MusicTab.PLAY -> MainHomeLauncherScreen(
                        viewModel = viewModel,
                        playbackState = playbackState,
                        isHapticsEnabled = isHapticsEnabled,
                        onOpenSettings = {
                            if (isHapticsEnabled) triggerHapticFeedback(context, 1)
                            viewModel.selectTab(MusicTab.SETTINGS)
                        },
                        onOpenStorage = {
                            if (isHapticsEnabled) triggerHapticFeedback(context, 1)
                            viewModel.selectTab(MusicTab.STORAGE)
                        },
                        onRequestConnectDrive = { showDriveAuthDialog = true }
                    )
                    MusicTab.HUB -> MusicHubScreen(
                        viewModel = viewModel,
                        playbackState = playbackState,
                        isHapticsEnabled = isHapticsEnabled,
                        onBack = {
                            if (isHapticsEnabled) triggerHapticFeedback(context, 1)
                            viewModel.switchToPlayScreen()
                        },
                        onOpenStorage = {
                            if (isHapticsEnabled) triggerHapticFeedback(context, 1)
                            viewModel.selectTab(MusicTab.STORAGE)
                        }
                    )
                    MusicTab.STORAGE -> StorageScreen(
                        viewModel = viewModel,
                        isHapticsEnabled = isHapticsEnabled,
                        onBack = {
                            if (isHapticsEnabled) triggerHapticFeedback(context, 1)
                            viewModel.switchToPlayScreen()
                        },
                        onRequestConnectDrive = { showDriveAuthDialog = true }
                    )
                    MusicTab.SETTINGS -> SettingsScreen(
                        viewModel = viewModel,
                        onBack = {
                            if (isHapticsEnabled) triggerHapticFeedback(context, 1)
                            viewModel.switchToPlayScreen()
                        },
                        onRequestConnectDrive = { showDriveAuthDialog = true }
                    )
                }
            }
        }
    }
}

/**
 * 左右スワイプ対応 3ページ型ホームランチャー画面
 * 画面上部エリア: Settings | Now Playing / ページインジケーター | Storage (右上)
 * メイン画面エリア: ライブラリ画面 (Page 0) | Play画面 (デフォルト: Page 1) | その他アプリ 9個 (Page 2)
 * 画面下部エリア: 現在ある再生中トラックと再生関連ボタンのエリア
 */
@Composable
fun MainHomeLauncherScreen(
    viewModel: MusicLauncherViewModel,
    playbackState: PlaybackUiState,
    isHapticsEnabled: Boolean,
    onOpenSettings: () -> Unit,
    onOpenStorage: () -> Unit,
    onRequestConnectDrive: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 1) { 3 }
    val context = LocalContext.current
    val currentTrack = playbackState.currentTrack

    // ライブラリまたはApps画面にいるとき、端末の戻る操作で中央のPlay画面へ戻る
    if (pagerState.currentPage != 1) {
        BackHandler {
            coroutineScope.launch {
                pagerState.animateScrollToPage(1)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkPureBackground)
    ) {
        // ==========================================
        // 1. 画面上部エリア: Settings | Now Playing | Storage
        // ==========================================
        HomeTopHeaderArea(
            currentTrack = currentTrack,
            currentPage = pagerState.currentPage,
            onPageSelected = { page ->
                coroutineScope.launch {
                    if (isHapticsEnabled) triggerHapticFeedback(context, 1)
                    pagerState.animateScrollToPage(page)
                }
            },
            onOpenSettings = onOpenSettings,
            onOpenStorage = onOpenStorage
        )

        // ==========================================
        // 2. メイン画面エリア: ライブラリ画面 | Play画面 | その他アプリ (9個)
        // ==========================================
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("home_horizontal_pager")
        ) { page ->
            when (page) {
                0 -> {
                    // Page 0: ライブラリ画面（選曲ハブ・アルバム/アーティスト/全曲/Queue）
                    MusicHubScreen(
                        viewModel = viewModel,
                        playbackState = playbackState,
                        isHapticsEnabled = isHapticsEnabled,
                        onBack = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(1)
                            }
                        },
                        onOpenStorage = onOpenStorage
                    )
                }
                1 -> {
                    // Page 1: Play画面（デフォルト）
                    PlayCenterPageContent(
                        viewModel = viewModel,
                        playbackState = playbackState,
                        isHapticsEnabled = isHapticsEnabled,
                        onSwipeToLibrary = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(0)
                            }
                        },
                        onSwipeToApps = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(2)
                            }
                        }
                    )
                }
                2 -> {
                    // Page 2: その他アプリ（9個）
                    NineAppsGridPageContent(
                        viewModel = viewModel,
                        isHapticsEnabled = isHapticsEnabled
                    )
                }
            }
        }

        // ==========================================
        // 3. 画面下部エリア: 再生中トラックと再生関連ボタンのエリア
        // ==========================================
        HomeBottomPlayerArea(
            viewModel = viewModel,
            playbackState = playbackState,
            isHapticsEnabled = isHapticsEnabled
        )
    }
}

/**
 * 画面上部エリア: Settings | Now Playing / インジケーター | Storage (右上)
 */
@Composable
fun HomeTopHeaderArea(
    currentTrack: TrackInfo?,
    currentPage: Int,
    onPageSelected: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStorage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左：Settings歯車マーク
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .size(48.dp)
                .testTag("top_settings_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = stringResource(R.string.tab_settings),
                tint = Color.LightGray,
                modifier = Modifier.size(24.dp)
            )
        }

        // 中央：Now Playing / ページインジケーター（Library | Play | Apps）
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = when (currentPage) {
                        0 -> stringResource(R.string.tab_library)
                        2 -> stringResource(R.string.tab_apps)
                        else -> stringResource(R.string.now_playing)
                    },
                    color = Color.LightGray,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (currentTrack != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = DarkSurfaceVariant,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        val formatText = if (currentTrack.isDriveSource) "DRIVE" else {
                            val ext = currentTrack.uri.substringAfterLast('.', "").uppercase()
                            if (ext.length in 2..5 && !ext.contains("/")) ext else "AUDIO"
                        }
                        Text(
                            text = formatText,
                            color = AccentGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 3ページを示すコンパクトなインジケーター
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(0, 1, 2).forEach { pageIdx ->
                    val isCurrent = currentPage == pageIdx
                    Box(
                        modifier = Modifier
                            .size(width = if (isCurrent) 16.dp else 6.dp, height = 5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (isCurrent) AccentGreen else Color(0xFF444444))
                            .clickable { onPageSelected(pageIdx) }
                    )
                }
            }
        }

        // 右：Storage（ストレージ画面）
        IconButton(
            onClick = onOpenStorage,
            modifier = Modifier
                .size(48.dp)
                .testTag("top_storage_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.FolderOpen,
                contentDescription = stringResource(R.string.tab_storage),
                tint = Color.LightGray,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * Page 1: Play画面（アートワーク、楽曲情報、タップ操作）
 */
@Composable
fun PlayCenterPageContent(
    viewModel: MusicLauncherViewModel,
    playbackState: PlaybackUiState,
    isHapticsEnabled: Boolean,
    onSwipeToLibrary: () -> Unit,
    onSwipeToApps: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentTrack = playbackState.currentTrack

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        // 中央：アルバムアートワーク（タップで再生/一時停止、長押しやスワイプ対応）
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(DarkSurfaceVariant)
                .border(
                    1.dp,
                    if (playbackState.isPlaying) AccentGreen.copy(alpha = 0.5f) else Color(0xFF2A2A2A),
                    RoundedCornerShape(24.dp)
                )
                .clickable {
                    if (isHapticsEnabled) triggerHapticFeedback(context, 1)
                    viewModel.togglePlayPause()
                }
                .testTag("album_art_gesture_box"),
            contentAlignment = Alignment.Center
        ) {
            val artworkBitmap = remember(playbackState.artworkBytes) {
                playbackState.artworkBytes?.let { bytes ->
                    try {
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    } catch (_: Exception) {
                        null
                    }
                }
            }

            if (artworkBitmap != null) {
                // トリミングなし（ContentScale.Fit）：元のアスペクト比を完全維持
                Image(
                    bitmap = artworkBitmap,
                    contentDescription = currentTrack?.album ?: "Album Artwork",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .clip(RoundedCornerShape(18.dp)),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.Center
                )
            } else if (!currentTrack?.albumArtUri.isNullOrEmpty()) {
                AsyncImage(
                    model = currentTrack.albumArtUri,
                    contentDescription = currentTrack.album ?: "Album Artwork",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .clip(RoundedCornerShape(18.dp)),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.Center
                )
            } else {
                // レコード風の同心円装飾（アートワーク非存在時のフォールバック）
                Box(
                    modifier = Modifier
                        .fillMaxSize(0.88f)
                        .clip(CircleShape)
                        .background(Color(0xFF141414))
                        .border(1.dp, Color(0xFF222222), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize(0.55f)
                            .clip(CircleShape)
                            .background(Color(0xFF1C1C1C))
                            .border(1.dp, Color(0xFF282828), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (currentTrack?.isDriveSource == true) Icons.Rounded.CloudQueue else Icons.Rounded.MusicNote,
                            contentDescription = "Music Art",
                            tint = if (playbackState.isPlaying) AccentGreen else Color(0xFF555555),
                            modifier = Modifier.size(54.dp)
                        )
                    }
                }
            }
        }

        // 楽曲メタデータ
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = currentTrack?.title ?: stringResource(R.string.no_track_playing),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (currentTrack != null) "${currentTrack.artist} • ${currentTrack.album}" else "ストレージやGoogle Driveから音楽を選択",
                color = Color.Gray,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (currentTrack != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color(0xFF1C1C1C),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, Color(0xFF2E2E2E))
                    ) {
                        Text(
                            text = if (currentTrack.isDriveSource) "Google Drive" else "Local Storage",
                            color = if (currentTrack.isDriveSource) AccentGreen else Color(0xFF90CAF9),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    if (currentTrack.durationMs > 0) {
                        Surface(
                            color = Color(0xFF1C1C1C),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFF2E2E2E))
                        ) {
                            Text(
                                text = formatDuration(currentTrack.durationMs),
                                color = Color.LightGray,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Page 2: その他アプリ（9個）グリッド画面
 */
@Composable
fun NineAppsGridPageContent(
    viewModel: MusicLauncherViewModel,
    isHapticsEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val nineApps by viewModel.nineApps.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    var isEditMode by remember { mutableStateOf(false) }
    var editingSlotIndex by remember { mutableStateOf<Int?>(null) }

    // 編集モード中の戻る操作で編集モードを終了
    if (isEditMode && editingSlotIndex == null) {
        BackHandler {
            isEditMode = false
        }
    }

    if (editingSlotIndex != null) {
        AppPickerDialog(
            installedApps = installedApps,
            slotIndex = editingSlotIndex!!,
            onDismiss = { editingSlotIndex = null },
            onSelectApp = { app ->
                if (isHapticsEnabled) triggerHapticFeedback(context, 1)
                viewModel.updateNineAppSlot(editingSlotIndex!!, app)
                editingSlotIndex = null
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isEditMode) "アプリの変更" else stringResource(R.string.nine_apps_title),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isEditMode) stringResource(R.string.nine_apps_edit_hint) else stringResource(R.string.nine_apps_normal_hint),
                    color = if (isEditMode) AccentGreen else Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = if (isEditMode) FontWeight.Medium else FontWeight.Normal
                )
            }

            if (isEditMode) {
                IconButton(
                    onClick = {
                        if (isHapticsEnabled) triggerHapticFeedback(context, 1)
                        isEditMode = false
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(AccentGreen)
                        .testTag("nine_apps_done_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = stringResource(R.string.done),
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3x3 グリッド（9個のアプリ）
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("nine_apps_grid"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            items(9) { index ->
                val app = nineApps.getOrNull(index)
                AppSlotItem(
                    index = index,
                    app = app,
                    isEditMode = isEditMode,
                    onLaunch = {
                        if (isHapticsEnabled) triggerHapticFeedback(context, 1)
                        if (isEditMode) {
                            // 編集モード時：タップされたアプリスロットの選択ダイアログを表示
                            editingSlotIndex = index
                        } else {
                            if (app != null) {
                                val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                                if (launchIntent != null) {
                                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(launchIntent)
                                }
                            } else {
                                isEditMode = true
                                editingSlotIndex = index
                            }
                        }
                    },
                    onLongPress = {
                        if (isHapticsEnabled) triggerHapticFeedback(context, 2)
                        isEditMode = true
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppSlotItem(
    index: Int,
    app: AppInfo?,
    isEditMode: Boolean = false,
    onLaunch: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appIconBitmap = remember(app?.packageName) {
        if (app != null) {
            try {
                val iconDrawable = context.packageManager.getApplicationIcon(app.packageName)
                iconDrawable.toImageBitmap()
            } catch (e: Exception) {
                null
            }
        } else null
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurfaceVariant)
            .border(
                width = if (isEditMode) 1.5.dp else 1.dp,
                color = if (isEditMode) AccentGreen.copy(alpha = 0.6f) else Color(0xFF222222),
                shape = RoundedCornerShape(16.dp)
            )
            .combinedClickable(
                onClick = onLaunch,
                onLongClick = onLongPress
            )
            .padding(vertical = 12.dp, horizontal = 6.dp)
            .testTag("app_slot_$index"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (app != null && appIconBitmap != null) {
                Image(
                    bitmap = appIconBitmap,
                    contentDescription = app.label,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else if (app != null) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF282828)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Apps,
                        contentDescription = app.label,
                        tint = AccentGreen,
                        modifier = Modifier.size(28.dp)
                    )
                }
            } else {
                // 空きスロット
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF383838), RoundedCornerShape(12.dp))
                        .background(Color(0xFF181818)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.empty_slot),
                        tint = Color.Gray,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = app?.label ?: stringResource(R.string.empty_slot),
                color = if (app != null) Color.White else Color.Gray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 9スロットに配置するアプリの選択ダイアログ
 */
@Composable
fun AppPickerDialog(
    installedApps: List<AppInfo>,
    slotIndex: Int,
    onDismiss: () -> Unit,
    onSelectApp: (AppInfo) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }

    val filteredApps = remember(installedApps, searchQuery) {
        if (searchQuery.isBlank()) {
            installedApps
        } else {
            val q = searchQuery.trim().lowercase(Locale.ROOT)
            installedApps.filter {
                it.label.lowercase(Locale.ROOT).contains(q) ||
                it.alternateLabels.any { alt -> alt.lowercase(Locale.ROOT).contains(q) } ||
                it.packageName.lowercase(Locale.ROOT).contains(q)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 24.dp),
            shape = RoundedCornerShape(20.dp),
            color = DarkSurface,
            border = BorderStroke(1.dp, Color(0xFF2A2A2A))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${stringResource(R.string.select_app_title)} (Slot ${slotIndex + 1})",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.search_apps), color = Color.Gray, fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = AccentGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Rounded.Clear,
                                    contentDescription = "Clear",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = AccentGreen,
                        unfocusedBorderColor = Color(0xFF333333),
                        focusedContainerColor = DarkSurfaceVariant,
                        unfocusedContainerColor = DarkSurfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (filteredApps.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.no_apps_found),
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredApps, key = { it.packageName + "/" + it.className }) { app ->
                            val appIconBitmap = remember(app.packageName) {
                                try {
                                    val iconDrawable = context.packageManager.getApplicationIcon(app.packageName)
                                    iconDrawable.toImageBitmap()
                                } catch (e: Exception) {
                                    null
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onSelectApp(app) }
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (appIconBitmap != null) {
                                    Image(
                                        bitmap = appIconBitmap,
                                        contentDescription = app.label,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(DarkSurfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Apps,
                                            contentDescription = null,
                                            tint = AccentGreen,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.label,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = app.packageName,
                                        color = Color.Gray,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 画面下部エリア: 現在ある再生中トラックと再生関連ボタンのエリア
 */
@Composable
fun HomeBottomPlayerArea(
    viewModel: MusicLauncherViewModel,
    playbackState: PlaybackUiState,
    isHapticsEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val durationMs = playbackState.durationMs
    val positionMs = playbackState.currentPositionMs
    val progressFraction = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    var userDraggingProgress by remember { mutableStateOf<Float?>(null) }
    val displayProgress = userDraggingProgress ?: progressFraction

    Surface(
        color = DarkSurface,
        border = BorderStroke(1.dp, Color(0xFF1E1E1E)),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            // シークバー
            Slider(
                value = displayProgress,
                onValueChange = { userDraggingProgress = it },
                onValueChangeFinished = {
                    userDraggingProgress?.let { viewModel.seekToFraction(it) }
                    userDraggingProgress = null
                },
                colors = SliderDefaults.colors(
                    thumbColor = AccentGreen,
                    activeTrackColor = AccentGreen,
                    inactiveTrackColor = Color(0xFF282828)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = formatDuration(positionMs), color = Color.Gray, fontSize = 11.sp)
                Text(text = formatDuration(durationMs), color = Color.Gray, fontSize = 11.sp)
            }

            Spacer(modifier = Modifier.height(4.dp))

            // コントローラー群
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.toggleShuffle() },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        Icons.Rounded.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (playbackState.isShuffle) AccentGreen else Color.Gray,
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(
                    onClick = {
                        if (isHapticsEnabled) triggerHapticFeedback(context, 2)
                        viewModel.skipPrevious()
                    },
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        Icons.Rounded.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Surface(
                    onClick = {
                        if (isHapticsEnabled) triggerHapticFeedback(context, 1)
                        viewModel.togglePlayPause()
                    },
                    modifier = Modifier
                        .size(64.dp)
                        .testTag("main_play_pause_button"),
                    shape = CircleShape,
                    color = AccentGreen
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.Black,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                IconButton(
                    onClick = {
                        if (isHapticsEnabled) triggerHapticFeedback(context, 2)
                        viewModel.skipNext()
                    },
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        Icons.Rounded.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.toggleRepeat() },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = when (playbackState.repeatMode) {
                            2 -> Icons.Rounded.RepeatOne
                            else -> Icons.Rounded.Repeat
                        },
                        contentDescription = "Repeat",
                        tint = if (playbackState.repeatMode > 0) AccentGreen else Color.Gray,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

/**
 * 互換性のためのPlayScreenエイリアス
 */
@Composable
fun PlayScreen(
    viewModel: MusicLauncherViewModel,
    playbackState: PlaybackUiState,
    isHapticsEnabled: Boolean,
    onOpenHub: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    MainHomeLauncherScreen(
        viewModel = viewModel,
        playbackState = playbackState,
        isHapticsEnabled = isHapticsEnabled,
        onOpenSettings = onOpenSettings,
        onOpenStorage = onOpenStorage,
        onRequestConnectDrive = { },
        modifier = modifier
    )
}

/**
 * 2. Storage（ストレージ）画面
 */
@Composable
fun StorageScreen(
    viewModel: MusicLauncherViewModel,
    isHapticsEnabled: Boolean,
    onBack: (() -> Unit)? = null,
    onRequestConnectDrive: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val allTracks by viewModel.tracks.collectAsStateWithLifecycle()
    val driveTracks by viewModel.driveTracks.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val scanCount by viewModel.scanCount.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()

    val isDriveConnected by viewModel.isDriveConnected.collectAsStateWithLifecycle()
    val isDriveSyncing by viewModel.isDriveSyncing.collectAsStateWithLifecycle()
    val driveSyncStatus by viewModel.driveSyncStatus.collectAsStateWithLifecycle()
    val driveRoot by viewModel.driveRoot.collectAsStateWithLifecycle()
    val downloadingTrackId by viewModel.downloadingTrackId.collectAsStateWithLifecycle()

    val driveRawTracks by viewModel.driveRawTracks.collectAsStateWithLifecycle()
    val desiredCheckedIds by viewModel.desiredCheckedIds.collectAsStateWithLifecycle()
    val busyIds by viewModel.busyIds.collectAsStateWithLifecycle()
    val expandedFolderPaths by viewModel.expandedFolderPaths.collectAsStateWithLifecycle()
    val hasIncompleteDownloads by viewModel.hasIncompleteDownloads.collectAsStateWithLifecycle()

    var filterType by remember { mutableIntStateOf(0) } // 0: すべて, 1: ローカル, 2: Google Drive

    val combinedTreeNodes = remember(allTracks, folders, driveRawTracks) {
        buildCombinedStorageTree(
            localTracks = allTracks.filter { !it.isDriveSource },
            folders = folders,
            driveTracks = driveRawTracks
        )
    }
    val combinedTreeTrackCount = remember(combinedTreeNodes) {
        combinedTreeNodes.sumOf { it.getAllRecursiveTracks().size }
    }

    val filteredTracks = remember(allTracks, filterType) {
        when (filterType) {
            1 -> allTracks.filter { !it.isDriveSource }
            2 -> allTracks.filter { it.isDriveSource }
            else -> allTracks
        }
    }

    val storageBg = DarkPureBackground
    val textColorPrimary = Color.White
    val buttonBg = DarkSurfaceVariant

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(storageBg)
            .padding(horizontal = 16.dp, vertical = if (onBack != null) 16.dp else 4.dp)
    ) {
        // トップバー (単独表示時のみ戻るボタンを表示)
        if (onBack != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("storage_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back to Play",
                        tint = textColorPrimary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.tab_storage),
                    color = textColorPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // コンパクトなスクロール連動ヘッダー（Google Drive同期・フィルタチップ・ステータス）
        val storageHeader: @Composable () -> Unit = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                // Google Drive同期/接続コンパクトボタン
                Surface(
                    onClick = {
                        if (isDriveConnected) {
                            if (!isDriveSyncing) viewModel.syncGoogleDrive()
                        } else {
                            onRequestConnectDrive()
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = buttonBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (isDriveSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color(0xFF4285F4),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.CloudSync,
                                contentDescription = null,
                                tint = Color(0xFF4285F4),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (!isDriveConnected) "Drive接続"
                            else if (isDriveSyncing) "同期中..."
                            else if (driveTracks.isNotEmpty()) "Drive (${driveTracks.size})"
                                else "Drive同期",
                                color = textColorPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                // スキャン中表示 (スキャン時のみ表示)
                if (isScanning) {
                    Surface(
                        color = buttonBg,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = AccentGreen,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.scanning_local, scanCount),
                                color = textColorPrimary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // フィルタチップ（すべて / 端末 / Google Drive）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = filterType == 0,
                        onClick = { filterType = 0 },
                        label = { Text(stringResource(R.string.filter_cached_count, combinedTreeTrackCount), fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentGreen,
                            selectedLabelColor = Color.Black,
                            containerColor = DarkSurfaceVariant,
                            labelColor = Color.LightGray
                        )
                    )
                    FilterChip(
                        selected = filterType == 1,
                        onClick = { filterType = 1 },
                        label = { Text(stringResource(R.string.filter_local_count, allTracks.count { !it.isDriveSource }), fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentGreen,
                            selectedLabelColor = Color.Black,
                            containerColor = DarkSurfaceVariant,
                            labelColor = Color.LightGray
                        )
                    )
                    FilterChip(
                        selected = filterType == 2,
                        onClick = { filterType = 2 },
                        label = { Text(stringResource(R.string.filter_drive_count, driveTracks.size), fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF4285F4),
                            selectedLabelColor = Color.White,
                            containerColor = DarkSurfaceVariant,
                            labelColor = Color.LightGray
                        )
                    )
                }

                // 未完了ダウンロード制御バー (Driveタブ用)
                if (filterType == 2 && isDriveConnected && (hasIncompleteDownloads || busyIds.isNotEmpty())) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (busyIds.isNotEmpty()) stringResource(R.string.caching_in_progress_count, busyIds.size) else stringResource(R.string.incomplete_downloads_exist),
                            color = if (busyIds.isNotEmpty()) RedAlertColor else AntiqueGold,
                            fontSize = 11.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (busyIds.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = { viewModel.stopActiveCacheOperations() },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red.copy(alpha = 0.8f)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(stringResource(R.string.pause), fontSize = 11.sp)
                                }
                            }
                            if (hasIncompleteDownloads && busyIds.isEmpty()) {
                                OutlinedButton(
                                    onClick = { viewModel.resumeIncompleteDownloads() },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AntiqueGold),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(stringResource(R.string.resume), fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 楽曲リスト本体 / 統合フォルダツリー表示 (すべて) / Google Driveツリー表示
        if (filterType == 0) {
            // "すべて" タブ: フォルダツリー表示 (端末 → Google Drive)
            // キャッシュ済のフォルダと過去3日でキャッシュしたことのあるフォルダのみ表示
            FolderTreeScreen(
                tracks = emptyList(),
                customRootNodes = combinedTreeNodes,
                desiredCheckedIds = desiredCheckedIds,
                busyIds = busyIds,
                expandedPaths = expandedFolderPaths,
                onToggleExpand = { viewModel.toggleFolderExpanded(it) },
                onInitExpandedPaths = { viewModel.initExpandedPathsIfNeeded(it) },
                currentPlayingTrack = driveRawTracks.find { it.id == playbackState.currentTrack?.id }
                    ?: combinedTreeNodes.flatMap { it.getAllRecursiveTracks() }.find { it.id == playbackState.currentTrack?.id },
                isPlaying = playbackState.isPlaying,
                onTrackClick = { track, list ->
                    if (isHapticsEnabled) triggerHapticFeedback(context, 1)
                    viewModel.playTrack(track.toTrackInfo(), list.map { it.toTrackInfo() })
                },
                onPlayFolderRecursive = { tracks, startingTrack ->
                    if (isHapticsEnabled) triggerHapticFeedback(context, 2)
                    viewModel.playFolderRecursive(tracks, startingTrack)
                },
                onTogglePlayPause = { viewModel.togglePlayPause() },
                onToggleTrack = { track ->
                    if (isHapticsEnabled) triggerHapticFeedback(context, 1)
                    viewModel.toggleTrackCache(track)
                },
                onToggleFolder = { folderPath, currentlyChecked ->
                    if (isHapticsEnabled) triggerHapticFeedback(context, 2)
                    viewModel.toggleFolderCache(folderPath, currentlyChecked)
                },
                onOpenSync = { viewModel.syncGoogleDrive(forceFull = true) },
                emptyMessage = stringResource(R.string.empty_cached_folders_title),
                emptyDescription = stringResource(R.string.empty_cached_folders_desc),
                headerContent = storageHeader,
                modifier = Modifier.weight(1f)
            )
        } else if (filterType == 2) {
            if (!isDriveConnected) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    storageHeader()
                    EmptyLibraryState(
                        title = stringResource(R.string.drive_not_connected_title),
                        description = stringResource(R.string.drive_not_connected_desc),
                        onSyncClick = onRequestConnectDrive,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            } else {
                FolderTreeScreen(
                    tracks = driveRawTracks,
                    desiredCheckedIds = desiredCheckedIds,
                    busyIds = busyIds,
                    expandedPaths = expandedFolderPaths,
                    onToggleExpand = { viewModel.toggleFolderExpanded(it) },
                    onInitExpandedPaths = { viewModel.initExpandedPathsIfNeeded(it) },
                    currentPlayingTrack = driveRawTracks.find { it.id == playbackState.currentTrack?.id },
                    isPlaying = playbackState.isPlaying,
                    onTrackClick = { track, list ->
                        if (isHapticsEnabled) triggerHapticFeedback(context, 1)
                        viewModel.playTrack(track.toTrackInfo(), list.map { it.toTrackInfo() })
                    },
                    onPlayFolderRecursive = { tracks, startingTrack ->
                        if (isHapticsEnabled) triggerHapticFeedback(context, 2)
                        viewModel.playFolderRecursive(tracks, startingTrack)
                    },
                    onTogglePlayPause = { viewModel.togglePlayPause() },
                    onToggleTrack = { track ->
                        if (isHapticsEnabled) triggerHapticFeedback(context, 1)
                        viewModel.toggleTrackCache(track)
                    },
                    onToggleFolder = { folderPath, currentlyChecked ->
                        if (isHapticsEnabled) triggerHapticFeedback(context, 2)
                        viewModel.toggleFolderCache(folderPath, currentlyChecked)
                    },
                    onOpenSync = { viewModel.syncGoogleDrive(forceFull = true) },
                    headerContent = storageHeader,
                    modifier = Modifier.weight(1f)
                )
            }
        } else if (filteredTracks.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                storageHeader()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isScanning || isDriveSyncing) stringResource(R.string.syncing_or_loading)
                        else stringResource(R.string.no_tracks_add_folder_hint),
                        color = Color.Gray,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item(key = "storage_list_header") {
                    storageHeader()
                }
                items(filteredTracks, key = { it.id }) { track ->
                    val isCurrent = playbackState.currentTrack?.id == track.id
                    val isDownloadingThis = downloadingTrackId == track.id

                    Surface(
                        color = if (isCurrent) DarkSurfaceVariant else DarkPureBackground,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isHapticsEnabled) triggerHapticFeedback(context, 1)
                                viewModel.playTrack(track)
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isCurrent && playbackState.isPlaying) Icons.Rounded.Equalizer
                                else if (track.isDriveSource) Icons.Rounded.Cloud
                                else Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = if (isCurrent) AccentGreen else if (track.isDriveSource) Color(0xFF4285F4) else Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    color = if (isCurrent) AccentGreen else Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${track.artist} • ${track.album}",
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (isDownloadingThis) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color(0xFF4285F4),
                                    strokeWidth = 2.dp
                                )
                            } else if (track.isDriveSource) {
                                if (track.isDownloaded) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = "Cached",
                                        tint = AccentGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.FileDownload,
                                        contentDescription = "Download",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else if (track.durationMs > 0L) {
                                Text(
                                    text = formatDuration(track.durationMs),
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }
        }
    }
}

/**
 * 3. Settings（設定）画面
 */
@Composable
fun SettingsScreen(
    viewModel: MusicLauncherViewModel,
    onBack: () -> Unit,
    onRequestConnectDrive: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isHapticsEnabled by viewModel.isHapticsEnabled.collectAsStateWithLifecycle()
    val isDefaultHome = remember { viewModel.isDefaultLauncher(context) }
    val isBatteryOptimizationIgnored by viewModel.isBatteryOptimizationIgnored.collectAsStateWithLifecycle()
    val sleepTimerRemainingSeconds by viewModel.sleepTimerRemainingSeconds.collectAsStateWithLifecycle()
    val sleepTimerMode by viewModel.sleepTimerMode.collectAsStateWithLifecycle()
    val screenDimFactor by viewModel.screenDimFactor.collectAsStateWithLifecycle()
    val isDriveConnected by viewModel.isDriveConnected.collectAsStateWithLifecycle()
    val driveRoot by viewModel.driveRoot.collectAsStateWithLifecycle()
    val connectionName by viewModel.connectionTypeName.collectAsStateWithLifecycle()
    val musicVolume by viewModel.musicVolume.collectAsStateWithLifecycle()
    val pauseOnBluetoothDisconnect by viewModel.pauseOnBluetoothDisconnect.collectAsStateWithLifecycle()
    val isDriveSyncing by viewModel.isDriveSyncing.collectAsStateWithLifecycle()
    val driveSyncStatus by viewModel.driveSyncStatus.collectAsStateWithLifecycle()
    val isWifiOnly by viewModel.isWifiOnly.collectAsStateWithLifecycle()
    val audioCacheSize by viewModel.audioCacheSizeFormatted.collectAsStateWithLifecycle()
    val isMonoAudioEnabled by viewModel.isMonoAudioEnabled.collectAsStateWithLifecycle()
    val disabledAudioExtensions by viewModel.disabledAudioExtensions.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // トップバー
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("settings_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back to Play",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.settings_title),
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // デフォルトホーム ＆ Doze対策 (完了項目はコンパクト表示)
        if (isDefaultHome && isBatteryOptimizationIgnored) {
            // 両方完了(✓)時：横並びのコンパクト表示
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // デフォルトホーム（完了）
                Surface(
                    onClick = { viewModel.openDefaultHomeSettings(context) },
                    shape = RoundedCornerShape(12.dp),
                    color = DarkSurfaceVariant,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Home,
                            contentDescription = null,
                            tint = AccentGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.settings_set_default_launcher),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = "✓ OK",
                                color = AccentGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // スリープ中の音途切れ防止（完了）
                Surface(
                    onClick = { viewModel.requestIgnoreBatteryOptimization(context) },
                    shape = RoundedCornerShape(12.dp),
                    color = DarkSurfaceVariant,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BatteryChargingFull,
                            contentDescription = null,
                            tint = AccentGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.doze_ok),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = "✓ OK",
                                color = AccentGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        } else {
            // 未設定の項目がある場合は個別にカード表示
            if (isDefaultHome) {
                Surface(
                    onClick = { viewModel.openDefaultHomeSettings(context) },
                    shape = RoundedCornerShape(12.dp),
                    color = DarkSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Home,
                            contentDescription = null,
                            tint = AccentGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.default_home_ok),
                            color = AccentGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Home,
                                contentDescription = null,
                                tint = AccentGreen,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_set_default_launcher),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = stringResource(R.string.settings_set_default_launcher_desc),
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.openDefaultHomeSettings(context) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = Color.Black)
                        ) {
                            Text(stringResource(R.string.settings_set_default_launcher))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            if (isBatteryOptimizationIgnored) {
                Surface(
                    onClick = { viewModel.requestIgnoreBatteryOptimization(context) },
                    shape = RoundedCornerShape(12.dp),
                    color = DarkSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BatteryChargingFull,
                            contentDescription = null,
                            tint = AccentGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.doze_ok),
                            color = AccentGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.BatteryChargingFull,
                                contentDescription = null,
                                tint = AntiqueGold,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.doze_title),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = stringResource(R.string.doze_status_not_ignored),
                                    color = RedAlertColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = stringResource(R.string.doze_desc),
                            color = Color.Gray,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { viewModel.requestIgnoreBatteryOptimization(context) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = Color.Black)
                        ) {
                            Text(stringResource(R.string.doze_button_request))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // おやすみタイマー（徐々に画面減光＆省電力完全停止）
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bedtime,
                        contentDescription = null,
                        tint = AccentGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.sleep_timer_title),
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val remainingText = if (sleepTimerRemainingSeconds != null && sleepTimerRemainingSeconds!! > 0) {
                            val min = sleepTimerRemainingSeconds!! / 60
                            val sec = sleepTimerRemainingSeconds!! % 60
                            val dimPct = (screenDimFactor * 100).toInt()
                            stringResource(R.string.sleep_timer_active_format, min, sec, dimPct)
                        } else {
                            stringResource(R.string.sleep_timer_off)
                        }
                        Text(
                            text = remainingText,
                            color = if (sleepTimerMode != null) AccentGreen else Color.Gray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.sleep_timer_desc),
                    color = Color.Gray,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // タイマー選択ボタン群
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val firstRow = listOf(
                        null to stringResource(R.string.sleep_timer_off),
                        15 to "15min",
                        30 to "30min"
                    )
                    firstRow.forEach { (mins, label) ->
                        val isSelected = sleepTimerMode == mins
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isHapticsEnabled) triggerHapticFeedback(context, 1)
                                viewModel.setSleepTimer(mins)
                            },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentGreen,
                                selectedLabelColor = Color.Black
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val secondRow = listOf(
                        45 to "45min",
                        60 to "60min",
                        -1 to stringResource(R.string.sleep_timer_end_of_track)
                    )
                    secondRow.forEach { (mins, label) ->
                        val isSelected = sleepTimerMode == mins
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isHapticsEnabled) triggerHapticFeedback(context, 1)
                                viewModel.setSleepTimer(mins)
                            },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentGreen,
                                selectedLabelColor = Color.Black
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Google Drive 連携設定
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudSync,
                        contentDescription = null,
                        tint = Color(0xFF4285F4),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.settings_cloud),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.cloud_account_status), color = Color.LightGray, fontSize = 13.sp)
                        Text(
                            text = if (isDriveConnected) stringResource(R.string.cloud_status_connected, driveRoot.displayNameJa) else stringResource(R.string.cloud_status_disconnected),
                            color = if (isDriveConnected) AccentGreen else Color.Gray,
                            fontSize = 12.sp
                        )
                        if (isDriveConnected && driveSyncStatus.isNotBlank()) {
                            Text(
                                text = driveSyncStatus,
                                color = Color(0xFF888888),
                                fontSize = 11.sp
                            )
                        }
                    }
                    if (isDriveConnected) {
                        OutlinedButton(
                            onClick = { viewModel.disconnectDrive() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red.copy(alpha = 0.8f))
                        ) {
                            Text(stringResource(R.string.cloud_disconnect), fontSize = 12.sp)
                        }
                    } else {
                        Button(
                            onClick = onRequestConnectDrive,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4))
                        ) {
                            Text(stringResource(R.string.cloud_connect), fontSize = 12.sp)
                        }
                    }
                }

                if (isDriveConnected) {
                    Spacer(modifier = Modifier.height(10.dp))

                    // 探索ルート選択
                    Text(stringResource(R.string.cloud_search_root), color = Color.LightGray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DriveRoot.entries.forEach { root ->
                            FilterChip(
                                selected = driveRoot == root,
                                onClick = { viewModel.setDriveRoot(root) },
                                label = { Text(root.displayNameJa, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF4285F4),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 同期アクションボタン
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { viewModel.syncIncrementalWithGoogleDrive() },
                            enabled = !isDriveSyncing,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.cloud_sync_diff), fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { viewModel.syncGoogleDrive(forceFull = true) },
                            enabled = !isDriveSyncing,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.cloud_sync_full), fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = Color(0xFF2E2E2E))
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.cloud_wifi_only), color = Color.LightGray, fontSize = 13.sp)
                            Text(stringResource(R.string.cloud_wifi_only_desc), color = Color.Gray, fontSize = 11.sp)
                            Text(stringResource(R.string.cloud_current_network, connectionName), color = AccentGreen, fontSize = 11.sp)
                        }
                        Switch(
                            checked = isWifiOnly,
                            onCheckedChange = { viewModel.setWifiOnly(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = AccentGreen)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = Color(0xFF2E2E2E))
                    Spacer(modifier = Modifier.height(10.dp))

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.cloud_cache_size), color = Color.LightGray, fontSize = 13.sp)
                        Text(stringResource(R.string.cloud_cache_current_usage, audioCacheSize), color = Color.Gray, fontSize = 11.sp)

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.cacheAllLibrary() },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentGreen),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.cloud_cache_all), fontSize = 11.sp, maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = { viewModel.clearAudioCache() },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red.copy(alpha = 0.8f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.cloud_cache_delete), fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 音声 & ハプティクス設定
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = null,
                        tint = AccentGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.settings_audio_controls_title),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 再生音質（モノラル / ステレオ）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_audio_output),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.settings_audio_output_desc),
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !isMonoAudioEnabled,
                        onClick = {
                            if (isHapticsEnabled) triggerHapticFeedback(context, 1)
                            viewModel.setMonoAudioEnabled(false)
                        },
                        label = { Text(stringResource(R.string.settings_stereo), fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentGreen,
                            selectedLabelColor = Color.Black
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = isMonoAudioEnabled,
                        onClick = {
                            if (isHapticsEnabled) triggerHapticFeedback(context, 1)
                            viewModel.setMonoAudioEnabled(true)
                        },
                        label = { Text(stringResource(R.string.settings_mono), fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentGreen,
                            selectedLabelColor = Color.Black
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                HorizontalDivider(color = Color(0xFF2E2E2E), modifier = Modifier.padding(vertical = 8.dp))

                // アプリ内音量スライダー
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.settings_volume), color = Color.LightGray, fontSize = 13.sp)
                    Text("${(musicVolume * 100).toInt()}%", color = AccentGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = musicVolume,
                    onValueChange = { viewModel.setMusicVolume(it) },
                    colors = SliderDefaults.colors(
                        thumbColor = AccentGreen,
                        activeTrackColor = AccentGreen,
                        inactiveTrackColor = Color(0xFF333333)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(color = Color(0xFF2E2E2E), modifier = Modifier.padding(vertical = 8.dp))

                // Bluetooth切断時の一時停止
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_pause_bt_title),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.settings_pause_bt_desc),
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = pauseOnBluetoothDisconnect,
                        onCheckedChange = { viewModel.setPauseOnBluetoothDisconnect(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = AccentGreen)
                    )
                }

                HorizontalDivider(color = Color(0xFF2E2E2E), modifier = Modifier.padding(vertical = 8.dp))

                // ハプティクス
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_haptics),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.settings_haptics_desc),
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = isHapticsEnabled,
                        onCheckedChange = { viewModel.setHapticsEnabled(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = AccentGreen)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 表示拡張子フィルター（録音・特定ファイル非表示設定）
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FilterList,
                        contentDescription = null,
                        tint = AccentGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.settings_ext_filter),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = stringResource(R.string.settings_ext_filter_desc),
                    color = Color.Gray,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                val availableExtensions = MusicLauncherViewModel.SUPPORTED_AUDIO_EXTENSIONS
                val rows = availableExtensions.chunked(4)
                rows.forEachIndexed { index, rowExts ->
                    if (index > 0) Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowExts.forEach { ext ->
                            val isEnabled = !disabledAudioExtensions.contains(ext.lowercase())
                            FilterChip(
                                selected = isEnabled,
                                onClick = {
                                    if (isHapticsEnabled) triggerHapticFeedback(context, 1)
                                    viewModel.toggleAudioExtension(ext)
                                },
                                label = { Text(".${ext.uppercase()}", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentGreen,
                                    selectedLabelColor = Color.Black,
                                    containerColor = DarkSurface,
                                    labelColor = Color.Gray
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // アプリアップデート（Google Play）
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SystemUpdate,
                        contentDescription = null,
                        tint = AccentGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.settings_play_updates_title),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = stringResource(R.string.settings_play_updates_desc),
                    color = Color.Gray,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { viewModel.openPlayStoreUpdates(context) },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = Color.Black),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_play_check_updates), fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * ミリ秒を "mm:ss" 形式にフォーマット
 */
fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "00:00"
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

/**
 * 触覚フィードバック（ハプティクス）ユーティリティ
 */
fun triggerHapticFeedback(context: Context, pattern: Int) {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (pattern == 1) {
                    vibrator.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    val timings = longArrayOf(0, 25, 60, 25)
                    val amplitudes = intArrayOf(0, 200, 0, 200)
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                }
            } else {
                @Suppress("DEPRECATION")
                if (pattern == 1) {
                    vibrator.vibrate(25)
                } else {
                    vibrator.vibrate(longArrayOf(0, 25, 60, 25), -1)
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
