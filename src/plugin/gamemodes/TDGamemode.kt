package plugin.gamemodes

import arc.Events
import arc.func.Cons
import arc.struct.ObjectMap
import arc.struct.Seq
import arc.struct.Seq.with
import arc.util.Log
import arc.util.Timer
import mindustry.Vars
import mindustry.content.Blocks
import mindustry.content.Items.*
import mindustry.content.UnitTypes.*
import mindustry.game.EventType.*
import mindustry.gen.Call
import mindustry.net.Administration
import mindustry.net.Administration.*
import mindustry.type.Item
import mindustry.type.UnitType
import mindustry.world.blocks.environment.Floor
import kotlin.math.roundToInt

object TDGamemode {

val items = ObjectMap<UnitType, Seq<Item>?>()

fun loadRes() {
    // region ground attack
    items.putAll(
        dagger,
        with(copper, lead, graphite),
        mace,
        with(copper, lead, graphite, silicon, sand, titanium),
        fortress,
        with(copper, lead, silicon, graphite, metaglass, sand, thorium, phaseFabric, plastanium),
        scepter,
        with(copper, lead, titanium, silicon, graphite, sand, metaglass, thorium, surgeAlloy, phaseFabric, plastanium),
        reign,
        with(copper, lead, titanium, metaglass, sand, thorium, surgeAlloy, silicon, graphite, plastanium, phaseFabric)
    )

    // endregion

    // region ground support
    items.putAll(
        nova, with(lead, silicon, copper),
        pulsar, with(copper, lead, silicon, graphite, titanium),
        quasar, with(copper, lead, silicon, graphite, metaglass, titanium, thorium, plastanium),
        vela, with(copper, lead, silicon, graphite, metaglass, titanium, thorium, surgeAlloy, plastanium, phaseFabric),
        corvus, with(copper, lead, silicon, graphite, metaglass, titanium, thorium, surgeAlloy, plastanium, phaseFabric)
    )

    // endregion

    // region ground legs
    items.putAll(
        crawler,
        with(coal, lead, copper),
        atrax,
        with(coal, graphite, silicon, lead, copper),
        spiroct,
        with(coal, graphite, silicon, lead, copper, titanium, metaglass, plastanium),
        arkyid,
        with(coal, graphite, silicon, lead, copper, thorium, titanium, metaglass, sand, plastanium, phaseFabric),
        toxopid,
        with(
            coal,
            graphite,
            silicon,
            lead,
            copper,
            thorium,
            titanium,
            metaglass,
            sand,
            surgeAlloy,
            plastanium,
            phaseFabric
        )
    )

    // endregion

    // region naval attack
    items.putAll(
        risso, with(silicon, metaglass, titanium, lead, copper),
        minke, with(silicon, metaglass, titanium, graphite, lead, copper),
        bryde, with(silicon, metaglass, titanium, graphite, lead, copper, plastanium),
        sei, with(silicon, metaglass, titanium, graphite, lead, copper, thorium, surgeAlloy, plastanium, phaseFabric),
        omura, with(silicon, metaglass, titanium, graphite, lead, copper, thorium, surgeAlloy, plastanium, phaseFabric)
    )
    // endregion

    // region air attack
    items.putAll(
        flare, with(silicon, copper)
    )
    // endregion

    // region naval support(+heal)
    items.putAll(
        retusa,
        with(silicon, metaglass, titanium, lead, copper),
        oxynoe,
        with(silicon, metaglass, titanium, graphite, lead, copper),
        cyerce,
        with(silicon, metaglass, titanium, graphite, lead, copper, plastanium),
        aegires,
        with(silicon, metaglass, titanium, graphite, lead, copper, thorium, surgeAlloy, plastanium, phaseFabric),
        navanax,
        with(silicon, metaglass, titanium, graphite, lead, copper, thorium, surgeAlloy, plastanium, phaseFabric)
    )
    // endregion

    // region erekir - tank
    items.putAll(
        stell, with(beryllium),
        locus, with(beryllium, graphite, tungsten),
        precept, with(beryllium, graphite, silicon, tungsten),
        vanquish, with(beryllium, graphite, silicon, thorium, phaseFabric, tungsten),
        conquer, with(beryllium, graphite, silicon, thorium, surgeAlloy, phaseFabric, tungsten),
    )
    // endregion

    // region erekir - mech
    items.putAll(
        merui, with(beryllium, silicon),
        cleroi, with(silicon, tungsten),
        anthicus, with(thorium, silicon),
        tecta, with(tungsten, beryllium, silicon),
    )
    // endregion


}

var floors: Seq<Floor> = with(Blocks.darkPanel2.asFloor(), Blocks.darkPanel3.asFloor())
var actions: Seq<Administration.ActionType> = with(
    ActionType.breakBlock, /*ActionType.buildSelect,*/
    ActionType.pickupBlock,
    ActionType.placeBlock,
    ActionType.dropPayload
)

//var healthMod = 1f
var resMod = 1f
const val baseRes = 40

fun load() {
    Log.info("Loading Tower Defense gamemode")
    loadRes()
    Events.on(ServerLoadEvent::class.java) { _: ServerLoadEvent? ->
        for (u in Vars.content.units()) {
            u.payloadCapacity = 0f
        }
        Vars.netServer.admins.addActionFilter(ActionFilter { action: PlayerAction? ->
            if (action != null && action.tile != null && actions.contains(action.type) && (action.block != Blocks.shockMine && action.tile.block() != Blocks.shockMine) && floors.contains(
                    action.tile.floor()
                )
            ) {
                return@ActionFilter false
            }
            true
        })
    }
    Events.on(UnitDestroyEvent::class.java, Cons { e: UnitDestroyEvent? ->
        val unit = e!!.unit
        val it = items.get(unit.type) ?: return@Cons
        val core = Vars.state.rules.defaultTeam
        val sb = StringBuilder()

        for (i in 0..<it.size) {
            val item = it.get(i)
            val amount = ((baseRes * resMod) / (i + 1)).roundToInt()
            core.items().add(item, amount)
            sb.append(item.emoji()).append(amount).append(" ")
        }
        Call.label(sb.toString(), 1.5f, unit.x, unit.y)
    })
    Events.on(UnitSpawnEvent::class.java) { e: UnitSpawnEvent? ->
        if (e!!.unit.team() != Vars.state.rules.defaultTeam) {
            Timer.schedule({
                //e.unit.healthMultiplier(healthMod)
                //e.unit.heal()
                e.unit.controller(TDGroundAI())
            }, 0.5f)
        }
    }
    Events.on(WorldLoadEvent::class.java) { _: WorldLoadEvent? ->
        reload()
    }
    Events.on(WaveEvent::class.java) { _: WaveEvent? ->
        if (Vars.state.wave % 4 == 0) {
            Vars.state.rules.waveTeam.rules().unitHealthMultiplier += 0.1f
            if (resMod < 200) resMod += 0.2f
        }
    }
}

private fun reload() {
    // healthMod = 1f
    resMod = 1f
    Timer.schedule({ ->
        val rules = Vars.state.rules
        rules.defaultTeam.rules().blockDamageMultiplier = 1f
        rules.defaultTeam.rules().unitDamageMultiplier = 1f
        rules.waveTeam.rules().unitDamageMultiplier = 0f
        rules.waveTeam.rules().blockDamageMultiplier = 0f
        rules.bannedUnits.addAll(
            flare,
            dagger,
            nova,
            crawler,
            mace,
            fortress,
            scepter,
            reign,
            pulsar,
            quasar,
            vela,
            corvus,
            atrax,
            spiroct,
            arkyid,
            toxopid
        )
        Vars.state.rules = rules;
        Call.setRules(rules)
    }, 1f)
}

}
