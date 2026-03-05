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
import mindustry.net.Administration.ActionFilter
import mindustry.net.Administration.ActionType
import mindustry.net.Administration.PlayerAction
import mindustry.type.Item
import mindustry.type.UnitType
import mindustry.world.blocks.environment.Floor
import kotlin.math.roundToInt
import mindustry.gen.Groups

val items = ObjectMap<UnitType?, Seq<Item>?>()

fun loadRes() {
    // region ground attack
    items.putAll(
        dagger, with(copper, lead, graphite),
        mace, with(copper, lead, graphite, silicon, sand, titanium),
        fortress, with(copper, lead, silicon, graphite, metaglass, sand, thorium),
        scepter, with(copper, lead, titanium, silicon, graphite, sand, metaglass, thorium, surgeAlloy),
        reign, with(copper, lead, titanium, metaglass, sand, thorium, surgeAlloy, silicon, graphite)
    )

    // endregion

    // region ground support
    items.putAll(
        nova, with(lead, silicon, copper),
        pulsar, with(copper, lead, silicon, graphite, titanium),
        quasar, with(copper, lead, silicon, graphite, metaglass, titanium, thorium),
        vela, with(copper, lead, silicon, graphite, metaglass, titanium, thorium, surgeAlloy),
        corvus, with(copper, lead, silicon, graphite, metaglass, titanium, thorium, surgeAlloy)
    )

    // endregion

    // region ground legs
    items.putAll(
        crawler, with(coal, lead, copper),
        atrax, with(coal, graphite, silicon, lead, copper),
        spiroct, with(coal, graphite, silicon, lead, copper, titanium, metaglass),
        arkyid, with(coal, graphite, silicon, lead, copper, thorium, titanium, metaglass, sand),
        toxopid, with(coal, graphite, silicon, lead, copper, thorium, titanium, metaglass, sand, surgeAlloy)
    )

    // endregion

    // region naval attack
    items.putAll(
        risso, with(silicon, metaglass, titanium, lead, copper),
        minke, with(silicon, metaglass, titanium, graphite, lead, copper),
        bryde, with(silicon, metaglass, titanium, graphite, lead, copper),
        sei, with(silicon, metaglass, titanium, graphite, lead, copper, thorium, surgeAlloy),
        omura, with(silicon, metaglass, titanium, graphite, lead, copper, thorium, surgeAlloy)
    )
    // endregion

    // region air attack
    items.putAll(
        flare, with(silicon, copper)
    )
    // endregion

    // region naval support(+heal)
    items.putAll(
        retusa, with(silicon, metaglass, titanium, lead, copper),
        oxynoe, with(silicon, metaglass, titanium, graphite, lead, copper),
        cyerce, with(silicon, metaglass, titanium, graphite, lead, copper),
        aegires, with(silicon, metaglass, titanium, graphite, lead, copper, thorium, surgeAlloy),
        navanax, with(silicon, metaglass, titanium, graphite, lead, copper, thorium, surgeAlloy)
    )
    // endregion
}

var floors: Seq<Floor> = with(Blocks.darkPanel2.asFloor(), Blocks.darkPanel3.asFloor())
var actions : Seq<Administration.ActionType> = with(ActionType.breakBlock, ActionType.buildSelect, ActionType.pickupBlock, ActionType.placeBlock, ActionType.dropPayload)
var healthMod = 1f
var resMod = 1f
const val baseRes = 40

fun load() {
    Log.info("Loading Tower Defense gamemode")
    loadRes()
    Events.on(ServerLoadEvent::class.java) { _: ServerLoadEvent? ->
        Vars.netServer.admins.addActionFilter(ActionFilter { action: PlayerAction? ->
            if (action != null && action.tile != null && actions.contains(action.type) && action.block !== Blocks.shockMine && floors.contains(action.tile.floor())) {
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
        if(e!!.unit.team() != Vars.state.rules.defaultTeam) {
            Timer.schedule({
                e.unit.healthMultiplier(healthMod)
                e.unit.heal()
                e.unit.controller(TDGroundAI())
            }, 0.5f)
        }
    }
    Events.on(WorldLoadEvent::class.java) { _: WorldLoadEvent? ->
        reload()
    }
    Events.on(WaveEvent::class.java) { _: WaveEvent? ->
        if (Vars.state.wave % 4 == 0) {
            healthMod += 0.1f
            if (resMod < 200) resMod += 0.2f
        }
    }
}

private fun reload() {
    healthMod = 1f
    resMod = 1f
}
