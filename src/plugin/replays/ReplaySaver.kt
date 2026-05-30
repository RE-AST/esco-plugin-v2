package plugin.replays

import arc.struct.LongMap
import kotlinx.serialization.json.Json
import plugin.history.HistoryStack

fun saveReplay(history: LongMap<HistoryStack>): String {
    val map = HashMap<Long, ReplayStack>()
    history.forEach { entry ->
        val key = entry.key
        val value = entry.value
        val stack = ReplayStack()
        value.stack.forEach { r ->
            stack.add(ReplayRecord(
                r.playerName(),
                r.playerId.orElse(null),
                r.type.ordinal,
                r.block.id,
                r.unit.id,
                r.time
            ))
        }
        map[key] = stack
    }
    return Json.encodeToString(map)
}