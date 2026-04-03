package plugin.discord

import arc.Core
import arc.func.Cons
import arc.math.Mathf
import arc.struct.Seq
import arc.util.CommandHandler
import arc.util.Log
import arc.util.Strings
import arc.util.Timer
import mindustry.Vars
import mindustry.gen.Groups
import mindustry.gen.Player
import mindustry.maps.Map
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Message.Attachment
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.utils.FileUpload
import plugin.PVars
import plugin.database.Database
import plugin.database.getPlayerData
import plugin.database.models.PlayerData
import plugin.utils.Loader
import plugin.utils.MapPreview
import plugin.utils.Permission
import java.awt.Color
import java.io.File
import java.util.function.Consumer
import java.util.function.Function
import kotlin.math.min

fun register(handler: CommandHandler) {
    handler.register("help", "What a dog doing?") { _: Array<String>, ctx: Context ->
        val commandsList = handler.commandList.sort(
            Comparator.comparing(
                Function { commandx: CommandHandler.Command -> commandx.text })
        )
        val commands = StringBuilder()

        for (command in commandsList) {
            commands.append(handler.getPrefix()).append(command.text)
            if (!command.paramText.isEmpty()) {
                commands.append(" ").append(command.paramText)
            }

            commands.append(" - ").append(command.description).append("\n")
        }

        //reply(m, "Available commands:\n"+commands.toString());
        ctx.reply("Available commands:\n$commands")
    }

    handler.register("status", "Check server status") { _: Array<String>, ctx: Context ->
        val sb = StringBuilder()
        val embed = EmbedBuilder()

        Groups.player.each { p: Player ->
            getPlayerData(p).ifPresent(Consumer { pd: PlayerData ->
                sb.append("[").append(pd.id).append("] ")
            })
            sb.append(p.plainName()).append("\n")
        }

        embed.addField("Map", Vars.state.map.name(), false)
        embed.addField("TPS", Core.graphics.framesPerSecond.toString(), false)
        embed.addField("Wave", Vars.state.wave.toString(), false)
        embed.addField("Players", sb.toString(), true)

        embed.setColor(Color.green)

        embed.setImage("attachment://minimap.png")
        val image = MapPreview.parseTiles(Vars.world.tiles)
        ctx.message.replyEmbeds(embed.build()).addFiles(FileUpload.fromData(image, "minimap.png")).queue()
    }

    handler.register("link", "<code>", "") { arg: Array<String>, ctx: Context ->
        if (arg[0].length > 15) return@register
        if (!PVars.linkCodes.containsKey(arg[0])) {
            ctx.reply("Code not found! Are you on server?")
            return@register
        }
        val player = PVars.linkCodes.get(arg[0])
        PVars.linkCodes.remove(arg[0])
        getPlayerData(player).ifPresent(Consumer { p: PlayerData ->
            if (p.updateDiscordId(ctx.author.idLong)) ctx.reply("Success!")
            else ctx.reply("Failed")
        })
    }

    handler.register("maps", "[page]", "") { arg: Array<String>, ctx: Context ->
        val mapsList = Vars.maps.customMaps()
        if (mapsList.isEmpty) {
            ctx.reply("No custom maps on server")
            return@register
        }
        if (arg.isNotEmpty() && !Strings.canParseInt(arg[0])) {
            ctx.reply("Page must be int!")
            return@register
        }
        var page = if (arg.isNotEmpty()) Strings.parseInt(arg[0]) else 1
        val pages = Mathf.ceil(mapsList.size / 16.0f)
        page--
        if (page in 0..<pages) {
            val maps = StringBuilder()

            for (i in 16 * page..<min(16 * (page + 1), mapsList.size)) {
                val map = mapsList.get(i)
                maps.append(Strings.stripColors(map.name())).append("\n")
            }

            ctx.replyEmbed(
                EmbedBuilder()
                    .setColor(Color.green)
                    .setTitle(Strings.format("Maps: @. Page @/@", mapsList.size, page + 1, pages))
                    .addField("", maps.toString(), false)
                    .build()
            )
        } else {
            ctx.reply("Unknown page. Avail. pages 1-$pages")
        }
    }

    handler.register<Context>("map", "<name...>", "") { args: Array<String>, ctx: Context ->
        val mapsList = Vars.maps.customMaps()
        if (mapsList.isEmpty) {
            ctx.reply("No custom maps on server")
            return@register
        }
        val map = mapsList.find { m: Map -> m.name().contains(args[0]) }
        if (map == null) {
            ctx.reply("No map found")
            return@register
        }
        val embed = EmbedBuilder()
        embed.setColor(Color.magenta)
            .setTitle(map.name())
            .setFooter(map.width.toString() + "x" + map.height)
            .setAuthor(map.author())

        // .setDescription(map.description())
        //.setImage("attachment://minimap.png");
        val msg = ctx.channel.sendMessageEmbeds(embed.build())
        msg.addFiles(FileUpload.fromData(map.file.file(), map.file.name()))
        //msg.addFiles(FileUpload.fromData(parseMap(map), "minimap.png"))
        msg.queue()
    }

    handler.register<Context>("delmap", "<name...>", "") { args: Array<String>, ctx: Context ->
        if (!ctx.hasPerm(Permission.editMaps)) return@register
        val mapsList = Vars.maps.customMaps()
        if (mapsList.isEmpty) {
            ctx.reply("No custom maps on server")
            return@register
        }
        val map = mapsList.find { m: Map -> m.name().contains(args[0]) }
        if (map == null) {
            ctx.reply("No map found")
            return@register
        }
        val embed = EmbedBuilder()
        embed.setColor(Color.red)
            .setTitle("Deleted " + map.name())
            .setFooter(map.width.toString() + "x" + map.height)
            .setAuthor(map.author())

        // .setDescription(map.description())
        //.setImage("attachment://minimap.png");
        val msg = ctx.channel.sendMessageEmbeds(embed.build())
        msg.addFiles(FileUpload.fromData(map.file.file(), map.file.name()))
        //msg.addFiles(FileUpload.fromData(parseMap(map), "minimap.png"))
        map.file.delete()
        Vars.maps.reload()
        msg.queue()
    }

    handler.register("uploadmap", "") { _: Array<String>, ctx: Context ->
        if (!ctx.hasPerm(Permission.editMaps)) return@register
        val msg = ctx.message
        val attachments = Seq<Attachment?>()
        msg.attachments.forEach(Consumer { a: Attachment ->
            if (a.fileExtension == Vars.mapExtension) attachments.add(a)
        })
        if (attachments.size > 4) {
            ctx.reply("Too many maps!")
            return@register
        }
        attachments.each(Cons { map: Attachment ->
            val file = Vars.customMapDirectory.child(map.fileName)
            if (file.exists()) {
                ctx.reply("File " + file.name() + " already exists!")
                return@Cons
            }
            map.proxy.downloadToFile(file.file()).thenAccept(Consumer { _: File? ->
                ctx.reply("File " + file.name() + " downloaded!")
                Vars.maps.reload()
            }).exceptionally(Function { t: Throwable? ->
                Log.err("Failed to download map!", t)
                null
            })
        })
    }

    handler.register("restart", "SS") { _: Array<String>, ctx: Context ->
        if (!ctx.hasPerm(Permission.editServer)) return@register
        sendLog("Restart scheduled by <@" + ctx.message.author.id + ">")
        ctx.message.addReaction(Emoji.fromUnicode("✅")).queue()
        if (Groups.player.isEmpty) {
            Timer.schedule({ Loader.exit() }, 1f)
        }
        PVars.needRestart = true
    }
    handler.register<Context>("debug", "SS") { _: Array<String>, ctx: Context ->
        if (!ctx.hasPerm(Permission.editServer)) return@register
        val pool = Database.dataSource.hikariPoolMXBean
        ctx.reply("Restart: ${PVars.needRestart}\nFPS: ${Core.graphics.framesPerSecond}\nHeap: ${Core.app.javaHeap / 1024 / 1024}\nVersion: ${Core.app.version}\n\nDatabase\nTotal: ${pool.totalConnections}\nActive: ${pool.activeConnections}\nIdle: ${pool.idleConnections}")
    }

    handler.register("update", "<ver>", "SS") { _: Array<String>, ctx: Context ->
        if (!ctx.hasPerm(
                Permission.editServer
            )
        ) return@register
    }
}
