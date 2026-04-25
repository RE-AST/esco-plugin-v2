package plugin.database.models

data class MapStats(
    val id: Int,
    val name: String,
    val server: Int,
    val minWave: Int,
    val maxWave: Int,
    val minPlaytime: Int,
    val maxPlaytime: Int,
    val wins: Int,
    val loses: Int,
    val skips: Int
)