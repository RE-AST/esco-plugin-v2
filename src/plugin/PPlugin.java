package plugin;

import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Threads;
import mindustry.mod.Plugin;

import plugin.commands.ClientCommands;
import plugin.commands.CustomHandler;
import plugin.commands.ServerCommands;
import plugin.database.BanListener;
import plugin.discord.Bot;
import plugin.utils.Loader;
import plugin.menus.Menu;
import plugin.menus.TextMenu;
import plugin.patches.Patches;
import plugin.utils.MapPreview;

import static plugin.PVars.*;

public class PPlugin extends Plugin {
    @Override
    public void init() {
        Config.load();
        Bundle.load();
        Patches.load();
        PEvents.load();
        MapPreview.loadColors();
        Loader.loadServerId();
        Loader.loadLogging();
        Loader.loadTimers();
        Loader.loadGamemode();
        BanListener.load();
        Menu.load();
        TextMenu.load();
        // Foos.Companion.init();

        Threads.daemon(Bot::load);

        Log.info("Plugin successfully loaded!");
    }

    @Override
    public void registerServerCommands(CommandHandler handler){
        ServerCommands.register(handler);
    }

    @Override
    public void registerClientCommands(CommandHandler handler){
        clientCommands = new CustomHandler(handler);
        Foos.Companion.init();
        ClientCommands.register(clientCommands);
    }
}
