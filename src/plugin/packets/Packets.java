package plugin.packets;

import mindustry.Vars;
import mindustry.gen.AdminRequestCallPacket;
import mindustry.gen.Call;
import mindustry.gen.SendChatMessageCallPacket;
import mindustry.io.JsonIO;

import static plugin.PVars.SSUsers;

public class Packets {
    public static void load() {
        Vars.net.handleServer(AdminRequestCallPacket.class, AdminRequest::handle);
        // Vars.net.handleServer(SendChatMessageCallPacket.class, SendChatMessage::handle);

        loadCustom();
    }

    private static void loadCustom() {
        // scheme size integration
        Vars.netServer.addPacketHandler("MySubtitle", (target, args) -> {
            SSUsers.put(target.id, args);
            Call.clientPacketReliable("Subtitles", JsonIO.write(SSUsers));
        });
    }
}
