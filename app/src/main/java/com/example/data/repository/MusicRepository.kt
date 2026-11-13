package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.drive.DriveFolderGroup
import com.example.data.drive.DriveRoot
import com.example.data.drive.GoogleDriveClient
import com.example.data.model.Track
import com.example.util.Id3Helper
import com.example.util.NetworkUtils
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class WifiRequiredException(message: String = "4G/モバイル通信保護のためWi-Fi接続が必要です") : Exception(message)

data class SyncResult(
    val newTracks: Int,
    val updatedTracks: Int,
    val folderCount: Int = 0,
    val isRealApi: Boolean = true,
    val errorMessage: String? = null
)

data class IncrementalSyncResult(
    val addedTracks: Int = 0,
    val updatedTracks: Int = 0,
    val deletedTracks: Int = 0,
    val isFullSyncFallback: Boolean = false,
    val isRealApi: Boolean = true,
    val errorMessage: String? = null
) {
    val hasChanges: Boolean get() = (addedTracks > 0 || updatedTracks > 0 || deletedTracks > 0)
}

/**
 * Google Drive同期・キャッシュ永続化リポジトリ。
 * 参照実装（Largo）の設計を踏襲し、高速差分同期（Changes API）、Wi-Fi通信保護、
 * およびローカルキャッシュ（audio_cache）の確実な管理を提供します。
 */
class MusicRepository(private val context: Context) {

    companion object {
        private const val TAG = "MusicRepository"
        private const val PREFS_NAME = "music_repository_prefs"
        private const val KEY_DRIVE_TRACKS_JSON = "key_drive_tracks_json"
        private const val KEY_WIFI_ONLY = "key_wifi_only_download"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val trackListAdapter = moshi.adapter<List<Track>>(
        Types.newParameterizedType(List::class.java, Track::class.java)
    )

    fun isWifiOnlyDownload(): Boolean {
        return prefs.getBoolean(KEY_WIFI_ONLY, true)
    }

    fun setWifiOnlyDownload(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WIFI_ONLY, enabled).apply()
    }

    suspend fun loadSavedDriveTracks(): List<Track> = withContext(Dispatchers.IO) {
        try {
            val json = prefs.getString(KEY_DRIVE_TRACKS_JSON, null)
            if (!json.isNullOrEmpty()) {
                val list = trackListAdapter.fromJson(json) ?: emptyList()
                val cacheDir = File(context.cacheDir, "audio_cache")
                return@withContext list.map { track ->
                    val sanitizedName = track.fileName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
                    val targetFile = File(cacheDir, "${track.id}_$sanitizedName")
                    if (targetFile.exists() && targetFile.length() > 0L) {
                        val cachedTime = if (track.lastCachedAt > 0L) track.lastCachedAt else (targetFile.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis())
                        track.copy(localFilePath = targetFile.absolutePath, isDownloaded = true, isCached = true, lastCachedAt = cachedTime)
                    } else {
                        track.copy(localFilePath = null, isDownloaded = false, isCached = false)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading saved drive tracks", e)
        }
        emptyList()
    }

    suspend fun saveDriveTracks(tracks: List<Track>) = withContext(Dispatchers.IO) {
        try {
            val json = trackListAdapter.toJson(tracks)
            prefs.edit().putString(KEY_DRIVE_TRACKS_JSON, json).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving drive tracks", e)
        }
    }

    suspend fun clearSavedDriveTracks() = withContext(Dispatchers.IO) {
        prefs.edit().remove(KEY_DRIVE_TRACKS_JSON).apply()
    }

    suspend fun getCacheSizeFormatted(): String = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "audio_cache")
        var totalBytes = 0L
        if (cacheDir.exists()) {
            cacheDir.listFiles()?.forEach { file ->
                totalBytes += file.length()
            }
        }
        val mb = totalBytes.toDouble() / (1024 * 1024)
        if (mb >= 1.0) {
            "%.1f MB".format(mb)
        } else {
            "%.1f KB".format(totalBytes.toDouble() / 1024)
        }
    }

    fun getAudioCacheSizeBytes(): Long {
        val cacheDir = File(context.cacheDir, "audio_cache")
        if (!cacheDir.exists() || !cacheDir.isDirectory) return 0L
        return cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    suspend fun clearAudioCache(): Long = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "audio_cache")
        var freedBytes = 0L
        if (cacheDir.exists()) {
            cacheDir.listFiles()?.forEach { file ->
                freedBytes += file.length()
                file.delete()
            }
        }
        val tracks = loadSavedDriveTracks().map { it.copy(localFilePath = null, isDownloaded = false, isCached = false) }
        saveDriveTracks(tracks)
        freedBytes
    }

    /**
     * 単曲の明示的キャッシュ（Wi-Fi保護付き）
     */
    suspend fun cacheTrack(track: Track, forceCellular: Boolean = false): Result<Track> = withContext(Dispatchers.IO) {
        if (!forceCellular && isWifiOnlyDownload() && !NetworkUtils.isWifiConnected(context)) {
            return@withContext Result.failure(WifiRequiredException())
        }

        try {
            val tokenRes = GoogleDriveClient.ensureValidAccessToken(context)
            var token = tokenRes.getOrNull()
            if (!token.isNullOrBlank()) {
                var downloadRes = GoogleDriveClient.downloadTrackToCache(context, track, token)
                if (downloadRes.isFailure) {
                    val errMsg = downloadRes.exceptionOrNull()?.message ?: ""
                    if (errMsg.contains("401") || errMsg.contains("UNAUTHENTICATED")) {
                        val refreshed = GoogleDriveClient.ensureValidAccessToken(context, forceRefresh = true).getOrNull()
                        if (!refreshed.isNullOrBlank()) {
                            token = refreshed
                            downloadRes = GoogleDriveClient.downloadTrackToCache(context, track, token)
                        }
                    }
                }
                val targetFile = downloadRes.getOrThrow()
                val updatedTrack = track.copy(isDownloaded = true, isCached = true, localFilePath = targetFile.absolutePath, lastCachedAt = System.currentTimeMillis())
                val all = loadSavedDriveTracks().map { if (it.id == track.id) updatedTrack else it }
                saveDriveTracks(all)
                return@withContext Result.success(updatedTrack)
            }
            Result.failure(Exception("有効なアクセストークンがありません"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 単曲のキャッシュ削除
     */
    suspend fun uncacheTrack(trackId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val all = loadSavedDriveTracks().toMutableList()
            val idx = all.indexOfFirst { it.id == trackId }
            if (idx >= 0) {
                val track = all[idx]
                track.localFilePath?.let { path ->
                    try { File(path).delete() } catch (_: Exception) {}
                }
                val cacheDir = File(context.cacheDir, "audio_cache")
                val sanitizedName = track.fileName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
                val targetFile = File(cacheDir, "${track.id}_$sanitizedName")
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                all[idx] = track.copy(isDownloaded = false, isCached = false, localFilePath = null)
                saveDriveTracks(all)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * フォルダ内のキャッシュ一括削除
     */
    suspend fun uncacheFolder(folderPathPrefix: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val all = loadSavedDriveTracks().toMutableList()
            var count = 0
            val cacheDir = File(context.cacheDir, "audio_cache")
            for (i in all.indices) {
                val track = all[i]
                if (track.folderPath == folderPathPrefix || track.folderPath.startsWith("$folderPathPrefix/")) {
                    track.localFilePath?.let { path ->
                        try { File(path).delete() } catch (_: Exception) {}
                    }
                    val sanitizedName = track.fileName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
                    val targetFile = File(cacheDir, "${track.id}_$sanitizedName")
                    if (targetFile.exists()) {
                        targetFile.delete()
                    }
                    if (track.isDownloaded || track.isCached) {
                        count++
                    }
                    all[i] = track.copy(isDownloaded = false, isCached = false, localFilePath = null)
                }
            }
            saveDriveTracks(all)
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * フォルダ内の全楽曲キャッシュ保存
     */
    suspend fun cacheFolder(folderPathPrefix: String, forceCellular: Boolean = false): Result<Int> = withContext(Dispatchers.IO) {
        if (!forceCellular && isWifiOnlyDownload() && !NetworkUtils.isWifiConnected(context)) {
            return@withContext Result.failure(WifiRequiredException())
        }
        val all = loadSavedDriveTracks()
        val folderTracks = all.filter {
            (it.folderPath == folderPathPrefix || it.folderPath.startsWith("$folderPathPrefix/")) && (!it.isDownloaded && !it.isCached)
        }
        var count = 0
        for (track in folderTracks) {
            val res = cacheTrack(track, forceCellular = true)
            if (res.isSuccess) {
                count++
            }
        }
        Result.success(count)
    }

    /**
     * アルバムの全曲キャッシュ保存
     */
    suspend fun cacheAlbum(album: String, forceCellular: Boolean = false): Result<Int> = withContext(Dispatchers.IO) {
        if (!forceCellular && isWifiOnlyDownload() && !NetworkUtils.isWifiConnected(context)) {
            return@withContext Result.failure(WifiRequiredException())
        }
        val all = loadSavedDriveTracks()
        val albumTracks = all.filter { it.album == album && (!it.isDownloaded && !it.isCached) }
        var count = 0
        for (track in albumTracks) {
            val res = cacheTrack(track, forceCellular = true)
            if (res.isSuccess) {
                count++
            }
        }
        Result.success(count)
    }

    /**
     * 全曲の一括キャッシュダウンロード
     */
    suspend fun cacheAllTracks(forceCellular: Boolean = false): Result<Int> = withContext(Dispatchers.IO) {
        if (!forceCellular && isWifiOnlyDownload() && !NetworkUtils.isWifiConnected(context)) {
            return@withContext Result.failure(WifiRequiredException())
        }
        val all = loadSavedDriveTracks()
        var cachedCount = 0
        for (track in all) {
            if (!track.isDownloaded && !track.isCached) {
                cacheTrack(track, forceCellular = true)
                cachedCount++
            }
        }
        Result.success(cachedCount)
    }

    /**
     * Google Drive API v3: フル同期
     */
    suspend fun syncGoogleDrive(): SyncResult = withContext(Dispatchers.IO) {
        val tokenRes = GoogleDriveClient.ensureValidAccessToken(context)
        var token = tokenRes.getOrNull()
        if (token.isNullOrBlank()) {
            return@withContext SyncResult(
                newTracks = 0,
                updatedTracks = 0,
                isRealApi = false,
                errorMessage = "Googleドライブ未接続です。「設定」タブで「Googleアカウントで接続」を行ってください。"
            )
        }

        val root = GoogleDriveClient.getSavedDriveRoot(context)
        var folderResult = GoogleDriveClient.fetchAudioFolders(context, token, root)

        if (folderResult.isFailure) {
            val err = folderResult.exceptionOrNull()?.message ?: "Drive通信エラー"
            if (err.contains("401") || err.contains("UNAUTHENTICATED") || err.contains("authError")) {
                val refreshed = GoogleDriveClient.ensureValidAccessToken(context, forceRefresh = true).getOrNull()
                if (!refreshed.isNullOrBlank()) {
                    token = refreshed
                    folderResult = GoogleDriveClient.fetchAudioFolders(context, token, root)
                }
            }
        }

        if (folderResult.isFailure) {
            val err = folderResult.exceptionOrNull()?.message ?: "Drive通信エラー"
            if (err.contains("401") || err.contains("UNAUTHENTICATED") || err.contains("authError")) {
                GoogleDriveClient.saveAccessToken(context, null)
                return@withContext SyncResult(
                    newTracks = 0,
                    updatedTracks = 0,
                    isRealApi = true,
                    errorMessage = "Googleアカウントの再ログインが必要です。「設定」の「Googleアカウントで接続」を押してください。"
                )
            }
            return@withContext SyncResult(
                newTracks = 0,
                updatedTracks = 0,
                isRealApi = true,
                errorMessage = err
            )
        }

        val folderGroups = folderResult.getOrThrow()
        val allDriveTracks = folderGroups.flatMap { it.tracks }
        val existing = loadSavedDriveTracks()

        val merged = allDriveTracks.map { driveTrack ->
            val matched = existing.find { it.id == driveTrack.id }
            if (matched != null) {
                driveTrack.copy(
                    isDownloaded = matched.isDownloaded,
                    isCached = matched.isCached,
                    localFilePath = matched.localFilePath,
                    lastCachedAt = matched.lastCachedAt
                )
            } else {
                driveTrack
            }
        }

        saveDriveTracks(merged)

        val newCount = merged.count { existing.none { ex -> ex.id == it.id } }
        val updatedCount = merged.size - newCount

        SyncResult(
            newTracks = newCount,
            updatedTracks = updatedCount,
            folderCount = folderGroups.size,
            isRealApi = true,
            errorMessage = null
        )
    }

    /**
     * Google Drive API v3: Changes APIによる高速差分同期
     */
    suspend fun syncIncrementalGoogleDrive(): IncrementalSyncResult = withContext(Dispatchers.IO) {
        val tokenRes = GoogleDriveClient.ensureValidAccessToken(context)
        var token = tokenRes.getOrNull()
        if (token.isNullOrBlank()) {
            return@withContext IncrementalSyncResult(
                errorMessage = "Googleドライブ未接続です。「設定」タブで「Googleアカウントで接続」を行ってください。"
            )
        }

        val root = GoogleDriveClient.getSavedDriveRoot(context)
        val savedToken = GoogleDriveClient.getSavedStartPageToken(context)
        val existing = loadSavedDriveTracks().toMutableList()

        if (savedToken.isNullOrBlank() || existing.isEmpty()) {
            val fullRes = syncGoogleDrive()
            return@withContext IncrementalSyncResult(
                addedTracks = fullRes.newTracks,
                updatedTracks = fullRes.updatedTracks,
                deletedTracks = 0,
                isFullSyncFallback = true,
                errorMessage = fullRes.errorMessage
            )
        }

        var diffResult = GoogleDriveClient.fetchDifferentialChanges(context, token, savedToken, root)
        if (diffResult.isFailure) {
            val err = diffResult.exceptionOrNull()
            if (err is GoogleDriveClient.TokenExpiredException) {
                val fullRes = syncGoogleDrive()
                return@withContext IncrementalSyncResult(
                    addedTracks = fullRes.newTracks,
                    updatedTracks = fullRes.updatedTracks,
                    deletedTracks = 0,
                    isFullSyncFallback = true,
                    errorMessage = fullRes.errorMessage
                )
            } else if (err?.message?.contains("401") == true || err?.message?.contains("UNAUTHENTICATED") == true) {
                val refreshed = GoogleDriveClient.ensureValidAccessToken(context, forceRefresh = true).getOrNull()
                if (!refreshed.isNullOrBlank()) {
                    token = refreshed
                    diffResult = GoogleDriveClient.fetchDifferentialChanges(context, token, savedToken, root)
                }
            }
        }

        if (diffResult.isFailure) {
            return@withContext IncrementalSyncResult(
                errorMessage = diffResult.exceptionOrNull()?.message ?: "差分同期エラー"
            )
        }

        val result = diffResult.getOrThrow()
        GoogleDriveClient.saveStartPageToken(context, result.newStartPageToken)

        if (result.changes.isEmpty()) {
            return@withContext IncrementalSyncResult(addedTracks = 0, updatedTracks = 0, deletedTracks = 0)
        }

        val folderMap = GoogleDriveClient.fetchAllFolders(token, root)
        var addedCount = 0
        var updatedCount = 0
        var deletedCount = 0

        for (change in result.changes) {
            val trackId = "drive_${change.fileId}"
            val existingIdx = existing.indexOfFirst { it.id == trackId }
            val isRemovedOrTrashed = change.isRemoved || (change.fileObj?.optBoolean("trashed", false) == true)

            if (isRemovedOrTrashed) {
                if (existingIdx >= 0) {
                    val removed = existing.removeAt(existingIdx)
                    removed.localFilePath?.let { path ->
                        try { File(path).delete() } catch (_: Exception) {}
                    }
                    deletedCount++
                }
            } else if (change.fileObj != null) {
                val fileObj = change.fileObj
                val fileName = fileObj.optString("name", "")
                val lower = fileName.lowercase()
                val hasAudioExt = GoogleDriveClient.SUPPORTED_EXTENSIONS.any { lower.endsWith(it) }
                if (!hasAudioExt || lower.endsWith(".wav")) {
                    continue
                }

                val parents = fileObj.optJSONArray("parents")
                val parentId = if (parents != null && parents.length() > 0) parents.getString(0) else "root"
                val folderPath = GoogleDriveClient.resolveFolderPath(parentId, folderMap, root)
                val streamUrl = "https://www.googleapis.com/drive/v3/files/${change.fileId}?alt=media"
                val modifiedTime = GoogleDriveClient.parseIsoTimestamp(fileObj.optString("modifiedTime", ""))

                val track = Id3Helper.createTrackFromPath(
                    id = trackId,
                    folderPath = folderPath,
                    fileName = fileName,
                    streamUrl = streamUrl,
                    driveModifiedTime = modifiedTime
                )

                if (existingIdx >= 0) {
                    val prev = existing[existingIdx]
                    existing[existingIdx] = track.copy(
                        isDownloaded = prev.isDownloaded,
                        isCached = prev.isCached,
                        localFilePath = prev.localFilePath,
                        lastCachedAt = prev.lastCachedAt
                    )
                    updatedCount++
                } else {
                    existing.add(track)
                    addedCount++
                }
            }
        }

        saveDriveTracks(existing)

        IncrementalSyncResult(
            addedTracks = addedCount,
            updatedTracks = updatedCount,
            deletedTracks = deletedCount
        )
    }
}
