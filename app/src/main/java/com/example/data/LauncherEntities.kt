package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "home_items")
data class HomeItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pageIndex: Int,
    val positionIndex: Int,
    val itemType: String, // "APP", "GROUP", or "WEB_SHORTCUT"
    val label: String,    // App name, Folder name, or Shortcut title
    val packageName: String?, // Null for groups / web shortcuts
    val className: String?,   // Null for groups / web shortcuts
    val url: String? = null   // URL for web shortcuts
)

@Entity(tableName = "group_apps")
data class GroupAppEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val homeItemId: Long, // References HomeItemEntity.id
    val packageName: String,
    val className: String,
    val label: String,
    val positionIndex: Int,
    val url: String? = null // URL for web shortcuts inside folders
)
