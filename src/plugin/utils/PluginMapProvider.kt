package plugin.utils

import arc.util.Log
import mindustry.Vars
import mindustry.game.Gamemode
import mindustry.maps.Map
import mindustry.maps.Maps
import plugin.database.getNextMap

class PluginMapProvider : Maps.MapProvider {
    override fun next(mode: Gamemode, previous: Map?): Map? {
        if(previous != null) {
            Log.debug("[MapProvider] Previous Map: $previous")
            val mapNameOpt = getNextMap(previous.name())
            if(mapNameOpt.isPresent) {
                val mapName = mapNameOpt.get()
                Log.debug("[MapProvider] Selected map name is $mapName")
                val map = Vars.maps.customMaps().find { it.name() == mapName }
                if(map != null) {
                    return map
                } else {
                    Log.debug("[MapProvider] yay, db returned unknown map!")
                }
            }
        }
        return Vars.maps.customMaps().random(previous ?: Vars.emptyMap)
    }
}