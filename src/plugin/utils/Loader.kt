package plugin.utils

import arc.Core
import arc.util.Log
import arc.util.Timer
import kotlinx.coroutines.launch
import mindustry.Vars
import mindustry.Vars.state
import mindustry.core.GameState
import mindustry.gen.Groups
import mindustry.net.Administration
import mindustry.server.ServerControl
import plugin.Bundle
import plugin.Config
import plugin.KVars.globalScope
import plugin.PVars
import plugin.PVars.serverCommands
import plugin.database.BanListener
import plugin.database.models.Server
import plugin.discord.sendLog
import plugin.events.PEvents
import plugin.gamemodes.hexed.HexedGamemode
import plugin.gamemodes.hexed.HexedGamemode.hexedGamemode
import plugin.menus.Menu
import plugin.menus.TextMenu
import plugin.menus.loadMenus
import plugin.packets.Packets
import plugin.patches.Patches
import kotlin.system.exitProcess
import plugin.gamemodes.tdf.TDGamemode

object Loader {

    @JvmStatic
    fun load() {
        Config.load()
        Bundle.load()
        Patches.load()
        PEvents.load()
        MapPreview.loadColors()
        loadServerId()
        loadLogging()
        loadTimers()
        loadGamemode()
        BanListener.load()
        Menu.load()
        TextMenu.load()

        PVars.version = getResource("version")!!.readString()

        loadMenus()
        if(PVars.gamemode != Gamemode.hexed && Core.settings.getBool("autorestarted", false)) {
            if(state.isGame) {
                Vars.net.closeServer()
                ServerControl.instance.cancelPlayTask()
                state.set(GameState.State.menu)
                Log.info("Stopped server.")
            }

            loadSave("autorestart")

            Core.settings.put("autorestarted", false)
        }

	    Log.debug("Loader: OK!")
    }

    @JvmStatic
    fun loadAfterStart() {
        // ClientCrasher.load();
        // AntiFimoz.load();
        Administration.Config.showConnectMessages.set(false)
        Packets.load()
        /*if(PVars.gamemode != Gamemode.hexed) {
            Vars.maps.setMapProvider(PluginMapProvider())
        }*/
    }

    fun loadGamemode() {
        when(PVars.gamemode) {
            Gamemode.tdefense -> TDGamemode.load()
            Gamemode.hexed -> {
                hexedGamemode = HexedGamemode()
                hexedGamemode.init()
                hexedGamemode.registerServerCommands(serverCommands)
            }
            else -> {}
        }
    }

    fun loadTimers() {
        Timer.schedule({
            if (!Groups.player.isEmpty) Bundle.sendMessage("advertise.discord", PVars.discordLink)
        }, (15 * 60).toFloat(), (15 * 60).toFloat())
        Timer.schedule({
            if (!Groups.player.isEmpty) Bundle.sendMessage("advertise.reports", PVars.discordLink)
        }, (15 * 60).toFloat(), (35 * 60).toFloat())
        if (PVars.lokiLoggingEnabled) Timer.schedule({ pushLogs() }, 0f, (5 * 60).toFloat())
    }

    fun loadServerId() {
        val serverOpt = Server.getOrCreateServer()
        if (serverOpt.isPresent) PVars.serverId = serverOpt.get().id
        else Log.err("WTF, cannot create/get server record. Server is unstable")
    }

    fun loadLogging() {
        val time = (5 * 60).toFloat()
        Timer.schedule({ saveLogs() }, time, time)
    }

    fun saveLogs() {
        Log.info("Saving @ logs", PVars.logsBuffer.size)

        while (PVars.logsBuffer.size > 0) {
            val log = PVars.logsBuffer.pop()
            globalScope.launch {
                log.write()
            }
        }
    }

    @JvmStatic
    fun exit() {
        Log.info("Exiting server, please wait...")
        sendLog("Exiting server")
        save("autorestart")
        Core.settings.put("autorestarted", true)
        Core.settings.manualSave()
        saveLogs()
        Timer.schedule({
            exitProcess(0)
        }, 5f)
    }
}
