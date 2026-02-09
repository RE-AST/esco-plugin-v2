package plugin.database.models;

import arc.struct.ObjectMap;
import arc.struct.Seq;
import mindustry.gen.Player;
import plugin.utils.Permission;
import plugin.database.Database;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import static plugin.database.Database.executeUpdate;
import static plugin.utils.Permission.getPerms;

public class Admin {
    public static ObjectMap<Player, Admin> cache = new ObjectMap<>();
    public int id, playerId, rankId;
    public String rankName;
    public Seq<Permission> perms;
    public boolean hidden;

    Admin(int id, int playerId, int rankId, String rankName, Seq<Permission> perms, boolean hidden) {
        this.id = id;
        this.playerId = playerId;
        this.rankId = rankId;
        this.rankName = rankName;
        this.perms = perms;
        this.hidden = hidden;
    }

    public boolean updateHidden(boolean hidden) {
        boolean updated = executeUpdate(
                """
                        UPDATE ADMINS SET
                        hidden = ?
                        WHERE id = ?
                        """,
                stmt->{
                    stmt.setBoolean(1, hidden);
                    stmt.setInt(2, id);
                }
        );
        if(updated)
            this.hidden = hidden;
        return updated;
    }

    public static boolean updateHidden(int pid, boolean hidden) {
        return executeUpdate(
                """
                        UPDATE ADMINS SET
                        hidden = ?
                        WHERE player_id = ?
                        """,
                stmt->{
                    stmt.setBoolean(1, hidden);
                    stmt.setInt(2, pid);
                }
        );
    }

    public static Optional<Admin> getAdmin(Player player) {
        if(cache.containsKey(player))
            return Optional.of(cache.get(player));

        var a = Database.executeQueryAsync(
                """
                        SELECT 
                                COALESCE(ar.name, 'player') AS rank_name,
                                COALESCE(ar.permissions, ARRAY['none']) AS permissions, a.id, a.player_id, a.hidden, a.rank_id
                            FROM players p
                            LEFT JOIN admins a ON a.player_id = p.id
                            LEFT JOIN admin_ranks ar ON ar.id = a.rank_id
                            WHERE p.uuid = ?
                    """,
                stmt->stmt.setString(1, player.uuid()),
                Admin::getAdmin
        );
        a.ifPresent(admin -> cache.put(player, admin));
        return a;
    }

    public static Admin getAdmin(ResultSet rs) throws SQLException {
        return new Admin(
                rs.getInt("id"),
                rs.getInt("player_id"),
                rs.getInt("rank_id"),
                rs.getString("rank_name"),
                getPerms(rs),
                rs.getBoolean("hidden")
        );
    }
}
