package plugin.patches;

import arc.Core;
import arc.struct.IntSeq;
import arc.struct.IntSet;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Time;
import arc.util.Timekeeper;
import arc.util.io.ReusableByteOutStream;
import arc.util.io.Writes;
import mindustry.core.GameState;
import mindustry.core.NetServer;
import mindustry.entities.units.BuildPlan;
import mindustry.game.Teams;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.gen.Syncc;
import mindustry.io.TypeIO;
import mindustry.logic.GlobalVars;
import mindustry.net.Administration;
import mindustry.world.blocks.storage.CoreBlock;

import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;

import static mindustry.Vars.*;
import static plugin.PVars.vanishedPlayers;

public class NetServerPatched extends NetServer {

    private static final int maxSnapshotSize = 800;

    private static final Timekeeper blockSyncTime = Timekeeper.ofSeconds(6f);
    private static final Timekeeper healthSyncTime = Timekeeper.ofSeconds(0.5f);
    private static final Timekeeper planPreviewSyncTime = Timekeeper.ofSeconds(0.5f);

    private static final TypeIO.ClientBuildPlans plansOut = new TypeIO.ClientBuildPlans();

    private static final class F {
        static final Field closing;
        static final Field pvpAutoPaused;

        static final Field buildHealthChanged;
        static final Field healthSeq;

        static final Field syncStream;
        static final Field dataStream;
        static final Field dataWrites;
        static final Field dataStreamWrites;
        static final Field hiddenIds;

        static {
            try {
                Class<?> c = NetServer.class;

                closing = field(c, "closing");
                pvpAutoPaused = field(c, "pvpAutoPaused");

                buildHealthChanged = field(c, "buildHealthChanged");
                healthSeq = field(c, "healthSeq");

                syncStream = field(c, "syncStream");
                dataStream = field(c, "dataStream");
                dataWrites = field(c, "dataWrites");
                dataStreamWrites = field(c, "dataStreamWrites");
                hiddenIds = field(c, "hiddenIds");

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private static Field field(Class<?> c, String name) throws Exception {
            Field f = c.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T get(Field f) {
        try {
            return (T) f.get(this);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void set(Field f, Object value) {
        try {
            f.set(this, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void update() {
        if (!headless && !(Boolean) get(F.closing) && net.server() && state.isMenu()) {
            set(F.closing, true);
            ui.loadfrag.show("@server.closing");
            Time.runTask(5f, () -> {
                net.closeServer();
                ui.loadfrag.hide();
                set(F.closing, false);
            });
        }

        if (state.isGame() && net.server()) {
            if (state.rules.pvp && state.rules.pvpAutoPause) {
                boolean waiting = isWaitingForPlayers(), paused = state.isPaused();
                if (waiting != paused) {
                    if (waiting) {
                        set(F.pvpAutoPaused, true);
                        state.set(GameState.State.paused);
                    } else if ((Boolean) get(F.pvpAutoPaused)) {
                        state.set(GameState.State.playing);
                        set(F.pvpAutoPaused, false);
                    }
                }
            }

            sync();
        }
    }

    void sync() {
        try {
            int interval = Administration.Config.snapshotInterval.num();

            Groups.player.each(p -> !p.isLocal(), player -> {
                if (player.con == null || !player.con.isConnected()) {
                    onDisconnect(player, "disappeared");
                    return;
                }

                var connection = player.con;

                if (Time.timeSinceMillis(connection.syncTime) < interval || !connection.hasConnected) return;

                connection.syncTime = Time.millis();

                try {
                    if (vanishedPlayers.isEmpty())
                        super.writeEntitySnapshot(player);
                    else
                        writeEntitySnapshot(player);
                } catch (IOException e) {
                    Log.err(e);
                }
            });

            if (Groups.player.size() > 0 && Core.settings.getBool("blocksync") && blockSyncTime.poll()) {
                writeBlockSnapshots();
            }

            if (Groups.player.size() > 0) {
                IntSet buildHealthChanged = (IntSet) get(F.buildHealthChanged);
                if (buildHealthChanged.size > 0 && healthSyncTime.poll()) {

                    IntSeq healthSeq = (IntSeq) get(F.healthSeq);
                    healthSeq.clear();

                    var iter = buildHealthChanged.iterator();
                    while (iter.hasNext) {
                        int next = iter.next();
                        var build = world.build(next);

                        if (build != null) {
                            healthSeq.add(next, Float.floatToRawIntBits(build.health));
                        }

                        if (healthSeq.size * 4 >= maxSnapshotSize) {
                            Call.buildHealthUpdate(healthSeq);
                            healthSeq.clear();
                        }
                    }

                    if (healthSeq.size > 0) {
                        Call.buildHealthUpdate(healthSeq);
                    }

                    buildHealthChanged.clear();
                }
            }

            if (Groups.player.size() > 0 && planPreviewSyncTime.poll()) {
                if (!headless) {
                    player.previewPlansCurrent.clear();
                    control.input.getSyncedPlans(player.previewPlansCurrent);
                    player.previewPlansCurrent.truncate(1000);
                }

                Groups.player.each(pl -> {
                    int id = ++pl.lastPreviewPlanGroupServer;
                    plansOut.clear();
                    Seq<BuildPlan> plans = pl.getPreviewPlans();
                    if (plans.isEmpty()) {
                        clientPlanSnapshotSend(pl, id, null);
                    } else {
                        BuildPlan[] items = plans.items;
                        int size = plans.size;
                        if (size < 75) {
                            plansOut.set(plans);
                            clientPlanSnapshotSend(pl, id, plansOut);
                        } else {
                            for (int i = 0; i < size; i += 75) {
                                int len = Math.min(i + 75, size) - i;
                                plansOut.ensureCapacity(len);
                                System.arraycopy(items, i, plansOut.items, 0, len);
                                plansOut.size = len;
                                clientPlanSnapshotSend(pl, id, plansOut);
                            }
                        }
                    }
                });
            }

        } catch (IOException e) {
            Log.err(e);
        }
    }

    private static void clientPlanSnapshotSend(Player player, int groupId, TypeIO.ClientBuildPlans plans) {
        for (Player other : player.team().data().players) {
            if (other != player && !other.isLocal() && other.con != null && other.con.isConnected()) {
                Call.clientPlanSnapshotReceived(other.con, player, groupId, plans);
            }
        }
    }

    @Override
    public void writeEntitySnapshot(Player player) throws IOException {
        byte tps = (byte) Math.min(Core.graphics.getFramesPerSecond(), 255);

        ReusableByteOutStream syncStream = (ReusableByteOutStream) get(F.syncStream);
        DataOutputStream dataStream = (DataOutputStream) get(F.dataStream);
        Writes dataWrites = (Writes) get(F.dataWrites);
        Writes dataStreamWrites = (Writes) get(F.dataStreamWrites);
        IntSeq hiddenIds = (IntSeq) get(F.hiddenIds);

        syncStream.reset();

        int activeTeams = (byte) state.teams.present.count(t -> t.cores.size > 0);

        dataStream.writeByte(activeTeams);
        dataWrites.output = dataStream;

        for (Teams.TeamData data : state.teams.present) {
            if (data.cores.size > 0) {
                dataStream.writeByte(data.team.id);
                ((CoreBlock.CoreBuild) data.cores.first()).items.write(dataWrites);
            }
        }

        dataStream.close();

        Call.stateSnapshot(player.con, state.wavetime, state.wave, state.enemies, state.isPaused(), state.gameOver,
                universe.seconds(), tps, GlobalVars.rand.seed0, GlobalVars.rand.seed1, syncStream.toByteArray());

        syncStream.reset();
        hiddenIds.clear();

        int sent = 0;
        for (Syncc entity : Groups.sync) {
            if (entity.isSyncHidden(player)) {
                hiddenIds.add(entity.id());
                continue;
            } else if (entity instanceof Player p && player != p && vanishedPlayers.contains(p)) {
                hiddenIds.add(entity.id());
                continue;
            }

            dataStream.writeInt(entity.id());
            dataStream.writeByte(entity.classId() & 0xFF);
            entity.beforeWrite();
            entity.writeSync(dataStreamWrites);

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
