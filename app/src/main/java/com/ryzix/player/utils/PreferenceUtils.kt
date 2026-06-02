package com.ryzix.player.utils

  import android.content.Context
  import androidx.datastore.core.DataStore
  import androidx.datastore.preferences.core.*
  import androidx.datastore.preferences.preferencesDataStore
  import kotlinx.coroutines.flow.Flow
  import kotlinx.coroutines.flow.map

  private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ryzix_prefs")

  class PreferenceUtils(private val context: Context) {
      companion object {
          val KEY_PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
          val KEY_ASPECT_RATIO = intPreferencesKey("aspect_ratio")
          val KEY_SUBTITLE_SIZE = floatPreferencesKey("subtitle_size")
          val KEY_SUBTITLE_ENABLED = booleanPreferencesKey("subtitle_enabled")
          val KEY_SORT_ORDER = intPreferencesKey("sort_order")
          val KEY_VIEW_MODE = intPreferencesKey("view_mode")
          val KEY_REMEMBER_POSITION = booleanPreferencesKey("remember_position")
          val KEY_HARDWARE_DECODE = booleanPreferencesKey("hardware_decode")
          val KEY_PIP_ENABLED = booleanPreferencesKey("pip_enabled")
          const val SORT_BY_NAME = 0; const val SORT_BY_DATE = 1
          const val SORT_BY_SIZE = 2; const val SORT_BY_DURATION = 3
          const val VIEW_LIST = 0; const val VIEW_GRID = 1
          const val ASPECT_FIT = 0; const val ASPECT_FILL = 1
          const val ASPECT_STRETCH = 2; const val ASPECT_16_9 = 3; const val ASPECT_4_3 = 4
      }
      val playbackSpeed: Flow<Float> = context.dataStore.data.map { it[KEY_PLAYBACK_SPEED] ?: 1.0f }
      val aspectRatio: Flow<Int> = context.dataStore.data.map { it[KEY_ASPECT_RATIO] ?: ASPECT_FIT }
      val subtitleEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_SUBTITLE_ENABLED] ?: true }
      val sortOrder: Flow<Int> = context.dataStore.data.map { it[KEY_SORT_ORDER] ?: SORT_BY_DATE }
      val viewMode: Flow<Int> = context.dataStore.data.map { it[KEY_VIEW_MODE] ?: VIEW_LIST }
      val rememberPosition: Flow<Boolean> = context.dataStore.data.map { it[KEY_REMEMBER_POSITION] ?: true }
      val hardwareDecode: Flow<Boolean> = context.dataStore.data.map { it[KEY_HARDWARE_DECODE] ?: true }
      val pipEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_PIP_ENABLED] ?: true }
      suspend fun setPlaybackSpeed(speed: Float) = context.dataStore.edit { it[KEY_PLAYBACK_SPEED] = speed }
      suspend fun setAspectRatio(ratio: Int) = context.dataStore.edit { it[KEY_ASPECT_RATIO] = ratio }
      suspend fun setSubtitleEnabled(enabled: Boolean) = context.dataStore.edit { it[KEY_SUBTITLE_ENABLED] = enabled }
      suspend fun setSortOrder(order: Int) = context.dataStore.edit { it[KEY_SORT_ORDER] = order }
      suspend fun setViewMode(mode: Int) = context.dataStore.edit { it[KEY_VIEW_MODE] = mode }
      suspend fun setRememberPosition(enabled: Boolean) = context.dataStore.edit { it[KEY_REMEMBER_POSITION] = enabled }
      suspend fun setHardwareDecode(enabled: Boolean) = context.dataStore.edit { it[KEY_HARDWARE_DECODE] = enabled }
      suspend fun setPipEnabled(enabled: Boolean) = context.dataStore.edit { it[KEY_PIP_ENABLED] = enabled }
  }