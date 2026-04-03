package plugin.discord;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import plugin.utils.Permission;

import java.util.List;

import static plugin.PVars.gamemode;
import static plugin.utils.Permission.getPermsByDiscordId;

public class Context {
    public User author;
    public Message message;
    public MessageChannelUnion channel;
    public List<Message.Attachment> attachments;

    public Context(Message message, MessageChannelUnion channel, User author) {
        this.message = message;
        this.channel = channel;
        this.author = author;
        this.attachments = this.message.getAttachments();
    }

    public boolean hasPerm(Permission perm) {
        boolean has = getPermsByDiscordId(author.getIdLong()).contains(perm);
        if (!has) {
            reply("No access! You need " + perm.name());
        }
        return has;
    }

    public void reply(String content) {
        message.reply(content).queue();
    }

    public void replyServer(String content) {
        message.reply("["+gamemode.simpleName+"] "+content).queue();;
    }

    public void replyEmbed(MessageEmbed embed) {
        message.reply(MessageCreateData.fromEmbeds(embed)).queue();
    }
}
