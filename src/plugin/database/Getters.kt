package plugin.database

import arc.struct.ObjectMap
import arc.util.Strings
import mindustry.gen.Groups
import mindustry.gen.Player
import mindustry.net.Administration
import mindustry.net.Administration.PlayerAction
import plugin.PVars
import plugin.database.Database.StatementSetter
import plugin.database.Database.executeUpdate
import plugin.database.models.*
import plugin.utils.Permission
import plugin.utils.getUDPAddress
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.util.*
import java.util.function.Consumer

// GOVNOCODE

@JvmField
var adminsCache = ObjectMap<Player, Admin>()

@JvmField
var playerDataCache = ObjectMap<Player, PlayerData>()

@JvmField
var playerStatsCache = ObjectMap<String, PlayerStats>()

@JvmField
var mutesCache = ObjectMap<Int, Mute>()

// region Admin

fun updateAdminHidden(pid: Int, hidden: Boolean): Boolean {
    return executeUpdate(
        """
                        UPDATE ADMINS SET
                        hidden = ?
                        WHERE player_id = ?
                        
                        """.trimIndent()
    ) { stmt: PreparedStatement ->
        stmt.setBoolean(1, hidden)
        stmt.setInt(2, pid)
    }
}

fun getAdmin(player: Player): Optional<Admin> {
    if (adminsCache.containsKey(player)) return Optional.of(adminsCache.get(player))

    val a = Database.executeQueryAsync(
        """
                        SELECT 
                                COALESCE(ar.name, 'player') AS rank_name,
                                COALESCE(ar.permissions, ARRAY['none']) AS permissions, a.id, a.player_id, a.hidden, a.rank_id
                            FROM players p
                            LEFT JOIN admins a ON a.player_id = p.id
                            LEFT JOIN admin_ranks ar ON ar.id = a.rank_id
                            WHERE p.uuid = ?
                    
                    """.trimIndent(),
        { stmt: PreparedStatement -> stmt.setString(1, player.uuid()) },
        { rs: ResultSet -> getAdmin(rs) }
    )
    a.ifPresent(Consumer { admin: Admin -> adminsCache.put(player, admin) })
    return a
}

@Throws(SQLException::class)
fun getAdmin(rs: ResultSet): Admin {
    return Admin(
        rs.getInt("id"),
        rs.getInt("player_id"),
        rs.getInt("rank_id"),
        rs.getString("rank_name"),
        Permission.getPerms(rs),
        rs.getBoolean("hidden")
    )
}

// endregion

// region Ban

fun ban(pid: Int, aid: Int, reason: String?, unban: Long): Boolean {
    return executeUpdate(
        "INSERT INTO bans (player_id, admin_id, reason, unban_time) VALUES (?, ?, ?, ?)",
        StatementSetter { stmt: PreparedStatement? ->
            stmt!!.setInt(1, pid)
            stmt.setInt(2, aid)
            stmt.setString(3, reason)
            stmt.setTimestamp(4, getTimestamp(unban))
        }
    )
}

fun ban(pid: Int, admin: Player, reason: String?, unban: Long): Boolean {
    return executeUpdate(
        """
        INSERT INTO bans (player_id, admin_id, reason, unban_time)
        VALUES (
            ?,
            (SELECT id FROM players WHERE uuid = ?),
            ?,
            ?
        )
    
    """.trimIndent()
    ) { stmt: PreparedStatement ->
        stmt.setInt(1, pid)
        stmt.setString(2, admin.uuid())
        stmt.setString(3, reason)
        stmt.setTimestamp(4, getTimestamp(unban))
    }
}

fun ban(player: Player, admin: Player, reason: String?, unban: Long): Boolean {
    return executeUpdate(
        """
        INSERT INTO bans (player_id, admin_id, reason, unban_time)
        VALUES (
            (SELECT id FROM players WHERE uuid = ?),
            (SELECT id FROM players WHERE uuid = ?),
            ?,
            ?
        )
    
    """.trimIndent()
    ) { stmt: PreparedStatement ->
        stmt.setString(1, player.uuid())
        stmt.setString(2, admin.uuid())
        stmt.setString(3, reason)
        stmt.setTimestamp(4, getTimestamp(unban))
    }
}

fun getTimestamp(seconds: Long): Timestamp? {
    if (seconds < 0) return null // перм бан


    val millis = seconds * 1000
    return Timestamp(System.currentTimeMillis() + millis)
}

fun getBan(id: Int): Optional<Ban> {
    return Database.executeQueryAsync(
        "SELECT * FROM bans WHERE id = ?",
        { stmt: PreparedStatement -> stmt.setInt(1, id) },
        { rs: ResultSet -> getBan(rs) }
    )
}

fun getBan(player: Player): Optional<Ban> {
    return Database.executeQueryAsync(
        """
SELECT b.*
FROM bans b
JOIN players p ON p.id = b.player_id
WHERE b.active = true
  AND (b.unban_time IS NULL OR b.unban_time > NOW())
  AND (
        p.uuid = ?
        OR p.last_ip = ?
        OR EXISTS (
            SELECT 1
            FROM usid_list ul
            WHERE ul.player_id = p.id
              AND ul.usid = ?
              AND ul.server = ?
        )
  )
LIMIT 1;
                
                """.trimIndent(),
        { stmt: PreparedStatement ->
            stmt.setString(1, player.uuid())
            stmt.setString(2, player.ip())
            stmt.setString(3, player.usid())
            stmt.setInt(4, PVars.serverId)
        },
        { rs: ResultSet -> getBan(rs) }
    )
}

@Throws(SQLException::class)
fun getBan(rs: ResultSet): Ban {
    val banId = rs.getInt("id")

    val banTs = rs.getTimestamp("ban_time")
    val unbanTs = rs.getTimestamp("unban_time")

    val banTime = if (banTs != null) banTs.toInstant() else Instant.now()
    val unbanTime = if (unbanTs != null) unbanTs.toInstant() else null

    return Ban(
        banId,
        rs.getInt("player_id"),
        rs.getInt("admin_id"),
        rs.getBoolean("active"),
        banTime,
        unbanTime,
        rs.getString("reason")
    )
}

// endregion

// region Log

fun putLog(type: String?, message: String?) {
    PVars.logsBuffer.add(Log(null, type, message))
}

fun putLog(pid: Int?, type: String?, message: String?) {
    PVars.logsBuffer.add(Log(pid, type, message))
}

fun putLog(action: PlayerAction, pd: PlayerData) {
    val message: String?
    val type = action.type

    val tile = action.tile ?: return
    if (tile.block().isAir) return

    when (type) {
        Administration.ActionType.breakBlock -> message =
            Strings.format("Player break block @ at @ @", tile.block().name, tile.x, tile.y)

        Administration.ActionType.placeBlock -> message =
            Strings.format("Player placed block @ at @ @", action.block, tile.x, tile.y)

        Administration.ActionType.rotate -> message =
            Strings.format("Player rotated block @ at @ @", tile.block().name, tile.x, tile.y)

        Administration.ActionType.configure -> message =
            Strings.format("Player configured block @ at @ @", tile.block().name, tile.x, tile.y)

        else -> return
    }

    putLog(pd.id, "action", message)
}

// endregion

// region PlayerData

fun deepSearchNames(player: Player): MutableList<String> {
    return Database.executeQueryList(
        """
                SELECT DISTINCT p.last_name
                FROM players p
                LEFT JOIN usid_list u ON u.player_id = p.id
                LEFT JOIN connections c ON c.player_id = p.id
                WHERE p.last_ip = ?
                   OR u.usid = ?
                   OR c.address = ?
                
                """.trimIndent(),
        { stmt: PreparedStatement ->
            stmt.setString(1, player.ip())
            stmt.setString(2, player.usid())
            stmt.setString(3, player.ip())
        },
        { rs: ResultSet -> rs.getString("last_name") }
    )
}

fun deepSearch(player: Player): MutableList<PlayerData> {
    return Database.executeQueryList(
        """
                SELECT DISTINCT p.*
                FROM players p
                LEFT JOIN usid_list u ON u.player_id = p.id
                LEFT JOIN connections c ON c.player_id = p.id
                WHERE p.last_ip = ?
                   OR u.usid = ?
                   OR c.address = ?
                
                """.trimIndent(),
        { stmt: PreparedStatement? ->
            stmt!!.setString(1, player.ip())
            stmt.setString(2, player.usid())
            stmt.setString(3, player.ip())
        },
        { rs: ResultSet -> getPlayerData(rs) }
    )
}

fun getPlayerById(id: Int): Optional<Player> {
    val pdOpt = getPlayerData(id)
    var p: Player? = null
    if (pdOpt.isPresent) {
        val pd = pdOpt.get()
        p = Groups.player.find { player: Player -> player.uuid() == pd.uuid }
    }
    return Optional.ofNullable<Player>(p)
}

fun getOrCreatePlayerData(p: Player): Optional<PlayerData> {
    if (playerDataCache.containsKey(p)) {
        return Optional.of(playerDataCache.get(p))
    }

    val pd = Database.executeQueryAsync(
        """
                        WITH update_players AS (
                            INSERT INTO players (uuid, last_name, last_ip, locale, color)
                            VALUES (?, ?, ?, ?, ?)
                            ON CONFLICT (uuid) DO UPDATE SET
                                last_name = EXCLUDED.last_name,
                                last_ip   = EXCLUDED.last_ip,
                                color     = EXCLUDED.color,
                                locale    = EXCLUDED.locale,
                                last_seen = NOW()
                            RETURNING id, uuid, last_name, last_ip, locale, color, discord_id, prefs
                        ),
                        insert_usid AS (
                            INSERT INTO usid_list (player_id, usid, server)
                            SELECT id, ?, ?
                            FROM update_players
                            ON CONFLICT (usid, server) DO NOTHING
                        ),
                        insert_connection AS (
                            INSERT INTO connections(player_name, address, address_udp, server_id, player_id)
                            SELECT last_name, last_ip, ?, ?, id
                            FROM update_players
                        ),
                        insert_stats AS (
                            INSERT INTO statistics (player_id)
                            SELECT id
                            FROM update_players
                            ON CONFLICT (player_id) DO NOTHING
                        )
                        SELECT *
                        FROM update_players;
                        
                        """.trimIndent(),
        { stmt: PreparedStatement ->
            stmt.setString(1, p.uuid())
            stmt.setString(2, p.name())
            stmt.setString(3, p.ip())
            stmt.setString(4, p.locale)
            stmt.setString(5, p.color.toString())
            stmt.setString(6, p.usid())
            stmt.setInt(7, PVars.serverId)
            stmt.setString(8, getUDPAddress(p))
            stmt.setInt(9, PVars.serverId)
        },
        { rs: ResultSet -> getPlayerData(rs) }
    )
    if (!playerDataCache.containsKey(p) && pd.isPresent) playerDataCache.put(p, pd.get())
    return pd
}

fun getPlayerData(id: Int): Optional<PlayerData> {
    return Database.executeQueryAsync(
        "SELECT * FROM players WHERE id = ?",
        { stmt: PreparedStatement -> stmt.setInt(1, id) },
        { rs: ResultSet -> getPlayerData(rs) }
    )
}

fun getPlayerData(player: Player): Optional<PlayerData> {
    if (playerDataCache.containsKey(player)) return Optional.of(playerDataCache.get(player))

    return Database.executeQueryAsync(
        "SELECT * FROM players WHERE uuid = ?",
        { stmt: PreparedStatement -> stmt.setString(1, player.uuid()) },
        { rs: ResultSet -> getPlayerData(rs) }
    )
}

fun getPlayerId(player: Player): Optional<Int> {
    if (playerDataCache.containsKey(player)) return Optional.of<Int>(playerDataCache.get(player).id)

    return Database.executeQueryAsync(
        "SELECT id FROM players WHERE uuid = ?",
        { stmt: PreparedStatement -> stmt.setString(1, player.uuid()) },
        { rs: ResultSet -> rs.getInt("id") }
    )
}

@Throws(SQLException::class)
fun getPlayerData(rs: ResultSet): PlayerData {
    val prefs = try {
        PVars.objectMapper.readValue(rs.getString("prefs"), PlayerPrefs::class.java)
    } catch (e: Exception) {
        arc.util.Log.err(e)
        PlayerPrefs()
    }

    val discordIdRaw = rs.getLong("discord_id")
    val discordId: Long? = if (rs.wasNull()) null else discordIdRaw

    return PlayerData(
        rs.getInt("id"),
        rs.getString("uuid"),
        discordId,
        prefs
    )
}

// endregion

// region PlayerStats

fun getPlayerStats(player: Player): Optional<PlayerStats> {
    val uuid = player.uuid()

    val cached: PlayerStats? = playerStatsCache.get(uuid)
    if (cached != null) return Optional.of(cached)

    val opt = Database.executeQueryAsync(
        "SELECT * from statistics WHERE player_id in (SELECT id FROM players WHERE uuid = ?)",
        { stmt: PreparedStatement -> stmt.setString(1, uuid) },
        { rs: ResultSet -> getPlayerStats(rs) }
    )

    opt.ifPresent(Consumer { stats: PlayerStats? -> playerStatsCache.put(uuid, stats) })

    return opt
}

fun getPlayerStats(pid: Int): Optional<PlayerStats> {
    return Database.executeQueryAsync(
        "SELECT * from statistics WHERE player_id = ?",
        { stmt: PreparedStatement -> stmt.setInt(1, pid) },
        { rs: ResultSet -> getPlayerStats(rs) }
    )
}

@Throws(SQLException::class)
fun getPlayerStats(rs: ResultSet): PlayerStats {
    return PlayerStats(
        rs.getInt("id"),
        rs.getInt("player_id"),
        rs.getLong("playtime"),
        rs.getInt("blocks_build"),
        rs.getInt("blocks_broken"),
        rs.getInt("balance"),
        rs.getInt("waves_survived")
    )
}

// endregion

// region mute

fun getMute(player: Player): Optional<Mute> {
    val idOpt = getPlayerId(player)
    if(idOpt.isEmpty)
        return Optional.empty<Mute>()
    return getMute(idOpt.get())
}

fun getMute(pid: Int): Optional<Mute> {
    val cached: Mute? = mutesCache.get(pid)
    if(cached != null)
        return Optional.of(cached)

    val mute = Database.executeQueryAsync(
        "SELECT * FROM mutes WHERE player_id = ? AND active = TRUE AND unmute_time > NOW()",
        { stmt: PreparedStatement -> stmt.setInt(1, pid) },
        { rs: ResultSet -> getMute(rs) }
    )

    if(mute.isPresent)
        mutesCache.put(pid, mute.get())

    return mute
}

fun mutePlayer(target: Int, admin: Int, reason: String, unmute: Long): Boolean {
    if(unmute <= 0) {
        return false // no perm mutes
    }
    return executeUpdate(
        """
            INSERT INTO mutes VALUES (player_id, admin_id, reason, unmute_time)
            VALUES (?, ?, ?, ?)
            
        """.trimIndent(),
        { stmt: PreparedStatement ->
            stmt.setInt(1, target)
            stmt.setInt(2, admin)
            stmt.setString(3, reason)
            stmt.setTimestamp(4, getTimestamp(unmute))
        }

    )
}

@Throws(SQLException::class)
fun getMute(rs: ResultSet): Mute {
    return Mute(
        rs.getInt("id"),
        rs.getBoolean("active"),
        rs.getInt("player_id"),
        rs.getInt("admin_id"),
        rs.getString("reason"),
        rs.getTimestamp("mute_time").toInstant(),
        rs.getTimestamp("unmute_time").toInstant()
    )
}

// endregion

// region map stats

@Throws(SQLException::class)
fun getMapStats(rs: ResultSet): MapStats {
    return MapStats(
        rs.getInt("id"),
        rs.getString("name"),
        rs.getInt("server"),
        rs.getInt("min_wave"),
        rs.getInt("max_wave"),
        rs.getInt("min_playtime"),
        rs.getInt("max_playtime"),
        rs.getInt("wins"),
        rs.getInt("loses"),
        rs.getInt("skips")
    )
}