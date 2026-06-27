package plugin.database.models

import arc.struct.ObjectMap
import arc.util.Log
import arc.util.Time
import arc.util.Timekeeper
import mindustry.gen.Player
import org.postgresql.util.PGobject
import plugin.PVars
import plugin.database.Database
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.*

class PlayerData(
    var id: Int, var uuid: String?, var discordId: Long?, var prefs: PlayerPrefs, var lastName: String?, // stats
    var playtime: Long, var blocksBuild: Int, var blocksBroken: Int, var balance: Int, var wavesSurvived: Int
) {
    var usid: String? = null

    @Transient
    var lastGambling: Timekeeper? = null

    fun getUsid(): String? {
        if (usid != null) {
            return usid
        }
        val usidOpt = Database.executeQuery(
            """
                        SELECT usid FROM usid_list
                        WHERE player_id = ? AND server = ?
                        
                        """.trimIndent(),
            { stmt: PreparedStatement ->
                stmt.setInt(1, id)
                stmt.setInt(2, PVars.serverId)
            },
            { rs: ResultSet -> rs.getString("usid") }
        )
        if (usidOpt != null) usid = usidOpt
        return usidOpt
    }

    fun updateDiscordId(dsid: Long): Boolean {
        val updated = Database.executeUpdate(
            """
                        UPDATE players SET discord_id = ?
                        WHERE id = ?
                        
                        """.trimIndent()
        ) { stmt: PreparedStatement ->
            stmt.setLong(1, dsid)
            stmt.setInt(2, id)
        }
        if (updated) this.discordId = dsid
        return updated
    }

    fun updatePrefs(): Boolean {
        try {
            val `object` = PGobject()
            `object`.setType("jsonb")
            `object`.setValue(PVars.objectMapper.writeValueAsString(prefs))
            return Database.executeUpdate(
                """
                            UPDATE players SET prefs = ? WHERE id = ?
                            
                            """.trimIndent()
            ) { stmt: PreparedStatement ->
                stmt.setObject(1, `object`)
                stmt.setInt(2, id)
            }
        } catch (e: Exception) {
            Log.err(e)
            return false
        }
    }

    fun setLastGambling(time: Timekeeper): PlayerData {
        lastGambling = time
        return this
    }

    fun write(): Boolean {
        return Database.executeUpdate(
            "UPDATE players SET playtime = ?, blocks_build = ?, blocks_broken = ?, balance = ?, waves_survived = ? WHERE id = ?"
        ) { stmt: PreparedStatement? ->
            stmt!!.setLong(1, playtime)
            stmt.setInt(2, blocksBuild)
            stmt.setInt(3, blocksBroken)
            stmt.setInt(4, balance)
            stmt.setInt(5, wavesSurvived)
            stmt.setInt(6, id)
        }
    }

    fun adjBlocksBuild(): PlayerData {
        this.blocksBuild += 1
        if (blocksBuild % 50 == 0) adjBalance(PVars.gamemode.blockCost)
        return this
    }

    fun adjBlocksBroken(): PlayerData {
        this.blocksBroken += 1
        return this
    }

    fun adjWavesSurvived(): PlayerData {
        this.wavesSurvived += 1
        adjBalance(PVars.gamemode.waveCost)
        return this
    }

    fun adjWins(): PlayerData {
        return adjBalance(PVars.gamemode.winCost)
    }

    @JvmOverloads
    fun adjBalance(count: Int = 1): PlayerData {
        this.balance += count
        return this
    }

    fun subBalance(count: Int): PlayerData {
        this.balance -= count
        return this
    }

    fun update(player: Player, purge: Boolean): PlayerData {
        synchronized(joinTime) {
            val time: Long? = joinTime.remove(player.uuid())
            if (time != null) {
                val deltaSec = (Time.millis() - time) / 1000
                if (deltaSec > 0) playtime += deltaSec
            }
            if (!purge) setJoinTime(player)
        }
        if (purge) write()

        return this
    }

    companion object {
        var joinTime: ObjectMap<String?, Long?> = ObjectMap<String?, Long?>()

        fun setJoinTime(p: Player) {
            joinTime.put(p.uuid(), Time.millis())
        }
    }
}