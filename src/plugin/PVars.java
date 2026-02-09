package plugin;

import arc.struct.IntMap;
import arc.struct.ObjectMap;
import arc.struct.StringMap;
import arc.util.CommandHandler;
import mindustry.gen.Player;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import plugin.commands.CustomHandler;
import plugin.utils.Gamemode;
import plugin.utils.VoteMap;
import plugin.utils.VotekickSession;

import arc.struct.Seq;
import plugin.database.models.Log;

public class PVars {
    public static int serverId;
    public static Gamemode gamemode = Gamemode.idk;
    public static String bundleApi, vpnApi;

    public static final String discordLink = "https://discord.gg/KfusjwYFDx";

    public static String botToken, serverGuildStr, serverChannelStr, logsChannelStr, votekicksChannelStr;
    public static Guild serverGuild;
    public static TextChannel serverChannel, logsChannel, votekicksChannel;

    public static String dbHost, dbPort, dbPassword, dbUser, db;

    public static VotekickSession currentlyKicking;
    public static VoteMap mapVote;

    public static CustomHandler clientCommands;
    public static CommandHandler discordCommands;

    public static ObjectMap<String, Player> linkCodes = new ObjectMap<>();

    public static boolean needRestart = false;

    public static Seq<Log> logsBuffer = new Seq<>();

    public static IntMap<String> SSUsers = new IntMap<>(8);
}
