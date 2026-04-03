package plugin.events

import arc.Core.app
import arc.Events
import kotlinx.coroutines.launch
import mindustry.game.EventType.PlayerConnect
import plugin.KVars.eventsScope
import plugin.PVars
import plugin.antigrief.apply
import plugin.database.getAdmin
import plugin.database.getBan
import plugin.database.getOrCreatePlayerData
import plugin.database.models.Admin
import plugin.database.putLog
import plugin.discord.sendLog
import plugin.utils.ApiResponse
import plugin.utils.Permission
import plugin.utils.isAnon
import java.util.function.Consumer

fun loadEvents() {
    Events.on(PlayerConnect::class.java) { e: PlayerConnect ->  // pre-connect
        val player = e.player

        eventsScope.launch {
            val pdataOpt = getOrCreatePlayerData(player)
            if (pdataOpt.isEmpty) {
                app.post {
                    player.kick("[scarlet]Failed to create player!", 0)
                }
                return@launch
            }
            val pd = pdataOpt.get()
            pd.usid.ifPresent(Consumer { u: String ->
                if (u != player.usid()) {
                    putLog(pd.id, "system", "Possible account thief")
                    app.post {
                        player.kick(
                            "Failed to get player data.\nUsid in database is different from current!\nPlease contact us.\nDiscord: " + PVars.discordLink,
                            0
                        )
                    }
                    sendLog("Possible account thief! Usid: " + player.usid() + " Database: " + u)
                }
            })

            isAnon(player.ip()) { resp: ApiResponse ->
                if (resp.anon && pd.discordId == null) {
                    putLog(pd.id, "system", "Detected using vpn or proxy.")
                    app.post {
                        player.kick(
                            "You detected by [pink]AntiVPN[] system\nTry re-connect and disable vpn/proxy\nOr try linking your discord by /link\nDiscord: " + PVars.discordLink,
                            0
                        )
                    }
                }
                //AntiFimoz.apply(resp.isp, player);
                //if(player.con.isConnected())
                app.post {
                    apply(player, resp.isp, pd)
                }
            }

            val banOpt = getBan(player)
            if (banOpt.isPresent) {
                val ban = banOpt.get()
                app.post {
                    ban.kickPlayer(player)
                }
                putLog(pd.id, "system", "Ban " + ban.id + " hit!")
                sendLog(
                    "New ban hit!\nReason: " + ban.reason + "\n" + "ID: " + ban.id + "\nNickname: " + player.plainName()
                        .replace("@", "")
                )
                return@launch
            }

            getAdmin(player).ifPresent(Consumer { a: Admin ->
                app.post {
                    if (a.perms.contains(Permission.admin) && !a.hidden) player.admin(true)
                    if (a.perms.size > 1) player.sendMessage("Your permissions " + Permission.seqToString(a.perms))
                }
            })
            if (PVars.mapVote != null) PVars.mapVote.checkPass()
        }
    }
}
