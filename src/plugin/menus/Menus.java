package plugin.menus;

import arc.math.Mathf;
import arc.struct.ObjectIntMap;
import arc.struct.Seq;
import arc.util.Timekeeper;
import mindustry.Vars;
import mindustry.content.UnitTypes;
import mindustry.gen.Building;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.net.Administration;
import mindustry.type.UnitType;
import plugin.Bundle;
import plugin.database.models.PlayerStats;
import plugin.utils.Permission;

import java.util.concurrent.atomic.AtomicInteger;

import static plugin.Bundle.label;
import static plugin.Bundle.sendMessage;
import static plugin.PVars.discordLink;
import static plugin.PVars.gamemode;
import static plugin.database.models.Ban.ban;
import static plugin.database.models.PlayerData.getPlayerData;
import static plugin.utils.Gamemode.pvp;
import static plugin.utils.UtilsKt.parseTime;

public class Menus {
    static final ObjectIntMap<UnitType> unitCosts = new ObjectIntMap<>();
    static final String[] slotsSymbols = {
            "\uF82F", // sili
            "\uF82C", // surge alloy
            "\uF838", // copper
            "\uF82D",
            "\uF837" // lead
    };

    static {
        unitCosts.putAll(
                UnitTypes.crawler, 50,
                UnitTypes.dagger, 100,
                UnitTypes.flare, 100,
                UnitTypes.mono, 100,
                UnitTypes.renale, 700,
                UnitTypes.poly, 700,
                UnitTypes.mace, 700,
                UnitTypes.mega, 1550,
                UnitTypes.fortress, 1550
        );
    }

    public static void showWelcome(Player p) {
        new Menu(Bundle.get("menu.welcome.title", p.locale), Bundle.get("menu.welcome.message", p.locale))
                .add(Bundle.get("menus.close"), Menus::empty)
                .add("[blue]\uE80DDiscord",
                        (pl) -> Call.openURI(pl.con, discordLink)
                )
                .add(Bundle.get("menus.dontshow", p.locale), (pl)->{
                    getPlayerData(pl).ifPresent(data->{
                        data.prefs.setShowWelcomeMenu(false);
                        data.updatePrefs();
                    });
                })
                .show(p);
    }

    public static void slot(Player p, PlayerStats stats, int bet){

        if(bet <= 0){
            sendMessage("slots.smollbet", p);
            return;
        }

        if(bet>600) {
            sendMessage("args.lessthan", p, "<bet>", 600);
            return;
        }

        if(stats.balance < bet){
            sendMessage("menu.shop.nomoney", p);
            return;
        }

        showSlot(p, stats, bet);
    }

    private static void showSlot(Player p, PlayerStats stats, int bet) {
        if(bet > stats.balance) {
            sendMessage("menu.shop.nomoney", p);
            return;
        }

        if(stats.lastGambling != null && !stats.lastGambling.get()) {
            p.sendMessage("[scarlet]Not so fast!");
            return;
        }

        String s1 = slotsSymbols[Mathf.random(slotsSymbols.length-1)];
        String s2 = slotsSymbols[Mathf.random(slotsSymbols.length-1)];
        String s3 = slotsSymbols[Mathf.random(slotsSymbols.length-1)];

        float multiplier = 0f;

        if(s1.equals(s2) && s2.equals(s3)){
            switch(s1){
                case "\uF82D": multiplier = 5f; break;
                case "\uF82C": multiplier = 4f; break; // surge
                case "\uF82F": multiplier = 3f; break; // sili
                case "\uF837": multiplier = 2f; break; // lead
                default: multiplier = 1.5f;
            }
        }
        else if(s1.equals(s2) || s2.equals(s3) || s1.equals(s3)){
            multiplier = 1.2f;
        }

        int win = Math.round((bet * multiplier));

        if(stats.lastGambling == null) {
            stats.setLastGambling(new Timekeeper(0.5f));
        } else {
            stats.lastGambling.reset();
        }

        if(win > 0){
            stats.adjBalance(win);
        } else {
            stats.subBalance(bet);
        }

        /*
        if(win>0) {
            sendMessage("slots.win", p.coloredName(), s1, s2, s3, win);
        } else {
            sendMessage("slots.fail", p.coloredName(), s1, s2, s3, bet);
        }
         */

        new Menu("Slot", "Bet: "+bet+"\nBalance: "+stats.balance+"\n" + (win>0 ? Bundle.get("menus.slots.win", p.locale, win) : Bundle.get("menus.slots.fail", p.locale,bet)))
                .add("["+s1+"]")
                .add("["+s2+"]")
                .add("["+s3+"]").row()
                .add("[green]Lets go gambling!", (pl)->showSlot(pl, stats, bet))
                .add("-")
                .add(Bundle.get("menus.close", p.locale))
                .show(p);
    }

    public static void showShop(PlayerStats stats, Player p) {
        Menu menu = new Menu(Bundle.get("menu.shop.title", p.locale), "Balance: [green]$[white]"+stats.balance);

        AtomicInteger i = new AtomicInteger();
        unitCosts.forEach((en)->{
            if(i.get() >= 4) {
                menu.row();
                i.set(0);
            }
            i.addAndGet(1);
            UnitType type = en.key;
            int cost = gamemode == pvp ? en.value * 3 : en.value;

            menu.add(type.emoji()+"\n[green]$[lightgray]"+cost, pl->{
                if(cost > stats.balance) {
                    sendMessage("menu.shop.nomoney", pl);
                    return;
                }
               type.spawn(pl.team(), pl.x, pl.y);
               stats.subBalance(cost);
               //sendMessage("menu.shop.unitbuy", pl.coloredName(), type.emoji(), cost);
                label("menu.shop.unitbuy", 1, pl.x, pl.y, pl.coloredName(), type.emoji(), cost);
            });
        });
        menu.row();
        if(gamemode != pvp)
            menu.add(Bundle.get("menu.shop.healcores", p.locale)+"\n[green]$[lightgray]2500", pl->{
                if(2500 > stats.balance) {
                    sendMessage("menu.shop.nomoney", pl);
                    return;
                }
                // sendMessage("menu.shop.advertise.healcores", pl.coloredName(), pl.team().emoji);
                label("menu.shop.advertise.healcores", 1, pl.x, pl.y, pl.coloredName(), pl.team().emoji);
                pl.team().cores().each(Building::heal);
            });
        menu.add(Bundle.get("menus.close", p.locale), Menus::empty);

        menu.show(p);
    }

    public static void showTrace(Player p, Player other, Seq<Permission> perms) {
        Menu menu = new Menu("Info", "");
        Administration.PlayerInfo stats = Vars.netServer.admins.getInfo(other.uuid());
        menu.add("[green]Name\n"+other.coloredName(), (pl)->{
            Call.infoMessage(pl.con, stats.names.toString("\n"));
        }).row();
        var pdOpt = getPlayerData(other);
        pdOpt.ifPresent(d->{
            menu.add("ID\n"+d.id).row();
        });
        menu.add("Locale\n"+other.locale).row()
                .add("[green]IP\n"+other.ip(), (pl)->{
                    Call.infoMessage(pl.con, stats.ips.toString("\n"));
                }).row()
                .add("Mobile\n"+other.con.mobile).row()
                .add("Custom Client\n"+other.con.modclient).row()
                .add("Times Joined\n"+stats.timesJoined).row()
                .add("Times Kicked\n"+stats.timesKicked).row();
        if(perms.contains(Permission.punish) && pdOpt.isPresent()) {
            menu.add("[scarlet]Ban", (pl2)->{
                showBanMenu(pl2, pdOpt.get().id);
            });
        }
        menu.add("[red]Close")
                .show(p);
    }

    private static void showBanMenu(Player p, int playerId) {
        new TextMenu((pl, reason)->{
            if(reason.isEmpty()) {
                pl.sendMessage("[scarlet]Reason is empty!");
                return;
            }
            new TextMenu((pl2, time)->{
                if(time.isEmpty()) {
                    pl2.sendMessage("[scarlet]Time is empty! 1h 1d 1m 1y perm etc");
                    return;
                }
                long timeL = parseTime(time);
                if(timeL == -1 && !time.contains("perm")) {
                    pl2.sendMessage("[scarlet]Unknown! 1h 1d 1m 1y perm etc");
                    return;
                }
                if(ban(playerId, pl2, reason, timeL)) {
                    pl2.sendMessage("[green]Player banned.");
                } else {
                    pl2.sendMessage("[scarlet]Failed to ban player.");
                }
            }).setTitle("Ban Time").setMessage("Write time here, 1d etc.").show(pl);
        }).setTitle("Reason").setMessage("Write reason here").show(p);
    }

    private static void empty(Player p) {}
}
