package com.example.ui.screens

import com.example.BuildConfig
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.drive.DriveRoot
import com.example.data.drive.GoogleDriveClient
import com.example.data.model.EditedFolderInfo
import com.example.ui.theme.AntiqueGold
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.WineBurgundy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    isSyncing: Boolean,
    syncStatusMessage: String?,
    isWifiOnly: Boolean,
    connectionName: String,
    cacheSize: String,
    editedFolders: List<EditedFolderInfo>,
    hasDriveToken: Boolean,
    driveRoot: DriveRoot,
    musicVolume: Float = 1.0f,
    onMusicVolumeChange: (Float) -> Unit = {},
    pauseOnBluetoothDisconnect: Boolean = true,
    onTogglePauseOnBluetoothDisconnect: () -> Unit = {},
    onSyncDrive: () -> Unit,
    onToggleWifiOnly: () -> Unit,
    onSelectDriveRoot: (DriveRoot) -> Unit,
    onCacheAll: () -> Unit,
    onClearCache: () -> Unit,
    onCopyEditedFolders: () -> Unit,
    onResetEditedList: () -> Unit,
    onSaveDriveToken: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var showTokenDialog by remember { mutableStateOf(false) }
    var showGoogleAuthDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("settings_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.largo_logo),
                        contentDescription = "Largo Logo",
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Largo",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = AntiqueGold
                        )
                        Text(
                            text = "クラシック音楽専用 Google Drive プレイヤー",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Version ${BuildConfig.VERSION_NAME}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = AntiqueGold.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "設定 & 同期・Mac連携",
                style = MaterialTheme.typography.headlineSmall,
                color = AntiqueGold,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
            Text(
                text = "Googleドライブ同期・明示的ダウンロード・Mac戻しフォルダ管理",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Section: Ambient / Music Playback Volume Slider
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_music_volume_card"),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val volumeIcon = when {
                            musicVolume <= 0.001f -> Icons.Default.VolumeMute
                            musicVolume < 0.4f -> Icons.Default.VolumeDown
                            else -> Icons.Default.VolumeUp
                        }
                        Icon(
                            imageVector = volumeIcon,
                            contentDescription = "音楽再生音量",
                            tint = AntiqueGold,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "音楽再生音量（環境音・BGM）",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "端末マスター音量を変えずに本アプリの音楽だけ微調整",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        val percent = (musicVolume * 100).roundToInt()
                        Text(
                            text = when {
                                percent == 0 -> "0% (消音)"
                                percent == 100 -> "100% (通常)"
                                else -> "$percent%"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AntiqueGold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Slider(
                        value = musicVolume,
                        onValueChange = onMusicVolumeChange,
                        valueRange = 0f..1f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_music_volume_slider"),
                        colors = SliderDefaults.colors(
                            thumbColor = AntiqueGold,
                            activeTrackColor = AntiqueGold,
                            inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                        )
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Preset buttons for quick selection
                    Text(
                        text = "ワンタッププリセット:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val presets = listOf(
                            Pair("消音 0%", 0.0f),
                            Pair("就寝 10%", 0.10f),
                            Pair("環境音 25%", 0.25f),
                            Pair("作業BGM 50%", 0.50f),
                            Pair("通常 100%", 1.0f)
                        )
                        for ((label, vol) in presets) {
                            val isSelected = kotlin.math.abs(musicVolume - vol) < 0.03f
                            FilterChip(
                                selected = isSelected,
                                onClick = { onMusicVolumeChange(vol) },
                                label = { Text(label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AntiqueGold,
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "※ 本設定はアプリ内の楽曲音量のみを減衰させます。着信音、アラーム、他の動画・アプリの音量には影響しません。",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Bluetooth,
                                    contentDescription = null,
                                    tint = AntiqueGold,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Bluetooth切断時に停止",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Bluetoothイヤホンやカーオーディオ等の切断時に自動で再生を停止し、端末スピーカーからの意図しない音漏れを防ぎます",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = pauseOnBluetoothDisconnect,
                            onCheckedChange = { onTogglePauseOnBluetoothDisconnect() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = AntiqueGold,
                                checkedTrackColor = AntiqueGold.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.testTag("bluetooth_disconnect_pause_switch")
                        )
                    }
                }
            }
        }

        // Section 1: Google Drive Sync & Root Selection
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = AntiqueGold,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Google ドライブ同期 (Drive API v3)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Root selector
                    Text(
                        text = "探索ルート:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DriveRoot.entries.forEach { root ->
                            FilterChip(
                                selected = driveRoot == root,
                                onClick = { onSelectDriveRoot(root) },
                                label = { Text(root.displayNameJa) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AntiqueGold.copy(alpha = 0.25f),
                                    selectedLabelColor = AntiqueGold
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "※「パソコン」ルートはGoogle Drive APIの制約と端末同期仕様が複雑なため安全に除外しています。Mac上の音楽はマイドライブまたは共有ドライブをご利用ください。",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Rules Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(10.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = AntiqueGold,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "スキャン仕様・ダウンロードルール",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AntiqueGold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "• 対象形式: mp3, m4a, aac, flac, ogg（wavは除外）\n• 音楽ファイルが入っているフォルダのみ階層付きで表示\n• 音源ダウンロードは各フォルダまたは曲単位で明示的に実行\n• 未接続時はローカルキャッシュのみ表示",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    if (syncStatusMessage != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(AntiqueGold.copy(alpha = 0.12f))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = syncStatusMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = AntiqueGold,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val isTokenExpired = !hasDriveToken ||
                            syncStatusMessage?.contains("再ログイン") == true

                    if (isTokenExpired) {
                        Button(
                            onClick = { showGoogleAuthDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = AntiqueGold),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("connect_google_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Googleアカウントで接続 (ワンタップ)",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = AntiqueGold,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Googleアカウント連携中（同期時自動再取得有効）",
                                    fontSize = 11.sp,
                                    color = AntiqueGold,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onSyncDrive,
                                    enabled = !isSyncing,
                                    colors = ButtonDefaults.buttonColors(containerColor = AntiqueGold),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("sync_drive_button")
                                ) {
                                    if (isSyncing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("同期中...", color = MaterialTheme.colorScheme.onPrimary, fontSize = 13.sp)
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Driveを同期", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }

                                OutlinedButton(
                                    onClick = { onSaveDriveToken(null) },
                                    modifier = Modifier.testTag("disconnect_google_button")
                                ) {
                                    Text("接続解除", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 2: Wi-Fi & Cache Protection
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = null,
                            tint = AntiqueGold,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "通信量保護 & キャッシュ設定",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Wi-Fi接続時のみキャッシュ保存",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "4G/5Gモバイル通信でのダウンロードパケット消費を防止します",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isWifiOnly,
                            onCheckedChange = { onToggleWifiOnly() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = AntiqueGold,
                                checkedTrackColor = AntiqueGold.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.testTag("wifi_only_switch")
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.NetworkCheck,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "現在の接続状態: ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = connectionName,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = AntiqueGold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.SdStorage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "保存済みキャッシュ容量: ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = cacheSize,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onCacheAll,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("cache_all_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = AntiqueGold, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("全曲キャッシュ", color = AntiqueGold, fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = onClearCache,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("clear_cache_button")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("キャッシュ削除", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Section 3: Edited Folders List (For Mac sync back!)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = WineBurgundy,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Macへ戻す用 編集済みフォルダ一覧",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "本アプリでID3タグを編集したファイルが属するフォルダです。Mac上の元ライブラリに戻す対象フォルダを一目で確認できます。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (editedFolders.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "編集済みのフォルダはありません（すべてMacと同期済）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            editedFolders.forEach { folderInfo ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = AntiqueGold,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = folderInfo.folderPath,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "編集曲数: ${folderInfo.editedTrackCount} 曲",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = WineBurgundy,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onCopyEditedFolders,
                                    colors = ButtonDefaults.buttonColors(containerColor = AntiqueGold),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("copy_edited_folders_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("フォルダ名をコピー", color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp)
                                }

                                OutlinedButton(
                                    onClick = onResetEditedList,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("reset_edited_list_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Done,
                                        contentDescription = null,
                                        tint = AntiqueGold,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Mac転送完了(リセット)", color = AntiqueGold, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(100.dp))
        }
    }

    // Google Drive Token Dialog
    if (showTokenDialog) {
        var tokenInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showTokenDialog = false },
            title = {
                Text(
                    text = "Google Drive API 認証トークン設定",
                    style = MaterialTheme.typography.titleLarge,
                    color = AntiqueGold,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "プロジェクト「${GoogleDriveClient.PROJECT_ID}」のOAuth Access Token（Bearerトークン）を入力してください。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        label = { Text("OAuth Access Token") },
                        placeholder = { Text("ya29....") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "※トークン設定後、「Driveを同期」を押すと音楽ファイル（mp3, m4a, aac, flac, ogg / wav除外）を含むフォルダが階層付きで読み込まれます。",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (tokenInput.isNotBlank()) {
                            onSaveDriveToken(tokenInput)
                        }
                        showTokenDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AntiqueGold)
                ) {
                    Text("トークンを保存", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showTokenDialog = false }) {
                    Text("閉じる")
                }
            }
        )
    }

    if (showGoogleAuthDialog) {
        com.example.ui.components.GoogleAuthDialog(
            onDismiss = { showGoogleAuthDialog = false },
            onTokenReceived = { token ->
                onSaveDriveToken(token)
                onSyncDrive()
            }
        )
    }
}
