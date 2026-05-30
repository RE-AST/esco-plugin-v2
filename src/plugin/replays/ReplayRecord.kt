package plugin.replays

import kotlinx.serialization.Serializable
import java.util.Optional

@Serializable
data class ReplayRecord(
    val playerName: String,
    val playerId: Int?,
    val type: Int,
    val blockId: Short,
    val unitId: Short,
    val time: Long
) {
}