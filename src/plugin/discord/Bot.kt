package plugin.discord

import arc.util.CommandHandler
import arc.util.Log
import mindustry.gen.Player
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.requests.GatewayIntent
import plugin.PVars
import java.awt.Color
import java.util.*

fun load() {
    val intents = EnumSet.allOf(GatewayIntent::class.java)
    try {
        val jda = JDABuilder.create(PVars.botToken, intents)
            .addEventListeners(MessageListener())
            .build()

        jda.awaitReady()

        PVars.serverGuild = jda.getGuildById(PVars.serverGuildStr)
        if (PVars.serverGuild != null) {
            PVars.serverChannel =
                PVars.serverGuild.getChannelById(TextChannel::class.java, PVars.serverChannelStr)
            PVars.logsChannel =
                PVars.serverGuild.getChannelById(TextChannel::class.java, PVars.logsChannelStr)
            PVars.votekicksChannel =
                PVars.serverGuild.getChannelById(TextChannel::class.java, PVars.votekicksChannelStr)
            PVars.roundsChannel =
                PVars.serverGuild.getChannelById(TextChannel::class.java, PVars.roundsChannelStr)
            PVars.parrotChannel =
                PVars.serverGuild.getChannelById(TextChannel::class.java, PVars.parrotChannelStr)
        } else {
            Log.err("Failed to get server guild!")
        }

        PVars.discordCommands = CommandHandler(PVars.gamemode.botPrefix)
        PVars.globalCommands = CommandHandler("gc.")
        register(PVars.discordCommands)
        registerGlobal(PVars.globalCommands)

        Log.info("Bot loaded")
    } catch (e: Exception) {
        Log.err("Failed to load discord bot!", e)
    }
}

fun sendLog(message: String?) {
    if (PVars.logsChannel != null) PVars.logsChannel.sendMessage("[" + PVars.gamemode.simpleName + "] " + message)
        .queue()
}

fun sendServerMessage(message: String) {
    if (PVars.serverChannel == null) return
    PVars.serverChannel.sendMessage(message).queue()
}

fun sendParrotMessage(message: String) {
    if (PVars.parrotChannel == null) return
    PVars.parrotChannel.sendMessage(message).queue()
}

fun sendRoundMessage(message: String?) {
    if (PVars.roundsChannel == null) return
    PVars.roundsChannel.sendMessage("[" + PVars.gamemode.simpleName + "] " + message).queue()
}

fun sendJoinMessage(player: Player, id: Int) {
    if (PVars.serverChannel == null) return
    val embed = EmbedBuilder()
        .setColor(Color.green)
        .addField("", "[" + id + "] " + player.plainName() + " joined!", false)
    PVars.serverChannel.sendMessageEmbeds(embed.build()).queue()
}

fun sendLeaveMessage(player: Player, id: Int) {
    if (PVars.serverChannel == null) return
    val embed = EmbedBuilder()
        .setColor(Color.red)
        .addField("", "[" + id + "] " + player.plainName() + " left!", false)
    PVars.serverChannel.sendMessageEmbeds(embed.build()).queue()
}

fun reply(message: Message, content: String) {
    message.reply(content).queue()
}