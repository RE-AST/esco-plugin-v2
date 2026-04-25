package plugin.utils

import arc.files.Fi
import arc.func.Cons
import arc.net.Connection
import arc.util.Http
import arc.util.Log
import arc.util.Reflect
import arc.util.Strings
import mindustry.Vars
import mindustry.gen.Player
import mindustry.maps.Map
import plugin.PVars
import plugin.PVars.apiAuth
import plugin.PVars.httpClient
import plugin.utils.Permission.getPerms
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.util.*
import java.util.zip.InflaterInputStream

const val characters = "qwertyuiopasdfghjklzxcvbnm123456789="

fun isAnon(ip: String?, callback: Cons<ApiResponse>) {
    Http.get(PVars.vpnApi + ip)
        .header("Authorization", "Basic $apiAuth")
        .error { th ->
            Log.err("Failed to check ip $ip", th)
        }
        .submit { resp ->
            Log.debug("Received IPAPI response")
            try {
                val apiResponse = PVars.objectMapper.readValue(
                    resp!!.resultAsString,
                    ApiResponse::class.java
                )
                if (!apiResponse.status.equals("success")) {
                    Log.err("Failed to check ip $ip messsage ${apiResponse.message}")
                    return@submit
                }
                callback.get(
                    apiResponse
                )
            } catch (e: Exception) {
                Log.err("Failed to parse api response", e)
            }
        }
    /*
    Http.get(
        PVars.vpnApi + ip,
        { resp: Http.HttpResponse? ->
            try {
                callback.get(
                    PVars.objectMapper.readValue(
                        resp!!.resultAsString,
                        ApiResponse::class.java
                    )
                )
            } catch (e: Exception) {
                Log.err("Failed to parse api response", e)
            }
        },
        { err: Throwable? ->
            Log.err("Failed to check ip", err)
        }
    )*/
}

fun download(url: String, dest: Path) {
    val req = HttpRequest.newBuilder(URI.create(url)).GET().build()
    val resp = httpClient.send(req, HttpResponse.BodyHandlers.ofFile(dest))
    require(resp.statusCode() in 200..299) { "HTTP ${resp.statusCode()}" }
}

fun httpGetString(url: String): String {
    val req = HttpRequest.newBuilder(URI.create(url)).GET().build()
    val resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString())
    require(resp.statusCode() in 200..299) { "HTTP ${resp.statusCode()}" }
    return resp.body().trim()
}

fun parseBool(bool: String): Int {
    return when (bool.lowercase(Locale.getDefault())) {
        "y", "yes", "д", "да", "+", "t", "true" -> 1
        "n", "no", "н", "нет", "-", "f", "false" -> -1
        else -> 0
    }
}

fun getRandomString(len: Int): String {
    val sb = StringBuilder()

    for (i in 0..<len) {
        sb.append(characters[PVars.random.nextInt(characters.length)])
    }

    return sb.toString()
}

fun getResource(name: String): Fi? {
    return Vars.mods.locateMod("plugin").root.child(name)
}

fun stripFoo(string: String): String {
    val var1 = StringBuilder(string)
    for (i in string.length - 1 downTo 0) {
        if (var1[i].code in 0xf80..0x107f) var1.deleteCharAt(i)
    }
    return var1.toString()
}

fun formatTime(time: Long): String {
    val days = time / 86400
    val hours = (time % 86400) / 3600
    val minutes = (time % 3600) / 60
    val seconds = time % 60

    val sb = StringBuilder()
    if (days > 0) sb.append(days).append("d")
    if (hours > 0) sb.append(hours).append("h")
    if (minutes > 0) sb.append(minutes).append("m")
    if (seconds > 0 || sb.isEmpty()) sb.append(seconds).append("s")

    return sb.toString().trim { it <= ' ' }
}

fun parseTime(time: String?): Long {
    time ?: return 0
    var time = time
    if (time.isEmpty() || !Character.isDigit(time[0])) return -1
    val timeMod = time[time.length - 1].lowercaseChar() // last char

    if (Character.isDigit(timeMod)) {
        // minutes
        if (!Strings.canParseInt(time)) return -1
        return time.toLong() * 60
    }

    time = time.substring(0, time.length - 1)
    if (!Strings.canParseInt(time)) return -1

    val parsed = time.toLong()
    if (timeMod == 'h') return parsed * 60 * 60
    if (timeMod == 'd') return parsed * 60 * 60 * 24
    if (timeMod == 'w') return parsed * 60 * 60 * 24 * 7
    if (timeMod == 'm') return parsed * 60 * 60 * 24 * 30
    if (timeMod == 'y') return parsed * 60 * 60 * 24 * 365
    return parsed
}

fun decompress(data: ByteArray?): String {
    if (data == null) return ""
    try {
        DataInputStream(InflaterInputStream(ByteArrayInputStream(data))).use { stream ->
            stream.read() // Version
            val bytelen = stream.readInt()
            if (bytelen > 1024 * 100) return ""
            val bytes = ByteArray(bytelen)
            stream.readFully(bytes)
            return String(bytes, Vars.charset)
        }
    } catch (e: IOException) {
        Log.err(e)
    }
    return "" // Somehow this failed to read the code
}


/*public static int countWords(String word, String text) {
        return (text.length() - text.replace(word, "").length()) / word.length();
    }*/
fun countWords(w: String, t: String): Int {
    return countOccurrences(w, t)
}

fun countOccurrences(word: String, text: String): Int {
    if (word.isEmpty()) return 0

    var count = 0
    var index = 0

    while ((text.indexOf(word, index).also { index = it }) != -1) {
        count++
        index += word.length
    }

    return count
}

fun getUDPAddress(player: Player): String {
    return Reflect.get<Connection>(player.con, "connection").remoteAddressUDP.address.toString().substring(1)
}

fun Player.hasPerms(perm: Permission): Boolean {
    return getPerms(this).contains(perm)
}

fun findMap(name: String): Map? {
    val maps = Vars.maps.customMaps()
    for (map in maps)
        if (map.name().contains(name))
            return map
    return null
}
