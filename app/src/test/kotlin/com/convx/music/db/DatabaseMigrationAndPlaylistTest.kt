package com.convx.music.db

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import kotlin.system.measureNanoTime

/**
 * Verifies Room v40 -> v41 non-destructive migration against exported Room JSON schemas,
 * playlist CRUD/reorder/duplicate/position compaction, FK cascade safety on moved tracks,
 * and batched SQLite scale benchmarks for 100 / 1,000 / 10,000 tracks.
 */
class DatabaseMigrationAndPlaylistTest {

    private fun openInMemorySqlite(): Connection {
        val conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        conn.createStatement().use { st ->
            st.execute("PRAGMA foreign_keys = ON")
        }
        return conn
    }

    private fun resolveSchemaFile(version: Int): File {
        val candidates = listOf(
            File("schemas/com.convx.music.db.InternalDatabase/$version.json"),
            File("app/schemas/com.convx.music.db.InternalDatabase/$version.json"),
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Could not locate Room schema $version.json from ${File(".").absolutePath}")
    }

    private fun applySchemaSql(conn: Connection, schemaFile: File) {
        val root = JSONObject(schemaFile.readText()).getJSONObject("database")
        val entities = root.getJSONArray("entities")
        conn.createStatement().use { st ->
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val tableName = entity.getString("tableName")
                val createSql = entity.getString("createSql").replace("\${TABLE_NAME}", tableName)
                st.execute(createSql)
                if (entity.has("indices")) {
                    val indices = entity.getJSONArray("indices")
                    for (j in 0 until indices.length()) {
                        val indexSql = indices.getJSONObject(j)
                            .getString("createSql")
                            .replace("\${TABLE_NAME}", tableName)
                        st.execute(indexSql)
                    }
                }
            }
            if (root.has("views")) {
                val views = root.getJSONArray("views")
                for (i in 0 until views.length()) {
                    val view = views.getJSONObject(i)
                    val viewName = view.getString("viewName")
                    val viewSql = view.getString("createSql").replace("\${VIEW_NAME}", viewName)
                    st.execute(viewSql)
                }
            }
        }
    }

    private fun executeMigration40To41(conn: Connection) {
        conn.createStatement().use { st ->
            val existingCols = HashSet<String>()
            st.executeQuery("PRAGMA table_info('song')").use { rs ->
                while (rs.next()) existingCols.add(rs.getString("name"))
            }
            if ("trackNumber" !in existingCols) st.execute("ALTER TABLE `song` ADD COLUMN `trackNumber` INTEGER DEFAULT NULL")
            if ("discNumber" !in existingCols) st.execute("ALTER TABLE `song` ADD COLUMN `discNumber` INTEGER DEFAULT NULL")
            if ("fileSize" !in existingCols) st.execute("ALTER TABLE `song` ADD COLUMN `fileSize` INTEGER DEFAULT NULL")
            if ("mimeType" !in existingCols) st.execute("ALTER TABLE `song` ADD COLUMN `mimeType` TEXT DEFAULT NULL")
            if ("localPath" !in existingCols) st.execute("ALTER TABLE `song` ADD COLUMN `localPath` TEXT DEFAULT NULL")

            st.execute("CREATE INDEX IF NOT EXISTS `index_song_isLocal` ON `song` (`isLocal`)")
            st.execute("CREATE INDEX IF NOT EXISTS `index_song_inLibrary` ON `song` (`inLibrary`)")
            st.execute("CREATE INDEX IF NOT EXISTS `index_song_title` ON `song` (`title`)")
            st.execute("CREATE INDEX IF NOT EXISTS `index_song_liked` ON `song` (`liked`)")
            st.execute("CREATE INDEX IF NOT EXISTS `index_song_totalPlayTime` ON `song` (`totalPlayTime`)")

            st.execute("CREATE INDEX IF NOT EXISTS `index_album_isLocal` ON `album` (`isLocal`)")
            st.execute("CREATE INDEX IF NOT EXISTS `index_album_inLibrary` ON `album` (`inLibrary`)")
            st.execute("CREATE INDEX IF NOT EXISTS `index_album_title` ON `album` (`title`)")
            st.execute("CREATE INDEX IF NOT EXISTS `index_artist_isLocal` ON `artist` (`isLocal`)")
            st.execute("CREATE INDEX IF NOT EXISTS `index_artist_name` ON `artist` (`name`)")
            st.execute("CREATE INDEX IF NOT EXISTS `index_playlist_isLocal` ON `playlist` (`isLocal`)")
            st.execute("CREATE INDEX IF NOT EXISTS `index_playlist_browseId` ON `playlist` (`browseId`)")
            st.execute("CREATE INDEX IF NOT EXISTS `index_playlist_name` ON `playlist` (`name`)")
            st.execute("CREATE INDEX IF NOT EXISTS `index_playlist_song_map_playlistId_position` ON `playlist_song_map` (`playlistId`, `position`)")
        }
    }

    @Test
    fun `migration 40 to 41 preserves all existing user data and creates required indexes`() {
        val schema40 = resolveSchemaFile(40)
        val schema41 = resolveSchemaFile(41)
        val expected41 = JSONObject(schema41.readText()).getJSONObject("database").getJSONArray("entities")

        openInMemorySqlite().use { conn ->
            applySchemaSql(conn, schema40)

            // Seed v40 user data: album, artist, song, playlist, playlist_song_map, event
            conn.createStatement().use { st ->
                st.execute("INSERT INTO album(id, title, songCount, duration, lastUpdateTime, isLocal) VALUES ('alb1', 'Keep Me', 1, 180, 1000, 1)")
                st.execute("INSERT INTO artist(id, name, lastUpdateTime, isLocal) VALUES ('art1', 'Local Artist', 1000, 1)")
                st.execute("INSERT INTO song(id, title, duration, albumId, albumName, liked, totalPlayTime, inLibrary, isLocal) VALUES ('content://media/external/audio/media/1', 'Preserved Track', 180, 'alb1', 'Keep Me', 1, 90000, 1700000000, 1)")
                st.execute("INSERT INTO playlist(id, name, isLocal, isEditable) VALUES ('pl1', 'Road Trip', 1, 1)")
                st.execute("INSERT INTO playlist_song_map(playlistId, songId, position) VALUES ('pl1', 'content://media/external/audio/media/1', 0)")
                st.execute("INSERT INTO event(songId, timestamp, playTime) VALUES ('content://media/external/audio/media/1', 1700005000, 180000)")
            }

            // Run exact MIGRATION_40_41 DDL statements
            executeMigration40To41(conn)

            // Assert existing user data survived intact
            conn.createStatement().use { st ->
                st.executeQuery("SELECT title, liked, totalPlayTime, trackNumber, discNumber, fileSize, mimeType, localPath FROM song WHERE id = 'content://media/external/audio/media/1'").use { rs ->
                    assertTrue(rs.next())
                    assertEquals("Preserved Track", rs.getString("title"))
                    assertEquals(1, rs.getInt("liked"))
                    assertEquals(90_000L, rs.getLong("totalPlayTime"))
                    assertEquals(null, rs.getObject("trackNumber"))
                    assertEquals(null, rs.getObject("mimeType"))
                }
                st.executeQuery("SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = 'pl1'").use { rs ->
                    assertTrue(rs.next())
                    assertEquals(1, rs.getInt(1))
                }
                st.executeQuery("SELECT COUNT(*) FROM event WHERE songId = 'content://media/external/audio/media/1'").use { rs ->
                    assertTrue(rs.next())
                    assertEquals(1, rs.getInt(1))
                }
            }

            // Assert every index declared in 41.json exists on the migrated SQLite tables
            for (i in 0 until expected41.length()) {
                val entity = expected41.getJSONObject(i)
                val table = entity.getString("tableName")
                if (!entity.has("indices")) continue
                val actualIndexes = HashSet<String>()
                conn.createStatement().use { st ->
                    st.executeQuery("PRAGMA index_list('$table')").use { rs ->
                        while (rs.next()) actualIndexes.add(rs.getString("name"))
                    }
                }
                val expectedIndexes = entity.getJSONArray("indices")
                for (j in 0 until expectedIndexes.length()) {
                    val idxName = expectedIndexes.getJSONObject(j).getString("name")
                    assertTrue("Missing index $idxName on $table after MIGRATION_40_41", idxName in actualIndexes)
                }
            }
        }
    }

    @Test
    fun `playlist supports duplicates, rename, remove compaction, reorder, and moved song remap`() {
        val schema41 = resolveSchemaFile(41)
        openInMemorySqlite().use { conn ->
            applySchemaSql(conn, schema41)

            conn.createStatement().use { st ->
                st.execute("INSERT INTO song(id, title, duration, liked, totalPlayTime, isLocal) VALUES ('s1', 'One', 100, 1, 5000, 1)")
                st.execute("INSERT INTO song(id, title, duration, liked, totalPlayTime, isLocal) VALUES ('s2', 'Two', 200, 0, 1000, 1)")
                st.execute("INSERT INTO song(id, title, duration, liked, totalPlayTime, isLocal) VALUES ('s3', 'Three', 150, 0, 0, 1)")
                st.execute("INSERT INTO playlist(id, name, isLocal, isEditable) VALUES ('p1', 'Workout', 1, 1)")
                // Allow duplicate track 's1' at positions 0 and 2
                st.execute("INSERT INTO playlist_song_map(playlistId, songId, position) VALUES ('p1', 's1', 0), ('p1', 's2', 1), ('p1', 's1', 2), ('p1', 's3', 3)")
                st.execute("INSERT INTO event(songId, timestamp, playTime) VALUES ('s1', 1700001000, 100000)")
            }

            // Rename playlist
            conn.createStatement().use { it.execute("UPDATE playlist SET name = 'Running Mix', lastUpdateTime = 9999 WHERE id = 'p1'") }

            // Reorder position 3 ('s3') -> position 1 (mirrors DatabaseDao.move)
            conn.createStatement().use { st ->
                val from = 3
                val to = 1
                st.execute(
                    """UPDATE playlist_song_map SET position =
                        CASE WHEN position < $from THEN position + 1 WHEN position > $from THEN position - 1 ELSE $to END
                        WHERE playlistId = 'p1' AND position BETWEEN MIN($from, $to) AND MAX($from, $to)"""
                )
            }

            // Verify order after move: [s1@0, s3@1, s2@2, s1@3]
            val afterMove = queryPlaylistOrder(conn, "p1")
            assertEquals(listOf("s1", "s3", "s2", "s1"), afterMove)

            // Remove item at position 1 ('s3') via move-to-end + delete + compact
            conn.createStatement().use { st ->
                st.execute("DELETE FROM playlist_song_map WHERE playlistId = 'p1' AND position = 1")
                st.execute("UPDATE playlist_song_map SET position = position - 1 WHERE playlistId = 'p1' AND position > 1")
            }
            assertEquals(listOf("s1", "s2", "s1"), queryPlaylistOrder(conn, "p1"))

            // Next appended song uses MAX(position) + 1 (= 3) cleanly
            conn.createStatement().use { st ->
                var nextPos = 0
                st.executeQuery("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_song_map WHERE playlistId = 'p1'").use { rs ->
                    rs.next()
                    nextPos = rs.getInt(1)
                }
                assertEquals(3, nextPos)
                st.execute("INSERT INTO playlist_song_map(playlistId, songId, position) VALUES ('p1', 's3', $nextPos)")
            }
            assertEquals(listOf("s1", "s2", "s1", "s3"), queryPlaylistOrder(conn, "p1"))

            // Moved file remap preserves playlist_song_map and event despite CASCADE FK on old ID delete
            conn.createStatement().use { st ->
                st.execute("INSERT INTO song(id, title, duration, liked, totalPlayTime, isLocal) VALUES ('s1_moved', 'One', 100, 1, 5000, 1)")
                st.execute("UPDATE playlist_song_map SET songId = 's1_moved' WHERE songId = 's1'")
                st.execute("UPDATE event SET songId = 's1_moved' WHERE songId = 's1'")
                st.execute("DELETE FROM song WHERE id = 's1'")
            }
            assertEquals(listOf("s1_moved", "s2", "s1_moved", "s3"), queryPlaylistOrder(conn, "p1"))
            conn.createStatement().use { st ->
                st.executeQuery("SELECT COUNT(*) FROM event WHERE songId = 's1_moved'").use { rs ->
                    assertTrue(rs.next())
                    assertEquals(1, rs.getInt(1))
                }
            }
        }
    }

    @Test
    fun `benchmarks batched sqlite inserts and paginated queries at 100, 1000, and 10000 tracks`() {
        val schema41 = resolveSchemaFile(41)
        for (count in listOf(100, 1_000, 10_000)) {
            openInMemorySqlite().use { conn ->
                applySchemaSql(conn, schema41)

                val insertMs = measureNanoTime {
                    conn.autoCommit = false
                    conn.prepareStatement(
                        "INSERT INTO song(id, title, duration, albumId, albumName, liked, totalPlayTime, inLibrary, isLocal, trackNumber, discNumber, fileSize, mimeType, localPath) VALUES (?, ?, ?, ?, ?, 0, 0, ?, 1, ?, 1, ?, 'audio/flac', ?)",
                    ).use { ps ->
                        for (i in 0 until count) {
                            ps.setString(1, "content://media/external/audio/media/${i + 1}")
                            ps.setString(2, "Track %05d".format(i + 1))
                            ps.setInt(3, 180 + (i % 90))
                            ps.setString(4, "local_album_ms_${(i / 12) + 1}")
                            ps.setString(5, "Album ${(i / 12) + 1}")
                            ps.setLong(6, 1_700_000_000L + i)
                            ps.setInt(7, (i % 12) + 1)
                            ps.setLong(8, 8_000_000L + i)
                            ps.setString(9, "/storage/emulated/0/Music/Album${(i / 12) + 1}/t${i + 1}.flac")
                            ps.addBatch()
                            if ((i + 1) % 500 == 0) {
                                ps.executeBatch()
                                conn.commit()
                            }
                        }
                        ps.executeBatch()
                        conn.commit()
                    }
                    conn.autoCommit = true
                } / 1_000_000.0

                var firstPageCount = 0
                val pageQueryMs = measureNanoTime {
                    conn.prepareStatement(
                        "SELECT id, title FROM song WHERE isLocal = 1 ORDER BY title COLLATE NOCASE ASC LIMIT ? OFFSET ?",
                    ).use { ps ->
                        ps.setInt(1, 200)
                        ps.setInt(2, (count / 2).coerceAtMost((count - 1).coerceAtLeast(0)))
                        ps.executeQuery().use { rs ->
                            while (rs.next()) firstPageCount++
                        }
                    }
                } / 1_000_000.0

                assertTrue(firstPageCount in 1..200)
                println(
                    "[BENCHMARK][SQLite] tracks=$count batchedInsert500Ms=%.2f pagedLimit200QueryMs=%.2f"
                        .format(insertMs, pageQueryMs),
                )
            }
        }
    }

    private fun queryPlaylistOrder(conn: Connection, playlistId: String): List<String> {
        val list = mutableListOf<String>()
        conn.createStatement().use { st ->
            st.executeQuery("SELECT songId FROM playlist_song_map WHERE playlistId = '$playlistId' ORDER BY position ASC").use { rs ->
                while (rs.next()) list.add(rs.getString(1))
            }
        }
        return list
    }
}

