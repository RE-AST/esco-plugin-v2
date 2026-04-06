package plugin.database.models

import java.time.Instant

class Mute(
    id: Int,
    active: Boolean,
    playerId: Int,
    adminId: Int,
    reason: String,
    muteTime: Instant,
    unmuteTime: Instant
    ) {
}