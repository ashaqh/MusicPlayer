/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ai

import android.content.Context
import androidx.media3.common.MediaItem
import com.convx.music.db.MusicDatabase
import com.convx.music.db.entities.PlaylistEntity
import com.convx.music.db.entities.PlaylistSongMap
import com.convx.music.db.entities.Song
import com.convx.music.db.entities.SongAnalysisEntity
import com.convx.music.extensions.toMediaItem
import com.convx.music.playback.audio.DjSetOrderer
import com.convx.music.playback.dj.BeatGrid
import com.convx.music.playback.dj.MusicalKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime

/** Maximum number of local songs included as AI candidates. */
private const val MAX_LIBRARY_CANDIDATES = 150

/** An AI-selected song and its position in a concert set. */
private data class ChosenSong(val title: String, val artist: String, val role: String)

/** Playback order for roles in a concert set. */
private val CONCERT_ROLES = listOf("opener", "rising", "peak", "closer")

data class AiDjSetResult(
    val playlistName: String,
    val orderedTitles: List<String>,
    val unmatchedTitles: List<String>,
    /** Description of how this set was generated. */
    val method: String,
    /** Song ids in playback order. */
    val orderedIds: List<String>,
    /** Resolved local library songs, keyed by id; excludes the seed song. */
    val songsById: Map<String, Song>,
    /** Local library song used as the seed, if any. */
    val seedSong: Song?,
)

/**
 * Builds a set from local library tracks using a prompt, seed song, or both.
 * Concert mode preserves the AI's role order, while other sets use [DjSetOrderer].
 * Saving is a separate operation.
 */
object AiDjSetBuilder {

    /** Assumed track length, used to estimate how many songs meet a duration target. */
    private const val ASSUMED_AVERAGE_SONG_SECONDS = 210

    /** Builds a set from a prompt, seed song, or both over local library songs. */
    suspend fun buildSet(
        context: Context,
        database: MusicDatabase,
        prompt: String?,
        seedSongId: String?,
        songCount: Int,
        /** Target set duration in seconds. When set, this determines the song count. */
        targetDurationSeconds: Long? = null,
        /** Keep the AI's role order for a concert set. */
        concertMode: Boolean = false,
        onLog: suspend (String) -> Unit,
    ): AiDjSetResult? = withContext(Dispatchers.IO) {
        val effectiveSongCount = targetDurationSeconds
            ?.let { (it / ASSUMED_AVERAGE_SONG_SECONDS).toInt().coerceAtLeast(1) }
            ?: songCount
        val trimmedPrompt = prompt?.trim().orEmpty()
        if (trimmedPrompt.isEmpty() && seedSongId.isNullOrBlank()) {
            onLog("Need a vibe or a song to start from.")
            return@withContext null
        }

        onLog(
            "Gathering local candidates... (prompt=${if (trimmedPrompt.isEmpty()) "none" else "\"$trimmedPrompt\""}, " +
                "seed=${seedSongId ?: "none"})"
        )
        val seedSong = if (!seedSongId.isNullOrBlank()) database.song(seedSongId).first() else null
        val librarySongs = database.allSearchableSongs().first()
            .filter { it.id != seedSongId }
        val related = findSeedRelatedSongs(seedSongId, seedSong, librarySongs, onLog)

        // Concert mode uses the seed artist as its theme when the prompt is blank.
        val conceptLabel = trimmedPrompt.ifBlank {
            seedSong?.artists?.firstOrNull()?.name ?: seedSong?.song?.title.orEmpty()
        }
        val useAi = trimmedPrompt.isNotEmpty() || (concertMode && conceptLabel.isNotBlank())

        val chosenSongs = mutableListOf<ChosenSong>()
        var usedAi = false

        if (useAi && librarySongs.isNotEmpty()) {
            onLog(
                if (concertMode) "Asking AI to design a concert set for \"$conceptLabel\"..."
                else "Asking AI to pick local songs for the vibe..."
            )
            val candidatePool = (related.values + librarySongs)
                .distinctBy { it.id }
                .take(MAX_LIBRARY_CANDIDATES)
            val poolJson = buildCandidatePool(candidatePool)

            val systemPrompt = buildAiPrompt(conceptLabel, poolJson, effectiveSongCount, concertMode)

            val response = AiChatClient.complete(context, systemPrompt, conceptLabel, onLog)
            chosenSongs += parseAiResponse(response, onLog)
            usedAi = chosenSongs.isNotEmpty()
            if (usedAi) {
                onLog("AI picked: " + chosenSongs.joinToString { "${it.title} — ${it.artist} [${it.role}]" })
            }
        }

        val resolved = LinkedHashMap<String, Song>()
        val roleById = mutableMapOf<String, String>()
        val unmatched = mutableListOf<String>()

        if (usedAi && chosenSongs.isNotEmpty()) {
            onLog("Matching ${chosenSongs.size} songs against your library...")
            val results = resolveChosenSongs(chosenSongs, related.values, librarySongs, onLog)
            results.forEach { (chosen, item) ->
                if (item != null && item.id != seedSongId) {
                    resolved[item.id] = item
                    roleById[item.id] = chosen.role
                } else {
                    unmatched += chosen.title
                }
            }
        } else {
            val fallbackPool = if (related.isNotEmpty()) related.values else librarySongs
            addRelatedSongs(resolved, fallbackPool, targetDurationSeconds, effectiveSongCount)
            onLog("Using ${resolved.size} local library songs.")
        }

        if (resolved.isEmpty() && seedSong == null) {
            onLog("No matching local songs found.")
            return@withContext null
        }

        val method = when {
            concertMode && usedAi -> "AI-designed local concert set for \"$conceptLabel\" (opener, rising, peak, closer)"
            usedAi && trimmedPrompt.isNotEmpty() -> "AI-curated from your local library"
            seedSong != null -> "Local tracks related to \"${seedSong.song.title}\""
            else -> "Local library mix"
        }

        onLog("Ordering for the mix...")
        val orderedIds = orderSongs(
            database = database,
            songIds = resolved.keys,
            roleById = roleById,
            concertMode = concertMode,
            seedSongId = seedSongId,
        )
        val seedTitle = seedSong?.song?.title

        val name = playlistName(
            concertMode = concertMode,
            conceptLabel = conceptLabel,
            prompt = trimmedPrompt,
            seedTitle = seedTitle,
        )

        onLog("Done! Built \"$name\" with ${orderedIds.size} songs.")
        AiDjSetResult(
            playlistName = name,
            orderedTitles = orderedIds.map { id -> resolved[id]?.song?.title ?: seedTitle.takeIf { id == seedSongId } ?: id },
            unmatchedTitles = unmatched,
            method = method,
            orderedIds = orderedIds,
            songsById = resolved,
            seedSong = seedSong,
        )
    }

    private fun addRelatedSongs(
        target: MutableMap<String, Song>,
        relatedSongs: Collection<Song>,
        targetDurationSeconds: Long?,
        songCount: Int,
    ) {
        if (targetDurationSeconds == null) {
            relatedSongs.take(songCount).forEach { target[it.id] = it }
            return
        }

        var totalSeconds = 0L
        for (song in relatedSongs) {
            if (totalSeconds >= targetDurationSeconds) break
            target[song.id] = song
            totalSeconds += song.song.duration.takeIf { it > 0 } ?: ASSUMED_AVERAGE_SONG_SECONDS
        }
    }

    private fun playlistName(
        concertMode: Boolean,
        conceptLabel: String,
        prompt: String,
        seedTitle: String?,
    ): String = when {
        concertMode -> "Concert: $conceptLabel"
        prompt.isNotEmpty() && seedTitle != null -> "AI DJ Mix: $seedTitle + $prompt"
        prompt.isNotEmpty() -> "AI DJ Mix: $prompt"
        seedTitle != null -> "AI DJ Mix: $seedTitle"
        else -> "AI DJ Mix"
    }.take(100)

    private suspend fun orderSongs(
        database: MusicDatabase,
        songIds: Collection<String>,
        roleById: Map<String, String>,
        concertMode: Boolean,
        seedSongId: String?,
    ): List<String> {
        val ids = songIds.toMutableList()
        if (seedSongId != null) {
            ids.remove(seedSongId)
            ids.add(0, seedSongId)
        }

        if (concertMode && roleById.isNotEmpty()) {
            return orderConcertSongs(ids, roleById, seedSongId)
        }

        val analysisRows = database.songAnalyses(ids, SongAnalysisEntity.CURRENT_ANALYSIS_VERSION)
        val analysisById = analysisRows.associate { row ->
            row.id to (
                BeatGrid(row.bpm, row.anchorBeatMs, row.bpmConfidence) to
                    MusicalKey(row.keyIndex, row.keyIsMinor, row.keyConfidence)
                )
        }
        return DjSetOrderer.order(ids, analysisById)
    }

    /** Keeps the AI's concert arc and appends songs with unrecognized or missing roles. */
    internal fun orderConcertSongs(
        ids: List<String>,
        roleById: Map<String, String>,
        seedSongId: String?,
    ): List<String> {
        // BPM and key ordering would break the concert arc chosen by the AI.
        val ordered = listOfNotNull(seedSongId) + CONCERT_ROLES.flatMap { role ->
            ids.filter { it != seedSongId && roleById[it] == role }
        }
        val orderedSet = ordered.toHashSet()
        return ordered + ids.filterNot(orderedSet::contains)
    }

    private suspend fun resolveChosenSongs(
        chosenSongs: List<ChosenSong>,
        relatedSongs: Collection<Song>,
        librarySongs: Collection<Song>,
        onLog: suspend (String) -> Unit,
    ): List<Pair<ChosenSong, Song?>> {
        val searchSpace = (relatedSongs + librarySongs).distinctBy { it.id }
        return chosenSongs.mapIndexed { index, chosen ->
            val match = searchSpace.firstOrNull { song ->
                val titleMatch = song.song.title.equals(chosen.title, ignoreCase = true) ||
                    song.song.title.contains(chosen.title, ignoreCase = true)
                val artistMatch = chosen.artist.isBlank() ||
                    song.artists.any { it.name.contains(chosen.artist, ignoreCase = true) }
                titleMatch && artistMatch
            } ?: searchSpace.firstOrNull { song ->
                song.song.title.equals(chosen.title, ignoreCase = true)
            }
            onLog("Matched [${index + 1}/${chosenSongs.size}]: ${chosen.title}")
            chosen to match
        }
    }

    private suspend fun findSeedRelatedSongs(
        seedSongId: String?,
        seedSong: Song?,
        librarySongs: List<Song>,
        onLog: suspend (String) -> Unit,
    ): LinkedHashMap<String, Song> {
        val related = LinkedHashMap<String, Song>()
        if (seedSongId.isNullOrBlank() || seedSong == null) return related

        val seedArtists = seedSong.artists.map { it.name.lowercase() }.toSet()
        val seedAlbum = seedSong.album?.id
        librarySongs.forEach { candidate ->
            if (candidate.id == seedSongId) return@forEach
            val sameArtist = candidate.artists.any { it.name.lowercase() in seedArtists }
            val sameAlbum = seedAlbum != null && candidate.album?.id == seedAlbum
            if (sameArtist || sameAlbum) {
                related[candidate.id] = candidate
            }
        }
        onLog("Found ${related.size} related tracks in your library.")
        return related
    }

    private fun buildAiPrompt(
        conceptLabel: String,
        poolJson: String,
        songCount: Int,
        concertMode: Boolean,
    ): String = if (concertMode) {
        """
            You are a concert-setlist designer arranging tracks from the user's local library for "$conceptLabel".
            ONLY pick songs from this candidate list:
            $poolJson

            Structure the set into these parts in order:
            - opener: 1 track that eases in
            - rising: a few tracks building energy
            - peak: highest energy tracks
            - closer: 1-2 tracks winding down

            Pick up to $songCount tracks using the exact title and artist from the candidate list.
            Output ONLY a JSON array of objects:
            [{"title": "...", "artist": "...", "role": "opener|rising|peak|closer"}].
            Do NOT include markdown formatting, explanations, or any other text.
        """.trimIndent()
    } else {
        """
            You are a DJ curating a set strictly from the user's local music library.
            Candidate songs (title/artist pairs):
            $poolJson

            Pick up to $songCount tracks ONLY from this candidate list that best match the requested vibe.
            Output ONLY a JSON array of objects: [{"title": "...", "artist": "..."}].
            Do NOT include markdown formatting, explanations, or any other text.
        """.trimIndent()
    }

    private fun buildCandidatePool(librarySongs: List<Song>): String =
        JSONArray().apply {
            librarySongs.forEach { song ->
                put(JSONObject().apply {
                    put("title", song.song.title)
                    put("artist", song.artists.firstOrNull()?.name.orEmpty())
                })
            }
        }.toString()

    private suspend fun parseAiResponse(
        response: String?,
        onLog: suspend (String) -> Unit,
    ): List<ChosenSong> {
        val chosenSongs = mutableListOf<ChosenSong>()
        if (response == null) return chosenSongs

        try {
            val json = response.replace("```json", "").replace("```", "").trim()
            val songs = JSONArray(json)
            for (index in 0 until songs.length()) {
                val song = songs.optJSONObject(index) ?: continue
                val title = song.optString("title")
                if (title.isBlank()) continue

                val role = song.optString("role").lowercase().takeIf { it in CONCERT_ROLES } ?: "peak"
                chosenSongs += ChosenSong(title, song.optString("artist"), role)
            }
        } catch (e: Exception) {
            onLog("Couldn't parse the AI response, falling back to local tracks.")
        }
        return chosenSongs
    }

    /** Saves a generated set as a playlist in the library. */
    suspend fun savePlaylist(database: MusicDatabase, result: AiDjSetResult): String = withContext(Dispatchers.IO) {
        val playlistId = PlaylistEntity.generatePlaylistId()
        database.insert(
            PlaylistEntity(
                id = playlistId,
                name = result.playlistName,
                bookmarkedAt = LocalDateTime.now(),
                lastUpdateTime = LocalDateTime.now(),
                thumbnailUrl = result.seedSong?.song?.thumbnailUrl ?: result.songsById.values.firstOrNull()?.song?.thumbnailUrl,
                isEditable = true,
            )
        )
        result.orderedIds.forEachIndexed { index, id ->
            database.insert(PlaylistSongMap(playlistId = playlistId, songId = id, position = index))
        }
        playlistId
    }
}

/** Converts the ordered set, including its local seed, into playable queue items. */
fun AiDjSetResult.toMediaItems(): List<MediaItem> = orderedIds.mapNotNull { id ->
    songsById[id]?.toMediaItem() ?: seedSong?.takeIf { it.song.id == id }?.toMediaItem()
}
