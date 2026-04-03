package plugin;

import arc.struct.IntMap;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.CommandHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import mindustry.gen.Player;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import plugin.commands.CustomHandler;
import plugin.database.models.Log;
import plugin.utils.Gamemode;
import plugin.votes.VoteMap;
import plugin.votes.VoteWave;
import plugin.votes.VotekickSession;

import java.net.http.HttpClient;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PVars {
    public static int serverId;
    public static String version = "unknown";
    public static Gamemode gamemode = Gamemode.idk;
    public static String bundleApi, vpnApi, apiAuth, lokiApi;
    public static boolean lokiLoggingEnabled;

    public static final String discordLink = "https://discord.gg/KfusjwYFDx";

    public static String botToken, serverGuildStr, serverChannelStr, logsChannelStr, votekicksChannelStr, roundsChannelStr, parrotChannelStr;
    public static Guild serverGuild;
    public static TextChannel serverChannel, logsChannel, votekicksChannel, roundsChannel, parrotChannel;

    public static String dbHost, dbPort, dbPassword, dbUser, db;

    public static VotekickSession currentlyKicking;
    public static VoteMap mapVote;
    public static VoteWave waveVote;

    public static CustomHandler clientCommands;
    public static CommandHandler discordCommands, globalCommands;

    public static Random random = new Random();

    public static ObjectMap<String, Player> linkCodes = new ObjectMap<>();
    public static Seq<Player> historyPlayers = new Seq<>(), vanishedPlayers = new Seq<>();

    public static boolean needRestart = false;

    public static Seq<Log> logsBuffer = new Seq<>();

    public static IntMap<String> SSUsers = new IntMap<>(8);

    public static final ObjectMapper objectMapper = new ObjectMapper();

    public static final ExecutorService globalExecutor = Executors.newSingleThreadExecutor();

    public static HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    //public static long startTime = Time.millis();
}
