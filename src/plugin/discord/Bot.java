package plugin.discord;


import arc.util.CommandHandler;
import arc.util.Log;
import mindustry.Vars;
import mindustry.gen.Player;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import plugin.PVars;

import java.awt.*;
import java.util.EnumSet;

import static plugin.PVars.*;

public class Bot {
    public static void load() {
        EnumSet<GatewayIntent> intents = EnumSet.allOf(GatewayIntent.class);
        try {
            JDA jda = JDABuilder.create(botToken, intents)
                    .addEventListeners(new MessageListener())
                    .build();

            jda.awaitReady();

            serverGuild = jda.getGuildById(serverGuildStr);
            if(serverGuild != null) {
                serverChannel = serverGuild.getChannelById(TextChannel.class, serverChannelStr);
                logsChannel = serverGuild.getChannelById(TextChannel.class, logsChannelStr);
                votekicksChannel = serverGuild.getChannelById(TextChannel.class, votekicksChannelStr);
            } else {
                Log.err("Failed to get server guild!");
            }

            discordCommands = new CommandHandler(gamemode.botPrefix);
            Commands.register(discordCommands);

            Log.info("Bot loaded");
        } catch (Exception e) {
            Log.err("Failed to load discord bot!", e);
        }
    }

    public static void sendLog(String message) {
        if(logsChannel != null)
            logsChannel.sendMessage("["+gamemode.simpleName+"] "+message).queue();
    }

    public static void sendServerMessage(String message) {
        if(serverChannel == null) return;
        serverChannel.sendMessage(message).queue();
    }

    public static void sendJoinMessage(Player player) {
        if(serverChannel == null) return;
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(Color.green)
                .addField("", player.plainName() + " joined!", false);
        serverChannel.sendMessageEmbeds(embed.build()).queue();
    }

    public static void sendLeaveMessage(Player player) {
        if(serverChannel == null) return;
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(Color.red)
                .addField("", player.plainName() + " left!", false);
        serverChannel.sendMessageEmbeds(embed.build()).queue();
    }

    public static void reply(Message message, String content) {
        message.reply(content).queue();
    }
}
