package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Track
import com.example.ui.components.CacheTreeCheckbox
import com.example.ui.components.DriveNodeStatusIcon
import com.example.ui.components.EmptyLibraryState
import com.example.ui.components.NodeCacheStatus
import com.example.ui.theme.AntiqueGold
import com.example.ui.theme.DarkSurfaceElevated
import com.example.util.Id3Helper
import java.io.File
import java.util.Locale

class DriveTreeNode(
    val fullPath: String,
    val name: String,
    val isFolder: Boolean,
    val level: Int,
    val subFolders: MutableMap<String, DriveTreeNode> = mutableMapOf(),
    val tracks: MutableList<Track> = mutableListOf()
) {
    fun getAllRecursiveTracks(): List<Track> {
        val list = mutableListOf<Track>()
        val sortedTracks = tracks.sortedWith(
            compareBy<Track>({ if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE })
                .thenBy { it.fileName }
        )
        list.addAll(sortedTracks)
        for (sub in subFolders.values.sortedBy { it.name }) {
            list.addAll(sub.getAllRecursiveTracks())
        }
        return list
    }

    fun getAllFolderPaths(): List<String> {
        val list = mutableListOf(fullPath)
        for (sub in subFolders.values) {
            list.addAll(sub.getAllFolderPaths())
        }
        return list
    }

    /**
     * デフォルトで開いておくフォルダパスのリストを取得。
     * 「内部が音楽ファイルだけでフォルダがないフォルダ」（subFoldersが空の末端フォルダ）は
     * デフォルトで閉じて表示するため、subFoldersを持つフォルダのみを初期展開に含める。
     */
    fun getDefaultExpandedFolderPaths(): List<String> {
        val list = mutableListOf<String>()
        if (subFolders.isNotEmpty()) {
            list.add(fullPath)
            for (sub in subFolders.values) {
                list.addAll(sub.getDefaultExpandedFolderPaths())
            }
        }
        return list
    }
}

/**
 * ディスク上のキャッシュ容量をフォーマット (例: 258MB, 1.2GB, 850KB, 0MB)
 * ユーザー指定形式: "242/1002 258MB" に準拠
 */
fun formatDiskSize(bytes: Long): String {
    if (bytes <= 0L) return "0MB"
    val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
    if (gb >= 1.0) {
        val formatted = if (gb >= 10.0) "%.0f".format(Locale.US, gb) else "%.1f".format(Locale.US, gb)
        return "${formatted.removeSuffix(".0")}GB"
    }
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    if (mb >= 1.0) {
        val formatted = if (mb >= 100.0) "%.0f".format(Locale.US, mb) else "%.1f".format(Locale.US, mb)
        return "${formatted.removeSuffix(".0")}MB"
    }
    val kb = bytes.toDouble() / 1024.0
    if (kb >= 1.0) {
        val formatted = "%.0f".format(Locale.US, kb)
        return "${formatted}KB"
    }
    return "${bytes}B"
}

/**
 * フォルダ内のキャッシュ済みトラックの実ディスクサイズを合算
 */
fun calculateCachedFolderBytes(tracks: List<Track>, context: Context): Long {
    val cacheDir = File(context.cacheDir, "audio_cache")
    var totalBytes = 0L
    for (track in tracks) {
        if (!track.isCached) continue
        val path = track.localFilePath
        val file = if (path != null) File(path) else File(cacheDir, "${track.id}_${track.fileName}")
        if (file.exists() && file.length() > 0L) {
            totalBytes += file.length()
        } else if (track.durationMs > 0L) {
            // 実ファイルが物理的に未配備（テスト・モック時等）のフォールバック計算（約192kbps MP3想定: 24KB/s）
            totalBytes += (track.durationMs / 1000L) * 24L * 1024L
        } else {
            // ファイルも曲長も不明な場合のフォールバック（1曲約3MB）
            totalBytes += 3L * 1024L * 1024L
        }
    }
    return totalBytes
}

@Composable
fun FolderTreeScreen(
    tracks: List<Track>,
    desiredCheckedIds: Set<String>,
    busyIds: Set<String>,
    expandedPaths: Set<String>,
    onToggleExpand: (String) -> Unit,
    onInitExpandedPaths: (Set<String>) -> Unit,
    currentPlayingTrack: Track?,
    isPlaying: Boolean,
    onTrackClick: (Track, List<Track>) -> Unit,
    onPlayFolderRecursive: (List<Track>, Track?) -> Unit = { _, _ -> },
    onTogglePlayPause: () -> Unit = {},
    onToggleTrack: (Track) -> Unit,
    onToggleFolder: (String, Boolean) -> Unit,
    onOpenSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Build the hierarchical tree from all tracks (音楽ファイルが存在するフォルダのみ)
    val rootNodes = remember(tracks) {
        buildDriveTree(tracks)
    }

    if (rootNodes.isEmpty()) {
        EmptyLibraryState(
            title = "音楽ファイルが存在しません",
            description = "Googleドライブを同期すると、音楽ファイルを含むフォルダ階層ツリーが表示されます。",
            onSyncClick = onOpenSync,
            modifier = modifier
        )
        return
    }

    // Initialize expanded paths only once on initial load (preserving user state).
    // Leaf folders with only music files (no subfolders) are closed by default.
    LaunchedEffect(rootNodes) {
        if (rootNodes.isNotEmpty()) {
            val initialPaths = rootNodes.flatMap { it.getDefaultExpandedFolderPaths() }.toSet()
            onInitExpandedPaths(initialPaths)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("folder_tree_screen")
    ) {
        // Compact 5-State Icon Classification Legend (Bulky description text removed per request)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NodeLegendItem(NodeCacheStatus.ALL_CACHED, "すべてキャッシュ済")
                NodeLegendItem(NodeCacheStatus.PARTIAL_CACHED, "一部キャッシュ済")
                NodeLegendItem(NodeCacheStatus.SAVING_OR_QUEUED, "保存中/待機")
                NodeLegendItem(NodeCacheStatus.NOT_CACHED, "未キャッシュ(未保存)")
            }
        }

        // Tree List
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp)
                .testTag("drive_tree_list")
        ) {
            for (root in rootNodes) {
                renderTreeNode(
                    node = root,
                    expandedPaths = expandedPaths,
                    desiredCheckedIds = desiredCheckedIds,
                    busyIds = busyIds,
                    currentPlayingTrack = currentPlayingTrack,
                    isPlaying = isPlaying,
                    onToggleExpand = onToggleExpand,
                    onToggleTrack = onToggleTrack,
                    onToggleFolder = onToggleFolder,
                    onTrackClick = onTrackClick,
                    onPlayFolderRecursive = onPlayFolderRecursive,
                    onTogglePlayPause = onTogglePlayPause
                )
            }
            item {
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}

@Composable
private fun NodeLegendItem(
    status: NodeCacheStatus,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        DriveNodeStatusIcon(
            status = status,
            isFolder = true,
            size = 16.dp
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.renderTreeNode(
    node: DriveTreeNode,
    expandedPaths: Set<String>,
    desiredCheckedIds: Set<String>,
    busyIds: Set<String>,
    currentPlayingTrack: Track?,
    isPlaying: Boolean,
    onToggleExpand: (String) -> Unit,
    onToggleTrack: (Track) -> Unit,
    onToggleFolder: (String, Boolean) -> Unit,
    onTrackClick: (Track, List<Track>) -> Unit,
    onPlayFolderRecursive: (List<Track>, Track?) -> Unit,
    onTogglePlayPause: () -> Unit
) {
    val allTracks = node.getAllRecursiveTracks().filter { isMusicFile(it.fileName) }
    val totalTracks = allTracks.size
    // 音楽ファイルが存在しないフォルダは表示しない
    if (totalTracks == 0) return

    val isExpanded = expandedPaths.contains(node.fullPath)
    val cachedTracks = allTracks.count { it.isCached }
    val checkedTracks = allTracks.count { it.id in desiredCheckedIds }

    // Folder Checked state
    val isFolderAllChecked = totalTracks > 0 && checkedTracks == totalTracks
    val isFolderAllCached = totalTracks > 0 && cachedTracks == totalTracks
    val isFolderPartiallyCached = cachedTracks in 1 until totalTracks
    val isFolderBusy = busyIds.contains(node.fullPath) || allTracks.any { it.id in busyIds }
    val hasPendingDownloads = allTracks.any { it.id in desiredCheckedIds && !it.isCached }

    // Calculate 4-classification node cache status:
    // 全曲キャッシュ済、処理中、ダウンロード待機中、一部キャッシュ済、未キャッシュ
    val folderStatus = when {
        isFolderAllCached -> NodeCacheStatus.ALL_CACHED                                       // すべてキャッシュ済
        isFolderBusy -> NodeCacheStatus.SAVING_OR_QUEUED                                      // 保存中/待機 (処理中)
        hasPendingDownloads -> NodeCacheStatus.SAVING_OR_QUEUED                                // 保存中/待機 (未完了・ダウンロード待機中)
        isFolderPartiallyCached -> NodeCacheStatus.PARTIAL_CACHED                             // 一部キャッシュ済
        else -> NodeCacheStatus.NOT_CACHED                                                    // 未キャッシュ(未保存)
    }

    item(key = "folder_${node.fullPath}") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isExpanded) DarkSurfaceElevated.copy(alpha = 0.5f) else Color.Transparent)
                .clickable { onToggleExpand(node.fullPath) }
                .padding(vertical = 5.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Indentation
            Spacer(modifier = Modifier.width((node.level * 16).dp))

            // Expand/Collapse Chevron
            IconButton(
                onClick = { onToggleExpand(node.fullPath) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                    contentDescription = if (isExpanded) "閉じる" else "展開",
                    tint = AntiqueGold,
                    modifier = Modifier.size(18.dp)
                )
            }

            val isFolderPartial = !isFolderAllCached && isFolderPartiallyCached && !hasPendingDownloads && !isFolderBusy
            val isFolderChecked = isFolderAllChecked || hasPendingDownloads

            // 4-State Cache Checkbox (一部キャッシュ済みの中間状態に対応)
            CacheTreeCheckbox(
                isChecked = isFolderChecked,
                isCached = isFolderAllCached,
                isPartial = isFolderPartial,
                isBusy = isFolderBusy,
                onToggle = { onToggleFolder(node.fullPath, isFolderAllChecked) },
                testTag = "checkbox_folder_${node.name}"
            )

            Spacer(modifier = Modifier.width(8.dp))

            val isFolderPlaying = currentPlayingTrack != null && allTracks.any { it.id == currentPlayingTrack.id }

            // 5-Classification Node Status Icon (先頭のアイコン・タップで配下全曲再帰ループ再生)
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(
                        role = androidx.compose.ui.semantics.Role.Button,
                        onClickLabel = "${node.name} の配下全曲を再帰ループ再生"
                    ) {
                        if (isFolderPlaying && isPlaying) {
                            onTogglePlayPause()
                        } else {
                            onPlayFolderRecursive(allTracks, null)
                        }
                    }
                    .padding(2.dp)
                    .testTag("folder_icon_${node.name}")
            ) {
                DriveNodeStatusIcon(
                    status = folderStatus,
                    isFolder = true,
                    isExpanded = isExpanded,
                    isBusy = isFolderBusy,
                    size = 20.dp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Folder Name & Counts
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = node.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val context = LocalContext.current
                val cachedBytes = remember(allTracks) {
                    calculateCachedFolderBytes(allTracks, context)
                }
                val sizeFormatted = formatDiskSize(cachedBytes)
                Text(
                    text = "$cachedTracks/$totalTracks $sizeFormatted",
                    fontSize = 11.sp,
                    color = if (isFolderAllCached) AntiqueGold else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (isExpanded) {
        // Child Folders (音楽ファイルを含むフォルダのみ)
        val validSubFolders = node.subFolders.values
            .filter { it.getAllRecursiveTracks().any { t -> isMusicFile(t.fileName) } }
            .sortedBy { it.name }
        for (sub in validSubFolders) {
            renderTreeNode(
                node = sub,
                expandedPaths = expandedPaths,
                desiredCheckedIds = desiredCheckedIds,
                busyIds = busyIds,
                currentPlayingTrack = currentPlayingTrack,
                isPlaying = isPlaying,
                onToggleExpand = onToggleExpand,
                onToggleTrack = onToggleTrack,
                onToggleFolder = onToggleFolder,
                onTrackClick = onTrackClick,
                onPlayFolderRecursive = onPlayFolderRecursive,
                onTogglePlayPause = onTogglePlayPause
            )
        }

        // Child Tracks in this folder (音楽ファイルのみ)
        val musicTracks = node.tracks
            .filter { isMusicFile(it.fileName) }
            .sortedBy { it.trackNumber }
        for (track in musicTracks) {
            val isTrackChecked = (track.id in desiredCheckedIds) || track.isCached
            val isTrackCached = track.isCached
            val isTrackBusy = busyIds.contains(track.id)
            val isCurrentPlaying = currentPlayingTrack?.id == track.id && isPlaying

            // Calculate 5-state node cache status for single file
            val fileStatus = when {
                isTrackBusy || (isTrackChecked && !isTrackCached) -> NodeCacheStatus.SAVING_OR_QUEUED // 保存中/待機
                isTrackCached -> NodeCacheStatus.ALL_CACHED                                          // すべてキャッシュ済
                else -> NodeCacheStatus.NOT_CACHED                                                   // 未キャッシュ(未保存)
            }

            item(key = "track_${track.id}") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isCurrentPlaying) AntiqueGold.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable { onPlayFolderRecursive(allTracks, track) }
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Indentation (one level deeper than parent folder)
                    Spacer(modifier = Modifier.width(((node.level + 1) * 16).dp + 24.dp))

                    // Track Checkbox with 4-State Visuals
                    CacheTreeCheckbox(
                        isChecked = isTrackChecked,
                        isCached = isTrackCached,
                        isBusy = isTrackBusy,
                        onToggle = { onToggleTrack(track) },
                        testTag = "checkbox_track_${track.id}"
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // 5-Classification File Status Icon (先頭のアイコン)
                    DriveNodeStatusIcon(
                        status = fileStatus,
                        isFolder = false,
                        isBusy = isTrackBusy,
                        size = 18.dp
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    if (isCurrentPlaying) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "再生中",
                            tint = AntiqueGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (isCurrentPlaying) FontWeight.Bold else FontWeight.Normal,
                            color = if (isCurrentPlaying) AntiqueGold else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = track.fileName.substringAfterLast('.', "").uppercase(),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AntiqueGold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(AntiqueGold.copy(alpha = 0.15f))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = Id3Helper.formatDuration(track.durationMs),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

val SUPPORTED_MUSIC_EXTENSIONS = listOf(".mp3", ".m4a", ".aac", ".flac", ".ogg")

fun isMusicFile(fileName: String): Boolean {
    val lower = fileName.lowercase().trim()
    if (lower.endsWith(".wav") || lower.contains(".wav")) return false
    return SUPPORTED_MUSIC_EXTENSIONS.any { lower.endsWith(it) }
}

/**
 * 音楽ファイルのみを抽出し、音楽ファイルが存在するフォルダのみで階層ツリーを構築
 * 音楽ファイルが存在しないフォルダは再帰的に除去される
 */
fun buildDriveTree(tracks: List<Track>): List<DriveTreeNode> {
    val rootsMap = mutableMapOf<String, DriveTreeNode>()
    val musicTracks = tracks.filter { isMusicFile(it.fileName) }

    for (track in musicTracks) {
        val parts = track.folderPath.split('/').filter { it.isNotBlank() }
        if (parts.isEmpty()) continue

        var currentNode: DriveTreeNode? = null
        var currentPath = ""

        for (i in parts.indices) {
            val part = parts[i]
            currentPath = if (currentPath.isEmpty()) part else "$currentPath/$part"

            if (i == 0) {
                val root = rootsMap.getOrPut(part) {
                    DriveTreeNode(
                        fullPath = currentPath,
                        name = part,
                        isFolder = true,
                        level = 0
                    )
                }
                currentNode = root
            } else {
                val parent = currentNode!!
                val child = parent.subFolders.getOrPut(part) {
                    DriveTreeNode(
                        fullPath = currentPath,
                        name = part,
                        isFolder = true,
                        level = i
                    )
                }
                currentNode = child
            }
        }

        // Add track to leaf folder
        currentNode?.tracks?.add(track)
    }

    // 音楽ファイルが存在しない空フォルダ（末端または再帰的に音楽がないフォルダ）を完全除外
    fun pruneNode(node: DriveTreeNode): Boolean {
        node.tracks.removeAll { !isMusicFile(it.fileName) }
        val iterator = node.subFolders.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val hasMusic = pruneNode(entry.value)
            if (!hasMusic) {
                iterator.remove()
            }
        }
        return node.tracks.isNotEmpty() || node.subFolders.isNotEmpty()
    }

    return rootsMap.values.filter { pruneNode(it) }.sortedBy { it.name }
}
