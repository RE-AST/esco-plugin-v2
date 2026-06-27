package plugin.database.models

import plugin.PVars.gamemode
import plugin.database.Database.executeQuery
import java.sql.ResultSet
import java.sql.SQLException
import java.util.*

class Server(
    val id: Int,
    val name: String
) {

    companion object {
        fun getOrCreateServer(): Optional<Server> {
            val server = executeQuery(
                """
                SELECT * FROM servers
                WHERE name = ?
                """.trimIndent(), { stmt ->
                    stmt.setString(1, gamemode.simpleName)
                }, { rs ->
                    getServer(rs)
                })

            if (server.isPresent) {
                return server
            }

            return executeQuery(
                """
                INSERT INTO servers (name)
                VALUES (?)
                ON CONFLICT (name)
                DO UPDATE SET name = EXCLUDED.name
                RETURNING id, name
                """.trimIndent(),
                { stmt ->
                    stmt.setString(1, gamemode.simpleName)
                }, { rs ->
                    getServer(rs)
                })
        }

        @JvmStatic
        @Throws(SQLException::class)
        fun getServer(rs: ResultSet): Server {
            return Server(
                rs.getInt("id"),
                rs.getString("name")
            )
        }
    }
}