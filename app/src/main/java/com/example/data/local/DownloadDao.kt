package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.DownloadTask
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM download_tasks ORDER BY createdAt DESC")
    fun getAllTasks(): Flow<List<DownloadTask>>

    @Query("SELECT * FROM download_tasks WHERE id = :id")
    suspend fun getTaskById(id: String): DownloadTask?

    @Query("SELECT * FROM download_tasks WHERE status = :status ORDER BY createdAt DESC")
    fun getTasksByStatus(status: String): Flow<List<DownloadTask>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: DownloadTask)

    @Update
    suspend fun updateTask(task: DownloadTask)

    @Query("UPDATE download_tasks SET status = :status, downloadedBytes = :downloadedBytes, totalBytes = :totalBytes, downloadSpeedBps = :speed, etaSeconds = :eta WHERE id = :id")
    suspend fun updateProgress(id: String, status: String, downloadedBytes: Long, totalBytes: Long, speed: Long, eta: Long)

    @Query("UPDATE download_tasks SET status = :status, localFilePath = :filePath, completedAt = :completedAt WHERE id = :id")
    suspend fun markCompleted(id: String, status: String, filePath: String, completedAt: Long)

    @Query("UPDATE download_tasks SET status = :status, errorMessage = :error WHERE id = :id")
    suspend fun markFailed(id: String, status: String, error: String)

    @Query("DELETE FROM download_tasks WHERE id = :id")
    suspend fun deleteTaskById(id: String)

    @Query("DELETE FROM download_tasks WHERE status = 'COMPLETED'")
    suspend fun clearCompletedTasks()

    @Query("DELETE FROM download_tasks")
    suspend fun clearAllTasks()
}
