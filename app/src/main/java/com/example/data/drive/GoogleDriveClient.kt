package com.example.data.drive

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.data.model.Track
import com.example.util.Id3Helper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.resume

enum class DriveRoot(val displayNameJa: String, val key: String) {
    MY_DRIVE("マイドライブ", "my_drive"),
    SHARED_DRIVES("共有ドライブ", "shared_drives");
    // パソコン(Computers)ルートはGoogle Drive API v3でデスクトップ同期APIの仕様が異なり
    // 端末ごとの差分管理が極めて複雑かつ制約が多いため、ユーザー指示に従い除外。
}

data class DriveFolderGroup(
    val folderId: String,
    val folderPath: String,
    val folderName: String,
    val tracks: List<Track>,
    val totalSizeBytes: Long,
    val rootType: DriveRoot
)

object GoogleDriveClient {
    private const val TAG = "GoogleDriveClient"
    const val OAUTH_CLIENT_ID = "1024363141006-40s5ftkj6n7f90m2i2aff7i1f81dbu1l.apps.googleusercontent.com"
    const val PROJECT_ID = "gen-lang-client-0977296177"
    const val PROJECT_NUMBER = "1024363141006"

    private const val PREFS_NAME = "google_drive_prefs"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_TOKEN_SAVED_AT = "token_saved_at_ms"
    private const val KEY_DRIVE_ROOT = "drive_root"

    private const val KEY_START_PAGE_TOKEN = "drive_start_page_token"

    // Supported extensions: mp3, m4a, aac, flac, ogg. (WAV is explicitly excluded as instructed by user)
    val SUPPORTED_EXTENSIONS = listOf(".mp3", ".m4a", ".aac", ".flac", ".ogg")

    fun getSavedStartPageToken(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_START_PAGE_TOKEN, null)
    }

    fun saveStartPageToken(context: Context, token: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_START_PAGE_TOKEN, token).apply()
    }

    fun getSavedAccessToken(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ACCESS_TOKEN, null)
    }

    fun saveAccessToken(context: Context, token: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, token)
            .putLong(KEY_TOKEN_SAVED_AT, if (token != null) System.currentTimeMillis() else 0L)
            .apply()
    }

    fun getTokenSavedAt(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_TOKEN_SAVED_AT, 0L)
    }

    /**
     * トークンが有効期限内（取得後50分未満）かどうかを判定
     */
    fun isTokenFresh(context: Context): Boolean {
        val token = getSavedAccessToken(context) ?: return false
        val savedAt = getTokenSavedAt(context)
        if (savedAt <= 0L) return false
        val fiftyMinutesMs = 50 * 60 * 1000L
        return (System.currentTimeMillis() - savedAt) < fiftyMinutesMs
    }

    /**
     * 非表示のバックグラウンドWebViewを用いて、ユーザー操作なしでGoogle認証URLを呼び出し、
     * 既存のログインセッションCookieを活用して即座に新しいアクセストークンを再取得する。
     */
    suspend fun silentRefreshAccessToken(context: Context): Result<String> = withContext(Dispatchers.Main) {
        val clientId = OAUTH_CLIENT_ID
        val redirectUri = "https://ais-dev-cucyrt7xkb3nmihyxxb52f-406625588047.asia-northeast1.run.app"
        val scope = "https://www.googleapis.com/auth/drive.readonly https://www.googleapis.com/auth/drive.file"
        val authUrl = "https://accounts.google.com/o/oauth2/v2/auth?" +
                "client_id=$clientId&" +
                "redirect_uri=$redirectUri&" +
                "response_type=token&" +
                "scope=${URLEncoder.encode(scope, "UTF-8")}"

        suspendCancellableCoroutine { continuation ->
            var webView: WebView? = null
            var isResumed = false

            fun finishWithResult(result: Result<String>) {
                if (!isResumed) {
                    isResumed = true
                    try {
                        webView?.stopLoading()
                        webView?.destroy()
                    } catch (e: Exception) {
                        Log.w(TAG, "Error cleaning up silent WebView", e)
                    }
                    webView = null
                    continuation.resume(result)
                }
            }

            try {
                val wv = WebView(context.applicationContext)
                webView = wv
                wv.settings.javaScriptEnabled = true
                wv.settings.domStorageEnabled = true
                wv.settings.userAgentString =
                    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

                wv.webViewClient = object : WebViewClient() {
                    private fun extractTokenIfPresent(url: String?): Boolean {
                        if (url == null) return false
                        if (url.startsWith(redirectUri) || url.contains("access_token=")) {
                            val fragment = url.substringAfter('#', "").ifEmpty { url.substringAfter('?', "") }
                            val params = fragment.split('&').associate {
                                val parts = it.split('=')
                                if (parts.size == 2) parts[0] to parts[1] else "" to ""
                            }
                            val token = params["access_token"]
                            if (!token.isNullOrBlank()) {
                                saveAccessToken(context, token)
                                Log.i(TAG, "Silent re-auth succeeded! Fresh token saved.")
                                finishWithResult(Result.success(token))
                                return true
                            }
                        }
                        if (url.contains("error=access_denied") || url.contains("error=interaction_required")) {
                            Log.w(TAG, "Silent re-auth requires user interaction: $url")
                            finishWithResult(Result.failure(Exception("ユーザー操作が必要です")))
                            return true
                        }
                        return false
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        extractTokenIfPresent(url)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        if (!extractTokenIfPresent(url)) {
                            if (url?.contains("accounts.google.com/signin") == true ||
                                url?.contains("accounts.google.com/v3/signin") == true ||
                                url?.contains("ServiceLogin") == true
                            ) {
                                Log.w(TAG, "Google redirected to login page: user must sign in manually.")
                                finishWithResult(Result.failure(Exception("Googleログインセッションが切れています。再接続が必要です。")))
                            }
                        }
                    }

                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        return extractTokenIfPresent(request?.url?.toString())
                    }
                }

                wv.loadUrl(authUrl)

                continuation.invokeOnCancellation {
                    try {
                        wv.stopLoading()
                        wv.destroy()
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start silent re-auth WebView", e)
                finishWithResult(Result.failure(e))
            }
        }
    }

    /**
     * 同期やダウンロードに必要な有効なアクセストークンを保証する。
     * - まだ50分以内であれば既存の保存済みトークンを即時返却（レイテンシゼロ）
     * - 期限切れまたは明示的な更新指示（401発生時等）時はバックグラウンドで自動サイレント再取得
     */
    suspend fun ensureValidAccessToken(context: Context, forceRefresh: Boolean = false): Result<String> {
        val existing = getSavedAccessToken(context)
        if (!forceRefresh && !existing.isNullOrBlank() && isTokenFresh(context)) {
            return Result.success(existing)
        }

        return try {
            val res = withTimeoutOrNull(9000L) {
                silentRefreshAccessToken(context)
            }
            if (res != null && res.isSuccess) {
                res
            } else if (!existing.isNullOrBlank() && !forceRefresh) {
                // サイレント更新が一時的に不通でも既存トークンをフォールバックとして試行
                Result.success(existing)
            } else {
                res ?: Result.failure(Exception("Google認証の自動更新がタイムアウトしました。"))
            }
        } catch (e: Exception) {
            if (!existing.isNullOrBlank() && !forceRefresh) {
                Result.success(existing)
            } else {
                Result.failure(e)
            }
        }
    }

    fun getSavedDriveRoot(context: Context): DriveRoot {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = prefs.getString(KEY_DRIVE_ROOT, DriveRoot.MY_DRIVE.key)
        return DriveRoot.entries.find { it.key == key } ?: DriveRoot.MY_DRIVE
    }

    fun saveDriveRoot(context: Context, root: DriveRoot) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DRIVE_ROOT, root.key).apply()
    }

    /**
     * Google Drive API v3:
     * - 非フォルダファイルを全取得し、mp3, m4a, aac, flac, ogg のみ抽出し、wavは確実に除外
     * - フォルダ階層を復元し、音楽ファイルを含むフォルダのみを階層付きでグルーピング
     */
    suspend fun fetchAudioFolders(
        context: Context,
        accessToken: String,
        root: DriveRoot = DriveRoot.MY_DRIVE
    ): Result<List<DriveFolderGroup>> = withContext(Dispatchers.IO) {
        try {
            // 1. Fetch all folders map (id -> name & parent)
            val folderMap = fetchAllFolders(accessToken, root)

            val folderTracksMap = mutableMapOf<String, MutableList<Track>>()
            val folderSizeMap = mutableMapOf<String, Long>()
            var totalAudioFilesFound = 0

            // 2. Fetch files with pagination
            var pageToken: String? = null
            var pageCount = 0

            do {
                pageCount++
                // Simple, 100% reliable query supported by Google Drive API v3
                val query = "trashed = false and mimeType != 'application/vnd.google-apps.folder'"
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val fields = URLEncoder.encode("nextPageToken, files(id, name, mimeType, size, modifiedTime, parents)", "UTF-8")

                var urlString = "https://www.googleapis.com/drive/v3/files?q=$encodedQuery&fields=$fields&pageSize=500&supportsAllDrives=true&includeItemsFromAllDrives=true"
                if (root == DriveRoot.SHARED_DRIVES) {
                    urlString += "&corpora=allDrives"
                }
                if (!pageToken.isNullOrBlank()) {
                    urlString += "&pageToken=" + URLEncoder.encode(pageToken, "UTF-8")
                }

                val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Authorization", "Bearer $accessToken")
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = 15000
                    readTimeout = 25000
                }

                val responseCode = conn.responseCode
                if (responseCode !in 200..299) {
                    val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                    Log.e(TAG, "Drive files API error: $errorBody")
                    return@withContext Result.failure(Exception("Google Drive APIエラー ($responseCode): $errorBody"))
                }

                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                val filesArray = json.optJSONArray("files")

                if (filesArray != null) {
                    for (i in 0 until filesArray.length()) {
                        val fileObj = filesArray.getJSONObject(i)
                        val fileName = fileObj.getString("name")
                        val lowerName = fileName.lowercase()
                        val mimeType = fileObj.optString("mimeType", "").lowercase()

                        // Strictly EXCLUDE WAV
                        if (lowerName.endsWith(".wav") || mimeType.contains("wav")) {
                            continue
                        }

                        // Check supported audio format: 音楽拡張子 (.mp3, .m4a, .aac, .flac, .ogg) を持つ音楽ファイルのみ取得
                        val hasAudioExtension = SUPPORTED_EXTENSIONS.any { lowerName.endsWith(it) }
                        if (!hasAudioExtension) {
                            continue
                        }

                        totalAudioFilesFound++
                        val fileId = fileObj.getString("id")
                        val size = fileObj.optLong("size", 0L)
                        val modifiedTimeStr = fileObj.optString("modifiedTime", "")
                        val modifiedTime = parseIsoTimestamp(modifiedTimeStr)

                        // Determine parent folder id and path
                        val parents = fileObj.optJSONArray("parents")
                        val parentId = if (parents != null && parents.length() > 0) parents.getString(0) else "root"

                        val folderPath = resolveFolderPath(parentId, folderMap, root)
                        val downloadUrl = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"

                        val track = Id3Helper.createTrackFromPath(
                            id = "drive_$fileId",
                            folderPath = folderPath,
                            fileName = fileName,
                            streamUrl = downloadUrl,
                            driveModifiedTime = modifiedTime
                        )

                        folderTracksMap.getOrPut(parentId) { mutableListOf() }.add(track)
                        folderSizeMap[parentId] = (folderSizeMap[parentId] ?: 0L) + size
                    }
                }

                pageToken = if (json.has("nextPageToken")) json.getString("nextPageToken") else null
            } while (!pageToken.isNullOrBlank() && pageCount < 20)

            Log.d(TAG, "Sync complete: found $totalAudioFilesFound audio files across ${folderTracksMap.size} folders")

            // Construct folder groups (音楽ファイルが存在するフォルダのみ)
            val resultGroups = folderTracksMap.map { (folderId, tracks) ->
                val fullPath = resolveFolderPath(folderId, folderMap, root)
                val folderName = folderMap[folderId]?.name ?: fullPath.substringAfterLast('/')
                DriveFolderGroup(
                    folderId = folderId,
                    folderPath = fullPath,
                    folderName = folderName,
                    tracks = tracks.sortedBy { it.trackNumber },
                    totalSizeBytes = folderSizeMap[folderId] ?: 0L,
                    rootType = root
                )
            }.sortedBy { it.folderPath }

            // Fetch and save startPageToken for subsequent differential syncs
            val tokenRes = fetchStartPageToken(accessToken, root)
            tokenRes.onSuccess { saveStartPageToken(context, it) }

            Result.success(resultGroups)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching drive folders: ${e.message}", e)
            Result.failure(e)
        }
    }

    data class DriveChangeItem(
        val fileId: String,
        val isRemoved: Boolean,
        val fileObj: JSONObject? = null
    )

    data class DifferentialChangesResult(
        val changes: List<DriveChangeItem>,
        val newStartPageToken: String
    )

    class TokenExpiredException(msg: String) : Exception(msg)

    /**
     * Google Drive API v3: changes/startPageToken を取得
     */
    suspend fun fetchStartPageToken(
        accessToken: String,
        root: DriveRoot = DriveRoot.MY_DRIVE
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            var urlString = "https://www.googleapis.com/drive/v3/changes/startPageToken?supportsAllDrives=true"
            if (root == DriveRoot.SHARED_DRIVES) {
                urlString += "&corpora=allDrives"
            }
            val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 15000
                readTimeout = 20000
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                return@withContext Result.failure(Exception("startPageToken error ($code): $err"))
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(text)
            val token = json.getString("startPageToken")
            Result.success(token)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Google Drive API v3: changes.list を利用した差分変更の取得
     */
    suspend fun fetchDifferentialChanges(
        context: Context,
        accessToken: String,
        startPageToken: String,
        root: DriveRoot = DriveRoot.MY_DRIVE
    ): Result<DifferentialChangesResult> = withContext(Dispatchers.IO) {
        try {
            val allChanges = mutableListOf<DriveChangeItem>()
            var currentPageToken: String? = startPageToken
            var newStartPageToken: String = startPageToken
            var pageCount = 0

            do {
                pageCount++
                val fields = URLEncoder.encode("nextPageToken, newStartPageToken, changes(fileId, removed, file(id, name, mimeType, size, modifiedTime, parents, trashed))", "UTF-8")
                var urlString = "https://www.googleapis.com/drive/v3/changes?pageToken=${URLEncoder.encode(currentPageToken, "UTF-8")}&fields=$fields&pageSize=1000&supportsAllDrives=true&includeItemsFromAllDrives=true"
                if (root == DriveRoot.SHARED_DRIVES) {
                    urlString += "&includeCorpusRemovals=true"
                }

                val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Authorization", "Bearer $accessToken")
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = 15000
                    readTimeout = 25000
                }

                val code = conn.responseCode
                if (code == 404 || code == 410) {
                    return@withContext Result.failure(TokenExpiredException("StartPageToken expired ($code)"))
                }
                if (code !in 200..299) {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                    return@withContext Result.failure(Exception("changes API error ($code): $err"))
                }

                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(text)

                val changesArray = json.optJSONArray("changes")
                if (changesArray != null) {
                    for (i in 0 until changesArray.length()) {
                        val changeObj = changesArray.getJSONObject(i)
                        val fileId = changeObj.getString("fileId")
                        val removed = changeObj.optBoolean("removed", false)
                        val fileObj = changeObj.optJSONObject("file")
                        allChanges.add(DriveChangeItem(fileId, removed, fileObj))
                    }
                }

                val nextToken = if (json.has("nextPageToken")) json.getString("nextPageToken") else null
                val startToken = if (json.has("newStartPageToken")) json.getString("newStartPageToken") else null
                if (startToken != null) {
                    newStartPageToken = startToken
                }
                currentPageToken = nextToken
            } while (!currentPageToken.isNullOrBlank() && pageCount < 10)

            Result.success(DifferentialChangesResult(allChanges, newStartPageToken))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun fetchAllFolders(accessToken: String, root: DriveRoot): Map<String, FolderNode> {
        val map = mutableMapOf<String, FolderNode>()
        try {
            var pageToken: String? = null
            var pageCount = 0

            do {
                pageCount++
                val query = "mimeType = 'application/vnd.google-apps.folder' and trashed = false"
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val fields = URLEncoder.encode("nextPageToken, files(id, name, parents)", "UTF-8")
                var urlString = "https://www.googleapis.com/drive/v3/files?q=$encodedQuery&fields=$fields&pageSize=500&supportsAllDrives=true&includeItemsFromAllDrives=true"
                if (root == DriveRoot.SHARED_DRIVES) {
                    urlString += "&corpora=allDrives"
                }
                if (!pageToken.isNullOrBlank()) {
                    urlString += "&pageToken=" + URLEncoder.encode(pageToken, "UTF-8")
                }

                val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Authorization", "Bearer $accessToken")
                    connectTimeout = 12000
                    readTimeout = 15000
                }

                if (conn.responseCode in 200..299) {
                    val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    val array = json.optJSONArray("files")
                    if (array != null) {
                        for (i in 0 until array.length()) {
                            val item = array.getJSONObject(i)
                            val id = item.getString("id")
                            val name = item.getString("name")
                            val parents = item.optJSONArray("parents")
                            val parentId = if (parents != null && parents.length() > 0) parents.getString(0) else null
                            map[id] = FolderNode(id, name, parentId)
                        }
                    }
                    pageToken = if (json.has("nextPageToken")) json.getString("nextPageToken") else null
                } else {
                    break
                }
            } while (!pageToken.isNullOrBlank() && pageCount < 10)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch folders: ${e.message}")
        }
        return map
    }

    fun resolveFolderPath(folderId: String, folderMap: Map<String, FolderNode>, root: DriveRoot): String {
        val segments = mutableListOf<String>()
        var currentId: String? = folderId

        while (currentId != null && currentId != "root" && folderMap.containsKey(currentId)) {
            val node = folderMap[currentId] ?: break
            segments.add(0, node.name)
            currentId = node.parentId
        }

        val rootPrefix = root.displayNameJa
        return if (segments.isEmpty()) {
            rootPrefix
        } else {
            "$rootPrefix/${segments.joinToString("/")}"
        }
    }

    /**
     * Google Driveから音楽ファイルをローカルキャッシュに明示的にダウンロード
     */
    suspend fun downloadTrackToCache(
        context: Context,
        track: Track,
        accessToken: String
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val fileId = track.id.removePrefix("drive_")
            val cacheDir = File(context.cacheDir, "audio_cache").apply { mkdirs() }
            val sanitizedName = track.fileName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            val targetFile = File(cacheDir, "${track.id}_$sanitizedName")

            var currentUrl = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media&supportsAllDrives=true"
            var redirectCount = 0
            var finalConn: HttpURLConnection? = null

            while (redirectCount < 6) {
                val url = URL(currentUrl)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    requestMethod = "GET"
                    if (currentUrl.contains("googleapis.com")) {
                        setRequestProperty("Authorization", "Bearer $accessToken")
                    }
                    setRequestProperty("User-Agent", "LargoClassicalPlayer/1.0 (Android)")
                    connectTimeout = 15000
                    readTimeout = 40000
                }

                val code = conn.responseCode
                if (code == HttpURLConnection.HTTP_MOVED_TEMP ||
                    code == HttpURLConnection.HTTP_MOVED_PERM ||
                    code == HttpURLConnection.HTTP_SEE_OTHER ||
                    code == 307 || code == 308) {
                    val location = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (!location.isNullOrBlank()) {
                        currentUrl = location
                        redirectCount++
                        continue
                    }
                }

                if (code in 200..299) {
                    finalConn = conn
                    break
                } else {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                    conn.disconnect()
                    return@withContext Result.failure(Exception("ダウンロード失敗: HTTP $code ($err)"))
                }
            }

            if (finalConn == null) {
                return@withContext Result.failure(Exception("リダイレクト超過またはダウンロード接続失敗"))
            }

            finalConn.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            finalConn.disconnect()

            if (targetFile.length() == 0L) {
                targetFile.delete()
                return@withContext Result.failure(Exception("ダウンロードされたファイルサイズが0バイトです"))
            }

            Result.success(targetFile)
        } catch (e: Exception) {
            Log.e(TAG, "downloadTrackToCache failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun parseIsoTimestamp(isoString: String): Long {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                java.time.Instant.parse(isoString).toEpochMilli()
            } else {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                sdf.parse(isoString)?.time ?: System.currentTimeMillis()
            }
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    data class FolderNode(
        val id: String,
        val name: String,
        val parentId: String?
    )
}
