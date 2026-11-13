package com.example.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

import java.text.Normalizer
import java.util.Locale

data class AppInfo(
    val packageName: String,
    val className: String,
    val label: String,
    val alternateLabels: List<String> = emptyList(),
    val firstInstallTime: Long = 0L
)

fun normalizeForSearch(text: String): String {
    if (text.isBlank()) return ""
    // 1. NFKC normalization (half-width Katakana -> full-width, full-width ASCII -> standard ASCII)
    val nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC)
    
    // 2. Convert Katakana (\u30A1..\u30F6) to Hiragana (\u3041..\u3096)
    val sb = StringBuilder(nfkc.length)
    for (i in 0 until nfkc.length) {
        val ch = nfkc[i]
        if (ch in '\u30A1'..'\u30F6') {
            sb.append((ch.code - 0x60).toChar())
        } else {
            sb.append(ch)
        }
    }
    
    // 3. Lowercase using ROOT locale
    return sb.toString().lowercase(Locale.ROOT)
}

private fun getAppLabelForLocale(context: Context, info: android.content.pm.ResolveInfo, locale: Locale): String? {
    return try {
        val pm = context.packageManager
        val packageName = info.activityInfo.packageName
        val labelRes = if (info.activityInfo.labelRes != 0) {
            info.activityInfo.labelRes
        } else {
            info.activityInfo.applicationInfo.labelRes
        }
        if (labelRes != 0) {
            val res = pm.getResourcesForApplication(packageName)
            val config = Configuration(res.configuration)
            config.setLocale(locale)
            val metrics = res.displayMetrics
            @Suppress("DEPRECATION")
            val localizedRes = Resources(res.assets, metrics, config)
            val str = localizedRes.getString(labelRes)
            if (str.isNotBlank()) str else null
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}

val englishFirstComparator = Comparator<AppInfo> { app1, app2 ->
    val label1 = app1.label.trim()
    val label2 = app2.label.trim()
    if (label1.isEmpty() && label2.isEmpty()) return@Comparator 0
    if (label1.isEmpty()) return@Comparator 1
    if (label2.isEmpty()) return@Comparator -1

    val char1 = label1.first()
    val char2 = label2.first()

    val isEng1 = char1 in 'A'..'Z' || char1 in 'a'..'z' || char1 in '0'..'9'
    val isEng2 = char2 in 'A'..'Z' || char2 in 'a'..'z' || char2 in '0'..'9'

    if (isEng1 && !isEng2) {
        -1
    } else if (!isEng1 && isEng2) {
        1
    } else {
        label1.lowercase(Locale.ENGLISH).compareTo(label2.lowercase(Locale.ENGLISH))
    }
}

fun getInstalledApps(context: Context): List<AppInfo> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN, null).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }
    
    val resolveInfos = try {
        pm.queryIntentActivities(intent, 0)
    } catch (e: Exception) {
        emptyList()
    }

    return resolveInfos.map { info ->
        val pkgName = info.activityInfo.packageName
        val installTime = try {
            pm.getPackageInfo(pkgName, 0).firstInstallTime
        } catch (e: Exception) {
            0L
        }

        val primaryLabel = info.loadLabel(pm).toString()
        val jaLabel = getAppLabelForLocale(context, info, Locale.JAPAN) ?: getAppLabelForLocale(context, info, Locale.JAPANESE)
        val enLabel = getAppLabelForLocale(context, info, Locale.ENGLISH) ?: getAppLabelForLocale(context, info, Locale.US)

        val alternateList = mutableListOf<String>()
        if (!jaLabel.isNullOrBlank() && jaLabel != primaryLabel) {
            alternateList.add(jaLabel)
        }
        if (!enLabel.isNullOrBlank() && enLabel != primaryLabel && enLabel != jaLabel) {
            alternateList.add(enLabel)
        }

        AppInfo(
            packageName = pkgName,
            className = info.activityInfo.name,
            label = primaryLabel,
            alternateLabels = alternateList,
            firstInstallTime = installTime
        )
    }.distinctBy { it.packageName + "/" + it.className }
     .sortedWith(englishFirstComparator)
}

fun Drawable.toImageBitmap(): ImageBitmap {
    if (this is BitmapDrawable) {
        return this.bitmap.asImageBitmap()
    }
    val width = if (intrinsicWidth > 0) intrinsicWidth else 128
    val height = if (intrinsicHeight > 0) intrinsicHeight else 128
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, width, height)
    draw(canvas)
    return bitmap.asImageBitmap()
}
