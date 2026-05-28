package plugin.history;

import mindustry.type.UnitType;
import mindustry.world.Block;

import java.util.Optional;

public record HistoryRecord(String playerName, Optional<Integer> playerId, HistoryType type, Block block,
                            UnitType unit) {
    public String getMessage() {
        String actor = playerName == null ?
                unit == null ? "?" : unit.emoji() :
                playerName;
        if (playerId.isPresent())
            actor = playerId.get() + " " + actor;
        return switch (type) {
            case rotate -> actor + " [tan]rotated[white] " + block.emoji();
            case breakBlock -> actor + " [red]broken[white] " + block.emoji();
            case buildBlock -> actor + " [green]built[white] " + block.emoji();
            case configure -> actor + " [tan]configured[white] " + block.emoji();
            case destroyBlock -> block.emoji() + " [red]destroyed";
            default -> actor + " [tan]" + type.name() + "[white] " + block.emoji();
        };
    }
}
