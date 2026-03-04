package plugin.commands

import arc.func.Cons
import arc.util.CommandHandler
import arc.util.Log
import mindustry.gen.Call
import mindustry.gen.Groups
import plugin.Bundle
import plugin.PVars
import plugin.discord.*
import plugin.gamemodes.load
import plugin.history.History
import plugin.patches.Patches
import plugin.utils.Loader

fun register(handler: CommandHandler) {
    handler.register("reload-bundle", "reload bundle") { _: Array<String?>? ->
        Bundle.load()
    }

    handler.register("despawn-units", "Despawn all unused untis") { _: Array<String?>? ->
        Patches.despawnUnits()
    }

    handler.register("restart", "set needRestart to true.", Cons { _: Array<String?>? ->
        if (Groups.player.isEmpty) {
            Loader.exit()
            return@Cons
        }
        PVars.needRestart = true
        sendLog("Now server needs a restart!")
        Log.info("Ok!")
    })

    handler.register("savelog", "save logs") { _: Array<String?>? ->
        Loader.saveLogs()
    }

    handler.register("historysize", "") { _: Array<String?>? ->
        Log.info("Stacks: @", History.history.size)
    }

    handler.register("say", "<text...>", "") { a: Array<String?>? ->
        Log.info("Server: @", a!![0])
        Call.sendMessage("[scarlet][Server]:[white] " + a[0])
        sendServerMessage("Server: " + a[0])
    }

    handler.register("loadgm", "<name>", "") { a: Array<String?>? ->
        when (a!![0]) {
            "tdf" -> load()
            else -> Log.err("Unknown gamemode!")
        }
    }
}