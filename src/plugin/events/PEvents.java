package plugin.events;

import arc.Events;
import arc.util.Timekeeper;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.content.UnitTypes;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.modules.ItemModule;
import plugin.database.models.PlayerData;
import plugin.history.History;
import plugin.utils.Loader;

import static plugin.PVars.*;
import static plugin.database.GettersKt.getPlayerData;
import static plugin.events.PEventsKt.loadEvents;
import static plugin.utils.Gamemode.campaign;
import static plugin.utils.Gamemode.sandbox;

public class PEvents {
    public static Timekeeper antigriefCooldown = Timekeeper.ofSeconds(3);

    public static void load() {
        loadEvents();

        Events.on(EventType.ServerLoadEvent.class, (e) -> {
            Loader.loadAfterStart();

            /*Vars.netServer.admins.addActionFilter(a -> {
                return true;
            });*/

            /*Vars.netServer.admins.addChatFilter((player, message) -> {
                return message;
            });*/
        });

        Events.on(EventType.GameOverEvent.class, (e) -> {
            if (mapVote != null)
                mapVote.cancel();
            History.clear();
            if (e.winner != Team.derelict)
                Groups.player.each(p -> {
                    if (p.team() == e.winner)
                        getPlayerData(p).ifPresent(PlayerData::adjWins);
                });
        });

        Events.on(EventType.WorldLoadEvent.class, (e) -> {
            Timer.schedule(() -> {
                if (gamemode == sandbox) {
                    Vars.state.rules.unitDamageMultiplier = 0;
                    Vars.state.rules.blockDamageMultiplier = 0;
                    Vars.state.rules.unitHealthMultiplier = 0.1f;
                    Vars.state.rules.blockHealthMultiplier = 0.1f;
                    Vars.state.rules.coreCapture = false;
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
                }
            }, 1);
        });

        Events.on(EventType.TapEvent.class, (e) -> {
            if (e.player == null || e.tile == null || !historyPlayers.contains(e.player))
                return;
            Call.setHudText(e.player.con, History.getMessage(e.tile.pos()));
        });
    }
}
