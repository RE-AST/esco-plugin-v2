package plugin.utils;

import arc.files.Fi;
import arc.func.Cons;
import arc.util.Http;
import arc.util.Log;
import arc.util.Strings;
import mindustry.Vars;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Random;
import java.util.zip.InflaterInputStream;

import static mindustry.Vars.charset;
import static plugin.PVars.vpnApi;

public class Utils {
    public static final String characters = "qwertyuiopasdfghjklzxcvbnm123456789=";
    public static void isAnon(String ip, Runnable callback) {
        Http.get(
                vpnApi + ip,
                (resp)->{
                    if(resp.getResultAsString().contains("\"anon\":true"))
                        callback.run();
                },
                (err)->{
                    Log.err("Failed to check ip", err);
                }
        );
    }

    public static int parseBool(String bool) {
        return switch(bool.toLowerCase()) {
            case "y", "yes", "д", "да", "+", "t", "true" -> 1;
            case "n", "no", "н", "нет", "-", "f", "false" -> -1;
            default -> 0;
        };
    }

    public static String getRandomString(int len) {
        StringBuilder sb = new StringBuilder();
        Random random = new Random();

        for(int i = 0;i < len;i++) {
            sb.append(characters.charAt(random.nextInt(characters.length())));
        }

        return sb.toString();
    }

    public static Fi getResource(String name) {
        return Vars.mods.locateMod("plugin").root.child(name);
    }

    public static String stripFoo(String string) {
        StringBuilder var1 = new StringBuilder(string);
        for (int i = string.length() - 1; i >= 0; i--) {
            if (var1.charAt(i) >= 0xf80 && var1.charAt(i) <= 0x107f) var1.deleteCharAt(i);
        }
        return var1.toString();
    }

    public static String formatTime(long time) {
        long days = time / 86400;
        long hours = (time % 86400) / 3600;
        long minutes = (time % 3600) / 60;
        long seconds = time % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d");
        if (hours > 0) sb.append(hours).append("h");
        if (minutes > 0) sb.append(minutes).append("m");
        if (seconds > 0 || sb.length() == 0) sb.append(seconds).append("s");

        return sb.toString().trim();
    }

    public static long parseTime(String time) {
        if (time.length() == 0 || !Character.isDigit(time.charAt(0)))
            return -1;
        char timeMod = Character.toLowerCase(time.charAt(time.length() - 1)); // last char

        if (Character.isDigit(timeMod)) {
            // minutes
            if (!Strings.canParseInt(time))
                return -1;
            return Long.parseLong(time) * 60;
        }

        time = time.substring(0, time.length() - 1);
        if (!Strings.canParseInt(time))
            return -1;

        long parsed = Long.parseLong(time);
        if (timeMod == 'h')
            return parsed * 60 * 60;
        if (timeMod == 'd')
            return parsed * 60 * 60 * 24;
        if (timeMod == 'w')
            return parsed * 60 * 60 * 24 * 7;
        if (timeMod == 'm')
            return parsed * 60 * 60 * 24 * 30;
        if (timeMod == 'y')
            return parsed * 60 * 60 * 24 * 365;
        return parsed;
    }

    public static String decompress(byte[] data){
        if (data == null) return "";
        try(DataInputStream stream = new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(data)))){

            stream.read(); // Version
            int bytelen = stream.readInt();
            if(bytelen > 1024 * 100) return "";
            byte[] bytes = new byte[bytelen];
            stream.readFully(bytes);

            return new String(bytes, charset);
        }catch(IOException e){
            Log.err(e);
        }
        return ""; // Somehow this failed to read the code
    }

    /*public static int countWords(String word, String text) {
        return (text.length() - text.replace(word, "").length()) / word.length();
    }*/

    public static int countWords(String w, String t) {
        return countOccurrences(w, t);
    }

    public static int countOccurrences(String word, String text) {
    if (word.isEmpty()) return 0;

    int count = 0;
    int index = 0;

    while ((index = text.indexOf(word, index)) != -1) {
        count++;
        index += word.length();
    }

    return count;
}
}
