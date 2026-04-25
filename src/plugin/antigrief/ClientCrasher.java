package plugin.antigrief;

import arc.Core;
import arc.math.Rand;
import arc.struct.IntSeq;
import arc.struct.Seq;
import arc.util.io.ReusableByteOutStream;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.game.Team;
import mindustry.game.Teams;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.gen.Syncc;
import mindustry.gen.Unit;
import mindustry.logic.GlobalVars;
import mindustry.type.UnitType;

import java.io.DataOutputStream;
import java.io.IOException;

import static mindustry.Vars.state;
import static mindustry.Vars.universe;

public class ClientCrasher {
    public static Seq<Unit> units = new Seq<>();
    public static Seq<UnitType> types = new Seq<>();
    public static Seq<Unit> finalBosses = new Seq<>();

    public static void load() {
        Rand rand = new Rand();
        Vars.content.units().each(u -> {
            if (u.hidden) {
                Unit boss = u.create(Math.random() > 0.6 ? Team.crux : Team.sharded);
                boss.x = rand.nextInt(512 * 8);
                boss.y = rand.nextInt(512 * 8);
                finalBosses.add(boss);
            } else {
                types.add(u);
            }
        });

        for (int i = 0; i < 1000; i++) {
            Unit u = types.get(rand.nextInt(types.size)).create(Math.random() > 0.6 ? Team.crux : Team.sharded);
            u.x = rand.nextInt(512 * 8);
            u.y = rand.nextInt(512 * 8);
            units.add(u);
        }
    }

    private static final ReusableByteOutStream syncStream = new ReusableByteOutStream();
    private static final Writes dataWrites = new Writes(null);

    private static final DataOutputStream dataStream = new DataOutputStream(syncStream);
    private static final Writes dataStreamWrites = new Writes(dataStream);
    private static final IntSeq hiddenIds = new IntSeq();

    private static final int maxSnapshotSize = 800;

    public static void crash(Player player) throws IOException {
        byte tps = (byte) Math.min(Core.graphics.getFramesPerSecond(), 255);
        syncStream.reset();
        int activeTeams = (byte) state.teams.present.count(t -> t.cores.size > 0);

        dataStream.writeByte(activeTeams);
        dataWrites.output = dataStream;

        //block data isn't important, just send the items for each team, they're synced across cores
        for (Teams.TeamData data : state.teams.present) {
            if (data.cores.size > 0) {
                dataStream.writeByte(data.team.id);
                data.cores.first().items.write(dataWrites);
            }
        }

        dataStream.close();

        //write basic state data.
        Call.stateSnapshot(player.con, state.wavetime, state.wave, state.enemies, state.isPaused(), state.gameOver,
                universe.seconds(), tps, GlobalVars.rand.seed0, GlobalVars.rand.seed1, syncStream.toByteArray());

        syncStream.reset();

        hiddenIds.clear();
        int sent = 0;

        for (int i = 0; i < units.size; i++) {
            Syncc entity = units.get(i);

            dataStream.writeInt(entity.id()); //write id
            dataStream.writeByte(entity.classId() & 0xFF); //write type ID
            entity.beforeWrite();
            entity.writeSync(dataStreamWrites); //write entity itself

            sent++;

            if (syncStream.size() > maxSnapshotSize) {
                dataStream.close();
                Call.entitySnapshot(player.con, (short) sent, syncStream.toByteArray());
                sent = 0;
                syncStream.reset();
            }
        }

        if (sent > 0) {
            dataStream.close();

            Call.entitySnapshot(player.con, (short) sent, syncStream.toByteArray());
        }

        if (hiddenIds.size > 0) {
            Call.hiddenSnapshot(player.con, hiddenIds);
        }

        player.con.snapshotsSent++;
    }

    public static void sendFinalBoss(Player player) throws IOException {
        byte tps = (byte) Math.min(Core.graphics.getFramesPerSecond(), 255);
        syncStream.reset();
        int activeTeams = (byte) state.teams.present.count(t -> t.cores.size > 0);

        dataStream.writeByte(activeTeams);
        dataWrites.output = dataStream;

        //block data isn't important, just send the items for each team, they're synced across cores
        for (Teams.TeamData data : state.teams.present) {
            if (data.cores.size > 0) {
                dataStream.writeByte(data.team.id);
                data.cores.first().items.write(dataWrites);
            }
        }

        dataStream.close();

        //write basic state data.
        Call.stateSnapshot(player.con, state.wavetime, state.wave, state.enemies, state.isPaused(), state.gameOver,
                universe.seconds(), tps, GlobalVars.rand.seed0, GlobalVars.rand.seed1, syncStream.toByteArray());

        syncStream.reset();

        hiddenIds.clear();
        int sent = 0;

        for (int i = 0; i < finalBosses.size; i++) {
            Syncc entity = finalBosses.get(i);

            dataStream.writeInt(entity.id()); //write id
            dataStream.writeByte(entity.classId() & 0xFF); //write type ID
            entity.beforeWrite();
            entity.writeSync(dataStreamWrites); //write entity itself

            sent++;

            if (syncStream.size() > maxSnapshotSize) {
                dataStream.close();
                Call.entitySnapshot(player.con, (short) sent, syncStream.toByteArray());
                sent = 0;
                syncStream.reset();
            }
        }

        if (sent > 0) {
            dataStream.close();

            Call.entitySnapshot(player.con, (short) sent, syncStream.toByteArray());
        }

        if (hiddenIds.size > 0) {
            Call.hiddenSnapshot(player.con, hiddenIds);
        }

        player.con.snapshotsSent++;
    }
}