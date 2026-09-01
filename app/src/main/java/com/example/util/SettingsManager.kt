package com.example.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("soundbox_settings", Context.MODE_PRIVATE)

    private val _themeFlow = MutableStateFlow(prefs.getString("theme", "SYSTEM") ?: "SYSTEM")
    val themeFlow: StateFlow<String> = _themeFlow

    private val _visibleTabsFlow = MutableStateFlow(getVisibleTabs())
    val visibleTabsFlow: StateFlow<Set<String>> = _visibleTabsFlow

    private val _searchHistoryFlow = MutableStateFlow(getSearchHistory())
    val searchHistoryFlow: StateFlow<List<String>> = _searchHistoryFlow

    private val _crossfadeSecondsFlow = MutableStateFlow(prefs.getInt("crossfade_seconds", 3))
    val crossfadeSecondsFlow: StateFlow<Int> = _crossfadeSecondsFlow

    private val _globalThumbnailIndexFlow = MutableStateFlow(prefs.getInt("global_thumbnail_index", -1))
    val globalThumbnailIndexFlow: StateFlow<Int> = _globalThumbnailIndexFlow

    private val _showSpectrumFlow = MutableStateFlow(prefs.getBoolean("show_spectrum", true))
    val showSpectrumFlow: StateFlow<Boolean> = _showSpectrumFlow

    private val _dynamicArtworkColorsFlow = MutableStateFlow(prefs.getBoolean("dynamic_artwork_colors", true))
    val dynamicArtworkColorsFlow: StateFlow<Boolean> = _dynamicArtworkColorsFlow

    private val _songThumbnailMapFlow = MutableStateFlow(loadSongThumbnailMap())
    val songThumbnailMapFlow: StateFlow<Map<String, Int>> = _songThumbnailMapFlow

    fun setCrossfadeSeconds(seconds: Int) {
        prefs.edit().putInt("crossfade_seconds", seconds).apply()
        _crossfadeSecondsFlow.value = seconds
    }

    fun setDynamicArtworkColors(enabled: Boolean) {
        prefs.edit().putBoolean("dynamic_artwork_colors", enabled).apply()
        _dynamicArtworkColorsFlow.value = enabled
    }

    fun setGlobalThumbnailIndex(index: Int) {
        prefs.edit().putInt("global_thumbnail_index", index).apply()
        _globalThumbnailIndexFlow.value = index
    }

    fun setShowSpectrum(show: Boolean) {
        prefs.edit().putBoolean("show_spectrum", show).apply()
        _showSpectrumFlow.value = show
    }

    fun setSongThumbnailIndex(songId: String, index: Int) {
        val current = _songThumbnailMapFlow.value.toMutableMap()
        if (index < 0) {
            current.remove(songId)
        } else {
            current[songId] = index
        }
        _songThumbnailMapFlow.value = current
        saveSongThumbnailMap(current)
    }

    private fun loadSongThumbnailMap(): Map<String, Int> {
        val str = prefs.getString("song_thumbnail_overrides", "") ?: ""
        if (str.isBlank()) return emptyMap()
        return try {
            str.split(";").mapNotNull {
                val parts = it.split(":")
                if (parts.size == 2) parts[0] to parts[1].toInt() else null
            }.toMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun saveSongThumbnailMap(map: Map<String, Int>) {
        val str = map.entries.joinToString(";") { "${it.key}:${it.value}" }
        prefs.edit().putString("song_thumbnail_overrides", str).apply()
    }

    fun setTheme(theme: String) {
        prefs.edit().putString("theme", theme).apply()
        _themeFlow.value = theme
    }

    fun toggleTabVisibility(tab: String, isVisible: Boolean) {
        val current = getVisibleTabs().toMutableSet()
        if (isVisible) {
            current.add(tab)
        } else {
            current.remove(tab)
        }
        prefs.edit().putStringSet("visible_tabs", current).apply()
        _visibleTabsFlow.value = current
    }

    private fun getVisibleTabs(): Set<String> {
        val defaultTabs = setOf("SONGS", "ALBUMS", "ARTISTS", "GENRES", "FOLDERS", "PLAYLISTS")
        return prefs.getStringSet("visible_tabs", defaultTabs) ?: defaultTabs
    }

    private fun getSearchHistory(): List<String> {
        val historyStr = prefs.getString("search_history", "") ?: ""
        if (historyStr.isBlank()) return emptyList()
        return historyStr.split("|||").filter { it.isNotBlank() }
    }

    fun addSearchQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        val current = getSearchHistory().toMutableList()
        current.remove(trimmed)
        current.add(0, trimmed)
        val updated = current.take(5)
        prefs.edit().putString("search_history", updated.joinToString("|||")).apply()
        _searchHistoryFlow.value = updated
    }

    fun removeSearchQuery(query: String) {
        val current = getSearchHistory().toMutableList()
        current.remove(query)
        prefs.edit().putString("search_history", current.joinToString("|||")).apply()
        _searchHistoryFlow.value = current
    }

    fun clearSearchHistory() {
        prefs.edit().remove("search_history").apply()
        _searchHistoryFlow.value = emptyList()
    }
}
