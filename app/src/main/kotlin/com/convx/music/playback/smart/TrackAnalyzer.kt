/*
 * Based on Orchard's TrackAnalyzer (https://github.com/SFG5453/Orchard). Beat and vocal
 * analysis runs over the track head and tail, which transition logic reads.
 *
 * Copyright (C) 2026 Convx contributors
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.convx.music.playback.smart

import android.content.Context
import android.media.MediaDataSource
import android.net.Uri
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.util.UnstableApi
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CancellationException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.max

/** Produces cached track analysis for [TransitionPlanner] without blocking playback. */
@UnstableApi
class TrackAnalyzer(context: Context) {

    private val appContext = context.applicationContext

    private val power = context.applicationContext.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager

    /**
     * Analysis is CPU and model heavy; a warm phone or Battery Saver should not be asked for more.
     * The first measurement a transition is waiting on only yields when the phone is already hot;
     * the minutes-long refinement also yields to Battery Saver and mild throttling.
     */
    private fun deferred(refine: Boolean): Boolean {
        val thermal = if (android.os.Build.VERSION.SDK_INT >= 29) power.currentThermalStatus else 0
        return if (refine) power.isPowerSaveMode || thermal >= android.os.PowerManager.THERMAL_STATUS_MODERATE
        else thermal >= android.os.PowerManager.THERMAL_STATUS_SEVERE
    }

    private val trackerDelegate = lazy { BeatTracker(context.applicationContext) }
    private val vocalsDelegate = lazy { VocalTracker(context.applicationContext) }
    private val tracker by trackerDelegate
    private val vocals by vocalsDelegate
    private val lifecycleLock = Any()
    private val analysisRevision = AtomicLong()
    @Volatile private var released = false
    private val activeRevision = ThreadLocal<Long>()

    private val results = ConcurrentHashMap<String, TrackAnalysis>()
    private val running = ConcurrentHashMap<String, Long>()
    private val retryAfter = ConcurrentHashMap<String, Long>()

    /** Tracks already looked for on disk this session; see [restoreOnce]. */
    private val restoreAttempted = ConcurrentHashMap.newKeySet<String>()

    private fun lane(name: String, threadPriority: Int) = ThreadPoolExecutor(
        1, 1, 30L, TimeUnit.SECONDS, LinkedBlockingQueue<Runnable>(),
    ) { runnable ->
        Thread({
            Process.setThreadPriority(threadPriority)
            runnable.run()
        }, name).apply { isDaemon = true }
    }.apply { allowCoreThreadTimeOut(true) }

    /** First measurement of a track; a transition is waiting on this one. */
    private val headExecutor = lane("convx-smart-analysis", Process.THREAD_PRIORITY_BACKGROUND)

    /** Whole-track refinement of a track that already has a result; minutes long and never urgent. */
    private val refineExecutor = lane("convx-smart-refine", Process.THREAD_PRIORITY_LOWEST)

    /** Results that survive the process, so a track is measured once and stays measured. */
    private val store = AnalysisStore(context)

    fun analysisFor(trackId: String): TrackAnalysis = results[trackId] ?: TrackAnalysis(trackId = trackId)

    private fun restoreOnce(trackId: String, uri: Uri? = null) {
        if (results.containsKey(trackId) || !restoreAttempted.add(trackId)) return
        val fingerprint = uri?.takeIf(::isDeviceFile)?.let { { SongFingerprint.of(appContext, it) } }
        store.load(trackId, fingerprint)?.let { results.putIfAbsent(trackId, it) }
    }

    fun isAnalysed(trackId: String): Boolean = results.containsKey(trackId)

    fun isAnalysing(trackId: String): Boolean = running.containsKey(trackId)

    fun request(trackId: String, uri: Uri, durationSeconds: Double) {
        if (trackId.isBlank() || !isDeviceFile(uri)) return
        if (results[trackId]?.isUsable == true) return
        if (deferred(refine = results.containsKey(trackId))) return
        synchronized(lifecycleLock) {
            if (released) return
            val now = SystemClock.elapsedRealtime()
            if (now < (retryAfter[trackId] ?: 0L)) return
            val revision = analysisRevision.get()
            if (running.putIfAbsent(trackId, revision) != null) return
            retryAfter[trackId] = now + REQUEST_RETRY_MS
            val executor = if (results.containsKey(trackId)) refineExecutor else headExecutor
            executor.execute {
                activeRevision.set(revision)
                try {
                    checkWorkCurrent()
                    analyzeRequest(trackId, uri, durationSeconds)
                } catch (_: CancellationException) {
                    // Cancellation does not turn a pending analysis into a failed result.
                } finally {
                    synchronized(lifecycleLock) {
                        if (running.remove(trackId, revision) && !released &&
                            revision == analysisRevision.get()
                        ) retryAfter[trackId] = SystemClock.elapsedRealtime() + REQUEST_RETRY_MS
                    }
                    if (running.isEmpty()) releaseModels()
                }
            }
        }
    }

    private fun analyzeRequest(trackId: String, uri: Uri, durationSeconds: Double) {
        try {
            restoreOnce(trackId, uri)
            checkWorkCurrent()
            val recorded = results[trackId]
            if (recorded != null) return
            if (!isDeviceFile(uri)) return

            checkWorkCurrent()
            val outcome = analyze(trackId, uri, durationSeconds)
            checkWorkCurrent()
            val whole = outcome.analysis
            if (whole != null) {
                results[trackId] = whole
                store.save(trackId, whole, SongFingerprint.of(appContext, uri))
                restoreAttempted.add(trackId)
            } else if (outcome.decodedShort) {
                results[trackId] = empty(trackId, durationSeconds)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            if (!isWorkCurrent()) return
            Log.w(TAG, "Analysis of $trackId failed", error)
            results[trackId] = empty(trackId, durationSeconds)
        }
    }

    /** Replaces a track's vocal mask with the one measured from its stem, and stores it. */
    fun applyStemMask(trackId: String) {
        restoreOnce(trackId)
        val current = results[trackId] ?: return
        val mask = StemVocalMask.forTrack(appContext, trackId, current.energyCurve) ?: return
        val updated = current.copy(vocalActivityMask = mask)
        results[trackId] = updated
        store.save(trackId, updated)
    }

    fun cancelPending() {
        synchronized(lifecycleLock) {
            if (released) return
            analysisRevision.incrementAndGet()
            headExecutor.queue.clear()
            refineExecutor.queue.clear()
            running.clear()
            retryAfter.clear()
        }
    }

    private fun isWorkCurrent(): Boolean = !released && activeRevision.get() == analysisRevision.get()

    private fun checkWorkCurrent() {
        if (!isWorkCurrent()) throw CancellationException("Track analysis cancelled")
    }

    private fun releaseModels() {
        if (trackerDelegate.isInitialized()) tracker.release()
        if (vocalsDelegate.isInitialized()) vocals.release()
    }

    /** Separates a short decode (a failed read) from missing audio. */
    private class WholeTrack(val analysis: TrackAnalysis?, val decodedShort: Boolean = false)

    /** Keeps short-decode failures distinct from missing audio during Pass 1. */
    private class Structural(
        val features: TrackFeatures.Features?,
        val decodedShort: Boolean = false,
    )

    private fun structure(
        trackId: String,
        openSource: () -> MediaDataSource?,
        effectiveDuration: Double,
    ): Structural {
        checkWorkCurrent()
        val structRate = TrackFeatures.sampleRate
        val decoded = openSource()?.use { AudioDecoder.decodeRegion(it, 0.0, effectiveDuration, ::isWorkCurrent) }
            ?: return Structural(null)
        checkWorkCurrent()
        val (pcm, _) = decoded

        // A short decode can look like trailing silence and place the mix-out point too early.
        val decodedSeconds = if (pcm.sampleRate > 0) pcm.samples.size / pcm.sampleRate else 0.0
        if (decodedSeconds < effectiveDuration * MIN_DECODED_FRACTION) {
            Log.w(
                TAG,
                "Analysis of $trackId refused: decoded " +
                    "${"%.1f".format(decodedSeconds)}s of a ${"%.1f".format(effectiveDuration)}s container",
            )
            return Structural(null, decodedShort = true)
        }

        val samples = if (abs(pcm.sampleRate - structRate) > 1.0) {
            TrackFeatures.resample(pcm.samples, pcm.sampleRate, structRate) ?: return Structural(null)
        } else {
            pcm.samples
        }

        return Structural(TrackFeatures.analyze(samples, effectiveDuration))
    }

    /** Only device files (`content://` / `file://`) are analyzed locally. */
    private fun isDeviceFile(uri: Uri): Boolean = uri.scheme == "content" || uri.scheme == "file"

    private fun analyze(trackId: String, uri: Uri, durationSeconds: Double): WholeTrack {
        checkWorkCurrent()
        if (!isDeviceFile(uri)) return WholeTrack(null)
        fun openSource(): MediaDataSource? = UriMediaDataSource.open(appContext, uri)

        var effectiveDuration = durationSeconds
        if (!effectiveDuration.isFinite() || effectiveDuration <= 0) {
            effectiveDuration = openSource()?.use(AudioDecoder::containerDurationSeconds) ?: 0.0
        }
        if (effectiveDuration <= 0) {
            Log.d(TAG, "Skipping $trackId: local media has no duration")
            return WholeTrack(empty(trackId, 0.0))
        }

        // Release structural decode buffers before allocating the model buffers.
        val structural = structure(trackId, ::openSource, effectiveDuration)
        checkWorkCurrent()
        if (structural.decodedShort) return WholeTrack(null, decodedShort = true)
        val features = structural.features ?: return WholeTrack(empty(trackId, effectiveDuration))

        // Transitions use only the outgoing tail and incoming head, so analyze those windows only.
        // Each window is decoded once and shared by beat and vocal analysis.
        val window = BeatTracker.WINDOW_SECONDS
        val tailStart = max(0.0, effectiveDuration - window)
        val head = region(::openSource, 0.0, minOf(window, effectiveDuration), features)
        val tail = if (tailStart > window / 2) region(::openSource, tailStart, effectiveDuration, features) else null

        val headGrid = head?.grid
        val tailGrid = tail?.grid

        // The tail governs where the outgoing track is mixed out, so it takes precedence; the
        // head is what a track uses when it is the *incoming* side of a different transition.
        val leading = tailGrid ?: headGrid

        Log.d(
            TAG,
            "Analysed $trackId: bpm=${leading?.bpm ?: features.bpm} " +
                "conf=${leading?.beatConfidence ?: features.beatConfidence} " +
                "key=${features.key} contentEnd=${features.contentEndTime} " +
                "mixOutCandidates=${features.mixOutCandidates.size} " +
                "vocalMask=${if (head?.vocalMask != null || tail?.vocalMask != null) "model" else "dsp"}",
        )

        return WholeTrack(
                TrackAnalysis(
                    status = TrackAnalysis.STATUS_READY,
                    trackId = trackId,
                    duration = effectiveDuration,
                    contentEndTime = features.contentEndTime.takeIf { it > 0 } ?: effectiveDuration,
                    bpm = leading?.bpm ?: features.bpm,
                beatInterval = leading?.beatInterval ?: features.beatInterval,
                beatConfidence = leading?.beatConfidence ?: features.beatConfidence,
                downbeats = (headGrid?.downbeats.orEmpty() + tailGrid?.downbeats.orEmpty())
                    .ifEmpty { features.downbeats }
                    .sorted(),
                firstBeat = headGrid?.firstBeat ?: features.firstBeat,
                phraseBoundaries = features.phraseBoundaries,
                key = features.key,
                keyConfidence = features.keyConfidence,
                audibleStartTime = features.audibleStartTime,
                pickupTime = features.pickupTime,
                introEndTime = features.introEndTime,
                outroStartTime = features.outroStartTime,
                mixInTime = features.mixInTime,
                mixOutTime = features.mixOutTime,
                mixInCandidates = features.mixInCandidates,
                mixOutCandidates = features.mixOutCandidates,
                energyCurve = features.energyCurve,
                lowEnergyCurve = features.lowEnergyCurve,
                // Keep DSP estimates wherever the model has no measurements.
                vocalActivityMask = StemVocalMask.forTrack(appContext, trackId, features.energyCurve)
                    ?: mergeMasks(features.energyCurve.size, head?.vocalMask, tail?.vocalMask)
                    ?: features.vocalActivityMask,
                vocalProbability = features.vocalProbability,
            ),
        )
    }

    /**
     * Everything a decoded region contributes, once its audio has been let go
     * of. [seconds] is what was actually decoded, which for a partially cached
     * file is not what was asked for.
     */
    private class Region(
        val grid: BeatTracker.Grid?,
        val vocalMask: DoubleArray?,
        val seconds: Double,
        /** Only populated when the caller asked for it; see [region]'s `deriveFeatures`. */
        val features: TrackFeatures.Features? = null,
    )

    /** Decodes one window; null means decoding failed or returned under one second. */
    private fun region(
        openSource: () -> MediaDataSource?,
        startSeconds: Double,
        endSeconds: Double,
        features: TrackFeatures.Features?,
        deriveFeatures: Boolean = false,
    ): Region? {
        checkWorkCurrent()
        val decoded = openSource()?.use { AudioDecoder.decodeRegionStereo(it, startSeconds, endSeconds, ::isWorkCurrent) }
            ?: run {
                Log.d(TAG, "Region [$startSeconds, $endSeconds) would not open")
                return null
            }
        checkWorkCurrent()
        val (stereo, actualStart) = decoded
        if (stereo.left.size < stereo.sampleRate) {
            Log.d(TAG, "Region [$startSeconds, $endSeconds) decoded ${stereo.left.size} frames; too few")
            return null
        }

        val seconds = stereo.left.size / stereo.sampleRate
        // Keep the large mono downmix inside this helper so it can be collected before inference.
        val inputs = regionInputs(stereo, seconds, deriveFeatures)
        checkWorkCurrent()

        return Region(
            // The decoder may seek back to a sync sample; beat times use the actual start.
            grid = inputs.modelInput?.let { tracker.track(it, offsetSeconds = actualStart) },
            vocalMask = features?.let { vocalMask(stereo, it, actualStart) },
            seconds = seconds,
            features = inputs.features,
        )
    }

    private class RegionInputs(val modelInput: FloatArray?, val features: TrackFeatures.Features?)

    /** Builds the model input and optional DSP features from one mono downmix. */
    private fun regionInputs(
        stereo: AudioDecoder.StereoPcm,
        seconds: Double,
        deriveFeatures: Boolean,
    ): RegionInputs {
        val mono = FloatArray(stereo.left.size) { index -> (stereo.left[index] + stereo.right[index]) * 0.5f }
        val modelInput = if (abs(stereo.sampleRate - MelSpectrogram.sampleRate) > 1.0) {
            MelSpectrogram.resample(mono, stereo.sampleRate, MelSpectrogram.sampleRate)
        } else {
            mono
        }

        val features = if (deriveFeatures) {
            val forFeatures = if (abs(stereo.sampleRate - TrackFeatures.sampleRate) > 1.0) {
                TrackFeatures.resample(mono, stereo.sampleRate, TrackFeatures.sampleRate)
            } else {
                mono
            }
            forFeatures?.let { TrackFeatures.analyze(it, seconds) }
        } else {
            null
        }

        return RegionInputs(modelInput, features)
    }

    /** Builds an energy-curve-sized mask; unmeasured points stay below the vocal activity threshold. */
    private fun vocalMask(
        stereo: AudioDecoder.StereoPcm,
        features: TrackFeatures.Features,
        actualStart: Double,
    ): DoubleArray? {
        val curve = features.energyCurve
        if (curve.isEmpty() || !VocalSpectrogram.available) return null

        // The vocal model has a fixed input size; leave two frames for resampling rounding.
        val maxSeconds = (VocalTracker.FIXED_FRAMES - 2) * VocalSpectrogram.hop / VocalSpectrogram.sampleRate
        val maxSamples = (maxSeconds * stereo.sampleRate).toInt().coerceAtMost(stereo.left.size)
        if (maxSamples <= 0) return null
        val left = if (maxSamples < stereo.left.size) stereo.left.copyOf(maxSamples) else stereo.left
        val right = if (maxSamples < stereo.right.size) stereo.right.copyOf(maxSamples) else stereo.right

        checkWorkCurrent()
        val values = vocals.track(left, right, stereo.sampleRate) ?: return null

        val mask = DoubleArray(curve.size) { NEUTRAL_VOCAL }
        for (index in curve.indices) {
            val frame = ((curve[index].time - actualStart) * VocalSpectrogram.frameRate).toInt()
            if (frame in values.indices) mask[index] = values[frame].toDouble()
        }
        return mask
    }

    /**
     * Overlays the head and tail masks onto one full-length curve, or null when neither ran —
     * which the caller answers by keeping the DSP heuristic rather than reporting a mask of
     * nothing but [NEUTRAL_VOCAL].
     */
    private fun mergeMasks(size: Int, head: DoubleArray?, tail: DoubleArray?): List<Double>? {
        if (size <= 0 || (head == null && tail == null)) return null
        val merged = DoubleArray(size) { NEUTRAL_VOCAL }
        for (source in listOfNotNull(head, tail)) {
            for (index in merged.indices) {
                if (index < source.size && source[index] != NEUTRAL_VOCAL) merged[index] = source[index]
            }
        }
        return merged.toList()
    }

    /** Recorded ready-but-empty so a track that cannot be decoded is not retried every tick. */
    private fun empty(trackId: String, durationSeconds: Double) = TrackAnalysis(
        status = TrackAnalysis.STATUS_READY,
        trackId = trackId,
        duration = durationSeconds,
    )

    fun release() {
        synchronized(lifecycleLock) {
            if (released) return
            released = true
            analysisRevision.incrementAndGet()
            headExecutor.queue.clear()
            refineExecutor.queue.clear()
            running.clear()
            retryAfter.clear()
            headExecutor.execute {
                releaseModels()
                results.clear()
                restoreAttempted.clear()
            }
            headExecutor.shutdown()
            refineExecutor.shutdown()
        }
    }

    private companion object {
        const val TAG = "ConvxTrackAnalyzer"
        const val REQUEST_RETRY_MS = 1000L

        /**
         * What an unmeasured instant reads as. Below the policy's VOCAL_ACTIVE_THRESHOLD by
         * design, so absence of measurement is never mistaken for absence of a vocal, or for the
         * presence of one.
         */
        const val NEUTRAL_VOCAL = 0.5

        /**
         * How much of the container's stated duration must actually decode
         * before the whole-track pass is trusted. Not 1.0: a decoder legitimately
         * comes up a frame or two short of the container's rounding, and
         * refusing over that would refuse everything.
         */
        const val MIN_DECODED_FRACTION = 0.95
    }
}
