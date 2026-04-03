package plugin.menus;

import arc.Events;
import arc.func.Cons2;
import arc.struct.ObjectMap;
import mindustry.game.EventType;
import mindustry.gen.Call;
import mindustry.gen.Player;

/**
 * Memory leak here btw.
 */
public class TextMenu {
    static int lastId = 0;
    static ObjectMap<Integer, TextMenu> menus = new ObjectMap<>();

    Player player;
    int id;
    Cons2<Player, String> handler;
    String title = "No title", message = "Write here", defMessage = "";
    int len = 32;
    boolean numeric = false;

    TextMenu(Cons2<Player, String> handler) {
        this.handler = handler;
        lastId += 1;
        this.id = lastId;
    }

    public TextMenu setTitle(String var) {
        this.title = var;
        return this;
    }

    public TextMenu setMessage(String var) {
        this.message = var;
        return this;
    }

    public TextMenu setDefMessage(String var) {
        this.defMessage = var;
        return this;
    }

    public TextMenu setLen(int var) {
        this.len = var;
        return this;
    }

    public TextMenu setNumeric(boolean var) {
        this.numeric = var;
        return this;
    }

    public TextMenu show(Player p) {
        Call.textInput(p.con, id, title, message, len, defMessage, numeric);

        this.player = p;

        menus.put(id, this);

        return this;
    }

    public static void load() {
        Events.on(EventType.PlayerLeave.class, e -> {
            menus.each((id, menu) -> {
                if (menu.player == e.player) menus.remove(id);
            });
        });

        Events.on(EventType.TextInputEvent.class, (e) -> {
            int id = e.textInputId;
            Player player = e.player;
            String text = e.text;

            TextMenu menu = menus.get(id);
            if (menu == null) {
                player.sendMessage("[scarlet]Unknown input!");
                return;
            }
            if (menu.player != player) {
                player.sendMessage("[scarlet]Unknown input!");
                return;
            }

            menus.remove(id);
            menu.handler.get(player, text);
        });
    }
}
