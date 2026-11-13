package com.example.ui

import android.app.Application
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppInfo
import com.example.data.getInstalledApps
import com.example.data.LauncherDatabase
import com.example.data.drive.DriveFolderGroup
import com.example.data.drive.DriveRoot
import com.example.data.drive.GoogleDriveClient
import com.example.data.model.Track
import com.example.data.repository.MusicRepository
import com.example.player.MusicPlayerManager
import com.example.util.CrashRecoveryManager
import com.example.util.SafFolderScanner
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * 画面タブ定義（Play / Storage / Settings）
 */
enum class MusicTab {
    PLAY,
    HUB,
    STORAGE,
    SETTINGS
}

/**
 * トラック情報モデル
 */
@JsonClass(generateAdapter = true)
data class TrackInfo(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0L,
    val uri: String = "",
    val albumArtUri: String? = null,
    val isDriveSource: Boolean = false,
    val folderUriString: String? = null,
    val isDownloaded: Boolean = false,
    val composer: String = "",
    val genre: String = "",
    val isCompilation: Boolean = false,
    val grouping: String = "",
    val folderName: String = "",
    val musicalForm: String = ""
)

/**
 * 登録済みフォルダ情報モデル
 */
@JsonClass(generateAdapter = true)
data class MusicFolder(
    val uriString: String,
    val displayName: String,
    val trackCount: Int = 0,
    val addedTimeMs: Long = System.currentTimeMillis()
)

/**
 * 再生状態モデル
 */
data class PlaybackUiState(
    val currentTrack: TrackInfo? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isShuffle: Boolean = false,
    val repeatMode: Int = 0, // 0: Off, 1: All, 2: One
    val currentPlaylist: List<TrackInfo> = emptyList(),
    val currentIndex: Int = -1,
    val artworkBytes: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as PlaybackUiState
        if (currentTrack != other.currentTrack) return false
        if (isPlaying != other.isPlaying) return false
        if (currentPositionMs != other.currentPositionMs) return false
        if (durationMs != other.durationMs) return false
        if (isShuffle != other.isShuffle) return false
        if (repeatMode != other.repeatMode) return false
        if (currentPlaylist != other.currentPlaylist) return false
        if (currentIndex != other.currentIndex) return false
        if (artworkBytes != null) {
            if (other.artworkBytes == null) return false
            if (!artworkBytes.contentEquals(other.artworkBytes)) return false
        } else if (other.artworkBytes != null) return false
        return true
    }

    override fun hashCode(): Int {
        var result = currentTrack?.hashCode() ?: 0
        result = 31 * result + isPlaying.hashCode()
        result = 31 * result + currentPositionMs.hashCode()
        result = 31 * result + durationMs.hashCode()
        result = 31 * result + isShuffle.hashCode()
        result = 31 * result + repeatMode
        result = 31 * result + currentPlaylist.hashCode()
        result = 31 * result + currentIndex
        result = 31 * result + (artworkBytes?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * 音楽専用ランチャーのコアViewModel。
 * ホームボタン挙動、SAFストレージフォルダ管理、Google Drive同期、ExoPlayer再生を統合。
 */
class MusicLauncherViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val SCREEN_RETENTION_TIMEOUT_MS = 5 * 60 * 1000L
        private const val PREFS_NAME = "musicpot_launcher_prefs"
        private const val KEY_LAST_TAB = "key_last_selected_tab"
        private const val KEY_HAPTICS_ENABLED = "key_haptics_enabled"
        private const val KEY_FOLDERS_JSON = "key_saved_folders_json"
        private const val KEY_LOCAL_TRACKS_JSON = "key_saved_local_tracks_json"
        const val KEY_MONO_AUDIO_ENABLED = "key_mono_audio_enabled"
        const val KEY_DISABLED_AUDIO_EXTENSIONS = "key_disabled_audio_extensions"
        val SUPPORTED_AUDIO_EXTENSIONS = listOf("mp3", "m4a", "flac", "wav", "aac", "ogg", "opus", "3gp")
    }

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val database = LauncherDatabase.getDatabase(application)
    val musicRepository = MusicRepository(application)

    // ExoPlayer再生コア
    private val playerManager = MusicPlayerManager(application)
    val playbackState: StateFlow<PlaybackUiState> = playerManager.playbackState

    // 現在選択されているタブ (デフォルトは PLAY)
    private val _currentTab = MutableStateFlow(
        MusicTab.valueOf(prefs.getString(KEY_LAST_TAB, MusicTab.PLAY.name) ?: MusicTab.PLAY.name)
    )
    val currentTab: StateFlow<MusicTab> = _currentTab.asStateFlow()

    // 登録フォルダ一覧（ローカル）
    private val _folders = MutableStateFlow<List<MusicFolder>>(emptyList())
    val folders: StateFlow<List<MusicFolder>> = _folders.asStateFlow()

    // ローカルスキャン済みトラック一覧
    private val _localTracks = MutableStateFlow<List<TrackInfo>>(emptyList())

    // Google Drive同期トラック一覧
    private val _driveTracks = MutableStateFlow<List<Track>>(emptyList())
    val driveTracks: StateFlow<List<Track>> = _driveTracks.asStateFlow()

    private val _driveFolderGroups = MutableStateFlow<List<DriveFolderGroup>>(emptyList())
    val driveFolderGroups: StateFlow<List<DriveFolderGroup>> = _driveFolderGroups.asStateFlow()

    // 統合トラック一覧（ローカル + Google Drive）
    private val _allTracks = MutableStateFlow<List<TrackInfo>>(emptyList())
    val tracks: StateFlow<List<TrackInfo>> = _allTracks.asStateFlow()

    // 最近再生されたトラック一覧
    private val _recentTracks = MutableStateFlow<List<TrackInfo>>(emptyList())
    val recentTracks: StateFlow<List<TrackInfo>> = _recentTracks.asStateFlow()

    // Google Drive 接続・同期状態
    private val _isDriveConnected = MutableStateFlow(GoogleDriveClient.getSavedAccessToken(application) != null)
    val isDriveConnected: StateFlow<Boolean> = _isDriveConnected.asStateFlow()

    private val _isDriveSyncing = MutableStateFlow(false)
    val isDriveSyncing: StateFlow<Boolean> = _isDriveSyncing.asStateFlow()

    private val _driveSyncStatus = MutableStateFlow("")
    val driveSyncStatus: StateFlow<String> = _driveSyncStatus.asStateFlow()

    private val _driveRoot = MutableStateFlow(GoogleDriveClient.getSavedDriveRoot(application))
    val driveRoot: StateFlow<DriveRoot> = _driveRoot.asStateFlow()

    private val _isWifiOnly = MutableStateFlow(musicRepository.isWifiOnlyDownload())
    val isWifiOnly: StateFlow<Boolean> = _isWifiOnly.asStateFlow()

    private val _connectionTypeName = MutableStateFlow(com.example.util.NetworkUtils.getConnectionTypeName(application))
    val connectionTypeName: StateFlow<String> = _connectionTypeName.asStateFlow()

    private val _musicVolume = MutableStateFlow(prefs.getFloat("key_music_volume", 1.0f))
    val musicVolume: StateFlow<Float> = _musicVolume.asStateFlow()

    private val _pauseOnBluetoothDisconnect = MutableStateFlow(prefs.getBoolean("key_pause_on_bt_disconnect", true))
    val pauseOnBluetoothDisconnect: StateFlow<Boolean> = _pauseOnBluetoothDisconnect.asStateFlow()

    private val _audioCacheSizeFormatted = MutableStateFlow("0 MB")
    val audioCacheSizeFormatted: StateFlow<String> = _audioCacheSizeFormatted.asStateFlow()

    private val _downloadingTrackId = MutableStateFlow<String?>(null)
    val downloadingTrackId: StateFlow<String?> = _downloadingTrackId.asStateFlow()

    // ローカルスキャン進行中フラグ & 検出曲数
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanCount = MutableStateFlow(0)
    val scanCount: StateFlow<Int> = _scanCount.asStateFlow()

    // 触覚フィードバック（ブラインド操作）設定
    private val _isHapticsEnabled = MutableStateFlow(prefs.getBoolean(KEY_HAPTICS_ENABLED, true))
    val isHapticsEnabled: StateFlow<Boolean> = _isHapticsEnabled.asStateFlow()

    private val _isSafeMode = MutableStateFlow(CrashRecoveryManager.isSafeModeActive(application))
    val isSafeMode: StateFlow<Boolean> = _isSafeMode.asStateFlow()

    // バッテリー最適化除外 (Dozeキル防止)
    private val _isBatteryOptimizationIgnored = MutableStateFlow(false)
    val isBatteryOptimizationIgnored: StateFlow<Boolean> = _isBatteryOptimizationIgnored.asStateFlow()

    // おやすみスリープタイマー & 画面減光 (Screen Dimming)
    private val _sleepTimerRemainingSeconds = MutableStateFlow<Int?>(null)
    val sleepTimerRemainingSeconds: StateFlow<Int?> = _sleepTimerRemainingSeconds.asStateFlow()

    private val _sleepTimerMode = MutableStateFlow<Int?>(null) // null: OFF, 15, 30, 45, 60, -1: 楽曲終了時
    val sleepTimerMode: StateFlow<Int?> = _sleepTimerMode.asStateFlow()

    private val _screenDimFactor = MutableStateFlow<Float>(1.0f)
    val screenDimFactor: StateFlow<Float> = _screenDimFactor.asStateFlow()

    private var sleepTimerJob: Job? = null

    // 再生音質（モノラル / ステレオ）
    private val _isMonoAudioEnabled = MutableStateFlow(false)
    val isMonoAudioEnabled: StateFlow<Boolean> = _isMonoAudioEnabled.asStateFlow()

    // 拡張子フィルター（録音・特定ファイル非表示設定）
    private val _disabledAudioExtensions = MutableStateFlow<Set<String>>(emptySet())
    val disabledAudioExtensions: StateFlow<Set<String>> = _disabledAudioExtensions.asStateFlow()

    // ホームランチャー用：インストール済みアプリ & 9個の配置アプリ
    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    private val _nineApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val nineApps: StateFlow<List<AppInfo>> = _nineApps.asStateFlow()

    private val KEY_NINE_APPS_CONFIG = "key_nine_apps_config"

    private var lastBackgroundTimeMs: Long = 0L

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val folderListAdapter = moshi.adapter<List<MusicFolder>>(
        Types.newParameterizedType(List::class.java, MusicFolder::class.java)
    )
    private val trackListAdapter = moshi.adapter<List<TrackInfo>>(
        Types.newParameterizedType(List::class.java, TrackInfo::class.java)
    )

    // Google Drive 楽曲キャッシュキュー & フォルダツリー状態
    val driveRawTracks: StateFlow<List<Track>> = _driveTracks.asStateFlow()

    private val _desiredCheckedIds = MutableStateFlow<Set<String>>(emptySet())
    val desiredCheckedIds: StateFlow<Set<String>> = _desiredCheckedIds.asStateFlow()

    private val _busyIds = MutableStateFlow<Set<String>>(emptySet())
    val busyIds: StateFlow<Set<String>> = _busyIds.asStateFlow()

    private val _expandedFolderPaths = MutableStateFlow<Set<String>>(emptySet())
    val expandedFolderPaths: StateFlow<Set<String>> = _expandedFolderPaths.asStateFlow()
    private var hasInitializedExpandedPaths = false
    private var hasInitializedDesiredCheckedIds = false

    val hasIncompleteDownloads: StateFlow<Boolean> = combine(_driveTracks, _desiredCheckedIds) { tracks, desired ->
        tracks.any { it.id in desired && !it.isCached && !it.isDownloaded }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val activeCacheJobs = mutableListOf<Job>()

    init {
        val savedExpanded = prefs.getStringSet("expanded_folder_paths_v2", null)
        if (savedExpanded != null) {
            _expandedFolderPaths.value = savedExpanded
            hasInitializedExpandedPaths = true
        }

        viewModelScope.launch {
            CrashRecoveryManager.onAppHealthy(getApplication())
            loadPersistedData()
            refreshCacheSize()
            loadInstalledApps()
            refreshBatteryOptimizationStatus()
        }

        viewModelScope.launch {
            _driveTracks.collect { tracks ->
                val cachedIds = tracks.filter { it.isCached || it.isDownloaded }.map { it.id }.toSet()
                if (!hasInitializedDesiredCheckedIds) {
                    _desiredCheckedIds.value = cachedIds
                    hasInitializedDesiredCheckedIds = true
                } else {
                    _desiredCheckedIds.value = _desiredCheckedIds.value + cachedIds
                }
            }
        }
    }

    private suspend fun loadPersistedData() = withContext(Dispatchers.IO) {
        try {
            // ローカルフォルダ
            val foldersJson = prefs.getString(KEY_FOLDERS_JSON, null)
            if (!foldersJson.isNullOrEmpty()) {
                val loadedFolders = folderListAdapter.fromJson(foldersJson) ?: emptyList()
                _folders.value = loadedFolders
            }

            // ローカルトラック
            val localJson = prefs.getString(KEY_LOCAL_TRACKS_JSON, null)
            if (!localJson.isNullOrEmpty()) {
                val loadedTracks = trackListAdapter.fromJson(localJson) ?: emptyList()
                _localTracks.value = loadedTracks
            }

            // Google Driveトラック
            val loadedDriveTracks = musicRepository.loadSavedDriveTracks()
            _driveTracks.value = loadedDriveTracks

            // モノラル再生設定
            val isMono = prefs.getBoolean(KEY_MONO_AUDIO_ENABLED, false)
            _isMonoAudioEnabled.value = isMono
            playerManager.setMonoAudioEnabled(isMono)

            // 拡張子フィルター設定
            val disabledExts = prefs.getStringSet(KEY_DISABLED_AUDIO_EXTENSIONS, emptySet()) ?: emptySet()
            _disabledAudioExtensions.value = disabledExts.map { it.lowercase() }.toSet()

            updateAllTracks()
        } catch (e: Exception) {
            Log.e("MusicLauncherViewModel", "Failed to load persisted data", e)
        }
    }

    fun setMonoAudioEnabled(enabled: Boolean) {
        _isMonoAudioEnabled.value = enabled
        prefs.edit().putBoolean(KEY_MONO_AUDIO_ENABLED, enabled).apply()
        playerManager.setMonoAudioEnabled(enabled)
    }

    fun toggleAudioExtension(ext: String) {
        val lowerExt = ext.lowercase()
        val current = _disabledAudioExtensions.value.toMutableSet()
        if (current.contains(lowerExt)) {
            current.remove(lowerExt)
        } else {
            current.add(lowerExt)
        }
        _disabledAudioExtensions.value = current
        prefs.edit().putStringSet(KEY_DISABLED_AUDIO_EXTENSIONS, current).apply()
        updateAllTracks()
    }

    private fun updateAllTracks() {
        val disabledExts = _disabledAudioExtensions.value
        val driveAsTrackInfo = _driveTracks.value.map { it.toTrackInfo() }
        val combined = _localTracks.value + driveAsTrackInfo
        _allTracks.value = if (disabledExts.isEmpty()) {
            combined
        } else {
            combined.filter { track ->
                val ext = track.uri.substringAfterLast('.', "").lowercase()
                !disabledExts.contains(ext)
            }
        }
    }

    private suspend fun saveLocalPersistedData() = withContext(Dispatchers.IO) {
        try {
            val foldersJson = folderListAdapter.toJson(_folders.value)
            val tracksJson = trackListAdapter.toJson(_localTracks.value)
            prefs.edit()
                .putString(KEY_FOLDERS_JSON, foldersJson)
                .putString(KEY_LOCAL_TRACKS_JSON, tracksJson)
                .apply()
        } catch (e: Exception) {
            Log.e("MusicLauncherViewModel", "Failed to save persisted data", e)
        }
    }

    fun refreshCacheSize() {
        viewModelScope.launch(Dispatchers.IO) {
            val bytes = musicRepository.getAudioCacheSizeBytes()
            val mb = bytes.toDouble() / (1024 * 1024)
            _audioCacheSizeFormatted.value = String.format(Locale.US, "%.1f MB", mb)
        }
    }

    /**
     * SAFで選択されたフォルダを追加し、オーディオファイルをスキャン
     */
    fun addMusicFolder(treeUri: Uri) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            try {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                Log.w("MusicLauncherViewModel", "Failed to take persistable URI permission", e)
            }

            _isScanning.value = true
            _scanCount.value = 0

            val (folderName, scannedTracks) = SafFolderScanner.scanFolder(context, treeUri) { count ->
                _scanCount.value = count
            }

            val uriStr = treeUri.toString()
            val newFolder = MusicFolder(
                uriString = uriStr,
                displayName = folderName,
                trackCount = scannedTracks.size
            )

            val updatedFolders = _folders.value.filter { it.uriString != uriStr } + newFolder
            _folders.value = updatedFolders

            val tracksWithFolder = scannedTracks.map { it.copy(folderUriString = uriStr) }
            val otherTracks = _localTracks.value.filter { it.folderUriString != uriStr }
            _localTracks.value = otherTracks + tracksWithFolder

            updateAllTracks()
            saveLocalPersistedData()
            _isScanning.value = false
        }
    }

    fun removeMusicFolder(folder: MusicFolder) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            try {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(folder.uriString),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // ignore
            }

            _folders.value = _folders.value.filter { it.uriString != folder.uriString }
            _localTracks.value = _localTracks.value.filter { it.folderUriString != folder.uriString }
            updateAllTracks()
            saveLocalPersistedData()
        }
    }

    fun rescanFolder(folder: MusicFolder) {
        addMusicFolder(Uri.parse(folder.uriString))
    }

    // ==========================================
    // Google Drive 連携 & 同期ロジック
    // ==========================================

    fun connectDriveWithToken(token: String, root: DriveRoot) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            GoogleDriveClient.saveAccessToken(context, token)
            GoogleDriveClient.saveDriveRoot(context, root)
            _driveRoot.value = root
            _isDriveConnected.value = true
            _driveSyncStatus.value = "接続完了。初回同期を開始します..."
            syncGoogleDrive(forceFull = true)
        }
    }

    fun disconnectDrive() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            GoogleDriveClient.saveAccessToken(context, null)
            _isDriveConnected.value = false
            _driveTracks.value = emptyList()
            _driveFolderGroups.value = emptyList()
            _driveSyncStatus.value = "切断されました"
            musicRepository.clearSavedDriveTracks()
            updateAllTracks()
        }
    }

    fun setDriveRoot(root: DriveRoot) {
        _driveRoot.value = root
        GoogleDriveClient.saveDriveRoot(getApplication(), root)
        if (_isDriveConnected.value) {
            syncGoogleDrive(forceFull = true)
        }
    }

    fun setWifiOnly(enabled: Boolean) {
        _isWifiOnly.value = enabled
        musicRepository.setWifiOnlyDownload(enabled)
    }

    fun syncGoogleDrive(forceFull: Boolean = false) {
        if (_isDriveSyncing.value) return
        viewModelScope.launch {
            _isDriveSyncing.value = true
            _driveSyncStatus.value = "Google Driveと同期中..."

            val context = getApplication<Application>()
            val tokenRes = GoogleDriveClient.ensureValidAccessToken(context)
            if (tokenRes.isFailure) {
                val err = tokenRes.exceptionOrNull()?.message ?: "アクセストークンが無効です"
                _driveSyncStatus.value = "同期失敗: $err"
                _isDriveSyncing.value = false
                return@launch
            }

            val token = tokenRes.getOrThrow()
            val foldersRes = GoogleDriveClient.fetchAudioFolders(context, token, _driveRoot.value)

            if (foldersRes.isSuccess) {
                val groups = foldersRes.getOrThrow()
                _driveFolderGroups.value = groups
                val allDriveTracks = groups.flatMap { it.tracks }
                _driveTracks.value = allDriveTracks
                musicRepository.saveDriveTracks(allDriveTracks)
                updateAllTracks()
                _driveSyncStatus.value = "同期完了 (${allDriveTracks.size}曲 検出)"
            } else {
                val err = foldersRes.exceptionOrNull()?.message ?: "不明なエラー"
                _driveSyncStatus.value = "同期エラー: $err"
            }

            _isDriveSyncing.value = false
            refreshCacheSize()
        }
    }

    /**
     * Changes APIによる高速差分同期
     */
    fun syncIncrementalWithGoogleDrive() {
        if (_isDriveSyncing.value) return
        viewModelScope.launch {
            _isDriveSyncing.value = true
            _driveSyncStatus.value = "差分を確認中..."

            val res = musicRepository.syncIncrementalGoogleDrive()
            if (res.errorMessage != null) {
                _driveSyncStatus.value = res.errorMessage
            } else if (!res.hasChanges) {
                _driveSyncStatus.value = "最新状態です（変更なし）"
            } else {
                _driveSyncStatus.value = "差分同期完了 (追加${res.addedTracks}, 更新${res.updatedTracks}, 削除${res.deletedTracks})"
            }

            _driveTracks.value = musicRepository.loadSavedDriveTracks()
            updateAllTracks()
            _isDriveSyncing.value = false
            refreshCacheSize()
        }
    }

    /**
     * 全曲の一括キャッシュダウンロード
     */
    fun cacheAllLibrary() {
        viewModelScope.launch {
            Toast.makeText(getApplication(), "全曲キャッシュを開始しました...", Toast.LENGTH_SHORT).show()
            val res = musicRepository.cacheAllTracks()
            res.onSuccess { count ->
                Toast.makeText(getApplication(), "$count 曲のキャッシュを保存しました", Toast.LENGTH_SHORT).show()
                _driveTracks.value = musicRepository.loadSavedDriveTracks()
                updateAllTracks()
                refreshCacheSize()
            }.onFailure { e ->
                Toast.makeText(getApplication(), e.message ?: "キャッシュ失敗", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun setMusicVolume(vol: Float) {
        val clamped = vol.coerceIn(0f, 1f)
        _musicVolume.value = clamped
        prefs.edit().putFloat("key_music_volume", clamped).apply()
        playerManager.setVolume(clamped)
    }

    fun setPauseOnBluetoothDisconnect(enabled: Boolean) {
        _pauseOnBluetoothDisconnect.value = enabled
        prefs.edit().putBoolean("key_pause_on_bt_disconnect", enabled).apply()
    }

    fun downloadDriveTrack(track: Track, onComplete: ((Boolean) -> Unit)? = null) {
        if (_downloadingTrackId.value != null) return

        val context = getApplication<Application>()
        if (_isWifiOnly.value && !isWifiConnected(context)) {
            Toast.makeText(context, "Wi-Fi接続時のみダウンロードするように設定されています", Toast.LENGTH_SHORT).show()
            onComplete?.invoke(false)
            return
        }

        viewModelScope.launch {
            _downloadingTrackId.value = track.id
            val tokenRes = GoogleDriveClient.ensureValidAccessToken(context)
            if (tokenRes.isFailure) {
                Toast.makeText(context, "トークンの有効期限が切れています", Toast.LENGTH_SHORT).show()
                _downloadingTrackId.value = null
                onComplete?.invoke(false)
                return@launch
            }

            val token = tokenRes.getOrThrow()
            val dlRes = GoogleDriveClient.downloadTrackToCache(context, track, token)

            if (dlRes.isSuccess) {
                val file = dlRes.getOrThrow()
                val updatedTrack = track.copy(localFilePath = file.absolutePath, isDownloaded = true, isCached = true, lastCachedAt = System.currentTimeMillis())
                val updatedList = _driveTracks.value.map { if (it.id == track.id) updatedTrack else it }
                _driveTracks.value = updatedList
                musicRepository.saveDriveTracks(updatedList)
                updateAllTracks()
                refreshCacheSize()
                onComplete?.invoke(true)
            } else {
                val err = dlRes.exceptionOrNull()?.message ?: "ダウンロード失敗"
                Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                onComplete?.invoke(false)
            }

            _downloadingTrackId.value = null
        }
    }

    fun clearAudioCache() {
        stopActiveCacheOperations()
        _desiredCheckedIds.value = emptySet()
        viewModelScope.launch {
            musicRepository.clearAudioCache()
            _driveTracks.value = _driveTracks.value.map { it.copy(localFilePath = null, isDownloaded = false, isCached = false) }
            updateAllTracks()
            refreshCacheSize()
            Toast.makeText(getApplication(), "キャッシュを削除しました", Toast.LENGTH_SHORT).show()
        }
    }

    private fun trackJob(job: Job) {
        synchronized(activeCacheJobs) {
            activeCacheJobs.add(job)
        }
        job.invokeOnCompletion {
            synchronized(activeCacheJobs) {
                activeCacheJobs.remove(job)
            }
        }
    }

    fun stopActiveCacheOperations() {
        synchronized(activeCacheJobs) {
            activeCacheJobs.forEach { it.cancel() }
            activeCacheJobs.clear()
        }
        _busyIds.value = emptySet()
        _driveSyncStatus.value = "キャッシュ処理を一時停止しました"
    }

    fun toggleFolderExpanded(path: String) {
        val current = _expandedFolderPaths.value
        val updated = if (current.contains(path)) current - path else current + path
        _expandedFolderPaths.value = updated
        prefs.edit().putStringSet("expanded_folder_paths_v2", updated).apply()
    }

    fun initExpandedPathsIfNeeded(defaultPaths: Set<String>) {
        if (!hasInitializedExpandedPaths) {
            val saved = prefs.getStringSet("expanded_folder_paths_v2", null)
            val initial = saved ?: defaultPaths
            _expandedFolderPaths.value = initial
            hasInitializedExpandedPaths = true
            if (saved == null) {
                prefs.edit().putStringSet("expanded_folder_paths_v2", defaultPaths).apply()
            }
        }
    }

    fun toggleTrackCache(track: Track, forceCellular: Boolean = false) {
        if (track.folderPath.startsWith("端末")) {
            Toast.makeText(getApplication(), "端末内の楽曲は既にローカルに保存されています", Toast.LENGTH_SHORT).show()
            return
        }
        val shouldUncache = track.isCached || track.isDownloaded || _desiredCheckedIds.value.contains(track.id)
        val job = viewModelScope.launch {
            _busyIds.value = _busyIds.value + track.id
            try {
                if (shouldUncache) {
                    _desiredCheckedIds.value = _desiredCheckedIds.value - track.id
                    val res = musicRepository.uncacheTrack(track.id)
                    res.onSuccess {
                        _driveSyncStatus.value = "「${track.title}」のキャッシュを削除しました"
                        reloadDriveTracks()
                    }.onFailure {
                        _driveSyncStatus.value = "キャッシュ削除に失敗しました: ${it.message}"
                    }
                } else {
                    _desiredCheckedIds.value = _desiredCheckedIds.value + track.id
                    val res = musicRepository.cacheTrack(track, forceCellular)
                    res.onSuccess {
                        _driveSyncStatus.value = "「${track.title}」をキャッシュ保存しました"
                        reloadDriveTracks()
                    }.onFailure { e ->
                        _driveSyncStatus.value = e.message ?: "キャッシュに失敗しました"
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Cancelled
            } finally {
                _busyIds.value = _busyIds.value - track.id
                refreshCacheSize()
            }
        }
        trackJob(job)
    }

    fun toggleFolderCache(
        folderPathPrefix: String,
        currentlyChecked: Boolean? = null,
        forceCellular: Boolean = false
    ) {
        if (folderPathPrefix.startsWith("端末")) {
            Toast.makeText(getApplication(), "端末内のフォルダは既にローカルに保存されています", Toast.LENGTH_SHORT).show()
            return
        }
        val all = _driveTracks.value
        val folderTracks = all.filter {
            it.folderPath == folderPathPrefix || it.folderPath.startsWith("$folderPathPrefix/")
        }
        val folderTrackIds = folderTracks.map { it.id }.toSet()
        val hasAnyCached = folderTracks.any { it.isCached || it.isDownloaded }
        val hasAnyChecked = folderTracks.any { it.id in _desiredCheckedIds.value }
        val shouldUncache = currentlyChecked ?: (hasAnyCached || hasAnyChecked)

        val job = viewModelScope.launch {
            _busyIds.value = _busyIds.value + folderPathPrefix
            try {
                if (shouldUncache) {
                    _desiredCheckedIds.value = _desiredCheckedIds.value - folderTrackIds
                    val res = musicRepository.uncacheFolder(folderPathPrefix)
                    res.onSuccess { count ->
                        _driveSyncStatus.value = "フォルダから $count 曲のキャッシュを削除しました"
                        reloadDriveTracks()
                    }.onFailure {
                        _driveSyncStatus.value = "キャッシュ削除に失敗しました: ${it.message}"
                    }
                } else {
                    _desiredCheckedIds.value = _desiredCheckedIds.value + folderTrackIds
                    val res = musicRepository.cacheFolder(folderPathPrefix, forceCellular)
                    res.onSuccess { count ->
                        _driveSyncStatus.value = "フォルダの $count 曲をキャッシュ保存しました"
                        reloadDriveTracks()
                    }.onFailure { e ->
                        _driveSyncStatus.value = e.message ?: "キャッシュに失敗しました"
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Cancelled
            } finally {
                _busyIds.value = _busyIds.value - folderPathPrefix
                refreshCacheSize()
            }
        }
        trackJob(job)
    }

    fun resumeIncompleteDownloads(forceCellular: Boolean = false): Int {
        val all = _driveTracks.value
        val desired = _desiredCheckedIds.value
        val tracksToResume = all.filter { it.id in desired && (!it.isCached && !it.isDownloaded) }
        if (tracksToResume.isEmpty()) return 0

        val job = viewModelScope.launch {
            _driveSyncStatus.value = "未完了の ${tracksToResume.size}曲のダウンロードを再開しました"
            try {
                for (track in tracksToResume) {
                    _busyIds.value = _busyIds.value + track.id
                    try {
                        musicRepository.cacheTrack(track, forceCellular)
                    } finally {
                        _busyIds.value = _busyIds.value - track.id
                    }
                }
                reloadDriveTracks()
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Cancelled
            } finally {
                refreshCacheSize()
            }
        }
        trackJob(job)
        return tracksToResume.size
    }

    fun cacheTrack(track: Track, forceCellular: Boolean = false) {
        toggleTrackCache(track, forceCellular)
    }

    fun uncacheTrack(track: Track) {
        viewModelScope.launch {
            _busyIds.value = _busyIds.value + track.id
            try {
                musicRepository.uncacheTrack(track.id)
                reloadDriveTracks()
            } finally {
                _busyIds.value = _busyIds.value - track.id
                refreshCacheSize()
            }
        }
    }

    fun playFolderRecursive(tracks: List<Track>, startingTrack: Track? = null) {
        val trackInfos = tracks.map { it.toTrackInfo() }
        if (trackInfos.isEmpty()) return
        val target = startingTrack?.toTrackInfo() ?: trackInfos.first()
        playTrack(target, trackInfos)
    }

    private suspend fun reloadDriveTracks() {
        val loadedDriveTracks = musicRepository.loadSavedDriveTracks()
        _driveTracks.value = loadedDriveTracks
        updateAllTracks()
        val cachedIds = loadedDriveTracks.filter { it.isCached || it.isDownloaded }.map { it.id }.toSet()
        _desiredCheckedIds.value = _desiredCheckedIds.value + cachedIds
    }

    private fun isWifiConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    // ==========================================
    // 音楽再生コントロール
    // ==========================================

    fun playTrack(track: TrackInfo, customPlaylist: List<TrackInfo>? = null) {
        val playlist = customPlaylist ?: _allTracks.value
        _recentTracks.value = listOf(track) + _recentTracks.value.filter { it.id != track.id }.take(29)
        if (track.isDriveSource && !track.isDownloaded) {
            // 未ダウンロードのGoogle Drive曲の場合、キャッシュにダウンロードしてから再生
            val driveTrack = _driveTracks.value.find { it.id == track.id }
            if (driveTrack != null) {
                Toast.makeText(getApplication(), "Driveから楽曲を準備中...", Toast.LENGTH_SHORT).show()
                downloadDriveTrack(driveTrack) { success ->
                    if (success) {
                        val downloaded = _driveTracks.value.find { it.id == track.id }
                        val downloadedInfo = downloaded?.toTrackInfo() ?: track
                        playerManager.playTrack(downloadedInfo, playlist)
                    }
                }
                return
            }
        }
        playerManager.playTrack(track, playlist)
    }

    fun playAlbum(albumTracks: List<TrackInfo>, startingTrack: TrackInfo? = null, shuffle: Boolean = false) {
        if (albumTracks.isEmpty()) return
        val playlist = if (shuffle) albumTracks.shuffled() else albumTracks
        val target = startingTrack ?: playlist.firstOrNull() ?: return
        playTrack(target, playlist)
    }

    fun playArtist(artistTracks: List<TrackInfo>, startingTrack: TrackInfo? = null, shuffle: Boolean = false) {
        if (artistTracks.isEmpty()) return
        val playlist = if (shuffle) artistTracks.shuffled() else artistTracks
        val target = startingTrack ?: playlist.firstOrNull() ?: return
        playTrack(target, playlist)
    }

    fun playComposer(composerTracks: List<TrackInfo>, startingTrack: TrackInfo? = null, shuffle: Boolean = false) {
        if (composerTracks.isEmpty()) return
        val playlist = if (shuffle) composerTracks.shuffled() else composerTracks
        val target = startingTrack ?: playlist.firstOrNull() ?: return
        playTrack(target, playlist)
    }

    fun playGenre(genreTracks: List<TrackInfo>, startingTrack: TrackInfo? = null, shuffle: Boolean = false) {
        if (genreTracks.isEmpty()) return
        val playlist = if (shuffle) genreTracks.shuffled() else genreTracks
        val target = startingTrack ?: playlist.firstOrNull() ?: return
        playTrack(target, playlist)
    }

    fun playForm(formTracks: List<TrackInfo>, startingTrack: TrackInfo? = null, shuffle: Boolean = false) {
        if (formTracks.isEmpty()) return
        val playlist = if (shuffle) formTracks.shuffled() else formTracks
        val target = startingTrack ?: playlist.firstOrNull() ?: return
        playTrack(target, playlist)
    }

    fun playAll(tracks: List<TrackInfo>, shuffle: Boolean = false) {
        if (tracks.isEmpty()) return
        val playlist = if (shuffle) tracks.shuffled() else tracks
        val target = playlist.firstOrNull() ?: return
        playTrack(target, playlist)
    }

    fun togglePlayPause() {
        playerManager.togglePlayPause()
    }

    fun skipNext() {
        playerManager.skipNext()
    }

    fun skipPrevious() {
        playerManager.skipPrevious()
    }

    fun skipToQueueIndex(index: Int) {
        playerManager.skipToQueueIndex(index)
    }

    fun seekToFraction(fraction: Float) {
        val dur = playbackState.value.durationMs
        if (dur > 0L) {
            val targetMs = (dur * fraction).toLong()
            playerManager.seekTo(targetMs)
        }
    }

    fun toggleShuffle() {
        playerManager.toggleShuffle()
    }

    fun toggleRepeat() {
        playerManager.toggleRepeat()
    }

    fun selectTab(tab: MusicTab) {
        _currentTab.value = tab
        prefs.edit().putString(KEY_LAST_TAB, tab.name).apply()
    }

    fun switchToPlayScreen() {
        selectTab(MusicTab.PLAY)
    }

    fun onAppBackground(timestamp: Long = System.currentTimeMillis()) {
        lastBackgroundTimeMs = timestamp
        prefs.edit().putString(KEY_LAST_TAB, _currentTab.value.name).apply()
    }

    fun onActivityResumed() {
        val now = System.currentTimeMillis()
        val timeInBackground = if (lastBackgroundTimeMs > 0L) now - lastBackgroundTimeMs else Long.MAX_VALUE
        if (timeInBackground >= SCREEN_RETENTION_TIMEOUT_MS) {
            _currentTab.value = MusicTab.PLAY
        }
        _connectionTypeName.value = com.example.util.NetworkUtils.getConnectionTypeName(getApplication())
        refreshCacheSize()
        loadInstalledApps()
        refreshBatteryOptimizationStatus()
    }

    fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val app = getApplication<Application>()
                val allApps = getInstalledApps(app).filter { it.packageName != app.packageName }
                _installedApps.value = allApps

                val savedConfig = prefs.getString(KEY_NINE_APPS_CONFIG, null)
                val selectedNine = if (!savedConfig.isNullOrEmpty()) {
                    val keys = savedConfig.split(",")
                    keys.mapNotNull { key ->
                        val parts = key.split("|")
                        val pkg = parts.getOrNull(0) ?: ""
                        val cls = parts.getOrNull(1) ?: ""
                        allApps.find { it.packageName == pkg && (cls.isEmpty() || it.className == cls) }
                            ?: allApps.find { it.packageName == pkg }
                    }
                } else {
                    emptyList()
                }

                if (selectedNine.isNotEmpty()) {
                    _nineApps.value = selectedNine.take(9)
                } else {
                    val defaultPriorityPackages = listOf(
                        "com.android.chrome",
                        "org.chromium.chrome",
                        "com.android.settings",
                        "com.google.android.GoogleCamera",
                        "com.android.camera",
                        "com.google.android.apps.photos",
                        "com.android.gallery3d",
                        "com.google.android.documentsui",
                        "com.android.documentsui",
                        "com.google.android.youtube",
                        "com.google.android.deskclock",
                        "com.android.deskclock",
                        "com.google.android.calculator",
                        "com.google.android.calculator2",
                        "com.google.android.contacts",
                        "com.google.android.dialer"
                    )
                    val prioritized = mutableListOf<AppInfo>()
                    for (pkg in defaultPriorityPackages) {
                        val found = allApps.find { it.packageName == pkg }
                        if (found != null && !prioritized.any { it.packageName == found.packageName }) {
                            prioritized.add(found)
                            if (prioritized.size >= 9) break
                        }
                    }
                    for (item in allApps) {
                        if (prioritized.size >= 9) break
                        if (!prioritized.any { it.packageName == item.packageName }) {
                            prioritized.add(item)
                        }
                    }
                    _nineApps.value = prioritized.take(9)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateNineAppSlot(index: Int, newApp: AppInfo) {
        val current = _nineApps.value.toMutableList()
        if (index in 0 until 9) {
            if (index < current.size) {
                current[index] = newApp
            } else {
                while (current.size < index) {
                    current.add(newApp)
                }
                current.add(newApp)
            }
            _nineApps.value = current.take(9)
            saveNineAppsConfig(_nineApps.value)
        }
    }

    fun saveNineAppsConfig(apps: List<AppInfo>) {
        val str = apps.joinToString(",") { "${it.packageName}|${it.className}" }
        prefs.edit().putString(KEY_NINE_APPS_CONFIG, str).apply()
    }

    fun onHomeIntentReceived(isActivityResumed: Boolean) {
        val now = System.currentTimeMillis()
        val timeInBackground = if (lastBackgroundTimeMs > 0L) now - lastBackgroundTimeMs else Long.MAX_VALUE

        if (isActivityResumed || timeInBackground >= SCREEN_RETENTION_TIMEOUT_MS) {
            _currentTab.value = MusicTab.PLAY
        }
    }

    fun trimMemoryCache() {
        playerManager.clearArtworkCache()
    }

    fun setHapticsEnabled(enabled: Boolean) {
        _isHapticsEnabled.value = enabled
        prefs.edit().putBoolean(KEY_HAPTICS_ENABLED, enabled).apply()
    }

    fun openDefaultHomeSettings(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    if (!roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                        val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        return
                    }
                }
            }
            val homeSettingsIntent = Intent(Settings.ACTION_HOME_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(homeSettingsIntent)
        } catch (e: Exception) {
            try {
                val appSettingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(appSettingsIntent)
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
    }

    fun isDefaultLauncher(context: Context): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
            val resolveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.resolveActivity(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            }
            resolveInfo?.activityInfo?.packageName == context.packageName
        } catch (e: Exception) {
            false
        }
    }

    // ==========================================
    // バッテリー最適化除外誘導 (Doze対策)
    // ==========================================

    fun checkBatteryOptimizationIgnored(): Boolean {
        val app = getApplication<Application>()
        val pm = app.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(app.packageName)
    }

    fun refreshBatteryOptimizationStatus() {
        _isBatteryOptimizationIgnored.value = checkBatteryOptimizationIgnored()
    }

    fun requestIgnoreBatteryOptimization(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
    }

    // ==========================================
    // Google Play アプリアップデート & 設定起動
    // ==========================================

    fun openPlayStoreUpdates(context: Context) {
        try {
            val intent = Intent("com.google.android.finsky.VIEW_MY_DOWNLOADS").apply {
                setPackage("com.android.vending")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(context, "Google Playストアを開けませんでした", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun openPlayStoreAutoUpdateSettings(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account")).apply {
                setPackage("com.android.vending")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(context, "Google Playストアを開けませんでした", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ==========================================
    // おやすみスリープタイマー & 画面減光 (Screen Dimming)
    // ==========================================

    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerMode.value = minutes

        if (minutes == null) {
            _sleepTimerRemainingSeconds.value = null
            _screenDimFactor.value = 1.0f
            return
        }

        if (minutes == -1) {
            // 現在の曲終了時に自動停止
            sleepTimerJob = viewModelScope.launch {
                val dimWindowSeconds = 120 // 残り2分間で徐々に減光
                while (isActive) {
                    val state = playbackState.value
                    val dur = state.durationMs
                    val pos = state.currentPositionMs
                    val remainingMs = (dur - pos).coerceAtLeast(0L)
                    val remainingSec = (remainingMs / 1000).toInt()

                    _sleepTimerRemainingSeconds.value = remainingSec

                    // 曲終了判定 (再生完了位置に達したか、再生中でなく残り時間が1秒以下)
                    if (dur > 0 && (pos >= dur - 500L || (!state.isPlaying && remainingMs <= 1000L && pos > 0))) {
                        break
                    }

                    // 残り2分前から徐々に画面減光 (1.0f -> 0.05f)
                    if (remainingSec in 1..dimWindowSeconds) {
                        val progress = remainingSec.toFloat() / dimWindowSeconds.toFloat()
                        val dim = (0.05f + progress * 0.95f).coerceIn(0.05f, 1.0f)
                        _screenDimFactor.value = dim
                    } else {
                        _screenDimFactor.value = 1.0f
                    }

                    delay(500L)
                }

                // タイマー終了: 音楽を完全停止しリソース解放、減光を復帰
                _sleepTimerRemainingSeconds.value = null
                _sleepTimerMode.value = null
                _screenDimFactor.value = 1.0f
                playerManager.stop()
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), getApplication<Application>().getString(com.example.R.string.toast_sleep_timer_track_ended), Toast.LENGTH_SHORT).show()
                }
            }
            return
        }

        val totalSeconds = minutes * 60
        _sleepTimerRemainingSeconds.value = totalSeconds
        startSleepTimerCountdown(totalSeconds)
    }

    private fun startSleepTimerCountdown(totalSeconds: Int) {
        sleepTimerJob = viewModelScope.launch {
            var currentSec = totalSeconds
            val dimWindowSeconds = minOf(120, totalSeconds)

            while (currentSec > 0 && isActive) {
                _sleepTimerRemainingSeconds.value = currentSec

                // 終了前（最大2分前）から徐々に画面減光 (1.0f -> 0.05f)
                if (dimWindowSeconds > 0 && currentSec <= dimWindowSeconds) {
                    val progress = currentSec.toFloat() / dimWindowSeconds.toFloat()
                    val dim = (0.05f + progress * 0.95f).coerceIn(0.05f, 1.0f)
                    _screenDimFactor.value = dim
                } else {
                    _screenDimFactor.value = 1.0f
                }

                delay(1000L)
                currentSec--
            }

            // タイマー終了: 音楽を完全停止しリソース解放、減光を復帰
            _sleepTimerRemainingSeconds.value = null
            _sleepTimerMode.value = null
            _screenDimFactor.value = 1.0f
            playerManager.stop()
            withContext(Dispatchers.Main) {
                Toast.makeText(getApplication(), getApplication<Application>().getString(com.example.R.string.toast_sleep_timer_stopped), Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun stopPlayback() {
        playerManager.stop()
    }

    override fun onCleared() {
        super.onCleared()
        sleepTimerJob?.cancel()
        playerManager.release()
    }
}
