package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.melodyPrefsDataStore: DataStore<Preferences> by
  preferencesDataStore(name = "melody_ui_prefs")

class UserPreferencesStore(private val context: Context) {
  private val keyIsGridView = booleanPreferencesKey("library_is_grid_view")
  private val keyRecentSearches = stringPreferencesKey("recent_searches_delimited")

  val isGridViewFlow: Flow<Boolean> =
    context.melodyPrefsDataStore.data.map { prefs -> prefs[keyIsGridView] ?: false }

  val recentSearchesFlow: Flow<List<String>> =
    context.melodyPrefsDataStore.data.map { prefs ->
      val raw = prefs[keyRecentSearches].orEmpty()
      if (raw.isBlank()) emptyList()
      else raw.split("\u001F").filter { it.isNotBlank() }
    }

  suspend fun setGridView(isGrid: Boolean) {
    context.melodyPrefsDataStore.edit { prefs -> prefs[keyIsGridView] = isGrid }
  }

  suspend fun addRecentSearch(query: String) {
    val clean = query.trim()
    if (clean.isEmpty()) return
    context.melodyPrefsDataStore.edit { prefs ->
      val current =
        prefs[keyRecentSearches].orEmpty().split("\u001F").filter { it.isNotBlank() }
      val updated = (listOf(clean) + current.filterNot { it.equals(clean, ignoreCase = true) }).take(8)
      prefs[keyRecentSearches] = updated.joinToString("\u001F")
    }
  }

  suspend fun removeRecentSearch(query: String) {
    context.melodyPrefsDataStore.edit { prefs ->
      val current =
        prefs[keyRecentSearches].orEmpty().split("\u001F").filter { it.isNotBlank() }
      val updated = current.filterNot { it == query }
      prefs[keyRecentSearches] = updated.joinToString("\u001F")
    }
  }

  suspend fun clearRecentSearches() {
    context.melodyPrefsDataStore.edit { prefs -> prefs[keyRecentSearches] = "" }
  }
}
