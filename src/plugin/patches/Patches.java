package plugin.patches;

import arc.*;
import arc.files.*;
import arc.util.*;
import arc.util.Timer;
import mindustry.*;
import mindustry.game.EventType.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.net.*;

import java.time.*;
import java.time.format.*;

import static arc.util.ColorCodes.*;
import static arc.util.Log.*;

import plugin.Bundle;
import plugin.ai.DumbAI;
import static plugin.PVars.gamemode;

public class Patches {
    protected static String[] tags = {"&lc&fb[DEBUG]&fr", "&lb&fb[INFO]&fr", "&ly&fb[WARN]&fr", "&lr&fb[ERROR]", ""};
    protected static DateTimeFormatter dateTime = DateTimeFormatter.ofPattern("MM-dd-yyyy HH:mm:ss");
    public static final Fi logFolder = Core.settings.getDataDirectory().child("logs/");
    public static Fi currentLogFile;

    public static void load() {
        if(gamemode.optimized) {
            patchUnits();
            Timer.schedule(()->{
                despawnUnits();
            }, 30*60, 30*60);
            Timer.schedule(()->{
                Call.sendMessage("[stat]Time to change map! Save your buildings.");
                Timer.schedule(()->{
                    Events.fire(new GameOverEvent(Team.derelict));
                }, 5);
            }, 60*60*2, 60*60*2);
        }
        loadLogger();
        replaceNetServer();
    }

    private static void replaceNetServer() {
        Core.app.removeListener(Vars.netServer);
        Vars.netServer.dispose();
        Vars.netServer = new NetServerPatched();
        Core.app.addListener(Vars.netServer);
    }

    private static void patchUnits() {
        Vars.content.units().each(u->{
            if(!u.hidden)
                u.controller = (un) -> new DumbAI();
        });
    }

    public static void despawnUnits() {
        Log.info("Time to despawn unused units!");
        Bundle.sendMessage("unitdespawn");
        Groups.unit.each(u->{
            if(!u.controller().toString().toLowerCase().startsWith("player"))
                u.kill();
        });
    }

    public static void loadLogger() {
        Log.logger = (level1, text) -> {
            //err has red text instead of reset.
            if(level1 == LogLevel.err) text = text.replace(reset, lightRed + bold);

            String result = bold + lightBlack + "[" + dateTime.format(LocalDateTime.now()) + "] " + reset + format(tags[level1.ordinal()] + " " + text + "&fr");
            System.out.println(result);

            if(Administration.Config.logging.bool()){
                logToFile("[" + dateTime.format(LocalDateTime.now()) + "] " + formatColors(tags[level1.ordinal()] + " " + text + "&fr", false));
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

    public static void logToFile(String text){
        if(currentLogFile != null && currentLogFile.length() > Administration.Config.maxLogLength.num()){
            currentLogFile.writeString("[End of log file. Date: " + dateTime.format(LocalDateTime.now()) + "]\n", true);
            currentLogFile = null;
        }

        for(String value : values){
            text = text.replace(value, "");
        }

        if(currentLogFile == null){
            int i = 0;
            while(logFolder.child("log-" + i + ".txt").length() >= Administration.Config.maxLogLength.num()){
                i++;
            }

            currentLogFile = logFolder.child("log-" + i + ".txt");
        }

        currentLogFile.writeString(text + "\n", true);
    }
}
