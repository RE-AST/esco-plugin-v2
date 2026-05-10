package plugin.events

import arc.Core.app
import arc.Events
import arc.util.Log
import arc.util.Timer
import com.xpdustry.nohorny.client.ClassificationEvent
import com.xpdustry.nohorny.common.MindustryImageRenderer
import com.xpdustry.nohorny.common.Rating
import kotlinx.coroutines.launch
import mindustry.Vars
import mindustry.game.EventType
import mindustry.game.EventType.PlayerConnect
import mindustry.game.Team
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.utils.FileUpload
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import plugin.KVars
import plugin.KVars.eventsScope
import plugin.KVars.mapStats
import plugin.PVars
import plugin.antigrief.apply
import plugin.database.createOrGetMapStats
import plugin.database.getAdmin
import plugin.database.getBan
import plugin.database.*
import plugin.database.getPlayerId
import plugin.database.models.Admin
import plugin.database.putLog
import plugin.database.updateMapStats
import plugin.discord.sendLog
import plugin.utils.ApiResponse
import plugin.utils.Permission
import plugin.utils.isAnon
import plugin.utils.onAsync
import plugin.utils.parseImage
import java.awt.Color
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
                    putLog(pd.id, "system", "Detected using vpn or proxy. IP ${player.ip()}")
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
                    if(pd.prefs.customName.isNotEmpty())
                        player.name(pd.prefs.customName)
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

    Events.on(EventType.WorldLoadEvent::class.java) { _: EventType.WorldLoadEvent ->
        Timer.schedule({
            KVars.startTime = System.currentTimeMillis()
            eventsScope.launch {
                createOrGetMapStats(Vars.state.map.plainName()).ifPresent { stats ->
                    app.post {
                        mapStats = stats
                    }
                }
            }
        }, 1f)
    }

    onAsync(EventType.GameOverEvent::class.java) { e: EventType.GameOverEvent ->
        val stats = mapStats ?: return@onAsync

        val wave = Vars.state.wave
        val playtime = ((System.currentTimeMillis() - KVars.startTime) / 1000L).toInt() // seconds

        val minWave = minOf(stats.minWave, wave)
        val maxWave = maxOf(stats.maxWave, wave)

        val minPlaytime =
            if (stats.minPlaytime <= 0) playtime
            else minOf(stats.minPlaytime, playtime)

        val maxPlaytime = maxOf(stats.maxPlaytime, playtime)

        var wins = stats.wins
        var loses = stats.loses
        var skips = stats.skips
        if(e.winner == Team.derelict) {
            skips += 1
        } else if(e.winner == Vars.state.rules.defaultTeam) {
            wins += 1
        } else {
            loses += 1
        }

        updateMapStats(
            stats.name,
            minWave,
            maxWave,
            minPlaytime,
            maxPlaytime,
            wins,
            loses,
            skips
        )
    }

    onAsync(ClassificationEvent::class.java) { e: ClassificationEvent ->
        if(e.rating != Rating.NSFW) return@onAsync
        val embed = EmbedBuilder()
            .setColor(Color.red)
            .setTitle("NSFW detected on ${PVars.gamemode.name} (Rate: ${e.rating.name})")
        e.author?.uuid?.let { uuid ->
            getPlayerData(uuid).ifPresent { pd ->
                embed.setAuthor("[${pd.id}] ${pd.lastName}")
            }
        }
        val message = PVars.nsfwChannel.sendMessageEmbeds(embed.build())
        try {
            val image = parseImage(MindustryImageRenderer.render(e.group))
            message.addFiles(FileUpload.fromData(image, "image.png"))
            embed.setImage("attachment://image.png")
        } catch (e: Exception) {
            Log.err("Error while rendering nsfw image", e)
        }
        message.queue()
    }
}
