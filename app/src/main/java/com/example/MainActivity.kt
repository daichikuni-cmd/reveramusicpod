package com.example

import android.content.BroadcastReceiver
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import coil.Coil
import com.example.ui.DarkPureBackground
import com.example.ui.MusicLauncherApp
import com.example.ui.MusicLauncherViewModel
import com.example.ui.MusicTab
import com.example.ui.theme.MyApplicationTheme

/**
 * 音楽専用端末（DAP）としてのホームActivity。
 * singleTaskとCATEGORY_HOMEインテント、バックグラウンド復帰状態、
 * および古い端末向けのonTrimMemoryによる省メモリ制御を実装。
 */
class MainActivity : ComponentActivity() {

    private val viewModel: MusicLauncherViewModel by viewModels()
    private var isActivityResumed = false

    private var isScreenOffReceiverRegistered = false
    // 画面OFF時の省電力・状態保護用レシーバー
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                viewModel.onAppBackground()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 画面OFFレシーバー登録（安全に登録）
        try {
            val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
            registerReceiver(screenOffReceiver, filter)
            isScreenOffReceiverRegistered = true
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // クラッシュ検知とステータス復旧
        try {
            val lastCrash = com.example.util.CrashRecoveryManager.getLastCrashMessage(this)
            if (lastCrash != null) {
                android.util.Log.w("MainActivity", "Recovered from crash: $lastCrash")
                com.example.util.CrashRecoveryManager.clearLastCrash(this)
            }
            com.example.util.CrashRecoveryManager.onAppHealthy(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // ロック画面および通知領域のメディアコントロール表示権限（Android 13+）
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        // 没入型Edge-to-Edge（AMOLED漆黒）
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val currentTab by viewModel.currentTab.collectAsState()
                val screenDimFactor by viewModel.screenDimFactor.collectAsState()

                // スリープタイマー終了前の徐々な画面減光を適用
                LaunchedEffect(screenDimFactor) {
                    val lp = window.attributes
                    lp.screenBrightness = if (screenDimFactor < 0.99f) screenDimFactor else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    window.attributes = lp
                }

                // ホームアプリ仕様：StorageやSettingsにいる際に戻るボタンを押した場合はPlay画面に戻る
                BackHandler(enabled = currentTab != MusicTab.PLAY) {
                    viewModel.switchToPlayScreen()
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkPureBackground
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        MusicLauncherApp(viewModel = viewModel)

                        // 画面減光用のソフトウェアオーバーレイ (全端末・エミュレータで確実に画面減光を実現)
                        if (screenDimFactor < 0.99f) {
                            val overlayAlpha = ((1.0f - screenDimFactor) * 0.95f).coerceIn(0f, 0.95f)
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = overlayAlpha))
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isActivityResumed = true
        viewModel.onActivityResumed()
    }

    override fun onPause() {
        super.onPause()
        isActivityResumed = false
        viewModel.onAppBackground()
    }

    /**
     * 端末のホームボタン押下または他アプリからの復帰時
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        // CATEGORY_HOME または MAIN インテントの検知
        val isHomeIntent = intent.action == Intent.ACTION_MAIN &&
                (intent.hasCategory(Intent.CATEGORY_HOME) || intent.categories?.contains(Intent.CATEGORY_HOME) == true || intent.categories.isNullOrEmpty())

        if (isHomeIntent || intent.hasCategory(Intent.CATEGORY_HOME)) {
            // ホームボタンが押された時の挙動：
            // - 既にアプリが表示中なら、直ちにメインのPlay（再生）タブへ戻る
            // - 他アプリ（設定等）から戻ってきた直後（isActivityResumed == false）なら、直前タブを保持
            viewModel.onHomeIntentReceived(isActivityResumed = isActivityResumed)
        }
    }

    /**
     * 古い端末向けのメモリ保護（Low Memory Killer対策）
     * メモリ逼迫時に画像キャッシュ（アルバムアート等）を即座に解放
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        viewModel.trimMemoryCache()

        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW || level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            try {
                // Coilのインメモリ画像キャッシュを即時クリア
                Coil.imageLoader(this).memoryCache?.clear()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isScreenOffReceiverRegistered) {
            try {
                unregisterReceiver(screenOffReceiver)
                isScreenOffReceiverRegistered = false
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
