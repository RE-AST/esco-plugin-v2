package plugin.menus;

import arc.Events;
import arc.func.Cons;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import mindustry.game.EventType;
import mindustry.gen.Call;
import mindustry.gen.Player;

public class Menu {
    static int lastId = 0;

    static final ObjectMap<Integer, Menu> menus = new ObjectMap<>();

    Player player;
    int id;
    Seq<Seq<String>> rows = new Seq<>();
    String title, message;
    Seq<Cons<Player>> handlers = new Seq<>();

    Menu(String title, String message) {
        this.title = title;
        this.message = message;
    }

    public Menu add(String text, Cons<Player> handler) {
        if (rows.isEmpty()) {
            rows.add(new Seq<String>());
        }

        rows.peek().add(text);

        handlers.add(handler);

        return this;
    }

    public Menu add(String text) {
        return add(text, (p) -> {
        });
    }

    public Menu show(Player player) {
        this.player = player;
        lastId += 1;
        this.id = lastId;

        menus.put(this.id, this);

        Call.menu(player.con, this.id, title, message, buildRows());

        return this;
    }

    public Menu row() {
        rows.add(new Seq<String>());
        return this;
    }

    String[][] buildRows() {
        String[][] rowsReturn = new String[rows.size][];

        for (int i = 0; i < rows.size; i++) {
            Seq<String> row = rows.get(i);
            rowsReturn[i] = new String[row.size];
            for (int o = 0; o < row.size; o++) {
                rowsReturn[i][o] = row.get(o);
            }
        }

        return rowsReturn;
    }

    public static void load() {
        Events.on(EventType.PlayerLeave.class, e -> {
            menus.each((id, menu) -> {
                if (menu.player == e.player) menus.remove(id);
            });
        });

        Events.on(EventType.MenuOptionChooseEvent.class, (e) -> {
            Player player = e.player;
            Menu menu = menus.get(e.menuId);
            if (menu == null || menu.player != player) {
                player.sendMessage("[scarlet]Invalid menu.");
                return;
            }

            int option = e.option;
            if (option == -1) {
                player.sendMessage("[red]Closed.");
                return;
            }
            if (option > menu.handlers.size) {
                player.sendMessage("[scarlet]Invalid option.");
                return;
            }
            menus.remove(e.menuId);
            menu.handlers.get(option).get(player);
        });
    }
}
