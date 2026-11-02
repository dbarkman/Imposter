package com.redmesastudios.imposter

import org.json.JSONArray
import java.io.BufferedInputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

private const val SimpleTvPort = 8724

// The household's TVs share players through SimpleTV, which runs on every stick all the time
// (it's the home screen) and passes other apps' records between them. Imposter hands its
// players to the SimpleTV on the same stick and gets back the household's copy, which
// includes everyone added in Trivia Night. Requests carry the household's sync key
// (BuildConfig.SYNC_KEY, from ~/.config/simpletv/sync_key, the same file SimpleTV is built
// with); without one, or without SimpleTV, players stay on this TV.
object Household {
    val enabled: Boolean get() = BuildConfig.SYNC_KEY.isNotEmpty()

    fun exchange(profiles: List<Profile>): List<Profile> {
        if (!enabled) throw IOException("no sync key")
        val body = profilesToJson(profiles).toString().toByteArray()
        return profilesFromJson(JSONArray(String(post("/sync/shared/$PlayersCollection", body))))
    }

    // Plain HTTP over a socket to this stick's own SimpleTV; it never leaves the device.
    private fun post(path: String, body: ByteArray): ByteArray {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", SimpleTvPort), 2_000)
            socket.soTimeout = 15_000
            val head = "POST $path HTTP/1.1\r\nHost: 127.0.0.1\r\nX-Sync-Key: ${BuildConfig.SYNC_KEY}\r\n" +
                "Content-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
            socket.getOutputStream().apply { write(head.toByteArray()); write(body); flush() }
            val input = BufferedInputStream(socket.getInputStream())
            val status = readHttpLine(input)?.split(" ")?.getOrNull(1)?.toIntOrNull()
            val headers = generateSequence { readHttpLine(input)?.takeIf { it.isNotEmpty() } }
                .filter { ':' in it }.associate { it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim() }
            if (status != 200) throw IOException("HTTP $status for $path")
            val length = headers["content-length"]?.toIntOrNull() ?: throw IOException("No length for $path")
            return input.readNBytes(length)
        }
    }
}
