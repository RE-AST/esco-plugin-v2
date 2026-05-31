package plugin.replays

import kotlinx.serialization.Serializable

@Serializable
data class Replay(
    val map: String,
    val actions: HashMap<Long, ReplayStack>
)