package plugin.database.models;

import arc.struct.ObjectMap;
import mindustry.gen.Player;
import plugin.PVars;
import plugin.database.Database;
import mindustry.gen.Groups;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import static plugin.PVars.gamemode;
import static plugin.PVars.serverId;
import static plugin.database.Database.executeQueryAsync;
import static plugin.database.Database.executeUpdate;

public class PlayerData {
    public static ObjectMap<Player, PlayerData> cache = new ObjectMap<>();
    public int id;
    public String uuid;
    public Long discordId;

    PlayerData(int id, String uuid, Long discordId) {
        this.id = id;
        this.uuid = uuid;
        this.discordId = discordId;
    }

    public boolean updateDiscordId(Long dsid) {
        boolean updated = executeUpdate(
        """
                UPDATE players SET discord_id = ?
                WHERE id = ?
                """,
                stmt->{
                    stmt.setLong(1, dsid);
                    stmt.setInt(2, id);
                }
        );
        if(updated)
            this.discordId = discordId;
        return updated;
    }

    public Optional<String> getUsid() {
        return executeQueryAsync(
                """
                SELECT usid FROM usid_list
                WHERE player_id = ? AND server = ?
                """,
                stmt->{
                    stmt.setInt(1, id);
                    stmt.setInt(2, serverId);
                },
                rs->rs.getString("usid")
        );
    }

    public static Optional<Player> getPlayerById(int id) {
        Optional<PlayerData> pdOpt = getPlayerData(id);
        Player p = null;
        if(pdOpt.isPresent()) {
            PlayerData pd = pdOpt.get();
            p = Groups.player.find(player->player.uuid().equals(pd.uuid));
        }
        return Optional.ofNullable(p);
    }

    public static Optional<PlayerData> getOrCreatePlayerData(Player p) {
        if(cache.containsKey(p)) {
            return Optional.of(cache.get(p));
        }

        Optional<PlayerData> pd = Database.executeQueryAsync(
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
                            RETURNING id, uuid, last_name, last_ip, locale, color, discord_id
                        ),
                        insert_usid AS (
                            INSERT INTO usid_list (player_id, usid, server)
                            SELECT id, ?, ?
                            FROM update_players
                            ON CONFLICT (usid, server) DO NOTHING
                        )
                        SELECT *
                        FROM update_players;
                        """,
                stmt->{
                    stmt.setString(1, p.uuid());
                    stmt.setString(2, p.name());
                    stmt.setString(3, p.ip());
                    stmt.setString(4, p.locale);
                    stmt.setString(5, p.color.toString());
                    stmt.setString(6, p.usid());
                    stmt.setInt(7, serverId);
                },
                PlayerData::getPlayerData
        );
        if(!cache.containsKey(p) && pd.isPresent())
            cache.put(p, pd.get());
        return pd;
    }

    public static Optional<PlayerData> getPlayerData(int id) {
        return Database.executeQueryAsync(
                "SELECT * FROM players WHERE id = ?",
                stmt->stmt.setInt(1, id),
                PlayerData::getPlayerData
        );
    }

    public static Optional<PlayerData> getPlayerData(Player player) {
        if(cache.containsKey(player))
            return Optional.of(cache.get(player));

        return Database.executeQueryAsync(
                "SELECT * FROM players WHERE uuid = ?",
                stmt->stmt.setString(1, player.uuid()),
                PlayerData::getPlayerData
        );
    }

    public static Optional<Integer> getPlayerId(Player player) {
        if(cache.containsKey(player))
            return Optional.of(cache.get(player).id);

        return Database.executeQueryAsync(
                "SELECT id FROM players WHERE uuid = ?",
                stmt->stmt.setString(1, player.uuid()),
                rs->rs.getInt("id")
        );
    }

    public static PlayerData getPlayerData(ResultSet rs) throws SQLException {
        return new PlayerData(rs.getInt("id"), rs.getString("uuid"), rs.getObject("discord_id", Long.class));
    }
}
