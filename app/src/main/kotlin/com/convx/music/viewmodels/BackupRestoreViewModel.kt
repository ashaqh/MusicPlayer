/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.viewmodels

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.convx.music.MainActivity
import com.convx.music.R
import com.convx.music.db.InternalDatabase
import com.convx.music.db.MusicDatabase
import com.convx.music.extensions.div
import com.convx.music.extensions.tryOrNull
import com.convx.music.extensions.zipInputStream
import com.convx.music.extensions.zipOutputStream
import com.convx.music.playback.MusicService
import com.convx.music.playback.MusicService.Companion.PERSISTENT_AUTOMIX_FILE
import com.convx.music.playback.MusicService.Companion.PERSISTENT_PLAYBACK_SNAPSHOT_FILE
import com.convx.music.playback.MusicService.Companion.PERSISTENT_QUEUE_FILE
import com.convx.music.playback.MusicService.Companion.PERSISTENT_PLAYER_STATE_FILE
import com.convx.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import javax.inject.Inject
import kotlin.system.exitProcess

@HiltViewModel
class BackupRestoreViewModel @Inject constructor(
    val database: MusicDatabase,
) : ViewModel() {
    fun backup(context: Context, uri: Uri) {
        val appContext = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                appContext.contentResolver.openOutputStream(uri)?.use {
                    it.buffered().zipOutputStream().use { outputStream ->
                        val settingsFile = appContext.filesDir / "datastore" / SETTINGS_FILENAME
                        if (settingsFile.exists()) {
                            settingsFile.inputStream().buffered().use { inputStream ->
                                outputStream.putNextEntry(ZipEntry(SETTINGS_FILENAME))
                                inputStream.copyTo(outputStream)
                            }
                        }
                        database.checkpoint()
                        val dbPath = database.openHelper.writableDatabase.path
                        FileInputStream(dbPath).use { inputStream ->
                            outputStream.putNextEntry(ZipEntry(InternalDatabase.DB_NAME))
                            inputStream.copyTo(outputStream)
                        }
                        // Background media is stored as files; DataStore saves only its path.
                        appContext.filesDir.listFiles { file -> file.name.startsWith(HOME_BACKGROUND_FILE_PREFIX) }
                            ?.forEach { bgFile ->
                                bgFile.inputStream().use { inputStream ->
                                    outputStream.putNextEntry(ZipEntry("$HOME_BACKGROUND_ZIP_DIR/${bgFile.name}"))
                                    inputStream.copyTo(outputStream)
                                }
                            }
                    }
                } ?: error("Failed to open output stream")
            }.onSuccess {
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, R.string.backup_create_success, Toast.LENGTH_SHORT).show()
                }
            }.onFailure {
                reportException(it)
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, R.string.backup_create_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun restore(context: Context, uri: Uri) {
        val appContext = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                Timber.tag("RESTORE").i("Starting restore from URI: $uri")
                appContext.contentResolver.openInputStream(uri)?.use { raw ->
                    raw.zipInputStream().use { inputStream ->
                        var entry = tryOrNull { inputStream.nextEntry } // prevent ZipException
                        var foundAny = false
                        while (entry != null) {
                            Timber.tag("RESTORE").i("Found zip entry: ${entry.name}")
                            if (restoreArchiveEntry(entry.name, inputStream, appContext)) foundAny = true
                            entry = tryOrNull { inputStream.nextEntry } // prevent ZipException
                        }
                        if (!foundAny) {
                            Timber.tag("RESTORE").w("No expected entries found in archive")
                        }
                    }
                } ?: run {
                    Timber.tag("RESTORE").e("Could not open input stream for uri: $uri")
                    error("Could not open input stream")
                }

                appContext.stopService(Intent(appContext, MusicService::class.java))
                appContext.filesDir.resolve(PERSISTENT_QUEUE_FILE).delete()
                appContext.filesDir.resolve(PERSISTENT_AUTOMIX_FILE).delete()
                appContext.filesDir.resolve(PERSISTENT_PLAYER_STATE_FILE).delete()
                appContext.filesDir.resolve(PERSISTENT_PLAYBACK_SNAPSHOT_FILE).delete()
                withContext(Dispatchers.Main) {
                    val intent = Intent(appContext, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                    appContext.startActivity(intent)
                    exitProcess(0)
                }
            }.onFailure {
                reportException(it)
                Timber.tag("RESTORE").e(it, "Restore failed")
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, R.string.restore_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun restoreArchiveEntry(
        entryName: String,
        inputStream: InputStream,
        context: Context,
    ): Boolean = when (entryName) {
        SETTINGS_FILENAME -> {
            Timber.tag("RESTORE").i("Restoring settings to datastore")
            (context.filesDir / "datastore" / SETTINGS_FILENAME).outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            true
        }

        InternalDatabase.DB_NAME -> {
            Timber.tag("RESTORE").i("Restoring DB (entry = $entryName)")
            // Capture the path before closing Room.
            val dbPath = database.openHelper.writableDatabase.path
            database.checkpoint()
            // Mark shutdown as expected so active Flow collectors do not report a crash.
            com.convx.music.utils.CrashHandler.databaseCloseExpected = true
            database.close()
            // Remove WAL and SHM journal files to prevent SQLite WAL corruption/recovery conflicts
            File("$dbPath-wal").delete()
            File("$dbPath-shm").delete()
            Timber.tag("RESTORE").i("Overwriting DB at path: $dbPath")
            FileOutputStream(dbPath).use { outputStream -> inputStream.copyTo(outputStream) }
            Timber.tag("RESTORE").i("DB overwrite complete")
            true
        }

        else -> {
            if (!entryName.startsWith("$HOME_BACKGROUND_ZIP_DIR/")) {
                Timber.tag("RESTORE").i("Skipping unexpected entry: $entryName")
                false
            } else {
                Timber.tag("RESTORE").i("Restoring home background file: $entryName")
                val fileName = entryName.removePrefix("$HOME_BACKGROUND_ZIP_DIR/")
                context.filesDir.resolve(fileName).outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                true
            }
        }
    }

    companion object {
        const val SETTINGS_FILENAME = "settings.preferences_pb"

        // Keep this prefix in sync with HomeBackgroundSettings.copyBackgroundMedia().
        private const val HOME_BACKGROUND_FILE_PREFIX = "home_background_"
        private const val HOME_BACKGROUND_ZIP_DIR = "home_background_files"
    }
}
