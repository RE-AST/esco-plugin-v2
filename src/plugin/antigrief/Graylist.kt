package plugin.antigrief

import arc.struct.Seq
import arc.util.Http
import arc.util.Log
import arc.util.Reflect
import arc.util.Timer
import mindustry.Vars
import mindustry.gen.Player
import mindustry.net.ArcNetProvider
import plugin.Bundle
import plugin.PVars
import plugin.database.Database
import plugin.database.models.PlayerData
import plugin.database.putLog
import java.sql.PreparedStatement
import java.sql.ResultSet

const val baseUrl = "https://raw.githubusercontent.com/escocorp/graylist/refs/heads/main";
const val ispsUrl = "$baseUrl/isps.txt"
const val ipsUrl = "$baseUrl/ips.txt"
const val ipsBlockUrl = "$baseUrl/ips-filter.txt"

const val timeout = 30

val isps = Seq<String>()
val ips = Seq<String>()
val ipsBlock = Seq<String>()

fun apply(p: Player, isp: String?, pd: PlayerData) {
    if(pd.discordId == null && (isps.contains(isp) || ips.contains(p.ip()))) {
        p.kick(Bundle.get("kick.graylisted", p.locale, PVars.discordLink), 0)
        putLog(pd.id, "graylist", "Player graylisted by IP " + p.ip())
    }
    /*if (!(isGraylisted(isp) && pd.discordId == null)) return
    p.kick(Bundle.get("kick.graylisted", p.locale, PVars.discordLink), 0)
    putLog(pd.id, "graylist", "Player graylisted by IP " + p.ip())*/
}

fun loadGraylist() {
    Timer.schedule({
        reloadGraylist()
    }, 0f, 30f*60);

    Reflect.get<ArcNetProvider>(Vars.net, "provider").setConnectFilter { ip ->
        return@setConnectFilter !ipsBlock.contains(ip)
    }
}

fun reloadGraylist() {
    try {
        Http.get(ispsUrl)
            .error { e ->
                Log.err("Failed to load ISPs list", e)
            }
            .submit { response ->
                isps.clear()
                isps.addAll(
                    response.resultAsString
                        .lines()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                )
                Log.info("Loaded ${isps.size} ISPs")
            }
        Http.get(ipsUrl)
            .error { e ->
                Log.err("Failed to load IPs list", e)
            }
            .submit { response ->
                ips.clear()
                ips.addAll(
                    response.resultAsString
                        .lines()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                )
                Log.info("Loaded ${ips.size} IPs")
            }
        Http.get(ipsBlockUrl)
            .error { e ->
                Log.err("Failed to load IPs list", e)
            }
            .submit { response ->
                ipsBlock.clear()
                ipsBlock.addAll(
                    response.resultAsString
                        .lines()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                )
                Log.info("Loaded ${ipsBlock.size} blocked IPs")
            }
    } catch (e: Exception) {
        Log.err("Failed to load graylist", e)
    }
}

fun isGraylisted(isp: String?): Boolean {
    return Database.executeQuery(
        """
                        SELECT EXISTS(
                            SELECT 1 FROM graylist WHERE isp ILIKE ?
                        )
                        
                        """.trimIndent(),
        { stmt: PreparedStatement? -> stmt!!.setString(1, isp) },
        { rs: ResultSet? -> rs!!.getBoolean("exists") }
    ).orElse(false)
}