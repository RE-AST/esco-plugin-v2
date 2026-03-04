package plugin.commands;

import arc.util.CommandHandler;
import arc.util.Log;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import plugin.Bundle;
import plugin.discord.Bot;
import plugin.gamemodes.TDGamemode;
import plugin.history.History;
import plugin.utils.Loader;
import plugin.patches.Patches;
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

        handler.register("historysize", "", (a)->{
            Log.info("Stacks: @", History.history.size);
        });

        handler.register("say", "<text...>", "", (a)->{
            Log.info("Server: @", a[0]);
            Call.sendMessage("[scarlet][Server]:[white] "+a[0]);
            Bot.sendServerMessage("Server: "+a[0]);
        });

        handler.register("loadgm", "<name>", "", (a)->{
            switch(a[0]) {
                case "tdf":
                    TDGamemode.load();
                    break;
                default:
                    Log.err("Unknown gamemode!");
            }
        });
    }
}
