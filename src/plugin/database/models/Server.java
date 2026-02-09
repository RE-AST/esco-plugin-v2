package plugin.database.models;

import plugin.database.Database;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import static plugin.PVars.gamemode;

public class Server {
    public String name;
    public int id;

    public Server(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public static Optional<Server> getOrCreateServer() {
        return Database.executeQueryAsync(
                """
                        INSERT INTO servers (name)
                        VALUES (?)
                        ON CONFLICT (name)
                        DO UPDATE SET name = EXCLUDED.name
                        RETURNING id, name
                        """,
                stmt->stmt.setString(1, gamemode.simpleName),
                Server::getServer
        );
    }

    public static Server getServer(ResultSet rs) throws SQLException {
        return new Server(
                rs.getInt("id"),
                rs.getString("name")
        );
    }
}
