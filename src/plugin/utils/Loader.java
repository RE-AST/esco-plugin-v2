package plugin.utils;

import arc.util.Log.*;
import arc.util.Timer;
import mindustry.gen.Groups;
import mindustry.net.Administration;
// import plugin.antigrief.AntiFimoz;
import plugin.antigrief.ClientCrasher;
import plugin.database.models.Log;
import plugin.database.models.PlayerStats;
import plugin.database.models.Server;
import plugin.gamemodes.TDGamemode;
import plugin.packets.Packets;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static arc.util.Log.*;
import static plugin.Bundle.sendMessage;
import static plugin.PVars.*;
import static plugin.database.models.Server.getOrCreateServer;

public class Loader {
    private static final ExecutorService logsExecutor = Executors.newSingleThreadExecutor();

    public static void loadAfterStart() {
        // ClientCrasher.load();
        // AntiFimoz.load();
        Administration.Config.showConnectMessages.set(false);
        Packets.load();
    }

    public static void loadGamemode() {
        if(gamemode == Gamemode.tdefense)
            TDGamemode.load();
    }

    public static void loadTimers() {
        /*Timer.schedule(()->{
            if(!Groups.player.isEmpty())
                sendMessage("advertise.admins", discordLink); // we need admins bla-bla-bla...
        }, 15*60, 30*60);*/
        Timer.schedule(()->{
            if(!Groups.player.isEmpty())
                sendMessage("advertise.discord", discordLink);
        }, 15*60, 15*60);
        Timer.schedule(()->{
            if(!Groups.player.isEmpty())
                sendMessage("advertise.reports", discordLink);
        }, 15*60, 35*60);
        /*
        Timer.schedule(()->{
            Groups.player.each(p->{
                PlayerStats.getPlayerStats(p).ifPresent(s->{
                    info("Writing ALL player stats");
                    s.update(p, false);
                    s.write();
                });
            });
        }, 0, 6*60);*/
    }

    public static void loadServerId() {
        Optional<Server> serverOpt = getOrCreateServer();
        if(serverOpt.isPresent())
            serverId = serverOpt.get().id;
        else
            err("WTF, cannot create/get server record. Server is unstable");
    }

    public static void loadLogging() {
        float time = 5 * 60;
        Timer.schedule(Loader::saveLogs, time, time);
    }

    public static void saveLogs() {
        info("Saving @ logs", logsBuffer.size);

        while (logsBuffer.size > 0) {
            Log log = logsBuffer.pop();
            logsExecutor.submit(log::write);
        }
    }

    public static void exit() {
        info("Exiting server, please wait...");
        saveLogs();

        System.exit(0);
    }
}
