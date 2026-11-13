package com.example.util

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.MainActivity
import kotlin.system.exitProcess

/**
 * ホームアプリ専用のクラッシュ復旧マネージャー。
 * 未捕捉例外（UncaughtException）発生時、端末画面が暗転して操作不能になるのを防ぐため、
 * 即座にMainActivityを自動再起動します。
 * 連続クラッシュを検知した場合は「セーフモード（再生状態をリセット）」フラグを立てて無限ループを防ぎます。
 */
object CrashRecoveryManager {
    private const val TAG = "CrashRecoveryManager"
    private const val PREFS_NAME = "crash_recovery_prefs"
    private const val KEY_CRASH_COUNT = "consecutive_crash_count"
    private const val KEY_LAST_CRASH_TIME = "last_crash_timestamp"
    private const val KEY_SAFE_MODE = "is_safe_mode"
    private const val KEY_LAST_CRASH_MESSAGE = "last_crash_message"
    private const val KEY_LAST_CRASH_STACK = "last_crash_stack"

    private const val MAX_CRASH_COUNT_FOR_SAFE_MODE = 3
    private const val CRASH_INTERVAL_WINDOW_MS = 15_000L // 15秒以内の連続クラッシュを監視

    fun install(application: Application) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception in thread ${thread.name}: ${throwable.message}", throwable)

            try {
                val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit()
                    .putString(KEY_LAST_CRASH_MESSAGE, "${throwable.javaClass.simpleName}: ${throwable.message}")
                    .putString(KEY_LAST_CRASH_STACK, throwable.stackTraceToString())
                    .commit()
                handleCrash(application)
            } catch (e: Exception) {
                Log.e(TAG, "Error during crash recovery execution", e)
            } finally {
                // デフォルトのハンドラーを呼び出すかプロセスを安全に終了
                if (defaultHandler != null) {
                    defaultHandler.uncaughtException(thread, throwable)
                } else {
                    exitProcess(1)
                }
            }
        }
    }

    private fun handleCrash(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastCrashTime = prefs.getLong(KEY_LAST_CRASH_TIME, 0L)
        val currentCount = if (now - lastCrashTime < CRASH_INTERVAL_WINDOW_MS) {
            prefs.getInt(KEY_CRASH_COUNT, 0) + 1
        } else {
            1
        }

        val enterSafeMode = currentCount >= MAX_CRASH_COUNT_FOR_SAFE_MODE

        prefs.edit()
            .putLong(KEY_LAST_CRASH_TIME, now)
            .putInt(KEY_CRASH_COUNT, currentCount)
            .putBoolean(KEY_SAFE_MODE, enterSafeMode)
            .commit()

        Log.w(TAG, "Crash count: $currentCount. Safe mode triggered: $enterSafeMode")

        // 自動再起動Intentを登録
        val restartIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra("EXTRA_RESTARTED_FROM_CRASH", true)
            putExtra("EXTRA_SAFE_MODE", enterSafeMode)
        }

        val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_ONE_SHOT
        }

        val pendingIntent = try {
            PendingIntent.getActivity(
                context,
                9999,
                restartIntent,
                flags
            )
        } catch (e: Exception) {
            null
        }

        if (pendingIntent != null) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                alarmManager?.set(
                    AlarmManager.RTC,
                    System.currentTimeMillis() + 500,
                    pendingIntent
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to schedule restart with AlarmManager", e)
            }
        }
    }

    /**
     * アプリが正常に一定時間稼働した場合、クラッシュカウントをリセットします。
     */
    fun onAppHealthy(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_CRASH_COUNT, 0) > 0 || prefs.getBoolean(KEY_SAFE_MODE, false)) {
            prefs.edit()
                .putInt(KEY_CRASH_COUNT, 0)
                .putBoolean(KEY_SAFE_MODE, false)
                .apply()
            Log.i(TAG, "Crash recovery status reset to healthy.")
        }
    }

    fun isSafeModeActive(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SAFE_MODE, false)
    }

    fun getLastCrashMessage(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LAST_CRASH_MESSAGE, null)
    }

    fun clearLastCrash(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_LAST_CRASH_MESSAGE)
            .remove(KEY_LAST_CRASH_STACK)
            .apply()
    }
}
