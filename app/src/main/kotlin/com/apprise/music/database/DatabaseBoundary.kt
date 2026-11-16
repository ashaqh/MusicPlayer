package com.apprise.music.database

import com.convx.music.db.MusicDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Snapshot of Room database schema and library health metrics. */
data class DatabaseHealthSnapshot(
    val databaseName: String,
    val schemaVersion: Int,
    val isOpen: Boolean,
    val localSongCount: Int,
)

/** Clean boundary governing schema ownership, health diagnostics, and maintenance tasks. */
interface MusicDatabaseBoundary {
    suspend fun inspectHealth(): DatabaseHealthSnapshot
}

@Singleton
class RoomMusicDatabaseBoundary @Inject constructor(
    private val database: MusicDatabase,
) : MusicDatabaseBoundary {

    override suspend fun inspectHealth(): DatabaseHealthSnapshot = withContext(Dispatchers.IO) {
        val localCount = runCatching { database.localSongIds().size }.getOrDefault(0)
        val open = runCatching { database.openHelper.readableDatabase.isOpen }.getOrDefault(false)
        val version = runCatching { database.openHelper.readableDatabase.version }.getOrDefault(SCHEMA_VERSION)
        DatabaseHealthSnapshot(
            databaseName = DATABASE_NAME,
            schemaVersion = version,
            isOpen = open,
            localSongCount = localCount,
        )
    }

    companion object {
        const val DATABASE_NAME = "song.db"
        const val SCHEMA_VERSION = 40
    }
}

