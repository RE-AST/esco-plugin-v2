package plugin.discord.listeners;

import arc.util.CommandHandler.CommandResponse;
import arc.util.CommandHandler.ResponseType;
import arc.util.Log;
import mindustry.gen.Call;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import plugin.PVars;
import plugin.discord.Context;
import plugin.utils.Permission;

import java.text.MessageFormat;

import static plugin.PVars.*;
import static plugin.discord.BotKt.reply;

public class MessageListener extends ListenerAdapter {
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.isFromGuild())
            return;
        User author = event.getAuthor();
        Message message = event.getMessage();
        if (author.isBot() || author.isSystem() || message.isWebhookMessage())
            return;
        Member member = event.getMember();

        MessageChannelUnion channel = event.getChannel();
        String content = message.getContentDisplay();

        if (member != null && channel.getId().equals(PVars.serverChannelStr) && !content.startsWith(gamemode.botPrefix)) {
            String username = member.getEffectiveName();
            Log.info("@: @", username, content);
            String colorHex = new arc.graphics.Color(member.getColors().getPrimaryRaw()).toString();
            String mindustryMessage = MessageFormat.format(
                    "[blue]\uE80D[tan][[[#{0}]{1}[tan]][white]: {2}",
                    colorHex,
                    username,
                    content
            );
            Call.sendMessage(mindustryMessage);
        }
        if (discordCommands != null) {
            CommandResponse response = discordCommands.handleMessage(content, new Context(message, channel, author));
            if (response.type == ResponseType.fewArguments) {
                reply(message, MessageFormat.format("Too few arguments!\nUsage **{0}{1}** {2}", gamemode.botPrefix, response.command.text, response.command.paramText));
            } else if (response.type == ResponseType.manyArguments) {
                reply(message, MessageFormat.format("Too many arguments!\nUsage **{0}{1}** {2}", gamemode.botPrefix, response.command.text, response.command.paramText));
            }
        }
        if(globalCommands != null && content.startsWith("gc!")) {
            Context ctx = new Context(message, channel, author);
            if(!ctx.hasPerm(Permission.editServer)) return;
            globalCommands.handleMessage(content, ctx);
        }
    }
}
