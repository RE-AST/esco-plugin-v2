package plugin.history;

import arc.math.geom.Point2;
import arc.struct.LongMap;
import mindustry.net.Administration;
import mindustry.type.UnitType;
import mindustry.world.Block;
import mindustry.world.Tile;

import java.util.Optional;

public class History {
    public static final LongMap<HistoryStack> history = new LongMap<>();

    public static String getMessage(int pos) {
        StringBuilder sb = new StringBuilder();
        // Point2 pos2 = Point2.unpack(pos);
        int x = Point2.x(pos), y = Point2.y(pos); // int int

        sb.append("[" + x + "] " + "[" + y + "]");


        HistoryStack stack = history.get(pos); // long
        //stack.stack.each(s->{sb.append("\n").append(s.getMessage());});
        if (stack != null)
            for (int i = 0; i < stack.size(); i++) {
                sb.append("\n").append(stack.stack.get(i).getMessage());
            }

        return sb.toString();
    }

    public static void write(Tile tile,
                             String playerName,
                             Optional<Integer> playerId,
                             HistoryType type,
                             Block block,
                             UnitType unit) {

        if (tile == null) return;

        HistoryRecord record = new HistoryRecord(playerName, playerId, type, block, unit);

        tile.getLinkedTiles(t -> {

            long pos = t.pos();

            HistoryStack stack = history.get(pos);

            if (stack == null) {
                stack = new HistoryStack();
                history.put(pos, stack);
            }

            if (stack.size() >= 6) {
                stack.removeFirst();
            }

            stack.add(record);

        });
    }

    public static void clear() {
        history.clear();
    }
}
