package com.example.helmChatRisen.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.recentRoomsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "recent_rooms",
)

/**
 * Persists the most recently used rooms in a small DataStore file.
 *
 * The list is stored as JSON in a single preference so it survives app restarts and can
 * evolve without a database migration.
 */
class RecentRoomsRepository(context: Context) {

    private val dataStore = context.applicationContext.recentRoomsDataStore

    val recentRooms: Flow<List<RecentRoom>> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { preferences -> decode(preferences[RECENT_ROOMS_KEY]) }

    suspend fun record(room: RecentRoom) {
        dataStore.edit { preferences ->
            val current = decode(preferences[RECENT_ROOMS_KEY])
            preferences[RECENT_ROOMS_KEY] = encode(mergeRecentRoom(current, room))
        }
    }

    suspend fun remove(room: RecentRoom) {
        dataStore.edit { preferences ->
            val current = decode(preferences[RECENT_ROOMS_KEY])
            preferences[RECENT_ROOMS_KEY] = encode(
                current.filterNot {
                    it.roomName == room.roomName &&
                        canonicalServerKey(it.serverUrl) == canonicalServerKey(room.serverUrl)
                },
            )

        }
    }

    suspend fun clear() {
        dataStore.edit { preferences -> preferences.remove(RECENT_ROOMS_KEY) }
    }

    private fun decode(raw: String?): List<RecentRoom> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }
            .getOrDefault(emptyList())
    }

    private fun encode(rooms: List<RecentRoom>): String = json.encodeToString(serializer, rooms)

    private companion object {
        val RECENT_ROOMS_KEY = stringPreferencesKey("recent_rooms_v1")
        val serializer = ListSerializer(RecentRoom.serializer())
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
