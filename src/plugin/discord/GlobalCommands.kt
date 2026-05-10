package plugin.discord

import mindustry.gen.Groups
import arc.util.Timer
import net.dv8tion.jda.api.entities.emoji.Emoji
import arc.util.CommandHandler
import arc.util.Log
import mindustry.Vars
import plugin.Bundle
import plugin.KVars.buildsBaseUrl
import plugin.KVars.buildsLatestTxtUrl
import plugin.PVars.gamemode
import plugin.PVars.globalExecutor
import plugin.PVars.version
import plugin.PVars
import plugin.utils.*
import plugin.utils.download
import plugin.utils.httpGetString
import java.nio.file.Files
import java.nio.file.StandardCopyOption

fun registerGlobal(handler: CommandHandler) {
    handler.register("ver", "SS") { _: Array<String>, ctx: Context ->
        globalExecutor.submit {
            ctx.replyServer("Current: ${version}  Latest: ${httpGetString(buildsLatestTxtUrl)}")
        }
    }

    handler.register("ver", "SS") { _: Array<String>, _: Context ->
        System.gc()
    }

    handler.register("update", "ss") { _: Array<String>, ctx: Context ->
        globalExecutor.submit {
            try {
                val ver = httpGetString(buildsLatestTxtUrl)
                if(version.equals(ver)) {
                    ctx.replyServer("No new updates! But ok.")
                }
                val modFi = Vars.mods.getMod("plugin").file
                val tmpFi = modFi.parent().child(modFi.name() + ".part")
                download(
                    "$buildsBaseUrl/$ver/plugin.jar",
                    tmpFi.file().toPath()
                )
                modFi.delete()
                Files.move(
                    tmpFi.file().toPath(),
                    modFi.file().toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
                ctx.replyServer("Successful!\n$version -> $ver")
            } catch (e: Exception) {
                Log.err("Discord update failed", e)
                ctx.replyServer("ohno ${e.message}")
            }
        }
    }
    handler.register("restart", "SS") { _: Array<String>, ctx: Context ->
        sendLog("Restart scheduled by <@" + ctx.message.author.id + ">")
        ctx.message.addReaction(Emoji.fromUnicode("✅")).queue()
        if (Groups.player.isEmpty) {
            Timer.schedule({ Loader.exit() }, 1f)
        }
        PVars.needRestart = true
    }

    handler.register("reloadbundle", "SS") { _: Array<String>, ctx: Context ->
        Bundle.load()
        ctx.replyServer("IDK, reloaded probably")
    }
}
