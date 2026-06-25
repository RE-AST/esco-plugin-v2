package plugin.discord.listeners

import arc.graphics.Color
import arc.util.CommandHandler
import arc.util.Log
import mindustry.gen.Call
import mindustry.server.ServerControl
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import plugin.PVars
import plugin.discord.Context
import plugin.discord.reply
import plugin.utils.Permission
import plugin.utils.hasRole
import java.text.MessageFormat

class MessageListener : ListenerAdapter() {
    override fun onMessageReceived(event: MessageReceivedEvent) {
        if (!event.isFromGuild) return
        val author = event.author
        val message = event.message
        if (author.isBot || author.isSystem || message.isWebhookMessage) return
        val member = event.member

        val channel = event.getChannel()
        val content = message.contentDisplay

        if(member != null) {
            if (channel.id == PVars.consoleChannelStr && member.hasRole(PVars.ownerRoleId)) {
                ServerControl.instance.handleCommandString(content)
            }

            if (channel.id == PVars.serverChannelStr && !content.startsWith(PVars.gamemode.botPrefix)) {
                val username = member.effectiveName
                Log.info("@: @", username, content)
                val colorHex: String? = Color(member.colors.primaryRaw).toString()
                val mindustryMessage = MessageFormat.format(
                    "[blue]\uE80D[tan][[[#{0}]{1}[tan]][white]: {2}",
                    colorHex,
                    username,
                    content
                )
                Call.sendMessage(mindustryMessage)
            }
        }

        if (PVars.discordCommands != null) {
            val response = PVars.discordCommands.handleMessage(content, Context(message, channel, author, false))
            if (response.type == CommandHandler.ResponseType.fewArguments) {
                reply(
                    message,
                    MessageFormat.format(
                        "Too few arguments!\nUsage **{0}{1}** {2}",
                        PVars.gamemode.botPrefix,
                        response.command.text,
                        response.command.paramText
                    )
                )
            } else if (response.type == CommandHandler.ResponseType.manyArguments) {
                reply(
                    message,
                    MessageFormat.format(
                        "Too many arguments!\nUsage **{0}{1}** {2}",
                        PVars.gamemode.botPrefix,
                        response.command.text,
                        response.command.paramText
                    )
                )
            }
        }
        if (PVars.globalCommands != null && content.startsWith("gc!")) {
            val ctx = Context(message, channel, author, true)
            if (!ctx.hasPerm(Permission.editServer)) return
            PVars.globalCommands.handleMessage(content, ctx)
        }
    }
}