package com.example.data.repository

import android.content.Context
import com.example.data.dao.TrackDao
import com.example.data.drive.DriveFolderGroup
import com.example.data.drive.DriveRoot
import com.example.data.drive.GoogleDriveClient
import com.example.data.entity.TrackEntity
import com.example.data.model.EditedFolderInfo
import com.example.data.model.MusicalForm
import com.example.data.model.Track
import com.example.util.Id3Helper
import com.example.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class WifiRequiredException(message: String = "4G/モバイル通信保護のためWi-Fi接続が必要です") : Exception(message)

class MusicRepository(
    private val context: Context,
    private val trackDao: TrackDao
) {
    val allTracks: Flow<List<Track>> = trackDao.getAllTracks().map { list -> list.map { it.toModel() } }
    val cachedTracks: Flow<List<Track>> = trackDao.getCachedTracks().map { list -> list.map { it.toModel() } }
    val composers: Flow<List<String>> = trackDao.getDistinctComposers()
    val artists: Flow<List<String>> = trackDao.getDistinctArtists()
    val albums: Flow<List<String>> = trackDao.getDistinctAlbums()
    val musicalForms: Flow<List<String>> = trackDao.getDistinctMusicalForms()
    val folders: Flow<List<String>> = trackDao.getDistinctFolders()

    val editedTracks: Flow<List<Track>> = trackDao.getEditedTracks().map { list -> list.map { it.toModel() } }

    val editedFolders: Flow<List<EditedFolderInfo>> = trackDao.getEditedTracks().map { list ->
        list.groupBy { it.folderPath }.map { (folder, tracks) ->
            EditedFolderInfo(
                folderPath = folder,
                editedTrackCount = tracks.size,
                lastEditedTime = System.currentTimeMillis()
            )
        }.sortedByDescending { it.lastEditedTime }
    }

    suspend fun initializeIfEmpty() = withContext(Dispatchers.IO) {
        // Purge any previously inserted dummy mock tracks and non-music files completely
        trackDao.deleteDummyTracks()
        trackDao.deleteNonMusicTracks()
        reInferMusicalForms()
    }

    suspend fun reInferMusicalForms() = withContext(Dispatchers.IO) {
        val all = trackDao.getAllTracks().first()
        for (trackEntity in all) {
            if (!trackEntity.isId3Edited) {
                val inferredComposer = if (trackEntity.composer.isBlank() ||
                    trackEntity.composer == "Classical Master" ||
                    trackEntity.composer == "Classical Composer"
                ) {
                    val fromPath = Id3Helper.inferComposerFromPath(trackEntity.folderPath, trackEntity.album)
                    if (fromPath != "Classical Master") fromPath else trackEntity.composer
                } else {
                    trackEntity.composer
                }

                val inferredForm = MusicalForm.inferFromMetadata(
                    title = trackEntity.title,
                    album = trackEntity.album,
                    folderPath = trackEntity.folderPath,
                    composer = inferredComposer
                )

                var updated = trackEntity
                var changed = false
                if (inferredForm.name != trackEntity.musicalForm) {
                    updated = updated.copy(musicalForm = inferredForm.name)
                    changed = true
                }
                if (inferredComposer != trackEntity.composer) {
                    updated = updated.copy(composer = inferredComposer)
                    changed = true
                }
                if (changed) {
                    trackDao.update(updated)
                }
            }
        }
    }

    fun getTracksByComposer(composer: String): Flow<List<Track>> {
        return trackDao.getTracksByComposer(composer).map { list -> list.map { it.toModel() } }
    }

    fun getTracksByArtist(artist: String): Flow<List<Track>> {
        return trackDao.getTracksByArtist(artist).map { list -> list.map { it.toModel() } }
    }

    fun getTracksByAlbum(album: String): Flow<List<Track>> {
        return trackDao.getTracksByAlbum(album).map { list -> list.map { it.toModel() } }
    }

    fun getTracksByMusicalForm(form: String): Flow<List<Track>> {
        return trackDao.getTracksByMusicalForm(form).map { list -> list.map { it.toModel() } }
    }

    fun getTracksByFolder(folderPath: String): Flow<List<Track>> {
        return trackDao.getTracksByFolder(folderPath).map { list -> list.map { it.toModel() } }
    }

    suspend fun updateId3Tag(
        trackId: String,
        title: String,
        composer: String,
        artist: String,
        album: String,
        musicalForm: MusicalForm
    ) = withContext(Dispatchers.IO) {
        val existing = trackDao.getTrackById(trackId) ?: return@withContext
        val updated = existing.copy(
            title = title,
            composer = composer,
            artist = artist,
            album = album,
            musicalForm = musicalForm.name,
            isId3Edited = true,
            hasId3Tag = true
        )
        trackDao.update(updated)
    }

    /**
     * 明示的な楽曲ダウンロード（Wi-Fi保護付き）
     */
    suspend fun cacheTrack(track: Track, forceCellular: Boolean = false): Result<Track> = withContext(Dispatchers.IO) {
        if (!forceCellular && !NetworkUtils.isWifiConnected(context)) {
            return@withContext Result.failure(WifiRequiredException())
        }

        try {
            if (track.id.startsWith("drive_")) {
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
                    trackDao.updateCacheStatus(track.id, true, targetFile.absolutePath)
                    return@withContext Result.success(track.copy(isCached = true, localFilePath = targetFile.absolutePath))
                }
            }

            // Fallback for direct stream URL if any
            val cacheDir = File(context.cacheDir, "audio_cache").apply { mkdirs() }
            val sanitizedName = track.fileName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            val targetFile = File(cacheDir, "${track.id}_$sanitizedName")

            val url = URL(track.streamUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.connect()

            if (connection.responseCode in 200..299) {
                connection.inputStream.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                trackDao.updateCacheStatus(track.id, true, targetFile.absolutePath)
                Result.success(track.copy(isCached = true, localFilePath = targetFile.absolutePath))
            } else {
                Result.failure(Exception("ダウンロード失敗: HTTP ${connection.responseCode}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun cacheAlbum(album: String, forceCellular: Boolean = false): Result<Int> = withContext(Dispatchers.IO) {
        if (!forceCellular && !NetworkUtils.isWifiConnected(context)) {
            return@withContext Result.failure(WifiRequiredException())
        }
        val albumTracks = trackDao.getTracksByAlbum(album).first()
        var cachedCount = 0
        for (trackEntity in albumTracks) {
            if (!trackEntity.isCached) {
                cacheTrack(trackEntity.toModel(), forceCellular = true)
                cachedCount++
            }
        }
        Result.success(cachedCount)
    }

    suspend fun cacheFolder(folderPathPrefix: String, forceCellular: Boolean = false): Result<Int> = withContext(Dispatchers.IO) {
        if (!forceCellular && !NetworkUtils.isWifiConnected(context)) {
            return@withContext Result.failure(WifiRequiredException())
        }
        val all = trackDao.getAllTracks().first()
        var cachedCount = 0
        for (trackEntity in all) {
            if ((trackEntity.folderPath == folderPathPrefix || trackEntity.folderPath.startsWith("$folderPathPrefix/")) && !trackEntity.isCached) {
                cacheTrack(trackEntity.toModel(), forceCellular = true)
                cachedCount++
            }
        }
        Result.success(cachedCount)
    }

    suspend fun uncacheTrack(trackId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val trackEntity = trackDao.getTrackById(trackId) ?: return@withContext Result.success(Unit)
            trackEntity.localFilePath?.let { path ->
                val file = File(path)
                if (file.exists()) file.delete()
            }
            trackDao.updateCacheStatus(trackId, false, null)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uncacheFolder(folderPathPrefix: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val all = trackDao.getAllTracks().first()
            var deletedCount = 0
            for (trackEntity in all) {
                if (trackEntity.folderPath == folderPathPrefix || trackEntity.folderPath.startsWith("$folderPathPrefix/")) {
                    if (trackEntity.isCached) {
                        trackEntity.localFilePath?.let { path ->
                            val file = File(path)
                            if (file.exists()) file.delete()
                        }
                        deletedCount++
                    }
                }
            }
            trackDao.uncacheFolderTracks(folderPathPrefix, "$folderPathPrefix/%")
            Result.success(deletedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun cacheAllTracks(forceCellular: Boolean = false): Result<Int> = withContext(Dispatchers.IO) {
        if (!forceCellular && !NetworkUtils.isWifiConnected(context)) {
            return@withContext Result.failure(WifiRequiredException())
        }
        val all = trackDao.getAllTracks().first()
        var cachedCount = 0
        for (trackEntity in all) {
            if (!trackEntity.isCached) {
                cacheTrack(trackEntity.toModel(), forceCellular = true)
                cachedCount++
            }
        }
        Result.success(cachedCount)
    }

    suspend fun clearCache(): Long = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "audio_cache")
        var freedBytes = 0L
        if (cacheDir.exists()) {
            cacheDir.listFiles()?.forEach { file ->
                freedBytes += file.length()
                file.delete()
            }
        }
        trackDao.clearAllCacheStatus()
        freedBytes
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

    /**
     * Google Driveとの同期:
     * - トークンがあればDrive API v3でマイドライブ/共有ドライブをスキャン
     * - 音楽ファイル（mp3, m4a, aac, flac, ogg / wav除外）のみを取得
     * - 音楽ファイルが存在するフォルダのみ階層付きで登録
     * - トークンがない場合はダミーを挿入せず、未接続エラーを返却
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
                // 期限切れ時は強制更新して1度リトライ
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

        val existing = trackDao.getAllTracks().first()
        var updatedCount = 0
        var newCount = 0

        for (driveFile in allDriveTracks) {
            val matched = existing.find { it.id == driveFile.id }
            if (matched == null) {
                trackDao.insert(TrackEntity.fromModel(driveFile))
                newCount++
            } else if (driveFile.driveModifiedTime > matched.driveModifiedTime) {
                trackDao.update(
                    TrackEntity.fromModel(
                        driveFile.copy(isCached = matched.isCached, localFilePath = matched.localFilePath)
                    )
                )
                updatedCount++
            }
        }
        trackDao.deleteNonMusicTracks()

        SyncResult(
            newTracks = newCount,
            updatedTracks = updatedCount,
            folderCount = folderGroups.size,
            isRealApi = true,
            errorMessage = null
        )
    }

    suspend fun clearEditedFlags() = withContext(Dispatchers.IO) {
        trackDao.clearEditedFlags()
    }

    /**
     * Google Drive API v3: Changes APIを利用した差分同期
     * - 前回同期からの変更トークン（startPageToken）をもとに変更されたファイル・フォルダのみを即座に取得
     * - トークン未取得時や期限切れ時は自動的にフル同期へフォールバック
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
        val currentTrackCount = trackDao.getTrackCount()

        // トークン未保存またはDBが空の場合はフル同期を実行してstartPageTokenを確立
        if (savedToken.isNullOrBlank() || currentTrackCount == 0) {
            val fullRes = syncGoogleDrive()
            return@withContext IncrementalSyncResult(
                addedTracks = fullRes.newTracks,
                updatedTracks = fullRes.updatedTracks,
                deletedTracks = 0,
                isFullSyncFallback = true,
                errorMessage = fullRes.errorMessage
            )
        }

        // 差分変更を取得
        var diffResult = GoogleDriveClient.fetchDifferentialChanges(context, token, savedToken, root)
        if (diffResult.isFailure) {
            val err = diffResult.exceptionOrNull()
            if (err is GoogleDriveClient.TokenExpiredException) {
                // トークン無効/期限切れ時はフル同期へ自動フォールバック
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
            val existing = trackDao.getTrackById(trackId)
            val isRemovedOrTrashed = change.isRemoved || (change.fileObj?.optBoolean("trashed", false) == true)

            if (isRemovedOrTrashed) {
                if (existing != null) {
                    if (!existing.localFilePath.isNullOrBlank()) {
                        try {
                            File(existing.localFilePath).delete()
                        } catch (_: Exception) {}
                    }
                    trackDao.deleteTrackById(trackId)
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

                if (existing == null) {
                    val track = Id3Helper.createTrackFromPath(
                        id = trackId,
                        folderPath = folderPath,
                        fileName = fileName,
                        streamUrl = streamUrl,
                        driveModifiedTime = modifiedTime
                    )
                    trackDao.insert(TrackEntity.fromModel(track))
                    addedCount++
                } else {
                    if (modifiedTime > existing.driveModifiedTime || fileName != existing.fileName || folderPath != existing.folderPath) {
                        if (!existing.isId3Edited) {
                            val reInferred = Id3Helper.createTrackFromPath(
                                id = trackId,
                                folderPath = folderPath,
                                fileName = fileName,
                                streamUrl = streamUrl,
                                driveModifiedTime = modifiedTime
                            )
                            trackDao.update(
                                TrackEntity.fromModel(
                                    reInferred.copy(isCached = existing.isCached, localFilePath = existing.localFilePath)
                                )
                            )
                        } else {
                            trackDao.update(
                                existing.copy(
                                    fileName = fileName,
                                    folderPath = folderPath,
                                    streamUrl = streamUrl,
                                    driveModifiedTime = modifiedTime
                                )
                            )
                        }
                        updatedCount++
                    }
                }
            }
        }

        trackDao.deleteNonMusicTracks()

        IncrementalSyncResult(
            addedTracks = addedCount,
            updatedTracks = updatedCount,
            deletedTracks = deletedCount
        )
    }
}

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

data class SyncResult(
    val newTracks: Int,
    val updatedTracks: Int,
    val folderCount: Int = 0,
    val isRealApi: Boolean = false,
    val errorMessage: String? = null
)
