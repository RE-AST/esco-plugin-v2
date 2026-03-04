package plugin.commands;

import arc.Events;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Strings;
import arc.util.Time;
import arc.util.Timekeeper;
import mindustry.Vars;
import mindustry.core.NetServer;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.graphics.Pal;
import mindustry.net.Administration;
import plugin.utils.Permission;
import plugin.Bundle;
import java.util.Optional;

import plugin.database.models.*;
import plugin.utils.Utils;
import plugin.utils.VoteMap;
import plugin.utils.VotekickSession;

import static mindustry.Vars.netServer;
import static plugin.Bundle.infoMessage;
import static plugin.Bundle.sendMessage;
import static plugin.PVars.*;
import static plugin.database.models.Admin.getAdmin;
import static plugin.database.models.Admin.updateHidden;
import static plugin.database.models.Ban.*;
import static plugin.database.models.PlayerData.*;
import static plugin.menus.Menus.showShop;
import static plugin.menus.Menus.slot;
import static plugin.utils.Gamemode.sandbox;
import static plugin.utils.Permission.*;
import static plugin.utils.Utils.parseBool;
import static plugin.utils.Utils.parseTime;
import static plugin.database.models.Ban.ban;

public class ClientCommands {
    static final int commandsPerPage = 10;
    public static int voteCooldown = 60 * 5;

    public static void register(CustomHandler handler) {
        handler.registerCommand("vanish", "", vanish, (a, p)->{
            if(vanishedPlayers.contains(p)) {
                vanishedPlayers.remove(p);
                p.sendMessage("unvanished");
                return;
            }
            p.sendMessage("vanished");
            vanishedPlayers.add(p);
            Call.playerDisconnect(p.id);
        });
        handler.registerCommand("economy", "", (a, p)->{
            infoMessage("infomessage.economyguide", p);
        });
        handler.registerCommand("slot", "<bet>", (a, p)->{
            if(!Strings.canParseInt(a[0])) {
                sendMessage("args.mustbeint", p, "<bet>");
                return;
            }
            PlayerStats.getPlayerStats(p).ifPresent(s->slot(p, s, Strings.parseInt(a[0])));
        });
        handler.registerCommand("shop", (a, p)->{
            PlayerStats.getPlayerStats(p).ifPresent(s->showShop(s, p));
        });
        handler.registerCommand("sync", (args, player)->{
            if(Time.timeSinceMillis(player.getInfo().lastSyncTime) < 1000 * 5){
                player.sendMessage("[scarlet]You may only /sync every 5 seconds.");
                return;
            }

            player.getInfo().lastSyncTime = Time.millis();
            Call.worldDataBegin(player.con);
            netServer.sendWorldData(player);
        });
        handler.registerCommand("help", "[page]", (args, player)->{
            if (args.length > 0 && !Strings.canParseInt(args[0])) {
                player.sendMessage("[scarlet]\"page\" must be a integer.");
                return;
            }
            int page = args.length > 0 ? Strings.parseInt(args[0]) - 1 : 0;

            int availableCommands = 0;

            StringBuilder result = new StringBuilder();
            Seq<Permission> perms = getPerms(player);

            Seq<String> pages = new Seq<>();

            for (int i = 0; i < handler.commands.size; i++) {
                CustomHandler.CommandData c = handler.commands.get(i);

                if (!perms.contains(c.permission)) continue;

                if (availableCommands >= commandsPerPage) {
                    pages.add(result.toString());
                    result.setLength(0);
                    availableCommands = 0;
                }

                String req = "commands." + c.name + ".description";
                String desc = Bundle.get(req, player.locale);
                if(desc.equals(req))
                    desc = Bundle.get("commands.nodesc", player.locale);

                availableCommands++;
                result.append("[orange]/")
                        .append(c.name)
                        .append(" [white]")
                        .append(c.args)
                        .append(" - ")
                        .append(desc)
                        .append("\n");
            }

            if(result.length() != 0)
                pages.add(result.toString());

            if((page + 1) > pages.size || page < 0) {
                sendMessage("commands.unknownpage", player);
                return;
            }
            String resp = "[orange]-- Commands Page "+(page+1)+"/"+pages.size+" --\n\n" + pages.get(page);
            player.sendMessage(resp);
        });
        handler.registerCommand("test", "", Permission.test, (a, p)->{
            p.sendMessage("[green]Ok!");
        });

        handler.registerCommand("stats", "", (a, p)->{
            StringBuilder sb = new StringBuilder("[stat]Stats:\n");

            PlayerStats.getPlayerStats(p).ifPresent(s->{
                s.update(p, false);
                sb.append("Blocks build: ").append(s.blocksBuild).append("\n");
                sb.append("Blocks broken: ").append(s.blocksBroken).append("\n");
                sb.append("Waves survived: ").append(s.wavesSurvived).append("\n");
                sb.append("Balance: [green]$[]").append(s.balance).append("\n");
                sb.append("Playtime: ").append(Utils.formatTime(s.playtime));
            });

            p.sendMessage(sb.toString());
        });

        handler.registerCommand("rtv", "[y/n]", (a, p)->{
            int i;
            if(a.length == 0)
                i = 1;
            else
                i = parseBool(a[0]);
            if(i == 0) {
                sendMessage("vote.unknownvote", p);
                return;
            }
            if(mapVote == null) {
                mapVote = new VoteMap(p);
                mapVote.vote(p, i);
                return;
            }
            if(mapVote.voted.containsKey(p.ip())) {
                sendMessage("rtv.error.voted", p);
                return;
            }
            mapVote.vote(p, i);
        });

        handler.registerCommand("ban", "<id> <time> <reason...>", Permission.punish, (a, p)->{
            if(Strings.canParseInt(a[0])) {
                int id = Strings.parseInt(a[0]);
                long time = parseTime(a[1]);
		        boolean perm = a[1].equalsIgnoreCase("perm");
                if(time == -1 && !perm) {
                    p.sendMessage("[scarlet]Unknown time, use d w m y or perm!");
                    return;
                }
                boolean banned;
                if(perm) {
                    banned = ban(id, p, a[2], -1);
                } else {
                    banned = ban(id, p, a[2], time);
                }
                if(banned) {
                    p.sendMessage("[green]Player banned!");
                } else {
                    p.sendMessage("[scarlet]Failed to ban player");
                }
            } else {
                p.sendMessage("[scarlet]ID must be int!");
            }
        });

        handler.registerCommand("history", (a, p)->{
            if(historyPlayers.contains(p)) {
                historyPlayers.remove(p);
                p.sendMessage("[scarlet]Disabled");
                Call.hideHudText(p.con);
            } else {
                historyPlayers.add(p);
                p.sendMessage("[green]Enabled!");
            }
        });

        handler.registerCommand("discord", (a, p)->{
            Call.openURI(p.con, discordLink);
        });

        handler.registerCommand("link", (a, p)->{
            Optional<PlayerData> pdOpt = getPlayerData(p);
            if(pdOpt.isPresent() && pdOpt.get().discordId != null) {
                p.sendMessage("Account already linked!");
                return;
            }
            String code = Utils.getRandomString(5);

            linkCodes.put(code, p);
            infoMessage("discord.link", p, gamemode.botPrefix, code, discordLink);
        });

        handler.registerCommand("hidden", "<bool>", Permission.admin, (a, p)->{
            int i = parseBool(a[0]);
            Optional<Integer> idOpt = getPlayerId(p);
            int id;
            if(idOpt.isEmpty()) {
                return;
            }
            id = idOpt.get();
            if(i == 1) {
                updateHidden(id, true);
                p.admin(false);
                p.sendMessage("[green]Ok!");
            } else if(i == -1) {
                updateHidden(id, false);
                p.admin(true);
                p.sendMessage("[green]Ok!");
            } else {
                p.sendMessage("[scarlet]Unknown bool! Use y/yes/д/да/t or n/no/н/нет/f");
            }
        });

        /*
            handler.registerCommand("team", "<team>", (arg, player)->{
                if(!Strings.canParseInt(arg[0])) {
                    sendMessage("args.mustbeint", player, "<team>");
                    return;
                }
                int id = Strings.parseInt(arg[0]);
                if(id > 5) {
                    sendMessage("args.lessthan", player, "<team>", 5);
                    return;
                }
                player.team(Team.get(id));
            });*/

        handler.registerCommand("artv", "", admin, (a, p)->{
            //Events.fire(new EventType.GameOverEvent(Team.derelict));
        });

        handler.registerCommand("a", "<message...>", admin, (arg, p)->{
            String raw = "[#" + Pal.adminChat.toString() + "]<A> " + Vars.netServer.chatFormatter.format(p, arg[0]);
            Groups.player.each(pl->pl.admin || getPerms(pl).contains(admin), a -> a.sendMessage(raw, p, arg[0]));
        });

        handler.registerCommand("vote", "<y/n/c>", (arg, player) -> {
            if(currentlyKicking == null){
                // player.sendMessage("[scarlet]Nobody is being voted on.");
                sendMessage("vote.novoteinprogress", player);
            }else{
                if(getPerms(player).contains(admin) && arg[0].equalsIgnoreCase("c")){
                    // Call.sendMessage(Strings.format("[lightgray]Vote canceled by admin[orange] @[lightgray].", player.name));
                    sendMessage("vote.canceledbyadmin", player.coloredName());
                    currentlyKicking.task.cancel();
                    currentlyKicking = null;
                    return;
                }

                if(player.isLocal()){
                    // player.sendMessage("[scarlet]Local players can't vote. Kick the player yourself instead.");
                    sendMessage("vote.local", player);
                    return;
                }

                int sign = switch(arg[0].toLowerCase()){
                    case "y", "yes" -> 1;
                    case "n", "no" -> -1;
                    default -> 0;
                };

                //hosts can vote all they want
                if((currentlyKicking.voted.get(player.uuid(), 2) == sign || currentlyKicking.voted.get(Vars.netServer.admins.getInfo(player.uuid()).lastIP, 2) == sign)){
                    // player.sendMessage(Strings.format("[scarlet]You've already voted @. Sit down.", arg[0].toLowerCase()));
                    sendMessage("vote.alreadyvoted", player, arg[0].toLowerCase());
                    return;
                }

                if(currentlyKicking.target == player){
                    // player.sendMessage("[scarlet]You can't vote on your own trial.");
                    sendMessage("vote.targetisplayer", player);
                    return;
                }

                if(currentlyKicking.target.team() != player.team()){
                    // player.sendMessage("[scarlet]You can't vote for other teams.");
                    sendMessage("vote.otherteam", player);
                    return;
                }

                if(sign == 0){
                    // player.sendMessage("[scarlet]Vote either 'y' (yes) or 'n' (no).");
                    sendMessage("vote.unknownvote", player);
                    return;
                }

                currentlyKicking.vote(player, sign);
            }
        });

        ObjectMap<String, Timekeeper> cooldowns = new ObjectMap<>();
        handler.registerCommand("votekick", "[player] [reason...]", (args, player) -> {
            if(!Administration.Config.enableVotekick.bool()){
                // player.sendMessage("[scarlet]Vote-kick is disabled on this server.");
                sendMessage("votekick.disabled", player);
                return;
            }

            if(Groups.player.size() < 3){
                // player.sendMessage("[scarlet]At least 3 players are needed to start a votekick.");
                sendMessage("votekick.fewplayers", player);
                return;
            }

            if(player.isLocal()){
                // player.sendMessage("[scarlet]Just kick them yourself if you're the host.");
                sendMessage("votekick.localplayer", player);
                return;
            }

            if(currentlyKicking != null){
                // player.sendMessage("[scarlet]A vote is already in progress.");
                sendMessage("votekick.alreadystarted", player);
                return;
            }

            if(args.length == 0){
                StringBuilder builder = new StringBuilder();
                builder.append("[orange]Players to kick: \n");

                Groups.player.each(p -> !p.admin && p.con != null && p != player, p -> {
                    builder.append("[lightgray] ").append(p.name).append("[accent] (#").append(p.id()).append(")\n");
                });
                player.sendMessage(builder.toString());
            }else if(args.length == 1){
                // player.sendMessage("[orange]You need a valid reason to kick the player. Add a reason after the player name.");
                sendMessage("votekick.noreason", player);
            }else{
                Player found;
                if(args[0].length() > 1 && args[0].startsWith("#") && Strings.canParseInt(args[0].substring(1))){
                    int id = Strings.parseInt(args[0].substring(1));
                    found = Groups.player.find(p -> p.id() == id);
                }else{
                    found = Groups.player.find(p -> p.name.equalsIgnoreCase(args[0]));
                }

                if(found != null){
                    Seq<Permission> perms = getPerms(found);
                    if(found == player){
                        // player.sendMessage("[scarlet]You can't vote to kick yourself.");
                        sendMessage("votekick.kickyouself", player);
                    }else if(found.admin || perms.contains(admin)){
                        // player.sendMessage("[scarlet]Did you really expect to be able to kick an admin?");
                        sendMessage("votekick.admin", player);
                    }else if(perms.contains(votekickImmune)) {
                        sendMessage("votekick.immune", player);
                    }else if(found.isLocal()){
                        // player.sendMessage("[scarlet]Local players cannot be kicked.");
                        sendMessage("votekick.localplayer2", player);
                    }else if(found.team() != player.team()){
                        // player.sendMessage("[scarlet]Only players on your team can be kicked.");
                        sendMessage("votekick.onlyyourteam", player);
                    }else{
                        Timekeeper vtime = cooldowns.get(player.uuid(), () -> new Timekeeper(voteCooldown));

                        if(!vtime.get()){
                            // player.sendMessage("[scarlet]You must wait " + voteCooldown/60 + " minutes between votekicks.");
                            sendMessage("votekick.wait", player, voteCooldown/60);
                            return;
                        }

                        VotekickSession session = new VotekickSession(found, player, args[1]);
                        session.vote(player, 1);
                        // Call.sendMessage(Strings.format("[lightgray]Reason:[orange] @[lightgray].", args[1]));
                        sendMessage("votekick.start", args[1]);
                        vtime.reset();
                        currentlyKicking = session;
                    }
                }else{
                    // player.sendMessage("[scarlet]No player [orange]'" + args[0] + "'[scarlet] found.");
                    sendMessage("votekick.playernotfound", player, args[0]);
                }
            }
        });
    }
}
