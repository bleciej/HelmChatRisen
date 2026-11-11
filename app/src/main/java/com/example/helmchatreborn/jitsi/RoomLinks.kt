package com.example.helmchatreborn.jitsi

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Helpers for building links that can be shared with other riders.
 *
 * Jitsi rooms live directly under the server root, so the shareable link is simply
 * `<server>/<room>`. The room name is path-encoded, which keeps links valid for rooms with
 * spaces, Polish diacritics or other characters typed on the join screen.
 */
object RoomLinks {

    /**
     * Returns a browser-joinable link for [roomName] on [serverUrl], or just the server URL
     * when the room name is blank.
     */
    fun roomLink(serverUrl: String, roomName: String): String {
        val base = serverUrl.trim().trimEnd('/')
        val room = roomName.trim()
        if (room.isEmpty()) return base
        val encoded = URLEncoder.encode(room, StandardCharsets.UTF_8.name())
            .replace("+", "%20")
        return "$base/$encoded"
    }
}
