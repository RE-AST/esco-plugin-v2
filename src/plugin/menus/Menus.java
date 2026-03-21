package plugin.menus;

import arc.math.Mathf;
import arc.struct.ObjectIntMap;
import arc.struct.Seq;
import arc.util.Timekeeper;
import mindustry.Vars;
import mindustry.content.UnitTypes;
import mindustry.gen.Building;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.net.Administration;
import mindustry.type.UnitType;
import plugin.Bundle;
import plugin.database.models.PlayerData;
import plugin.database.models.PlayerStats;
import plugin.utils.Permission;

import java.util.concurrent.atomic.AtomicInteger;

import static plugin.Bundle.label;
import static plugin.Bundle.sendMessage;
import static plugin.PVars.discordLink;
import static plugin.PVars.gamemode;
import static plugin.database.GettersKt.*;
import static plugin.utils.Gamemode.pvp;
import static plugin.utils.UtilsKt.parseTime;

public class Menus {
}
