package plugin.database.models

import java.time.Instant

class Mute(
    val id: Int,
    val active: Boolean,
    val playerId: Int,
    val adminId: Int,
    val reason: String,
    val muteTime: Instant,
    val unmuteTime: Instant
    ) {
    @JvmField
    var unmuteIn: String? = null
}