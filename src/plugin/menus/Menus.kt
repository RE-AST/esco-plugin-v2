package plugin.menus

import arc.func.Cons
import arc.func.Cons2
import arc.math.Mathf
import arc.struct.ObjectIntMap
import arc.struct.Seq
import arc.util.Timekeeper
import mindustry.Vars
import mindustry.content.UnitTypes
import mindustry.gen.Call
import mindustry.gen.Player
import mindustry.type.UnitType
import mindustry.world.blocks.storage.CoreBlock.CoreBuild
import plugin.Bundle
import plugin.PVars
import plugin.database.ban
import plugin.database.deepSearchNames
import plugin.database.getPlayerData
import plugin.database.models.PlayerData
import plugin.database.models.PlayerStats
import plugin.utils.Gamemode
import plugin.utils.Permission
import plugin.utils.parseTime
import java.text.MessageFormat
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import kotlin.math.roundToInt

val unitCosts = ObjectIntMap<UnitType>()
val slotsSymbols = arrayOf(
    "\uF82F",  // sili
    "\uF82C",  // surge alloy
    "\uF838",  // copper
    "\uF82D",
    "\uF837" // lead
)

fun loadMenus() {
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
    )
}

fun showShop(stats: PlayerStats, p: Player) {
    val menu = Menu(Bundle.get("menu.shop.title", p.locale), "Balance: [green]$[white]" + stats.balance)

    val i = AtomicInteger()
    unitCosts.forEach(Consumer { en: ObjectIntMap.Entry<UnitType> ->
        if (i.get() >= 4) {
            menu.row()
            i.set(0)
        }
        i.addAndGet(1)
        val type = en.key
        val cost = if (PVars.gamemode == Gamemode.pvp) en.value * 3 else en.value
        menu.add(type.emoji() + "\n[green]$[lightgray]" + cost) { pl: Player ->
            if (cost > stats.balance) {
                Bundle.sendMessage("menu.shop.nomoney", pl)
                return@add
            }
            type.spawn(pl.team(), pl.x, pl.y)
            stats.subBalance(cost)
            //sendMessage("menu.shop.unitbuy", pl.coloredName(), type.emoji(), cost);
            Bundle.label("menu.shop.unitbuy", 1f, pl.x, pl.y, pl.coloredName(), type.emoji(), cost)
        }
    })
    menu.row()
    if (PVars.gamemode != Gamemode.pvp) menu.add(
        Bundle.get(
            "menu.shop.healcores",
            p.locale
        ) + "\n[green]$[lightgray]2500"
    ) { pl: Player ->
        if (2500 > stats.balance) {
            Bundle.sendMessage("menu.shop.nomoney", pl)
            return@add
        }
        // sendMessage("menu.shop.advertise.healcores", pl.coloredName(), pl.team().emoji);
        Bundle.label("menu.shop.advertise.healcores", 1f, pl.x, pl.y, pl.coloredName(), pl.team().emoji)
        pl.team().cores().each(Cons { obj: CoreBuild -> obj.heal() })
    }
    menu.add(Bundle.get("menus.close", p.locale))

    menu.show(p)
}

fun slot(p: Player, stats: PlayerStats, bet: Int) {
    if (bet <= 0) {
        Bundle.sendMessage("slots.smollbet", p)
        return
    }

    if (bet > 600) {
        Bundle.sendMessage("args.lessthan", p, "<bet>", 600)
        return
    }

    if (stats.balance < bet) {
        Bundle.sendMessage("menu.shop.nomoney", p)
        return
    }

    showSlot(p, stats, bet)
}

fun showSlot(p: Player, stats: PlayerStats, bet: Int) {
    if (bet > stats.balance) {
        Bundle.sendMessage("menu.shop.nomoney", p)
        return
    }

    if (stats.balance > 30000) {
        p.sendMessage("[scarlet]You have too much money")
        return
    }

    if (stats.lastGambling != null && !stats.lastGambling.get()) {
        p.sendMessage("[scarlet]Not so fast!")
        return
    }

    val s1 = slotsSymbols[Mathf.random(slotsSymbols.size - 1)]
    val s2 = slotsSymbols[Mathf.random(slotsSymbols.size - 1)]
    val s3 = slotsSymbols[Mathf.random(slotsSymbols.size - 1)]

    var multiplier = 0f

    if (s1 == s2 && s2 == s3) {
        multiplier = when (s1) {
            "\uF82D" -> 5f
            "\uF82C" -> 4f
            "\uF82F" -> 3f
            "\uF837" -> 2f
            else -> 1.5f
        }
    } else if (s1 == s2 || s2 == s3 || s1 == s3) {
        multiplier = 1.2f
    }

    val win = (bet * multiplier).roundToInt()

    if (stats.lastGambling == null) {
        stats.setLastGambling(Timekeeper.ofSeconds(0.5f))
    } else {
        stats.lastGambling.reset()
    }

    if (win > 0) {
        stats.adjBalance(win)
    } else {
        stats.subBalance(bet)
    }

    Menu(
        "Slot",
        "Bet: " + bet + "\nBalance: " + stats.balance + "\n" + (if (win > 0) Bundle.get(
            "menus.slots.win",
            p.locale,
            win
        ) else Bundle.get("menus.slots.fail", p.locale, bet))
    )
        .add("[$s1]")
        .add("[$s2]")
        .add("[$s3]").row()
        .add("[green]Lets go gambling!") { pl: Player -> showSlot(pl, stats, bet) }
        .add("-")
        .add(Bundle.get("menus.close", p.locale))
        .show(p)
}

fun showWelcome(p: Player) {
    Menu(Bundle.get("menu.welcome.title", p.locale), Bundle.get("menu.welcome.message", p.locale))
        .add(Bundle.get("menus.close"))
        .add(
            "[blue]\uE80DDiscord"
        ) { pl: Player -> Call.openURI(pl.con, PVars.discordLink) }
        .add(Bundle.get("menus.dontshow", p.locale)) { pl: Player ->
            getPlayerData(pl).ifPresent(Consumer { data: PlayerData ->
                data.prefs.setShowWelcomeMenu(false)
                data.updatePrefs()
            })
        }
        .show(p)
}

fun showTrace(p: Player, other: Player, perms: Seq<Permission?>) {
    val menu = Menu("Info", "")
    val stats = Vars.netServer.admins.getInfo(other.uuid())
    menu.add("[green]Name\n" + other.coloredName()) { pl: Player ->
        Call.infoMessage(pl.con, stats.names.toString("\n"))
    }.row()
    val pdOpt: Optional<PlayerData> = getPlayerData(other)
    pdOpt.ifPresent(Consumer { d: PlayerData? ->
        menu.add("ID\n" + d!!.id).row()
    })
    menu.add("Locale\n" + other.locale).row()
        .add("[green]IP\n" + other.ip()) { pl: Player ->
            Call.infoMessage(pl.con, stats.ips.toString("\n"))
        }.row()
        .add("Mobile\n" + other.con.mobile).row()
        .add("Custom Client\n" + other.con.modclient).row()
        .add("Times Joined\n" + stats.timesJoined).row()
        .add("Times Kicked\n" + stats.timesKicked).row()
    if (perms.contains(Permission.punish) && pdOpt.isPresent) {
        menu.add("[scarlet]Ban") { pl2: Player? ->
            showBanMenu(pl2!!, pdOpt.get().id, other)
        }.row()
        menu.add("[yellow]Mute") { pl2: Player ->
            pl2.sendMessage("WIP")
        }.row()
    }
    menu.add("DeepSearch") { pl2: Player ->
        Call.infoMessage(pl2.con, deepSearchNames(other).joinToString { " " })
    }
    menu.add("[red]Close")
        .show(p)
}

fun showBanMenu(p: Player, playerId: Int, target: Player) {
    TextMenu(Cons2 { pl: Player, reason: String ->
        if (reason.isEmpty()) {
            pl.sendMessage("[scarlet]Reason is empty!")
            return@Cons2
        }
        TextMenu(Cons2 { pl2: Player, time: String ->
            if (time.isEmpty()) {
                pl2.sendMessage("[scarlet]Time is empty! 1h 1d 1m 1y perm etc")
                return@Cons2
            }
            val timeL = parseTime(time)
            if (timeL == -1L && !time.contains("perm")) {
                pl2.sendMessage("[scarlet]Unknown! 1h 1d 1m 1y perm etc")
                return@Cons2
            }
            if (ban(playerId, pl2, reason, timeL)) {
                pl2.sendMessage("[green]Player banned.")
                target.kick(
                    MessageFormat.format(
                        Bundle.get("banned"),
                        reason,
                        time,
                        PVars.discordLink,
                        "unknown (re-join to see)"
                    ), 0);
            } else {
                pl2.sendMessage("[scarlet]Failed to ban player.")
            }
        }).setTitle("Ban Time").setMessage("Write time here, 1d etc.").show(pl)
    }).setTitle("Reason").setMessage("Write reason here").show(p)
}