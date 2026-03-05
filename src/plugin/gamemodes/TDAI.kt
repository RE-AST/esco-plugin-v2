package plugin.gamemodes

import mindustry.Vars
import mindustry.entities.units.AIController
import mindustry.game.Team
import mindustry.world.blocks.storage.CoreBlock
import arc.math.geom.Position

class TDAI : AIController() {
    var core : CoreBlock.CoreBuild? = null
    override fun updateMovement() {
        unit?: return
        var coreBuild = core
        if(coreBuild == null || coreBuild.dead()) {
            coreBuild = Vars.state.rules.defaultTeam.core()
        }
        coreBuild?: return
        moveTo(coreBuild, 0f, 100f, true, null)
        if(unit.within(coreBuild, 4)) {
            for(mount in unit.mounts) {
                mount.target = coreBuild
                mount.aimX = coreBuild.x
                mount.aimY = coreBuild.y
                mount.shoot = true
            }
        } else {
            for(mount in unit.mounts) {
                mount.target = null
                mount.shoot = false
            }
        }
    }
}