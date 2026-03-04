package plugin.gamemodes;

import arc.Events;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import static arc.struct.Seq.with;

import arc.util.Log;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Fx;
import mindustry.game.EventType;
import mindustry.gen.Call;
import mindustry.gen.Unit;
import mindustry.type.Item;
import mindustry.type.UnitType;
import mindustry.world.blocks.environment.Floor;

import java.util.concurrent.atomic.AtomicInteger;

import static mindustry.content.Items.*;
import static mindustry.content.UnitTypes.*;

public class TDGamemode {
    static final ObjectMap<UnitType, Seq<Item>> items = new ObjectMap<>();

    static void loadRes() {
        // region ground attack
        items.putAll(
                dagger, with(copper, lead, graphite),
                mace, with(copper, lead, graphite, silicon, sand),
                fortress, with(copper, lead, silicon, graphite, metaglass, sand, thorium),
                scepter, with(copper, lead, titanium, silicon, graphite, sand, metaglass, thorium, surgeAlloy),
                reign, with(copper, lead, titanium, metaglass, sand, thorium, surgeAlloy, silicon, graphite)
        );
        // endregion

        // region ground support
        items.putAll(
                nova, with(lead, silicon, copper),
                pulsar, with(copper, lead, silicon, graphite, titanium),
                quasar, with(copper, lead, silicon, graphite, metaglass, titanium, thorium),
                vela, with(copper, lead, silicon, graphite, metaglass, titanium, thorium, surgeAlloy),
                corvus, with(copper, lead, silicon, graphite, metaglass, titanium, thorium, surgeAlloy)
        );
        // endregion

        // region ground legs
        items.putAll(
                crawler, with(coal, lead, copper),
                atrax, with(coal, graphite, silicon, lead, copper),
                spiroct, with(coal, graphite, silicon, lead, copper, titanium, metaglass),
                arkyid, with(coal, graphite, silicon, lead, copper, thorium, titanium, metaglass, sand),
                toxopid, with(coal, graphite, silicon, lead, copper, thorium, titanium, metaglass, sand, surgeAlloy)
        );
        // endregion

        // region naval attack
        items.putAll(
                risso, with(silicon, metaglass, titanium, lead, copper),
                minke, with(silicon, metaglass, titanium, graphite, lead, copper),
                bryde, with(silicon, metaglass, titanium, graphite, lead, copper),
                sei, with(silicon, metaglass, titanium, graphite, lead, copper, thorium, surgeAlloy),
                omura, with(silicon, metaglass, titanium, graphite, lead, copper, thorium, surgeAlloy)
        );
        // endregion

        // region air attack
        items.putAll(
                flare, with(silicon, copper)
        );
        // endregion

        // region naval support(+heal)
        items.putAll(
                retusa, with(silicon, metaglass, titanium, lead, copper),
                oxynoe, with(silicon, metaglass, titanium, graphite, lead, copper),
                cyerce, with(silicon, metaglass, titanium, graphite, lead, copper),
                aegires, with(silicon, metaglass, titanium, graphite, lead, copper, thorium, surgeAlloy),
                navanax, with(silicon, metaglass, titanium, graphite, lead, copper, thorium, surgeAlloy)
        );
        // endregion
    }

    static Seq<Floor> floors = Seq.with(Blocks.darkPanel2.asFloor(), Blocks.darkPanel3.asFloor());
    static float healthMod = 1;
    static float resMod = 1;
    static final int baseRes = 20;

    public static void load() {
        Log.info("Loading Tower Defense gamemode");
        loadRes();
        Events.on(EventType.ServerLoadEvent.class, (e)->{
            Vars.netServer.admins.addActionFilter(action->{
                if(action.tile != null && action.block != Blocks.shockMine && floors.contains(action.tile.floor())) {
                    return false;
                }
                return true;
            });
        });
        Events.on(EventType.UnitDestroyEvent.class, e->{
            Unit unit = e.unit;
            Seq<Item> it = items.get(unit.type);
            if(it == null) return;
            var core = Vars.state.rules.defaultTeam;
            StringBuilder sb = new StringBuilder();

            for(int i = 0;i<it.size;i++) {
                Item item = it.get(i);
                int amount = Math.round((baseRes*resMod) / (i+1));
                core.items().add(item, amount);
                sb.append(item.emoji()).append(amount).append(" ");
            }
            Call.label(sb.toString(), 1.5f, unit.x, unit.y);
        });
        Events.on(EventType.UnitSpawnEvent.class, (e)->{
            Timer.schedule(()->{
                e.unit.healthMultiplier(healthMod);
                e.unit.heal();
            }, 0.2f);
        });
        Events.on(EventType.WorldLoadEvent.class, (e)->{
            reload();
        });
        Events.on(EventType.WaveEvent.class, (e)->{
            if(Vars.state.wave % 4 == 0) {
                healthMod += 0.1f;
                if(resMod < 200)
                    resMod += 0.2f;
            }
        });
    }

    private static void reload() {
        healthMod = 1;
        resMod = 1;
    }
}
