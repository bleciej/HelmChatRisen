package com.example.helmchatreborn.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentRoomTest {

    @Test
    fun mergeRecentRoom_addsNewRoomFirst() {
        val merged = mergeRecentRoom(
            existing = listOf(
                RecentRoom("old", "https://meet.ffmuc.net", lastUsedAt = 10L),
            ),
            newRoom = RecentRoom("new", "https://meet.ffmuc.net", lastUsedAt = 20L),
        )

        assertEquals(listOf("new", "old"), merged.map { it.roomName })
    }

    @Test
    fun mergeRecentRoom_deduplicatesSameRoomAndServer() {
        val merged = mergeRecentRoom(
            existing = listOf(
                RecentRoom("ride", "https://meet.ffmuc.net/", displayName = "Old", lastUsedAt = 10L),
                RecentRoom("other", "https://meet.ffmuc.net", lastUsedAt = 5L),
            ),
            newRoom = RecentRoom("ride", "https://meet.ffmuc.net", displayName = "Burt", lastUsedAt = 20L),
        )

        assertEquals(2, merged.size)
        assertEquals("Burt", merged.first { it.roomName == "ride" }.displayName)
    }

    @Test
    fun mergeRecentRoom_trimsValuesAndDropsBlankNames() {
        val merged = mergeRecentRoom(
            existing = emptyList(),
            newRoom = RecentRoom("  ride  ", "  https://meet.ffmuc.net/  ", "  Burt  ", 1L),
        )

        assertEquals("ride", merged.single().roomName)
        assertEquals("https://meet.ffmuc.net", merged.single().serverUrl)
        assertEquals("Burt", merged.single().displayName)

        assertTrue(
            mergeRecentRoom(
                existing = emptyList(),
                newRoom = RecentRoom("   ", "https://meet.ffmuc.net", lastUsedAt = 1L),
            ).isEmpty(),
        )
    }

    @Test
    fun mergeRecentRoom_limitsListSize() {
        val existing = (1..MAX_RECENT_ROOMS).map { index ->
            RecentRoom("room-$index", "https://meet.ffmuc.net", lastUsedAt = index.toLong())
        }

        val merged = mergeRecentRoom(
            existing = existing,
            newRoom = RecentRoom("newest", "https://meet.ffmuc.net", lastUsedAt = 100L),
        )

        assertEquals(MAX_RECENT_ROOMS, merged.size)
        assertEquals("newest", merged.first().roomName)
    }
}
