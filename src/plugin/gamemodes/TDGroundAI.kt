package plugin.gamemodes

import arc.func.Cons
import arc.math.Mathf
import arc.util.Time
import mindustry.Vars
import mindustry.ai.Pathfinder
import mindustry.ai.types.GroundAI
import mindustry.entities.Units
import mindustry.gen.Building
import mindustry.gen.Unit
import kotlin.math.max

class TDGroundAI : GroundAI() {
    var stuckTime: Float = 0f
    var stuckX: Float = -999f
    var stuckY: Float = -999f

    val stuckRange: Float = Vars.tilesize * 1.5f

    override fun updateMovement() {
        //if it hasn't moved the stuck range in twice the time it should have taken, it's stuck

        val stuckThreshold = max(1f, stuckRange * 2f / unit.type.speed)

        val core: Building? = unit.closestEnemyCore()
        var moved = false

        if (core != null && unit.within(core, 80f)) {
            core.damage(unit.health() / 2)
            unit.kill()
            return
        }

        if ((core == null || !unit.within(core, unit.type.range * 0.5f))) {
            var move = true

            if (Vars.state.rules.waves && unit.team === Vars.state.rules.defaultTeam) {
                val spawner = closestSpawner
                if (spawner != null && unit.within(spawner, Vars.state.rules.dropZoneRadius + 120f)) move = false
                if (spawner == null && core == null) move = false
            }

            //no reason to move if there's nothing there
            if (core == null && (!Vars.state.rules.waves || closestSpawner == null)) {
                move = false
            }

            moved = move

            if (move) pathfind(Pathfinder.fieldCore, true, stuckTime >= stuckThreshold)
        }

        if (unit.type.canBoost && unit.elevation > 0.001f && !unit.onSolid()) {
            unit.elevation = Mathf.approachDelta(unit.elevation, 0f, unit.type.descentSpeed)
        }

        faceTarget()

        if (moved) {
            if (unit.within(stuckX, stuckY, stuckRange)) {
                stuckTime += Time.delta
                if (stuckTime - Time.delta < stuckThreshold && stuckTime >= stuckThreshold) {
                    val radius = unit.hitSize * Vars.unitCollisionRadiusScale * 2f
                    Units.nearby(unit.team, unit.x, unit.y, radius, Cons { other: Unit? ->
                        other ?: return@Cons
                        if (other !== unit && other.controller() is TDGroundAI && other.within(
                                unit.x,
                                unit.y,
                                radius + other.hitSize * Vars.unitCollisionRadiusScale
                            )
                        ) {
                            val ai = other.controller() as TDGroundAI
                            ai.stuckX = other.x
                            ai.stuckY = other.y
                            ai.stuckTime = max(1f, stuckRange * 2f / other.type.speed) + 1f
                        }
                    })
                }
            } else {
                stuckX = unit.x
                stuckY = unit.y
                stuckTime = 0f
            }
        } else {
            stuckTime = 0f
        }
    }

    override fun updateWeapons() {

    }
}