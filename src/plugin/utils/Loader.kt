package plugin.utils

import arc.Core
import arc.util.Log
import arc.util.Timer
import kotlinx.coroutines.launch
import mindustry.gen.Groups
import mindustry.net.Administration
import plugin.Bundle
import plugin.Config
import plugin.KVars.globalScope
import plugin.PVars
import plugin.PVars.joinDemographics
import plugin.PVars.serverCommands
import plugin.antigrief.loadGraylist
import plugin.database.BanListener
import plugin.database.getPlayerData
import plugin.database.models.Server
import plugin.discord.sendLog
import plugin.events.PEvents
import plugin.gamemodes.crawlerarena.CrawlerArenaGamemode
import plugin.gamemodes.hexed.HexedGamemode
import plugin.gamemodes.hexed.HexedGamemode.hexedGamemode
import plugin.gamemodes.tdf.TDGamemode
import plugin.menus.Menu
import plugin.menus.TextMenu
import plugin.menus.loadMenus
import plugin.packets.Packets
import plugin.patches.Patches
import kotlin.system.exitProcess

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
        loadGraylist()

        PVars.version = getResource("version")!!.readString()

        loadMenus()

        Timer.schedule({
            // ipJoins.clear();
            if (joinDemographics.size > 7000) joinDemographics.clear();
        }, 60f, 60f);

        /*
        if(PVars.gamemode != Gamemode.hexed && Core.settings.getBool("autorestarted", false)) {
            if(state.isGame) {
                Vars.net.closeServer()
                ServerControl.instance.cancelPlayTask()
                state.set(GameState.State.menu)
                Log.info("Stopped server.")
            }

            loadSave("autorestart")

            Core.settings.put("autorestarted", false)
        }*/

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
        when (PVars.gamemode) {
            Gamemode.tdefense -> TDGamemode.load()
            Gamemode.hexed -> {
                hexedGamemode = HexedGamemode()
                hexedGamemode.init()
                hexedGamemode.registerServerCommands(serverCommands)
            }

            Gamemode.crawlerArena -> CrawlerArenaGamemode.init()
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
        Timer.schedule({
            Groups.player.each { p ->
                globalScope.launch {
                    getPlayerData(p).ifPresent { stats ->
                        stats.update(p, false)
                    }
                }
            }
        }, (15 * 60).toFloat(), (15 * 60).toFloat())
        if (PVars.lokiLoggingEnabled) Timer.schedule({ pushLogs() }, 0f, (5 * 60).toFloat())
    }

    fun loadServerId() {
        val serverOpt = Server.getOrCreateServer()
        if (serverOpt.isPresent) PVars.serverId = serverOpt.get().id
        else Log.err("Сannot create/get server record. Server is unstable")
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
