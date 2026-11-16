/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ai

import android.content.Context
import com.convx.music.db.InternalDatabase
import com.convx.music.db.MusicDatabase
import com.convx.music.db.entities.PlaylistSong
import com.convx.music.db.entities.PlaylistSongMap
import com.convx.music.db.entities.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object AiPlaylistModifier {

    suspend fun modifyPlaylist(
        context: Context,
        playlistId: String,
        currentSongs: List<PlaylistSong>,
        userPrompt: String,
        onLog: suspend (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val database = InternalDatabase.newInstance(context)

        onLog("Connecting to AI Provider...")

        val currentPlaylistJson = currentSongs.toPlaylistJson()

        val systemPrompt = """
            You are a highly accurate and strict music historian. The user wants to modify an existing playlist based on a specific prompt.
            The user will provide an INSTRUCTION.
            The CURRENT playlist is:
            $currentPlaylistJson

            You must output ONLY a valid JSON object containing exactly two arrays: "remove_ids" (list of internal IDs to remove) and "additions" (list of objects with "title" and "artist").
            
            CRITICAL RULES:
            1. YOU MUST VERIFY THE RELEASE YEAR, MOVIE, AND ARTIST/ACTOR for EVERY SINGLE TRACK you add or remove.
            2. For removals, ONLY include the "id" from the CURRENT playlist that match the user's removal instruction.
            3. For additions, ONLY include songs that match the user's addition instruction.
            4. You MUST output ONLY raw JSON. Do NOT include any markdown formatting (like ```json), explanations, or conversational text.
            
            Example format:
            {
              "remove_ids": [123, 456],
              "additions": [
                {"title": "Song Name", "artist": "Artist Name"}
              ]
            }
        """.trimIndent()

        val jsonOutput = AiChatClient.complete(context, systemPrompt, userPrompt, onLog) ?: return@withContext

        onLog("Parsing AI response...")
        val cleanJsonStr = jsonOutput.replace("```json", "").replace("```", "").trim()

        val parsedJson = try {
            JSONObject(cleanJsonStr)
        } catch (e: Exception) {
            onLog("Failed to parse AI output.")
            return@withContext
        }

        val removeIds = parsedJson.optJSONArray("remove_ids")
        val additions = parsedJson.optJSONArray("additions")

        val totalToRemove = removeIds?.length() ?: 0
        val totalToAdd = additions?.length() ?: 0

        if (removeIds != null && totalToRemove > 0) {
            onLog("Removing $totalToRemove songs...")
            removeSongs(database, currentSongs, removeIds)
        }

        if (additions != null && totalToAdd > 0) {
            addSongs(database, playlistId, currentSongs, additions, onLog)
        }

        if (totalToRemove == 0 && totalToAdd == 0) {
            onLog("No modifications requested by AI.")
        } else {
            onLog("Done! Playlist modified.")
        }
    }

    private fun List<PlaylistSong>.toPlaylistJson(): String = JSONArray().apply {
        forEach { playlistSong ->
            put(JSONObject().apply {
                put("id", playlistSong.map.id)
                put("title", playlistSong.song.song.title)
                put("artist", playlistSong.song.artists.joinToString { it.name })
            })
        }
    }.toString()

    private suspend fun removeSongs(
        database: MusicDatabase,
        currentSongs: List<PlaylistSong>,
        removeIds: JSONArray
    ) {
        val mapsById = currentSongs.associate { it.map.id to it.map }

        for (index in 0 until removeIds.length()) {
            val id = removeIds.optInt(index, -1)
            if (id != -1) mapsById[id]?.let(database::delete)
        }
    }

    private suspend fun addSongs(
        database: MusicDatabase,
        playlistId: String,
        currentSongs: List<PlaylistSong>,
        additions: JSONArray,
        onLog: suspend (String) -> Unit
    ) {
        val librarySongs = database.allSearchableSongs().first()
        val resolvedSongs = mutableListOf<Song>()
        onLog("Matching ${additions.length()} suggested additions against your library...")

        for (index in 0 until additions.length()) {
            val item = additions.optJSONObject(index) ?: continue
            val title = item.optString("title")
            val artist = item.optString("artist")
            if (title.isEmpty()) continue

            val match = librarySongs.firstOrNull { song ->
                val titleMatch = song.song.title.equals(title, ignoreCase = true) ||
                    song.song.title.contains(title, ignoreCase = true)
                val artistMatch = artist.isBlank() || song.artists.any { it.name.contains(artist, ignoreCase = true) }
                titleMatch && artistMatch
            } ?: librarySongs.firstOrNull { it.song.title.equals(title, ignoreCase = true) }

            if (match != null) resolvedSongs.add(match)
        }

        if (resolvedSongs.isEmpty()) {
            onLog("No suggested additions matched songs in your local library.")
            return
        }

        onLog("Adding ${resolvedSongs.size} local songs to playlist...")
        val maxPosition = currentSongs.maxOfOrNull { it.map.position } ?: -1

        resolvedSongs.forEachIndexed { index, song ->
            database.insert(
                PlaylistSongMap(
                    playlistId = playlistId,
                    songId = song.id,
                    position = maxPosition + 1 + index
                )
            )
        }
    }
}
