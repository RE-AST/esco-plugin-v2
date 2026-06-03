package plugin.gamemodes.hexed;

import arc.Core;
import arc.Events;
import arc.files.Fi;
import arc.math.Mathf;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.*;
import mindustry.content.Blocks;
import mindustry.core.GameState.State;
import mindustry.core.NetServer.TeamAssigner;
import mindustry.game.EventType.BlockDestroyEvent;
import mindustry.game.EventType.PlayerConnectionConfirmed;
import mindustry.game.EventType.PlayerLeave;
import mindustry.game.EventType.Trigger;
import mindustry.game.Rules;
import mindustry.game.Schematic;
import mindustry.game.Schematic.Stile;
import mindustry.game.Schematics;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.net.WorldReloader;
import mindustry.type.ItemStack;
import mindustry.world.Tile;
import mindustry.world.blocks.storage.CoreBlock;
import plugin.gamemodes.hexed.HexData.HexCaptureEvent;
import plugin.gamemodes.hexed.HexData.HexMoveEvent;
import plugin.gamemodes.hexed.HexData.HexTeam;
import plugin.gamemodes.hexed.HexData.ProgressIncreaseEvent;

import static arc.util.Log.info;
import static mindustry.Vars.*;
import static plugin.PPlugin.mainClass;

public class HexedGamemode {
    @Nullable
    public static HexedGamemode hexedGamemode = null;

    //TODO: move these to a config file or Config

    //in seconds
    public static final float spawnDelay = 60 * 4;
    //health requirement needed to capture a hex; no longer used
    public static final float healthRequirement = 3500;
    //item requirement to captured a hex
    public static final int itemRequirement = 210;

    public static final int messageTime = 1;
    //in ticks: 60 minutes
    public final static int roundTime = 60 * 60 * 90;
    //in ticks: 3 minutes
    public final static int leaderboardTime = 60 * 60 * 2;

    public final static int updateTime = 60 * 2;

    public final static int winCondition = 10;

    private final static int timerBoard = 0, timerUpdate = 1, timerWinCheck = 2;

    private final Rules rules = new Rules();
    private final Interval interval = new Interval(5);

    public HexData data;
    private boolean restarting = false;

    private Schematic baseSchematic;
    private double counter = 0f;
    private int lastMin;

    private final ObjectMap<String, LeavedPlayer> leavedPlayerTimers = new ObjectMap<>();
    public void init(){
        rules.pvp = true;
        rules.tags.put("hexed", "true");
        rules.canGameOver = false;
        rules.polygonCoreProtection = false;
        rules.enemyCoreBuildRadius = 31.5f * 8f;
        rules.placeRangeCheck = true;
        rules.pvpAutoPause = false;

        //attempt to load the base schematic from mods/hexed/base.msch, defaulting to a built-in one upon failure.
        Fi baseFile = mainClass.getConfig().sibling("base.msch");
        if(baseFile.exists()){
            try{
                baseSchematic = Schematics.read(baseFile);
            }catch(Exception e){
                Log.err("Failed to load base schematic file.", e);
            }
        }

        if(baseSchematic == null){
            baseSchematic = Schematics.readBase64("bXNjaAF4nE1S226cMBA9mIuNoZu0/4HUvuYf8tLXqg8OOAkS2MiwiVZRPr3dznjaKoD3eDxzjuey6HBToQpu9aj359GvsGMMhw/HvdvQv329e1u8m+6+vb+jm/w+pnk75hgANIt78MsO9ePnDb4czzHN53V4dcsyLC49efQfz3Dr0hqTnwbSf/GXmFC6NKLbI0UPmwt+QZ/85may4hwO2C2++jSEOHnofXTH4RP6kUSGcB4Xf97x+QP7763N6sNEgeYcluh417HkoxuPmC7QD1nnAvM/D+A7LRT5Q4n8VIDiIgW0gBFfK5GdWD1bbBO14KUIMq8QXsE89nUoOPLEBCVk9tJdivxlTUBb9tXsUaKiREWxSkPQGnW9ZgbrK1iWUJKGEvFSKij5ZaBlSFOLL4dUUgjfqSxZFYqOfkisp8O+gFRVSXTN0Z8ISInBouTD7GuQe9Ggqtm0tLNcaCNuLYVqzlLTS2lXBJYz08QrTwQ9p6vphkzIvNzrhqA2RLn+vv6iqpETNtwHBi1guGmGx1ITWL7AsDJDVjaibES5/TfFgsfS5kYS5Im3It2KdMvS7GslspPIXlROUm6JPITMs8KzkpLlv8gfnoxifw==");
        }

        Events.run(Trigger.update, () -> {
            if(active()){
                data.updateStats();

                for(Player player : Groups.player){
                    if(!restarting && player.team() != Team.derelict && player.team().cores().isEmpty()){
                        Log.info("elim: player=@ restarting=@ active=@", player.name, restarting, active());
                        player.clearUnit();
                        killTiles(player.team());
                        Call.sendMessage("[yellow](!)[] [accent]" + player.name + "[lightgray] has been eliminated![yellow] (!)");
                        Call.infoMessage(player.con, "Your cores have been destroyed. You are defeated.");
                        player.team(Team.derelict);
                    }

                    if(player.team() == Team.derelict){
                        player.clearUnit();
                    }else if(data.getControlled(player).size == data.hexes().size){
                        endGame();
                        break;
                    }
                }

                int minsToGo = (int)(roundTime - counter) / 60 / 60;
                if(minsToGo != lastMin){
                    lastMin = minsToGo;
                }

                if(interval.get(timerBoard, leaderboardTime)){
                    Call.infoToast(getLeaderboard(), 15f);
                }

                if(interval.get(timerUpdate, updateTime)){
                    data.updateControl();
                }

                if(interval.get(timerWinCheck, 60 * 2)){
                    Seq<Player> players = data.getLeaderboard();
                    if(!players.isEmpty() && data.getControlled(players.first()).size >= winCondition && players.size > 1 && data.getControlled(players.get(1)).size <= 1){
                        endGame();
                    }
                }

                counter += Time.delta;

                //kick everyone and restart w/ the script
                if(counter > roundTime && !restarting){
                    endGame();
                }
            }else{
                counter = 0;
            }
        });

        Events.on(BlockDestroyEvent.class, event -> {
            //reset last spawn times so this hex becomes vacant for a while.
            if(event.tile.block() instanceof CoreBlock){
                Hex hex = data.getHex(event.tile.pos());

                if(hex != null){
                    //update state
                    hex.spawnTime.reset();
                    hex.updateController();
                }
            }
        });

        Events.on(PlayerLeave.class, event -> {
            Team team = event.player.team();
            if(active() && team != Team.derelict){
                String uuid = event.player.uuid();
                LeavedPlayer old = leavedPlayerTimers.remove(uuid);
                if(old != null) old.removeTask.cancel();
                leavedPlayerTimers.put(uuid, new LeavedPlayer(
                        team,
                        Timer.schedule(()->{
                            leavedPlayerTimers.remove(uuid);
                            killTiles(team);
                        }, 5 * 60)
                ));
            }
        });

        Events.on(PlayerConnectionConfirmed.class, event -> {
            if(!active()) return;

            LeavedPlayer leaved = leavedPlayerTimers.remove(event.player.uuid());

            if(leaved != null){
                leaved.removeTask.cancel();
                event.player.team(leaved.team);
                data.data(event.player).lastMessage.reset();
                return;
            }

            if(event.player.team() == Team.derelict) return;

            Seq<Hex> copy = data.hexes().copy();
            copy.shuffle();
            Hex hex = copy.find(h -> h.controller == null && h.spawnTime.get());

            if(hex != null){
                loadout(event.player, hex.x, hex.y);
                Core.app.post(() -> data.data(event.player).chosen = false);
                hex.findController();
            }else{
                Call.infoMessage(event.player.con, "There are currently no empty hex spaces available.\nAssigning into spectator mode.");
                event.player.unit().kill();
                event.player.team(Team.derelict);
            }

            data.data(event.player).lastMessage.reset();
        });

        Events.on(ProgressIncreaseEvent.class, event -> updateText(event.player()));

        Events.on(HexCaptureEvent.class, event -> updateText(event.player()));

        Events.on(HexMoveEvent.class, event -> updateText(event.player()));

        TeamAssigner prev = netServer.assigner;
        netServer.assigner = (player, players) -> {
            Seq<Player> arr = Seq.with(players);

            if(active()){
                //pick first inactive team
                for(Team team : Team.all){
                    if(team.id > 5 && !team.active() && !arr.contains(p -> p.team() == team) && !data.data(team).dying && !data.data(team).chosen){
                        data.data(team).chosen = true;
                        return team;
                    }
                }
                Call.infoMessage(player.con, "There are currently no empty hex spaces available.\nAssigning into spectator mode.");
                return Team.derelict;
            }else{
                return prev.assign(player, players);
            }
        };
    }

    void updateText(Player player){
        HexTeam team = data.data(player);

        StringBuilder message = new StringBuilder("[white]Hex #" + team.location.id + "\n");

        if(!team.lastMessage.get()) return;

        if(team.location.controller == null){
            if(team.progressPercent > 0){
                message.append("[lightgray]Capture progress: [accent]").append((int)(team.progressPercent)).append("%");
            }else{
                message.append("[lightgray][[Empty]");
            }
        }else if(team.location.controller == player.team()){
            message.append("[yellow][[Captured]");
        }else if(team.location != null && team.location.controller != null && data.getPlayer(team.location.controller) != null){
            message.append("[#").append(team.location.controller.color).append("]Captured by ").append(data.getPlayer(team.location.controller).name);
        }else{
            message.append("<Unknown>");
        }

        Call.setHudText(player.con, message.toString());
    }

    public void registerServerCommands(CommandHandler handler){
        handler.register("hexed", "Begin hosting with the Hexed gamemode.", args -> {
            if(!state.is(State.menu)){
                Log.err("Stop the server first.");
                return;
            }

            data = new HexData();

            logic.reset();
            Log.info("Generating map...");
            HexedGenerator generator = new HexedGenerator();
            world.loadGenerator(Hex.size, Hex.size, generator);
            data.initHexes(generator.getHex());
            info("Map generated.");
            state.rules = rules.copy();
            logic.play();
            netServer.openServer();
        });

        handler.register("countdown", "Get the hexed restart countdown.", args -> {
            Log.info("Time until round ends: &lc@ minutes", (int)(roundTime - counter) / 60 / 60);
        });

        handler.register("end", "End the game.", args -> endGame());

        handler.register("r", "Restart the server.", args -> System.exit(2));
    }

    void endGame(){
        if(restarting) return;

        restarting = true;
        leavedPlayerTimers.each((uuid, leaved) -> leaved.removeTask.cancel());
        leavedPlayerTimers.clear();

        Seq<Player> players = data.getLeaderboard();
        StringBuilder builder = new StringBuilder();
        for(int i = 0; i < players.size && i < 3; i++){
            if(data.getControlled(players.get(i)).size > 1){
                builder.append("[yellow]").append(i + 1).append(".[accent] ").append(players.get(i).name)
                .append("[lightgray] (x").append(data.getControlled(players.get(i)).size).append(")[]\n");
            }
        }

        if(!players.isEmpty()){
            boolean dominated = data.getControlled(players.first()).size == data.hexes().size;

            for(Player player : Groups.player){
                Call.infoMessage(player.con, "[accent]--ROUND OVER--\n\n[lightgray]"
                + (player == players.first() ? "[accent]You[] were" : "[yellow]" + players.first().name + "[lightgray] was") +
                " victorious, with [accent]" + data.getControlled(players.first()).size + "[lightgray] hexes conquered." + (dominated ? "" : "\n\nFinal scores:\n" + builder));
            }
        }

        // Groups.player.each(p->p.team(Team.derelict));

        Time.runTask(60f * 10f, () -> {
            Groups.player.each(p->p.team(Team.derelict));
            counter = 0;
            lastMin = 0;
            data = new HexData();

            WorldReloader reloader = new WorldReloader();
            reloader.begin();

            HexedGenerator generator = new HexedGenerator();
            world.loadGenerator(Hex.size, Hex.size, generator);
            data.initHexes(generator.getHex());

            state.rules = rules.copy();
            logic.play();

            reloader.end();

/*
            Seq<Hex> available = data.hexes().copy();
            available.shuffle();

            for(Player player : Groups.player){
                Team newTeam = null;
                for(Team team : Team.all){
                    if(team.id > 5 && !team.active()){
                        newTeam = team;
                        break;
                    }
                }

                if(newTeam == null){
                    player.team(Team.derelict);
                    Call.infoMessage(player.con, "There are currently no empty hex spaces available.\nAssigning into spectator mode.");
                    continue;
                }

                player.team(newTeam);
                data.data(newTeam).chosen = true;

                Hex hex = available.find(h -> h.controller == null && h.spawnTime.get());
                if(hex != null){
                    available.remove(hex);
                    loadout(player, hex.x, hex.y);
                    data.data(player).chosen = false;
                    hex.findController();
                }else{
                    player.team(Team.derelict);
                    Call.infoMessage(player.con, "There are currently no empty hex spaces available.\nAssigning into spectator mode.");
                }
            }*/
            /*
            Timer.schedule(()->{
                Groups.player.each(p->{
                    if(p == null) return;

                    Seq<Hex> copy = data.hexes().copy();
                    copy.shuffle();
                    Hex hex = copy.find(h -> h.controller == null && h.spawnTime.get());

                    if(hex != null){
                        loadout(player, hex.x, hex.y);
                        Core.app.post(() -> data.data(player).chosen = false);
                        hex.findController();
                    }else{
                        Call.infoMessage(player.con, "There are currently no empty hex spaces available.\nAssigning into spectator mode.");
                        player.unit().kill();
                        player.team(Team.derelict);
                    }

                    data.data(player).lastMessage.reset();
                });

                restarting = false;
            }, 1f);*/

            restarting = false;

            Log.info("Hexed map regenerated, new round started.");
        });
    }

    public String getLeaderboard(){
        StringBuilder builder = new StringBuilder();
        builder.append("[accent]Leaderboard\n[scarlet]").append(lastMin).append("[lightgray] mins. remaining\n\n");
        int count = 0;
        for(Player player : data.getLeaderboard()){
            builder.append("[yellow]").append(++count).append(".[white] ")
            .append(player.name).append("[orange] (").append(data.getControlled(player).size).append(" hexes)\n[white]");

            if(count > 4) break;
        }
        return builder.toString();
    }

    public void killTiles(Team team){
        data.data(team).dying = true;
        Time.runTask(8f, () -> data.data(team).dying = false);
        for(int x = 0; x < world.width(); x++){
            for(int y = 0; y < world.height(); y++){
                Tile tile = world.tile(x, y);
                if(tile.build != null && tile.team() == team){
                    Time.run(Mathf.random(60f * 6), tile.build::kill);
                }
            }
        }
    }

    void loadout(Player player, int x, int y){
        Stile coreTile = baseSchematic.tiles.find(s -> s.block instanceof CoreBlock);
        if(coreTile == null) throw new IllegalArgumentException("Schematic has no core tile. Exiting.");
        int ox = x - coreTile.x, oy = y - coreTile.y;
        baseSchematic.tiles.each(st -> {
            Tile tile = world.tile(st.x + ox, st.y + oy);
            if(tile == null) return;

            if(tile.block() != Blocks.air){
                tile.removeNet();
            }

            tile.setNet(st.block, player.team(), st.rotation);

            if(st.config != null){
                tile.build.configureAny(st.config);
            }
            if(tile.block() instanceof CoreBlock){
                for(ItemStack stack : state.rules.loadout){
                    Call.setItem(tile.build, stack.item, stack.amount);
                }
            }
        });
    }

    public boolean active(){
        return state.rules.tags.getBool("hexed") && !state.is(State.menu);
    }


    private static class LeavedPlayer{
        public Team team;
        public Timer.Task removeTask;
        public LeavedPlayer(Team team, Timer.Task task){
            this.team = team;
            this.removeTask = task;
        }
    }
}
