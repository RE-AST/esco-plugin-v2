package plugin.discord;

import arc.Core;
import arc.util.CommandHandler;
import mindustry.Vars;
import mindustry.gen.Player;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import arc.struct.Seq;
import arc.util.CommandHandler.Command;

import java.awt.*;
import java.util.Comparator;

import static plugin.PVars.linkCodes;
import static plugin.discord.Bot.reply;
import static plugin.utils.Permission.editMaps;
import static plugin.utils.Permission.getPermsByDiscordId;

import mindustry.gen.Groups;
import net.dv8tion.jda.api.utils.FileUpload;
import plugin.database.models.PlayerData;
import plugin.utils.MapPreview;
import arc.files.Fi;
import arc.util.CommandHandler.CommandRunner;
import mindustry.Vars;
import mindustry.io.SaveIO;
import net.dv8tion.jda.api.entities.Message.Attachment;

public class Commands {
    public static void register(CommandHandler handler) {
        handler.<Context>register("help", "Help command", (a, ctx)->{
            Seq<Command> commandsList = handler.getCommandList().sort(Comparator.comparing(commandx -> commandx.text));
            StringBuilder commands = new StringBuilder();

            for (Command command : commandsList) {
                commands.append(handler.getPrefix()).append(command.text);
                if (!command.paramText.isEmpty()) {
                    commands.append(" ").append(command.paramText);
                }

                commands.append(" - ").append(command.description).append("\n");
            }

            //reply(m, "Available commands:\n"+commands.toString());
            ctx.reply("Available commands:\n"+commands.toString());
        });

        handler.<Context>register("status", "Check server status", (a, ctx)->{
            StringBuilder sb = new StringBuilder();
            EmbedBuilder embed = new EmbedBuilder();

            Groups.player.each((p)->{
                sb.append(p.plainName()).append("\n");
            });

            embed.addField("Map", Vars.state.map.name(), false);
            embed.addField("TPS", String.valueOf(Core.graphics.getFramesPerSecond()), false);
            embed.addField("Wave", String.valueOf(Vars.state.wave), false);
            embed.addField("Players", sb.toString(), true);

            embed.setColor(Color.green);

            embed.setImage("attachment://minimap.png");
            byte[] image = MapPreview.parseTiles(Vars.world.tiles);
            ctx.message.replyEmbeds(embed.build()).addFiles(FileUpload.fromData(image, "minimap.png")).queue();
        });

        handler.<Context>register("link", "<code>", "", (arg, ctx)->{
            if(!linkCodes.containsKey(arg[0])) {
                ctx.reply("Code not found! Are you on server?");
                return;
            }
            Player player = linkCodes.get(arg[0]);
            linkCodes.remove(arg[0]);
            PlayerData.getPlayerData(player).ifPresent(p->{
                if(p.updateDiscordId(ctx.author.getIdLong()))
                    ctx.reply("Success!");
                else
                    ctx.reply("Failed");
            });
        });
    }
}
