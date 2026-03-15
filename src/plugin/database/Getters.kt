package plugin.database

import arc.struct.ObjectMap
import mindustry.gen.Player
import plugin.database.models.Admin
import plugin.utils.Permission
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.util.*
import java.util.function.Consumer

@JvmField
var adminsCache = ObjectMap<Player, Admin>()

fun updateAdminHidden(pid: Int, hidden: Boolean): Boolean {
    return Database.executeUpdate(
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