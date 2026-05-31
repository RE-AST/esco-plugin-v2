package plugin.utils

import arc.Core
import arc.Events
import arc.files.Fi
import arc.func.Cons
import arc.net.Connection
import arc.util.Http
import arc.util.Log
import arc.util.Reflect
import arc.util.Strings
import kotlinx.coroutines.launch
import mindustry.Vars
import mindustry.Vars.saveDirectory
import mindustry.Vars.saveExtension
import mindustry.core.GameState
import mindustry.gen.Player
import mindustry.io.SaveIO
import mindustry.maps.Map
import plugin.Bundle
import plugin.KVars.eventsScope
import plugin.PVars
import plugin.PVars.apiAuth
import plugin.PVars.httpClient
import plugin.utils.Permission.getPerms
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.IOException
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.util.*
import java.util.zip.InflaterInputStream
import javax.imageio.ImageIO

const val characters = "qwertyuiopasdfghjklzxcvbnm123456789="

fun isAnon(ip: String?, callback: Cons<VPNApiResponse>) {
    Http.get(PVars.vpnApi + ip)
        .header("Authorization", "Basic $apiAuth")
        .error { th ->
            Log.err("Failed to check ip $ip", th)
        }
        .submit { resp ->
            Log.debug("Received IPAPI response")
            try {
                val apiResponse = PVars.objectMapper.readValue(
                    resp.resultAsString,
                    VPNApiResponse::class.java
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

fun <T> onAsync(type: Class<T>, listener: Cons<T>) {
    Events.on(type) { e: T ->
        eventsScope.launch {
            listener.get(e)
        }
    }
}

@Throws(IOException::class)
fun parseImage(image: BufferedImage): ByteArray {
    val stream = ByteArrayOutputStream()
    ImageIO.write(image, "png", stream)
    return stream.toByteArray()
}

fun save(name: String): Boolean {
    if(!Vars.state.isGame){
        Log.err("Not hosting. Failed to save.")
        return false
    }

    val file = saveDirectory.child("$name.$saveExtension")

    Core.app.post {
        SaveIO.save(file)
        Log.info("Saved to @.", file)
    }
    return true
}

fun loadSave(name: String): Boolean {
    if (Vars.state.isGame) {
        Log.err("Already hosting. Failed to load save.")
        return false
    }

    val file = saveDirectory.child(name + "." + saveExtension)

    if (!SaveIO.isSaveValid(file)) {
        Log.err("No (valid) save data found for slot.")
        return false
    }

    Core.app.post(Runnable {
        try {
            SaveIO.load(file)
            Vars.state.rules.sector = null
            Log.info("Save loaded.")
            Vars.state.set(GameState.State.playing)
            Vars.netServer.openServer()
        } catch (t: Throwable) {
            Log.err("Failed to load save. Outdated or corrupt file.")
        }
    })
    return true
}

fun Player.sendBundle(req: String) {
    Bundle.sendMessage(req, this)
}

fun Player.sendBundle(req: String, vararg params: Any) {
    Bundle.sendMessage(req, this, *params)
}