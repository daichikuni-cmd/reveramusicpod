package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AntiqueGold

enum class NodeCacheStatus(val labelJa: String) {
    ALL_CACHED("すべてキャッシュ済"),
    PARTIAL_CACHED("一部キャッシュ済"),
    NOT_CACHED("未キャッシュ(未保存)"),
    SAVING_OR_QUEUED("保存中/待機"),
    WAITING_DELETE("削除待ち")
}

val PartialAmberColor = Color(0xFFFFB300)

/**
 * ディレクトリ（ファイル）名の先頭の分類アイコン
 */
@Composable
fun DriveNodeStatusIcon(
    status: NodeCacheStatus,
    isFolder: Boolean,
    isExpanded: Boolean = false,
    isBusy: Boolean = false,
    size: Dp = 22.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        when (status) {
            NodeCacheStatus.ALL_CACHED -> {
                // すべてキャッシュ済（金色・完了バッジ）
                if (isFolder) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                        contentDescription = status.labelJa,
                        tint = AntiqueGold,
                        modifier = Modifier.size(size)
                    )
                    // 小さなチェックバッジ
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 2.dp, y = 2.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(AntiqueGold),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(8.dp)
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = status.labelJa,
                        tint = AntiqueGold,
                        modifier = Modifier.size(size)
                    )
                }
            }

            NodeCacheStatus.PARTIAL_CACHED -> {
                // ディレクトリの下部一部キャッシュ済（アンバー色・一部インジケータ）
                Icon(
                    imageVector = if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                    contentDescription = status.labelJa,
                    tint = PartialAmberColor,
                    modifier = Modifier.size(size)
                )
                // 小さな一部保存ドット
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 1.dp, y = 1.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(PartialAmberColor)
                )
            }

            NodeCacheStatus.NOT_CACHED -> {
                // ディレクトリ下部未キャッシュ（未保存、控えめなグレー）
                val grayColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                if (isFolder) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                        contentDescription = status.labelJa,
                        tint = grayColor,
                        modifier = Modifier.size(size)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = status.labelJa,
                        tint = grayColor,
                        modifier = Modifier.size(size)
                    )
                }
            }

            NodeCacheStatus.SAVING_OR_QUEUED -> {
                // 保存中/待機（赤色のダウンロードアイコン）
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(size * 0.85f),
                        color = RedAlertColor,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = status.labelJa,
                        tint = RedAlertColor,
                        modifier = Modifier.size(size)
                    )
                }
            }

            NodeCacheStatus.WAITING_DELETE -> {
                // 削除待ち（赤色のゴミ箱・解除待ちアイコン）
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(size * 0.85f),
                        color = RedAlertColor,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = status.labelJa,
                        tint = RedAlertColor,
                        modifier = Modifier.size(size)
                    )
                }
            }
        }
    }
}
