/*
Auto-generated on 2026-04-25T22:39:13.017Z
Based on migrations.sql#L99 (table: maps)
*/
package plugin.database.models

data class MapStats(
    val id: Int,
    val name: String,
    val server: Int,
    val minWave: Int,
    val avgWave: Int,
    val maxWave: Int,
    val minPlaytime: Int,
    val avgPlaytime: Int,
    val maxPlaytime: Int,
    val wins: Int,
    val loses: Int,
    val skips: Int
)