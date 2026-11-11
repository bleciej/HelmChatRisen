package com.example.helmchatreborn.jitsi

import org.junit.Assert.assertEquals
import org.junit.Test

class RoomLinksTest {

    @Test
    fun roomLink_appendsRoomToServer() {
        assertEquals(
            "https://meet.ffmuc.net/niedzielna-przejezdzajka",
            RoomLinks.roomLink("https://meet.ffmuc.net", "niedzielna-przejezdzajka"),
        )
    }

    @Test
    fun roomLink_trimsInputsAndTrailingSlash() {
        assertEquals(
            "https://meet.ffmuc.net/ride",
            RoomLinks.roomLink("  https://meet.ffmuc.net/  ", "  ride  "),
        )
    }

    @Test
    fun roomLink_encodesRoomNameForUrlPath() {
        assertEquals(
            "https://meet.ffmuc.net/niedzielna%20przejezdzajka",
            RoomLinks.roomLink("https://meet.ffmuc.net", "niedzielna przejezdzajka"),
        )
        assertEquals(
            "https://meet.ffmuc.net/%C5%BC%C3%B3%C5%82w",
            RoomLinks.roomLink("https://meet.ffmuc.net", "żółw"),
        )
    }

    @Test
    fun roomLink_blankRoomReturnsServer() {
        assertEquals(
            "https://meet.ffmuc.net",
            RoomLinks.roomLink("https://meet.ffmuc.net/", "   "),
        )
    }
}
