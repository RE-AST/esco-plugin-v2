package plugin.votes;

import arc.Events;
import arc.struct.ObjectIntMap;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.maps.Map;

import static plugin.Bundle.sendMessage;
import static plugin.PVars.mapVote;

public class VoteMap {
    public Player initiator;
    public ObjectIntMap<String> voted = new ObjectIntMap<>();
    public Timer.Task task;
    public int votes;
    public Map map;

    /*public VoteMap(Player init) {
        this.initiator = init;
        this.task = Timer.schedule(() -> {
            if (!checkPass()) {
                sendMessage("rtv.failed", votes, votesRequired());
                cancel();
            }
        }, 2 * 60);
    }*/

    public VoteMap(Player init, Map map) {
        this.initiator = init;
        this.map = map;
        this.task = Timer.schedule(() -> {
            if (!checkPass()) {
                sendMessage("rtv.failed", votes, votesRequired());
                cancel();
            }
        }, 2 * 60);
    }

    public void vote(Player player, int d) {
        /*int lastVote = voted.get(player.uuid(), 0) | voted.get(Vars.netServer.admins.getInfo(player.uuid()).lastIP, 0);
        votes -= lastVote;*/

        votes += d;
        //voted.put(player.uuid(), d);
        voted.put(Vars.netServer.admins.getInfo(player.uuid()).lastIP, d);
        if (map == null) {
            if (d == 1)
                sendMessage("rtv.votey", player.coloredName(), votes, votesRequired());
            else
                sendMessage("rtv.voten", player.coloredName(), votes, votesRequired());
        } else {
            if (d == 1)
                sendMessage("rtv.voteymap", player.coloredName(), votes, votesRequired(), map.name());
            else
                sendMessage("rtv.votenmap", player.coloredName(), votes, votesRequired(), map.name());
        }
        checkPass();
    }

    public boolean checkPass() {
        if (votes >= votesRequired()) {
            if (map != null)
                Vars.maps.setNextMapOverride(map);
            Events.fire(new EventType.GameOverEvent(Team.derelict));
            sendMessage("rtv.pass");

            cancel();
            return true;
        }
        return false;
    }

    public void cancel() {
        mapVote = null;
        this.task.cancel();
    }

    public int votesRequired() {
        return Math.max(1, (int) Math.round(Groups.player.size() * 0.75));
    }
}
