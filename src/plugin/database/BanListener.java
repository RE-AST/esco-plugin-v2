package plugin.database;

import arc.util.Log;
import arc.util.Strings;
import arc.util.Time;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;

import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static plugin.Bundle.sendMessage;
import static plugin.database.GettersKt.getBan;
import static plugin.database.GettersKt.getPlayerById;
import static plugin.discord.BotKt.sendLog;
import static plugin.utils.UtilsKt.formatTime;

public class BanListener {
    private static int failedTimes = 0;
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static void load() {
        executor.submit(() -> {
            try (Connection con = Database.dataSource.getConnection()) {
                PGConnection pgCon = con.unwrap(PGConnection.class);
                try (Statement st = con.createStatement()) {
                    st.execute("LISTEN new_ban");
                }

                while (true) {
                    PGNotification[] notifications = pgCon.getNotifications();

                    if (notifications != null)
                        for (PGNotification notify : notifications) {
                            String payload = notify.getParameter();
                            if (!Strings.canParseInt(payload)) continue;
                            int i = Strings.parseInt(payload);
                            getBan(i).ifPresent(ban ->
                                    getPlayerById(ban.playerId).ifPresent(p -> {
                                        sendMessage("advertise.banned", ban.playerId, p.coloredName(), ban.unbanTime == null ? "never" : formatTime((ban.unbanTime.toEpochMilli() - Time.millis()) / 1000), ban.reason);
                                        ban.kickPlayer(p);
                                    })
                            );
                        }

                    Thread.sleep(1500);
                }
            } catch (Exception e) {
                Log.err(e);
                sendLog(e.getMessage());
                if (failedTimes < 5) {
                    load();
                    failedTimes += 1;
                }
            }
        });
    }
}
