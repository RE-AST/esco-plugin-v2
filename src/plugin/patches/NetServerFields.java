package plugin.patches;

import mindustry.core.NetServer;

import java.lang.reflect.Field;

final class NetServerFields {

    static final Field closing;
    static final Field pvpAutoPaused;

    static final Field timer;
    static final Field timerBlockSync;
    static final Field blockSyncTime;

    static final Field timerHealthSync;
    static final Field healthSyncTime;

    static final Field buildHealthChanged;
    static final Field healthSeq;

    static final Field maxSnapshotSize;

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

            timer = field(c, "timer");
            timerBlockSync = field(c, "timerBlockSync");
            blockSyncTime = field(c, "blockSyncTime");

            timerHealthSync = field(c, "timerHealthSync");
            healthSyncTime = field(c, "healthSyncTime");

            buildHealthChanged = field(c, "buildHealthChanged");
            healthSeq = field(c, "healthSeq");

            maxSnapshotSize = field(c, "maxSnapshotSize");

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
