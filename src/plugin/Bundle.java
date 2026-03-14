package plugin;

import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.struct.StringMap;
import arc.util.Http;
import arc.util.Log;

import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;

import java.text.MessageFormat;

import static java.text.MessageFormat.format;
import static plugin.PVars.bundleApi;

public class Bundle {
    public static final Seq<String> locales = Seq.with("en", "ru");
    public static final ObjectMap<String, String> localesAliases = new ObjectMap<>();

    private static final ObjectMap<String, StringMap> bundles = new ObjectMap<>();

    public static String defaultLocale = "en";

    public static void load() {
        localesAliases.put("en_US", "en");
        localesAliases.put("ru_RU", "ru");

        for (String locale : locales) {
            loadLocale(locale);
        }
    }

    private static void loadLocale(String locale) {
        Http.get(bundleApi+locale)
                .header("Authorization", "Basic $apiAuth")
                .error(err->Log.err("Failed to load bundle locale '@'", locale, err))
                .submit(resp -> {
                    String content = resp.getResultAsString();

                    if (content == null || content.isEmpty()) {
                        Log.warn("Bundle locale '@' returned empty response", locale);
                        return;
                    }

                    StringMap map = new StringMap();

                    for (String line : content.split(";")) {
                        line = line.trim();

                        if (line.isEmpty() || line.startsWith("#")) continue;

                        int eq = line.indexOf('=');
                        if (eq <= 0) continue;

                        String key = line.substring(0, eq).trim();
                        String value = line.substring(eq + 1).trim();

                        map.put(key, value);
                    }

                    bundles.put(locale, map);
                    Log.info("Bundle locale '@' loaded (@ keys)", locale, map.size);
                });
        /*Http.get(bundleApi + locale,
                resp -> {
                    String content = resp.getResultAsString();

                    if (content == null || content.isEmpty()) {
                        Log.warn("Bundle locale '@' returned empty response", locale);
                        return;
                    }

                    StringMap map = new StringMap();

                    for (String line : content.split(";")) {
                        line = line.trim();

                        if (line.isEmpty() || line.startsWith("#")) continue;

                        int eq = line.indexOf('=');
                        if (eq <= 0) continue;

                        String key = line.substring(0, eq).trim();
                        String value = line.substring(eq + 1).trim();

                        map.put(key, value);
                    }

                    bundles.put(locale, map);
                    Log.info("Bundle locale '@' loaded (@ keys)", locale, map.size);

                },
                err -> Log.err("Failed to load bundle locale '@'", locale, err)
        );*/
    }

    public static String get(String key, String locale) {
        if(localesAliases.containsKey(locale))
            locale = localesAliases.get(locale);

        String value = getInternal(key, locale);
        if (value != null) return value;

        int underscore = locale.indexOf('_');
        if (underscore > 0) {
            String shortLocale = locale.substring(0, underscore);
            value = getInternal(key, shortLocale);
            if (value != null) return value;
        }

        if (!locale.equals(defaultLocale)) {
            value = getInternal(key, defaultLocale);
            if (value != null) return value;
        }

        return key;
    }

    public static String get(String key, String locale, Object... args) {
        return MessageFormat.format(get(key, locale), args);
    }

    public static String get(String key) {
        return get(key, defaultLocale);
    }

    private static String getInternal(String key, String locale) {
        StringMap map = bundles.get(locale);
        if (map == null) return null;
        return map.get(key);
    }

    public static void sendMessage(String req) {
        Groups.player.each(p->p.sendMessage(get(req, p.locale)));
    }

    public static void sendMessage(String req, Object... params) {
        Groups.player.each(p->p.sendMessage(format(get(req, p.locale), params)));
    }

    public static void sendMessage(String req, Player player, Object... params) {
        player.sendMessage(format(get(req, player.locale), params));
    }

    public static void sendMessage(String req, Player player) {
        player.sendMessage(get(req, player.locale));
    }

    public static void infoMessage(String req, Player player) {
        Call.infoMessage(player.con, Bundle.get(req, player.locale));
    }

    public static void infoMessage(String req, Player player, Object... params) {
        Call.infoMessage(player.con, format(Bundle.get(req, player.locale), params));
    }

    public static void label(String req, float dur, float x, float y, Object... params) {
        Groups.player.each(p->Call.label(p.con, MessageFormat.format(Bundle.get(req, p.locale), params), dur, x, y));
    }
}