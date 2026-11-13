package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LauncherDao {
    @Query("SELECT * FROM home_items ORDER BY pageIndex ASC, positionIndex ASC")
    fun getAllHomeItems(): Flow<List<HomeItemEntity>>

    @Query("SELECT * FROM group_apps ORDER BY homeItemId ASC, positionIndex ASC")
    fun getAllGroupApps(): Flow<List<GroupAppEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHomeItem(item: HomeItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroupApps(apps: List<GroupAppEntity>)

    @Update
    suspend fun updateHomeItem(item: HomeItemEntity)

    @Update
    suspend fun updateHomeItems(items: List<HomeItemEntity>)

    @Delete
    suspend fun deleteHomeItem(item: HomeItemEntity)

    @Delete
    suspend fun deleteHomeItems(items: List<HomeItemEntity>)

    @Delete
    suspend fun deleteGroupApp(app: GroupAppEntity)

    @Delete
    suspend fun deleteGroupApps(apps: List<GroupAppEntity>)

    @Query("DELETE FROM group_apps WHERE homeItemId = :homeItemId")
    suspend fun deleteGroupAppsForHomeItem(homeItemId: Long)

    @Query("DELETE FROM group_apps WHERE homeItemId IN (:homeItemIds)")
    suspend fun deleteGroupAppsForHomeItems(homeItemIds: List<Long>)

    @Query("DELETE FROM home_items")
    suspend fun deleteAllHomeItems()

    @Query("DELETE FROM group_apps")
    suspend fun deleteAllGroupApps()
}
