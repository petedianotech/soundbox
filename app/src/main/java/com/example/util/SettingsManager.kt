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
