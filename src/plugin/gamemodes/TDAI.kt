package plugin.gamemodes

import arc.struct.Seq
import mindustry.Vars
import mindustry.ai.Pathfinder
import mindustry.ai.types.GroundAI
import mindustry.entities.units.AIController
import mindustry.world.Tile
import mindustry.world.blocks.storage.CoreBlock
import kotlin.math.max

class TDAI : AIController() {

    var core: CoreBlock.CoreBuild? = null
    var path: Seq<Tile>? = null
    var pathIndex = 0
    var stuckTime: Float = 0f
    var stuckX: Float = -999f
    var stuckY: Float = -999f

    val stuckRange: Float = Vars.tilesize * 1.5f

    override fun updateMovement() {
        val unit = unit ?: return

        var coreBuild = core
        if (coreBuild == null || coreBuild.dead()) {
            coreBuild = unit.closestEnemyCore()
        }
        coreBuild ?: return


        //if it hasn't moved the stuck range in twice the time it should have taken, it's stuck
        val stuckThreshold = max(1f, stuckRange * 2f / unit.type.speed)

        if(unit.within(coreBuild, 80f)) {
            coreBuild.damage(unit.health() / 2)
            unit.kill()
            return
        }

        pathfind(Pathfinder.fieldCore, true, stuckTime >= stuckThreshold)

        core = coreBuild;
    }

    /*
    override fun updateMovement() {
        val unit = unit ?: return

        var coreBuild = core
        if (coreBuild == null || coreBuild.dead()) {
            coreBuild = unit.closestEnemyCore()
        }
        coreBuild ?: return

        if(unit.within(coreBuild, 80f)) {
            coreBuild.damage(unit.health() / 2)
            unit.kill()
        }

        if (path == null) { //|| pathIndex >= path!!.size) {

            val from = unit.tileOn() ?: return
            val to = coreBuild.tile

            path = Astar.pathfind(
                from,
                to,
                { 1f }, // TileHeuristic
                { t -> (!t.solid() || t.block() is CoreBlock) && t.floor() != Blocks.space.asFloor() } // passable
            )

            pathIndex = 0
        }

        val path = path ?: return
        if (pathIndex >= path.size) return

        val next = path[pathIndex]

        moveTo(next, 1f)

        if (unit.within(next.worldx(), next.worldy(), unit.type.range * 0.5f)) {
            pathIndex++
        }
    }*/

    override fun updateWeapons() {

    }
}