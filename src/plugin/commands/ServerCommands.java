package plugin.commands;

import arc.util.CommandHandler;
import arc.util.Log;
import mindustry.gen.Groups;
import plugin.Bundle;
import plugin.utils.Loader;
import plugin.utils.Patches;
import static plugin.PVars.*;
import static plugin.discord.Bot.sendLog;

public class ServerCommands {
    public static void register(CommandHandler handler) {
        handler.register("reload-bundle", "reload bundle", (a)->{
            Bundle.load();
        });

        handler.register("despawn-units", "Despawn all unused untis", (a)->{
            Patches.despawnUnits();
        });

        handler.register("restart", "set needRestart to true.", (a)->{
            if(Groups.player.isEmpty()) {
                Loader.exit();
                return;
            }
            needRestart = true;
            sendLog("Now server needs a restart!");
            Log.info("Ok!");
        });

        handler.register("savelog", "save logs", (a)->{
            Loader.saveLogs();
        });
    }
}
