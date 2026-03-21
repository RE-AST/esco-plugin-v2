package plugin.packets;

import arc.Events;
import arc.struct.Seq;
import arc.util.Log;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.AdminRequestCallPacket;
import mindustry.gen.Player;
import mindustry.net.NetConnection;
import mindustry.net.Packets;
import plugin.Bundle;
import plugin.utils.Permission;

import plugin.menus.MenusKt;

import static plugin.utils.Permission.getPerms;

public class AdminRequest {
    public static void handle(NetConnection con, AdminRequestCallPacket packet) {
        Player player = con.player;
        Player other = packet.other;
        Packets.AdminAction action = packet.action;
        Object params = packet.params;

        if (other == null) {
            return;
        }

        Events.fire(new EventType.AdminRequestEvent(player, other, action));

        Seq<Permission> perms = getPerms(player);

        switch (action) {
            case wave -> {
                if (!perms.contains(Permission.admin)) {
                    Bundle.sendMessage("noperms", player);
                    return;
                }
                Vars.logic.skipWave();
                Log.info("@ skipped wave!", player);
            }
            case ban -> {
                // ban(other, player, "Touch grass", parseTime("10"));
                if (!perms.contains(Permission.punish)) {
                    Bundle.sendMessage("noperms", player);
                    return;
                }
                player.sendMessage("use /ban");
            }
            case kick -> {
                if (!perms.contains(Permission.admin)) {
                    Bundle.sendMessage("noperms", player);
                    return;
                }
                other.kick(Bundle.get("admin.kicked") + " " + player.coloredName());
            }
            case trace -> {
                if (!perms.contains(Permission.admin)) {
                    Bundle.sendMessage("noperms", player);
                    return;
                }
                /*
                String uuid = other.uuid();
                var pdata = getPlayerData(other);

                if(pdata.isPresent())
                    uuid = uuid + " ID: "+pdata.get().id;

                Administration.PlayerInfo stats = netServer.admins.getInfo(other.uuid());
                Administration.TraceInfo info = new Administration.TraceInfo(other.con.address, uuid, other.locale, other.con.modclient, other.con.mobile, stats.timesJoined, stats.timesKicked, stats.ips.toArray(String.class), stats.names.toArray(String.class));
                if(player.con != null){
                    Call.traceInfo(player.con, other, info);
                }else{
                    NetClient.traceInfo(other, info);
                }*/
                MenusKt.showTrace(player, other, perms);
            }
            case switchTeam -> {
                if (!perms.contains(Permission.admin)) {
                    Bundle.sendMessage("noperms", player);
                    return;
                }
                if (params instanceof Team team) {
                    other.team(team);
                    other.sendMessage(Bundle.get("team.changed") + " " + team.coloredName());
                }
            }
        }
    }
}
