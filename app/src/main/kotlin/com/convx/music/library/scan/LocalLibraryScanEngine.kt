/**
 * Apprise Music (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.library.scan

import android.content.Context
import com.convx.music.db.MusicDatabase
import com.convx.music.di.ApplicationScope
import com.convx.music.utils.LocalAudioScanner
import com.convx.music.utils.LocalFolderIndex
import com.convx.music.utils.reportException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single-flight background coordinator for device audio library scanning.
 * Guarantees scans run off the main thread, expose live progress, support clean
 * cancellation, recover safely from storage errors, and avoid redundant full rescans.
 */
@Singleton
class LocalLibraryScanEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {
    private val scanMutex = Mutex()
    private var activeJob: Job? = null
    @Volatile
    private var lastCompletedAtMs: Long = 0L

    private val _progress = MutableStateFlow(LocalScanProgress())
    val progress: StateFlow<LocalScanProgress> = _progress.asStateFlow()

    private val _lastResult = MutableStateFlow<LocalAudioScanner.ScanResult?>(null)
    val lastResult: StateFlow<LocalAudioScanner.ScanResult?> = _lastResult.asStateFlow()

    fun triggerScan(force: Boolean = false) {
        if (!force && activeJob?.isActive == true) return
        if (!force && System.currentTimeMillis() - lastCompletedAtMs < AUTO_SCAN_DEBOUNCE_MS) return
        if (activeJob?.isActive == true) return

        activeJob = applicationScope.launch(Dispatchers.IO) {
            runScanLocked()
        }
    }

    suspend fun scanAndAwait(force: Boolean = true): LocalAudioScanner.ScanResult {
        if (!force && System.currentTimeMillis() - lastCompletedAtMs < AUTO_SCAN_DEBOUNCE_MS) {
            _lastResult.value?.let { return it }
        }
        val running = activeJob
        if (running != null && running.isActive) {
            running.join()
            _lastResult.value?.let { return it }
        }
        val job = applicationScope.launch(Dispatchers.IO) {
            runScanLocked()
        }
        activeJob = job
        job.join()
        return _lastResult.value ?: LocalAudioScanner.ScanResult(0, 0, 0)
    }

    fun cancelScan() {
        val current = activeJob
        if (current != null && current.isActive) {
            current.cancel(CancellationException("Scan cancelled by user"))
            _progress.value = _progress.value.copy(phase = LocalScanPhase.CANCELLED)
        }
    }

    fun clearLastResult() {
        _lastResult.value = null
        if (!_progress.value.isScanning) {
            _progress.value = LocalScanProgress()
        }
    }

    private suspend fun runScanLocked() {
        scanMutex.withLock {
            try {
                val result = LocalAudioScanner.scanAndInsert(
                    context = context,
                    database = database,
                    onProgress = { snapshot -> _progress.value = snapshot },
                )
                _lastResult.value = result
                lastCompletedAtMs = System.currentTimeMillis()
                LocalFolderIndex.invalidate()
            } catch (cancelled: CancellationException) {
                Timber.tag(TAG).i("Local library scan cancelled")
                _progress.value = _progress.value.copy(phase = LocalScanPhase.CANCELLED)
                throw cancelled
            } catch (error: Exception) {
                Timber.tag(TAG).e(error, "Local library scan failed")
                reportException(error)
                _progress.value = _progress.value.copy(
                    phase = LocalScanPhase.FAILED,
                    errorMessage = error.message ?: "Scan failed",
                )
            }
        }
    }

    private companion object {
        const val TAG = "LocalLibraryScanEngine"
        const val AUTO_SCAN_DEBOUNCE_MS = 5 * 60 * 1000L
    }
}

