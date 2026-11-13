package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.MainActivity

/**
 * 端末起動完了（BOOT_COMPLETED）を受信し、DAP専用端末としてMusicpotを起動します。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i("BootReceiver", "Device boot completed. Preparing Musicpot Home...")
            val prefs = context.getSharedPreferences("music_launcher_prefs", Context.MODE_PRIVATE)
            val launchOnBoot = prefs.getBoolean("pref_launch_on_boot", true)
            if (launchOnBoot) {
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                context.startActivity(launchIntent)
            }
        }
    }
}
