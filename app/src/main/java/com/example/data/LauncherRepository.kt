package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class LauncherRepository(private val launcherDao: LauncherDao) {

    val allHomeItems: Flow<List<HomeItemEntity>> = launcherDao.getAllHomeItems()
    val allGroupApps: Flow<List<GroupAppEntity>> = launcherDao.getAllGroupApps()

    suspend fun insertHomeItem(item: HomeItemEntity): Long {
        return launcherDao.insertHomeItem(item)
    }

    suspend fun deleteHomeItem(item: HomeItemEntity) {
        launcherDao.deleteHomeItem(item)
        if (item.itemType == "GROUP") {
            launcherDao.deleteGroupAppsForHomeItem(item.id)
        }
    }

    suspend fun cleanupUninstalledApps(installedPackageNames: Set<String>) {
        if (installedPackageNames.isEmpty()) return

        val currentHomeItems = launcherDao.getAllHomeItems().first()
        val currentGroupApps = launcherDao.getAllGroupApps().first()

        // 1. Remove individual apps placed on home/dock whose package is no longer installed
        val orphanedHomeItems = currentHomeItems.filter { item ->
            item.itemType == "APP" && !item.packageName.isNullOrEmpty() && !installedPackageNames.contains(item.packageName)
        }
        if (orphanedHomeItems.isNotEmpty()) {
            launcherDao.deleteHomeItems(orphanedHomeItems)
        }

        // 2. Remove apps inside folders whose package is no longer installed (keep web shortcuts)
        val orphanedGroupApps = currentGroupApps.filter { groupApp ->
            groupApp.url.isNullOrEmpty() && groupApp.packageName.isNotEmpty() && !installedPackageNames.contains(groupApp.packageName)
        }
        if (orphanedGroupApps.isNotEmpty()) {
            launcherDao.deleteGroupApps(orphanedGroupApps)
        }

        // 3. If a folder becomes completely empty after removing uninstalled apps, delete the folder entity
        val remainingGroupApps = launcherDao.getAllGroupApps().first()
        val remainingGroupIds = remainingGroupApps.map { it.homeItemId }.toSet()
        val emptyFolders = launcherDao.getAllHomeItems().first().filter { item ->
            item.itemType == "GROUP" && !remainingGroupIds.contains(item.id)
        }
        if (emptyFolders.isNotEmpty()) {
            launcherDao.deleteHomeItems(emptyFolders)
        }
    }

    companion object {
        const val MAX_PAGE_CAPACITY = 20
    }

    private fun findAvailablePageIndex(currentItems: List<HomeItemEntity>, desiredPageIndex: Int): Int {
        if (desiredPageIndex == -1) {
            val dockCount = currentItems.count { it.pageIndex == -1 }
            if (dockCount < 5) {
                return -1
            }
            var page = 0
            val pageCounts = currentItems.groupBy { it.pageIndex }.mapValues { it.value.size }.toMutableMap()
            while ((pageCounts[page] ?: 0) >= MAX_PAGE_CAPACITY) {
                page++
            }
            return page
        } else {
            var page = if (desiredPageIndex < 0) 0 else desiredPageIndex
            val pageCounts = currentItems.groupBy { it.pageIndex }.mapValues { it.value.size }.toMutableMap()
            while ((pageCounts[page] ?: 0) >= MAX_PAGE_CAPACITY) {
                page++
            }
            return page
        }
    }

    suspend fun addAppToHome(packageName: String, className: String, label: String, pageIndex: Int) {
        val currentItems = launcherDao.getAllHomeItems().first()
        val targetPage = findAvailablePageIndex(currentItems, pageIndex)
        val pageItems = currentItems.filter { it.pageIndex == targetPage }
        val nextPosition = if (pageItems.isEmpty()) 0 else pageItems.maxOf { it.positionIndex } + 1

        val newItem = HomeItemEntity(
            pageIndex = targetPage,
            positionIndex = nextPosition,
            itemType = "APP",
            label = label,
            packageName = packageName,
            className = className
        )
        launcherDao.insertHomeItem(newItem)
    }

    suspend fun addWebShortcutToHome(title: String, url: String, pageIndex: Int) {
        val currentItems = launcherDao.getAllHomeItems().first()
        val targetPage = findAvailablePageIndex(currentItems, pageIndex)
        val pageItems = currentItems.filter { it.pageIndex == targetPage }
        val nextPosition = if (pageItems.isEmpty()) 0 else pageItems.maxOf { it.positionIndex } + 1

        val newItem = HomeItemEntity(
            pageIndex = targetPage,
            positionIndex = nextPosition,
            itemType = "WEB_SHORTCUT",
            label = title,
            packageName = null,
            className = null,
            url = url
        )
        launcherDao.insertHomeItem(newItem)
    }

    suspend fun addMultipleWebShortcuts(shortcuts: List<Triple<String, String, Int>>) {
        val currentItems = launcherDao.getAllHomeItems().first().toMutableList()
        val itemsToInsert = mutableListOf<HomeItemEntity>()

        for ((title, url, desiredPage) in shortcuts) {
            val targetPage = findAvailablePageIndex(currentItems, desiredPage)
            val pageItems = currentItems.filter { it.pageIndex == targetPage }
            val nextPosition = if (pageItems.isEmpty()) 0 else pageItems.maxOf { it.positionIndex } + 1

            val newItem = HomeItemEntity(
                pageIndex = targetPage,
                positionIndex = nextPosition,
                itemType = "WEB_SHORTCUT",
                label = title,
                packageName = null,
                className = null,
                url = url
            )
            itemsToInsert.add(newItem)
            currentItems.add(newItem)
        }

        for (item in itemsToInsert) {
            launcherDao.insertHomeItem(item)
        }
    }

    suspend fun replaceAllWebShortcuts(shortcuts: List<Triple<String, String, Int>>) {
        val currentItems = launcherDao.getAllHomeItems().first()
        val existingWebShortcuts = currentItems.filter { it.itemType == "WEB_SHORTCUT" }
        if (existingWebShortcuts.isNotEmpty()) {
            launcherDao.deleteHomeItems(existingWebShortcuts)
        }
        addMultipleWebShortcuts(shortcuts)
    }

    suspend fun updateHomeItem(item: HomeItemEntity) {
        launcherDao.updateHomeItem(item)
    }

    suspend fun removeItems(items: List<HomeItemEntity>) {
        launcherDao.deleteHomeItems(items)
        val groupIds = items.filter { it.itemType == "GROUP" }.map { it.id }
        if (groupIds.isNotEmpty()) {
            launcherDao.deleteGroupAppsForHomeItems(groupIds)
        }
    }

    suspend fun moveItemsToPage(items: List<HomeItemEntity>, targetPageIndex: Int) {
        val currentItems = launcherDao.getAllHomeItems().first()
        val pageItems = currentItems.filter { it.pageIndex == targetPageIndex }
        var nextPosition = if (pageItems.isEmpty()) 0 else pageItems.maxOf { it.positionIndex } + 1

        val updatedItems = items.map { item ->
            val updated = item.copy(
                pageIndex = targetPageIndex,
                positionIndex = nextPosition
            )
            nextPosition++
            updated
        }
        launcherDao.updateHomeItems(updatedItems)
    }

    suspend fun groupItems(itemsToGroup: List<HomeItemEntity>, folderName: String, pageIndex: Int) {
        val currentItems = launcherDao.getAllHomeItems().first()
        val pageItems = currentItems.filter { it.pageIndex == pageIndex }
        val fallbackPosition = if (pageItems.isEmpty()) 0 else pageItems.maxOf { it.positionIndex } + 1
        val targetPosition = itemsToGroup.minOfOrNull { it.positionIndex } ?: fallbackPosition

        // Create the GROUP parent entity
        val groupItem = HomeItemEntity(
            pageIndex = pageIndex,
            positionIndex = targetPosition,
            itemType = "GROUP",
            label = folderName,
            packageName = null,
            className = null
        )
        val newGroupId = launcherDao.insertHomeItem(groupItem)

        // Compile all individual apps / web shortcuts to insert inside the group
        val groupAppsToInsert = mutableListOf<GroupAppEntity>()
        var appPosition = 0

        val currentGroupApps = launcherDao.getAllGroupApps().first()

        for (item in itemsToGroup) {
            if (item.itemType == "APP") {
                groupAppsToInsert.add(
                    GroupAppEntity(
                        homeItemId = newGroupId,
                        packageName = item.packageName ?: "",
                        className = item.className ?: "",
                        label = item.label,
                        positionIndex = appPosition++
                    )
                )
            } else if (item.itemType == "WEB_SHORTCUT") {
                groupAppsToInsert.add(
                    GroupAppEntity(
                        homeItemId = newGroupId,
                        packageName = "",
                        className = "",
                        label = item.label,
                        positionIndex = appPosition++,
                        url = item.url
                    )
                )
            } else if (item.itemType == "GROUP") {
                // If nested group, copy its apps into the new group
                val nestedApps = currentGroupApps.filter { it.homeItemId == item.id }.sortedBy { it.positionIndex }
                for (nestedApp in nestedApps) {
                    groupAppsToInsert.add(
                        GroupAppEntity(
                            homeItemId = newGroupId,
                            packageName = nestedApp.packageName,
                            className = nestedApp.className,
                            label = nestedApp.label,
                            positionIndex = appPosition++,
                            url = nestedApp.url
                        )
                    )
                }
            }
        }

        if (groupAppsToInsert.isNotEmpty()) {
            launcherDao.insertGroupApps(groupAppsToInsert)
        }

        // Delete the original home screen items that were grouped
        launcherDao.deleteHomeItems(itemsToGroup)
        val oldGroupIds = itemsToGroup.filter { it.itemType == "GROUP" }.map { it.id }
        if (oldGroupIds.isNotEmpty()) {
            launcherDao.deleteGroupAppsForHomeItems(oldGroupIds)
        }
    }
}
