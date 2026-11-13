package com.example.ui.screens

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import com.example.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextAlign
import com.example.ui.theme.AntiqueGold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.AccentGreen
import com.example.ui.DarkPureBackground
import com.example.ui.DarkSurface
import com.example.ui.DarkSurfaceVariant
import com.example.ui.MusicLauncherViewModel
import com.example.ui.PlaybackUiState
import com.example.ui.TrackInfo
import com.example.ui.triggerHapticFeedback
import com.example.util.Id3Helper
import com.example.util.MusicalFormClassifier

/**
 * 提案2: 【フルスクリーン・ミュージックハブ（アルバム・アーティスト別）】
 * 右上のハンバーガーメニューから遷移する、音楽再生選択に特化した専用ハブ画面。
 */
enum class HubCategory(@androidx.annotation.StringRes val labelResId: Int, val icon: ImageVector) {
    ALBUMS(R.string.hub_category_albums, Icons.Rounded.Album),
    FORMS(R.string.hub_category_forms, Icons.Rounded.MusicNote),
    COMPOSERS(R.string.hub_category_composers, Icons.Rounded.Person),
    GENRES(R.string.hub_category_genres, Icons.Rounded.Category)
}

@Composable
fun MusicHubScreen(
    viewModel: MusicLauncherViewModel,
    playbackState: PlaybackUiState,
    isHapticsEnabled: Boolean,
    onBack: () -> Unit,
    onOpenStorage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allTracks by viewModel.tracks.collectAsStateWithLifecycle()
    val recentTracks by viewModel.recentTracks.collectAsStateWithLifecycle()
    val driveTracks by viewModel.driveTracks.collectAsStateWithLifecycle()

    var selectedCategory by remember { mutableStateOf(HubCategory.ALBUMS) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }

    // ライブラリスクリーンにはローカルまたは同期済み（ダウンロード・キャッシュ済）のファイルのみ表示
    val localOrSyncedTracks = remember(allTracks) {
        allTracks.filter { !it.isDriveSource || it.isDownloaded }
    }

    // 検索フィルタリング（タイトル、アーティスト、アルバム、作曲家、ジャンル、形式）
    val filteredTracks = remember(localOrSyncedTracks, searchQuery) {
        if (searchQuery.isBlank()) {
            localOrSyncedTracks
        } else {
            val query = searchQuery.trim().lowercase()
            localOrSyncedTracks.filter { track ->
                val form = if (track.musicalForm.isNotBlank()) track.musicalForm else MusicalFormClassifier.classify(track)
                track.title.lowercase().contains(query) ||
                        track.artist.lowercase().contains(query) ||
                        track.album.lowercase().contains(query) ||
                        track.composer.lowercase().contains(query) ||
                        track.genre.lowercase().contains(query) ||
                        form.lowercase().contains(query)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkPureBackground)
            .testTag("music_hub_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (playbackState.currentTrack != null) 72.dp else 0.dp)
        ) {
            // 上部ヘッダー（戻るボタン、タイトル、検索トグル、ストレージ管理遷移ボタン）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = {
                            if (isHapticsEnabled) triggerHapticFeedback(context, 1)
                            onBack()
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("hub_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.done),
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Column {
                        Text(
                            text = stringResource(R.string.library_title),
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.tracks_available_count, filteredTracks.size),
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 検索バー表示トグル
                    IconButton(
                        onClick = {
                            if (isHapticsEnabled) triggerHapticFeedback(context, 1)
                            isSearchActive = !isSearchActive
                            if (!isSearchActive) searchQuery = ""
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("hub_search_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "検索切替",
                            tint = if (isSearchActive || searchQuery.isNotEmpty()) AccentGreen else Color.LightGray
                        )
                    }

                    // ストレージ（フォルダツリー管理）への直接遷移ボタン
                    IconButton(
                        onClick = {
                            if (isHapticsEnabled) triggerHapticFeedback(context, 1)
                            onOpenStorage()
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("hub_open_storage_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Folder,
                            contentDescription = "フォルダ・キャッシュ管理",
                            tint = Color.LightGray
                        )
                    }
                }
            }

            // 検索バー（アクティブ時）
            AnimatedVisibility(
                visible = isSearchActive,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.search_placeholder), color = Color.Gray, fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("hub_search_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentGreen,
                        unfocusedBorderColor = Color(0xFF333333),
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel), tint = Color.Gray)
                            }
                        }
                    }
                )
            }

            // Queue プレビューバー（タグ一覧の上に配置）
            val currentPlaylist = playbackState.currentPlaylist
            val currentIdx = playbackState.currentIndex
            val nextTrack = remember(currentPlaylist, currentIdx) {
                if (currentPlaylist.isNotEmpty() && currentIdx in currentPlaylist.indices) {
                    currentPlaylist.getOrNull(currentIdx + 1)
                } else null
            }
            val remainingQueueCount = remember(currentPlaylist, currentIdx) {
                if (currentPlaylist.isNotEmpty() && currentIdx >= 0) {
                    (currentPlaylist.size - (currentIdx + 1)).coerceAtLeast(0)
                } else 0
            }

            Surface(
                onClick = {
                    if (isHapticsEnabled) triggerHapticFeedback(context, 1)
                    showQueueSheet = true
                },
                shape = RoundedCornerShape(12.dp),
                color = DarkSurface,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (playbackState.isPlaying) AccentGreen.copy(alpha = 0.5f) else Color(0xFF2A2A2A)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("hub_queue_preview_bar")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = stringResource(R.string.now_playing),
                        tint = if (playbackState.isPlaying) AccentGreen else Color.LightGray,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (playbackState.currentTrack != null) stringResource(R.string.now_playing_title, playbackState.currentTrack.title) else stringResource(R.string.no_track_playing),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (nextTrack != null) stringResource(R.string.next_track_title, nextTrack.title, nextTrack.artist) else if (playbackState.currentTrack != null) stringResource(R.string.no_upcoming_tracks_end) else stringResource(R.string.queue_empty_hint),
                            color = if (nextTrack != null) AntiqueGold else Color.Gray,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = Color(0xFF222222),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (remainingQueueCount > 0) stringResource(R.string.queue_remaining_count, remainingQueueCount) else "Queue",
                                color = Color.LightGray,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowUp,
                                contentDescription = null,
                                tint = Color.LightGray,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // カテゴリ切り替えチップ（アルバム / アーティスト / 全曲 / 最近・キャッシュ）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HubCategory.values().forEach { category ->
                    val isSelected = selectedCategory == category
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (isHapticsEnabled) triggerHapticFeedback(context, 1)
                            selectedCategory = category
                        },
                        label = {
                            Text(
                                text = stringResource(category.labelResId),
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = category.icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = DarkSurface,
                            labelColor = Color.LightGray,
                            iconColor = Color.LightGray,
                            selectedContainerColor = AccentGreen.copy(alpha = 0.18f),
                            selectedLabelColor = AccentGreen,
                            selectedLeadingIconColor = AccentGreen
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = Color(0xFF2A2A2A),
                            selectedBorderColor = AccentGreen
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.testTag("hub_chip_${category.name.lowercase()}")
                    )
                }
            }

            // メインコンテンツエリア（カテゴリ別）
            if (filteredTracks.isEmpty()) {
                HubEmptyState(
                    hasQuery = searchQuery.isNotBlank(),
                    onOpenStorage = onOpenStorage
                )
            } else {
                when (selectedCategory) {
                    HubCategory.ALBUMS -> {
                        HubAlbumsView(
                            tracks = filteredTracks,
                            playbackState = playbackState,
                            isHapticsEnabled = isHapticsEnabled,
                            onPlayTrack = { track, playlist ->
                                viewModel.playTrack(track, playlist)
                            },
                            onPlayAlbum = { albumTracks, shuffle ->
                                viewModel.playAlbum(albumTracks, shuffle = shuffle)
                            }
                        )
                    }
                    HubCategory.FORMS -> {
                        HubFormsView(
                            tracks = filteredTracks,
                            playbackState = playbackState,
                            isHapticsEnabled = isHapticsEnabled,
                            onPlayTrack = { track, playlist ->
                                viewModel.playTrack(track, playlist)
                            },
                            onPlayForm = { formTracks, shuffle ->
                                viewModel.playForm(formTracks, shuffle = shuffle)
                            }
                        )
                    }
                    HubCategory.COMPOSERS -> {
                        HubComposersView(
                            tracks = filteredTracks,
                            playbackState = playbackState,
                            isHapticsEnabled = isHapticsEnabled,
                            onPlayTrack = { track, playlist ->
                                viewModel.playTrack(track, playlist)
                            },
                            onPlayComposer = { composerTracks, shuffle ->
                                viewModel.playComposer(composerTracks, shuffle = shuffle)
                            }
                        )
                    }
                    HubCategory.GENRES -> {
                        HubGenresView(
                            tracks = filteredTracks,
                            playbackState = playbackState,
                            isHapticsEnabled = isHapticsEnabled,
                            onPlayTrack = { track, playlist ->
                                viewModel.playTrack(track, playlist)
                            },
                            onPlayGenre = { genreTracks, shuffle ->
                                viewModel.playGenre(genreTracks, shuffle = shuffle)
                            }
                        )
                    }
                }
            }
        }

        if (showQueueSheet) {
            QueueBottomSheet(
                playbackState = playbackState,
                isHapticsEnabled = isHapticsEnabled,
                onDismiss = { showQueueSheet = false },
                onTrackClick = { index ->
                    viewModel.skipToQueueIndex(index)
                }
            )
        }
    }
}

/**
 * アルバムビュー
 */
@Composable
private fun HubAlbumsView(
    tracks: List<TrackInfo>,
    playbackState: PlaybackUiState,
    isHapticsEnabled: Boolean,
    onPlayTrack: (TrackInfo, List<TrackInfo>) -> Unit,
    onPlayAlbum: (List<TrackInfo>, Boolean) -> Unit
) {
    val context = LocalContext.current
    val albumGroups = remember(tracks) {
        tracks.groupBy { if (it.album.isBlank()) "Unknown Album" else it.album }
            .toList()
            .sortedBy { it.first.lowercase() }
    }

    val expandedAlbums = remember { mutableStateMapOf<String, Boolean>() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
        }

        items(albumGroups, key = { it.first }) { (albumName, albumTracks) ->
            val isExpanded = expandedAlbums[albumName] == true
            val isCompilation = albumTracks.any { it.isCompilation } || albumTracks.map { it.artist }.distinct().size > 1
            val artistName = if (isCompilation) {
                "コンピレーション • Various Artists"
            } else {
                albumTracks.firstOrNull()?.artist?.takeIf { it.isNotBlank() } ?: "Various Artists"
            }
            val totalDurationMs = albumTracks.sumOf { it.durationMs }
            val hasActiveTrack = albumTracks.any { it.id == playbackState.currentTrack?.id }

            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (hasActiveTrack) AccentGreen.copy(alpha = 0.5f) else Color(0xFF242424),
                        RoundedCornerShape(14.dp)
                    )
                    .testTag("album_card_${albumName.replace(" ", "_")}")
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isHapticsEnabled) triggerHapticFeedback(context, 1)
                                expandedAlbums[albumName] = !isExpanded
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // アルバムアイコン
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkSurfaceVariant)
                                .border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Album,
                                contentDescription = null,
                                tint = if (hasActiveTrack) AccentGreen else Color.Gray,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // アルバム情報
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = albumName,
                                    color = if (hasActiveTrack) AccentGreen else Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (isCompilation) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = Color(0xFF252525),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "コンピレーション",
                                            color = AntiqueGold,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$artistName • ${albumTracks.size}曲 • ${Id3Helper.formatDuration(totalDurationMs)}",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // アクションボタン（全曲再生 / シャッフル / 展開矢印）
                        IconButton(
                            onClick = {
                                if (isHapticsEnabled) triggerHapticFeedback(context, 1)
                                onPlayAlbum(albumTracks, false)
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "アルバム全曲再生",
                                tint = AccentGreen,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                if (isHapticsEnabled) triggerHapticFeedback(context, 1)
                                onPlayAlbum(albumTracks, true)
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shuffle,
                                contentDescription = "シャッフル再生",
                                tint = Color.LightGray,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = if (isExpanded) "折りたたむ" else "展開",
                            tint = Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 展開時のトラック一覧
                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkSurfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            albumTracks.forEachIndexed { index, track ->
                                val isCurrent = track.id == playbackState.currentTrack?.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            if (isHapticsEnabled) triggerHapticFeedback(context, 1)
                                            onPlayTrack(track, albumTracks)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        color = if (isCurrent) AccentGreen else Color.Gray,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.width(24.dp)
                                    )

                                    if (isCurrent) {
                                        Icon(
                                            imageVector = Icons.Rounded.VolumeUp,
                                            contentDescription = "再生中",
                                            tint = AccentGreen,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .padding(end = 4.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = track.title.ifBlank { "Untitled" },
                                            color = if (isCurrent) AccentGreen else Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (isCompilation && track.artist.isNotBlank()) {
                                            Text(
                                                text = track.artist,
                                                color = Color.Gray,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    if (track.isDriveSource) {
                                        Icon(
                                            imageVector = Icons.Rounded.CloudDone,
                                            contentDescription = "Google Drive",
                                            tint = AccentGreen.copy(alpha = 0.8f),
                                            modifier = Modifier
                                                .size(14.dp)
                                                .padding(end = 6.dp)
                                        )
                                    }

                                    Text(
                                        text = Id3Helper.formatDuration(track.durationMs),
                                        color = Color.Gray,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 作曲家ビュー（ID3タグ TCOM / Vorbis COMPOSER ベース）
 */
@Composable
private fun HubComposersView(
    tracks: List<TrackInfo>,
    playbackState: PlaybackUiState,
    isHapticsEnabled: Boolean,
    onPlayTrack: (TrackInfo, List<TrackInfo>) -> Unit,
    onPlayComposer: (List<TrackInfo>, Boolean) -> Unit
) {
    val context = LocalContext.current
    val composerGroups = remember(tracks) {
        tracks.groupBy { track ->
            if (track.composer.isNotBlank()) track.composer.trim()
            else "未設定の作曲家"
        }.toList().sortedWith(
            compareBy<Pair<String, List<TrackInfo>>> { (name, _) ->
                if (name == "未設定の作曲家") 1 else 0
            }.thenBy { (name, _) -> name.lowercase() }
        )
    }

    val expandedComposers = remember { mutableStateMapOf<String, Boolean>() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
        }

        items(composerGroups, key = { it.first }) { (composerName, composerTracks) ->
            val isExpanded = expandedComposers[composerName] == true
            val albumCount = composerTracks.map { it.album }.distinct().size
            val hasActiveTrack = composerTracks.any { it.id == playbackState.currentTrack?.id }

            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (hasActiveTrack) AccentGreen.copy(alpha = 0.5f) else Color(0xFF242424),
                        RoundedCornerShape(14.dp)
                    )
                    .testTag("composer_card_${composerName.replace(" ", "_")}")
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isHapticsEnabled) triggerHapticFeedback(context, 1)
                                expandedComposers[composerName] = !isExpanded
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 作曲家アイコン
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(DarkSurfaceVariant)
                                .border(1.dp, Color(0xFF333333), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                tint = if (hasActiveTrack) AccentGreen else Color.LightGray,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // 作曲家情報
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = composerName,
                                color = if (hasActiveTrack) AccentGreen else Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${composerTracks.size}曲 • ${albumCount}アルバム",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // アクションボタン（全曲再生 / シャッフル / 展開矢印）
                        IconButton(
                            onClick = {
                                if (isHapticsEnabled) triggerHapticFeedback(context, 1)
                                onPlayComposer(composerTracks, false)
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "作曲家の曲を再生",
                                tint = AccentGreen,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                if (isHapticsEnabled) triggerHapticFeedback(context, 1)
                                onPlayComposer(composerTracks, true)
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shuffle,
                                contentDescription = "シャッフル再生",
                                tint = Color.LightGray,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = if (isExpanded) "折りたたむ" else "展開",
                            tint = Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 展開時のトラック一覧
                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkSurfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            composerTracks.forEachIndexed { index, track ->
                                val isCurrent = track.id == playbackState.currentTrack?.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            if (isHapticsEnabled) triggerHapticFeedback(context, 1)
                                            onPlayTrack(track, composerTracks)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        color = if (isCurrent) AccentGreen else Color.Gray,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.width(24.dp)
                                    )

                                    if (isCurrent) {
                                        Icon(
                                            imageVector = Icons.Rounded.VolumeUp,
                                            contentDescription = "再生中",
                                            tint = AccentGreen,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .padding(end = 4.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = track.title.ifBlank { "Untitled" },
                                            color = if (isCurrent) AccentGreen else Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${track.album} • ${track.artist}",
                                            color = Color.Gray,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    if (track.isDriveSource) {
                                        Icon(
                                            imageVector = Icons.Rounded.CloudDone,
                                            contentDescription = "Google Drive",
                                            tint = AccentGreen.copy(alpha = 0.8f),
                                            modifier = Modifier
                                                .size(14.dp)
                                                .padding(end = 6.dp)
                                        )
                                    }

                                    Text(
                                        text = Id3Helper.formatDuration(track.durationMs),
                                        color = Color.Gray,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * ジャンルビュー（ID3タグ TCON / Vorbis GENRE ベース）
 */
@Composable
private fun HubGenresView(
    tracks: List<TrackInfo>,
    playbackState: PlaybackUiState,
    isHapticsEnabled: Boolean,
    onPlayTrack: (TrackInfo, List<TrackInfo>) -> Unit,
    onPlayGenre: (List<TrackInfo>, Boolean) -> Unit
) {
    val context = LocalContext.current
    val genreGroups = remember(tracks) {
        tracks.groupBy { track ->
            if (track.genre.isNotBlank()) track.genre.trim()
            else "未設定のジャンル"
        }.toList().sortedWith(
            compareBy<Pair<String, List<TrackInfo>>> { (name, _) ->
                if (name == "未設定のジャンル") 1 else 0
            }.thenBy { (name, _) -> name.lowercase() }
        )
    }

    val expandedGenres = remember { mutableStateMapOf<String, Boolean>() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
        }

        items(genreGroups, key = { it.first }) { (genreName, genreTracks) ->
            val isExpanded = expandedGenres[genreName] == true
            val albumCount = genreTracks.map { it.album }.distinct().size
            val hasActiveTrack = genreTracks.any { it.id == playbackState.currentTrack?.id }

            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (hasActiveTrack) AccentGreen.copy(alpha = 0.5f) else Color(0xFF242424),
                        RoundedCornerShape(14.dp)
                    )
                    .testTag("genre_card_${genreName.replace(" ", "_")}")
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isHapticsEnabled) triggerHapticFeedback(context, 1)
                                expandedGenres[genreName] = !isExpanded
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // ジャンルアイコン
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkSurfaceVariant)
                                .border(1.dp, Color(0xFF333333), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Category,
                                contentDescription = null,
                                tint = if (hasActiveTrack) AccentGreen else Color.LightGray,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // ジャンル情報
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = genreName,
                                color = if (hasActiveTrack) AccentGreen else Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${genreTracks.size}曲 • ${albumCount}アルバム",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // アクションボタン（全曲再生 / シャッフル / 展開矢印）
                        IconButton(
                            onClick = {
                                if (isHapticsEnabled) triggerHapticFeedback(context, 1)
                                onPlayGenre(genreTracks, false)
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "ジャンルの曲を再生",
                                tint = AccentGreen,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                if (isHapticsEnabled) triggerHapticFeedback(context, 1)
                                onPlayGenre(genreTracks, true)
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shuffle,
                                contentDescription = "シャッフル再生",
                                tint = Color.LightGray,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = if (isExpanded) "折りたたむ" else "展開",
                            tint = Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 展開時のトラック一覧
                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkSurfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            genreTracks.forEachIndexed { index, track ->
                                val isCurrent = track.id == playbackState.currentTrack?.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            if (isHapticsEnabled) triggerHapticFeedback(context, 1)
                                            onPlayTrack(track, genreTracks)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        color = if (isCurrent) AccentGreen else Color.Gray,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.width(24.dp)
                                    )

                                    if (isCurrent) {
                                        Icon(
                                            imageVector = Icons.Rounded.VolumeUp,
                                            contentDescription = "再生中",
                                            tint = AccentGreen,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .padding(end = 4.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = track.title.ifBlank { "Untitled" },
                                            color = if (isCurrent) AccentGreen else Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${track.artist} • ${track.album}",
                                            color = Color.Gray,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    if (track.isDriveSource) {
                                        Icon(
                                            imageVector = Icons.Rounded.CloudDone,
                                            contentDescription = "Google Drive",
                                            tint = AccentGreen.copy(alpha = 0.8f),
                                            modifier = Modifier
                                                .size(14.dp)
                                                .padding(end = 6.dp)
                                        )
                                    }

                                    Text(
                                        text = Id3Helper.formatDuration(track.durationMs),
                                        color = Color.Gray,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 形式ビュー（案C: ID3 Grouping / TIT1 タグ + タイトル・アルバム・ジャンル・フォルダ名のハイブリッド分類）
 * 交響曲、協奏曲、ピアノ、ヴァイオリン・弦楽器、室内楽、声楽・オペラ、管弦楽曲・組曲、吹奏楽・管楽器など
 */
@Composable
private fun HubFormsView(
    tracks: List<TrackInfo>,
    playbackState: PlaybackUiState,
    isHapticsEnabled: Boolean,
    onPlayTrack: (TrackInfo, List<TrackInfo>) -> Unit,
    onPlayForm: (List<TrackInfo>, Boolean) -> Unit
) {
    val context = LocalContext.current
    val formGroups = remember(tracks) {
        val grouped = tracks.groupBy { track ->
            if (track.musicalForm.isNotBlank()) track.musicalForm
            else MusicalFormClassifier.classify(track)
        }
        val orderMap = MusicalFormClassifier.ORDERED_FORMS.mapIndexed { index, name -> name to index }.toMap()
        grouped.toList().sortedWith(
            compareBy<Pair<String, List<TrackInfo>>> { (formName, _) ->
                orderMap[formName] ?: (orderMap.size + 1)
            }.thenBy { (formName, _) -> formName }
        )
    }

    val expandedForms = remember { mutableStateMapOf<String, Boolean>() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
        }

        items(formGroups, key = { it.first }) { (formName, formTracks) ->
            val isExpanded = expandedForms[formName] == true
            val albumCount = formTracks.map { it.album }.distinct().size
            val composerCount = formTracks.map { it.composer.ifBlank { it.artist } }.distinct().size
            val hasActiveTrack = formTracks.any { it.id == playbackState.currentTrack?.id }

            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (hasActiveTrack) AccentGreen.copy(alpha = 0.5f) else Color(0xFF242424),
                        RoundedCornerShape(14.dp)
                    )
                    .testTag("form_card_${formName.replace(" ", "_")}")
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isHapticsEnabled) triggerHapticFeedback(context, 1)
                                expandedForms[formName] = !isExpanded
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 形式アイコン
                        val formIcon = when (formName) {
                            MusicalFormClassifier.FORM_SYMPHONY -> Icons.Rounded.MusicNote
                            MusicalFormClassifier.FORM_CONCERTO -> Icons.Rounded.MusicNote
                            MusicalFormClassifier.FORM_PIANO -> Icons.AutoMirrored.Rounded.QueueMusic
                            MusicalFormClassifier.FORM_STRINGS -> Icons.Rounded.MusicNote
                            MusicalFormClassifier.FORM_CHAMBER -> Icons.Rounded.Equalizer
                            MusicalFormClassifier.FORM_VOCAL_OPERA -> Icons.Rounded.Person
                            MusicalFormClassifier.FORM_ORCHESTRAL -> Icons.Rounded.Album
                            else -> Icons.Rounded.Category
                        }

                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkSurfaceVariant)
                                .border(1.dp, Color(0xFF333333), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = formIcon,
                                contentDescription = null,
                                tint = if (hasActiveTrack) AccentGreen else Color.LightGray,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // 形式情報
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = formName,
                                color = if (hasActiveTrack) AccentGreen else Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${formTracks.size}曲 • ${composerCount}作曲家/演奏家 • ${albumCount}アルバム",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // アクションボタン（全曲再生 / シャッフル / 展開矢印）
                        IconButton(
                            onClick = {
                                if (isHapticsEnabled) triggerHapticFeedback(context, 1)
                                onPlayForm(formTracks, false)
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "形式の全曲を再生",
                                tint = AccentGreen,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                if (isHapticsEnabled) triggerHapticFeedback(context, 1)
                                onPlayForm(formTracks, true)
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shuffle,
                                contentDescription = "シャッフル再生",
                                tint = Color.LightGray,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = if (isExpanded) "折りたたむ" else "展開",
                            tint = Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 展開時のトラック一覧
                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkSurfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            formTracks.forEachIndexed { index, track ->
                                val isCurrent = track.id == playbackState.currentTrack?.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            if (isHapticsEnabled) triggerHapticFeedback(context, 1)
                                            onPlayTrack(track, formTracks)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        color = if (isCurrent) AccentGreen else Color.Gray,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.width(24.dp)
                                    )

                                    if (isCurrent) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                                            contentDescription = "再生中",
                                            tint = AccentGreen,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .padding(end = 4.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = track.title.ifBlank { "Untitled" },
                                            color = if (isCurrent) AccentGreen else Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val composerOrArtist = track.composer.ifBlank { track.artist }
                                        Text(
                                            text = "$composerOrArtist • ${track.album}",
                                            color = Color.Gray,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    if (track.isDriveSource) {
                                        Icon(
                                            imageVector = Icons.Rounded.CloudDone,
                                            contentDescription = "Google Drive",
                                            tint = AccentGreen.copy(alpha = 0.8f),
                                            modifier = Modifier
                                                .size(14.dp)
                                                .padding(end = 6.dp)
                                        )
                                    }

                                    Text(
                                        text = Id3Helper.formatDuration(track.durationMs),
                                        color = Color.Gray,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * アーティストビュー
 */
@Composable
private fun HubArtistsView(
    tracks: List<TrackInfo>,
    playbackState: PlaybackUiState,
    isHapticsEnabled: Boolean,
    onPlayTrack: (TrackInfo, List<TrackInfo>) -> Unit,
    onPlayArtist: (List<TrackInfo>, Boolean) -> Unit
) {
    val context = LocalContext.current
    val artistGroups = remember(tracks) {
        tracks.groupBy { if (it.artist.isBlank()) "Unknown Artist" else it.artist }
            .toList()
            .sortedBy { it.first.lowercase() }
    }

    val expandedArtists = remember { mutableStateMapOf<String, Boolean>() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
        }

        items(artistGroups, key = { it.first }) { (artistName, artistTracks) ->
            val isExpanded = expandedArtists[artistName] == true
            val albumCount = artistTracks.map { it.album }.distinct().size
            val hasActiveTrack = artistTracks.any { it.id == playbackState.currentTrack?.id }

            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (hasActiveTrack) AccentGreen.copy(alpha = 0.5f) else Color(0xFF242424),
                        RoundedCornerShape(14.dp)
                    )
                    .testTag("artist_card_${artistName.replace(" ", "_")}")
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isHapticsEnabled) triggerHapticFeedback(context, 1)
                                expandedArtists[artistName] = !isExpanded
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // アーティストアイコン
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(DarkSurfaceVariant)
                                .border(1.dp, Color(0xFF333333), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                tint = if (hasActiveTrack) AccentGreen else Color.LightGray,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = artistName,
                                color = if (hasActiveTrack) AccentGreen else Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${artistTracks.size}曲 • ${albumCount}アルバム",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }

                        // 全曲再生ボタン
                        IconButton(
                            onClick = {
                                if (isHapticsEnabled) triggerHapticFeedback(context, 1)
                                onPlayArtist(artistTracks, false)
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "アーティスト全曲再生",
                                tint = AccentGreen,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = if (isExpanded) "折りたたむ" else "展開",
                            tint = Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 展開時のトラック一覧
                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkSurfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            artistTracks.forEach { track ->
                                val isCurrent = track.id == playbackState.currentTrack?.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            if (isHapticsEnabled) triggerHapticFeedback(context, 1)
                                            onPlayTrack(track, artistTracks)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isCurrent) {
                                        Icon(
                                            imageVector = Icons.Rounded.VolumeUp,
                                            contentDescription = "再生中",
                                            tint = AccentGreen,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .padding(end = 6.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = track.title.ifBlank { "Untitled" },
                                            color = if (isCurrent) AccentGreen else Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = track.album.ifBlank { "Unknown Album" },
                                            color = Color.Gray,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    if (track.isDriveSource) {
                                        Icon(
                                            imageVector = Icons.Rounded.CloudDone,
                                            contentDescription = "Google Drive",
                                            tint = AccentGreen.copy(alpha = 0.8f),
                                            modifier = Modifier
                                                .size(14.dp)
                                                .padding(end = 6.dp)
                                        )
                                    }

                                    Text(
                                        text = Id3Helper.formatDuration(track.durationMs),
                                        color = Color.Gray,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 全曲 / 最近再生ビュー
 */
@Composable
private fun HubTracksView(
    tracks: List<TrackInfo>,
    playbackState: PlaybackUiState,
    isHapticsEnabled: Boolean,
    onPlayTrack: (TrackInfo) -> Unit,
    onPlayAll: (Boolean) -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // クイック全曲再生 & シャッフルバー
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${tracks.size} 曲",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (isHapticsEnabled) triggerHapticFeedback(context, 1)
                            onPlayAll(false)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentGreen,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.play_all_tracks), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            if (isHapticsEnabled) triggerHapticFeedback(context, 1)
                            onPlayAll(true)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DarkSurface,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333333)),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.LightGray
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.shuffle_play), fontSize = 12.sp)
                    }
                }
            }
        }

        itemsIndexed(tracks, key = { index, track -> "${track.id}_$index" }) { index, track ->
            val isCurrent = track.id == playbackState.currentTrack?.id

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isCurrent) DarkSurfaceVariant else DarkSurface
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (isCurrent) AccentGreen.copy(alpha = 0.6f) else Color(0xFF202020),
                        RoundedCornerShape(10.dp)
                    )
                    .clickable {
                        if (isHapticsEnabled) triggerHapticFeedback(context, 1)
                        onPlayTrack(track)
                    }
                    .testTag("hub_track_${track.id}")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkPureBackground),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCurrent) {
                            Icon(
                                imageVector = Icons.Rounded.VolumeUp,
                                contentDescription = stringResource(R.string.now_playing),
                                tint = AccentGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Text(
                                text = "${index + 1}",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title.ifBlank { "Untitled" },
                            color = if (isCurrent) AccentGreen else Color.White,
                            fontSize = 14.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${track.artist.ifBlank { stringResource(R.string.unknown_artist) }} • ${track.album.ifBlank { stringResource(R.string.unknown_album) }}",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // 音源バッジ（端末またはDrive）
                    Surface(
                        color = if (track.isDriveSource) AccentGreen.copy(alpha = 0.15f) else Color(0xFF262626),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = if (track.isDriveSource) "Drive" else stringResource(R.string.storage_local),
                            color = if (track.isDriveSource) AccentGreen else Color.LightGray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = Id3Helper.formatDuration(track.durationMs),
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 検索または楽曲なしの空状態
 */
@Composable
private fun HubEmptyState(
    hasQuery: Boolean,
    onOpenStorage: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = Color(0xFF444444),
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (hasQuery) stringResource(R.string.no_search_results) else stringResource(R.string.no_tracks_available),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (hasQuery) stringResource(R.string.search_different_keyword) else stringResource(R.string.no_tracks_add_storage_hint),
                color = Color.Gray,
                fontSize = 13.sp
            )

            if (!hasQuery) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onOpenStorage,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = Color.Black),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(imageVector = Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.open_storage_settings), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}



/**
 * 再生キュー（Queue）ボトムシート
 * 現在再生中の楽曲と、次に控える再生待ち楽曲一覧を表示。
 * タップで即時スキップ再生、閉じるボタン付き。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueBottomSheet(
    playbackState: PlaybackUiState,
    isHapticsEnabled: Boolean,
    onDismiss: () -> Unit,
    onTrackClick: (Int) -> Unit
) {
    val context = LocalContext.current
    val currentTrack = playbackState.currentTrack
    val playlist = playbackState.currentPlaylist
    val currentIndex = playbackState.currentIndex

    // 今後再生される曲（現在曲の次の曲からリスト末尾まで）
    val upcomingTracks = remember(playlist, currentIndex) {
        if (playlist.isEmpty() || currentIndex < 0) {
            emptyList<Pair<Int, TrackInfo>>()
        } else {
            ((currentIndex + 1) until playlist.size).map { idx ->
                Pair(idx, playlist[idx])
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = Color.Gray.copy(alpha = 0.5f)
            )
        },
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = Modifier.testTag("queue_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            // ヘッダー行: タイトル、残り曲数、閉じるボタン
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = null,
                        tint = AccentGreen,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "再生キュー",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (upcomingTracks.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = DarkSurfaceVariant,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "残り ${upcomingTracks.size}曲",
                                color = AccentGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // 閉じるボタン（ユーザー指定要件）
                IconButton(
                    onClick = {
                        if (isHapticsEnabled) triggerHapticFeedback(context, 1)
                        onDismiss()
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(DarkSurfaceVariant)
                        .testTag("queue_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "閉じる",
                        tint = Color.LightGray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // セクション1: 現在再生中 (NOW PLAYING)
                item(key = "now_playing_header") {
                    Text(
                        text = "NOW PLAYING",
                        color = AccentGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                item(key = "now_playing_card") {
                    if (currentTrack != null) {
                        Surface(
                            color = DarkSurfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(AccentGreen.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (playbackState.isPlaying) Icons.Rounded.Equalizer else Icons.Rounded.PlayArrow,
                                        contentDescription = null,
                                        tint = AccentGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentTrack.title,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${currentTrack.artist} • ${currentTrack.album}",
                                        color = Color.Gray,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = Id3Helper.formatDuration(currentTrack.durationMs),
                                    color = Color.LightGray,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    } else {
                        Surface(
                            color = DarkSurfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.no_track_playing),
                                color = Color.Gray,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(16.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // セクション2: 次の曲 (UP NEXT)
                item(key = "up_next_header") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.up_next_tap_skip),
                        color = Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                if (upcomingTracks.isEmpty()) {
                    item(key = "up_next_empty") {
                        Surface(
                            color = DarkSurfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = if (currentTrack != null) stringResource(R.string.no_upcoming_tracks_end) else stringResource(R.string.queue_empty_hint),
                                color = Color.Gray,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(16.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(upcomingTracks, key = { "${it.first}_${it.second.id}" }) { (actualIndex, track) ->
                        val queuePos = actualIndex - currentIndex
                        Surface(
                            color = Color(0xFF1E1E1E),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isHapticsEnabled) triggerHapticFeedback(context, 1)
                                    onTrackClick(actualIndex)
                                }
                                .testTag("queue_item_${track.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // キュー順序番号バッジ
                                Text(
                                    text = "$queuePos",
                                    color = AccentGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(24.dp)
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.title,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${track.artist} • ${track.album}",
                                        color = Color.Gray,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                    text = Id3Helper.formatDuration(track.durationMs),
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                item(key = "bottom_space") {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}
