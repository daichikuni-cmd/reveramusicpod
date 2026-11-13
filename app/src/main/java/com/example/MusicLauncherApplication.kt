package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.util.CrashRecoveryManager

/**
 * 古い端末向けに最適化されたApplicationクラス。
 * 起動時にCrashRecoveryManagerをインストールし、Coil画像ローダーのメモリ消費を抑制します。
 */
class MusicLauncherApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        // ランチャー常駐クラッシュ防止ハンドラー
        CrashRecoveryManager.install(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    // 古い端末のRAMを圧迫しないよう最大メモリの15%に制限
                    .maxSizePercent(0.15)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("album_art_cache"))
                    .maxSizeBytes(50L * 1024 * 1024) // 50MB
                    .build()
            }
            .respectCacheHeaders(false)
            .build()
    }
}
