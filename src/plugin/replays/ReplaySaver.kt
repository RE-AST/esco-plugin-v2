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