package com.example.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("soundbox_settings", Context.MODE_PRIVATE)

    private val _themeFlow = MutableStateFlow(prefs.getString("theme", "DARK") ?: "DARK")
    val themeFlow: StateFlow<String> = _themeFlow

    private val _visibleTabsFlow = MutableStateFlow(getVisibleTabs())
    val visibleTabsFlow: StateFlow<Set<String>> = _visibleTabsFlow

    private val _searchHistoryFlow = MutableStateFlow(getSearchHistory())
    val searchHistoryFlow: StateFlow<List<String>> = _searchHistoryFlow

    private val _crossfadeSeconds = MutableStateFlow(prefs.getInt("crossfade_sec", 3))
    val crossfadeSeconds: StateFlow<Int> = _crossfadeSeconds

    private val _gaplessPlayback = MutableStateFlow(prefs.getBoolean("gapless_playback", true))
    val gaplessPlayback: StateFlow<Boolean> = _gaplessPlayback

    private val _replayGainMode = MutableStateFlow(prefs.getString("replay_gain", "TRACK") ?: "TRACK")
    val replayGainMode: StateFlow<String> = _replayGainMode

    private val _hiResAudioEngine = MutableStateFlow(prefs.getBoolean("hi_res_engine", true))
    val hiResAudioEngine: StateFlow<Boolean> = _hiResAudioEngine

    private val _keepScreenOn = MutableStateFlow(prefs.getBoolean("keep_screen_on", false))
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn

    private val _hapticFeedback = MutableStateFlow(prefs.getBoolean("haptic_feedback", true))
    val hapticFeedback: StateFlow<Boolean> = _hapticFeedback

    private val _visualizerStyle = MutableStateFlow(prefs.getString("visualizer_style", "WAVEFORM") ?: "WAVEFORM")
    val visualizerStyle: StateFlow<String> = _visualizerStyle

    private val _visualizerEnabled = MutableStateFlow(prefs.getBoolean("visualizer_enabled", true))
    val visualizerEnabled: StateFlow<Boolean> = _visualizerEnabled

    private val _autoPauseOnHeadphoneUnplug = MutableStateFlow(prefs.getBoolean("auto_pause_headphone", true))
    val autoPauseOnHeadphoneUnplug: StateFlow<Boolean> = _autoPauseOnHeadphoneUnplug

    private val _autoResumeOnHeadphonePlug = MutableStateFlow(prefs.getBoolean("auto_resume_headphone", false))
    val autoResumeOnHeadphonePlug: StateFlow<Boolean> = _autoResumeOnHeadphonePlug

    private val _dynamicThemeFromAlbumArt = MutableStateFlow(prefs.getBoolean("dynamic_album_art_theme", true))
    val dynamicThemeFromAlbumArt: StateFlow<Boolean> = _dynamicThemeFromAlbumArt

    fun setAutoPauseOnHeadphoneUnplug(enabled: Boolean) {
        prefs.edit().putBoolean("auto_pause_headphone", enabled).apply()
        _autoPauseOnHeadphoneUnplug.value = enabled
    }

    fun setAutoResumeOnHeadphonePlug(enabled: Boolean) {
        prefs.edit().putBoolean("auto_resume_headphone", enabled).apply()
        _autoResumeOnHeadphonePlug.value = enabled
    }

    fun setDynamicThemeFromAlbumArt(enabled: Boolean) {
        prefs.edit().putBoolean("dynamic_album_art_theme", enabled).apply()
        _dynamicThemeFromAlbumArt.value = enabled
    }

    fun setCrossfadeSeconds(seconds: Int) {
        prefs.edit().putInt("crossfade_sec", seconds).apply()
        _crossfadeSeconds.value = seconds
    }

    fun setGaplessPlayback(enabled: Boolean) {
        prefs.edit().putBoolean("gapless_playback", enabled).apply()
        _gaplessPlayback.value = enabled
    }

    fun setReplayGainMode(mode: String) {
        prefs.edit().putString("replay_gain", mode).apply()
        _replayGainMode.value = mode
    }

    fun setHiResAudioEngine(enabled: Boolean) {
        prefs.edit().putBoolean("hi_res_engine", enabled).apply()
        _hiResAudioEngine.value = enabled
    }

    fun setKeepScreenOn(enabled: Boolean) {
        prefs.edit().putBoolean("keep_screen_on", enabled).apply()
        _keepScreenOn.value = enabled
    }

    fun setHapticFeedback(enabled: Boolean) {
        prefs.edit().putBoolean("haptic_feedback", enabled).apply()
        _hapticFeedback.value = enabled
    }

    fun setVisualizerStyle(style: String) {
        prefs.edit().putString("visualizer_style", style).apply()
        _visualizerStyle.value = style
    }

    fun setVisualizerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("visualizer_enabled", enabled).apply()
        _visualizerEnabled.value = enabled
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

    companion object {
        @Volatile
        private var INSTANCE: SettingsManager? = null

        fun getInstance(context: Context): SettingsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}

