package plugin.database.models;

import arc.util.Strings;
import mindustry.net.Administration;
import mindustry.net.Administration.ActionType.*;
import mindustry.world.Tile;
import plugin.PVars;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

import static mindustry.net.Administration.ActionType.breakBlock;
import static plugin.PVars.logsBuffer;
import static plugin.database.Database.executeUpdate;

public class Log {
    public int id, serverId;
    public Integer playerId = null;
    public String type, message;
    public Instant timestamp;

    protected boolean fromDb = false;
    // created from db
    public Log(int id, int serverId, Integer playerId, String type, String message, Instant timestamp) {
        this.id = id;
        this.serverId = serverId;
        this.playerId = playerId;
        this.type = type;
        this.message = message;
        this.timestamp = timestamp;
    }
    // created from action
    public Log(Integer playerId, String type, String message) {
        this.id = -1;
        this.serverId = PVars.serverId;
        this.playerId = playerId;
        this.type = type;
        this.message = message;
        this.timestamp = Instant.now();
    }

    public boolean write() {
        if(fromDb) return false;
        this.fromDb = true;
        return executeUpdate(
                """
                        INSERT INTO logs (type, message, timestamp, server_id, player_id) VALUES (?, ?, ?, ?, ?)
                        """,
                stmt->{
                    stmt.setString(1, type);
                    stmt.setString(2, message);
                    stmt.setTimestamp(3, Timestamp.from(timestamp));
                    stmt.setInt(4, serverId);
                    if (playerId == null) {
                        stmt.setNull(5, java.sql.Types.INTEGER);
                    } else {
                        stmt.setInt(5, playerId);
                    }
                }
        );
    }

    public static Log getLog(ResultSet rs) throws SQLException {
        int pid = rs.getInt("player_id");
        Integer playerId = rs.wasNull() ? null : pid;

        Log log = new Log(
                rs.getInt("id"),
                rs.getInt("server_id"),
                playerId,
                rs.getString("type"),
                rs.getString("message"),
                rs.getTimestamp("timestamp").toInstant()
        );
        log.fromDb = true;
        return log;
    }

    public static void putLog(String type, String message) {
        logsBuffer.add(new Log(null, type, message));
    }

    public static void putLog(Integer pid, String type, String message) {
        logsBuffer.add(new Log(pid, type, message));
    }

    public static void putLog(Administration.PlayerAction action, PlayerData pd) {
        String message;
        Administration.ActionType type = action.type;

        Tile tile = action.tile;
        if(tile == null) return;
        if(tile.block().isAir()) return;

        switch(type) {
            case breakBlock:
                message = Strings.format("Player break block @ at @ @", tile.block().name, tile.x, tile.y);
                break;
            //case buildSelect:
            case placeBlock:
                message = Strings.format("Player placed block @ at @ @", action.block, tile.x, tile.y);
                break;
            case rotate:
                message = Strings.format("Player rotated block @ at @ @", tile.block().name, tile.x, tile.y);
                break;
            case configure:
                message = Strings.format("Player configured block @ at @ @", tile.block().name, tile.x, tile.y);
                break;
            default:
                return;
        }

        putLog(pd.id, "action", message);
    }
}
