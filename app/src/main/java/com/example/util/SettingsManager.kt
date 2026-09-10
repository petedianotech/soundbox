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

    private val _crossfadeEnabled = MutableStateFlow(prefs.getBoolean("crossfade_enabled", false))
    val crossfadeEnabled: StateFlow<Boolean> = _crossfadeEnabled

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

    private val _autoResumeOnHeadphonePlug = MutableStateFlow(prefs.getBoolean("auto_resume_headphone", true))
    val autoResumeOnHeadphonePlug: StateFlow<Boolean> = _autoResumeOnHeadphonePlug

    private val _dynamicThemeFromAlbumArt = MutableStateFlow(prefs.getBoolean("dynamic_album_art_theme", true))
    val dynamicThemeFromAlbumArt: StateFlow<Boolean> = _dynamicThemeFromAlbumArt

    private val _songSortOrderFlow = MutableStateFlow(prefs.getString("song_sort_order", "NEWEST_FIRST") ?: "NEWEST_FIRST")
    val songSortOrderFlow: StateFlow<String> = _songSortOrderFlow

    fun setSongSortOrder(order: String) {
        prefs.edit().putString("song_sort_order", order).apply()
        _songSortOrderFlow.value = order
    }

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

    fun setCrossfadeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("crossfade_enabled", enabled).apply()
        _crossfadeEnabled.value = enabled
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
        val defaultTabs = setOf("SONGS", "ALBUMS", "ARTISTS", "GENRES", "PLAYLISTS")
        if (!prefs.contains("visible_tabs_v2")) {
            prefs.edit().putStringSet("visible_tabs", defaultTabs).putBoolean("visible_tabs_v2", true).apply()
            return defaultTabs
        }
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

    // --- Equalizer & DSP Settings Persistence ---
    fun isEqualizerEnabled(): Boolean = prefs.getBoolean("eq_enabled", true)
    fun setEqualizerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("eq_enabled", enabled).apply()
    }

    fun getEqualizerPresetName(): String = prefs.getString("eq_preset_name", "Flat") ?: "Flat"
    fun setEqualizerPresetName(name: String) {
        prefs.edit().putString("eq_preset_name", name).apply()
    }

    fun getEqualizerBandLevels(): List<Float> {
        val str = prefs.getString("eq_band_levels", null)
        if (str.isNullOrBlank()) {
            return listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        }
        return try {
            val list = str.split(",").mapNotNull { it.trim().toFloatOrNull() }
            if (list.size == 10) list else listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        } catch (e: Exception) {
            listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        }
    }

    fun setEqualizerBandLevels(levels: List<Float>) {
        val str = levels.joinToString(",") { it.toString() }
        prefs.edit().putString("eq_band_levels", str).apply()
    }

    fun getPreampGain(): Float = prefs.getFloat("eq_preamp_gain", 0f)
    fun setPreampGain(gain: Float) {
        prefs.edit().putFloat("eq_preamp_gain", gain).apply()
    }

    fun getBassBoostStrength(): Int = prefs.getInt("eq_bass_boost", 300)
    fun setBassBoostStrength(strength: Int) {
        prefs.edit().putInt("eq_bass_boost", strength).apply()
    }

    fun getTrebleGain(): Float = prefs.getFloat("eq_treble_gain", 0f)
    fun setTrebleGain(gain: Float) {
        prefs.edit().putFloat("eq_treble_gain", gain).apply()
    }

    fun getVirtualizerStrength(): Int = prefs.getInt("eq_virtualizer", 0)
    fun setVirtualizerStrength(strength: Int) {
        prefs.edit().putInt("eq_virtualizer", strength).apply()
    }

    fun getAudioBalance(): Float = prefs.getFloat("eq_balance", 0f)
    fun setAudioBalance(balance: Float) {
        prefs.edit().putFloat("eq_balance", balance).apply()
    }

    fun getReverbPreset(): Int = prefs.getInt("eq_reverb_preset", 0)
    fun setReverbPreset(presetId: Int) {
        prefs.edit().putInt("eq_reverb_preset", presetId).apply()
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

