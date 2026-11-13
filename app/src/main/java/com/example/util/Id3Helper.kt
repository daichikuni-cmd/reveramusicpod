package com.example.util

import com.example.data.model.Track

/**
 * ファイルパスやファイル名から楽曲メタデータ（トラック番号、タイトル、アーティスト、アルバム）を推測・生成するヘルパー。
 */
object Id3Helper {

    fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0L) return "--:--"
        val totalSec = durationMs / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%d:%02d".format(min, sec)
    }

    /**
     * ID3v2タグ (TIT1: Content group description または GRP1) をストリームから抽出する
     */
    fun extractGroupingFromUri(context: android.content.Context, uri: android.net.Uri): String? {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val header = ByteArray(10)
                var bytesRead = 0
                while (bytesRead < 10) {
                    val r = inputStream.read(header, bytesRead, 10 - bytesRead)
                    if (r < 0) return null
                    bytesRead += r
                }
                // ID3ヘッダー検証 ("ID3")
                if (header[0] == 'I'.code.toByte() && header[1] == 'D'.code.toByte() && header[2] == '3'.code.toByte()) {
                    val majorVersion = header[3].toInt() and 0xFF // 3 for 2.3, 4 for 2.4
                    val tagSize = ((header[6].toInt() and 0x7F) shl 21) or
                            ((header[7].toInt() and 0x7F) shl 14) or
                            ((header[8].toInt() and 0x7F) shl 7) or
                            (header[9].toInt() and 0x7F)
                    val bufferLimit = tagSize.coerceAtMost(128 * 1024)
                    val buffer = ByteArray(bufferLimit)
                    var total = 0
                    while (total < bufferLimit) {
                        val count = inputStream.read(buffer, total, bufferLimit - total)
                        if (count < 0) break
                        total += count
                    }
                    var offset = 0
                    while (offset + 10 <= total) {
                        val frameId = String(buffer, offset, 4, Charsets.US_ASCII)
                        if (frameId.all { it == '\u0000' } || !frameId.all { it.isLetterOrDigit() }) {
                            break
                        }
                        val frameSize = if (majorVersion >= 4) {
                            ((buffer[offset + 4].toInt() and 0x7F) shl 21) or
                                    ((buffer[offset + 5].toInt() and 0x7F) shl 14) or
                                    ((buffer[offset + 6].toInt() and 0x7F) shl 7) or
                                    (buffer[offset + 7].toInt() and 0x7F)
                        } else {
                            ((buffer[offset + 4].toInt() and 0xFF) shl 24) or
                                    ((buffer[offset + 5].toInt() and 0xFF) shl 16) or
                                    ((buffer[offset + 6].toInt() and 0xFF) shl 8) or
                                    (buffer[offset + 7].toInt() and 0xFF)
                        }
                        if (frameSize <= 0 || offset + 10 + frameSize > total) {
                            break
                        }
                        if (frameId == "TIT1" || frameId == "GRP1") {
                            val encoding = buffer[offset + 10].toInt() and 0xFF
                            val contentOffset = offset + 11
                            val contentLength = (frameSize - 1).coerceAtLeast(0)
                            val text = when (encoding) {
                                1, 2 -> String(buffer, contentOffset, contentLength, Charsets.UTF_16).trim('\u0000', ' ')
                                3 -> String(buffer, contentOffset, contentLength, Charsets.UTF_8).trim('\u0000', ' ')
                                else -> String(buffer, contentOffset, contentLength, Charsets.ISO_8859_1).trim('\u0000', ' ')
                            }
                            if (text.isNotBlank()) return text
                        }
                        offset += 10 + frameSize
                    }
                }
            }
        } catch (_: Throwable) {
            // パース失敗時はnullを返し安全にキーワード判定へフォールバック
        }
        return null
    }

    fun createTrackFromPath(
        id: String,
        folderPath: String,
        fileName: String,
        streamUrl: String?,
        driveModifiedTime: Long
    ): Track {
        val nameWithoutExt = fileName.substringBeforeLast('.')
        // 例: "01 - Symphony No. 5" や "1. Track Name", "02 Track" 等からトラック番号を抽出
        val trackNumRegex = Regex("^([0-9]{1,3})[\\s._-]+(.*)$")
        val match = trackNumRegex.find(nameWithoutExt)

        val trackNumber = match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        val rawTitle = match?.groupValues?.getOrNull(2)?.trim()?.ifEmpty { nameWithoutExt } ?: nameWithoutExt

        val pathSegments = folderPath.split('/').filter { it.isNotBlank() }
        val folderName = pathSegments.lastOrNull() ?: ""
        val album = folderName.ifBlank { "Google Drive" }
        val artist = if (pathSegments.size >= 2) pathSegments[pathSegments.size - 2] else "Google Drive"
        val form = MusicalFormClassifier.classify(
            title = rawTitle,
            album = album,
            folderName = folderName,
            folderUriString = folderPath,
            uri = folderPath
        )

        return Track(
            id = id,
            folderPath = folderPath,
            fileName = fileName,
            title = rawTitle,
            artist = artist,
            album = album,
            folderName = folderName,
            musicalForm = form,
            trackNumber = trackNumber,
            durationMs = 0L,
            streamUrl = streamUrl,
            driveModifiedTime = driveModifiedTime,
            localFilePath = null,
            isDownloaded = false
        )
    }
}
