package plugin.replays

import arc.math.geom.Point2
import arc.struct.LongMap
import arc.util.Timer
import kotlinx.serialization.json.Json
import mindustry.Vars
import mindustry.content.Blocks
import plugin.history.HistoryStack
import plugin.history.HistoryType
import java.util.TimerTask

fun saveReplay(history: LongMap<HistoryStack>): String {
    val map = HashMap<Long, ReplayStack>()
    history.forEach { entry ->
        val key = entry.key
        val value = entry.value
        val stack = ReplayStack()
        value.stack.forEach { r ->
            var unitId: Short? = null;
            r.unit?.let { u ->
                unitId = u.id
            }
            stack.add(ReplayRecord(
                r.playerName(),
                r.playerId.orElse(null),
                r.type.ordinal,
                r.block.id,
                unitId,
                r.time
            ))
        }
        map[key] = stack
    }
    return Json.encodeToString(map)
}

fun playReplay(replay: HashMap<Long, ReplayStack>) {
    val events = mutableListOf<ReplayEvent>()

    replay.forEach { entry ->
        val key = entry.key
        val value = entry.value
        value.stack.forEach { r ->
            events += ReplayEvent(key.toInt(), r)
        }
    }

    events.sortBy { it.record.time }
    val start = events.first().record.time
    events.forEach { event ->
        val delay = (event.record.time - start) / 1000f
        Timer.schedule({
            applyEvent(event)
        }, delay)
    }
}

private fun applyEvent(event: ReplayEvent) {
    val record = event.record
    val tile = Vars.world.tile(event.pos)
    tile ?: return
    when(event.record.type) {
        HistoryType.buildBlock.ordinal -> {
            tile.setNet(Vars.content.block(record.blockId.toInt()))
        }
        HistoryType.destroyBlock.ordinal,
        HistoryType.breakBlock.ordinal -> {
            tile.setNet(Blocks.air)
        }
    }
}