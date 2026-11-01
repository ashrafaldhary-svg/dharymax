package com.example.data.local

import com.example.data.model.DownloadTask
import kotlinx.coroutines.flow.Flow

class DownloadRepository(private val dao: DownloadDao) {
    val allTasks: Flow<List<DownloadTask>> = dao.getAllTasks()

    suspend fun getTaskById(id: String): DownloadTask? = dao.getTaskById(id)

    suspend fun insertTask(task: DownloadTask) = dao.insertTask(task)

    suspend fun updateTask(task: DownloadTask) = dao.updateTask(task)

    suspend fun updateProgress(
        id: String,
        status: String,
        downloadedBytes: Long,
        totalBytes: Long,
        speed: Long,
        eta: Long
    ) = dao.updateProgress(id, status, downloadedBytes, totalBytes, speed, eta)

    suspend fun markCompleted(id: String, status: String, filePath: String, completedAt: Long) =
        dao.markCompleted(id, status, filePath, completedAt)

    suspend fun markFailed(id: String, status: String, error: String) =
        dao.markFailed(id, status, error)

    suspend fun deleteTask(id: String) = dao.deleteTaskById(id)

    suspend fun clearCompleted() = dao.clearCompletedTasks()

    suspend fun clearAll() = dao.clearAllTasks()
}
