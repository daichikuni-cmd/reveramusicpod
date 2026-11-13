package com.example.player

import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import androidx.collection.LruCache
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaSession
import com.example.MainActivity
import com.example.service.MusicPlaybackService
import com.example.ui.PlaybackUiState
import com.example.ui.TrackInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 音楽再生コアマネージャー。ExoPlayerをラップし、Media3 MediaSession連携、
 * ロック画面および通知バーへのアルバムアート完全反映、イヤホン/Bluetooth切断時の自動一時停止、
 * 再生状態（PlaybackUiState）のリアルタイム更新を管理します。
 */
@OptIn(UnstableApi::class)
class MusicPlayerManager(private val context: Context) {

    companion object {
        var activeSession: MediaSession? = null
    }

    private val artworkCache = LruCache<String, ByteArray>(30)

    fun clearArtworkCache() {
        artworkCache.evictAll()
    }

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var progressJob: Job? = null

    private val _playbackState = MutableStateFlow(PlaybackUiState())
    val playbackState: StateFlow<PlaybackUiState> = _playbackState.asStateFlow()

    private var currentPlaylist: List<TrackInfo> = emptyList()
    private var currentIndex: Int = -1

    private var isNoisyReceiverRegistered = false
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            val action = intent?.action
            if (action == AudioManager.ACTION_AUDIO_BECOMING_NOISY ||
                action == BluetoothDevice.ACTION_ACL_DISCONNECTED ||
                action == BluetoothAdapter.ACTION_STATE_CHANGED
            ) {
                if (player.isPlaying) {
                    player.pause()
                    _playbackState.value = _playbackState.value.copy(isPlaying = false)
                    Log.d("MusicPlayerManager", "Audio becoming noisy or BT disconnected: paused playback")
                }
            }
        }
    }

    private val monoAudioProcessor = MonoAudioProcessor()

    fun setMonoAudioEnabled(enabled: Boolean) {
        monoAudioProcessor.isMonoEnabled = enabled
    }

    private val player: ExoPlayer by lazy {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): androidx.media3.exoplayer.audio.AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(monoAudioProcessor))
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
            }
        }

        ExoPlayer.Builder(context, renderersFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build().apply {
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _playbackState.value = _playbackState.value.copy(
                            isPlaying = isPlaying,
                            currentPositionMs = currentPosition.coerceAtLeast(0L),
                            durationMs = duration.coerceAtLeast(0L)
                        )
                        if (isPlaying) {
                            startProgressTicker()
                        } else {
                            stopProgressTicker()
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            _playbackState.value = _playbackState.value.copy(
                                durationMs = duration.coerceAtLeast(0L)
                            )
                        } else if (playbackState == Player.STATE_ENDED) {
                            skipNext()
                        }
                    }
                })
            }
    }

    init {
        try {
            activeSession?.release()
            activeSession = null
        } catch (_: Exception) {}

        try {
            val sessionActivityIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                sessionActivityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )

            val session = MediaSession.Builder(context, player)
                .setId("ReveraMusicPodSession_${System.currentTimeMillis()}")
                .setSessionActivity(pendingIntent)
                .build()
            activeSession = session
        } catch (e: Exception) {
            Log.e("MusicPlayerManager", "Failed to build MediaSession", e)
        }
        registerNoisyReceiver()
    }

    private fun registerNoisyReceiver() {
        if (isNoisyReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(noisyReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(noisyReceiver, filter)
            }
            isNoisyReceiverRegistered = true
        } catch (e: Exception) {
            Log.w("MusicPlayerManager", "Failed to register noisy receiver", e)
        }
    }

    private fun unregisterNoisyReceiver() {
        if (!isNoisyReceiverRegistered) return
        try {
            context.unregisterReceiver(noisyReceiver)
        } catch (_: Exception) {}
        isNoisyReceiverRegistered = false
    }

    private fun startProgressTicker() {
        stopProgressTicker()
        progressJob = scope.launch {
            while (isActive) {
                if (player.isPlaying) {
                    val pos = player.currentPosition.coerceAtLeast(0L)
                    val dur = player.duration.coerceAtLeast(0L)
                    _playbackState.value = _playbackState.value.copy(
                        currentPositionMs = pos,
                        durationMs = dur
                    )
                }
                delay(400L)
            }
        }
    }

    private fun stopProgressTicker() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun downsampleArtworkBytes(rawBytes: ByteArray, maxDim: Int = 512): ByteArray? {
        try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, options)
            val w = options.outWidth
            val h = options.outHeight
            if (w <= 0 || h <= 0) return null

            // トリミング（切り抜き）は一切行わず、元のアスペクト比を完全に保持したまま安全サイズにスケーリング
            var inSampleSize = 1
            while (w / inSampleSize > maxDim || h / inSampleSize > maxDim) {
                inSampleSize *= 2
            }

            if (inSampleSize == 1 && rawBytes.size <= 160 * 1024) {
                return rawBytes
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOptions) ?: return null
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
            bitmap.recycle()
            val compressed = baos.toByteArray()
            return if (compressed.isNotEmpty()) compressed else null
        } catch (e: Exception) {
            Log.w("MusicPlayerManager", "Failed to downsample artwork", e)
            return null
        }
    }

    private fun extractArtworkBytes(context: Context, track: TrackInfo): ByteArray? {
        val cached = artworkCache.get(track.uri)
        if (cached != null) return cached

        // 1. albumArtUri content resolver check
        try {
            if (!track.albumArtUri.isNullOrEmpty()) {
                val uri = Uri.parse(track.albumArtUri)
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bytes = stream.readBytes()
                    if (bytes.isNotEmpty()) {
                        val downsampled = downsampleArtworkBytes(bytes)
                        if (downsampled != null) {
                            artworkCache.put(track.uri, downsampled)
                            return downsampled
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. MediaMetadataRetriever embedded picture (ID3, VorbisComment, FLAC)
        var retriever: MediaMetadataRetriever? = null
        try {
            retriever = MediaMetadataRetriever()
            if (track.uri.startsWith("content://")) {
                retriever.setDataSource(context, Uri.parse(track.uri))
            } else {
                retriever.setDataSource(track.uri)
            }
            val picture = retriever.embeddedPicture
            if (picture != null && picture.isNotEmpty()) {
                val downsampled = downsampleArtworkBytes(picture)
                if (downsampled != null) {
                    artworkCache.put(track.uri, downsampled)
                    return downsampled
                }
            }
        } catch (_: Exception) {}
        finally {
            try {
                retriever?.release()
            } catch (_: Exception) {}
        }

        // 3. 同一フォルダ内の画像ファイル（cover.jpg, folder.jpg, front.jpg, album.jpg 等）の探索
        try {
            val folderCover = findCoverImageInFolder(context, track)
            if (folderCover != null) {
                val downsampled = downsampleArtworkBytes(folderCover)
                if (downsampled != null) {
                    artworkCache.put(track.uri, downsampled)
                    return downsampled
                }
            }
        } catch (_: Exception) {}

        return null
    }

    private fun findCoverImageInFolder(context: Context, track: TrackInfo): ByteArray? {
        val imageNames = listOf("cover", "folder", "album", "front", "artwork")
        val imageExtensions = listOf(".jpg", ".jpeg", ".png", ".webp")

        // SAF Folder Uri
        if (!track.folderUriString.isNullOrEmpty()) {
            try {
                val folderUri = Uri.parse(track.folderUriString)
                val folderDoc = DocumentFile.fromTreeUri(context, folderUri)
                val coverDoc = folderDoc?.listFiles()?.firstOrNull { file ->
                    val name = file.name?.lowercase() ?: ""
                    imageNames.any { n -> name.startsWith(n) } && imageExtensions.any { ext -> name.endsWith(ext) }
                }
                if (coverDoc != null) {
                    context.contentResolver.openInputStream(coverDoc.uri)?.use { stream ->
                        val b = stream.readBytes()
                        if (b.isNotEmpty()) return b
                    }
                }
            } catch (_: Exception) {}
        }

        // Direct File Path
        try {
            val path = if (track.uri.startsWith("file://")) Uri.parse(track.uri).path else track.uri
            if (path != null && path.startsWith("/")) {
                val dir = File(path).parentFile
                val coverFile = dir?.listFiles()?.firstOrNull { file ->
                    val name = file.name.lowercase()
                    imageNames.any { n -> name.startsWith(n) } && imageExtensions.any { ext -> name.endsWith(ext) }
                }
                if (coverFile != null && coverFile.exists()) {
                    return coverFile.readBytes()
                }
            }
        } catch (_: Exception) {}

        return null
    }

    fun playTrack(track: TrackInfo, playlist: List<TrackInfo> = listOf(track)) {
        try {
            currentPlaylist = playlist
            currentIndex = playlist.indexOfFirst { it.uri == track.uri }.let { if (it >= 0) it else 0 }

            val artworkBytes = extractArtworkBytes(context, track)

            val metadataBuilder = MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setAlbumTitle(track.album)

            if (artworkBytes != null) {
                metadataBuilder.setArtworkData(artworkBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            } else if (!track.albumArtUri.isNullOrEmpty()) {
                metadataBuilder.setArtworkUri(Uri.parse(track.albumArtUri))
            }

            val builtMetadata = metadataBuilder.build()

            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse(track.uri))
                .setMediaMetadata(builtMetadata)
                .build()

            player.setMediaItem(mediaItem)
            player.setPlaylistMetadata(builtMetadata)
            player.prepare()
            player.play()

            // Media3 Service常駐化によるロック画面コントロール & 通知生成
            val serviceIntent = Intent(context, MusicPlaybackService::class.java)
            try {
                context.startService(serviceIntent)
            } catch (e: Exception) {
                try {
                    ContextCompat.startForegroundService(context, serviceIntent)
                } catch (_: Exception) {}
                Log.w("MusicPlayerManager", "Failed to start MusicPlaybackService", e)
            }

            _playbackState.value = _playbackState.value.copy(
                currentTrack = track,
                isPlaying = true,
                currentPositionMs = 0L,
                durationMs = track.durationMs,
                currentPlaylist = playlist,
                currentIndex = currentIndex,
                artworkBytes = artworkBytes
            )
        } catch (e: Exception) {
            Log.e("MusicPlayerManager", "Failed to play track: ${track.title}", e)
        }
    }

    fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
            _playbackState.value = _playbackState.value.copy(isPlaying = false)
        } else {
            if (player.mediaItemCount > 0) {
                player.play()
                _playbackState.value = _playbackState.value.copy(isPlaying = true)
            } else if (currentPlaylist.isNotEmpty()) {
                playTrack(currentPlaylist.first(), currentPlaylist)
            }
        }
    }

    fun skipNext() {
        if (currentPlaylist.isEmpty()) return
        val nextIdx = if (_playbackState.value.isShuffle) {
            (currentPlaylist.indices).random()
        } else {
            (currentIndex + 1) % currentPlaylist.size
        }
        playTrack(currentPlaylist[nextIdx], currentPlaylist)
    }

    fun skipPrevious() {
        if (currentPlaylist.isEmpty()) return
        if (player.currentPosition > 3000L) {
            player.seekTo(0L)
            return
        }
        val prevIdx = if (currentIndex - 1 < 0) currentPlaylist.size - 1 else currentIndex - 1
        playTrack(currentPlaylist[prevIdx], currentPlaylist)
    }

    fun skipToQueueIndex(index: Int) {
        if (index in currentPlaylist.indices) {
            playTrack(currentPlaylist[index], currentPlaylist)
        }
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        _playbackState.value = _playbackState.value.copy(currentPositionMs = positionMs)
    }

    fun toggleShuffle() {
        val newShuffle = !_playbackState.value.isShuffle
        _playbackState.value = _playbackState.value.copy(isShuffle = newShuffle)
    }

    fun toggleRepeat() {
        val newRepeat = (_playbackState.value.repeatMode + 1) % 3
        player.repeatMode = when (newRepeat) {
            1 -> Player.REPEAT_MODE_ALL
            2 -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        _playbackState.value = _playbackState.value.copy(repeatMode = newRepeat)
    }

    fun setVolume(volume: Float) {
        player.volume = volume.coerceIn(0f, 1f)
    }

    fun stop() {
        stopProgressTicker()
        player.stop()
        player.clearMediaItems()
        _playbackState.value = _playbackState.value.copy(
            isPlaying = false,
            currentPositionMs = 0L
        )
    }

    fun release() {
        stopProgressTicker()
        unregisterNoisyReceiver()
        activeSession?.release()
        activeSession = null
        player.release()
    }
}

/**
 * ステレオ音声をリアルタイムでモノラル（L+R平均化ダウンミックス）に変換するAudioProcessor。
 */
@OptIn(UnstableApi::class)
class MonoAudioProcessor : AudioProcessor {
    @Volatile
    var isMonoEnabled: Boolean = false

    private var inputAudioFormat: AudioProcessor.AudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var buffer: java.nio.ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var outputBuffer: java.nio.ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded: Boolean = false

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        this.inputAudioFormat = inputAudioFormat
        return inputAudioFormat
    }

    override fun isActive(): Boolean = inputAudioFormat.encoding != C.ENCODING_INVALID

    override fun queueInput(inputBuffer: java.nio.ByteBuffer) {
        val position = inputBuffer.position()
        val limit = inputBuffer.limit()
        val remaining = limit - position

        if (buffer.capacity() < remaining) {
            buffer = java.nio.ByteBuffer.allocateDirect(remaining).order(java.nio.ByteOrder.nativeOrder())
        } else {
            buffer.clear()
        }

        if (isMonoEnabled && inputAudioFormat.channelCount == 2) {
            while (inputBuffer.position() < limit) {
                val left = inputBuffer.short
                val right = inputBuffer.short
                val mono = ((left.toInt() + right.toInt()) / 2).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                buffer.putShort(mono)
                buffer.putShort(mono)
            }
        } else {
            buffer.put(inputBuffer)
        }

        inputBuffer.position(limit)
        buffer.flip()
        outputBuffer = buffer
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): java.nio.ByteBuffer {
        val output = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return output
    }

    override fun isEnded(): Boolean = inputEnded && outputBuffer == AudioProcessor.EMPTY_BUFFER

    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
    }

    override fun reset() {
        flush()
        buffer = AudioProcessor.EMPTY_BUFFER
        inputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
    }
}
