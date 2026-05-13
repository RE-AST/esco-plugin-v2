package plugin.events

import arc.Core.app
import arc.Events
import arc.struct.ObjectMap
import arc.util.Log
import arc.util.Strings
import arc.util.Timer
import com.xpdustry.nohorny.client.ClassificationEvent
import com.xpdustry.nohorny.common.MindustryImageRenderer
import com.xpdustry.nohorny.common.Rating
import kotlinx.coroutines.launch
import mindustry.Vars
import mindustry.game.EventType
import mindustry.game.EventType.PlayerConnect
import mindustry.game.EventType.PlayerJoin
import mindustry.game.Team
import mindustry.gen.Call
import mindustry.gen.Groups
import mindustry.gen.Player
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.utils.FileUpload
import plugin.Bundle
import plugin.KVars
import plugin.KVars.eventsScope
import plugin.KVars.mapStats
import plugin.PVars
import plugin.antigrief.apply
import plugin.database.*
import plugin.database.models.Admin
import plugin.database.models.PlayerData
import plugin.database.models.PlayerStats
import plugin.discord.*
import plugin.menus.showWelcome
import plugin.utils.*
import plugin.utils.Loader.exit
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
            if(PVars.gamemode != Gamemode.hub)
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

    onAsync(PlayerJoin::class.java) { e ->
        // full connect
        val player: Player = e.player

        val pdOpt = getPlayerData(player)
        if (pdOpt.isEmpty) {
            app.post {
                player.kick("[scarlet]Failed to create player, try re-join")
            }
            return@onAsync
        }
        val pd = pdOpt.get()
        app.post {
            PlayerStats.setJoinTime(player)
        }

        //pd.setOriginalName(player.coloredName());
        getPlayerStats(player)

        app.post {
            Bundle.sendMessage("messages.join", pd.id.toString(), player.coloredName())
            putLog(pd.id, "event", "Player joined!")
        }

        Log.info("[@] Player @ joined [@]", pd.id, player.plainName(), player.uuid())
        app.post {
            sendJoinMessage(player, pd.id)

            Call.clientPacketReliable(player.con, "SendMeSubtitle", if (player == null) null else player.id.toString())
            if (pd.prefs.showWelcomeMenu) showWelcome(player)
        }


        // simple bot check
        Timer.schedule(Runnable {
            if (player.con.isConnected() && player.con.lastReceivedClientSnapshot == -1) {
                putLog(pd.id, "system", "Player detected as bot")
                player.kick("[scarlet]Try reconnect\nDiscord " + PVars.discordLink, 0)
            }
        }, 2f)
    }

    Events.on(EventType.PlayerLeave::class.java) { e ->
        val player = e.player
        if (player != null /* how? */) PVars.SSUsers.remove(player.id)

        val pdOpt = getPlayerData(player!!)
        if (pdOpt.isPresent()) {
            val pd = pdOpt.get()
            Bundle.sendMessage("messages.leave", pd.id.toString(), player.coloredName())
            Log.info("[@] Player @ left [@]", pd.id, player.plainName(), player.uuid())
            sendLeaveMessage(player, pd.id)
            putLog(pd.id, "event", "Player disconnected")
        }
        if (PVars.currentlyKicking != null && PVars.currentlyKicking.target == player) {
            ban(
                PVars.currentlyKicking.targetId,
                PVars.currentlyKicking.startedId,
                "AutoBan: Leave during votekick\n" + PVars.currentlyKicking.reason,
                (2 * 60 * 60).toLong()
            )
            PVars.currentlyKicking.cancel()
            Bundle.sendMessage("votekick.targetleft")
        }

        purgeData(player)


        /*if(rtvVotes.contains(player)) {
                rtvVotes.remove(player);
                Bundle.sendMessage("rtv.playerleft", rtvVotes.size+"/"+Math.max(1, (int) Math.round(Groups.player.size() * 0.8)));
            }*/
        Timer.schedule(Runnable {
            if (PVars.mapVote != null) PVars.mapVote.checkPass()
            if (Groups.player.isEmpty() && PVars.needRestart) {
                exit()
            }
        }, 0.2f)
    }

    onAsync(EventType.PlayerChatEvent::class.java) { e ->
        val player = e.player
        val message = e.message

        getPlayerData(player).ifPresent(Consumer { pd: PlayerData? ->
            putLog(pd!!.id, "event", "Player sent message $message")
        })
        if (!message.startsWith("/")) {
            val content =
                ("`" + player.plainName() + ": " + stripFoo(Strings.stripColors(message)) + "`").replace("@", "")
            sendServerMessage(content)
            if (Math.random() > 0.9) sendParrotMessage(content)
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
        val response = e.response
        if(response.rating != Rating.NSFW) return@onAsync

        val embed = EmbedBuilder()
            .setColor(Color.red)
            .setTitle("NSFW detected on ${PVars.gamemode.name} (Confidence: ${response.confidence.toInt()})")
            .setImage("attachment://image.png")

        var playerId: Int? = null

        e.author?.uuid?.let { uuid ->
            getPlayerData(uuid).ifPresent { pd ->
                playerId = pd.id
                embed.setAuthor("[${pd.id}] ${pd.lastName}")
            }
        }

        val message = PVars.nsfwChannel.sendMessageEmbeds(embed.build())

        playerId?.let {
            message.addComponents(ActionRow.of(
                Button.danger("$nohornyBanButtonId:$it", "🔨Ban")
            )
            )
        }

        try {
            val image = parseImage(MindustryImageRenderer.render(e.group))
            message.addFiles(FileUpload.fromData(image, "image.png"))
        } catch (ex: Exception) {
            Log.err("Error while rendering nsfw image", ex)
            message.addContent("Error while rendering nsfw image")
        }

        message.queue()
    }
}

fun purgeData(p: Player) {
    getPlayerId(p).ifPresent(Consumer { id: Int? ->
        mutesCache.remove(id)
    })
    Permission.cache.remove(p)
    playerDataCache.remove(p)
    adminsCache.remove(p)
    PlayerStats.purge(p)
    PVars.historyPlayers.remove(p)
    PVars.vanishedPlayers.remove(p)

    if (PVars.linkCodes.containsValue(
            p,
            false
        )
    ) PVars.linkCodes.forEach(Consumer { e: ObjectMap.Entry<String, Player> ->
        if (e.value == p) app.post { PVars.linkCodes.remove(e.key) }
    })
}