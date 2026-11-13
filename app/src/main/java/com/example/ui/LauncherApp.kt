package com.example.ui

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Process
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import java.util.Collections
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import kotlin.math.abs
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.compose.AsyncImagePainter
import com.example.data.AppInfo
import com.example.data.GroupAppEntity
import com.example.data.HomeItemEntity
import com.example.data.toImageBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import android.app.Activity
import android.net.Uri
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset

// Cosmic gradients for background
val SpaceGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF0F172A), // Slate 900
        Color(0xFF1E293B), // Slate 800
        Color(0xFF020617)  // Slate 950
    )
)

fun resolveLauncherActivity(
    context: Context,
    packageName: String,
    savedClassName: String?
): android.content.pm.LauncherActivityInfo? {
    val la = context.getSystemService(LauncherApps::class.java) ?: return null
    val list = try {
        la.getActivityList(packageName, Process.myUserHandle())
    } catch (e: Exception) {
        return null
    }
    if (list.isEmpty()) return null
    return if (!savedClassName.isNullOrBlank()) {
        list.firstOrNull { it.componentName.className == savedClassName } ?: list.first()
    } else {
        list.first()
    }
}

// #######################################
// netbkのみ。
object LaunchRetryGuard {
    private const val TAG = "LaunchRetry"
    private val targets = setOf("jp.co.netbk")
    private const val WINDOW_MS = 1000L
    private const val RETRY_DELAY_MS = 600L

    private var pkg: String? = null
    private var cls: String = ""
    private var launchedAt = 0L
    private var retried = false
    private var retrying = false

    fun onLaunch(packageName: String, className: String) {
        val target = packageName in targets
        android.util.Log.d(TAG, "onLaunch pkg=$packageName cls=$className target=$target retrying=$retrying")
        if (!target) { pkg = null; return }
        if (retrying) retrying = false else retried = false
        pkg = packageName
        cls = className
        launchedAt = android.os.SystemClock.elapsedRealtime()
    }

    fun onLauncherResumed(context: Context) {
        val p = pkg
        val elapsed = android.os.SystemClock.elapsedRealtime() - launchedAt
        android.util.Log.d(TAG, "resumed pending=$p elapsed=${elapsed}ms retried=$retried")
        if (p == null) return
        pkg = null
        if (retried) return
        if (elapsed > WINDOW_MS) {
            android.util.Log.d(TAG, "skip: window exceeded")
            return
        }
        retried = true
        retrying = true
        android.util.Log.d(TAG, "retry scheduled in ${RETRY_DELAY_MS}ms")
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            android.util.Log.d(TAG, "retry launch now")
            launchApp(context, p, cls)
        }, RETRY_DELAY_MS)
    }
}
// #######################################

// Helper function to safely launch apps
fun launchApp(context: Context, packageName: String, className: String) {
    if (packageName.isBlank()) return

    LaunchRetryGuard.onLaunch(packageName, className)   // ← 2026-10-18追加

    // 1. Prefer LauncherApps.startMainActivity (official launcher API; handles active activity-aliases and bank task resets properly)
    try {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val info = resolveLauncherActivity(context, packageName, className)
        if (launcherApps != null && info != null) {
            launcherApps.startMainActivity(info.componentName, Process.myUserHandle(), null, null)
            return
        }
    } catch (e: Exception) {
        // Fallback to Intent
    }

    // 2. Intent fallback with FLAG_ACTIVITY_NEW_TASK and FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
    try {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            if (className.isNotBlank()) {
                setClassName(packageName, className)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        context.startActivity(intent)
        return
    } catch (e: Exception) {
        // Fallback to getLaunchIntentForPackage
    }

    try {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        if (launchIntent != null) {
            context.startActivity(launchIntent)
        } else {
            Toast.makeText(context, "アプリを起動できませんでした", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "アプリを起動できませんでした", Toast.LENGTH_SHORT).show()
    }
}

// Helper function to launch web and app shortcuts
fun launchWebShortcut(context: Context, url: String) {
    try {
        var formattedUrl = url.trim()
        if (formattedUrl.isEmpty()) return

        // 1. Launch via LauncherApps.startShortcut if this is a pinned shortcut (e.g., individual LINE chat shortcut)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val shortcutIdMatch = Regex("(?:S\\.shortcut_id|shortcut_id)=([^;]+)").find(formattedUrl)?.groupValues?.get(1)
            if (!shortcutIdMatch.isNullOrEmpty()) {
                val shortcutId = Uri.decode(shortcutIdMatch)
                val pkg = Regex("(?:package|component)=([^;/]+)").find(formattedUrl)?.groupValues?.get(1)
                    ?: if (formattedUrl.contains("jp.naver.line.android")) "jp.naver.line.android" else ""
                if (pkg.isNotEmpty()) {
                    try {
                        val launcherApps = context.getSystemService(LauncherApps::class.java)
                        if (launcherApps != null) {
                            launcherApps.startShortcut(pkg, shortcutId, null, null, Process.myUserHandle())
                            return
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        // 2. Special handling for LINE links (Talk/Chat tab and direct actions)
        val isLineLink = formattedUrl.startsWith("line:", ignoreCase = true) ||
                formattedUrl.contains("line.me", ignoreCase = true) ||
                formattedUrl.contains("jp.naver.line.android", ignoreCase = true)

        if (isLineLink) {
            val isChatListRequest = formattedUrl.contains("nv/chat", ignoreCase = true) ||
                    formattedUrl.equals("https://line.me", ignoreCase = true) ||
                    formattedUrl.equals("https://line.me/", ignoreCase = true) ||
                    formattedUrl.equals("line.me", ignoreCase = true) ||
                    formattedUrl.equals("line://", ignoreCase = true) ||
                    formattedUrl.equals("line:", ignoreCase = true)

            if (isChatListRequest) {
                // Try sequence of intents for opening LINE Chat tab
                val candidateUris = listOf(
                    "https://line.me/R/nv/chat",
                    "line://nv/chat",
                    "https://line.me/R/nv/chat/",
                    "line://nv/chat/"
                )

                // Try 1: Explicit package with clear-top flags for Universal Link
                for (uriStr in candidateUris) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriStr)).apply {
                            setPackage("jp.naver.line.android")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        context.startActivity(intent)
                        return
                    } catch (e: Exception) {
                        // ignore and try next
                    }
                }

                // Try 2: Implicit ACTION_VIEW (let OS App Links route to LINE Chat)
                for (uriStr in candidateUris) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriStr)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        context.startActivity(intent)
                        return
                    } catch (e: Exception) {
                        // ignore and try next
                    }
                }

                // Fallback: Launch LINE app main
                try {
                    val lineIntent = context.packageManager.getLaunchIntentForPackage("jp.naver.line.android")
                    if (lineIntent != null) {
                        lineIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(lineIntent)
                        return
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                // Non-chat LINE URLs (e.g. ti/p, oaMessage, share, etc.)
                val normalizedLineUrl = when {
                    formattedUrl.contains("line.me/R/", ignoreCase = true) -> formattedUrl
                    formattedUrl.startsWith("line://", ignoreCase = true) -> "https://line.me/R/" + formattedUrl.removePrefix("line://")
                    formattedUrl.startsWith("line:", ignoreCase = true) -> "https://line.me/R/" + formattedUrl.removePrefix("line:")
                    else -> formattedUrl
                }

                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalizedLineUrl)).apply {
                        setPackage("jp.naver.line.android")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    context.startActivity(intent)
                    return
                } catch (e1: Exception) {
                    try {
                        val implicitIntent = Intent(Intent.ACTION_VIEW, Uri.parse(normalizedLineUrl)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        context.startActivity(implicitIntent)
                        return
                    } catch (e2: Exception) {
                        e2.printStackTrace()
                    }
                }
            }
        }

        // 2. Normalize intent URIs (e.g. intent/#Intent; or intent//#Intent; or intent/...)
        if (formattedUrl.startsWith("intent/", ignoreCase = true)) {
            formattedUrl = "intent:" + formattedUrl.substringAfter("intent/").trimStart('/')
        }
        if (formattedUrl.startsWith("#Intent;", ignoreCase = true)) {
            formattedUrl = "intent:$formattedUrl"
        }

        // 3. Intent URIs (intent:#Intent;...;end or intent://)
        if (formattedUrl.startsWith("intent:", ignoreCase = true) || formattedUrl.contains("#Intent;", ignoreCase = true)) {
            try {
                val intent = Intent.parseUri(formattedUrl, Intent.URI_INTENT_SCHEME)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent.action == null) {
                    intent.action = Intent.ACTION_VIEW
                }
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                e.printStackTrace()

                // Fallback 1: Extract browser_fallback_url or S.browser_fallback_url from intent URI
                val fallbackUrlMatch = Regex("(?:S\\.browser_fallback_url|browser_fallback_url)=([^;]+)").find(formattedUrl)?.groupValues?.get(1)
                if (!fallbackUrlMatch.isNullOrEmpty()) {
                    try {
                        val decodedUrl = Uri.decode(fallbackUrlMatch)
                        val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(decodedUrl)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(fallbackIntent)
                        return
                    } catch (eFallback: Exception) {
                        eFallback.printStackTrace()
                    }
                }

                // Fallback 2: Launch via package manager if package is specified in intent URI
                val pkgName = Regex("package=([^;]+);").find(formattedUrl)?.groupValues?.get(1)
                if (!pkgName.isNullOrEmpty()) {
                    try {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(pkgName)
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(launchIntent)
                            return
                        }
                    } catch (e2: Exception) {
                        e2.printStackTrace()
                    }
                }

                // Fallback 3: Try extracting scheme and data path
                val schemeMatch = Regex("scheme=([^;]+);").find(formattedUrl)?.groupValues?.get(1)
                if (!schemeMatch.isNullOrEmpty()) {
                    val rawData = formattedUrl.substringBefore("#Intent;").removePrefix("intent://").removePrefix("intent:")
                    val reconstructedUrl = "$schemeMatch://$rawData"
                    try {
                        val schemeIntent = Intent(Intent.ACTION_VIEW, Uri.parse(reconstructedUrl)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(schemeIntent)
                        return
                    } catch (e3: Exception) {
                        e3.printStackTrace()
                    }
                }

                Toast.makeText(context, "ショートカットの起動に失敗しました", Toast.LENGTH_SHORT).show()
                return
            }
        }

        // 3. Custom Scheme URIs (line://, etc.) & HTTP/HTTPS URLs
        var uriToParse = formattedUrl
        if (!uriToParse.contains("://") && !uriToParse.startsWith("about:") && !uriToParse.startsWith("line:")) {
            uriToParse = "https://$uriToParse"
        }

        val primaryIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uriToParse)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(primaryIntent)
        } catch (e: Exception) {
            e.printStackTrace()
            // If opening custom scheme like line: or line.me link failed directly, attempt LINE app launch fallback
            if (uriToParse.startsWith("line:", ignoreCase = true) || uriToParse.contains("line.me", ignoreCase = true)) {
                try {
                    val lineIntent = context.packageManager.getLaunchIntentForPackage("jp.naver.line.android")
                    if (lineIntent != null) {
                        lineIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(lineIntent)
                        return
                    }
                } catch (eLine: Exception) {
                    eLine.printStackTrace()
                }
            }
            Toast.makeText(context, "アプリまたはブラウザが開けませんでした", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "ショートカットを開けませんでした", Toast.LENGTH_SHORT).show()
    }
}

// Helper function to reliably launch package uninstallation or app settings
fun uninstallApp(context: Context, packageName: String) {
    if (packageName.isBlank()) return
    try {
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e1: Exception) {
        try {
            val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.fromParts("package", packageName, null)).apply {
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e2: Exception) {
            try {
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Toast.makeText(context, "アプリ詳細画面を開きました（ここからアンインストールできます）", Toast.LENGTH_LONG).show()
            } catch (e3: Exception) {
                Toast.makeText(context, "アンインストール画面を開けませんでした", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherApp(viewModel: LauncherViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Reload apps on resume to ensure installed/uninstalled apps list is immediately synced
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.reloadApps()
                val flatNotif = android.provider.Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                val isNotifListenerEnabled = flatNotif != null && flatNotif.contains(context.packageName)
                if (isNotifListenerEnabled && !com.example.service.LauncherNotificationListenerService.isServiceConnected) {
                    com.example.service.LauncherNotificationListenerService.requestRebindService(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Observe state from ViewModel
    val homeItems by viewModel.homeItems.collectAsStateWithLifecycle()
    val groupApps by viewModel.groupApps.collectAsStateWithLifecycle()
    val isDrawerOpen by viewModel.isDrawerOpen.collectAsStateWithLifecycle()
    val activeFolder by viewModel.activeFolder.collectAsStateWithLifecycle()
    val isSelectionMode by viewModel.isSelectionMode.collectAsStateWithLifecycle()
    val selectedItems by viewModel.selectedItems.collectAsStateWithLifecycle()
    val isDrawerSelectionMode by viewModel.isDrawerSelectionMode.collectAsStateWithLifecycle()
    val homePageCount by viewModel.homePageCount.collectAsStateWithLifecycle()
    val activePageIndices by viewModel.activePageIndices.collectAsStateWithLifecycle()
    val enabledPages by viewModel.enabledPages.collectAsStateWithLifecycle()
    val defaultPinnedPageIndex by viewModel.defaultPinnedPageIndex.collectAsStateWithLifecycle()
    val pageOrder by viewModel.pageOrder.collectAsStateWithLifecycle()
    val pageLabels by viewModel.pageLabels.collectAsStateWithLifecycle()
    val notificationCounts by viewModel.notificationCounts.collectAsStateWithLifecycle()
    val lockedPages by viewModel.lockedPages.collectAsStateWithLifecycle()
    val unlockedPages by viewModel.unlockedPages.collectAsStateWithLifecycle()
    val isSettingsLocked by viewModel.isSettingsLocked.collectAsStateWithLifecycle()
    val isSettingsUnlocked by viewModel.isSettingsUnlocked.collectAsStateWithLifecycle()
    val pageLockPin by viewModel.pageLockPin.collectAsStateWithLifecycle()
    val isDrawerEnabled by viewModel.isDrawerEnabled.collectAsStateWithLifecycle()
    val isDrawerLocked by viewModel.isDrawerLocked.collectAsStateWithLifecycle()
    val isDrawerUnlocked by viewModel.isDrawerUnlocked.collectAsStateWithLifecycle()
    val resetTrigger by viewModel.resetTrigger.collectAsStateWithLifecycle()
    val dismissDialogsTrigger by viewModel.dismissDialogsTrigger.collectAsStateWithLifecycle()

    var authenticatingLockedPageIndex by remember { mutableStateOf<Int?>(null) }
    var showSettingsAuthDialog by remember { mutableStateOf(false) }
    var showDrawerAuthDialog by remember { mutableStateOf(false) }

    val requestOpenDrawer: () -> Unit = {
        if (isDrawerEnabled) {
            if (isDrawerLocked && !isDrawerUnlocked) {
                showDrawerAuthDialog = true
            } else {
                viewModel.setDrawerOpen(true)
            }
        }
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val gridColumnCount = remember(screenWidthDp) {
        when {
            screenWidthDp >= 840 -> 8
            screenWidthDp >= 600 -> 6
            screenWidthDp >= 360 -> 5
            else -> 4
        }
    }

    val initialPagerIdx = remember {
        val idx = activePageIndices.indexOf(defaultPinnedPageIndex)
        if (idx != -1) idx else 0
    }
    val pagerState = rememberPagerState(
        initialPage = initialPagerIdx.coerceIn(0, (homePageCount - 1).coerceAtLeast(0)),
        pageCount = { homePageCount }
    )

    LaunchedEffect(homePageCount) {
        if (homePageCount > 0 && pagerState.currentPage >= homePageCount) {
            try {
                pagerState.scrollToPage((homePageCount - 1).coerceAtLeast(0))
            } catch (e: Exception) {
                // ignore safe fallback
            }
        }
    }

    val currentActualPageIndex = activePageIndices.getOrElse(pagerState.currentPage) { 0 }

    // Sync ViewModel page with pager settled state only when scrolling is fully complete
    LaunchedEffect(pagerState.settledPage) {
        viewModel.setCurrentPageIndex(pagerState.settledPage)
    }

    // Modal dialogs
    var showFolderNameDialog by remember { mutableStateOf(false) }
    var showMovePageDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showAddWebShortcutDialog by remember { mutableStateOf(false) }
    var editingWebShortcut by remember { mutableStateOf<HomeItemEntity?>(null) }
    var editingItemLabel by remember { mutableStateOf<HomeItemEntity?>(null) }

    LaunchedEffect(dismissDialogsTrigger) {
        if (dismissDialogsTrigger > 0) {
            showFolderNameDialog = false
            showMovePageDialog = false
            showSettingsDialog = false
            showSettingsAuthDialog = false
            showAddWebShortcutDialog = false
            editingWebShortcut = null
            editingItemLabel = null
            authenticatingLockedPageIndex = null
        }
    }

    LaunchedEffect(resetTrigger) {
        if (resetTrigger > 0) {
            showFolderNameDialog = false
            showMovePageDialog = false
            showSettingsDialog = false
            showSettingsAuthDialog = false
            showAddWebShortcutDialog = false
            editingWebShortcut = null
            editingItemLabel = null
            authenticatingLockedPageIndex = null
            val defaultIdx = defaultPinnedPageIndex
            val targetPagerIdx = activePageIndices.indexOf(defaultIdx)
            val safeTarget = if (targetPagerIdx != -1) targetPagerIdx else 0
            if (homePageCount > 0 && safeTarget in 0 until homePageCount) {
                try {
                    pagerState.scrollToPage(safeTarget)
                } catch (e: Exception) {
                    // ignore safe fallback
                }
            }
        }
    }

    val homeNestedScrollConnection = remember(isSelectionMode, activeFolder, isDrawerOpen) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (isDrawerEnabled && !isSelectionMode && activeFolder == null && !isDrawerOpen && available.y < -10f) {
                    requestOpenDrawer()
                }
                return Offset.Zero
            }
        }
    }

    val view = LocalView.current
    val pageBackgroundColors by viewModel.pageBackgroundColors.collectAsStateWithLifecycle()
    val currentActualPageColorHex = pageBackgroundColors[currentActualPageIndex]
    val currentActualPageColor = remember(currentActualPageColorHex) {
        if (!currentActualPageColorHex.isNullOrBlank() && currentActualPageColorHex != "default") {
            try { Color(android.graphics.Color.parseColor(currentActualPageColorHex)) } catch (e: Exception) { Color(0xFFD8E698) }
        } else {
            Color(0xFFD8E698)
        }
    }
    val isCurrentPageLight = remember(currentActualPageColor) {
        currentActualPageColor.luminance() > 0.55f
    }
    val currentHeaderContentColor = if (isCurrentPageLight) Color(0xFF1E1B4B) else Color.White

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = isCurrentPageLight
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = isCurrentPageLight
            }
        }
    }

    BackHandler(enabled = isDrawerOpen) {
        if (isDrawerSelectionMode) {
            viewModel.exitDrawerSelectionMode()
        } else {
            viewModel.setDrawerOpen(false)
        }
    }

    BackHandler(enabled = !isDrawerOpen && (isSelectionMode || activeFolder != null)) {
        if (activeFolder != null) {
            viewModel.closeFolder()
        } else if (isSelectionMode) {
            viewModel.exitSelectionMode()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(currentActualPageColor)
            .nestedScroll(homeNestedScrollConnection)
    ) {
        // Main Home Screen Layer
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .graphicsLayer {
                    if (activeFolder != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        renderEffect = BlurEffect(16f, 16f)
                    }
                }
        ) {
            // Elegant Top Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // アプリ一覧用トリガー (edlauncherテキストは削除)
                if (isDrawerEnabled) {
                    Surface(
                        onClick = { requestOpenDrawer() },
                        shape = CircleShape,
                        color = if (isCurrentPageLight) Color.White.copy(alpha = 0.65f) else Color.Black.copy(alpha = 0.35f),
                        shadowElevation = 2.dp,
                        modifier = Modifier.testTag("app_drawer_trigger")
                    ) {
                        Box(
                            modifier = Modifier.padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Apps,
                                contentDescription = "アプリ一覧",
                                tint = currentHeaderContentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.size(36.dp))
                }

                // 設定で指定された10文字程度のラベルを薄く表示
                val currentLabel = pageLabels[currentActualPageIndex] ?: ""
                Text(
                    text = currentLabel,
                    color = currentHeaderContentColor.copy(alpha = if (isCurrentPageLight) 0.5f else 0.7f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                // 右側: ピン(デフォルトページ) & 設定
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val isPinned = defaultPinnedPageIndex == currentActualPageIndex
                    IconButton(
                        onClick = {
                            if (isPinned) {
                                val firstActive = activePageIndices.firstOrNull() ?: 0
                                viewModel.setDefaultPinnedPage(firstActive)
                                val home1Label = pageLabels[firstActive] ?: ""
                                val msg = if (home1Label.isNotBlank()) {
                                    "「$home1Label」(ページ ${firstActive + 1}) をデフォルトページに設定しました"
                                } else {
                                    "ページ ${firstActive + 1} をデフォルトページに設定しました"
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.setDefaultPinnedPage(currentActualPageIndex)
                                val msg = if (currentLabel.isNotBlank()) {
                                    "「$currentLabel」をデフォルトページに設定しました"
                                } else {
                                    "ページ ${currentActualPageIndex + 1} をデフォルトページに設定しました"
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = if (isPinned) "デフォルトページ (ピン留め中)" else "デフォルトページに設定",
                            tint = if (isPinned) Color(0xFF2563EB) else currentHeaderContentColor.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            if (isSettingsLocked && !isSettingsUnlocked) {
                                showSettingsAuthDialog = true
                            } else {
                                showSettingsDialog = true
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = "Settings and Backup",
                                tint = if (isSettingsLocked && !isSettingsUnlocked) currentHeaderContentColor.copy(alpha = 0.5f) else currentHeaderContentColor,
                                modifier = Modifier.size(20.dp)
                            )
                            if (isSettingsLocked && !isSettingsUnlocked) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = "ロック中",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .size(10.dp)
                                        .align(Alignment.BottomEnd)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ViewPager for Home Pages
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                userScrollEnabled = !isSelectionMode // Lock swipe when in selection mode
            ) { pageIdx ->
                val targetPageIndex = activePageIndices.getOrElse(pageIdx) { pageIdx }
                val pageBackgroundColors by viewModel.pageBackgroundColors.collectAsStateWithLifecycle()
                val customColorHex = pageBackgroundColors[targetPageIndex]
                val pageColor = remember(customColorHex) {
                    if (!customColorHex.isNullOrBlank() && customColorHex != "default") {
                        try { Color(android.graphics.Color.parseColor(customColorHex)) } catch (e: Exception) { Color(0xFFD8E698) }
                    } else {
                        Color(0xFFD8E698)
                    }
                }
                val isPageLight = remember(pageColor) {
                    pageColor.luminance() > 0.55f
                }
                val pageTextColor = if (isPageLight) Color(0xFF1E1B4B) else Color.White

                val pageBgModifier = if (!customColorHex.isNullOrBlank() && customColorHex != "default") {
                    val parsed = try { Color(android.graphics.Color.parseColor(customColorHex)) } catch (e: Exception) { null }
                    if (parsed != null) Modifier.background(parsed) else Modifier
                } else {
                    Modifier
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(pageBgModifier)
                ) {
                val itemsOnPage = remember(homeItems, targetPageIndex) {
                    homeItems.filter { it.pageIndex == targetPageIndex }
                }

                if (itemsOnPage.isEmpty()) {
                    // Empty state visual
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Rounded.Home,
                                contentDescription = null,
                                tint = pageTextColor.copy(alpha = 0.35f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "アプリまたはウェブショートカットを追加してください",
                                color = pageTextColor.copy(alpha = 0.6f),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (isDrawerEnabled) {
                                    Button(
                                        onClick = { requestOpenDrawer() },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isPageLight) Color.White.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.18f),
                                            contentColor = pageTextColor
                                        )
                                    ) {
                                        Icon(Icons.Rounded.Apps, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("アプリ一覧", fontSize = 12.sp)
                                    }
                                }
                                Button(
                                    onClick = { showAddWebShortcutDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Rounded.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Web追加", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                } else {
                    // App and Folder Grid
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(gridColumnCount),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        contentPadding = PaddingValues(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        userScrollEnabled = false
                    ) {
                        items(itemsOnPage, key = { it.id }) { item ->
                            val isSelected = selectedItems.contains(item)
                            val appsInGroup = remember(groupApps, item.id) {
                                if (item.itemType == "GROUP") {
                                    groupApps.filter { it.homeItemId == item.id }
                                } else {
                                    emptyList()
                                }
                            }

                            HomeGridItem(
                                item = item,
                                appsInGroup = appsInGroup,
                                isSelectionMode = isSelectionMode,
                                isSelected = isSelected,
                                notificationCounts = notificationCounts,
                                onClick = {
                                    if (isSelectionMode) {
                                        viewModel.toggleItemSelection(item)
                                    } else {
                                        when (item.itemType) {
                                            "APP" -> launchApp(context, item.packageName ?: "", item.className ?: "")
                                            "WEB_SHORTCUT" -> launchWebShortcut(context, item.url ?: "")
                                            else -> viewModel.openFolder(item)
                                        }
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        viewModel.enterSelectionMode(item)
                                    }
                                },
                                textColor = pageTextColor
                            )
                        }
                    }
                }
            }
        }

            // Page Indicator (Capsule shape, clickable, displays all enabled pages in pageOrder, locked pages show lock indicator and require PIN)
            val pageBackgroundColors by viewModel.pageBackgroundColors.collectAsStateWithLifecycle()
            val orderedEnabledPages = remember(enabledPages, pageOrder) {
                val inOrder = pageOrder.filter { enabledPages.contains(it) }
                val remaining = enabledPages.filter { !inOrder.contains(it) }.sorted()
                inOrder + remaining
            }
            Row(
                Modifier
                    .wrapContentHeight()
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                orderedEnabledPages.forEach { pIdx ->
                    val isLocked = lockedPages.contains(pIdx) && !unlockedPages.contains(pIdx)
                    val isSelected = currentActualPageIndex == pIdx
                    val customHex = pageBackgroundColors[pIdx]
                    val pageColor = remember(customHex) {
                        if (!customHex.isNullOrBlank() && customHex != "default") {
                            try { Color(android.graphics.Color.parseColor(customHex)) } catch (e: Exception) { Color(0xFFD8E698) }
                        } else {
                            Color(0xFFD8E698)
                        }
                    }

                    val capsuleColor = remember(isSelected, isLocked, pageColor) {
                        if (isLocked) {
                            Color(0xFF64748B).copy(alpha = 0.5f)
                        } else if (isSelected) {
                            // 25% lower brightness
                            Color(
                                red = pageColor.red * 0.75f,
                                green = pageColor.green * 0.75f,
                                blue = pageColor.blue * 0.75f,
                                alpha = 1f
                            )
                        } else {
                            Color(
                                red = pageColor.red * 0.75f,
                                green = pageColor.green * 0.75f,
                                blue = pageColor.blue * 0.75f,
                                alpha = 0.35f
                            )
                        }
                    }

                    val isDefaultPinned = pIdx == defaultPinnedPageIndex
                    val capsuleWidth = if (isSelected) 33.dp else if (isLocked) 22.dp else 18.dp

                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .height(12.dp)
                            .width(capsuleWidth)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(capsuleColor)
                            .clickable {
                                if (isLocked) {
                                    authenticatingLockedPageIndex = pIdx
                                } else {
                                    val targetPagerIdx = activePageIndices.indexOf(pIdx)
                                    if (targetPagerIdx != -1 && targetPagerIdx in 0 until homePageCount) {
                                        scope.launch {
                                            try {
                                                pagerState.animateScrollToPage(targetPagerIdx)
                                            } catch (e: Exception) {
                                                pagerState.scrollToPage(targetPagerIdx)
                                            }
                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLocked) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = "ロック中",
                                tint = Color.White,
                                modifier = Modifier.size(8.dp)
                            )
                        } else if (isDefaultPinned) {
                            Box(
                                modifier = Modifier
                                    .size(2.dp)
                                    .background(Color(0xFFFF0000), CircleShape)
                            )
                        }
                    }
                }
            }

            // Dock Component (5 fixed app slots)
            val dockItems = remember(homeItems) {
                homeItems.filter { it.pageIndex == -1 }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.45f)
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("launcher_dock")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 0 until 5) {
                            val item = dockItems.getOrNull(i)
                            Box(
                                modifier = Modifier
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                if (item != null) {
                                    val isSelected = selectedItems.contains(item)
                                    val appsInGroup = remember(groupApps, item.id) {
                                        if (item.itemType == "GROUP") {
                                            groupApps.filter { it.homeItemId == item.id }
                                        } else {
                                            emptyList()
                                        }
                                    }

                                    HomeGridItem(
                                        item = item,
                                        appsInGroup = appsInGroup,
                                        isSelectionMode = isSelectionMode,
                                        isSelected = isSelected,
                                        notificationCounts = notificationCounts,
                                        onClick = {
                                            if (isSelectionMode) {
                                                viewModel.toggleItemSelection(item)
                                            } else {
                                                when (item.itemType) {
                                                    "APP" -> launchApp(context, item.packageName ?: "", item.className ?: "")
                                                    "WEB_SHORTCUT" -> launchWebShortcut(context, item.url ?: "")
                                                    else -> viewModel.openFolder(item)
                                                }
                                            }
                                        },
                                        onLongClick = {
                                            if (!isSelectionMode) {
                                                viewModel.enterSelectionMode(item)
                                            }
                                        },
                                        textColor = Color(0xFF1E1B4B),
                                        verticalPadding = 4.dp
                                    )
                                } else {
                                    // Empty slot placeholder
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .background(
                                                Color(0xFFBBBBFF),
                                                shape = RoundedCornerShape(14.dp)
                                            )
                                            .padding(8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Add,
                                            contentDescription = "Empty Dock Slot",
                                            tint = Color(0xFF1E1B4B).copy(alpha = 0.5f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Selection / Edit Mode Floating Action Bar
        AnimatedVisibility(
            visible = isSelectionMode,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp, start = 16.dp, end = 16.dp)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .graphicsLayer {
                    clip = false
                }
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)
                ),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("selection_control_bar")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "選択中: ${selectedItems.size}件",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Edit / Rename Action if 1 item selected
                        if (selectedItems.size == 1) {
                            val singleItem = selectedItems.first()
                            IconButton(
                                onClick = {
                                    if (singleItem.itemType == "WEB_SHORTCUT") {
                                        editingWebShortcut = singleItem
                                    } else {
                                        editingItemLabel = singleItem
                                    }
                                },
                                colors = IconButtonDefaults.iconButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Icon(Icons.Rounded.Edit, contentDescription = "名前変更")
                            }
                        }

                        // Group Action
                        IconButton(
                            onClick = { showFolderNameDialog = true },
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Rounded.Folder, contentDescription = "Group")
                            }
                        }

                        // Move Page Action
                        IconButton(
                            onClick = { showMovePageDialog = true },
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Icon(Icons.Rounded.SwapHoriz, contentDescription = "Move Page")
                        }

                        // Delete Action
                        IconButton(
                            onClick = { viewModel.removeSelectedItems() },
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Remove")
                        }

                        // Cancel Action
                        IconButton(
                            onClick = { viewModel.exitSelectionMode() },
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                            )
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Cancel")
                        }
                    }
                }
            }
        }

        // Folder Expanded Detail Dialog
        if (activeFolder != null) {
            val folder = activeFolder!!
            val appsInFolder = groupApps.filter { it.homeItemId == folder.id }

            val rowCount = remember(appsInFolder.size, gridColumnCount) {
                ((appsInFolder.size + gridColumnCount - 1) / gridColumnCount).coerceAtLeast(1)
            }
            val configuration = LocalConfiguration.current
            val maxGridHeight: Dp = remember(configuration.screenHeightDp) {
                (configuration.screenHeightDp * 0.58f).dp
            }
            val estimatedGridHeight: Dp = remember(rowCount) {
                (rowCount * 98).dp + ((rowCount - 1) * 16).coerceAtLeast(0).dp
            }
            val finalGridHeight: Dp = remember(estimatedGridHeight, maxGridHeight) {
                if (estimatedGridHeight < maxGridHeight) estimatedGridHeight else maxGridHeight
            }

            Dialog(
                onDismissRequest = { viewModel.closeFolder() },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1E293B).copy(alpha = 0.95f) // Glassmorphic dark
                    ),
                    shape = RoundedCornerShape(28.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .wrapContentHeight()
                        .padding(8.dp)
                        .testTag("expanded_folder_dialog")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = folder.label,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(gridColumnCount),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(finalGridHeight),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(appsInFolder, key = { it.id }) { app ->
                                var showAppMenu by remember { mutableStateOf(false) }
                                val isWeb = !app.url.isNullOrEmpty()

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .combinedClickable(
                                            onClick = {
                                                if (isWeb) {
                                                    launchWebShortcut(context, app.url ?: "")
                                                } else {
                                                    launchApp(context, app.packageName, app.className)
                                                }
                                                viewModel.closeFolder()
                                            },
                                            onLongClick = { showAppMenu = true }
                                        )
                                        .padding(vertical = 4.dp, horizontal = 2.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (isWeb) {
                                            WebShortcutIcon(url = app.url ?: "", label = app.label, size = 56.dp)
                                        } else {
                                            AppIcon(packageName = app.packageName, className = app.className, size = 56.dp)
                                        }
                                        val appNotifCount = if (!isWeb) notificationCounts[app.packageName] ?: 0 else 0
                                        if (appNotifCount > 0) {
                                            Surface(
                                                modifier = Modifier
                                                    .align(Alignment.TopStart)
                                                    .offset(x = (-4).dp, y = (-4).dp)
                                                    .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp),
                                                shape = CircleShape,
                                                color = Color.White,
                                                border = BorderStroke(1.dp, Color(0xFFEF4444)),
                                                shadowElevation = 2.dp
                                            ) {
                                                Box(
                                                    contentAlignment = Alignment.Center,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                ) {
                                                    Text(
                                                        text = if (appNotifCount > 99) "99+" else appNotifCount.toString(),
                                                        color = Color.Red,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        lineHeight = 12.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = app.label,
                                        color = Color.White,
                                        fontSize = 10.5.sp,
                                        lineHeight = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    DropdownMenu(
                                        expanded = showAppMenu,
                                        onDismissRequest = { showAppMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("フォルダから外す") },
                                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.DriveFileMove, contentDescription = null) },
                                            onClick = {
                                                showAppMenu = false
                                                viewModel.removeAppFromFolder(folder, app)
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { viewModel.closeFolder() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.15f),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("閉じる")
                        }
                    }
                }
            }
        }

        // Folder Name Dialog (Creating folder)
        if (showFolderNameDialog) {
            val selectedGroups = selectedItems.filter { it.itemType == "GROUP" }
            val defaultName = selectedGroups.joinToString("") { it.label }
            FolderNameDialog(
                initialName = defaultName,
                onDismiss = { showFolderNameDialog = false },
                onConfirm = { name ->
                    viewModel.groupSelectedItems(name)
                    showFolderNameDialog = false
                }
            )
        }

        // Move Page or Group Select Dialog
        if (showMovePageDialog) {
            val existingFolders = homeItems.filter { it.itemType == "GROUP" }
            MoveDestinationDialog(
                existingFolders = existingFolders,
                activePageIndices = activePageIndices,
                pageLabels = pageLabels,
                onDismiss = { showMovePageDialog = false },
                onPageSelected = { pageIdx ->
                    viewModel.moveSelectedItemsToPage(pageIdx)
                    showMovePageDialog = false
                },
                onFolderSelected = { targetFolder ->
                    viewModel.moveSelectedItemsToGroup(targetFolder)
                    showMovePageDialog = false
                }
            )
        }

        if (showSettingsDialog) {
            SettingsDialog(
                viewModel = viewModel,
                onDismiss = { showSettingsDialog = false },
                onOpenWebShortcuts = {
                    showSettingsDialog = false
                    showAddWebShortcutDialog = true
                }
            )
        }

        if (showAddWebShortcutDialog) {
            val resolvedCurrentPage = currentActualPageIndex
            ManageWebShortcutsDialog(
                existingItems = homeItems,
                currentPageIndex = resolvedCurrentPage,
                pageLabels = pageLabels,
                onDismiss = { showAddWebShortcutDialog = false },
                onConfirm = { shortcuts ->
                    viewModel.replaceAllWebShortcuts(shortcuts)
                    showAddWebShortcutDialog = false
                    Toast.makeText(context, "${shortcuts.size}件のウェブショートカットを反映しました", Toast.LENGTH_SHORT).show()
                }
            )
        }

        if (editingWebShortcut != null) {
            val shortcut = editingWebShortcut!!
            EditWebShortcutDialog(
                item = shortcut,
                pageLabels = pageLabels,
                onDismiss = { editingWebShortcut = null },
                onConfirm = { newTitle, newUrl, newPageIndex ->
                    viewModel.updateWebShortcut(shortcut, newTitle, newUrl, newPageIndex)
                    editingWebShortcut = null
                    viewModel.exitSelectionMode()
                    Toast.makeText(context, "ショートカットを更新しました", Toast.LENGTH_SHORT).show()
                }
            )
        }

        if (editingItemLabel != null) {
            val item = editingItemLabel!!
            RenameItemDialog(
                item = item,
                onDismiss = { editingItemLabel = null },
                onConfirm = { newName ->
                    viewModel.updateItemLabel(item, newName)
                    editingItemLabel = null
                    viewModel.exitSelectionMode()
                    Toast.makeText(context, "名称を変更しました", Toast.LENGTH_SHORT).show()
                }
            )
        }

        if (authenticatingLockedPageIndex != null) {
            val pageIdx = authenticatingLockedPageIndex!!
            val pageLabel = pageLabels[pageIdx] ?: ""
            PageLockAuthDialog(
                pageIndex = pageIdx,
                pageLabel = pageLabel,
                correctPin = pageLockPin,
                onSuccess = {
                    viewModel.unlockPage(pageIdx)
                    authenticatingLockedPageIndex = null
                    val targetPagerIdx = activePageIndices.indexOf(pageIdx)
                    if (targetPagerIdx != -1) {
                        scope.launch {
                            pagerState.animateScrollToPage(targetPagerIdx)
                        }
                    }
                },
                onDismiss = { authenticatingLockedPageIndex = null },
                onOpenSettings = {
                    authenticatingLockedPageIndex = null
                    showSettingsDialog = true
                }
            )
        }

        if (showSettingsAuthDialog) {
            SettingsLockAuthDialog(
                correctPin = pageLockPin,
                onSuccess = {
                    viewModel.unlockSettings()
                    showSettingsAuthDialog = false
                    showSettingsDialog = true
                },
                onDismiss = { showSettingsAuthDialog = false }
            )
        }

        if (showDrawerAuthDialog) {
            DrawerLockAuthDialog(
                correctPin = pageLockPin,
                onSuccess = {
                    viewModel.unlockDrawer()
                    showDrawerAuthDialog = false
                    viewModel.setDrawerOpen(true)
                },
                onDismiss = { showDrawerAuthDialog = false },
                onOpenSettings = {
                    showDrawerAuthDialog = false
                    showSettingsDialog = true
                }
            )
        }

        // Sliding Drawer Layer (Full screen app list)
        AnimatedVisibility(
            visible = isDrawerOpen,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    clip = false
                }
        ) {
            AppDrawer(viewModel = viewModel)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeGridItem(
    item: HomeItemEntity,
    appsInGroup: List<GroupAppEntity>,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    notificationCounts: Map<String, Int> = emptyMap(),
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    textColor: Color = Color.White,
    iconSize: Dp = 56.dp,
    verticalPadding: Dp = 8.dp
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "home_item_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                clip = false
            }
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = verticalPadding, horizontal = 2.dp)
            .testTag("home_grid_item_${item.id}"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            when (item.itemType) {
                "APP" -> AppIcon(packageName = item.packageName ?: "", className = item.className, size = iconSize)
                "WEB_SHORTCUT" -> WebShortcutIcon(url = item.url ?: "", label = item.label, size = iconSize)
                else -> FolderIcon(apps = appsInGroup, size = iconSize)
            }

            // Notification count badge (Top-Left, font color red)
            val notifCount = remember(notificationCounts, item, appsInGroup) {
                when (item.itemType) {
                    "APP" -> notificationCounts[item.packageName] ?: 0
                    "GROUP" -> appsInGroup.sumOf { notificationCounts[it.packageName] ?: 0 }
                    else -> 0
                }
            }

            if (notifCount > 0) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = (-4).dp, y = (-4).dp)
                        .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp),
                    shape = CircleShape,
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFEF4444)),
                    shadowElevation = 2.dp
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = if (notifCount > 99) "99+" else notifCount.toString(),
                            color = Color.Red,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 12.sp,
                            style = androidx.compose.ui.text.TextStyle(
                                platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                    includeFontPadding = false
                                )
                            )
                        )
                    }
                }
            }

            // Selection indicator badge
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .size(iconSize)
                        .background(
                            color = if (isSelected) Color(0x663B82F6) else Color(0x22000000),
                            shape = RoundedCornerShape(14.dp)
                        )
                )

                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .background(
                            color = if (isSelected) Color(0xFF3B82F6) else Color.White.copy(alpha = 0.3f),
                            shape = CircleShape
                        )
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = item.label,
            color = textColor,
            fontSize = 10.5.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun AppIcon(
    packageName: String,
    className: String? = null,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    innerPadding: Dp = 6.dp,
    cornerRadius: Dp = 14.dp
) {
    val context = LocalContext.current
    val imageBitmap = remember(packageName, className) {
        if (packageName.isBlank()) return@remember null
        try {
            val pm = context.packageManager
            val densityDpi = context.resources.displayMetrics.densityDpi
            val drawable: android.graphics.drawable.Drawable? =
                resolveLauncherActivity(context, packageName, className)?.getIcon(densityDpi)
                ?: if (!className.isNullOrBlank()) {
                    try {
                        pm.getActivityIcon(android.content.ComponentName(packageName, className))
                    } catch (e: Exception) {
                        val intent = pm.getLaunchIntentForPackage(packageName)
                        val resolveInfo = if (intent != null) pm.resolveActivity(intent, 0) else null
                        resolveInfo?.loadIcon(pm) ?: pm.getApplicationIcon(packageName)
                    }
                } else {
                    val intent = pm.getLaunchIntentForPackage(packageName)
                    val resolveInfo = if (intent != null) pm.resolveActivity(intent, 0) else null
                    resolveInfo?.loadIcon(pm) ?: pm.getApplicationIcon(packageName)
                }
            drawable?.toImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .background(Color.White.copy(alpha = 0.12f), shape = RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                filterQuality = FilterQuality.Low,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Android,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(size * 0.5f)
            )
        }
    }
}

@Composable
fun WebShortcutIcon(
    url: String,
    label: String = "",
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    innerPadding: Dp = 6.dp,
    cornerRadius: Dp = 14.dp,
    showLinkBadge: Boolean = true
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val iconVersion by com.example.util.WebShortcutIconManager.iconUpdateVersion.collectAsStateWithLifecycle()
    val cachedFile = remember(url, iconVersion) {
        com.example.util.WebShortcutIconManager.getCachedIconFile(context, url)
    }

    LaunchedEffect(url) {
        if (cachedFile == null && url.isNotBlank()) {
            com.example.util.WebShortcutIconManager.fetchAndCacheAsync(context, url)
        }
    }

    val (faviconUrl, snsBgColor) = remember(url, label) {
        val lowerUrl = url.lowercase().trim()
        val lowerLabel = label.lowercase().trim()

        val domain = try {
            when {
                lowerUrl.startsWith("line:") || lowerUrl.contains("line.me") || lowerLabel.contains("line") -> "line.me"
                lowerUrl.contains("youtube.com") || lowerUrl.contains("youtu.be") || lowerLabel.contains("youtube") -> "youtube.com"
                lowerUrl.contains("twitter.com") || lowerUrl.contains("x.com") || lowerLabel.contains("twitter") || lowerLabel == "x" -> "x.com"
                lowerUrl.contains("instagram.com") || lowerLabel.contains("instagram") -> "instagram.com"
                lowerUrl.contains("facebook.com") || lowerLabel.contains("facebook") -> "facebook.com"
                lowerUrl.contains("tiktok.com") || lowerLabel.contains("tiktok") -> "tiktok.com"
                lowerUrl.contains("yahoo.co.jp") || lowerUrl.contains("yahoo.com") || lowerLabel.contains("yahoo") -> "yahoo.co.jp"
                lowerUrl.contains("google.co.jp") || lowerUrl.contains("google.com") || lowerLabel.contains("google") -> "google.com"
                lowerUrl.contains("github.com") || lowerLabel.contains("github") -> "github.com"
                lowerUrl.contains("amazon.co.jp") || lowerUrl.contains("amazon.com") || lowerLabel.contains("amazon") -> "amazon.co.jp"
                else -> {
                    val parsed = Uri.parse(if (lowerUrl.startsWith("http://") || lowerUrl.startsWith("https://")) lowerUrl else "https://$lowerUrl")
                    parsed.host ?: ""
                }
            }
        } catch (e: Exception) {
            ""
        }

        val bgColor = when (domain) {
            "line.me" -> Color(0xFF06C755) // Official LINE Green
            "youtube.com" -> Color(0xFFFF0000) // YouTube Red
            "x.com" -> Color(0xFF000000) // X / Twitter
            "instagram.com" -> Color(0xFFE4405F) // Instagram
            "facebook.com" -> Color(0xFF1877F2) // Facebook
            "tiktok.com" -> Color(0xFF000000) // TikTok
            "yahoo.co.jp" -> Color(0xFFFF0033) // Yahoo
            "google.com" -> Color(0xFF4285F4) // Google
            "github.com" -> Color(0xFF24292E)
            "amazon.co.jp" -> Color(0xFFFF9900)
            else -> Color(0xFF0284C7)
        }

        val iconUrl = if (domain.isNotEmpty()) {
            "https://www.google.com/s2/favicons?domain=$domain&sz=128"
        } else if (lowerUrl.startsWith("http://") || lowerUrl.startsWith("https://")) {
            "https://www.google.com/s2/favicons?domain_url=${Uri.encode(lowerUrl)}&sz=128"
        } else {
            null
        }

        Pair(iconUrl, bgColor)
    }

    Box(
        modifier = modifier
            .size(size)
            .background(
                color = if (cachedFile != null) Color.Transparent else snsBgColor,
                shape = RoundedCornerShape(cornerRadius)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (cachedFile != null) {
            SubcomposeAsyncImage(
                model = cachedFile,
                contentDescription = label.ifEmpty { "Web Shortcut" },
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(cornerRadius)),
                contentScale = ContentScale.Crop
            ) {
                val state = painter.state
                if (state is AsyncImagePainter.State.Success) {
                    SubcomposeAsyncImageContent()
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(snsBgColor, RoundedCornerShape(cornerRadius)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Public,
                            contentDescription = "Web Shortcut",
                            tint = Color.White,
                            modifier = Modifier.size(size * 0.5f)
                        )
                    }
                }
            }
        } else if (!faviconUrl.isNullOrEmpty()) {
            SubcomposeAsyncImage(
                model = faviconUrl,
                contentDescription = label.ifEmpty { "Web Shortcut" },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .clip(RoundedCornerShape((cornerRadius - 2.dp).coerceAtLeast(1.dp))),
                contentScale = ContentScale.Fit
            ) {
                val state = painter.state
                if (state is AsyncImagePainter.State.Success) {
                    SubcomposeAsyncImageContent()
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Public,
                        contentDescription = "Web Shortcut",
                        tint = Color.White,
                        modifier = Modifier.size(size * 0.5f)
                    )
                }
            }
        } else {
            Icon(
                imageVector = Icons.Rounded.Public,
                contentDescription = "Web Shortcut",
                tint = Color.White,
                modifier = Modifier.size(size * 0.5f)
            )
        }

        if (showLinkBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(2.dp)
                    .size(size * 0.32f)
                    .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Link,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.2f)
                )
            }
        }
    }
}

@Composable
fun FolderIcon(
    apps: List<GroupAppEntity>,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .background(Color.White.copy(alpha = 0.22f), shape = RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = false,
            verticalArrangement = Arrangement.spacedBy(0.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            items(apps.take(9)) { app ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (!app.url.isNullOrEmpty()) {
                        WebShortcutIcon(
                            url = app.url,
                            label = app.label,
                            size = size / 3,
                            innerPadding = 0.dp,
                            cornerRadius = 2.dp,
                            showLinkBadge = false
                        )
                    } else {
                        AppIcon(
                            packageName = app.packageName,
                            className = app.className,
                            size = size / 3,
                            innerPadding = 0.dp,
                            cornerRadius = 2.dp
                        )
                    }
                }
            }
            items(9 - apps.size.coerceAtMost(9)) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White.copy(alpha = 0.05f))
                    )
                }
            }
        }
    }
}

private fun cleanBrackets(str: String): String {
    val trimmed = str.trim()
    if (trimmed.startsWith("[") && trimmed.endsWith("]") && trimmed.length >= 2) {
        return trimmed.substring(1, trimmed.length - 1).trim()
    }
    return trimmed
}

fun parseWebShortcutsFromText(text: String, defaultPageIndex: Int): List<Triple<String, String, Int>> {
    val result = mutableListOf<Triple<String, String, Int>>()

    var currentTitle = ""
    var currentUrl = ""
    var currentPage = defaultPageIndex

    fun commitCurrent() {
        if (currentTitle.isNotBlank() && currentUrl.isNotBlank()) {
            result.add(Triple(currentTitle, currentUrl, currentPage))
        }
        currentTitle = ""
        currentUrl = ""
        currentPage = defaultPageIndex
    }

    val lines = text.lines()
    for (rawLine in lines) {
        val line = rawLine.trim()
        if (line.isEmpty()) {
            commitCurrent()
            continue
        }

        val lower = line.lowercase()
        when {
            lower.startsWith("title:") || lower.startsWith("title：") || lower.startsWith("title=") ||
            lower.startsWith("名称:") || lower.startsWith("名称：") || lower.startsWith("タイトル:") || lower.startsWith("タイトル：") -> {
                if (currentTitle.isNotBlank() && currentUrl.isNotBlank()) {
                    commitCurrent()
                }
                val value = line.substringAfter(":").substringAfter("：").substringAfter("=").trim()
                currentTitle = cleanBrackets(value)
            }
            lower.startsWith("url:") || lower.startsWith("url：") || lower.startsWith("url=") ||
            lower.startsWith("intent:") || lower.startsWith("intent：") || lower.startsWith("intent=") ||
            lower.startsWith("アドレス:") || lower.startsWith("アドレス：") -> {
                val value = line.substringAfter(":").substringAfter("：").substringAfter("=").trim()
                currentUrl = cleanBrackets(value)
            }
            lower.startsWith("page:") || lower.startsWith("page：") || lower.startsWith("page=") ||
            lower.startsWith("配置先:") || lower.startsWith("配置先：") || lower.startsWith("ページ:") || lower.startsWith("ページ：") -> {
                val value = line.substringAfter(":").substringAfter("：").substringAfter("=").trim()
                val cleanedValue = cleanBrackets(value)
                val pNum = cleanedValue.toIntOrNull()
                if (pNum != null) {
                    currentPage = (pNum - 1).coerceAtLeast(0)
                }
            }
            else -> {
                if (currentTitle.isBlank()) {
                    currentTitle = cleanBrackets(line)
                } else if (currentUrl.isBlank()) {
                    currentUrl = cleanBrackets(line)
                } else {
                    val pNum = cleanBrackets(line).toIntOrNull()
                    if (pNum != null) {
                        currentPage = (pNum - 1).coerceAtLeast(0)
                    }
                    commitCurrent()
                }
            }
        }
    }
    commitCurrent()

    return result
}

@Composable
fun WebShortcutTextDialog(
    dialogTitle: String,
    initialText: String,
    defaultPageIndex: Int,
    pageLabels: Map<Int, String> = emptyMap(),
    onDismiss: () -> Unit,
    onConfirm: (shortcuts: List<Triple<String, String, Int>>) -> Unit
) {
    val context = LocalContext.current
    var rawText by remember(initialText) { mutableStateOf(initialText) }

    val parsedShortcuts = remember(rawText) {
        parseWebShortcutsFromText(rawText, defaultPageIndex)
    }

    val pageGuideText = remember(pageLabels) {
        val labeledPages = pageLabels.entries.filter { it.value.isNotBlank() }
            .sortedBy { it.key }
            .map { "page:${it.key + 1} (${it.value})" }
        if (labeledPages.isNotEmpty()) {
            "・設定済みラベル: " + labeledPages.joinToString(", ")
        } else ""
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 25.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0F172A),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = dialogTitle,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "閉じる",
                            tint = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Guide format box
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.06f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "【ショートカット一括編集・追加の使い方】",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "・既存のショートカットが一覧表示されます。テキストを修正・追記・削除して「反映・保存」してください。\n" +
                                    "・フォーマット:\n" +
                                    "title:[タイトル]\n" +
                                    "url:[URLまたはIntent]\n" +
                                    "page:[配置ページ番号]" +
                                    (if (pageGuideText.isNotEmpty()) "\n$pageGuideText" else ""),
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AssistChip(
                                onClick = {
                                    val pageNum = if (defaultPageIndex < 0) 1 else defaultPageIndex + 1
                                    val template = "title:LINEトーク\nurl:https://line.me/R/nv/chat\npage:$pageNum"
                                    rawText = if (rawText.isBlank()) template else "$rawText\n\n$template"
                                },
                                label = { Text("+ LINEトーク雛形", fontSize = 11.sp) },
                                colors = AssistChipDefaults.assistChipColors(labelColor = Color(0xFF10B981))
                            )
                            AssistChip(
                                onClick = {
                                    val pageNum = if (defaultPageIndex < 0) 1 else defaultPageIndex + 1
                                    val template = "title:Yahoo! JAPAN\nurl:https://yahoo.co.jp\npage:$pageNum"
                                    rawText = if (rawText.isBlank()) template else "$rawText\n\n$template"
                                },
                                label = { Text("+ Web追加雛形", fontSize = 11.sp) },
                                colors = AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Text Editor
                OutlinedTextField(
                    value = rawText,
                    onValueChange = { rawText = it },
                    placeholder = {
                        Text(
                            text = "title:[タイトル]\nurl:[URL]\npage:[ページ番号]",
                            color = Color.White.copy(alpha = 0.3f),
                            fontSize = 13.sp
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color.Black.copy(alpha = 0.3f),
                        unfocusedContainerColor = Color.Black.copy(alpha = 0.2f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 14.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        lineHeight = 20.sp
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Detection Count & Clear
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (parsedShortcuts.isNotEmpty()) "解析結果: ${parsedShortcuts.size}件のショートカット" else "title / url / page を入力してください",
                        fontSize = 12.sp,
                        color = if (parsedShortcuts.isNotEmpty()) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f)
                    )

                    if (rawText.isNotEmpty()) {
                        TextButton(
                            onClick = { rawText = "" },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("クリア", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("キャンセル", color = Color.White.copy(alpha = 0.7f))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (parsedShortcuts.isNotEmpty()) {
                                onConfirm(parsedShortcuts)
                            }
                        },
                        enabled = parsedShortcuts.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("反映する")
                    }
                }
            }
        }
    }
}

@Composable
fun ManageWebShortcutsDialog(
    existingItems: List<HomeItemEntity>,
    currentPageIndex: Int,
    pageLabels: Map<Int, String> = emptyMap(),
    onDismiss: () -> Unit,
    onConfirm: (shortcuts: List<Triple<String, String, Int>>) -> Unit
) {
    val initialPageNumber = if (currentPageIndex < 0) 1 else currentPageIndex + 1
    val initialText = remember(existingItems) {
        val webItems = existingItems.filter { it.itemType == "WEB_SHORTCUT" || !it.url.isNullOrEmpty() }
        if (webItems.isNotEmpty()) {
            webItems.joinToString("\n\n") { item ->
                val pageNum = if (item.pageIndex < 0) 1 else item.pageIndex + 1
                "title:${item.label}\nurl:${item.url ?: ""}\npage:$pageNum"
            }
        } else {
            "title:Google\nurl:https://google.com\npage:$initialPageNumber"
        }
    }

    WebShortcutTextDialog(
        dialogTitle = "ウェブショートカット修正・追加",
        initialText = initialText,
        defaultPageIndex = if (currentPageIndex < 0) 0 else currentPageIndex,
        pageLabels = pageLabels,
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}

@Composable
fun AddWebShortcutDialog(
    currentPageIndex: Int,
    pageLabels: Map<Int, String> = emptyMap(),
    onDismiss: () -> Unit,
    onConfirm: (shortcuts: List<Triple<String, String, Int>>) -> Unit
) {
    val initialPageNumber = if (currentPageIndex < 0) 1 else currentPageIndex + 1
    val defaultText = remember {
        "title:Google\nurl:https://google.com\npage:$initialPageNumber"
    }

    WebShortcutTextDialog(
        dialogTitle = "ウェブショートカット追加",
        initialText = defaultText,
        defaultPageIndex = if (currentPageIndex < 0) 0 else currentPageIndex,
        pageLabels = pageLabels,
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}

@Composable
fun EditWebShortcutDialog(
    item: HomeItemEntity,
    pageLabels: Map<Int, String> = emptyMap(),
    onDismiss: () -> Unit,
    onConfirm: (newTitle: String, newUrl: String, newPageIndex: Int) -> Unit
) {
    val initialPageNumber = if (item.pageIndex < 0) 1 else item.pageIndex + 1
    val initialText = remember(item) {
        "title:${item.label}\nurl:${item.url ?: ""}\npage:$initialPageNumber"
    }

    WebShortcutTextDialog(
        dialogTitle = "ウェブショートカット編集",
        initialText = initialText,
        defaultPageIndex = if (item.pageIndex < 0) 0 else item.pageIndex,
        pageLabels = pageLabels,
        onDismiss = onDismiss,
        onConfirm = { parsedList ->
            val first = parsedList.firstOrNull()
            if (first != null) {
                onConfirm(first.first, first.second, first.third)
            }
        }
    )
}

@Composable
fun RenameItemDialog(
    item: HomeItemEntity,
    onDismiss: () -> Unit,
    onConfirm: (newName: String) -> Unit
) {
    var text by remember { mutableStateOf(item.label) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("名前の変更", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "表示名を入力してください。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("名称") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (text.isNotBlank()) onConfirm(text.trim()) },
                enabled = text.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

@Composable
fun FolderNameDialog(
    initialName: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("グループ作成・統合", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("グループ名を入力してください") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { if (text.isNotBlank()) onConfirm(text.trim()) },
                enabled = text.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("作成")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

@Composable
fun MoveDestinationDialog(
    title: String = "移動先を選択",
    existingFolders: List<HomeItemEntity>,
    activePageIndices: List<Int> = listOf(0, 1, 2),
    pageLabels: Map<Int, String> = emptyMap(),
    onDismiss: () -> Unit,
    onPageSelected: (Int) -> Unit,
    onFolderSelected: (HomeItemEntity) -> Unit
) {
    val dialogBgColor = AlertDialogDefaults.containerColor
    val isDialogLight = (dialogBgColor.red * 0.299f + dialogBgColor.green * 0.587f + dialogBgColor.blue * 0.114f) > 0.5f
    val titleTextColor = if (isDialogLight) Color(0xFF1C1B1F) else Color(0xFFF4F4F4)
    val subtitleTextColor = if (isDialogLight) Color(0xFF49454F) else Color(0xFFCAC4D0)

    val cardBgColor = MaterialTheme.colorScheme.surfaceVariant
    val isCardLight = (cardBgColor.red * 0.299f + cardBgColor.green * 0.587f + cardBgColor.blue * 0.114f) > 0.5f
    val cardTextColor = if (isCardLight) Color(0xFF1C1B1F) else Color(0xFFF4F4F4)
    val cardSubIconColor = if (isCardLight) Color(0xFF49454F) else Color(0xFFCAC4D0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold, color = titleTextColor) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "移動先のページ、または既存のグループを選択してください",
                    fontSize = 13.sp,
                    color = subtitleTextColor,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Text(
                    text = "ページ / ドック",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                val pageOptions = remember(activePageIndices, pageLabels) {
                    listOf(-1 to "ドック (下部固定)") + activePageIndices.mapIndexed { index, i ->
                        val label = pageLabels[i]?.trim()
                        val screenNum = index + 1
                        val displayName = if (!label.isNullOrEmpty()) {
                            "$label (ページ $screenNum)"
                        } else {
                            "ページ $screenNum"
                        }
                        i to displayName
                    }
                }
                pageOptions.forEach { (pageIdx, name) ->
                    Card(
                        onClick = { onPageSelected(pageIdx) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = cardBgColor,
                            contentColor = cardTextColor
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (pageIdx == -1) Icons.Rounded.Star else Icons.Rounded.Layers,
                                    contentDescription = null,
                                    tint = cardSubIconColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = name,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp,
                                    color = cardTextColor
                                )
                            }
                        }
                    }
                }

                if (existingFolders.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "既存のグループ/フォルダ",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    existingFolders.forEach { folder ->
                        Card(
                            onClick = { onFolderSelected(folder) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = cardBgColor,
                                contentColor = cardTextColor
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = folder.label,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp,
                                        color = cardTextColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

@Composable
fun PageLockAuthDialog(
    pageIndex: Int,
    pageLabel: String,
    correctPin: String,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit = {}
) {
    var inputPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    val titleText = if (pageLabel.isNotBlank()) {
        "「$pageLabel」(ページ ${pageIndex + 1}) のロック解除"
    } else {
        "ページ ${pageIndex + 1} のロック解除"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(titleText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (correctPin.isNotBlank()) {
                    Text("このページは保護されています。暗証番号を入力してロックを解除してください。", fontSize = 13.sp)
                    OutlinedTextField(
                        value = inputPin,
                        onValueChange = {
                            inputPin = it.filter { ch -> ch.isDigit() }
                            errorMessage = ""
                        },
                        label = { Text("暗証番号 (PIN)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text("暗証番号（PIN）が未設定です。ページの保護を有効にするには、設定画面で暗証番号を設定してください。", fontSize = 13.sp)
                }
                if (errorMessage.isNotEmpty()) {
                    Text(errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            if (correctPin.isNotBlank()) {
                Button(
                    onClick = {
                        if (inputPin == correctPin) {
                            onSuccess()
                        } else {
                            errorMessage = "暗証番号が正しくありません"
                        }
                    }
                ) {
                    Text("解除")
                }
            } else {
                Button(
                    onClick = {
                        onDismiss()
                        onOpenSettings()
                    }
                ) {
                    Text("設定を開く")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

@Composable
fun SettingsLockAuthDialog(
    correctPin: String,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    var inputPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("設定画面のロック解除", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (correctPin.isNotBlank()) {
                    Text("設定画面は保護されています。暗証番号を入力してロックを解除してください。", fontSize = 13.sp)
                    OutlinedTextField(
                        value = inputPin,
                        onValueChange = {
                            inputPin = it.filter { ch -> ch.isDigit() }
                            errorMessage = ""
                        },
                        label = { Text("暗証番号 (PIN)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text("暗証番号（PIN）が未設定です。「設定を開く」をタップして暗証番号を設定してください。", fontSize = 13.sp)
                }
                if (errorMessage.isNotEmpty()) {
                    Text(errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (correctPin.isBlank()) {
                        onSuccess()
                    } else if (inputPin == correctPin) {
                        onSuccess()
                    } else {
                        errorMessage = "暗証番号が正しくありません"
                    }
                }
            ) {
                Text(if (correctPin.isBlank()) "設定を開く" else "解除")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

@Composable
fun DrawerLockAuthDialog(
    correctPin: String,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit = {}
) {
    var inputPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("アプリ一覧のロック解除", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (correctPin.isNotBlank()) {
                    Text("アプリ一覧は保護されています。暗証番号を入力してロックを解除してください。", fontSize = 13.sp)
                    OutlinedTextField(
                        value = inputPin,
                        onValueChange = {
                            inputPin = it.filter { ch -> ch.isDigit() }
                            errorMessage = ""
                        },
                        label = { Text("暗証番号 (PIN)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text("暗証番号（PIN）が未設定です。アプリ一覧の保護を有効にするには、設定画面で暗証番号を設定してください。", fontSize = 13.sp)
                }
                if (errorMessage.isNotEmpty()) {
                    Text(errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            if (correctPin.isNotBlank()) {
                Button(
                    onClick = {
                        if (inputPin == correctPin) {
                            onSuccess()
                        } else {
                            errorMessage = "暗証番号が正しくありません"
                        }
                    }
                ) {
                    Text("解除")
                }
            } else {
                Button(
                    onClick = {
                        onDismiss()
                        onOpenSettings()
                    }
                ) {
                    Text("設定を開く")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

data class ColorPreset(val name: String, val hex: String)

val JAPANESE_COLOR_PRESETS = listOf(
    ColorPreset("若菜色", "#D8E698"),
    ColorPreset("若竹色", "#68BE8D"),
    ColorPreset("空色", "#A0D8EF"),
    ColorPreset("青磁鼠", "#BED2C3"),
    ColorPreset("山鳩色", "#767C6B"),
    ColorPreset("古代紫", "#895B8A"),
    ColorPreset("菖蒲色", "#CC7EB1"),
    ColorPreset("浅紫", "#C4A3BF"),
    ColorPreset("宍色", "#EFAB93"),
    ColorPreset("蒸栗色", "#EBE1A9"),
    ColorPreset("柑子色", "#FF8A00"),
    ColorPreset("桜色", "#FEDFE1"),
    ColorPreset("藍色", "#165E83"),
    ColorPreset("深緋", "#C7372F"),
    ColorPreset("漆黒", "#202020"),
    ColorPreset("白", "#FFFFFF")
)

fun getColorDisplayName(hex: String?): String {
    if (hex.isNullOrBlank() || hex == "default") return "若菜色"
    val cleanHex = if (hex.startsWith("#")) hex.uppercase() else "#${hex.uppercase()}"
    val found = JAPANESE_COLOR_PRESETS.firstOrNull { it.hex.equals(cleanHex, ignoreCase = true) }
    return found?.name ?: cleanHex
}

@Composable
fun PageColorPickerDialog(
    pageIndex: Int,
    pageLabel: String,
    initialColorHex: String,
    onColorSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: プリセット, 1: カスタム(HSB)

    val initialHsv = remember(initialColorHex) {
        val parsed = try {
            val hex = if (initialColorHex.startsWith("#")) initialColorHex else "#$initialColorHex"
            android.graphics.Color.parseColor(hex)
        } catch (e: Exception) {
            android.graphics.Color.parseColor("#D8E698")
        }
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(parsed, hsv)
        hsv
    }

    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }

    var currentHexInput by remember {
        mutableStateOf(
            String.format("#%06X", 0xFFFFFF and android.graphics.Color.HSVToColor(floatArrayOf(initialHsv[0], initialHsv[1], initialHsv[2])))
        )
    }

    val currentIntColor = remember(hue, saturation, value) {
        android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))
    }
    val currentComposeColor = remember(currentIntColor) { Color(currentIntColor) }
    val currentHex = remember(currentIntColor) {
        String.format("#%06X", 0xFFFFFF and currentIntColor)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "背景色の設定",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "閉じる")
                    }
                }
                Text(
                    text = if (pageLabel.isNotBlank()) "${pageLabel} (${pageIndex + 1}ページ)" else "${pageIndex + 1}ページ",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                val textColor = run {
                    val lum = currentComposeColor.red * 0.299f + currentComposeColor.green * 0.587f + currentComposeColor.blue * 0.114f
                    if (lum > 0.6f) Color(0xFF1E1E1E) else Color.White
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(currentComposeColor)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$currentHex  (${getColorDisplayName(currentHex)})",
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("プリセット", fontSize = 12.sp, fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("カスタム (HSB)", fontSize = 12.sp, fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (selectedTab == 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val chunked = JAPANESE_COLOR_PRESETS.chunked(2)
                        for (row in chunked) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (preset in row) {
                                    val presetColor = try {
                                        Color(android.graphics.Color.parseColor(preset.hex))
                                    } catch (e: Exception) {
                                        Color(0xFFD8E698)
                                    }
                                    val pTextColor = run {
                                        val lum = presetColor.red * 0.299f + presetColor.green * 0.587f + presetColor.blue * 0.114f
                                        if (lum > 0.6f) Color(0xFF1E1E1E) else Color.White
                                    }
                                    val isSelected = currentHex.equals(preset.hex, ignoreCase = true)

                                    Surface(
                                        onClick = {
                                            val parsed = android.graphics.Color.parseColor(preset.hex)
                                            val hsv = FloatArray(3)
                                            android.graphics.Color.colorToHSV(parsed, hsv)
                                            hue = hsv[0]
                                            saturation = hsv[1]
                                            value = hsv[2]
                                            currentHexInput = preset.hex
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = presetColor,
                                        border = BorderStroke(
                                            width = if (isSelected) 2.5.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = preset.name,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = pTextColor
                                            )
                                            if (isSelected) {
                                                Icon(
                                                    Icons.Rounded.Check,
                                                    contentDescription = null,
                                                    tint = pTextColor,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("色相 (Hue)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${hue.toInt()}°", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                Color.Red,
                                                Color.Yellow,
                                                Color.Green,
                                                Color.Cyan,
                                                Color.Blue,
                                                Color.Magenta,
                                                Color.Red
                                            )
                                        )
                                    )
                            )
                            Slider(
                                value = hue,
                                onValueChange = {
                                    hue = it
                                    currentHexInput = String.format("#%06X", 0xFFFFFF and android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)))
                                },
                                valueRange = 0f..360f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("彩度 (Saturation)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${(saturation * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = saturation,
                                onValueChange = {
                                    saturation = it
                                    currentHexInput = String.format("#%06X", 0xFFFFFF and android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)))
                                },
                                valueRange = 0f..1f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("明度 (Brightness)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${(value * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = value,
                                onValueChange = {
                                    value = it
                                    currentHexInput = String.format("#%06X", 0xFFFFFF and android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)))
                                },
                                valueRange = 0f..1f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = currentHexInput,
                                onValueChange = { input ->
                                    currentHexInput = input
                                    try {
                                        val clean = if (input.startsWith("#")) input else "#$input"
                                        val p = android.graphics.Color.parseColor(clean)
                                        val hsv = FloatArray(3)
                                        android.graphics.Color.colorToHSV(p, hsv)
                                        hue = hsv[0]
                                        saturation = hsv[1]
                                        value = hsv[2]
                                    } catch (e: Exception) {}
                                },
                                label = { Text("HEXコード", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("キャンセル")
                    }
                    Button(
                        onClick = {
                            onColorSelected(currentHex)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("決定", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsDialog(
    viewModel: LauncherViewModel,
    onDismiss: () -> Unit,
    onOpenWebShortcuts: () -> Unit
) {
    val context = LocalContext.current
    var backupJson by remember { mutableStateOf("") }
    var importText by remember { mutableStateOf("") }
    var driveUrlText by remember { mutableStateOf("") }
    var showImportSection by remember { mutableStateOf(false) }
    var importMode by remember { mutableIntStateOf(0) } // 0: Google Drive/File SAF, 1: Drive URL, 2: JSON Paste
    var isDownloadingDrive by remember { mutableStateOf(false) }

    val pageBackgroundColors by viewModel.pageBackgroundColors.collectAsStateWithLifecycle()
    val enabledPages by viewModel.enabledPages.collectAsStateWithLifecycle()
    val pageLabels by viewModel.pageLabels.collectAsStateWithLifecycle()
    val lockedPages by viewModel.lockedPages.collectAsStateWithLifecycle()
    val isSettingsLocked by viewModel.isSettingsLocked.collectAsStateWithLifecycle()
    val isDrawerEnabled by viewModel.isDrawerEnabled.collectAsStateWithLifecycle()
    val isDrawerLocked by viewModel.isDrawerLocked.collectAsStateWithLifecycle()
    val isUninstallLocked by viewModel.isUninstallLocked.collectAsStateWithLifecycle()
    val pageLockPin by viewModel.pageLockPin.collectAsStateWithLifecycle()
    val backupUserName by viewModel.backupUserName.collectAsStateWithLifecycle()
    val pageOrder by viewModel.pageOrder.collectAsStateWithLifecycle()

    var tempEnabledPages by remember { mutableStateOf(enabledPages) }
    var tempPageLabels by remember { mutableStateOf(pageLabels) }
    var tempPageColors by remember { mutableStateOf(pageBackgroundColors) }
    var tempLockedPages by remember { mutableStateOf(lockedPages) }
    var tempIsSettingsLocked by remember { mutableStateOf(isSettingsLocked) }
    var tempIsDrawerEnabled by remember { mutableStateOf(isDrawerEnabled) }
    var tempIsDrawerLocked by remember { mutableStateOf(isDrawerLocked) }
    var tempIsUninstallLocked by remember { mutableStateOf(isUninstallLocked) }
    var tempPageOrder by remember(pageOrder) { mutableStateOf(pageOrder) }
    var tempPageLockPin by remember { mutableStateOf(pageLockPin) }
    var tempBackupUserName by remember(backupUserName) { mutableStateOf(backupUserName) }

    var draggingPageIdx by remember { mutableStateOf<Int?>(null) }
    var accumulatedDragY by remember { mutableFloatStateOf(0f) }

    val density = LocalDensity.current
    val itemHeightPx = with(density) { 110.dp.toPx() }

    var colorPickerTargetPageIndex by remember { mutableStateOf<Int?>(null) }
    var selectedPageIndex by remember { mutableIntStateOf(0) }
    var customHexInput by remember { mutableStateOf("") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader().use { it.readText() }
                }
                if (!content.isNullOrBlank()) {
                    viewModel.importConfig(content) { success, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        if (success) {
                            onDismiss()
                        }
                    }
                } else {
                    Toast.makeText(context, "ファイルが空です", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "ファイル読込失敗: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Read current layout config instantly & whenever settings change
    LaunchedEffect(pageBackgroundColors, enabledPages) {
        viewModel.exportConfig { json ->
            backupJson = json
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "EdLauncher 設定",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "閉じる",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "各種設定がおこなえます。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // 1. Web Shortcuts Management Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Language,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "ショートカットの一括管理",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "ウェブサイトのショートカットを一括追加できます。",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = onOpenWebShortcuts,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("ショートカットの一括編集・追加")
                            }
                        }
                    }

                    // 1.2. Notification Access & Badges Card
                    val flatNotif = android.provider.Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                    val isNotifListenerEnabled = flatNotif != null && flatNotif.contains(context.packageName)

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Notifications,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "通知件数バッジ機能",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isNotifListenerEnabled) "ステータス: 許可済み（通知数を表示中）" else "ステータス: 未許可（通知数を表示するにはシステム設定で通知アクセスを許可してください）",
                                fontSize = 11.sp,
                                color = if (isNotifListenerEnabled) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        try {
                                            val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "設定画面を開けませんでした", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("通知アクセス設定を開く", fontSize = 11.sp)
                                }
                                if (!isNotifListenerEnabled) {
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.clearAllNotificationCounts()
                                            Toast.makeText(context, "バッジをクリアしました", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.weight(0.7f)
                                    ) {
                                        Text("バッジクリア", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    // 1.5. Enabled Pages, Labels, Locks & Colors Setting Card (添付画像デザイン準拠)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.GridView,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "表示するページとラベル設定 (1〜9ページ)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "表示するページをONに設定し、各ページの表示ラベル（10文字以内）や背景色を登録できます。右側の「≡」ハンドルを長押しして上下にドラッグすると、ページの並び順（表示順序）を自由に入れ替えられます。",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            // 1. 設定
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                    .padding(10.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = "設定",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                FilterChip(
                                    selected = tempIsSettingsLocked,
                                    onClick = { tempIsSettingsLocked = !tempIsSettingsLocked },
                                    leadingIcon = {
                                        if (tempIsSettingsLocked) {
                                            Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
                                        } else {
                                            Icon(Icons.Rounded.LockOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = if (tempIsSettingsLocked) "ロック中" else "ロック",
                                            fontSize = 12.sp,
                                            fontWeight = if (tempIsSettingsLocked) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // 2. アプリ一覧
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                    .padding(10.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = "アプリ一覧",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 表示/非表示ボタン
                                    FilterChip(
                                        selected = tempIsDrawerEnabled,
                                        onClick = { tempIsDrawerEnabled = !tempIsDrawerEnabled },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (tempIsDrawerEnabled) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = if (tempIsDrawerEnabled) "アプリ一覧(表示)" else "アプリ一覧(非表示)",
                                                fontSize = 12.sp,
                                                fontWeight = if (tempIsDrawerEnabled) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(32.dp)
                                    )

                                    // ロックボタン
                                    FilterChip(
                                        selected = tempIsDrawerLocked,
                                        onClick = { tempIsDrawerLocked = !tempIsDrawerLocked },
                                        leadingIcon = {
                                            if (tempIsDrawerLocked) {
                                                Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
                                            } else {
                                                Icon(Icons.Rounded.LockOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                                            }
                                        },
                                        label = {
                                            Text(
                                                text = if (tempIsDrawerLocked) "ロック中" else "ロック",
                                                fontSize = 12.sp,
                                                fontWeight = if (tempIsDrawerLocked) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(32.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            Spacer(modifier = Modifier.height(8.dp))

                            // 3. 各ページ (1〜9ページ) - ドラッグ＆ドロップ（≡ハンドル長押し）並び替え対応
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                for (pageIdx in tempPageOrder) {
                                    val p = pageIdx + 1
                                    val isEnabled = tempEnabledPages.contains(pageIdx)
                                    val isLocked = tempLockedPages.contains(pageIdx)
                                    val currentText = tempPageLabels[pageIdx] ?: ""
                                    val currentColorHex = tempPageColors[pageIdx] ?: "#D8E698"
                                    val displayName = getColorDisplayName(currentColorHex)
                                    val isDraggingThis = draggingPageIdx == pageIdx

                                    val parsedColor = remember(currentColorHex) {
                                        try {
                                            Color(android.graphics.Color.parseColor(if (currentColorHex == "default" || currentColorHex.isBlank()) "#D8E698" else currentColorHex))
                                        } catch (e: Exception) {
                                            Color(0xFFD8E698)
                                        }
                                    }
                                    val colorButtonTextColor = remember(parsedColor) {
                                        val lum = parsedColor.red * 0.299f + parsedColor.green * 0.587f + parsedColor.blue * 0.114f
                                        if (lum > 0.55f) Color(0xFF1E1E1E) else Color.White
                                    }

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (isDraggingThis) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                                else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                            )
                                            .border(
                                                width = if (isDraggingThis) 2.dp else 1.dp,
                                                color = if (isDraggingThis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .padding(10.dp)
                                    ) {
                                        // 上段: ラベル入力欄 と 右側の「≡（ドラッグハンドル）」
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedTextField(
                                                value = currentText,
                                                onValueChange = { newLabel: String ->
                                                    if (newLabel.length <= 10) {
                                                        tempPageLabels = tempPageLabels.toMutableMap().apply {
                                                            put(pageIdx, newLabel)
                                                        }
                                                    }
                                                },
                                                placeholder = { Text("${p}ページラベル (10文字以内)", fontSize = 12.sp) },
                                                label = { Text("${p}ページラベル", fontSize = 11.sp) },
                                                singleLine = true,
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(8.dp),
                                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                                            )

                                            Spacer(modifier = Modifier.width(8.dp))

                                            // 右側:「≡（ドラッグハンドル）」アイコン (長押ししながら上下にドラッグして入れ替えます)
                                            Box(
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(
                                                        if (isDraggingThis) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                                    )
                                                    .pointerInput(pageIdx) {
                                                        detectDragGesturesAfterLongPress(
                                                            onDragStart = {
                                                                draggingPageIdx = pageIdx
                                                                accumulatedDragY = 0f
                                                            },
                                                            onDrag = { change, dragAmount ->
                                                                change.consume()
                                                                accumulatedDragY += dragAmount.y
                                                                val currentList = tempPageOrder.toMutableList()
                                                                val currentIndex = currentList.indexOf(pageIdx)
                                                                if (currentIndex != -1) {
                                                                    val stepThreshold = itemHeightPx * 0.65f
                                                                    if (accumulatedDragY > stepThreshold && currentIndex < currentList.lastIndex) {
                                                                        Collections.swap(currentList, currentIndex, currentIndex + 1)
                                                                        tempPageOrder = currentList
                                                                        accumulatedDragY -= itemHeightPx
                                                                    } else if (accumulatedDragY < -stepThreshold && currentIndex > 0) {
                                                                        Collections.swap(currentList, currentIndex, currentIndex - 1)
                                                                        tempPageOrder = currentList
                                                                        accumulatedDragY += itemHeightPx
                                                                    }
                                                                }
                                                            },
                                                            onDragEnd = {
                                                                draggingPageIdx = null
                                                                accumulatedDragY = 0f
                                                            },
                                                            onDragCancel = {
                                                                draggingPageIdx = null
                                                                accumulatedDragY = 0f
                                                            }
                                                        )
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.DragHandle,
                                                    contentDescription = "${p}ページの並び替え (≡長押しドラッグ)",
                                                    tint = if (isDraggingThis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // 下段: [表示on/off] [ロックon/off] [背景色] 横一列
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // 1. 表示on/off
                                            FilterChip(
                                                selected = isEnabled,
                                                onClick = {
                                                    tempEnabledPages = if (isEnabled) {
                                                        if (tempEnabledPages.size > 1) tempEnabledPages - pageIdx else tempEnabledPages
                                                    } else {
                                                        tempEnabledPages + pageIdx
                                                    }
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = if (isEnabled) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                },
                                                label = {
                                                    Text(
                                                        text = if (isEnabled) "表示" else "非表示",
                                                        fontSize = 12.sp,
                                                        fontWeight = if (isEnabled) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                                ),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.height(32.dp)
                                            )

                                            // 2. ロックon/off
                                            FilterChip(
                                                selected = isLocked,
                                                onClick = {
                                                    tempLockedPages = if (isLocked) tempLockedPages - pageIdx else tempLockedPages + pageIdx
                                                },
                                                leadingIcon = {
                                                    if (isLocked) {
                                                        Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    } else {
                                                        Icon(Icons.Rounded.LockOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    }
                                                },
                                                label = {
                                                    Text(
                                                        text = if (isLocked) "ロック中" else "ロック",
                                                        fontSize = 12.sp,
                                                        fontWeight = if (isLocked) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                                ),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.height(32.dp)
                                            )

                                            // 3. 背景色設定ボタン (高さ32dp、文字は色名またはカラーコードのみ)
                                            Surface(
                                                onClick = {
                                                    colorPickerTargetPageIndex = pageIdx
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                color = parsedColor,
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                                                shadowElevation = 1.dp,
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Rounded.Palette,
                                                        contentDescription = null,
                                                        tint = colorButtonTextColor,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Text(
                                                        text = displayName,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = colorButtonTextColor
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 1.7. Security Settings Card (PIN & Uninstall Lock)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Security,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "暗証番号・保護設定",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "設定画面、アプリ一覧、各ページにロックを適用した場合は、暗証番号の入力が必要になります。画面オフ（端末ロック）時に自動で再ロックされます。\n※バックアップ復旧時のため暗証番号をお控えください。",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val hasAnyLock = tempIsDrawerLocked || tempIsSettingsLocked || tempLockedPages.isNotEmpty() || tempIsUninstallLocked
                            val isPinMissing = hasAnyLock && tempPageLockPin.isBlank()

                            OutlinedTextField(
                                value = tempPageLockPin,
                                onValueChange = { tempPageLockPin = it.filter { ch -> ch.isDigit() } },
                                label = { Text("ロック解除用 暗証番号 (PIN)") },
                                placeholder = {
                                    Text(if (hasAnyLock) "4桁以上の数字を入力 (必須)" else "例: 1234 (ロック設定時に必須)")
                                },
                                isError = isPinMissing,
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            )

                            if (isPinMissing) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "※アプリ一覧や設定、ページのロックを有効にするには暗証番号（PIN）の設定が必要です",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "アンインストール保護:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                FilterChip(
                                    selected = tempIsUninstallLocked,
                                    onClick = { tempIsUninstallLocked = !tempIsUninstallLocked },
                                    leadingIcon = {
                                        if (tempIsUninstallLocked) {
                                            Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = if (tempIsUninstallLocked) "アンインストール非表示 (ロック中)" else "通常 (ボタン表示)",
                                            fontSize = 11.sp,
                                            fontWeight = if (tempIsUninstallLocked) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("settings_lock_chip_uninstall")
                                )
                            }
                        }
                    }

                    // 3. Export / Import Section
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "設定のエクスポート/インポート",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            // --- エクスポート ---
                            Text(
                                text = "【エクスポート】",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedTextField(
                                value = tempBackupUserName,
                                onValueChange = { tempBackupUserName = it },
                                label = { Text("ユーザー名（ファイル名用）", fontSize = 11.sp) },
                                placeholder = { Text("例: user / 児童名 / ニックネーム", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            Button(
                                onClick = {
                                    if (backupJson.isNotEmpty()) {
                                        try {
                                            viewModel.setBackupUserName(tempBackupUserName)
                                            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                                            val rawDeviceName = (Build.MODEL.ifBlank { Build.DEVICE }).trim()
                                            val sanitizedDevice = rawDeviceName.replace(Regex("[/\\\\:*?\"<>|\\s]+"), "").ifBlank { "device" }
                                            val rawUserName = tempBackupUserName.ifBlank { backupUserName }.ifBlank { "user" }.trim()
                                            val sanitizedUser = rawUserName.replace(Regex("[/\\\\:*?\"<>|\\s]+"), "").ifBlank { "user" }
                                            val fileName = "${timeStamp}_EdLauncher_${sanitizedDevice}_${sanitizedUser}.txt"
                                            val cacheFile = File(context.cacheDir, fileName)
                                            cacheFile.writeText(backupJson)

                                            val fileUri: Uri = FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                cacheFile
                                            )

                                            val sendIntent = Intent().apply {
                                                action = Intent.ACTION_SEND
                                                putExtra(Intent.EXTRA_STREAM, fileUri)
                                                type = "text/plain"
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            val shareIntent = Intent.createChooser(sendIntent, "設定ファイルの共有・エクスポート")
                                            context.startActivity(shareIntent)
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                            Toast.makeText(context, "エクスポート中にエラーが発生しました: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "エクスポート準備中、もう一度お試しください", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("ファイル共有", fontSize = 12.sp)
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(14.dp))

                            // --- インポート ---
                            Text(
                                text = "【インポート】",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Google Driveや端末内のバックアップ設定ファイルを選択して読み込みます。",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = {
                                        filePickerLauncher.launch(arrayOf("*/*", "application/json", "text/plain"))
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                ) {
                                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("読み込み（Google ドライブ参照）", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("キャンセル")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            val hasAnyLock = tempIsDrawerLocked || tempIsSettingsLocked || tempLockedPages.isNotEmpty() || tempIsUninstallLocked
                            if (hasAnyLock && tempPageLockPin.isBlank()) {
                                Toast.makeText(context, "ロックを有効にするには暗証番号（PIN）を設定してください", Toast.LENGTH_LONG).show()
                                return@Button
                            }
                            viewModel.saveSettings(
                                newEnabledPages = tempEnabledPages,
                                newPageLabels = tempPageLabels,
                                newPageColors = tempPageColors,
                                newLockedPages = tempLockedPages,
                                newPageLockPin = tempPageLockPin,
                                newIsSettingsLocked = tempIsSettingsLocked,
                                newIsDrawerEnabled = tempIsDrawerEnabled,
                                newIsDrawerLocked = tempIsDrawerLocked,
                                newIsUninstallLocked = tempIsUninstallLocked,
                                newPageOrder = tempPageOrder,
                                newBackupUserName = tempBackupUserName
                            )
                            Toast.makeText(context, "設定を保存しました", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("保存", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

    if (colorPickerTargetPageIndex != null) {
        val targetIdx = colorPickerTargetPageIndex!!
        val label = tempPageLabels[targetIdx] ?: ""
        val currentColor = tempPageColors[targetIdx] ?: "#D8E698"
        PageColorPickerDialog(
            pageIndex = targetIdx,
            pageLabel = label,
            initialColorHex = currentColor,
            onColorSelected = { newHex ->
                tempPageColors = tempPageColors.toMutableMap().apply {
                    put(targetIdx, newHex)
                }
            },
            onDismiss = {
                colorPickerTargetPageIndex = null
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppDrawer(viewModel: LauncherViewModel) {
    val context = LocalContext.current
    val searchQuery by viewModel.drawerSearchQuery.collectAsStateWithLifecycle()
    val filteredApps by viewModel.filteredDrawerApps.collectAsStateWithLifecycle()
    val appPlacements by viewModel.appPlacements.collectAsStateWithLifecycle()
    val notificationCounts by viewModel.notificationCounts.collectAsStateWithLifecycle()
    val homePageCount by viewModel.homePageCount.collectAsStateWithLifecycle()
    val currentPageIndex by viewModel.currentPageIndex.collectAsStateWithLifecycle()
    val activePageIndices by viewModel.activePageIndices.collectAsStateWithLifecycle()
    val pageLabels by viewModel.pageLabels.collectAsStateWithLifecycle()
    val homeItems by viewModel.homeItems.collectAsStateWithLifecycle()
    val pageOrder by viewModel.pageOrder.collectAsStateWithLifecycle()
    val enabledPages by viewModel.enabledPages.collectAsStateWithLifecycle()
    val orderedEnabledPages = remember(enabledPages, pageOrder) {
        val inOrder = pageOrder.filter { enabledPages.contains(it) }
        val remaining = enabledPages.filter { !inOrder.contains(it) }.sorted()
        inOrder + remaining
    }

    val isDrawerSelectionMode by viewModel.isDrawerSelectionMode.collectAsStateWithLifecycle()
    val selectedDrawerApps by viewModel.selectedDrawerApps.collectAsStateWithLifecycle()
    val isUninstallLocked by viewModel.isUninstallLocked.collectAsStateWithLifecycle()

    var showMoveDialogForDrawer by remember { mutableStateOf(false) }
    var showCreateGroupDialogForDrawer by remember { mutableStateOf(false) }

    val isDrawerOpen by viewModel.isDrawerOpen.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()

    val density = LocalDensity.current
    val pullThresholdPx = with(density) { 80.dp.toPx() }
    val pullDownOffset = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(isDrawerOpen) {
        if (!isDrawerOpen) {
            pullDownOffset.snapTo(0f)
        }
    }

    val nestedScrollConnection = remember(isDrawerOpen, pullThresholdPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!isDrawerOpen) return Offset.Zero
                // If drawer has been pulled down and user scrolls up (available.y < 0),
                // consume upward movement to return pullDownOffset towards 0
                if (pullDownOffset.value > 0f && available.y < 0f) {
                    val consumedY = available.y.coerceAtLeast(-pullDownOffset.value)
                    val newOffset = (pullDownOffset.value + consumedY).coerceAtLeast(0f)
                    coroutineScope.launch {
                        pullDownOffset.snapTo(newOffset)
                    }
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (!isDrawerOpen) return Offset.Zero
                // CRITICAL: NEVER accumulate pull-down during Fling (inertia when scrolling past top)!
                // Only accumulate when the user is actively dragging with their finger (UserInput).
                if (source == NestedScrollSource.UserInput &&
                    available.y > 0f &&
                    gridState.firstVisibleItemIndex == 0 &&
                    gridState.firstVisibleItemScrollOffset == 0
                ) {
                    val current = pullDownOffset.value
                    // Rubber-band resistance: damping gets stronger the further it is pulled
                    val progress = (current / (pullThresholdPx * 2.5f)).coerceIn(0f, 0.85f)
                    val resistance = (1f - progress) * 0.4f
                    val delta = available.y * resistance
                    val newOffset = current + delta
                    coroutineScope.launch {
                        pullDownOffset.snapTo(newOffset)
                    }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!isDrawerOpen) return Velocity.Zero
                val current = pullDownOffset.value
                if (current > 0f) {
                    if (current >= pullThresholdPx) {
                        viewModel.setDrawerOpen(false)
                        pullDownOffset.snapTo(0f)
                    } else {
                        pullDownOffset.animateTo(
                            0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                    }
                    return available
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (!isDrawerOpen) return Velocity.Zero
                val current = pullDownOffset.value
                if (current > 0f) {
                    if (current >= pullThresholdPx) {
                        viewModel.setDrawerOpen(false)
                        pullDownOffset.snapTo(0f)
                    } else {
                        pullDownOffset.animateTo(
                            0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                    }
                }
                return Velocity.Zero
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                clip = false
            }
            .background(Color(0xE6020617)) // Dark blur-like slate
            .windowInsetsPadding(WindowInsets.statusBars)
            .nestedScroll(nestedScrollConnection)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = pullDownOffset.value
                }
                .padding(top = 16.dp)
        ) {
            // Drag Down Bar Indicator & Back trigger
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.setDrawerOpen(false) }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = {},
                            onDragEnd = {
                                coroutineScope.launch {
                                    if (pullDownOffset.value >= pullThresholdPx) {
                                        viewModel.setDrawerOpen(false)
                                        pullDownOffset.snapTo(0f)
                                    } else {
                                        pullDownOffset.animateTo(
                                            0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                    }
                                }
                            },
                            onDragCancel = {
                                coroutineScope.launch {
                                    pullDownOffset.animateTo(0f)
                                }
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                val progress = (pullDownOffset.value / (pullThresholdPx * 2.5f)).coerceIn(0f, 0.8f)
                                val resistance = (1f - progress) * 0.75f
                                val newOffset = (pullDownOffset.value + dragAmount * resistance).coerceAtLeast(0f)
                                coroutineScope.launch {
                                    pullDownOffset.snapTo(newOffset)
                                }
                            }
                        )
                    }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val isThresholdReached = pullDownOffset.value >= pullThresholdPx
                val barWidth by animateDpAsState(
                    targetValue = if (isThresholdReached) 56.dp else if (pullDownOffset.value > 10f) 48.dp else 40.dp,
                    label = "bar_width"
                )
                Box(
                    modifier = Modifier
                        .width(barWidth)
                        .height(5.dp)
                        .background(
                            color = if (isThresholdReached) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(10.dp)
                        )
                )
                if (pullDownOffset.value > 15f) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isThresholdReached) "指を離してホームに戻る" else "下へ引いてホームに戻る",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isThresholdReached) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f)
                    )
                }
            }

            val filterNotOnHome by viewModel.drawerFilterNotOnHome.collectAsStateWithLifecycle()
            val sortByInstallTime by viewModel.drawerSortByInstallTime.collectAsStateWithLifecycle()
            val selectedHomePages by viewModel.drawerSelectedHomePages.collectAsStateWithLifecycle()

            val drawerConfiguration = androidx.compose.ui.platform.LocalConfiguration.current
            val drawerScreenWidthDp = drawerConfiguration.screenWidthDp
            val drawerGridColumns = remember(drawerScreenWidthDp) {
                when {
                    drawerScreenWidthDp >= 840 -> 8
                    drawerScreenWidthDp >= 600 -> 6
                    drawerScreenWidthDp >= 360 -> 5
                    else -> 4
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setDrawerSearchQuery(it) },
                placeholder = { Text("アプリを検索...", color = Color.White.copy(alpha = 0.5f)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .testTag("drawer_search_field"),
                shape = RoundedCornerShape(20.dp),
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.5f)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setDrawerSearchQuery("") }) {
                            Icon(Icons.Rounded.Clear, contentDescription = "Clear", tint = Color.White)
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color.White.copy(alpha = 0.08f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                    focusedBorderColor = Color.White.copy(alpha = 0.3f),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                ),
                singleLine = true
            )

            // Filter Chips (ホームになし, インストール順, 1, 2, ...)
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. ホームになし
                item {
                    FilterChip(
                        selected = filterNotOnHome,
                        onClick = { viewModel.setDrawerFilterNotOnHome(!filterNotOnHome) },
                        label = { Text("ホームになし", fontSize = 12.sp, fontWeight = FontWeight.Medium) },
                        leadingIcon = {
                            Checkbox(
                                checked = filterNotOnHome,
                                onCheckedChange = null,
                                modifier = Modifier.size(16.dp),
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.primary,
                                    uncheckedColor = Color.White.copy(alpha = 0.6f)
                                )
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            selectedLabelColor = Color.White,
                            containerColor = Color.White.copy(alpha = 0.08f),
                            labelColor = Color.White.copy(alpha = 0.8f)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = Color.White.copy(alpha = 0.2f),
                            selectedBorderColor = MaterialTheme.colorScheme.primary,
                            enabled = true,
                            selected = filterNotOnHome
                        ),
                        modifier = Modifier.testTag("drawer_filter_not_on_home")
                    )
                }

                // 2. インストール順
                item {
                    FilterChip(
                        selected = sortByInstallTime,
                        onClick = { viewModel.setDrawerSortByInstallTime(!sortByInstallTime) },
                        label = { Text("インストール順", fontSize = 12.sp, fontWeight = FontWeight.Medium) },
                        leadingIcon = {
                            Checkbox(
                                checked = sortByInstallTime,
                                onCheckedChange = null,
                                modifier = Modifier.size(16.dp),
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.primary,
                                    uncheckedColor = Color.White.copy(alpha = 0.6f)
                                )
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            selectedLabelColor = Color.White,
                            containerColor = Color.White.copy(alpha = 0.08f),
                            labelColor = Color.White.copy(alpha = 0.8f)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = Color.White.copy(alpha = 0.2f),
                            selectedBorderColor = MaterialTheme.colorScheme.primary,
                            enabled = true,
                            selected = sortByInstallTime
                        ),
                        modifier = Modifier.testTag("drawer_filter_install_order")
                    )
                }

                // 3. 各表示中ホーム画面 (1, 2, ...)
                items(orderedEnabledPages) { pageIndex ->
                    val pageNumber = orderedEnabledPages.indexOf(pageIndex) + 1
                    val isPageSelected = selectedHomePages.contains(pageNumber)
                    FilterChip(
                        selected = isPageSelected,
                        onClick = { viewModel.toggleDrawerSelectedHomePage(pageNumber) },
                        label = { Text(pageNumber.toString(), fontSize = 12.sp, fontWeight = FontWeight.Medium) },
                        leadingIcon = {
                            Checkbox(
                                checked = isPageSelected,
                                onCheckedChange = null,
                                modifier = Modifier.size(16.dp),
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.primary,
                                    uncheckedColor = Color.White.copy(alpha = 0.6f)
                                )
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            selectedLabelColor = Color.White,
                            containerColor = Color.White.copy(alpha = 0.08f),
                            labelColor = Color.White.copy(alpha = 0.8f)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = Color.White.copy(alpha = 0.2f),
                            selectedBorderColor = MaterialTheme.colorScheme.primary,
                            enabled = true,
                            selected = isPageSelected
                        ),
                        modifier = Modifier.testTag("drawer_filter_page_$pageNumber")
                    )
                }
            }

            val displayedFilteredApps = remember(
                filteredApps,
                appPlacements,
                filterNotOnHome,
                sortByInstallTime,
                selectedHomePages
            ) {
                val filtered = when {
                    filterNotOnHome -> {
                        filteredApps.filter { app -> appPlacements[app.packageName].isNullOrEmpty() }
                    }
                    selectedHomePages.isNotEmpty() -> {
                        filteredApps.filter { app ->
                            val placements = appPlacements[app.packageName] ?: emptyList()
                            selectedHomePages.any { pageNum -> placements.contains(pageNum) }
                        }
                    }
                    else -> filteredApps
                }

                if (sortByInstallTime) {
                    filtered.sortedByDescending { it.firstInstallTime }
                } else {
                    filtered
                }
            }

            // フィルターや並び順、検索語句の変更時にスクロール位置を最上部に戻す
            LaunchedEffect(filterNotOnHome, sortByInstallTime, selectedHomePages, searchQuery) {
                gridState.scrollToItem(0)
            }

            // Drawer Grid
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(drawerGridColumns),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .testTag("drawer_app_grid"),
                contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // All Filtered Apps
                items(displayedFilteredApps, key = { "all_${it.packageName}_${it.className}" }) { app ->
                    DrawerAppItem(
                        app = app,
                        viewModel = viewModel,
                        context = context,
                        appPlacements = appPlacements,
                        notificationCounts = notificationCounts,
                        isDrawerSelectionMode = isDrawerSelectionMode,
                        selectedDrawerApps = selectedDrawerApps,
                        activePageIndices = activePageIndices,
                        pageLabels = pageLabels
                    )
                }
            }
        }

        // Selection Floating Action Bar in AppDrawer (Unified with Home Screen)
        AnimatedVisibility(
            visible = isDrawerSelectionMode,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp, start = 16.dp, end = 16.dp)
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)
                ),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("drawer_selection_control_bar")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "選択中: ${selectedDrawerApps.size}件",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Select All / Deselect
                        IconButton(
                            onClick = {
                                if (selectedDrawerApps.size == filteredApps.size) {
                                    viewModel.clearDrawerAppSelection()
                                } else {
                                    viewModel.selectAllDrawerApps(filteredApps)
                                }
                            },
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Icon(
                                imageVector = if (selectedDrawerApps.size == filteredApps.size) Icons.Rounded.Deselect else Icons.Rounded.SelectAll,
                                contentDescription = "全選択/解除"
                            )
                        }

                        // Group Action
                        if (selectedDrawerApps.size >= 2) {
                            IconButton(
                                onClick = { showCreateGroupDialogForDrawer = true },
                                colors = IconButtonDefaults.iconButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Icon(
                                    Icons.Rounded.Folder,
                                    contentDescription = "グループ作成"
                                )
                            }
                        }

                        // Move / Add to Destination Dialog Action
                        IconButton(
                            onClick = { showMoveDialogForDrawer = true },
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Icon(Icons.Rounded.SwapHoriz, contentDescription = "追加 / 移動")
                        }

                        // Uninstall Selected Action (Hidden when uninstall is locked)
                        if (!isUninstallLocked) {
                            IconButton(
                                onClick = {
                                    val appsToUninstall = selectedDrawerApps.toList()
                                    viewModel.exitDrawerSelectionMode()
                                    for (appToUninstall in appsToUninstall) {
                                        uninstallApp(context, appToUninstall.packageName)
                                    }
                                },
                                enabled = selectedDrawerApps.isNotEmpty(),
                                colors = IconButtonDefaults.iconButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier.testTag("drawer_uninstall_button")
                            ) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = "アンインストール"
                                )
                            }
                        }

                        // Cancel
                        IconButton(
                            onClick = { viewModel.exitDrawerSelectionMode() },
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                            )
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "キャンセル")
                        }
                    }
                }
            }
        }

        // Dialogs for Drawer Selection Actions
        if (showMoveDialogForDrawer) {
            val existingFolders = homeItems.filter { it.itemType == "GROUP" }
            MoveDestinationDialog(
                title = "追加先を選択",
                existingFolders = existingFolders,
                activePageIndices = activePageIndices,
                pageLabels = pageLabels,
                onDismiss = { showMoveDialogForDrawer = false },
                onPageSelected = { pageIdx ->
                    viewModel.addDrawerAppsToPage(selectedDrawerApps.toList(), pageIdx)
                    showMoveDialogForDrawer = false
                    val pageName = if (pageIdx == -1) "ドック" else {
                        val screenNum = activePageIndices.indexOf(pageIdx).let { if (it != -1) it + 1 else pageIdx + 1 }
                        val lbl = pageLabels[pageIdx]?.trim()
                        if (!lbl.isNullOrEmpty()) "$lbl (ページ $screenNum)" else "ページ $screenNum"
                    }
                    Toast.makeText(context, "${selectedDrawerApps.size}個のアプリを $pageName に追加しました", Toast.LENGTH_SHORT).show()
                },
                onFolderSelected = { targetFolder ->
                    viewModel.addDrawerAppsToGroup(selectedDrawerApps.toList(), targetFolder)
                    showMoveDialogForDrawer = false
                    Toast.makeText(context, "${selectedDrawerApps.size}個のアプリを「${targetFolder.label}」に追加しました", Toast.LENGTH_SHORT).show()
                }
            )
        }

        if (showCreateGroupDialogForDrawer) {
            var folderName by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showCreateGroupDialogForDrawer = false },
                title = { Text("新しいグループを作成", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("選択した ${selectedDrawerApps.size} 個のアプリでグループを作成し、ホーム（ページ）に追加します。", fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = folderName,
                            onValueChange = { folderName = it },
                            label = { Text("グループ名") },
                            placeholder = { Text("例: 便利ツール") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val name = folderName.ifBlank { "グループ" }
                            val targetPage = activePageIndices.getOrNull(currentPageIndex) ?: (activePageIndices.firstOrNull() ?: 0)
                            viewModel.createGroupWithDrawerApps(selectedDrawerApps.toList(), name, pageIndex = targetPage)
                            showCreateGroupDialogForDrawer = false
                            Toast.makeText(context, "グループ「$name」を作成しました", Toast.LENGTH_SHORT).show()
                        },
                        enabled = folderName.isNotBlank() || selectedDrawerApps.isNotEmpty()
                    ) {
                        Text("作成")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateGroupDialogForDrawer = false }) {
                        Text("キャンセル")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DrawerAppItem(
    app: AppInfo,
    viewModel: LauncherViewModel,
    context: Context,
    appPlacements: Map<String, List<Int>>,
    notificationCounts: Map<String, Int> = emptyMap(),
    isDrawerSelectionMode: Boolean,
    selectedDrawerApps: Set<AppInfo>,
    activePageIndices: List<Int>,
    pageLabels: Map<Int, String> = emptyMap()
) {
    val isSelected = selectedDrawerApps.contains(app)
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "drawer_item_scale"
    )

    Box(
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            clip = false
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(
                    onClick = {
                        if (isDrawerSelectionMode) {
                            viewModel.toggleDrawerAppSelection(app)
                        } else {
                            launchApp(context, app.packageName, app.className)
                            viewModel.setDrawerOpen(false)
                        }
                    },
                    onLongClick = {
                        if (isDrawerSelectionMode) {
                            viewModel.toggleDrawerAppSelection(app)
                        } else {
                            viewModel.enterDrawerSelectionMode(app)
                        }
                    }
                )
                .padding(vertical = 12.dp, horizontal = 4.dp)
                .testTag("drawer_item_${app.packageName}"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val placements = appPlacements[app.packageName]
            val notifCount = notificationCounts[app.packageName] ?: 0

            Box(contentAlignment = Alignment.Center) {
                AppIcon(packageName = app.packageName, className = app.className, size = 56.dp)

                // Notification count badge at TOP of icon
                if (notifCount > 0) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = (-4).dp, y = (-4).dp)
                            .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp),
                        shape = CircleShape,
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFEF4444)),
                        shadowElevation = 2.dp
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = if (notifCount > 99) "99+" else notifCount.toString(),
                                color = Color.Red,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 12.sp,
                                style = androidx.compose.ui.text.TextStyle(
                                    platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                        includeFontPadding = false
                                    )
                                )
                            )
                        }
                    }
                }

                // Page placement numbers badge at BOTTOM of icon
                if (!placements.isNullOrEmpty()) {
                    val badgeText = placements.joinToString(",")
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = 4.dp)
                            .defaultMinSize(minWidth = 14.dp, minHeight = 13.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary,
                        border = BorderStroke(1.dp, Color.White),
                        shadowElevation = 3.dp
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = badgeText,
                                color = Color.White,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                lineHeight = 10.sp,
                                style = androidx.compose.ui.text.TextStyle(
                                    platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                        includeFontPadding = false
                                    )
                                )
                            )
                        }
                    }
                }

                // Selection indicator badge matching Home Screen
                if (isDrawerSelectionMode) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                color = if (isSelected) Color(0x663B82F6) else Color(0x22000000),
                                shape = RoundedCornerShape(14.dp)
                            )
                    )

                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-4).dp)
                            .background(
                                color = if (isSelected) Color(0xFF3B82F6) else Color.White.copy(alpha = 0.3f),
                                shape = CircleShape
                            )
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Selected",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = app.label,
                color = Color.White,
                fontSize = 10.5.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
