package plugin.utils;

import arc.struct.ObjectIntMap;
import arc.util.Strings;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.net.Packets;
import net.dv8tion.jda.api.EmbedBuilder;
import plugin.PVars;

import java.awt.*;
import java.text.MessageFormat;
import java.util.Optional;

import static mindustry.core.NetServer.kickDuration;
import static mindustry.core.NetServer.voteDuration;
import static plugin.Bundle.sendMessage;
import static plugin.PVars.*;
import static plugin.database.models.Ban.ban;
import static plugin.database.models.Ban.getBan;
import static plugin.database.models.PlayerData.getPlayerId;

public class VotekickSession{
    public Player target, started;
    public ObjectIntMap<String> voted = new ObjectIntMap<>();
    public Timer.Task task;
    public int votes, targetId, startedId;
    public String reason;

    public VotekickSession(Player target, Player started, String reason){
        this.target = target;
        this.started = started;
        this.reason = reason;
        this.task = Timer.schedule(() -> {
            if(!checkPass()){
                //Call.sendMessage(Strings.format("[lightgray]Vote failed. Not enough votes to kick[orange] @[lightgray].", target.name));
                sendMessage("votekick.failed", target.coloredName());
                cancel();
            }
        }, voteDuration);
        Optional<Integer> id = getPlayerId(started);
        if(id.isPresent())
            startedId = id.get();
        id = getPlayerId(target);
        if(id.isPresent())
            targetId = id.get();
    }

    public void vote(Player player, int d){
        int lastVote = voted.get(player.uuid(), 0) | voted.get(Vars.netServer.admins.getInfo(player.uuid()).lastIP, 0);
        votes -= lastVote;

        votes += d;
        voted.put(player.uuid(), d);
        voted.put(Vars.netServer.admins.getInfo(player.uuid()).lastIP, d);

        /*Call.sendMessage(Strings.format("[lightgray]@[lightgray] has voted on kicking[orange] @[lightgray].[accent] (@/@)\n[lightgray]Type[orange] /vote <y/n>[] to agree.",
                player.name, target.name, votes, votesRequired()));*/
        sendMessage("votekick.voted", player.coloredName(), target.coloredName(), votes, votesRequired());

        checkPass();
    }

    public boolean checkPass(){
        if(votes >= votesRequired()){
            // Call.sendMessage(Strings.format("[orange]Vote passed.[scarlet] @[orange] will be banned from the server for @ minutes.", target.name, (kickDuration / 60)));
            sendMessage("votekick.passed", target.coloredName(), (kickDuration / 60));
            //Groups.player.each(p -> p.uuid().equals(target.uuid()), p -> p.kick(Packets.KickReason.vote, kickDuration * 1000));
            cancel();

            sendEmbed(startedId, targetId);
            if(ban(targetId, startedId, "Votekick: "+reason, kickDuration)) {
                getBan(target).ifPresent(b->b.kickPlayer(target));
            }

            return true;
        }
        return false;
    }

    public void cancel() {
        task.cancel();
        currentlyKicking = null;
    }

    public void sendEmbed(int stId, int tId) {
        if(votekicksChannel == null) return;

        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(Color.red)
                .addField("Votekick", MessageFormat.format(
                        """
                                Started by: [{0}] {1}
                                Target: [{2}] {3}
                                Votes: {4}
                                Reason {5}
                                Server {6}
                                """,
                        stId, started.plainName(),
                        tId, target.plainName(),
                        votes, reason, PVars.gamemode.simpleName
                ), false);
        votekicksChannel.sendMessageEmbeds(embed.build()).queue();
    }

    public static int votesRequired(){
        return 2 + (Groups.player.size() > 4 ? 1 : 0);
    }
}