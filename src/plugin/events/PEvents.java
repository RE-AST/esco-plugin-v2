package plugin.events;

import arc.Core;
import arc.Events;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Time;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.content.UnitTypes;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.net.Administration;
import mindustry.net.Administration.ActionType;
import mindustry.world.Tile;
import mindustry.world.blocks.logic.LogicBlock;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.modules.ItemModule;
import plugin.Bundle;
import plugin.antigrief.AntiFimoz;
import plugin.database.models.Mute;
import plugin.database.models.PlayerData;
import plugin.database.models.PlayerStats;
import plugin.discord.BotKt;
import plugin.history.History;
import plugin.menus.MenusKt;
import plugin.utils.Loader;
import plugin.utils.Permission;

import java.util.Optional;

import static plugin.Bundle.sendMessage;
import static plugin.PVars.*;
import static plugin.database.GettersKt.*;
import static plugin.discord.BotKt.sendParrotMessage;
import static plugin.discord.BotKt.sendRoundMessage;
import static plugin.events.PEventsKt.loadEvents;
import static plugin.utils.Gamemode.campaign;
import static plugin.utils.Gamemode.sandbox;
import static plugin.utils.UtilsKt.*;

public class PEvents {
    public static void load() {
        loadEvents();

        Events.on(EventType.PlayerJoin.class, (e) -> { // full connect
            Player player = e.player;

            Optional<PlayerData> pdOpt = getPlayerData(player);
            if (pdOpt.isEmpty()) {
                player.kick("[scarlet]Failed to create player!");
                return;
            }
            PlayerData pd = pdOpt.get();

            PlayerStats.setJoinTime(player);
            //pd.setOriginalName(player.coloredName());
            getPlayerStats(player);

            sendMessage("messages.join", String.valueOf(pd.id), player.coloredName());
            putLog(pd.id, "event", "Player joined!");

            Log.info("[@] Player @ joined [@]", pd.id, player.plainName(), player.uuid());
            BotKt.sendJoinMessage(player, pd.id);

            Call.clientPacketReliable(player.con, "SendMeSubtitle", player == null ? null : String.valueOf(player.id));
            if (pd.prefs.showWelcomeMenu)
                MenusKt.showWelcome(player);

            // simple bot check
            Timer.schedule(() -> {
                if (player.con.isConnected() && player.con.lastReceivedClientSnapshot == -1) {
                    putLog(pd.id, "system", "Player detected as bot");
                    player.kick("[scarlet]Try reconnect\nDiscord " + discordLink, 0);
                }
            }, 2);
            /*if (gamemode == pvp)
                player.name = "[white]<" + player.team().coloredName() + "[white]> " + player.coloredName();*/
        });

        Events.on(EventType.PlayerLeave.class, (e) -> {
            Player player = e.player;

            if (player != null/* how? */) SSUsers.remove(player.id);

            Optional<PlayerData> pdOpt = getPlayerData(player);
            if (pdOpt.isPresent()) {
                PlayerData pd = pdOpt.get();
                sendMessage("messages.leave", String.valueOf(pd.id), player.coloredName());
                Log.info("[@] Player @ left [@]", pd.id, player.plainName(), player.uuid());
                BotKt.sendLeaveMessage(player, pd.id);
                putLog(pd.id, "event", "Player disconnected");
            }
            if (currentlyKicking != null && currentlyKicking.target.equals(player)) {
                ban(currentlyKicking.targetId, currentlyKicking.startedId, "AutoBan: Leave during votekick\n" + currentlyKicking.reason, 2 * 60 * 60);
                currentlyKicking.cancel();
                sendMessage("votekick.targetleft");
            }

            purgeData(player);

            /*if(rtvVotes.contains(player)) {
                rtvVotes.remove(player);
                Bundle.sendMessage("rtv.playerleft", rtvVotes.size+"/"+Math.max(1, (int) Math.round(Groups.player.size() * 0.8)));
            }*/
            Timer.schedule(() -> {
                if (mapVote != null) mapVote.checkPass();
                if (Groups.player.isEmpty() && needRestart) {
                    Loader.exit();
                }
            }, 0.2f);
        });

        Events.on(EventType.PlayerChatEvent.class, (e) -> {
            Player player = e.player;
            String message = e.message;

            getPlayerData(player).ifPresent(pd -> {
                putLog(pd.id, "event", "Player sent message " + message);
            });

            if (!message.startsWith("/")) {
                String content = ("`" + player.plainName() + ": " + stripFoo(Strings.stripColors(message)) + "`").replace("@", "");
                BotKt.sendServerMessage(content);
                if (Math.random() > 0.9)
                    sendParrotMessage(content);
            }
        });

        Events.on(EventType.ServerLoadEvent.class, (e) -> {
            Loader.loadAfterStart();

            Vars.netServer.admins.addActionFilter(a -> {
                Administration.ActionType type = a.type;
                Player player = a.player;

                Optional<PlayerData> pdOpt = getPlayerData(player);
                if (pdOpt.isEmpty()) {
                    return false;
                }
                PlayerData pd = pdOpt.get();

                if (type == Administration.ActionType.buildSelect || type == Administration.ActionType.configure)
                    if (a.tile.block() instanceof LogicBlock && a.config instanceof byte[] b) {
                        String code = decompress(b);
                        if (code.isEmpty()) return true;
                        if (countWords("radar", code) > 25) {
                            sendMessage("logic.antilag", player);
                            putLog(pd.id, "system", "Player possible building lag machines!");
                            return false;
                        }
                    }

                // putLog(a, pd);
                return true;
            });

            Vars.netServer.admins.addChatFilter((player, message) -> {
                if (AntiFimoz.applyMessage(message, player))
                    return null;

                /* WIP
                Optional<Mute> muteOpt = getMute(player);
                if(muteOpt.isPresent()) {
                    Mute mute = muteOpt.get();
                    if(mute.unmuteIn == null)
                        mute.unmuteIn = formatTime((mute.getUnmuteTime().toEpochMilli() - Time.millis()) / 1000);
                    Bundle.sendMessage("muted", player, mute.getReason(), mute.unmuteIn);
                    return null;
                }*/

                return message;
            });
        });

        Events.on(EventType.BlockBuildEndEvent.class, (e) -> {
            if (e.tile == null || e.unit == null)
                return;

            Player player = e.unit.getPlayer();

            if (player != null)
                getPlayerStats(player).ifPresent(s -> {
                    if (e.breaking) {
                        s.adjBlocksBroken();
                        if (s.blocksBroken >= 350 && s.blocksBuild < 15 && s.playtime < 600) {
                            ban(player, player, "AutoBan: Possible Griefer", parseTime("3d"));
                            player.kick("AutoBan: Possible Griefer", 0);
                            player.con.close();
                        }
                    } else
                        s.adjBlocksBuild();
                });

            if (e.breaking) return;

            Unit unit = e.unit;
            Tile tile = e.tile;
            String name = null;
            Optional<Integer> pid = Optional.empty();
            if (player != null) {
                name = player.coloredName();
                pid = getPlayerId(player);
            }

            History.write(tile, name, pid, ActionType.buildSelect, tile.block(), unit.type());
        });

        Events.on(EventType.BlockBuildBeginEvent.class, (e) -> {
            if (e.tile == null || e.unit == null || !e.breaking)
                return;
            Player player = e.unit.getPlayer();
            Unit unit = e.unit;
            Tile tile = e.tile;
            String name = null;
            Optional<Integer> pid = Optional.empty();

            if (player != null) {
                name = player.coloredName();
                pid = getPlayerId(player);
            }

            History.write(tile, name, pid, ActionType.breakBlock, tile.block(), unit.type());
        });

        Events.on(EventType.BuildRotateEvent.class, (e) -> {
            if (e.build == null || e.unit == null || e.unit.getPlayer() == null)
                return;
            Player player = e.unit.getPlayer();
            Building build = e.build;
            String name = null;
            Optional<Integer> pid = Optional.empty();
            if (player != null) {
                name = player.coloredName();
                pid = getPlayerId(player);
            }

            History.write(build.tile, name, pid, ActionType.rotate, build.block, null);
        });

        Events.on(EventType.ConfigEvent.class, (e) -> {
            if (e.player == null || e.tile == null)
                return;
            Player player = e.player;
            Building build = e.tile;
            String name = player.coloredName();

            History.write(build.tile, name, getPlayerId(player), ActionType.configure, build.block, null);
        });

        Events.on(EventType.GameOverEvent.class, (e) -> {
            if (mapVote != null)
                mapVote.cancel();
            History.clear();
            sendRoundMessage("Game Over! Team " + e.winner.name + " wins!\nTotal players: " + Groups.player.size());
            if (e.winner != Team.derelict)
                Groups.player.each(p -> {
                    if (p.team() == e.winner)
                        getPlayerStats(p).ifPresent(PlayerStats::adjWins);
                });
        });

        Events.on(EventType.WorldLoadEvent.class, (e) -> {
            Timer.schedule(() -> {
                if (gamemode == sandbox) {
                    Vars.state.rules.unitDamageMultiplier = 0;
                    Vars.state.rules.blockDamageMultiplier = 0;
                    Vars.state.rules.unitHealthMultiplier = 0.1f;
                    Vars.state.rules.blockHealthMultiplier = 0.1f;
                } else if (gamemode == campaign) {
                    CoreBlock.CoreBuild core = Vars.state.rules.defaultTeam.core();
                    if (core == null) return;
                    ItemModule items = core.items;
                    items.add(Items.copper, 500);
                    items.add(Items.silicon, 300);
                    items.add(Items.graphite, 250);
                    items.add(Items.coal, 1500);
                    items.add(Items.metaglass, 65);
                    items.add(Items.lead, 350);
                    for (int i = 0; i < 5; i++)
                        UnitTypes.mono.spawn(core.team(), core.x, core.y);
                }/* else if (gamemode == pvp) {
                    Groups.player.each(p -> getPlayerData(p).ifPresent(d -> {
                        p.name = "[white]<" + p.team().coloredName() + "[white]> " + d.originalName;
                    }));
                }*/
            }, 1);
        });

        Events.on(EventType.TapEvent.class, (e) -> {
            if (e.player == null || e.tile == null || !historyPlayers.contains(e.player))
                return;
            Call.setHudText(e.player.con, History.getMessage(e.tile.pos()));
        });

        Events.on(EventType.WaveEvent.class, (e) -> {
            Groups.player.each(p -> getPlayerStats(p).ifPresent(s -> s.adjWavesSurvived()));
        });
    }

    public static void purgeData(Player p) {
        getPlayerId(p).ifPresent(id->{
            mutesCache.remove(id);
        });
        Permission.cache.remove(p);
        playerDataCache.remove(p);
        adminsCache.remove(p);
        PlayerStats.purge(p);
        historyPlayers.remove(p);
        vanishedPlayers.remove(p);

        if (linkCodes.containsValue(p, false))
            linkCodes.forEach(e -> {
                if (e.value.equals(p))
                    Core.app.post(() -> linkCodes.remove(e.key));
            });
    }
}
