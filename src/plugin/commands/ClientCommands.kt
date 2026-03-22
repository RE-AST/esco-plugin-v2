package plugin.commands

import arc.Core
import arc.Events
import arc.struct.ObjectMap
import arc.struct.Seq
import arc.util.CommandHandler.CommandRunner
import arc.util.Strings
import arc.util.Time
import arc.util.Timekeeper
import kotlinx.coroutines.launch
import mindustry.Vars
import mindustry.game.EventType
import mindustry.game.Team
import mindustry.gen.Call
import mindustry.gen.Groups
import mindustry.gen.Player
import mindustry.graphics.Pal
import mindustry.net.Administration
import plugin.Bundle
import plugin.PVars
import plugin.database.*
import plugin.database.models.PlayerStats
import plugin.menus.showShop
import plugin.menus.slot
import plugin.utils.*
import plugin.votes.VoteMap
import plugin.votes.VoteWave
import plugin.votes.VotekickSession
import java.util.*
import java.util.function.Consumer
import kotlin.math.roundToInt

import mindustry.maps.Map

import plugin.KVars.globalScope

const val commandsPerPage = 10
var voteCooldown = 60 * 5

fun register(handler: CustomHandler) {
    handler.registerCommand("maps", "", CommandRunner { _: Array<String>, p: Player ->
        Vars.maps.customMaps().each { m: Map ->
            p.sendMessage("${m.name()} : ${m.author()}")
        }
    })
    handler.registerCommand("vanish", "", Permission.vanish, CommandRunner { _: Array<String>, p: Player ->
        if (PVars.vanishedPlayers.contains(p)) {
            PVars.vanishedPlayers.remove(p)
            p.sendMessage("unvanished")
            return@CommandRunner
        }
        p.sendMessage("vanished")
        PVars.vanishedPlayers.add(p)
        Call.playerDisconnect(p.id)
    })
    handler.registerCommand("pay", "<amount> <playername...>", CommandRunner { args: Array<String>, player: Player ->
        val target = Groups.player.find { p: Player -> p.plainName().equals(args[1], ignoreCase = true) }
        if (target == null || target === player) {
            player.sendMessage("[scarlet]Player with that name not found!")
            return@CommandRunner
        }
        if (!Strings.canParseInt(args[0])) {
            Bundle.sendMessage("args.mustbeint", player, "<amount>")
            return@CommandRunner
        }
        val targetStatsOpt = getPlayerStats(target)
        val playerStatsOpt = getPlayerStats(player)
        if (targetStatsOpt.isEmpty || playerStatsOpt.isEmpty) {
            player.sendMessage("[scarlet]Unknown error")
            return@CommandRunner
        }
        val targetStats = targetStatsOpt.get()
        val playerStats = playerStatsOpt.get()
        val amount = Strings.parseInt(args[0])
        if (amount < 1) {
            player.sendMessage("Amount must be > 0")
            return@CommandRunner
        }
        if (amount > playerStats.balance) {
            Bundle.sendMessage("menu.shop.nomoney", player)
            return@CommandRunner
        }
        val commision = (amount * 0.02f).roundToInt()
        playerStats.subBalance(amount)
        targetStats.adjBalance(amount - commision)
        target.sendMessage("[green]Player " + player.coloredName() + " [green]give you $[white]" + amount + " [green](commision $[white]" + commision + "[green])")
        player.sendMessage("[green]You give " + target.coloredName() + " [green]$[white]" + amount + " [green](commision $[white]" + commision + "[green])")
    })
    handler.registerCommand("economy", "") { _: Array<String?>?, p: Player? ->
        Bundle.infoMessage("infomessage.economyguide", p)
    }
    handler.registerCommand("slot", "<bet>", CommandRunner { a: Array<String>, p: Player ->
        if (!Strings.canParseInt(a[0])) {
            Bundle.sendMessage("args.mustbeint", p, "<bet>")
            return@CommandRunner
        }
        globalScope.launch {
            getPlayerStats(p)
                .ifPresent(Consumer { s: PlayerStats -> Core.app.post { slot(p, s, Strings.parseInt(a[0])) } })
        }
    })
    handler.registerCommand("shop", CommandRunner { _: Array<String>, p: Player ->
        globalScope.launch {
            getPlayerStats(p).ifPresent(
                Consumer { s: PlayerStats -> Core.app.post { showShop(s, p) } })
        }
    })
    handler.registerCommand("sync", CommandRunner { _: Array<String>, player: Player ->
        if (Time.timeSinceMillis(player.info.lastSyncTime) < 1000 * 5) {
            player.sendMessage("[scarlet]You may only /sync every 5 seconds.")
            return@CommandRunner
        }
        player.info.lastSyncTime = Time.millis()
        Call.worldDataBegin(player.con)
        Vars.netServer.sendWorldData(player)
    })
    handler.registerCommand("help", "[page]", CommandRunner { args: Array<String>, player: Player ->
        if (args.isNotEmpty() && !Strings.canParseInt(args[0])) {
            player.sendMessage("[scarlet]\"page\" must be a integer.")
            return@CommandRunner
        }
        val page = if (args.isNotEmpty()) Strings.parseInt(args[0]) - 1 else 0

        var availableCommands = 0

        val result = StringBuilder()
        val perms = Permission.getPerms(player)

        val pages = Seq<String?>()

        for (i in 0..<handler.commands.size) {
            val c = handler.commands.get(i) ?: continue

            if (!perms.contains(c.permission)) continue

            if (availableCommands >= commandsPerPage) {
                pages.add(result.toString())
                result.setLength(0)
                availableCommands = 0
            }

            val req = "commands." + c.name + ".description"
            var desc = Bundle.get(req, player.locale)
            if (desc == req) desc = Bundle.get("commands.nodesc", player.locale)

            availableCommands++
            result.append("[orange]/")
                .append(c.name)
                .append(" [white]")
                .append(c.args)
                .append(" - ")
                .append(desc)
                .append("\n")
        }

        if (result.isNotEmpty()) pages.add(result.toString())

        if ((page + 1) > pages.size || page < 0) {
            Bundle.sendMessage("commands.unknownpage", player)
            return@CommandRunner
        }
        val resp = "[orange]-- Commands Page " + (page + 1) + "/" + pages.size + " --\n\n" + pages.get(page)
        player.sendMessage(resp)
    })
    handler.registerCommand("test", "", Permission.test, CommandRunner { _: Array<String>, p: Player ->
        p.sendMessage("[green]Ok!")
    })

    handler.registerCommand("stats", "", CommandRunner { _: Array<String>, p: Player ->
        val sb = StringBuilder("[stat]Stats:\n")
        getPlayerStats(p).ifPresent(Consumer { s: PlayerStats ->
            s.update(p, false)
            sb.append("Blocks build: ").append(s.blocksBuild).append("\n")
            sb.append("Blocks broken: ").append(s.blocksBroken).append("\n")
            sb.append("Waves survived: ").append(s.wavesSurvived).append("\n")
            sb.append("Balance: [green]$[]").append(s.balance).append("\n")
            sb.append("Playtime: ").append(formatTime(s.playtime))
        })
        p.sendMessage(sb.toString())
    })

    handler.registerCommand("vnw", "[y/n]", CommandRunner { a: Array<String>, p: Player ->
        val i = if (a.isEmpty()) 1
        else parseBool(a[0])

        if (i == 0) {
            Bundle.sendMessage("vote.unknownvote", p)
            return@CommandRunner
        }
        if (PVars.waveVote == null) {
            PVars.waveVote = VoteWave()
            PVars.waveVote.vote(p, i)
            return@CommandRunner
        }
        if (PVars.waveVote.voted.containsKey(p.ip())) {
            Bundle.sendMessage("rtv.error.voted", p)
            return@CommandRunner
        }
        PVars.waveVote.vote(p, i)
    })

    handler.registerCommand("rtv", "[y/n] [map...]", CommandRunner { a: Array<String>, p: Player ->
        var map: Map? = null
        if(a.size == 2 && PVars.mapVote == null) {
            map = findMap(a[1])
            if(map == null) {
                p.sendMessage("[scarlet]Not found")
                return@CommandRunner
            }
        }
        val i: Int = if (a.isEmpty()) 1
        else parseBool(a[0])
        if (i == 0) {
            Bundle.sendMessage("vote.unknownvote", p)
            return@CommandRunner
        }
        if (PVars.mapVote == null) {
            PVars.mapVote = VoteMap(p, map)
            PVars.mapVote.vote(p, i)
            return@CommandRunner
        }
        if (PVars.mapVote.voted.containsKey(p.ip())) {
            Bundle.sendMessage("rtv.error.voted", p)
            return@CommandRunner
        }
        PVars.mapVote.vote(p, i)
    })

    handler.registerCommand(
        "ban",
        "<id> <time> <reason...>",
        Permission.punish,
        CommandRunner { a: Array<String>, p: Player ->
            if (Strings.canParseInt(a[0])) {
                val id = Strings.parseInt(a[0])
                val time = parseTime(a[1])
                val perm = a[1].equals("perm", ignoreCase = true)
                if (time == -1L && !perm) {
                    p.sendMessage("[scarlet]Unknown time, use d w m y or perm!")
                    return@CommandRunner
                }
                val banned: Boolean = if (perm) {
                    ban(id, p, a[2], -1)
                } else {
                    ban(id, p, a[2], time)
                }
                if (banned) {
                    p.sendMessage("[green]Player banned!")
                } else {
                    p.sendMessage("[scarlet]Failed to ban player")
                }
            } else {
                p.sendMessage("[scarlet]ID must be int!")
            }
        })

    handler.registerCommand("history", CommandRunner { _: Array<String>, p: Player ->
        if (PVars.historyPlayers.contains(p)) {
            PVars.historyPlayers.remove(p)
            p.sendMessage("[scarlet]Disabled")
            Call.hideHudText(p.con)
        } else {
            PVars.historyPlayers.add(p)
            p.sendMessage("[green]Enabled!")
        }
    })

    handler.registerCommand("discord", CommandRunner { _: Array<String>, p: Player ->
        Call.openURI(p.con, PVars.discordLink)
    })

    handler.registerCommand("link", CommandRunner { _: Array<String>, p: Player ->
        val pdOpt = getPlayerData(p)
        if (pdOpt.isPresent && pdOpt.get().discordId != null) {
            p.sendMessage("Account already linked!")
            return@CommandRunner
        }
        var code: String? = PVars.linkCodes.findKey(p, false)

        if(code == null) {
            code = getRandomString(6)
            PVars.linkCodes.put(code, p)
        }

        Bundle.infoMessage("discord.link", p, PVars.gamemode.botPrefix, code, PVars.discordLink)
    })

    handler.registerCommand("hidden", "<bool>", Permission.admin, CommandRunner { a: Array<String>, p: Player ->
        val i = parseBool(a[0])
        val idOpt = getPlayerId(p)
        if (idOpt.isEmpty) {
            return@CommandRunner
        }
        val id: Int = idOpt.get()
        when (i) {
            1 -> {
                updateAdminHidden(id, true)
                p.admin(false)
                p.sendMessage("[green]Ok!")
            }

            -1 -> {
                updateAdminHidden(id, false)
                p.admin(true)
                p.sendMessage("[green]Ok!")
            }

            else -> {
                p.sendMessage("[scarlet]Unknown bool! Use y/yes/д/да/t or n/no/н/нет/f")
            }
        }
    })


    /*
            handler.registerCommand("team", "<team>", (arg, player)->{
                if(!Strings.canParseInt(arg[0])) {
                    sendMessage("args.mustbeint", player, "<team>");
                    return@CommandRunner;
                }
                int id = Strings.parseInt(arg[0]);
                if(id > 5) {
                    sendMessage("args.lessthan", player, "<team>", 5);
                    return@CommandRunner;
                }
                player.team(Team.get(id));
            });*/
    handler.registerCommand("artv", "", Permission.artv, CommandRunner { _: Array<String>, _: Player ->
        Events.fire(EventType.GameOverEvent(Team.derelict))
    })

    handler.registerCommand("a", "<message...>", Permission.admin, CommandRunner { arg: Array<String>, p: Player ->
        val raw = "[#" + Pal.adminChat.toString() + "]<A> " + Vars.netServer.chatFormatter.format(p, arg[0])
        Groups.player.each(
            { pl: Player -> pl.admin || Permission.getPerms(pl).contains(Permission.admin) },
            { a: Player -> a.sendMessage(raw, p, arg[0]) })
    })

    handler.registerCommand("vote", "<y/n/c>", CommandRunner { arg: Array<String>, player: Player ->
        if (PVars.currentlyKicking == null) {
            // player.sendMessage("[scarlet]Nobody is being voted on.");
            Bundle.sendMessage("vote.novoteinprogress", player)
        } else {
            if (Permission.getPerms(player).contains(Permission.admin) && arg[0].equals("c", ignoreCase = true)) {
                // Call.sendMessage(Strings.format("[lightgray]Vote canceled by admin[orange] @[lightgray].", player.name));
                Bundle.sendMessage("vote.canceledbyadmin", player.coloredName())
                PVars.currentlyKicking.task.cancel()
                PVars.currentlyKicking = null
                return@CommandRunner
            }

            if (player.isLocal) {
                // player.sendMessage("[scarlet]Local players can't vote. Kick the player yourself instead.");
                Bundle.sendMessage("vote.local", player)
                return@CommandRunner
            }

            val sign = when (arg[0].lowercase(Locale.getDefault())) {
                "y", "yes" -> 1
                "n", "no" -> -1
                else -> 0
            }

            //hosts can vote all they want
            if ((PVars.currentlyKicking.voted.get(
                    player.uuid(),
                    2
                ) == sign || PVars.currentlyKicking.voted.get(
                    Vars.netServer.admins.getInfo(player.uuid()).lastIP,
                    2
                ) == sign)
            ) {
                // player.sendMessage(Strings.format("[scarlet]You've already voted @. Sit down.", arg[0].toLowerCase()));
                Bundle.sendMessage("vote.alreadyvoted", player, arg[0].lowercase(Locale.getDefault()))
                return@CommandRunner
            }

            if (PVars.currentlyKicking.target === player) {
                // player.sendMessage("[scarlet]You can't vote on your own trial.");
                Bundle.sendMessage("vote.targetisplayer", player)
                return@CommandRunner
            }

            if (PVars.currentlyKicking.target.team() !== player.team()) {
                // player.sendMessage("[scarlet]You can't vote for other teams.");
                Bundle.sendMessage("vote.otherteam", player)
                return@CommandRunner
            }

            if (sign == 0) {
                // player.sendMessage("[scarlet]Vote either 'y' (yes) or 'n' (no).");
                Bundle.sendMessage("vote.unknownvote", player)
                return@CommandRunner
            }

            PVars.currentlyKicking.vote(player, sign)
        }
    })

    val cooldowns = ObjectMap<String?, Timekeeper>()
    handler.registerCommand(
        "votekick",
        "[player] [reason...]",
        CommandRunner { args: Array<String>, player: Player ->
            if (!Administration.Config.enableVotekick.bool()) {
                // player.sendMessage("[scarlet]Vote-kick is disabled on this server.");
                Bundle.sendMessage("votekick.disabled", player)
                return@CommandRunner
            }
            if (Groups.player.size() < 3) {
                // player.sendMessage("[scarlet]At least 3 players are needed to start a votekick.");
                Bundle.sendMessage("votekick.fewplayers", player)
                return@CommandRunner
            }

            if (player.isLocal) {
                // player.sendMessage("[scarlet]Just kick them yourself if you're the host.");
                Bundle.sendMessage("votekick.localplayer", player)
                return@CommandRunner
            }

            if (PVars.currentlyKicking != null) {
                // player.sendMessage("[scarlet]A vote is already in progress.");
                Bundle.sendMessage("votekick.alreadystarted", player)
                return@CommandRunner
            }
            if (args.isEmpty()) {
                val builder = StringBuilder()
                builder.append("[orange]Players to kick: \n")

                Groups.player.each(
                    { p: Player -> !p.admin && p.con != null && p !== player },
                    { p: Player ->
                        builder.append("[lightgray] ").append(p.name).append("[accent] (#").append(p.id())
                            .append(")\n")
                    })
                player.sendMessage(builder.toString())
            } else if (args.size == 1) {
                // player.sendMessage("[orange]You need a valid reason to kick the player. Add a reason after the player name.");
                Bundle.sendMessage("votekick.noreason", player)
            } else {
                val found: Player?
                if (args[0].length > 1 && args[0].startsWith("#") && Strings.canParseInt(args[0].substring(1))) {
                    val id = Strings.parseInt(args[0].substring(1))
                    found = Groups.player.find { p: Player -> p.id() == id }
                } else {
                    found = Groups.player.find { p: Player -> p.name.equals(args[0], ignoreCase = true) }
                }

                if (found != null) {
                    val perms = Permission.getPerms(found)
                    if (found === player) {
                        // player.sendMessage("[scarlet]You can't vote to kick yourself.");
                        Bundle.sendMessage("votekick.kickyouself", player)
                    } else if (found.admin || perms.contains(Permission.admin)) {
                        // player.sendMessage("[scarlet]Did you really expect to be able to kick an admin?");
                        Bundle.sendMessage("votekick.admin", player)
                    } else if (perms.contains(Permission.votekickImmune)) {
                        Bundle.sendMessage("votekick.immune", player)
                    } else if (found.isLocal) {
                        // player.sendMessage("[scarlet]Local players cannot be kicked.");
                        Bundle.sendMessage("votekick.localplayer2", player)
                    } else if (found.team() !== player.team()) {
                        // player.sendMessage("[scarlet]Only players on your team can be kicked.");
                        Bundle.sendMessage("votekick.onlyyourteam", player)
                    } else {
                        val vtime =
                            cooldowns.get(player.uuid()) { Timekeeper(voteCooldown.toFloat()) }

                        if (!vtime.get()) {
                            // player.sendMessage("[scarlet]You must wait " + voteCooldown/60 + " minutes between votekicks.");
                            Bundle.sendMessage("votekick.wait", player, voteCooldown / 60)
                            return@CommandRunner
                        }

                        val session = VotekickSession(found, player, args[1])
                        session.vote(player, 1)
                        // Call.sendMessage(Strings.format("[lightgray]Reason:[orange] @[lightgray].", args[1]));
                        Bundle.sendMessage("votekick.start", args[1])
                        vtime.reset()
                        PVars.currentlyKicking = session
                    }
                } else {
                    // player.sendMessage("[scarlet]No player [orange]'" + args[0] + "'[scarlet] found.");
                    Bundle.sendMessage("votekick.playernotfound", player, args[0])
                }
            }
        })
}
