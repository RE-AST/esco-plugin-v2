package plugin.utils;

import arc.util.Log.*;
import arc.util.Timer;
import plugin.database.models.Log;
import plugin.database.models.Server;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static arc.util.Log.*;
import static plugin.PVars.logsBuffer;
import static plugin.PVars.serverId;
import static plugin.database.models.Server.getOrCreateServer;

public class Loader {
    private static final ExecutorService logsExecutor = Executors.newSingleThreadExecutor();

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
