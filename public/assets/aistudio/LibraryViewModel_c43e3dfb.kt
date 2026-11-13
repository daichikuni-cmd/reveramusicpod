package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.MusicalForm
import com.example.data.model.Track
import com.example.data.repository.MusicRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class LibraryFilter {
    data object None : LibraryFilter()
    data class Composer(val name: String) : LibraryFilter()
    data class Artist(val name: String) : LibraryFilter()
    data class Album(val name: String) : LibraryFilter()
    data class Form(val form: MusicalForm) : LibraryFilter()
    data class Folder(val path: String) : LibraryFilter()
}

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    val repository = MusicRepository(application, db.trackDao())

    val allTracks: StateFlow<List<Track>> = repository.allTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cachedTracks: StateFlow<List<Track>> = repository.cachedTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val composers: StateFlow<List<String>> = repository.composers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val artists: StateFlow<List<String>> = repository.artists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val albums: StateFlow<List<String>> = repository.albums
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val musicalForms: StateFlow<List<String>> = repository.musicalForms
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders: StateFlow<List<String>> = repository.folders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _activeFilter = MutableStateFlow<LibraryFilter>(LibraryFilter.None)
    val activeFilter: StateFlow<LibraryFilter> = _activeFilter.asStateFlow()

    private val _id3EditingTrack = MutableStateFlow<Track?>(null)
    val id3EditingTrack: StateFlow<Track?> = _id3EditingTrack.asStateFlow()

    private val _messageSnackbar = MutableStateFlow<String?>(null)
    val messageSnackbar: StateFlow<String?> = _messageSnackbar.asStateFlow()

    // All tracks filtered by search & active drill-down filter (for FolderTreeScreen)
    val filteredTracks: StateFlow<List<Track>> = combine(allTracks, _searchQuery, _activeFilter) { tracks, query, filter ->
        var list = tracks
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            list = list.filter {
                it.title.lowercase().contains(q) ||
                it.composer.lowercase().contains(q) ||
                it.artist.lowercase().contains(q) ||
                it.album.lowercase().contains(q) ||
                it.folderPath.lowercase().contains(q)
            }
        }
        when (filter) {
            is LibraryFilter.Composer -> list.filter { it.composer == filter.name }
            is LibraryFilter.Artist -> list.filter { it.artist == filter.name }
            is LibraryFilter.Album -> list.filter { it.album == filter.name }
            is LibraryFilter.Form -> list.filter { it.musicalForm == filter.form }
            is LibraryFilter.Folder -> list.filter { it.folderPath == filter.path }
            LibraryFilter.None -> list
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Cached tracks only (for Composers, Performers, Forms, and Albums tabs)
    val filteredCachedTracks: StateFlow<List<Track>> = combine(cachedTracks, _searchQuery, _activeFilter) { tracks, query, filter ->
        var list = tracks
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            list = list.filter {
                it.title.lowercase().contains(q) ||
                it.composer.lowercase().contains(q) ||
                it.artist.lowercase().contains(q) ||
                it.album.lowercase().contains(q) ||
                it.folderPath.lowercase().contains(q)
            }
        }
        when (filter) {
            is LibraryFilter.Composer -> list.filter { it.composer == filter.name }
            is LibraryFilter.Artist -> list.filter { it.artist == filter.name }
            is LibraryFilter.Album -> list.filter { it.album == filter.name }
            is LibraryFilter.Form -> list.filter { it.musicalForm == filter.form }
            is LibraryFilter.Folder -> list.filter { it.folderPath == filter.path }
            LibraryFilter.None -> list
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            repository.initializeIfEmpty()
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: LibraryFilter) {
        _activeFilter.value = filter
    }

    fun clearFilter() {
        _activeFilter.value = LibraryFilter.None
    }

    fun startEditingId3(track: Track) {
        _id3EditingTrack.value = track
    }

    fun dismissEditingId3() {
        _id3EditingTrack.value = null
    }

    fun saveId3Tag(
        trackId: String,
        title: String,
        composer: String,
        artist: String,
        album: String,
        musicalForm: MusicalForm
    ) {
        viewModelScope.launch {
            repository.updateId3Tag(trackId, title, composer, artist, album, musicalForm)
            _id3EditingTrack.value = null
            _messageSnackbar.value = "ID3タグを更新しました (フォルダ変更リストに記録)"
        }
    }



    private val prefs = getApplication<Application>().getSharedPreferences("largo_folder_prefs", android.content.Context.MODE_PRIVATE)

    private val _desiredCheckedIds = MutableStateFlow<Set<String>>(emptySet())
    val desiredCheckedIds: StateFlow<Set<String>> = _desiredCheckedIds.asStateFlow()

    private val _busyIds = MutableStateFlow<Set<String>>(emptySet())
    val busyIds: StateFlow<Set<String>> = _busyIds.asStateFlow()

    private val _expandedFolderPaths = MutableStateFlow<Set<String>>(emptySet())
    val expandedFolderPaths: StateFlow<Set<String>> = _expandedFolderPaths.asStateFlow()
    private var hasInitializedExpandedPaths = false

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

    private var hasInitializedDesiredCheckedIds = false

    /**
     * 未完了のダウンロード（ユーザーが保存を希望したが未完了・待機中の楽曲）が存在するかどうか
     */
    val hasIncompleteDownloads: StateFlow<Boolean> = combine(allTracks, _desiredCheckedIds) { tracks, desired ->
        tracks.any { it.id in desired && !it.isCached }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        val saved = prefs.getStringSet("expanded_folder_paths_v2", null)
        if (saved != null) {
            _expandedFolderPaths.value = saved
            hasInitializedExpandedPaths = true
        }

        // 初回ロード時はキャッシュ済み音源からdesiredCheckedIdsを初期化。
        // 以降の更新では未キャッシュの保存待機中IDを消去せず、新しくキャッシュされたIDを追加マージする。
        viewModelScope.launch {
            allTracks.collect { tracks ->
                val cachedIds = tracks.filter { it.isCached }.map { it.id }.toSet()
                if (!hasInitializedDesiredCheckedIds) {
                    _desiredCheckedIds.value = cachedIds
                    hasInitializedDesiredCheckedIds = true
                } else {
                    _desiredCheckedIds.value = _desiredCheckedIds.value + cachedIds
                }
            }
        }
    }

    /**
     * 一時停止中、失敗、またはダウンロード待機中の未保存楽曲を取得
     */
    fun getIncompleteTracks(): List<Track> {
        val all = allTracks.value
        val desired = _desiredCheckedIds.value

        // チェック済み（保存要求中）だがまだ未キャッシュの楽曲
        return all.filter { it.id in desired && !it.isCached }
    }

    /**
     * フォルダ画面の右上同期マーク等から、未完了のダウンロードを一括再開する
     */
    fun resumeIncompleteDownloads(forceCellular: Boolean = false): Int {
        val tracksToResume = getIncompleteTracks()
        if (tracksToResume.isEmpty()) return 0

        val trackIds = tracksToResume.map { it.id }.toSet()
        _desiredCheckedIds.value = _desiredCheckedIds.value + trackIds

        val job = viewModelScope.launch {
            _messageSnackbar.value = "未完了の ${tracksToResume.size}曲のダウンロードを再開しました"
            try {
                for (track in tracksToResume) {
                    _busyIds.value = _busyIds.value + track.id
                    try {
                        val res = repository.cacheTrack(track, forceCellular)
                        if (res.isFailure) {
                            Log.w("LibraryViewModel", "Track cache failed on resume: ${track.title} - ${res.exceptionOrNull()?.message}")
                        }
                    } finally {
                        _busyIds.value = _busyIds.value - track.id
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                Log.d("LibraryViewModel", "resumeIncompleteDownloads was cancelled/paused")
            }
        }
        trackJob(job)
        return tracksToResume.size
    }

    fun showSnackbar(message: String) {
        _messageSnackbar.value = message
    }

    private val activeCacheJobs = mutableListOf<Job>()

    fun stopActiveCacheOperations() {
        synchronized(activeCacheJobs) {
            activeCacheJobs.forEach { it.cancel() }
            activeCacheJobs.clear()
        }
        _busyIds.value = emptySet()
        _messageSnackbar.value = "キャッシュ処理を一時停止しました"
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

    fun onCacheCleared() {
        stopActiveCacheOperations()
        _desiredCheckedIds.value = emptySet()
    }

    fun toggleTrackCache(track: Track, forceCellular: Boolean = false) {
        val shouldUncache = track.isCached || _desiredCheckedIds.value.contains(track.id)
        val job = viewModelScope.launch {
            _busyIds.value = _busyIds.value + track.id
            try {
                if (shouldUncache) {
                    // User unchecks -> delete cache immediately without checking or waiting for WiFi
                    _desiredCheckedIds.value = _desiredCheckedIds.value - track.id
                    val res = repository.uncacheTrack(track.id)
                    res.onSuccess {
                        _messageSnackbar.value = "「${track.title}」のキャッシュを削除しました"
                    }.onFailure {
                        _messageSnackbar.value = "キャッシュ削除に失敗しました: ${it.message}"
                    }
                } else {
                    // User checks -> cache track
                    _desiredCheckedIds.value = _desiredCheckedIds.value + track.id
                    val res = repository.cacheTrack(track, forceCellular)
                    res.onSuccess {
                        _messageSnackbar.value = "「${track.title}」をキャッシュ保存しました"
                    }.onFailure { e ->
                        // Do not clear desiredCheckedIds on failure so that user can resume!
                        _messageSnackbar.value = e.message ?: "キャッシュに失敗しました（同期マークで再開可能）"
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Cancelled / Paused
            } finally {
                _busyIds.value = _busyIds.value - track.id
            }
        }
        trackJob(job)
    }

    fun toggleFolderCache(
        folderPathPrefix: String,
        currentlyChecked: Boolean? = null,
        forceCellular: Boolean = false
    ) {
        val all = allTracks.value
        val folderTracks = all.filter {
            it.folderPath == folderPathPrefix || it.folderPath.startsWith("$folderPathPrefix/")
        }
        val folderTrackIds = folderTracks.map { it.id }.toSet()
        val hasAnyCached = folderTracks.any { it.isCached }
        val hasAnyChecked = folderTracks.any { it.id in _desiredCheckedIds.value }
        val shouldUncache = currentlyChecked ?: (hasAnyCached || hasAnyChecked)

        val job = viewModelScope.launch {
            _busyIds.value = _busyIds.value + folderPathPrefix
            try {
                if (shouldUncache) {
                    // Delete cache immediately without checking or waiting for WiFi!
                    _desiredCheckedIds.value = _desiredCheckedIds.value - folderTrackIds
                    val res = repository.uncacheFolder(folderPathPrefix)
                    res.onSuccess { count ->
                        _messageSnackbar.value = "フォルダから $count 曲のキャッシュを削除しました"
                    }.onFailure {
                        _messageSnackbar.value = "キャッシュ削除に失敗しました: ${it.message}"
                    }
                } else {
                    // Not checked -> user wants to check all and cache
                    _desiredCheckedIds.value = _desiredCheckedIds.value + folderTrackIds
                    val res = repository.cacheFolder(folderPathPrefix, forceCellular)
                    res.onSuccess { count ->
                        _messageSnackbar.value = "フォルダの $count 曲をキャッシュ保存しました"
                    }.onFailure { e ->
                        // Do not clear desiredCheckedIds on failure so that user can resume!
                        _messageSnackbar.value = e.message ?: "キャッシュに失敗しました（同期マークで再開可能）"
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Cancelled / Paused
            } finally {
                _busyIds.value = _busyIds.value - folderPathPrefix
            }
        }
        trackJob(job)
    }

    fun cacheTrack(track: Track, forceCellular: Boolean = false) {
        _desiredCheckedIds.value = _desiredCheckedIds.value + track.id
        val job = viewModelScope.launch {
            _busyIds.value = _busyIds.value + track.id
            try {
                val res = repository.cacheTrack(track, forceCellular)
                res.onSuccess {
                    _messageSnackbar.value = "「${track.title}」をキャッシュしました"
                }.onFailure { e ->
                    _messageSnackbar.value = e.message ?: "キャッシュに失敗しました（同期マークで再開可能）"
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Cancelled / Paused
            } finally {
                _busyIds.value = _busyIds.value - track.id
            }
        }
        trackJob(job)
    }

    fun uncacheTrack(track: Track) {
        _desiredCheckedIds.value = _desiredCheckedIds.value - track.id
        val job = viewModelScope.launch {
            _busyIds.value = _busyIds.value + track.id
            try {
                repository.uncacheTrack(track.id)
                _messageSnackbar.value = "「${track.title}」のキャッシュを削除しました"
            } finally {
                _busyIds.value = _busyIds.value - track.id
            }
        }
        trackJob(job)
    }

    fun cacheAlbum(album: String, forceCellular: Boolean = false) {
        viewModelScope.launch {
            val res = repository.cacheAlbum(album, forceCellular)
            res.onSuccess { count ->
                _messageSnackbar.value = "アルバム「$album」の $count 曲をキャッシュしました"
            }.onFailure { e ->
                _messageSnackbar.value = e.message ?: "Wi-Fi接続が必要です"
            }
        }
    }

    fun cacheFolder(folderPath: String, forceCellular: Boolean = false) {
        toggleFolderCache(folderPathPrefix = folderPath, currentlyChecked = false, forceCellular = forceCellular)
    }

    fun clearMessage() {
        _messageSnackbar.value = null
    }
}
