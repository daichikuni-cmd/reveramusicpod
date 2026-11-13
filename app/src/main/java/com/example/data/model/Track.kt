package com.example.data.model

import com.example.ui.TrackInfo
import com.squareup.moshi.JsonClass

/**
 * Google Drive同期および共通楽曲モデル
 */
@JsonClass(generateAdapter = true)
data class Track(
    val id: String,
    val folderPath: String = "",
    val fileName: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val composer: String = "",
    val genre: String = "",
    val isCompilation: Boolean = false,
    val grouping: String = "",
    val folderName: String = "",
    val musicalForm: String = "",
    val trackNumber: Int = 0,
    val durationMs: Long = 0L,
    val streamUrl: String? = null,
    val driveModifiedTime: Long = 0L,
    val localFilePath: String? = null,
    val isDownloaded: Boolean = false,
    val isCached: Boolean = false,
    val isId3Edited: Boolean = false,
    val sizeBytes: Long = 0L,
    val lastCachedAt: Long = 0L
) {
    /**
     * UI・プレイヤー用のTrackInfoへ変換
     */
    fun toTrackInfo(): TrackInfo {
        val isLocal = folderPath.startsWith("端末") || localFilePath?.startsWith("content://") == true || (streamUrl == null && localFilePath != null && !localFilePath.contains("audio_cache"))
        val derivedFolderName = folderName.ifBlank { folderPath.substringAfterLast('/').ifBlank { album } }
        val derivedForm = musicalForm.ifBlank {
            com.example.util.MusicalFormClassifier.classify(
                grouping = grouping,
                title = title.ifBlank { fileName.substringBeforeLast('.') },
                album = album.ifBlank { derivedFolderName },
                folderName = derivedFolderName,
                folderUriString = if (isLocal) (localFilePath ?: "") else "drive://$folderPath",
                uri = localFilePath ?: (streamUrl ?: ""),
                genre = genre,
                composer = composer
            )
        }
        return TrackInfo(
            id = id,
            title = title.ifBlank { fileName.substringBeforeLast('.') },
            artist = artist.ifBlank { if (isLocal) "端末" else "Google Drive" },
            album = album.ifBlank { derivedFolderName },
            durationMs = durationMs,
            uri = localFilePath ?: (streamUrl ?: ""),
            albumArtUri = null,
            isDriveSource = !isLocal,
            folderUriString = if (isLocal) (localFilePath ?: "") else "drive://$folderPath",
            isDownloaded = isDownloaded || isCached || isLocal,
            composer = composer,
            genre = genre,
            isCompilation = isCompilation,
            grouping = grouping,
            folderName = derivedFolderName,
            musicalForm = derivedForm
        )
    }
}
