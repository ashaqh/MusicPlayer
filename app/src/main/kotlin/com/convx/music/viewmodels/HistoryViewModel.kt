/**
 * Apprise Music (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.viewmodels

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.convx.music.constants.DataSaverEnabledKey
import com.convx.music.constants.HideVideoSongsKey
import com.convx.music.db.entities.EventWithSong
import com.convx.music.history.HistoryLocalDataSource
import com.convx.music.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val historyDataSource: HistoryLocalDataSource,
) : ViewModel() {
    private val today = LocalDate.now()
    private val thisMonday = today.with(DayOfWeek.MONDAY)
    private val lastMonday = thisMonday.minusDays(7)

    val searchQuery = MutableStateFlow("")
    private val _events = MutableStateFlow<Map<DateAgo, List<EventWithSong>>>(emptyMap())
    val filteredEvents = _events.asStateFlow()
    private val _flatEvents = MutableStateFlow<List<EventWithSong>>(emptyList())
    val filteredFlatEvents = _flatEvents.asStateFlow()
    private var pageOffset = 0
    private var reachedEnd = false
    private var hideVideoSongs = false
    private var localLoadJob: kotlinx.coroutines.Job? = null

    private fun groupEvents(events: List<EventWithSong>) =
        events.groupBy {
            val date = it.event.timestamp.toLocalDate()
            val daysAgo = ChronoUnit.DAYS.between(date, today).toInt()
            when {
                daysAgo == 0 -> DateAgo.Today
                daysAgo == 1 -> DateAgo.Yesterday
                date >= thisMonday -> DateAgo.ThisWeek
                date >= lastMonday -> DateAgo.LastWeek
                else -> DateAgo.Other(date.withDayOfMonth(1))
            }
        }.toSortedMap(compareBy { dateAgo ->
            when (dateAgo) {
                DateAgo.Today -> 0L
                DateAgo.Yesterday -> 1L
                DateAgo.ThisWeek -> 2L
                DateAgo.LastWeek -> 3L
                is DateAgo.Other -> ChronoUnit.DAYS.between(dateAgo.date, today)
            }
        }).mapValues { (_, items) -> items.distinctBy { it.song.id } }

    fun loadMoreEvents() {
        if (reachedEnd || localLoadJob?.isActive == true) return
        localLoadJob = viewModelScope.launch {
            val page = historyDataSource.loadEvents(PAGE_SIZE, pageOffset, hideVideoSongs, searchQuery.value)
            pageOffset += page.size
            reachedEnd = page.size < PAGE_SIZE
            if (page.isNotEmpty()) {
                val combined = (_flatEvents.value + page).distinctBy { it.event.id }
                _flatEvents.value = combined
                _events.value = groupEvents(combined)
            }
        }
    }

    private fun resetLocalHistory(hideVideos: Boolean, query: String) {
        localLoadJob?.cancel()
        hideVideoSongs = hideVideos
        pageOffset = 0
        reachedEnd = false
        _flatEvents.value = emptyList()
        _events.value = emptyMap()
        localLoadJob = viewModelScope.launch {
            val page = historyDataSource.loadEvents(PAGE_SIZE, pageOffset, hideVideoSongs, query)
            pageOffset = page.size
            reachedEnd = page.size < PAGE_SIZE
            _flatEvents.value = page
            _events.value = groupEvents(page)
        }
    }

    init {
        viewModelScope.launch {
            val hideVideos = context.dataStore.data
                .map { (it[HideVideoSongsKey] ?: false) || (it[DataSaverEnabledKey] ?: false) }
                .distinctUntilChanged()
            combine(hideVideos, searchQuery) { hide, query -> hide to query }
                .distinctUntilChanged()
                .collect { (hide, query) -> resetLocalHistory(hide, query) }
        }
    }

    private companion object {
        const val PAGE_SIZE = 50
    }
}

@Immutable
sealed class DateAgo {
    @Immutable
    data object Today : DateAgo()

    @Immutable
    data object Yesterday : DateAgo()

    @Immutable
    data object ThisWeek : DateAgo()

    @Immutable
    data object LastWeek : DateAgo()

    @Immutable
    class Other(
        val date: LocalDate,
    ) : DateAgo() {
        override fun equals(other: Any?): Boolean {
            if (other is Other) return date == other.date
            return super.equals(other)
        }

        override fun hashCode(): Int = date.hashCode()
    }
}
