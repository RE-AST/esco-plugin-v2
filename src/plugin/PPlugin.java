package plugin;

import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Threads;
import mindustry.mod.Plugin;
import plugin.commands.ClientCommandsKt;
import plugin.commands.CustomHandler;
import plugin.commands.ServerCommandsKt;
import plugin.database.BanListener;
import plugin.discord.BotKt;
import plugin.events.PEvents;
import plugin.menus.Menu;
import plugin.menus.TextMenu;
import plugin.patches.Patches;
import plugin.utils.Loader;
import plugin.utils.MapPreview;

import static plugin.PVars.clientCommands;

public class PPlugin extends Plugin {
    @Override
    public void init() {
        Loader.load();

        Threads.daemon(BotKt::load);

        Log.info("Plugin successfully loaded!");
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        ServerCommandsKt.register(handler);
    }

    @Override
    public void registerClientCommands(CommandHandler handler) {
        clientCommands = new CustomHandler(handler);
        Foos.Companion.init();
        //ClientCommands.register(clientCommands);
        ClientCommandsKt.register(clientCommands);
    }
}
