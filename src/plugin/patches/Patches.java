package plugin.patches;

import arc.Core;
import arc.Events;
import arc.files.Fi;
import arc.util.Log;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.game.EventType.GameOverEvent;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.net.Administration;
import mindustry.world.blocks.storage.CoreBlock;
import plugin.Bundle;
import plugin.ai.DumbAI;
import plugin.utils.Gamemode;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static arc.util.ColorCodes.*;
import static arc.util.Log.*;
import static plugin.PVars.gamemode;
import static plugin.discord.BotKt.sendConsoleMessage;
import static plugin.utils.LokiLoggerKt.addLog;

public class Patches {
    protected static String[] tags = {"&lc&fb[DEBUG]&fr", "&lb&fb[INFO]&fr", "&ly&fb[WARN]&fr", "&lr&fb[ERROR]", ""};
    protected static DateTimeFormatter dateTime = DateTimeFormatter.ofPattern("MM-dd-yyyy HH:mm:ss");
    public static final Fi logFolder = Core.settings.getDataDirectory().child("logs/");
    public static Fi currentLogFile;

    public static void load() {
        if (gamemode == Gamemode.sandbox) {
            Vars.content.units().each(u -> {
                if (!u.hidden)
                    u.controller = (un) -> new DumbAI();
            });

            Timer.schedule(Patches::despawnUnits, 30 * 60, 30 * 60);

            Timer.schedule(() -> {
                Call.sendMessage("[stat]Time to change map! Save your buildings.");
                Timer.schedule(() -> {
                    Events.fire(new GameOverEvent(Team.derelict));
                }, 10);
            }, 60 * 60 * 6, 60 * 60 * 6);

            Vars.content.each(content -> {
                if (content instanceof CoreBlock block) {
                    block.health = 999999999;
                }
            });
        }
        loadLogger();

        Core.app.removeListener(Vars.netServer);
        Vars.netServer.dispose();
        Vars.netServer = new NetServerPatched();
        Core.app.addListener(Vars.netServer);
    }

    public static void despawnUnits() {
        Log.info("Time to despawn unused units!");
        Bundle.sendMessage("unitdespawn");
        Groups.unit.each(u -> {
            if (!u.controller().toString().toLowerCase().startsWith("player"))
                u.kill();
        });
    }

    public static void loadLogger() {
        Log.logger = (level1, text) -> {
            //err has red text instead of reset.
            if (level1 == LogLevel.err) text = text.replace(reset, lightRed + bold);

            String result = bold + lightBlack + "[" + dateTime.format(LocalDateTime.now()) + "] " + reset + format(tags[level1.ordinal()] + " " + text + "&fr");
            System.out.println(result);
            addLog(level1.name(), text);

            String cleanText = "[" + dateTime.format(LocalDateTime.now()) + "] " + formatColors(tags[level1.ordinal()] + " " + text + "&fr", false);

            sendConsoleMessage(cleanText.replaceAll("\u001B\\[[;\\d]*m", ""));

            if (Administration.Config.logging.bool()) {
                logToFile(cleanText);
            }

            /*if(socketOutput != null){
                try{
                    socketOutput.println(formatColors(text + "&fr", false));
                }catch(Throwable e1){
                    err("Error occurred logging to socket: @", e1.getClass().getSimpleName());
                }
            }*/
        };
    }

    public static void logToFile(String text) {
        if (currentLogFile != null && currentLogFile.length() > Administration.Config.maxLogLength.num()) {
            currentLogFile.writeString("[End of log file. Date: " + dateTime.format(LocalDateTime.now()) + "]\n", true);
            currentLogFile = null;
        }

        for (String value : values) {
            text = text.replace(value, "");
        }

        if (currentLogFile == null) {
            int i = 0;
            while (logFolder.child("log-" + i + ".txt").length() >= Administration.Config.maxLogLength.num()) {
                i++;
            }

            currentLogFile = logFolder.child("log-" + i + ".txt");
        }

        currentLogFile.writeString(text + "\n", true);
    }
}
