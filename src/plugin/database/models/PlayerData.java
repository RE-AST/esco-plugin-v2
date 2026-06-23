package plugin.database.models;

import arc.struct.ObjectMap;
import arc.util.Time;
import arc.util.Timekeeper;
import mindustry.gen.Player;
import org.postgresql.util.PGobject;

import java.util.Optional;

import static arc.util.Log.err;
import static plugin.PVars.*;
import static plugin.PVars.gamemode;
import static plugin.database.Database.executeQuery;
import static plugin.database.Database.executeUpdate;

public class PlayerData {
    public int id;
    public String uuid, lastName;
    public Long discordId;
    public PlayerPrefs prefs;
    private String usid;
    // stats
    public long playtime;
    public int blocksBuild, blocksBroken, balance, wavesSurvived;

    public transient Timekeeper lastGambling;

    /*public transient String originalName = "frog"; // set when player join

    public PlayerData setOriginalName(String s) {
        this.originalName = s;
        return this;
    }*/

    public PlayerData(int id, String uuid, Long discordId, PlayerPrefs prefs, String lastName, long playtime, int blocksBuild, int blocksBroken, int balance, int wavesSurvived) {
        this.id = id;
        this.uuid = uuid;
        this.discordId = discordId;
        this.prefs = prefs;
        this.lastName = lastName;
        // stats
        this.playtime = playtime; // sec!!
        this.blocksBuild = blocksBuild;
        this.blocksBroken = blocksBroken;
        this.balance = balance;
        this.wavesSurvived = wavesSurvived;
    }

    public boolean updateDiscordId(Long dsid) {
        boolean updated = executeUpdate(
                """
                        UPDATE players SET discord_id = ?
                        WHERE id = ?
                        """,
                stmt -> {
                    stmt.setLong(1, dsid);
                    stmt.setInt(2, id);
                }
        );
        if (updated)
            this.discordId = dsid;
        return updated;
    }

    public boolean updatePrefs() {
        try {
            PGobject object = new PGobject();
            object.setType("jsonb");
            object.setValue(objectMapper.writeValueAsString(prefs));
            return executeUpdate(
                    """
                            UPDATE players SET prefs = ? WHERE id = ?
                            """,
                    stmt -> {
                        stmt.setObject(1, object);
                        stmt.setInt(2, id);
                    }
            );
        } catch (Exception e) {
            err(e);
            return false;
        }
    }

    public Optional<String> getUsid() {
        if(usid != null) {
            return Optional.of(usid);
        }
        Optional<String> usidOpt =  executeQuery(
                """
                        SELECT usid FROM usid_list
                        WHERE player_id = ? AND server = ?
                        """,
                stmt -> {
                    stmt.setInt(1, id);
                    stmt.setInt(2, serverId);
                },
                rs -> rs.getString("usid")
        );
        if(usidOpt.isPresent())
            usid = usidOpt.get();
        return usidOpt;
    }

    // region PlayerStats
    public static ObjectMap<String, Long> joinTime = new ObjectMap<>();

    public PlayerData setLastGambling(Timekeeper time) {
        lastGambling = time;
        return this;
    }

    public boolean write() {
        return executeUpdate("UPDATE players SET playtime = ?, blocks_build = ?, blocks_broken = ?, balance = ?, waves_survived = ? WHERE id = ?",
                stmt -> {
                    stmt.setLong(1, playtime);
                    stmt.setInt(2, blocksBuild);
                    stmt.setInt(3, blocksBroken);
                    stmt.setInt(4, balance);
                    stmt.setInt(5, wavesSurvived);
                    stmt.setInt(6, id);
                });
    }

    public PlayerData adjBlocksBuild() {
        this.blocksBuild += 1;
        if (blocksBuild % 50 == 0)
            adjBalance(gamemode.blockCost);
        return this;
    }

    public PlayerData adjBlocksBroken() {
        this.blocksBroken += 1;
        return this;
    }

    public PlayerData adjWavesSurvived() {
        this.wavesSurvived += 1;
        adjBalance(gamemode.waveCost);
        return this;
    }

    public PlayerData adjWins() {
        return adjBalance(gamemode.winCost);
    }

    public PlayerData adjBalance() {
        return adjBalance(1);
    }

    public PlayerData adjBalance(int count) {
        this.balance += count;
        return this;
    }

    public PlayerData subBalance(int count) {
        this.balance -= count;
        return this;
    }

    public static void setJoinTime(Player p) {
        joinTime.put(p.uuid(), Time.millis());
    }

    public PlayerData update(Player player, boolean purge) {
        synchronized (joinTime) {
            Long time = joinTime.remove(player.uuid());
            if (time != null) {
                long deltaSec = (Time.millis() - time) / 1000;
                if (deltaSec > 0)
                    playtime += deltaSec;
            }
            if (!purge)
                setJoinTime(player);
        }
        if (purge)
            write();

        return this;
    }

    /*public static void purge(Player player) {
        PlayerData stats = playerStatsCache.get(player.uuid());

        if (stats != null) {
            stats.update(player, true);
        }

        playerStatsCache.remove(player.uuid());
    }*/
    // endregion
}
