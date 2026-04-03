package plugin.antigrief

import mindustry.gen.Player
import plugin.Bundle
import plugin.PVars
import plugin.database.Database
import plugin.database.models.PlayerData
import plugin.database.putLog
import java.sql.PreparedStatement
import java.sql.ResultSet

fun apply(p: Player, isp: String?, pd: PlayerData) {
    if (!(isGraylisted(isp) && pd.discordId == null)) return
    p.kick(Bundle.get("kick.graylisted", p.locale, PVars.discordLink), 0)
    putLog(pd.id, "graylist", "Player graylisted by IP " + p.ip())
}

fun isGraylisted(isp: String?): Boolean {
    return Database.executeQueryAsync(
        """
                        SELECT EXISTS(
                            SELECT 1 FROM graylist WHERE isp ILIKE ?
                        )
                        
                        """.trimIndent(),
        { stmt: PreparedStatement? -> stmt!!.setString(1, isp) },
        { rs: ResultSet? -> rs!!.getBoolean("exists") }
    ).orElse(false)
}