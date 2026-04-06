package plugin.utils

import arc.util.Log
import arc.util.Timer
import kotlinx.coroutines.launch
import mindustry.gen.Groups
import mindustry.net.Administration
import plugin.Bundle
import plugin.Config
import plugin.KVars.globalScope
import plugin.PVars
import plugin.database.BanListener
import plugin.database.models.Server
import plugin.events.PEvents
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

        PVars.version = getResource("version")!!.readString()

        loadMenus()
    }

    @JvmStatic
    fun loadAfterStart() {
        // ClientCrasher.load();
        // AntiFimoz.load();
        Administration.Config.showConnectMessages.set(false)
        Packets.load()
    }

    fun loadGamemode() {
        if (PVars.gamemode == Gamemode.tdefense) load()
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
        saveLogs()

        exitProcess(0)
    }
}
