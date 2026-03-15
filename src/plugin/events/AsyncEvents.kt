package plugin.events

import arc.Events
import arc.func.Cons
import mindustry.game.EventType.PlayerConnect
import plugin.PVars
import plugin.antigrief.apply
import plugin.database.models.Admin
import plugin.database.models.Ban
import plugin.database.models.Log
import plugin.database.models.PlayerData
import plugin.discord.sendLog
import plugin.utils.ApiResponse
import plugin.utils.Permission
import plugin.utils.isAnon
import java.util.function.Consumer

fun loadEvents() {
    Events.on<PlayerConnect>(PlayerConnect::class.java, Cons { e: PlayerConnect ->  // pre-connect
        val player = e.player

        val pdataOpt = PlayerData.getOrCreatePlayerData(player)
        if (pdataOpt.isEmpty()) {
            player.kick("[scarlet]Failed to create player!", 0)
            return@Cons
        }
        val pd = pdataOpt.get()
        pd.getUsid().ifPresent(Consumer { u: String? ->
            if (u != player.usid()) {
                Log.putLog(pd.id, "system", "Possible account thief")
                player.kick(
                    "Failed to get player data.\nUsid in database is different from current!\nPlease contact us.\nDiscord: " + PVars.discordLink,
                    0
                )
                sendLog("Possible account thief! Usid: " + player.usid() + " Database: " + u)
            }
        })

        isAnon(player.ip(), Cons { resp: ApiResponse? ->
            if (resp!!.anon && pd.discordId == null) {
                Log.putLog(pd.id, "system", "Detected using vpn or proxy.")
                player.kick("You detected by [pink]AntiVPN[] system\nTry re-connect and disable vpn/proxy\nOr try linking your discord by /link\nDiscord: " + PVars.discordLink)
            }
            //AntiFimoz.apply(resp.isp, player);
            //if(player.con.isConnected())
            apply(player, resp.isp, pd)
        })

        val banOpt = Ban.getBan(player)
        if (banOpt.isPresent()) {
            val ban = banOpt.get()
            ban.kickPlayer(player)
            Log.putLog(pd.id, "system", "Ban " + ban.id + " hit!")
            sendLog(
                "New ban hit!\nReason: " + ban.reason + "\n" + "ID: " + ban.id + "\nNickname: " + player.plainName()
                    .replace("@", "")
            )
            return@Cons
        }

        Admin.getAdmin(player).ifPresent(Consumer { a: Admin ->
            if (a.perms.contains(Permission.admin) && !a.hidden) player.admin(true)
            if (a.perms.size > 1) player.sendMessage("Your permissions " + Permission.seqToString(a.perms))
        })
        if (PVars.mapVote != null) PVars.mapVote.checkPass()
    })
}