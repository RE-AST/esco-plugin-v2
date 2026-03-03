package plugin.patches;

import arc.Core;
import arc.struct.IntSeq;
import arc.struct.IntSet;
import arc.util.Interval;
import arc.util.Log;
import arc.util.Reflect;
import arc.util.Time;
import arc.util.io.ReusableByteOutStream;
import arc.util.io.Writes;
import mindustry.core.GameState;
import mindustry.core.NetServer;
import mindustry.game.Teams;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.gen.Syncc;
import mindustry.logic.GlobalVars;
import mindustry.net.Administration;

import java.io.DataOutputStream;
import java.io.IOException;

import static mindustry.Vars.*;
import static mindustry.Vars.net;
import static mindustry.Vars.state;
import static mindustry.Vars.ui;

public class NetServerPatched extends NetServer {
    @Override
    public void update(){
        if(!headless && !Reflect.<Boolean>get(NetServer.class, this, "closing") && net.server() && state.isMenu()){
            // closing = true;
            Reflect.set(NetServer.class, this, "closing", true);
            ui.loadfrag.show("@server.closing");
            Time.runTask(5f, () -> {
                net.closeServer();
                ui.loadfrag.hide();
                //closing = false;
                Reflect.set(NetServer.class, this, "closing", false);
            });
        }

        if(state.isGame() && net.server()){
            if(state.rules.pvp && state.rules.pvpAutoPause){
                boolean waiting = isWaitingForPlayers(), paused = state.isPaused();
                if(waiting != paused){
                    if(waiting){
                        //is now waiting, enable pausing, flag it correctly
                        //pvpAutoPaused = true;
                        Reflect.set(NetServer.class, this, "pvpAutoPaused", true);
                        state.set(GameState.State.paused);
                    }else if(Reflect.<Boolean>get(NetServer.class, this, "pvpAutoPaused")){
                        //no longer waiting, stop pausing
                        state.set(GameState.State.playing);
                        //pvpAutoPaused = false;
                        Reflect.set(NetServer.class, this, "pvpAutoPause", false);
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
                    writeEntitySnapshot(player);
                } catch (IOException e) {
                    Log.err(e);
                }
            });

            if (Groups.player.size() > 0 && Core.settings.getBool("blocksync") && Reflect.<Interval>get(NetServer.class, this, "timer").get(Reflect.<Integer>get(NetServer.class, this, "timerBlockSync"), Reflect.<Float>get(NetServer.class, this, "blockSyncTime"))) {
                writeBlockSnapshots();
            }

            if (Groups.player.size() > 0 && Reflect.<IntSet>get(NetServer.class, this, "buildHealthChanged").size > 0 && Reflect.<Interval>get(NetServer.class, this, "timer").get(Reflect.<Integer>get(NetServer.class, this, "timerHealthSync"), Reflect.<Float>get(NetServer.class, this, "healthSyncTime"))) {
                Reflect.<IntSeq>get(NetServer.class, this, "healthSeq").clear();

                var iter = Reflect.<IntSet>get(NetServer.class, this, "buildHealthChanged").iterator();
                while (iter.hasNext) {
                    int next = iter.next();
                    var build = world.build(next);

                    //pack pos + health into update list
                    if (build != null) {
                        Reflect.<IntSeq>get(NetServer.class, this, "healthSeq").add(next, Float.floatToRawIntBits(build.health));
                    }

                    //if size exceeds snapshot limit, send it out and begin building it up again
                    if (Reflect.<IntSeq>get(NetServer.class, this, "healthSeq").size * 4 >= Reflect.<Integer>get(NetServer.class, this, "maxSnapshotSize")) {
                        Call.buildHealthUpdate(Reflect.<IntSeq>get(NetServer.class, this, "healthSeq"));
                        Reflect.<IntSeq>get(NetServer.class, this, "healthSeq").clear();
                    }
                }

                //send any residual health updates
                if (Reflect.<IntSeq>get(NetServer.class, this, "healthSeq").size > 0) {
                    Call.buildHealthUpdate(Reflect.<IntSeq>get(NetServer.class, this, "healthSeq"));
                }

                Reflect.<IntSet>get(NetServer.class, this, "buildHealthChanged").clear();
            }
        } catch (IOException e) {
            Log.err(e);
        }
    }
    @Override
    public void writeEntitySnapshot(Player player) throws IOException{
        byte tps = (byte)Math.min(Core.graphics.getFramesPerSecond(), 255);
        //syncStream.reset();
        Reflect.<ReusableByteOutStream>get(NetServer.class, this, "syncStream").reset();

        int activeTeams = (byte)state.teams.present.count(t -> t.cores.size > 0);

        //dataStream.writeByte(activeTeams);
        Reflect.<DataOutputStream>get(NetServer.class, this, "dataStream").writeByte(activeTeams);
        //dataWrites.output = dataStream;
        Reflect.<Writes>get(NetServer.class, this, "dataWrites").output = Reflect.<DataOutputStream>get(NetServer.class, this, "dataStream");

        //block data isn't important, just send the items for each team, they're synced across cores
        for(Teams.TeamData data : state.teams.present){
            if(data.cores.size > 0){
                Reflect.<DataOutputStream>get(NetServer.class, this, "dataStream").writeByte(data.team.id);
                data.cores.first().items.write(Reflect.<Writes>get(NetServer.class, this, "dataWrites"));
            }
        }

        Reflect.<DataOutputStream>get(NetServer.class, this, "dataStream").close();

        //write basic state data.
        Call.stateSnapshot(player.con, state.wavetime, state.wave, state.enemies, state.isPaused(), state.gameOver,
                universe.seconds(), tps, GlobalVars.rand.seed0, GlobalVars.rand.seed1, Reflect.<ReusableByteOutStream>get(NetServer.class, this, "syncStream").toByteArray());

        Reflect.<ReusableByteOutStream>get(NetServer.class, this, "syncStream").reset();

        //hiddenIds.clear();
        Reflect.<IntSeq>get(NetServer.class, this, "hiddenIds").clear();
        int sent = 0;

        for(Syncc entity : Groups.sync){
            //TODO write to special list
            if(entity.isSyncHidden(player)){
                Reflect.<IntSeq>get(NetServer.class, this, "hiddenIds").add(entity.id());
                continue;
            }

            //write all entities now
            Reflect.<DataOutputStream>get(NetServer.class, this, "dataStream").writeInt(entity.id()); //write id
            Reflect.<DataOutputStream>get(NetServer.class, this, "dataStream").writeByte(entity.classId() & 0xFF); //write type ID
            entity.beforeWrite();
            entity.writeSync(Reflect.<Writes>get(NetServer.class, this, "dataStreamWrites")); //write entity itself

            sent++;

            if(Reflect.<ReusableByteOutStream>get(NetServer.class, this, "syncStream").size() > Reflect.<Integer>get(NetServer.class, this, "maxSnapshotSize")){
                Reflect.<DataOutputStream>get(NetServer.class, this, "dataStream").close();
                Call.entitySnapshot(player.con, (short)sent, Reflect.<ReusableByteOutStream>get(NetServer.class, this, "syncStream").toByteArray());
                sent = 0;
                Reflect.<ReusableByteOutStream>get(NetServer.class, this, "syncStream").reset();
            }
        }

        if(sent > 0){
            Reflect.<DataOutputStream>get(NetServer.class, this, "dataStream").close();

            Call.entitySnapshot(player.con, (short)sent, Reflect.<ReusableByteOutStream>get(NetServer.class, this, "syncStream").toByteArray());
        }

        if(Reflect.<IntSeq>get(NetServer.class, this, "hiddenIds").size > 0){
            Call.hiddenSnapshot(player.con, Reflect.<IntSeq>get(NetServer.class, this, "hiddenIds"));
        }

        player.con.snapshotsSent++;
    }
}
