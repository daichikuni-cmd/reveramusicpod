package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.LauncherDatabase
import com.example.data.LauncherRepository
import com.example.data.getInstalledApps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PackageChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_PACKAGE_REMOVED ||
            action == Intent.ACTION_PACKAGE_FULLY_REMOVED ||
            action == Intent.ACTION_PACKAGE_ADDED ||
            action == Intent.ACTION_PACKAGE_REPLACED ||
            action == Intent.ACTION_PACKAGE_CHANGED
        ) {
            val db = LauncherDatabase.getDatabase(context.applicationContext)
            val repository = LauncherRepository(db.launcherDao())

            CoroutineScope(Dispatchers.IO).launch {
                val apps = getInstalledApps(context.applicationContext)
                val installedPackages = apps.map { it.packageName }.toSet()
                if (installedPackages.isNotEmpty()) {
                    repository.cleanupUninstalledApps(installedPackages)
                }
            }
        }
    }
}
