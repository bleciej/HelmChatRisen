package com.example.helmchatreborn.data

import kotlinx.serialization.Serializable

/**
 * A room the rider has joined before. Used to pre-fill the join form so a ride can be
 * started with a single, glove-friendly tap.
 */
@Serializable
data class RecentRoom(
    val roomName: String,
    val serverUrl: String,
    val displayName: String? = null,
    val lastUsedAt: Long = 0L,
)

/** Keep the home screen short enough to stay usable on a motorcycle handlebar mount. */
internal const val MAX_RECENT_ROOMS = 8

/**
 * Adds [newRoom] to [existing], removes duplicates and keeps the list sorted by most
 * recently used first. Pure function so it can be covered by JVM unit tests.
 */
internal fun mergeRecentRoom(
    existing: List<RecentRoom>,
    newRoom: RecentRoom,
    limit: Int = MAX_RECENT_ROOMS,
): List<RecentRoom> {
    val normalized = newRoom.copy(
        roomName = newRoom.roomName.trim(),
        serverUrl = newRoom.serverUrl.trim().trimEnd('/'),
        displayName = newRoom.displayName?.trim()?.ifBlank { null },
    )
    if (normalized.roomName.isBlank() || normalized.serverUrl.isBlank()) {
        return existing
    }

    val withoutDuplicate = existing.filterNot { room ->
        room.roomName == normalized.roomName &&
            room.serverUrl.trimEnd('/') == normalized.serverUrl
    }

    return (listOf(normalized) + withoutDuplicate)
        .sortedByDescending { it.lastUsedAt }
        .take(limit)
}
