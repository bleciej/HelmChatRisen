package com.example.helmChatRisen.data

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
 * Stable server identity that ignores the scheme, host casing and trailing slashes. The optional
 * path is preserved so distinct deployments on the same host stay separate.
 */
internal fun canonicalServerKey(serverUrl: String): String =
    serverUrl.trim().trimEnd('/').substringAfter("://").lowercase()

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

    val normalizedServerKey = canonicalServerKey(normalized.serverUrl)
    val withoutDuplicate = existing.filterNot { room ->
        room.roomName == normalized.roomName &&
            canonicalServerKey(room.serverUrl) == normalizedServerKey
    }

    return (listOf(normalized) + withoutDuplicate)
        .sortedByDescending { it.lastUsedAt }
        .take(limit)
}
