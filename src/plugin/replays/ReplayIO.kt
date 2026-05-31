package plugin.replays

import arc.struct.LongMap
import arc.util.Timer
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import mindustry.Vars
import mindustry.content.Blocks
import mindustry.game.Team
import mindustry.gen.Call
import plugin.history.HistoryStack
import plugin.history.HistoryType

fun saveReplay(history: LongMap<HistoryStack>, mapName: String): ByteArray {
    val map = HashMap<Long, ReplayStack>()
    history.forEach { entry ->
        val key = entry.key
        val value = entry.value
        val stack = ReplayStack()
        value.stack.forEach { r ->
            if(r.center()) {
                var unitId: Short? = null
                r.unit?.let { u ->
                    unitId = u.id
                }
                stack.add(ReplayRecord(
                    r.playerName(),
                    r.playerId.orElse(null),
                    r.type.ordinal,
                    r.block.id,
                    unitId,
                    r.time,
                    r.team.id,
                    r.rotation
                ))
            }
        }
        map[key] = stack
    }
    return ProtoBuf.encodeToByteArray(Replay(mapName, map))
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
    val end = events.last().record.time
    events.forEach { event ->
        val delay = (event.record.time - start) / 1000f
        Timer.schedule({
            if(event.record.time == end) {
                Call.sendMessage("Done!")
            }
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
            tile.setNet(Vars.content.block(record.blockId.toInt()), Team.get(record.team), record.rotation)
        }
        HistoryType.destroyBlock.ordinal,
        HistoryType.breakBlock.ordinal -> {
            tile.setNet(Blocks.air)
        }
    }
}