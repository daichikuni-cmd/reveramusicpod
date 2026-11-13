package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.math.min

object WebShortcutIconManager {
    private const val TAG = "WebShortcutIconManager"
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val _iconUpdateVersion = MutableStateFlow(0)
    val iconUpdateVersion = _iconUpdateVersion.asStateFlow()

    private fun getCacheDir(context: Context): File {
        val dir = File(context.filesDir, "shortcut_icons")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun hashUrl(url: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest(url.trim().toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            url.hashCode().toString()
        }
    }

    fun getCachedIconFile(context: Context, url: String): File? {
        if (url.isBlank()) return null
        val hash = hashUrl(url)
        val file = File(getCacheDir(context), "$hash.png")
        return if (file.exists() && file.length() > 0) file else null
    }

    fun fetchAndCacheAsync(context: Context, url: String, onComplete: ((Boolean) -> Unit)? = null) {
        if (url.isBlank()) {
            onComplete?.invoke(false)
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            val result = fetchAndCacheIcon(context, url)
            withContext(Dispatchers.Main) {
                if (result != null) {
                    _iconUpdateVersion.value = _iconUpdateVersion.value + 1
                }
                onComplete?.invoke(result != null)
            }
        }
    }

    suspend fun fetchAndCacheIcon(context: Context, rawUrl: String): File? = withContext(Dispatchers.IO) {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return@withContext null

        val targetUrl = if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            "https://$trimmed"
        } else {
            trimmed
        }

        val cacheFile = File(getCacheDir(context), "${hashUrl(rawUrl)}.png")

        try {
            val baseUri = Uri.parse(targetUrl)
            val baseHost = baseUri.host
            if (baseHost.isNullOrBlank()) return@withContext null

            // 1. Fetch HTML and Final Resolved URL
            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .build()

            var html: String? = null
            var finalUrl: String = targetUrl

            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        html = response.body?.string()
                        finalUrl = response.request.url.toString()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch HTML for $targetUrl: ${e.message}")
            }

            // Step 1: Web App Manifest (manifest.json) icons[] (maskable first, then largest size)
            if (!html.isNullOrEmpty()) {
                val manifestUrl = extractManifestUrl(html!!, finalUrl)
                if (manifestUrl != null) {
                    val iconFromManifest = fetchIconFromManifest(manifestUrl)
                    if (iconFromManifest != null && saveBitmapToFile(iconFromManifest, cacheFile, cropToSquare = false)) {
                        return@withContext cacheFile
                    }
                }
            }

            // Step 2: <link rel="apple-touch-icon"> (largest size, typically 180x180)
            if (!html.isNullOrEmpty()) {
                val appleTouchUrls = extractAppleTouchIcons(html!!, finalUrl)
                for (iconUrl in appleTouchUrls) {
                    val bitmap = downloadBitmap(iconUrl)
                    if (bitmap != null && saveBitmapToFile(bitmap, cacheFile, cropToSquare = false)) {
                        return@withContext cacheFile
                    }
                }
            }

            // Step 3: <link rel="icon" sizes="..."> (largest size first)
            if (!html.isNullOrEmpty()) {
                val linkIcons = extractLinkIcons(html!!, finalUrl)
                for (iconUrl in linkIcons) {
                    val bitmap = downloadBitmap(iconUrl)
                    if (bitmap != null && saveBitmapToFile(bitmap, cacheFile, cropToSquare = false)) {
                        return@withContext cacheFile
                    }
                }
            }

            // Step 4: Root /favicon.ico
            val rootFaviconUrl = "${baseUri.scheme ?: "https"}://${baseUri.host}/favicon.ico"
            val rootBitmap = downloadBitmap(rootFaviconUrl)
            if (rootBitmap != null && saveBitmapToFile(rootBitmap, cacheFile, cropToSquare = false)) {
                return@withContext cacheFile
            }

            // Step 5: og:image (crop to square, last resort)
            if (!html.isNullOrEmpty()) {
                val ogImageUrl = extractOgImage(html!!, finalUrl)
                if (ogImageUrl != null) {
                    val ogBitmap = downloadBitmap(ogImageUrl)
                    if (ogBitmap != null && saveBitmapToFile(ogBitmap, cacheFile, cropToSquare = true)) {
                        return@withContext cacheFile
                    }
                }
            }

            // Fallback: Google Favicon API
            val googleFaviconUrl = "https://www.google.com/s2/favicons?domain=${baseUri.host}&sz=128"
            val fallbackBitmap = downloadBitmap(googleFaviconUrl)
            if (fallbackBitmap != null && saveBitmapToFile(fallbackBitmap, cacheFile, cropToSquare = false)) {
                return@withContext cacheFile
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching icon for $rawUrl", e)
        }

        if (cacheFile.exists() && cacheFile.length() > 0) cacheFile else null
    }

    private fun extractManifestUrl(html: String, baseUrl: String): String? {
        val pattern = Pattern.compile("""<link\s+[^>]*rel=["'](?:manifest|alternate\s+manifest)["'][^>]*>""", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(html)
        if (matcher.find()) {
            val tag = matcher.group()
            val hrefPattern = Pattern.compile("""href=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE)
            val hrefMatcher = hrefPattern.matcher(tag)
            if (hrefMatcher.find()) {
                return resolveUrl(baseUrl, hrefMatcher.group(1))
            }
        }
        return null
    }

    private fun fetchIconFromManifest(manifestUrl: String): Bitmap? {
        try {
            val request = Request.Builder()
                .url(manifestUrl)
                .header("User-Agent", USER_AGENT)
                .build()

            val jsonString = client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            } ?: return null

            val json = JSONObject(jsonString)
            val iconsArray = json.optJSONArray("icons") ?: return null

            data class ManifestIconCandidate(
                val src: String,
                val maxDimension: Int,
                val isMaskable: Boolean
            )

            val candidates = mutableListOf<ManifestIconCandidate>()

            for (i in 0 until iconsArray.length()) {
                val iconObj = iconsArray.optJSONObject(i) ?: continue
                val src = iconObj.optString("src")
                if (src.isBlank()) continue

                val purpose = iconObj.optString("purpose", "").lowercase()
                val isMaskable = purpose.contains("maskable")

                val sizes = iconObj.optString("sizes", "").lowercase()
                var maxDim = 0
                if (sizes.contains("x")) {
                    for (part in sizes.split(" ")) {
                        val dims = part.split("x")
                        if (dims.size == 2) {
                            val w = dims[0].toIntOrNull() ?: 0
                            val h = dims[1].toIntOrNull() ?: 0
                            maxDim = maxOf(maxDim, maxOf(w, h))
                        }
                    }
                } else if (sizes == "any") {
                    maxDim = 512
                }

                candidates.add(ManifestIconCandidate(src, maxDim, isMaskable))
            }

            // Sort: maskable first, then largest maxDimension descending
            val sorted = candidates.sortedWith(
                compareByDescending<ManifestIconCandidate> { it.isMaskable }
                    .thenByDescending { it.maxDimension }
            )

            for (candidate in sorted) {
                val resolvedSrc = resolveUrl(manifestUrl, candidate.src)
                val bitmap = downloadBitmap(resolvedSrc)
                if (bitmap != null) {
                    return bitmap
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse manifest at $manifestUrl: ${e.message}")
        }
        return null
    }

    private data class IconLinkTag(val href: String, val size: Int)

    private fun extractAppleTouchIcons(html: String, baseUrl: String): List<String> {
        val pattern = Pattern.compile("""<link\s+[^>]*rel=["'](apple-touch-icon(?:-precomposed)?)["'][^>]*>""", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(html)
        val list = mutableListOf<IconLinkTag>()

        while (matcher.find()) {
            val tag = matcher.group()
            val href = extractAttribute(tag, "href") ?: continue
            val sizes = extractAttribute(tag, "sizes") ?: ""
            val size = parseMaxDimension(sizes, default = 180)
            list.add(IconLinkTag(resolveUrl(baseUrl, href), size))
        }

        return list.sortedByDescending { it.size }.map { it.href }
    }

    private fun extractLinkIcons(html: String, baseUrl: String): List<String> {
        val pattern = Pattern.compile("""<link\s+[^>]*rel=["'](?:shortcut\s+icon|icon)["'][^>]*>""", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(html)
        val list = mutableListOf<IconLinkTag>()

        while (matcher.find()) {
            val tag = matcher.group()
            val href = extractAttribute(tag, "href") ?: continue
            val sizes = extractAttribute(tag, "sizes") ?: ""
            val size = parseMaxDimension(sizes, default = 32)
            list.add(IconLinkTag(resolveUrl(baseUrl, href), size))
        }

        return list.sortedByDescending { it.size }.map { it.href }
    }

    private fun extractOgImage(html: String, baseUrl: String): String? {
        val pattern = Pattern.compile("""<meta\s+[^>]*(?:property|name)=["']og:image["'][^>]*>""", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(html)
        if (matcher.find()) {
            val tag = matcher.group()
            val content = extractAttribute(tag, "content") ?: return null
            return resolveUrl(baseUrl, content)
        }
        return null
    }

    private fun extractAttribute(tag: String, attrName: String): String? {
        val pattern = Pattern.compile("""$attrName=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(tag)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun parseMaxDimension(sizes: String, default: Int): Int {
        if (sizes.isBlank()) return default
        var maxDim = 0
        for (part in sizes.lowercase().split(" ")) {
            val dims = part.split("x")
            if (dims.size == 2) {
                val w = dims[0].toIntOrNull() ?: 0
                val h = dims[1].toIntOrNull() ?: 0
                maxDim = maxOf(maxDim, maxOf(w, h))
            }
        }
        return if (maxDim > 0) maxDim else default
    }

    private fun resolveUrl(baseUrl: String, relativeUrl: String): String {
        val trimmed = relativeUrl.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed
        }
        return try {
            val baseUri = Uri.parse(baseUrl)
            if (trimmed.startsWith("//")) {
                "${baseUri.scheme ?: "https"}:$trimmed"
            } else if (trimmed.startsWith("/")) {
                "${baseUri.scheme ?: "https"}://${baseUri.authority}$trimmed"
            } else {
                val basePath = baseUri.path ?: "/"
                val parentPath = if (basePath.endsWith("/")) basePath else basePath.substringBeforeLast("/", "") + "/"
                "${baseUri.scheme ?: "https"}://${baseUri.authority}$parentPath$trimmed"
            }
        } catch (e: Exception) {
            trimmed
        }
    }

    private fun downloadBitmap(url: String): Bitmap? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes()
                    if (bytes != null && bytes.isNotEmpty()) {
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    } else null
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun saveBitmapToFile(bitmap: Bitmap, targetFile: File, cropToSquare: Boolean): Boolean {
        return try {
            val finalBitmap = if (cropToSquare) {
                cropCenterSquare(bitmap)
            } else {
                bitmap
            }

            FileOutputStream(targetFile).use { out ->
                finalBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            }
            targetFile.exists() && targetFile.length() > 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save bitmap to file ${targetFile.absolutePath}", e)
            false
        }
    }

    private fun cropCenterSquare(srcBmp: Bitmap): Bitmap {
        val width = srcBmp.width
        val height = srcBmp.height
        if (width == height) return srcBmp

        val squareSize = min(width, height)
        val xOffset = (width - squareSize) / 2
        val yOffset = (height - squareSize) / 2

        return Bitmap.createBitmap(srcBmp, xOffset, yOffset, squareSize, squareSize)
    }
}
