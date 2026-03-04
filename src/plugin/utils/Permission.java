package plugin.utils;

import arc.struct.ObjectMap;
import arc.struct.Seq;
import mindustry.gen.Player;
import plugin.database.Database;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public enum Permission {
    none,
    test,
    admin,
    punish,
    editMaps,
    votekickImmune,
    editServer,
    vanish;

    public static final ObjectMap<Player, Seq<Permission>> cache = new ObjectMap<>();
    // public static final Permission[] all = values();

    public static Seq<Permission> parsePerms(String[] perms) {
        Seq<Permission> rperms = new Seq<>();
        for(String pname :perms)
            rperms.add(parsePerm(pname));
        return rperms;
    }

    public static Permission parsePerm(String name) {
        for(Permission p : values())
            if(p.name().contains(name)) return p;
        return none;
    }

    public static Seq<Permission> getPerms(Player p) {
        if(cache.containsKey(p))
            return cache.get(p);

        Optional<Seq<Permission>> r = Database.executeQueryAsync(
                "SELECT ar.permissions\n" +
                "        FROM players p\n" +
                "        LEFT JOIN admins a ON a.player_id = p.id\n" +
                "        LEFT JOIN admin_ranks ar ON ar.id = a.rank_id\n" +
                "        WHERE p.uuid = ?",
                stmt->stmt.setString(1, p.uuid()),
                Permission::getPerms
        );

        Seq<Permission> seq = r.orElseGet(() -> Seq.with(none));
        if(!seq.contains(none))
            seq.add(none);
        cache.put(p, seq);

        return seq;
    }

    public static Seq<Permission> getPermsByDiscordId(Long discordId) {
        Optional<Seq<Permission>> r = Database.executeQueryAsync(
                "SELECT ar.permissions\n" +
                        "        FROM players p\n" +
                        "        LEFT JOIN admins a ON a.player_id = p.id\n" +
                        "        LEFT JOIN admin_ranks ar ON ar.id = a.rank_id\n" +
                        "        WHERE p.discord_id = ?",
                stmt->stmt.setLong(1, discordId),
                Permission::getPerms
        );

        Seq<Permission> seq = r.orElseGet(() -> Seq.with(none));
        if(!seq.contains(none))
            seq.add(none);

        return seq;
    }

    public static Seq<Permission> getPerms(ResultSet rs) throws SQLException {
        Array sqlArray = rs.getArray("permissions");
        String[] perms;

        if(sqlArray != null) {
            perms = (String[]) sqlArray.getArray();
        } else {
            perms = new String[]{"none"};
        }

        return Permission.parsePerms(perms);
    }

    public static String seqToString(Seq<Permission> s) {
        StringBuilder sb = new StringBuilder();
        s.each(p->sb.append(p.name()+" "));
        return sb.toString();
    }
}
