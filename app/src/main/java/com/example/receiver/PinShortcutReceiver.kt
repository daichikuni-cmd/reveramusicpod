package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import com.example.data.LauncherDatabase
import com.example.data.LauncherRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PinShortcutReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == "com.android.launcher.action.INSTALL_SHORTCUT" || action == "com.android.launcher.action.ACTION_INSTALL_SHORTCUT") {
            val label = intent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME)
                ?: intent.getStringExtra("android.intent.extra.shortcut.NAME")
                ?: "Shortcut"

            val shortcutIntent: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT)
            }

            var url = shortcutIntent?.dataString
                ?: shortcutIntent?.toUri(Intent.URI_INTENT_SCHEME)
                ?: intent.getStringExtra("url")
                ?: ""

            if (url.startsWith("intent/#Intent") || url.startsWith("intent//#Intent")) {
                url = url.replaceFirst(Regex("^intent/+#Intent"), "intent:#Intent")
            }

            if (url.isNotEmpty()) {
                val db = LauncherDatabase.getDatabase(context.applicationContext)
                val repository = LauncherRepository(db.launcherDao())

                CoroutineScope(Dispatchers.IO).launch {
                    repository.addWebShortcutToHome(title = label, url = url, pageIndex = 0)
                }
                Toast.makeText(context.applicationContext, "「$label」のショートカットを追加しました", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
