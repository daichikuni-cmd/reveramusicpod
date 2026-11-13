package com.example.ui.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.drive.DriveFolderGroup
import com.example.data.drive.DriveRoot
import com.example.data.drive.GoogleDriveClient
import com.example.data.model.EditedFolderInfo
import com.example.data.model.Track
import com.example.data.repository.MusicRepository
import com.example.util.NetworkUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SyncViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    val repository = MusicRepository(application, db.trackDao())

    val editedFolders: StateFlow<List<EditedFolderInfo>> = repository.editedFolders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val editedTracks: StateFlow<List<Track>> = repository.editedTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _isClearingCache = MutableStateFlow(false)
    val isClearingCache: StateFlow<Boolean> = _isClearingCache.asStateFlow()

    private val _syncStatusMessage = MutableStateFlow<String?>("未同期（Googleドライブ未接続時はローカルキャッシュのみ表示）")
    val syncStatusMessage: StateFlow<String?> = _syncStatusMessage.asStateFlow()

    private val _isWifiOnlyEnabled = MutableStateFlow(true)
    val isWifiOnlyEnabled: StateFlow<Boolean> = _isWifiOnlyEnabled.asStateFlow()

    private val _isWifiConnected = MutableStateFlow(false)
    val isWifiConnected: StateFlow<Boolean> = _isWifiConnected.asStateFlow()

    private val _connectionTypeName = MutableStateFlow("確認中")
    val connectionTypeName: StateFlow<String> = _connectionTypeName.asStateFlow()

    private val _cacheSizeFormatted = MutableStateFlow("0.0 MB")
    val cacheSizeFormatted: StateFlow<String> = _cacheSizeFormatted.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _hasDriveToken = MutableStateFlow(GoogleDriveClient.getSavedAccessToken(application) != null)
    val hasDriveToken: StateFlow<Boolean> = _hasDriveToken.asStateFlow()

    private val _driveRoot = MutableStateFlow(GoogleDriveClient.getSavedDriveRoot(application))
    val driveRoot: StateFlow<DriveRoot> = _driveRoot.asStateFlow()

    private val _candidateFolders = MutableStateFlow<List<DriveFolderGroup>>(emptyList())
    val candidateFolders: StateFlow<List<DriveFolderGroup>> = _candidateFolders.asStateFlow()

    init {
        refreshNetworkStatus()
        refreshCacheSize()
    }

    fun refreshNetworkStatus() {
        val app = getApplication<Application>()
        _isWifiConnected.value = NetworkUtils.isWifiConnected(app)
        _connectionTypeName.value = NetworkUtils.getConnectionTypeName(app)
        _hasDriveToken.value = GoogleDriveClient.getSavedAccessToken(app) != null
    }

    fun saveDriveToken(token: String?) {
        val app = getApplication<Application>()
        val trimmed = token?.trim()?.ifEmpty { null }
        GoogleDriveClient.saveAccessToken(app, trimmed)
        _hasDriveToken.value = trimmed != null
        _snackbarMessage.value = if (trimmed != null) "Google Driveアクセストークンを保存しました" else "アクセストークンを解除しました"
    }

    fun setDriveRoot(root: DriveRoot) {
        val app = getApplication<Application>()
        GoogleDriveClient.saveDriveRoot(app, root)
        _driveRoot.value = root
        _snackbarMessage.value = "探索ルートを「${root.displayNameJa}」に変更しました"
    }

    fun refreshCacheSize() {
        viewModelScope.launch {
            _cacheSizeFormatted.value = repository.getCacheSizeFormatted()
        }
    }

    fun toggleWifiOnly() {
        _isWifiOnlyEnabled.value = !_isWifiOnlyEnabled.value
    }

    private var syncJob: Job? = null

    /**
     * 進行中のGoogleドライブ同期を中止
     */
    fun stopSync() {
        if (syncJob?.isActive == true || _isSyncing.value) {
            syncJob?.cancel()
            syncJob = null
            _isSyncing.value = false
            _syncStatusMessage.value = "同期を中止しました"
            _snackbarMessage.value = "同期を停止しました"
        }
    }

    /**
     * Googleドライブ Changes APIを利用した高速差分同期
     * - 前回同期からの変更差分のみを即座に検知・更新・削除
     */
    fun syncIncrementalWithGoogleDrive() {
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            _isSyncing.value = true
            _syncStatusMessage.value = "Googleドライブの差分を確認中..."
            try {
                val result = repository.syncIncrementalGoogleDrive()
                if (result.errorMessage != null) {
                    if (result.errorMessage.contains("有効期限") || result.errorMessage.contains("未接続")) {
                        _hasDriveToken.value = false
                    }
                    _syncStatusMessage.value = result.errorMessage
                    _snackbarMessage.value = result.errorMessage
                } else if (!result.hasChanges) {
                    _syncStatusMessage.value = "差分同期完了: 変更はありません（最新状態です）"
                    _snackbarMessage.value = "Googleドライブに変更はありません（最新状態です）"
                } else {
                    val msg = buildString {
                        append("差分同期完了: ")
                        val parts = mutableListOf<String>()
                        if (result.addedTracks > 0) parts.add("追加 ${result.addedTracks}件")
                        if (result.updatedTracks > 0) parts.add("更新 ${result.updatedTracks}件")
                        if (result.deletedTracks > 0) parts.add("削除 ${result.deletedTracks}件")
                        append(parts.joinToString(", "))
                    }
                    _syncStatusMessage.value = msg
                    _snackbarMessage.value = msg
                }
            } catch (e: CancellationException) {
                _syncStatusMessage.value = "同期を中止しました"
                _snackbarMessage.value = "同期を停止しました"
            } catch (e: Exception) {
                _syncStatusMessage.value = "差分同期エラー: ${e.message}"
                _snackbarMessage.value = "差分同期に失敗しました"
            } finally {
                _isSyncing.value = false
                syncJob = null
            }
        }
    }

    /**
     * Googleドライブスキャン・同期
     * - 音楽ファイル（mp3, m4a, aac, flac, ogg / wav除外）のみ探索
     * - 音楽ファイルを含むフォルダのみ階層付きで一覧化
     */
    fun syncWithGoogleDrive() {
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            _isSyncing.value = true
            _syncStatusMessage.value = "Googleドライブ（${_driveRoot.value.displayNameJa}）を検索中..."
            try {
                val tokenRes = GoogleDriveClient.ensureValidAccessToken(getApplication())
                var token = tokenRes.getOrNull()
                if (token.isNullOrBlank()) {
                    _hasDriveToken.value = false
                    _syncStatusMessage.value = "Googleドライブ未接続です。「設定」タブで「Googleアカウントで接続」を行ってください。"
                    _snackbarMessage.value = "Googleアカウントとの連携が必要です"
                    return@launch
                }
                _hasDriveToken.value = true

                var folderRes = GoogleDriveClient.fetchAudioFolders(getApplication(), token, _driveRoot.value)
                if (folderRes.isFailure) {
                    val err = folderRes.exceptionOrNull()?.message ?: "Drive検索エラー"
                    if (err.contains("401") || err.contains("UNAUTHENTICATED") || err.contains("authError")) {
                        // トークン期限切れ時はバックグラウンドで強制更新して1度リトライ
                        val refreshed = GoogleDriveClient.ensureValidAccessToken(getApplication(), forceRefresh = true).getOrNull()
                        if (!refreshed.isNullOrBlank()) {
                            token = refreshed
                            val retryRes = GoogleDriveClient.fetchAudioFolders(getApplication(), token, _driveRoot.value)
                            if (retryRes.isSuccess) {
                                folderRes = retryRes
                            }
                        }
                    }
                }

                if (folderRes.isFailure) {
                    val err = folderRes.exceptionOrNull()?.message ?: "Drive検索エラー"
                    if (err.contains("401") || err.contains("UNAUTHENTICATED") || err.contains("authError")) {
                        GoogleDriveClient.saveAccessToken(getApplication(), null)
                        _hasDriveToken.value = false
                        _syncStatusMessage.value = "Googleアカウントの再ログインが必要です。「設定」の「Googleアカウントで接続」を押してください。"
                        _snackbarMessage.value = "再ログインが必要です"
                        return@launch
                    }
                    _syncStatusMessage.value = "同期エラー: $err"
                    _snackbarMessage.value = "Googleドライブの検索に失敗しました"
                    return@launch
                }

                val groups = folderRes.getOrThrow()
                _candidateFolders.value = groups

                val syncResult = repository.syncGoogleDrive()
                if (syncResult.errorMessage != null) {
                    if (syncResult.errorMessage.contains("有効期限")) {
                        _hasDriveToken.value = false
                    }
                    _syncStatusMessage.value = syncResult.errorMessage
                    _snackbarMessage.value = syncResult.errorMessage
                } else if (groups.isEmpty()) {
                    _syncStatusMessage.value = "同期完了: 音楽ファイル（mp3, m4a, aac, flac, ogg）は見つかりませんでした。"
                    _snackbarMessage.value = "Googleドライブに対象の音楽ファイルが見つかりませんでした"
                } else {
                    val totalTracks = groups.sumOf { it.tracks.size }
                    _syncStatusMessage.value = "同期完了: 音楽フォルダ ${groups.size}件, 楽曲 ${totalTracks}件取得"
                    _snackbarMessage.value = "Googleドライブから音楽フォルダ ${groups.size}件（${totalTracks}曲）を取得しました"
                }
            } catch (e: CancellationException) {
                _syncStatusMessage.value = "同期を中止しました"
                _snackbarMessage.value = "同期を停止しました"
            } catch (e: Exception) {
                _syncStatusMessage.value = "同期エラー: ${e.message}"
                _snackbarMessage.value = "同期に失敗しました"
            } finally {
                _isSyncing.value = false
                syncJob = null
            }
        }
    }

    /**
     * フォルダ単位で明示的に全曲キャッシュダウンロード
     */
    fun downloadFolder(folderGroup: DriveFolderGroup) {
        viewModelScope.launch {
            refreshNetworkStatus()
            val force = !_isWifiOnlyEnabled.value
            var successCount = 0
            for (track in folderGroup.tracks) {
                val res = repository.cacheTrack(track, forceCellular = force)
                if (res.isSuccess) successCount++
            }
            refreshCacheSize()
            _snackbarMessage.value = "「${folderGroup.folderName}」の ${successCount}曲を保存しました"
        }
    }

    /**
     * 明示的な楽曲ダウンロード
     */
    fun downloadSingleTrack(track: Track) {
        viewModelScope.launch {
            refreshNetworkStatus()
            val force = !_isWifiOnlyEnabled.value
            val res = repository.cacheTrack(track, forceCellular = force)
            res.onSuccess {
                refreshCacheSize()
                _snackbarMessage.value = "「${track.title}」をキャッシュしました"
            }.onFailure { e ->
                _snackbarMessage.value = e.message ?: "ダウンロードに失敗しました"
            }
        }
    }

    fun cacheAllLibrary(forceCellular: Boolean = false) {
        viewModelScope.launch {
            refreshNetworkStatus()
            val res = repository.cacheAllTracks(forceCellular = forceCellular || !_isWifiOnlyEnabled.value)
            res.onSuccess { count ->
                refreshCacheSize()
                _snackbarMessage.value = "$count 曲のキャッシュを保存しました"
            }.onFailure { e ->
                _snackbarMessage.value = e.message ?: "Wi-Fi接続が必要です"
            }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            _isClearingCache.value = true
            try {
                repository.clearCache()
                refreshCacheSize()
                _snackbarMessage.value = "キャッシュをクリアしました"
            } finally {
                _isClearingCache.value = false
            }
        }
    }

    fun copyEditedFoldersToClipboard() {
        val list = editedFolders.value
        if (list.isEmpty()) {
            _snackbarMessage.value = "編集済みのフォルダはありません"
            return
        }

        val text = list.joinToString("\n") { it.folderPath }
        val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Mac戻し用編集済みフォルダ一覧", text)
        clipboard.setPrimaryClip(clip)
        _snackbarMessage.value = "${list.size} 件のフォルダパスをクリップボードにコピーしました"
    }

    fun clearEditedList() {
        viewModelScope.launch {
            repository.clearEditedFlags()
            _snackbarMessage.value = "Mac同期済みとして編集リストをリセットしました"
        }
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }
}
