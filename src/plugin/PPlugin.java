package plugin;

import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Threads;
import mindustry.mod.Plugin;
import plugin.commands.ClientCommandsKt;
import plugin.commands.CustomHandler;
import plugin.commands.ServerCommandsKt;
import plugin.discord.BotKt;
import plugin.utils.Loader;

import static plugin.PVars.clientCommands;
import static plugin.PVars.serverCommands;

public class PPlugin extends Plugin {
    public static PPlugin mainClass;

    @Override
    public void init() {
        mainClass = this;
        Loader.load();

        Threads.daemon(BotKt::load);

        Log.info("Plugin successfully loaded!");
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        ServerCommandsKt.register(handler);
        serverCommands = handler;
        Log.info("Registered @ server commands", handler.getCommandList().size);
    }

    @Override
    public void registerClientCommands(CommandHandler handler) {
        clientCommands = new CustomHandler(handler);
        Foos.Companion.init();
        //ClientCommands.register(clientCommands);
        ClientCommandsKt.register(clientCommands);

        Log.info("Registered @ client commands", handler.getCommandList().size);
    }
}
