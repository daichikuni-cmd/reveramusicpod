package com.example.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.example.ui.TrackInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Storage Access Framework (SAF) 経由で選択されたフォルダ内のオーディオファイルを再帰的にスキャンするユーティリティ。
 * 古い端末でも軽量かつ安全に動作するよう設計。
 */
object SafFolderScanner {
    private const val TAG = "SafFolderScanner"

    private val AUDIO_EXTENSIONS = setOf(
        "mp3", "flac", "m4a", "aac", "wav", "ogg", "opus", "wma", "alac", "aiff"
    )

    suspend fun scanFolder(
        context: Context,
        treeUri: Uri,
        onProgress: ((count: Int) -> Unit)? = null
    ): Pair<String, List<TrackInfo>> = withContext(Dispatchers.IO) {
        val rootDoc = try {
            DocumentFile.fromTreeUri(context, treeUri)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve treeUri: $treeUri", e)
            null
        } ?: return@withContext ("Music Folder" to emptyList())

        val folderName = rootDoc.name ?: "Music Folder"
        val tracks = mutableListOf<TrackInfo>()
        val retriever = MediaMetadataRetriever()

        try {
            scanDirectoryRecursively(context, rootDoc, tracks, retriever, onProgress)
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // ignore
            }
        }

        // アルバム・トラック名順でソート
        val sortedTracks = tracks.sortedWith(
            compareBy<TrackInfo> { it.album.lowercase() }
                .thenBy { it.artist.lowercase() }
                .thenBy { it.title.lowercase() }
        )

        folderName to sortedTracks
    }

    private fun scanDirectoryRecursively(
        context: Context,
        dir: DocumentFile,
        outList: MutableList<TrackInfo>,
        retriever: MediaMetadataRetriever,
        onProgress: ((count: Int) -> Unit)?
    ) {
        val files = dir.listFiles()
        for (file in files) {
            if (file.isDirectory) {
                scanDirectoryRecursively(context, file, outList, retriever, onProgress)
            } else if (file.isFile) {
                val name = file.name ?: continue
                val ext = name.substringAfterLast('.', "").lowercase()
                val mime = file.type?.lowercase() ?: ""

                if (AUDIO_EXTENSIONS.contains(ext) || mime.startsWith("audio/")) {
                    val uri = file.uri
                    var title = name.substringBeforeLast('.')
                    var artist = "Unknown Artist"
                    var album = dir.name ?: "Unknown Album"
                    var duration = 0L
                    var composer = ""
                    var genre = ""
                    var isCompilation = false

                    try {
                        retriever.setDataSource(context, uri)
                        val metaTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                        val metaArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                        val metaAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                        val metaDur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        val metaComposer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER)
                        val metaGenre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                        val metaCompilation = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPILATION)
                        } else null
                        val metaAlbumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)

                        if (!metaTitle.isNullOrBlank()) title = metaTitle.trim()
                        if (!metaArtist.isNullOrBlank()) artist = metaArtist.trim()
                        if (!metaAlbum.isNullOrBlank()) album = metaAlbum.trim()
                        if (!metaDur.isNullOrBlank()) duration = metaDur.toLongOrNull() ?: 0L
                        if (!metaComposer.isNullOrBlank()) composer = metaComposer.trim()
                        if (!metaGenre.isNullOrBlank()) genre = metaGenre.trim()
                        isCompilation = metaCompilation == "1" ||
                                metaAlbumArtist?.contains("Various", ignoreCase = true) == true ||
                                dir.name?.contains("Compilation", ignoreCase = true) == true
                    } catch (e: Exception) {
                        // メタデータ取得不可時はファイル名をそのまま使用
                    }

                    var grouping = ""
                    try {
                        grouping = Id3Helper.extractGroupingFromUri(context, uri) ?: ""
                    } catch (_: Exception) {}

                    val folderName = dir.name ?: ""
                    val musicalForm = MusicalFormClassifier.classify(
                        grouping = grouping,
                        title = title,
                        album = album,
                        folderName = folderName,
                        folderUriString = uri.toString(),
                        uri = uri.toString(),
                        genre = genre,
                        composer = composer
                    )

                    outList.add(
                        TrackInfo(
                            id = uri.toString(),
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = duration,
                            uri = uri.toString(),
                            albumArtUri = null,
                            isDriveSource = false,
                            folderUriString = uri.toString(),
                            composer = composer,
                            genre = genre,
                            isCompilation = isCompilation,
                            grouping = grouping,
                            folderName = folderName,
                            musicalForm = musicalForm
                        )
                    )
                    onProgress?.invoke(outList.size)
                }
            }
        }
    }
}
