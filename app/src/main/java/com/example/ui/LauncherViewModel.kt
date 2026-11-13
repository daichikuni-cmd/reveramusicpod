package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppInfo
import com.example.data.GroupAppEntity
import com.example.data.HomeItemEntity
import com.example.data.LauncherDatabase
import com.example.data.LauncherRepository
import com.example.data.getInstalledApps
import com.example.data.normalizeForSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val database = LauncherDatabase.getDatabase(application)
    private val repository = LauncherRepository(database.launcherDao())

    // All installed apps
    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    // Search query for the drawer
    private val _drawerSearchQuery = MutableStateFlow("")
    val drawerSearchQuery: StateFlow<String> = _drawerSearchQuery.asStateFlow()

    // Filter states for the drawer
    private val _drawerFilterNotOnHome = MutableStateFlow(false)
    val drawerFilterNotOnHome: StateFlow<Boolean> = _drawerFilterNotOnHome.asStateFlow()

    private val _drawerSortByInstallTime = MutableStateFlow(false)
    val drawerSortByInstallTime: StateFlow<Boolean> = _drawerSortByInstallTime.asStateFlow()

    private val _drawerSelectedHomePages = MutableStateFlow<Set<Int>>(emptySet())
    val drawerSelectedHomePages: StateFlow<Set<Int>> = _drawerSelectedHomePages.asStateFlow()

    private var lastDrawerActivityTimeMs: Long = 0L
    private var lastDrawerClosedTimeMs: Long = 0L

    companion object {
        // 短時間の行き来（5分以内）であればアプリ一覧の検索テキストやチェックボックスを保持する
        const val DRAWER_RETENTION_TIMEOUT_MS = 5 * 60 * 1000L
    }

    // Recently installed apps (top 8 = 2 rows in 4-column grid)
    val recentlyInstalledApps: StateFlow<List<AppInfo>> = _installedApps.map { apps ->
        apps.sortedByDescending { it.firstInstallTime }.take(8)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    // Database state flows
    val homeItems: StateFlow<List<HomeItemEntity>> = repository.allHomeItems
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    val groupApps: StateFlow<List<GroupAppEntity>> = repository.allGroupApps
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    private val prefs = application.getSharedPreferences("edlauncher_prefs", Context.MODE_PRIVATE)

    private val _enabledPages = MutableStateFlow<Set<Int>>(loadEnabledPagesFromPrefs())
    val enabledPages: StateFlow<Set<Int>> = _enabledPages.asStateFlow()

    // Locked Pages Configuration
    private val _lockedPages = MutableStateFlow<Set<Int>>(loadLockedPagesFromPrefs())
    val lockedPages: StateFlow<Set<Int>> = _lockedPages.asStateFlow()

    private val _isSettingsLocked = MutableStateFlow<Boolean>(prefs.getBoolean("is_settings_locked", false))
    val isSettingsLocked: StateFlow<Boolean> = _isSettingsLocked.asStateFlow()

    private val _isDrawerEnabled = MutableStateFlow<Boolean>(prefs.getBoolean("is_drawer_enabled", true))
    val isDrawerEnabled: StateFlow<Boolean> = _isDrawerEnabled.asStateFlow()

    private val _isDrawerLocked = MutableStateFlow<Boolean>(prefs.getBoolean("is_drawer_locked", false))
    val isDrawerLocked: StateFlow<Boolean> = _isDrawerLocked.asStateFlow()

    private val _isUninstallLocked = MutableStateFlow<Boolean>(prefs.getBoolean("is_uninstall_locked", false))
    val isUninstallLocked: StateFlow<Boolean> = _isUninstallLocked.asStateFlow()

    private val _pageOrder = MutableStateFlow<List<Int>>(loadPageOrderFromPrefs())
    val pageOrder: StateFlow<List<Int>> = _pageOrder.asStateFlow()

    private fun loadPageOrderFromPrefs(): List<Int> {
        val jsonStr = prefs.getString("page_order_list", null)
        val defaultList = (0..8).toList()
        if (jsonStr.isNullOrBlank()) return defaultList
        return try {
            val array = org.json.JSONArray(jsonStr)
            val list = mutableListOf<Int>()
            for (i in 0 until array.length()) {
                val p = array.getInt(i)
                if (p in 0..8 && !list.contains(p)) {
                    list.add(p)
                }
            }
            defaultList.forEach { p ->
                if (!list.contains(p)) list.add(p)
            }
            list
        } catch (e: Exception) {
            defaultList
        }
    }

    private fun savePageOrderToPrefs(order: List<Int>) {
        val array = org.json.JSONArray()
        order.forEach { array.put(it) }
        prefs.edit().putString("page_order_list", array.toString()).apply()
    }

    private val _pageLockPin = MutableStateFlow<String>(prefs.getString("page_lock_pin", "") ?: "")
    val pageLockPin: StateFlow<String> = _pageLockPin.asStateFlow()

    // Backup user name (e.g. for backup filename and device identification)
    private val _backupUserName = MutableStateFlow<String>(loadBackupUserNameFromPrefs())
    val backupUserName: StateFlow<String> = _backupUserName.asStateFlow()

    private fun loadBackupUserNameFromPrefs(): String {
        val saved = prefs.getString("backup_user_name", null)
        if (!saved.isNullOrBlank()) return saved

        val app = getApplication<Application>()
        try {
            val userManager = app.getSystemService(Context.USER_SERVICE) as? android.os.UserManager
            val uName = userManager?.userName
            if (!uName.isNullOrBlank()) return uName
        } catch (e: Exception) {}

        try {
            val devName = android.provider.Settings.Global.getString(app.contentResolver, android.provider.Settings.Global.DEVICE_NAME)
            if (!devName.isNullOrBlank()) return devName
        } catch (e: Exception) {}

        try {
            val btName = android.provider.Settings.Secure.getString(app.contentResolver, "bluetooth_name")
            if (!btName.isNullOrBlank()) return btName
        } catch (e: Exception) {}

        return "user"
    }

    fun setBackupUserName(name: String) {
        val clean = name.trim()
        _backupUserName.value = clean
        prefs.edit().putString("backup_user_name", clean).apply()
    }

    // Session unlocked pages and settings (cleared on screen lock)
    private val _unlockedPages = MutableStateFlow<Set<Int>>(emptySet())
    val unlockedPages: StateFlow<Set<Int>> = _unlockedPages.asStateFlow()

    private val _isSettingsUnlocked = MutableStateFlow<Boolean>(false)
    val isSettingsUnlocked: StateFlow<Boolean> = _isSettingsUnlocked.asStateFlow()

    private val _isDrawerUnlocked = MutableStateFlow<Boolean>(false)
    val isDrawerUnlocked: StateFlow<Boolean> = _isDrawerUnlocked.asStateFlow()

    fun unlockPage(pageIndex: Int) {
        _unlockedPages.value = _unlockedPages.value + pageIndex
    }

    fun unlockSettings() {
        _isSettingsUnlocked.value = true
    }

    fun lockSettings() {
        _isSettingsUnlocked.value = false
    }

    fun unlockDrawer() {
        _isDrawerUnlocked.value = true
    }

    fun lockDrawer() {
        _isDrawerUnlocked.value = false
    }

    fun lockAllPages() {
        _unlockedPages.value = emptySet()
        _isSettingsUnlocked.value = false
        _isDrawerUnlocked.value = false
    }

    fun setSettingsLocked(locked: Boolean) {
        _isSettingsLocked.value = locked
        prefs.edit().putBoolean("is_settings_locked", locked).apply()
    }

    fun setDrawerEnabled(enabled: Boolean) {
        _isDrawerEnabled.value = enabled
        prefs.edit().putBoolean("is_drawer_enabled", enabled).apply()
    }

    fun setDrawerLocked(locked: Boolean) {
        _isDrawerLocked.value = locked
        prefs.edit().putBoolean("is_drawer_locked", locked).apply()
    }

    fun setUninstallLocked(locked: Boolean) {
        _isUninstallLocked.value = locked
        prefs.edit().putBoolean("is_uninstall_locked", locked).apply()
    }

    private fun loadLockedPagesFromPrefs(): Set<Int> {
        val jsonStr = prefs.getString("locked_pages_set", null) ?: return emptySet()
        return try {
            val array = org.json.JSONArray(jsonStr)
            val rawSet = mutableSetOf<Int>()
            for (i in 0 until array.length()) {
                rawSet.add(array.getInt(i))
            }
            rawSet.filter { it in 0..8 }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    private fun saveLockedPagesToPrefs(locked: Set<Int>) {
        val array = org.json.JSONArray()
        locked.sorted().forEach { array.put(it) }
        prefs.edit().putString("locked_pages_set", array.toString()).apply()
    }

    fun setPageLockPin(pin: String) {
        val clean = pin.trim().take(8)
        _pageLockPin.value = clean
        prefs.edit().putString("page_lock_pin", clean).apply()
    }

    // activePageIndices: Visible/Swipeable pages (enabled pages that are not locked, OR are currently unlocked)
    val activePageIndices: StateFlow<List<Int>> = combine(
        _enabledPages,
        _lockedPages,
        _unlockedPages,
        _pageOrder
    ) { enabled, locked, unlocked, order ->
        val visible = order.filter { pIdx ->
            enabled.contains(pIdx) && (!locked.contains(pIdx) || unlocked.contains(pIdx))
        }
        if (visible.isEmpty()) {
            // Fallback: if all enabled pages are locked, keep at least the first enabled or 0
            listOf(enabled.minOrNull() ?: 0)
        } else {
            visible
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = listOf(0, 1, 2)
    )

    val homePageCount: StateFlow<Int> = activePageIndices.map { it.size }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = 3
    )

    private val _resetTrigger = MutableStateFlow(0)
    val resetTrigger: StateFlow<Int> = _resetTrigger.asStateFlow()

    private val _dismissDialogsTrigger = MutableStateFlow(0)
    val dismissDialogsTrigger: StateFlow<Int> = _dismissDialogsTrigger.asStateFlow()

    // Filtered drawer apps (Apps placed ONLY on currently locked pages are hidden from drawer)
    @Suppress("UNCHECKED_CAST")
    val filteredDrawerApps: StateFlow<List<AppInfo>> = combine(
        listOf(
            _installedApps,
            _drawerSearchQuery,
            homeItems,
            groupApps,
            _enabledPages,
            _lockedPages,
            _unlockedPages
        )
    ) { flowValues ->
        val apps = flowValues[0] as List<AppInfo>
        val query = flowValues[1] as String
        val items = flowValues[2] as List<HomeItemEntity>
        val groups = flowValues[3] as List<GroupAppEntity>
        val enabled = flowValues[4] as Set<Int>
        val locked = flowValues[5] as Set<Int>
        val unlocked = flowValues[6] as Set<Int>

        // Effective locked pages: All locked pages that are not currently unlocked
        // (even if the locked page itself is hidden from the home screen in settings)
        val currentlyLockedPages = locked - unlocked

        val candidateApps = if (currentlyLockedPages.isEmpty()) {
            apps
        } else {
            val groupMap = items.associateBy { it.id }
            val lockedPackages = mutableSetOf<String>()
            val accessiblePackages = mutableSetOf<String>()

            items.forEach { item ->
                if (item.itemType == "APP" && !item.packageName.isNullOrBlank()) {
                    if (item.pageIndex in currentlyLockedPages) {
                        lockedPackages.add(item.packageName)
                    } else if (item.pageIndex == -1 || item.pageIndex in enabled) {
                        accessiblePackages.add(item.packageName)
                    }
                }
            }

            groups.forEach { groupApp ->
                val parentFolder = groupMap[groupApp.homeItemId]
                if (parentFolder != null && !groupApp.packageName.isNullOrBlank()) {
                    if (parentFolder.pageIndex in currentlyLockedPages) {
                        lockedPackages.add(groupApp.packageName)
                    } else if (parentFolder.pageIndex == -1 || parentFolder.pageIndex in enabled) {
                        accessiblePackages.add(groupApp.packageName)
                    }
                }
            }

            val lockedOnlyPackages = lockedPackages - accessiblePackages
            if (lockedOnlyPackages.isEmpty()) {
                apps
            } else {
                apps.filterNot { lockedOnlyPackages.contains(it.packageName) }
            }
        }

        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) {
            candidateApps
        } else {
            val normalizedQuery = normalizeForSearch(trimmedQuery)
            candidateApps.filter { app ->
                val searchCandidates = buildList {
                    add(app.label)
                    addAll(app.alternateLabels)
                    add(app.packageName)
                    val lastPkgSegment = app.packageName.substringAfterLast('.')
                    if (lastPkgSegment.isNotBlank()) {
                        add(lastPkgSegment)
                    }
                }
                searchCandidates.any { candidate ->
                    normalizeForSearch(candidate).contains(normalizedQuery)
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    fun togglePageEnabled(pageIndex: Int) {
        val current = _enabledPages.value.toMutableSet()
        if (current.contains(pageIndex)) {
            if (current.size > 1) {
                current.remove(pageIndex)
            }
        } else {
            current.add(pageIndex)
        }
        _enabledPages.value = current
        saveEnabledPagesToPrefs(current)
    }

    fun setEnabledPages(pages: Set<Int>) {
        val safe = pages.filter { it in 0..8 }.toSet()
        val finalSet = if (safe.isEmpty()) setOf(0) else safe
        _enabledPages.value = finalSet
        saveEnabledPagesToPrefs(finalSet)
    }

    private fun loadEnabledPagesFromPrefs(): Set<Int> {
        val jsonStr = prefs.getString("enabled_pages_set", null)
        if (jsonStr.isNullOrBlank()) {
            val legacyCount = prefs.getInt("home_page_count", 3).coerceIn(1, 9)
            return (0 until legacyCount).toSet()
        }
        return try {
            val array = org.json.JSONArray(jsonStr)
            val rawSet = mutableSetOf<Int>()
            for (i in 0 until array.length()) {
                rawSet.add(array.getInt(i))
            }
            if (rawSet.isEmpty()) return setOf(0, 1, 2)

            // Convert 1-indexed saved values (e.g., 1, 2, 3, 4, 9 without 0) to 0-indexed (0, 1, 2, 3, 8)
            val isOneIndexed = !rawSet.contains(0) && rawSet.any { it in 1..9 }
            val normalized = if (isOneIndexed) {
                rawSet.map { if (it in 1..9) it - 1 else it }.toSet()
            } else {
                rawSet
            }
            val safe = normalized.filter { it in 0..8 }.toSet()
            if (safe.isEmpty()) setOf(0, 1, 2) else safe
        } catch (e: Exception) {
            setOf(0, 1, 2)
        }
    }

    private fun saveEnabledPagesToPrefs(pages: Set<Int>) {
        val array = org.json.JSONArray()
        pages.sorted().forEach { array.put(it) }
        prefs.edit().putString("enabled_pages_set", array.toString()).apply()
    }

    private val _defaultPinnedPageIndex = MutableStateFlow<Int>(loadDefaultPinnedPageFromPrefs())
    val defaultPinnedPageIndex: StateFlow<Int> = _defaultPinnedPageIndex.asStateFlow()

    fun setDefaultPinnedPage(pageIndex: Int) {
        val maxPage = (_enabledPages.value.maxOrNull() ?: 9) - 1
        val clamped = pageIndex.coerceIn(0, maxPage.coerceAtLeast(0))
        _defaultPinnedPageIndex.value = clamped
        prefs.edit().putInt("default_pinned_page_index", clamped).apply()
    }

    private fun loadDefaultPinnedPageFromPrefs(): Int {
        return prefs.getInt("default_pinned_page_index", 0)
    }

    private val _pageLabels = MutableStateFlow<Map<Int, String>>(loadPageLabelsFromPrefs())
    val pageLabels: StateFlow<Map<Int, String>> = _pageLabels.asStateFlow()

    fun setPageLabel(pageIndex: Int, label: String) {
        val trimmed = label.take(10)
        val updated = _pageLabels.value.toMutableMap()
        if (trimmed.isBlank()) {
            updated.remove(pageIndex)
        } else {
            updated[pageIndex] = trimmed
        }
        _pageLabels.value = updated
        savePageLabelsToPrefs(updated)
    }

    private fun loadPageLabelsFromPrefs(): Map<Int, String> {
        val jsonStr = prefs.getString("page_labels", "{}") ?: "{}"
        return try {
            val map = mutableMapOf<Int, String>()
            val json = org.json.JSONObject(jsonStr)
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key.toInt()] = json.getString(key)
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun savePageLabelsToPrefs(map: Map<Int, String>) {
        val json = org.json.JSONObject()
        map.forEach { (key, value) -> json.put(key.toString(), value) }
        prefs.edit().putString("page_labels", json.toString()).apply()
    }

    fun saveSettings(
        newEnabledPages: Set<Int>,
        newPageLabels: Map<Int, String>,
        newPageColors: Map<Int, String>,
        newLockedPages: Set<Int> = _lockedPages.value,
        newPageLockPin: String = _pageLockPin.value,
        newIsSettingsLocked: Boolean = _isSettingsLocked.value,
        newIsDrawerEnabled: Boolean = _isDrawerEnabled.value,
        newIsDrawerLocked: Boolean = _isDrawerLocked.value,
        newIsUninstallLocked: Boolean = _isUninstallLocked.value,
        newPageOrder: List<Int> = _pageOrder.value,
        newBackupUserName: String = _backupUserName.value
    ) {
        setEnabledPages(newEnabledPages)

        _pageOrder.value = newPageOrder
        savePageOrderToPrefs(newPageOrder)

        _lockedPages.value = newLockedPages
        saveLockedPagesToPrefs(newLockedPages)

        _isSettingsLocked.value = newIsSettingsLocked
        prefs.edit().putBoolean("is_settings_locked", newIsSettingsLocked).apply()

        _isDrawerEnabled.value = newIsDrawerEnabled
        prefs.edit().putBoolean("is_drawer_enabled", newIsDrawerEnabled).apply()

        _isDrawerLocked.value = newIsDrawerLocked
        prefs.edit().putBoolean("is_drawer_locked", newIsDrawerLocked).apply()

        _isUninstallLocked.value = newIsUninstallLocked
        prefs.edit().putBoolean("is_uninstall_locked", newIsUninstallLocked).apply()

        setPageLockPin(newPageLockPin)

        setBackupUserName(newBackupUserName)

        _pageLabels.value = newPageLabels
        savePageLabelsToPrefs(newPageLabels)

        _pageBackgroundColors.value = newPageColors
        savePageColorsToPrefs(newPageColors)

        if (!activePageIndices.value.contains(_defaultPinnedPageIndex.value)) {
            val fallback = activePageIndices.value.firstOrNull() ?: 0
            setDefaultPinnedPage(fallback)
        }
    }

    private val _pageBackgroundColors = MutableStateFlow<Map<Int, String>>(loadPageColorsFromPrefs())
    val pageBackgroundColors: StateFlow<Map<Int, String>> = _pageBackgroundColors.asStateFlow()

    private val _manualNotificationCounts = MutableStateFlow<Map<String, Int>>(emptyMap())

    val notificationCounts: StateFlow<Map<String, Int>> = combine(
        com.example.service.LauncherNotificationListenerService.activeNotificationCounts,
        _manualNotificationCounts
    ) { autoCounts, manualCounts ->
        val result = autoCounts.toMutableMap()
        manualCounts.forEach { (pkg, count) ->
            if (count <= 0) {
                result.remove(pkg)
            } else {
                result[pkg] = count
            }
        }
        result
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun updateNotificationCount(packageName: String, count: Int) {
        val updated = _manualNotificationCounts.value.toMutableMap()
        if (count <= 0) {
            updated.remove(packageName)
        } else {
            updated[packageName] = count
        }
        _manualNotificationCounts.value = updated
    }

    fun clearAllNotificationCounts() {
        _manualNotificationCounts.value = emptyMap()
    }

    fun setPageBackgroundColor(pageIndex: Int, colorHex: String) {
        val updated = _pageBackgroundColors.value.toMutableMap()
        if (colorHex == "default" || colorHex.isBlank()) {
            updated.remove(pageIndex)
        } else {
            updated[pageIndex] = colorHex
        }
        _pageBackgroundColors.value = updated
        savePageColorsToPrefs(updated)
    }

    private fun loadPageColorsFromPrefs(): Map<Int, String> {
        val jsonStr = prefs.getString("page_background_colors", "{}") ?: "{}"
        return try {
            val map = mutableMapOf<Int, String>()
            val json = org.json.JSONObject(jsonStr)
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key.toInt()] = json.getString(key)
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun savePageColorsToPrefs(map: Map<Int, String>) {
        val json = org.json.JSONObject()
        map.forEach { (key, value) -> json.put(key.toString(), value) }
        prefs.edit().putString("page_background_colors", json.toString()).apply()
    }

    // Map of packageName to sorted list of screen placement numbers (0 for Dock, 1 for Screen 1, etc.)
    val appPlacements: StateFlow<Map<String, List<Int>>> = combine(
        homeItems,
        groupApps,
        _enabledPages,
        _pageOrder
    ) { items, groups, enabled, order ->
        val groupParentMap = items.associateBy { it.id }
        val map = mutableMapOf<String, MutableSet<Int>>()

        // Build mapping from physical pageIndex to display screen number (1-based)
        val inOrder = order.filter { enabled.contains(it) }
        val remaining = enabled.filter { !inOrder.contains(it) }.sorted()
        val orderedPages = inOrder + remaining
        val pageIndexToScreenNum = orderedPages.mapIndexed { index, pIdx -> pIdx to (index + 1) }.toMap()

        fun getScreenNumber(pIdx: Int): Int {
            return if (pIdx == -1) 0 else (pageIndexToScreenNum[pIdx] ?: (pIdx + 1))
        }

        for (item in items) {
            if (item.itemType == "APP" && !item.packageName.isNullOrEmpty()) {
                val screenNum = getScreenNumber(item.pageIndex)
                map.getOrPut(item.packageName) { mutableSetOf() }.add(screenNum)
            }
        }

        for (groupApp in groups) {
            val parent = groupParentMap[groupApp.homeItemId]
            if (parent != null && groupApp.packageName.isNotEmpty()) {
                val screenNum = getScreenNumber(parent.pageIndex)
                map.getOrPut(groupApp.packageName) { mutableSetOf() }.add(screenNum)
            }
        }

        map.mapValues { entry -> entry.value.sorted() }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyMap()
    )

    // Navigation and UX states
    private val _currentPageIndex = MutableStateFlow(0)
    val currentPageIndex: StateFlow<Int> = _currentPageIndex.asStateFlow()

    private val _isDrawerOpen = MutableStateFlow(false)
    val isDrawerOpen: StateFlow<Boolean> = _isDrawerOpen.asStateFlow()

    // Active open folder entity (null means no folder is currently open)
    private val _activeFolder = MutableStateFlow<HomeItemEntity?>(null)
    val activeFolder: StateFlow<HomeItemEntity?> = _activeFolder.asStateFlow()

    // Selection/Edit Mode States
    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private val _selectedItems = MutableStateFlow<Set<HomeItemEntity>>(emptySet())
    val selectedItems: StateFlow<Set<HomeItemEntity>> = _selectedItems.asStateFlow()

    // Drawer Selection Mode States
    private val _isDrawerSelectionMode = MutableStateFlow(false)
    val isDrawerSelectionMode: StateFlow<Boolean> = _isDrawerSelectionMode.asStateFlow()

    private val _selectedDrawerApps = MutableStateFlow<Set<AppInfo>>(emptySet())
    val selectedDrawerApps: StateFlow<Set<AppInfo>> = _selectedDrawerApps.asStateFlow()

    init {
        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val apps = getInstalledApps(getApplication())
            _installedApps.value = apps

            val installedPackages = apps.map { it.packageName }.toSet()
            if (installedPackages.isNotEmpty()) {
                repository.cleanupUninstalledApps(installedPackages)
            }

            // If the Home database is empty, seed some initial default apps for a gorgeous FTUX
            val currentHome = repository.allHomeItems.first()
            if (currentHome.isEmpty() && apps.isNotEmpty()) {
                seedInitialApps(apps)
            }
        }
    }

    private suspend fun seedInitialApps(apps: List<AppInfo>) {
        // Try to place standard common apps if installed, otherwise take from general apps list
        val targetKeywords = listOf("phone", "dialer", "chrome", "settings", "contacts", "camera", "maps", "gallery", "mail", "browser")
        val seededApps = mutableListOf<AppInfo>()

        for (kw in targetKeywords) {
            val matched = apps.find { it.packageName.contains(kw, ignoreCase = true) && !seededApps.contains(it) }
            if (matched != null) {
                seededApps.add(matched)
            }
        }

        // Ensure we have a good list of apps, filling with other installed apps if necessary
        val pool = seededApps.toMutableList()
        for (app in apps) {
            if (!pool.contains(app)) {
                pool.add(app)
            }
        }

        // Put up to 5 apps in the Dock (pageIndex = -1)
        val dockSeeded = pool.take(5)
        dockSeeded.forEach { app ->
            repository.addAppToHome(app.packageName, app.className, app.label, pageIndex = -1)
        }

        // Put next up to 4 apps on Page 0 (pageIndex = 0)
        val page0Seeded = pool.drop(5).take(4)
        page0Seeded.forEach { app ->
            repository.addAppToHome(app.packageName, app.className, app.label, pageIndex = 0)
        }
    }

    fun addAppToDock(app: AppInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentDockItems = repository.allHomeItems.first().filter { it.pageIndex == -1 }
            if (currentDockItems.size >= 5) {
                viewModelScope.launch(Dispatchers.Main) {
                    android.widget.Toast.makeText(getApplication(), "ドックには最大5つのアプリしか配置できません", android.widget.Toast.LENGTH_SHORT).show()
                }
            } else {
                repository.addAppToHome(
                    packageName = app.packageName,
                    className = app.className,
                    label = app.label,
                    pageIndex = -1
                )
                viewModelScope.launch(Dispatchers.Main) {
                    android.widget.Toast.makeText(getApplication(), "ドックに追加しました", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun reloadApps() {
        loadInstalledApps()
    }

    fun setDrawerSearchQuery(query: String) {
        _drawerSearchQuery.value = query
        lastDrawerActivityTimeMs = System.currentTimeMillis()
    }

    fun setDrawerFilterNotOnHome(enabled: Boolean) {
        _drawerFilterNotOnHome.value = enabled
        if (enabled) {
            _drawerSelectedHomePages.value = emptySet()
        }
        lastDrawerActivityTimeMs = System.currentTimeMillis()
    }

    fun setDrawerSortByInstallTime(enabled: Boolean) {
        _drawerSortByInstallTime.value = enabled
        lastDrawerActivityTimeMs = System.currentTimeMillis()
    }

    fun toggleDrawerSelectedHomePage(pageNumber: Int) {
        val current = _drawerSelectedHomePages.value
        val updated = if (current.contains(pageNumber)) {
            current - pageNumber
        } else {
            current + pageNumber
        }
        _drawerSelectedHomePages.value = updated
        if (updated.isNotEmpty()) {
            _drawerFilterNotOnHome.value = false
        }
        lastDrawerActivityTimeMs = System.currentTimeMillis()
    }

    fun clearDrawerSearchAndFilters() {
        _drawerSearchQuery.value = ""
        _drawerFilterNotOnHome.value = false
        _drawerSortByInstallTime.value = false
        _drawerSelectedHomePages.value = emptySet()
        _isDrawerSelectionMode.value = false
        _selectedDrawerApps.value = emptySet()
        lastDrawerClosedTimeMs = 0L
    }

    fun checkDrawerRetentionTimeout() {
        if (lastDrawerClosedTimeMs > 0L) {
            val now = System.currentTimeMillis()
            if (now - lastDrawerClosedTimeMs >= DRAWER_RETENTION_TIMEOUT_MS) {
                clearDrawerSearchAndFilters()
            }
        }
    }

    fun onAppBackground(timeMs: Long = System.currentTimeMillis()) {
        lastDrawerClosedTimeMs = timeMs
        _isDrawerUnlocked.value = false
    }

    fun onScreenOff() {
        lockAllPages()
        clearDrawerSearchAndFilters()
    }

    fun setDrawerOpen(isOpen: Boolean) {
        if (isOpen) {
            checkDrawerRetentionTimeout()
        } else {
            lastDrawerClosedTimeMs = System.currentTimeMillis()
            _isDrawerUnlocked.value = false
        }
        _isDrawerOpen.value = isOpen
    }

    fun setCurrentPageIndex(index: Int) {
        _currentPageIndex.value = index
    }

    // Edit/Selection Mode Methods
    fun enterSelectionMode(firstItem: HomeItemEntity? = null) {
        _isSelectionMode.value = true
        _selectedItems.value = if (firstItem != null) setOf(firstItem) else emptySet()
    }

    fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedItems.value = emptySet()
    }

    fun toggleItemSelection(item: HomeItemEntity) {
        val currentSet = _selectedItems.value.toMutableSet()
        if (currentSet.contains(item)) {
            currentSet.remove(item)
        } else {
            currentSet.add(item)
        }
        _selectedItems.value = currentSet

        // Auto-exit selection mode if nothing is selected anymore
        if (currentSet.isEmpty()) {
            exitSelectionMode()
        }
    }

    // Drawer Selection Mode Methods
    fun enterDrawerSelectionMode(firstApp: AppInfo? = null) {
        _isDrawerSelectionMode.value = true
        _selectedDrawerApps.value = if (firstApp != null) setOf(firstApp) else emptySet()
    }

    fun exitDrawerSelectionMode() {
        _isDrawerSelectionMode.value = false
        _selectedDrawerApps.value = emptySet()
    }

    fun toggleDrawerAppSelection(app: AppInfo) {
        val currentSet = _selectedDrawerApps.value.toMutableSet()
        if (currentSet.contains(app)) {
            currentSet.remove(app)
        } else {
            currentSet.add(app)
        }
        _selectedDrawerApps.value = currentSet

        if (currentSet.isEmpty()) {
            exitDrawerSelectionMode()
        }
    }

    fun selectAllDrawerApps(apps: List<AppInfo>) {
        _selectedDrawerApps.value = apps.toSet()
    }

    fun clearDrawerAppSelection() {
        _selectedDrawerApps.value = emptySet()
    }

    fun resetToDefaultHomeScreen(forcePageReset: Boolean = true) {
        _isDrawerOpen.value = false
        _isDrawerUnlocked.value = false
        _activeFolder.value = null
        _isSelectionMode.value = false
        _selectedItems.value = emptySet()
        _dismissDialogsTrigger.value += 1
        if (forcePageReset) {
            lockAllPages()
            clearDrawerSearchAndFilters()
            val defaultIdx = _defaultPinnedPageIndex.value
            val targetPagerIdx = activePageIndices.value.indexOf(defaultIdx)
            if (targetPagerIdx != -1) {
                _currentPageIndex.value = targetPagerIdx
            } else {
                _currentPageIndex.value = 0
            }
            _resetTrigger.value += 1
        }
    }

    fun addDrawerAppsToPage(apps: List<AppInfo>, targetPageIndex: Int) {
        exitDrawerSelectionMode()
        viewModelScope.launch(Dispatchers.IO) {
            if (targetPageIndex == -1) {
                val currentDock = repository.allHomeItems.first().filter { it.pageIndex == -1 }
                var available = 5 - currentDock.size
                if (available <= 0) {
                    viewModelScope.launch(Dispatchers.Main) {
                        android.widget.Toast.makeText(getApplication(), "ドックには最大5つのアイテムしか配置できません", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                for (app in apps.take(available)) {
                    repository.addAppToHome(
                        packageName = app.packageName,
                        className = app.className,
                        label = app.label,
                        pageIndex = -1
                    )
                }
            } else {
                for (app in apps) {
                    repository.addAppToHome(
                        packageName = app.packageName,
                        className = app.className,
                        label = app.label,
                        pageIndex = targetPageIndex
                    )
                }
            }
        }
    }

    fun addDrawerAppsToGroup(apps: List<AppInfo>, targetGroup: HomeItemEntity) {
        exitDrawerSelectionMode()
        viewModelScope.launch(Dispatchers.IO) {
            val currentGroupApps = repository.allGroupApps.first()
            val targetGroupApps = currentGroupApps.filter { it.homeItemId == targetGroup.id }
            var nextPos = if (targetGroupApps.isEmpty()) 0 else targetGroupApps.maxOf { it.positionIndex } + 1

            val appsToInsert = mutableListOf<GroupAppEntity>()
            for (app in apps) {
                appsToInsert.add(
                    GroupAppEntity(
                        homeItemId = targetGroup.id,
                        packageName = app.packageName,
                        className = app.className,
                        label = app.label,
                        positionIndex = nextPos++
                    )
                )
            }
            database.launcherDao().insertGroupApps(appsToInsert)
        }
    }

    fun createGroupWithDrawerApps(apps: List<AppInfo>, folderName: String, pageIndex: Int) {
        exitDrawerSelectionMode()
        viewModelScope.launch(Dispatchers.IO) {
            val currentItems = repository.allHomeItems.first()
            val pageItems = currentItems.filter { it.pageIndex == pageIndex }
            val targetPosition = if (pageItems.isEmpty()) 0 else pageItems.maxOf { it.positionIndex } + 1

            val groupItem = HomeItemEntity(
                pageIndex = pageIndex,
                positionIndex = targetPosition,
                itemType = "GROUP",
                label = folderName,
                packageName = null,
                className = null
            )
            val newGroupId = database.launcherDao().insertHomeItem(groupItem)

            val groupAppsToInsert = apps.mapIndexed { idx, app ->
                GroupAppEntity(
                    homeItemId = newGroupId,
                    packageName = app.packageName,
                    className = app.className,
                    label = app.label,
                    positionIndex = idx
                )
            }
            database.launcherDao().insertGroupApps(groupAppsToInsert)
        }
    }

    fun openFolder(folder: HomeItemEntity) {
        if (folder.itemType == "GROUP") {
            _activeFolder.value = folder
        }
    }

    fun closeFolder() {
        _activeFolder.value = null
    }

    // Repository Delegation Methods
    fun addAppToHome(app: AppInfo, pageIndex: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addAppToHome(
                packageName = app.packageName,
                className = app.className,
                label = app.label,
                pageIndex = pageIndex
            )
        }
    }

    fun addWebShortcutToHome(title: String, url: String, pageIndex: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addWebShortcutToHome(title = title, url = url, pageIndex = pageIndex)
            com.example.util.WebShortcutIconManager.fetchAndCacheAsync(getApplication(), url)
        }
    }

    fun addMultipleWebShortcuts(shortcuts: List<Triple<String, String, Int>>) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addMultipleWebShortcuts(shortcuts)
            shortcuts.forEach { (_, url, _) ->
                com.example.util.WebShortcutIconManager.fetchAndCacheAsync(getApplication(), url)
            }
        }
    }

    fun replaceAllWebShortcuts(shortcuts: List<Triple<String, String, Int>>) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.replaceAllWebShortcuts(shortcuts)
            shortcuts.forEach { (_, url, _) ->
                com.example.util.WebShortcutIconManager.fetchAndCacheAsync(getApplication(), url)
            }
        }
    }

    fun addWebShortcutToDock(title: String, url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentDockItems = repository.allHomeItems.first().filter { it.pageIndex == -1 }
            if (currentDockItems.size >= 5) {
                viewModelScope.launch(Dispatchers.Main) {
                    android.widget.Toast.makeText(getApplication(), "ドックには最大5つのアイテムしか配置できません", android.widget.Toast.LENGTH_SHORT).show()
                }
            } else {
                repository.addWebShortcutToHome(title = title, url = url, pageIndex = -1)
                com.example.util.WebShortcutIconManager.fetchAndCacheAsync(getApplication(), url)
                viewModelScope.launch(Dispatchers.Main) {
                    android.widget.Toast.makeText(getApplication(), "ドックにウェブショートカットを追加しました", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun updateWebShortcut(item: HomeItemEntity, newTitle: String, newUrl: String, newPageIndex: Int? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val targetPage = newPageIndex ?: item.pageIndex
            val updated = item.copy(label = newTitle, url = newUrl, pageIndex = targetPage)
            repository.updateHomeItem(updated)
            if (item.url != newUrl) {
                com.example.util.WebShortcutIconManager.fetchAndCacheAsync(getApplication(), newUrl)
            }
        }
    }

    fun updateItemLabel(item: HomeItemEntity, newLabel: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = item.copy(label = newLabel)
            repository.updateHomeItem(updated)
        }
    }

    fun removeSelectedItems() {
        val itemsToDelete = _selectedItems.value.toList()
        exitSelectionMode()
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeItems(itemsToDelete)
        }
    }

    fun moveSelectedItemsToPage(targetPageIndex: Int) {
        val itemsToMove = _selectedItems.value.toList()
        exitSelectionMode()
        viewModelScope.launch(Dispatchers.IO) {
            repository.moveItemsToPage(itemsToMove, targetPageIndex)
        }
    }

    fun groupSelectedItems(folderName: String) {
        val itemsToGroup = _selectedItems.value.toList()
        val page = itemsToGroup.firstOrNull()?.pageIndex
            ?: activePageIndices.value.getOrNull(_currentPageIndex.value)
            ?: (activePageIndices.value.firstOrNull() ?: 0)
        exitSelectionMode()
        viewModelScope.launch(Dispatchers.IO) {
            repository.groupItems(itemsToGroup, folderName, page)
        }
    }

    fun removeAppFromFolder(folder: HomeItemEntity, app: GroupAppEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            // Remove the app from GroupAppEntity
            database.launcherDao().deleteGroupApp(app)
            
            // Check if the folder is now empty. If so, delete the folder as well.
            val remainingApps = database.launcherDao().getAllGroupApps().first().filter { it.homeItemId == folder.id }
            if (remainingApps.isEmpty()) {
                repository.deleteHomeItem(folder)
                _activeFolder.value = null
            } else {
                // Return item back to the home screen as an individual app or web shortcut
                if (!app.url.isNullOrEmpty()) {
                    repository.addWebShortcutToHome(
                        title = app.label,
                        url = app.url,
                        pageIndex = folder.pageIndex
                    )
                } else {
                    repository.addAppToHome(
                        packageName = app.packageName,
                        className = app.className,
                        label = app.label,
                        pageIndex = folder.pageIndex
                    )
                }
            }
        }
    }

    // Move selected apps into an existing group/folder
    fun moveSelectedItemsToGroup(targetGroup: HomeItemEntity) {
        val itemsToMove = _selectedItems.value.toList()
        exitSelectionMode()
        viewModelScope.launch(Dispatchers.IO) {
            val currentGroupApps = repository.allGroupApps.first()
            val targetGroupApps = currentGroupApps.filter { it.homeItemId == targetGroup.id }
            var nextPos = if (targetGroupApps.isEmpty()) 0 else targetGroupApps.maxOf { it.positionIndex } + 1

            val appsToInsert = mutableListOf<GroupAppEntity>()
            val itemsToRemove = mutableListOf<HomeItemEntity>()

            for (item in itemsToMove) {
                if (item.id == targetGroup.id) continue

                if (item.itemType == "APP") {
                    appsToInsert.add(
                        GroupAppEntity(
                            homeItemId = targetGroup.id,
                            packageName = item.packageName ?: "",
                            className = item.className ?: "",
                            label = item.label,
                            positionIndex = nextPos++
                        )
                    )
                    itemsToRemove.add(item)
                } else if (item.itemType == "WEB_SHORTCUT") {
                    appsToInsert.add(
                        GroupAppEntity(
                            homeItemId = targetGroup.id,
                            packageName = "",
                            className = "",
                            label = item.label,
                            positionIndex = nextPos++,
                            url = item.url
                        )
                    )
                    itemsToRemove.add(item)
                } else if (item.itemType == "GROUP") {
                    // Merge nested folder apps into target folder
                    val nestedApps = currentGroupApps.filter { it.homeItemId == item.id }
                    for (nestedApp in nestedApps) {
                        appsToInsert.add(
                            GroupAppEntity(
                                homeItemId = targetGroup.id,
                                packageName = nestedApp.packageName,
                                className = nestedApp.className,
                                label = nestedApp.label,
                                positionIndex = nextPos++,
                                url = nestedApp.url
                            )
                        )
                    }
                    itemsToRemove.add(item)
                }
            }

            if (appsToInsert.isNotEmpty()) {
                database.launcherDao().insertGroupApps(appsToInsert)
            }
            if (itemsToRemove.isNotEmpty()) {
                repository.removeItems(itemsToRemove)
            }
        }
    }

    // Export current desktop configuration to a portable JSON string
    fun exportConfig(onComplete: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val items = repository.allHomeItems.first()
                val subApps = repository.allGroupApps.first()

                val root = org.json.JSONObject()
                root.put("version", 1)
                root.put("homePageCount", homePageCount.value)

                val enabledArr = org.json.JSONArray()
                _enabledPages.value.sorted().forEach { enabledArr.put(it) }
                root.put("enabledPages", enabledArr)

                val itemsArray = org.json.JSONArray()
                for (item in items) {
                    val jo = org.json.JSONObject()
                    jo.put("id", item.id)
                    jo.put("pageIndex", item.pageIndex)
                    jo.put("positionIndex", item.positionIndex)
                    jo.put("itemType", item.itemType)
                    jo.put("label", item.label)
                    jo.put("packageName", item.packageName ?: org.json.JSONObject.NULL)
                    jo.put("className", item.className ?: org.json.JSONObject.NULL)
                    jo.put("url", item.url ?: org.json.JSONObject.NULL)
                    itemsArray.put(jo)
                }
                root.put("homeItems", itemsArray)

                val appsArray = org.json.JSONArray()
                for (app in subApps) {
                    val jo = org.json.JSONObject()
                    jo.put("homeItemId", app.homeItemId)
                    jo.put("packageName", app.packageName)
                    jo.put("className", app.className)
                    jo.put("label", app.label)
                    jo.put("positionIndex", app.positionIndex)
                    jo.put("url", app.url ?: org.json.JSONObject.NULL)
                    appsArray.put(jo)
                }
                root.put("groupApps", appsArray)

                val colorsJson = org.json.JSONObject()
                _pageBackgroundColors.value.forEach { (k, v) -> colorsJson.put(k.toString(), v) }
                root.put("pageBackgroundColors", colorsJson)

                root.put("defaultPinnedPageIndex", _defaultPinnedPageIndex.value)

                val labelsJson = org.json.JSONObject()
                _pageLabels.value.forEach { (k, v) -> labelsJson.put(k.toString(), v) }
                root.put("pageLabels", labelsJson)

                val lockedArr = org.json.JSONArray()
                _lockedPages.value.sorted().forEach { lockedArr.put(it) }
                root.put("lockedPages", lockedArr)

                root.put("pageLockPin", _pageLockPin.value)
                root.put("userName", _backupUserName.value)

                // バックアップ作成時は設定スクリーンのみロック解除した状態(false)で作成 (PIN紛失時の復旧用)
                root.put("isSettingsLocked", false)
                root.put("isDrawerEnabled", _isDrawerEnabled.value)
                root.put("isDrawerLocked", _isDrawerLocked.value)
                root.put("isUninstallLocked", _isUninstallLocked.value)

                val pageOrderArr = org.json.JSONArray()
                _pageOrder.value.forEach { pageOrderArr.put(it) }
                root.put("pageOrder", pageOrderArr)

                val jsonStr = root.toString(2)
                viewModelScope.launch(Dispatchers.Main) {
                    onComplete(jsonStr)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                viewModelScope.launch(Dispatchers.Main) {
                    onComplete("")
                }
            }
        }
    }

    // Import configurations from a portable JSON string
    fun importConfig(jsonStr: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val root = org.json.JSONObject(jsonStr)
                if (!root.has("homeItems")) {
                    viewModelScope.launch(Dispatchers.Main) {
                        onResult(false, "不適切なJSON形式です：homeItemsキーがありません")
                    }
                    return@launch
                }

                // Clear both tables
                database.launcherDao().deleteAllHomeItems()
                database.launcherDao().deleteAllGroupApps()

                val itemsArray = root.getJSONArray("homeItems")
                val oldToNewIdMap = mutableMapOf<Long, Long>()

                for (i in 0 until itemsArray.length()) {
                    val jo = itemsArray.getJSONObject(i)
                    val oldId = jo.optLong("id", 0L)
                    val item = HomeItemEntity(
                        pageIndex = jo.getInt("pageIndex"),
                        positionIndex = jo.getInt("positionIndex"),
                        itemType = jo.getString("itemType"),
                        label = jo.getString("label"),
                        packageName = if (jo.isNull("packageName")) null else jo.getString("packageName"),
                        className = if (jo.isNull("className")) null else jo.getString("className"),
                        url = if (jo.isNull("url")) null else jo.getString("url")
                    )
                    val newId = database.launcherDao().insertHomeItem(item)
                    if (oldId != 0L) {
                        oldToNewIdMap[oldId] = newId
                    }
                }

                if (root.has("groupApps")) {
                    val appsArray = root.getJSONArray("groupApps")
                    val groupAppsList = mutableListOf<GroupAppEntity>()
                    for (i in 0 until appsArray.length()) {
                        val jo = appsArray.getJSONObject(i)
                        val oldParentId = jo.getLong("homeItemId")
                        val newParentId = oldToNewIdMap[oldParentId]
                        if (newParentId != null) {
                            groupAppsList.add(
                                GroupAppEntity(
                                    homeItemId = newParentId,
                                    packageName = jo.getString("packageName"),
                                    className = jo.getString("className"),
                                    label = jo.getString("label"),
                                    positionIndex = jo.getInt("positionIndex"),
                                    url = if (jo.isNull("url")) null else jo.getString("url")
                                )
                            )
                        }
                    }
                    if (groupAppsList.isNotEmpty()) {
                        database.launcherDao().insertGroupApps(groupAppsList)
                    }
                }

                if (root.has("pageBackgroundColors")) {
                    val colorsObj = root.getJSONObject("pageBackgroundColors")
                    val map = mutableMapOf<Int, String>()
                    val keys = colorsObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        map[key.toInt()] = colorsObj.getString(key)
                    }
                    _pageBackgroundColors.value = map
                    savePageColorsToPrefs(map)
                }

                if (root.has("defaultPinnedPageIndex")) {
                    val pinnedIdx = root.getInt("defaultPinnedPageIndex")
                    _defaultPinnedPageIndex.value = pinnedIdx
                    prefs.edit().putInt("default_pinned_page_index", pinnedIdx).apply()
                }

                if (root.has("pageLabels")) {
                    val labelsObj = root.getJSONObject("pageLabels")
                    val map = mutableMapOf<Int, String>()
                    val keys = labelsObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        map[key.toInt()] = labelsObj.getString(key)
                    }
                    _pageLabels.value = map
                    savePageLabelsToPrefs(map)
                }

                if (root.has("enabledPages")) {
                    val arr = root.getJSONArray("enabledPages")
                    val set = mutableSetOf<Int>()
                    for (i in 0 until arr.length()) {
                        set.add(arr.getInt(i))
                    }
                    if (set.isNotEmpty()) setEnabledPages(set)
                } else if (root.has("homePageCount")) {
                    val count = root.getInt("homePageCount").coerceIn(1, 9)
                    setEnabledPages((0 until count).toSet())
                }

                if (root.has("lockedPages")) {
                    val arr = root.getJSONArray("lockedPages")
                    val set = mutableSetOf<Int>()
                    for (i in 0 until arr.length()) {
                        set.add(arr.getInt(i))
                    }
                    _lockedPages.value = set
                    saveLockedPagesToPrefs(set)
                }

                if (root.has("pageLockPin")) {
                    setPageLockPin(root.getString("pageLockPin"))
                }

                if (root.has("userName")) {
                    setBackupUserName(root.getString("userName"))
                }

                if (root.has("isSettingsLocked")) {
                    val settingsLocked = root.getBoolean("isSettingsLocked")
                    _isSettingsLocked.value = settingsLocked
                    prefs.edit().putBoolean("is_settings_locked", settingsLocked).apply()
                } else {
                    _isSettingsLocked.value = false
                    prefs.edit().putBoolean("is_settings_locked", false).apply()
                }

                if (root.has("isDrawerEnabled")) {
                    val drawerEnabled = root.getBoolean("isDrawerEnabled")
                    _isDrawerEnabled.value = drawerEnabled
                    prefs.edit().putBoolean("is_drawer_enabled", drawerEnabled).apply()
                } else {
                    _isDrawerEnabled.value = true
                    prefs.edit().putBoolean("is_drawer_enabled", true).apply()
                }

                if (root.has("isDrawerLocked")) {
                    val drawerLocked = root.getBoolean("isDrawerLocked")
                    _isDrawerLocked.value = drawerLocked
                    prefs.edit().putBoolean("is_drawer_locked", drawerLocked).apply()
                } else {
                    _isDrawerLocked.value = false
                    prefs.edit().putBoolean("is_drawer_locked", false).apply()
                }

                if (root.has("isUninstallLocked")) {
                    val uninstallLocked = root.getBoolean("isUninstallLocked")
                    _isUninstallLocked.value = uninstallLocked
                    prefs.edit().putBoolean("is_uninstall_locked", uninstallLocked).apply()
                } else {
                    _isUninstallLocked.value = false
                    prefs.edit().putBoolean("is_uninstall_locked", false).apply()
                }

                if (root.has("pageOrder")) {
                    val arr = root.getJSONArray("pageOrder")
                    val list = mutableListOf<Int>()
                    for (i in 0 until arr.length()) {
                        val p = arr.getInt(i)
                        if (p in 0..8 && !list.contains(p)) list.add(p)
                    }
                    (0..8).forEach { if (!list.contains(it)) list.add(it) }
                    _pageOrder.value = list
                    savePageOrderToPrefs(list)
                }

                viewModelScope.launch(Dispatchers.Main) {
                    onResult(true, "レイアウト設定を復元しました")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                viewModelScope.launch(Dispatchers.Main) {
                    onResult(false, "復元に失敗しました: ${e.localizedMessage}")
                }
            }
        }
    }

    fun importFromGoogleDriveUrl(urlStr: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fileIdPattern = Regex("(?:/d/|id=)([a-zA-Z0-9_-]+)")
                val match = fileIdPattern.find(urlStr)
                val fileId = match?.groupValues?.get(1) ?: if (urlStr.trim().matches(Regex("[a-zA-Z0-9_-]{20,}"))) urlStr.trim() else null

                if (fileId.isNullOrEmpty()) {
                    viewModelScope.launch(Dispatchers.Main) {
                        onResult(false, "有効なGoogle Drive URLまたはファイルIDが見つかりませんでした")
                    }
                    return@launch
                }

                val downloadUrl = "https://drive.google.com/uc?export=download&confirm=no_antivirus&id=$fileId"
                val connection = java.net.URL(downloadUrl).openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 12000
                connection.readTimeout = 12000
                connection.instanceFollowRedirects = true

                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
                    importConfig(jsonStr, onResult)
                } else {
                    viewModelScope.launch(Dispatchers.Main) {
                        onResult(false, "Google Driveからの取得に失敗しました (HTTP $responseCode)")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                viewModelScope.launch(Dispatchers.Main) {
                    onResult(false, "エラーが発生しました: ${e.localizedMessage}")
                }
            }
        }
    }
}
