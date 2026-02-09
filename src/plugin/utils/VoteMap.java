package plugin.utils;

import arc.Events;
import arc.scene.style.ScaledNinePatchDrawable;
import arc.struct.ObjectIntMap;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.gen.Player;

import static plugin.Bundle.sendMessage;
import static plugin.PVars.*;

public class VoteMap {
    public Player initiator;
    public ObjectIntMap<String> voted = new ObjectIntMap<>();
    public Timer.Task task;
    public int votes;

    public VoteMap(Player init) {
        this.initiator = init;
        this.task = Timer.schedule(()->{
            if(!checkPass()) {
                sendMessage("rtv.failed", votes, votesRequired());
                cancel();
            }
        }, 2*60);
    }

    public void vote(Player player, int d){
        /*int lastVote = voted.get(player.uuid(), 0) | voted.get(Vars.netServer.admins.getInfo(player.uuid()).lastIP, 0);
        votes -= lastVote;*/

        votes += d;
        voted.put(player.uuid(), d);
        voted.put(Vars.netServer.admins.getInfo(player.uuid()).lastIP, d);
        if(d == 1)
            sendMessage("rtv.votey", player.coloredName(), votes, votesRequired());
        else
            sendMessage("rtv.voten", player.coloredName(), votes, votesRequired());
        checkPass();
    }

    public boolean checkPass() {
        if(votes >= votesRequired()) {
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
