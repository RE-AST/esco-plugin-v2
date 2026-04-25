package plugin.commands

import arc.func.Boolf
import arc.struct.Seq
import arc.util.CommandHandler
import arc.util.CommandHandler.CommandResponse
import arc.util.CommandHandler.CommandRunner
import arc.util.Log
import arc.util.Strings
import mindustry.Vars
import mindustry.core.NetServer.InvalidCommandHandler
import mindustry.gen.Player
import plugin.Bundle
import plugin.utils.Permission

class CustomHandler {
    var handler: CommandHandler? = null
    val commands: Seq<CommandData?> = Seq<CommandData?>()

    constructor(handler: CommandHandler) {
        this.handler = handler
        registerPseudoCommands()
        Vars.netServer.invalidHandler = InvalidCommandHandler { player: Player, response: CommandResponse ->
            //val command = response.command
            Log.debug("Type " + response.type)
            if (response.type == CommandHandler.ResponseType.manyArguments) {
                //return "[scarlet]Too many arguments. Usage:[lightgray] " + response.command.text + "[gray] " + response.command.paramText;
                return@InvalidCommandHandler Bundle.get(
                    "commands.manyargs",
                    player.locale,
                    response.command.text,
                    response.command.paramText
                )
            } else if (response.type == CommandHandler.ResponseType.fewArguments) {
                //return "[scarlet]Too few arguments. Usage:[lightgray] " + response.command.text + "[gray] " + response.command.paramText;
                return@InvalidCommandHandler Bundle.get(
                    "commands.fewargs",
                    player.locale,
                    response.command.text,
                    response.command.paramText
                )
            } else { //unknown command
                //val closest = getClosest(response.runCommand)
                val closest: CommandHandler.Command? = getClosest(response.runCommand, player)

                if (closest != null) {
                    return@InvalidCommandHandler Bundle.get("commands.didyoumean", player.locale)
                        .replace("{0}", closest.text)
                } else {
                    return@InvalidCommandHandler Bundle.get("commands.unknown", player.locale)
                }
            }
        }
    }

    fun getClosest(name: String, player: Player?): CommandHandler.Command? {
        var minDst = 0
        var closest: CommandHandler.Command? = null
        val perms = Permission.getPerms(player)

        for (command in Vars.netServer.clientCommands.commandList) {
            if (!perms.contains(getCommand(command.text)!!.permission)) continue
            val dst = Strings.levenshtein(command.text, name)
            if (dst < 3 && (closest == null || dst < minDst)) {
                minDst = dst
                closest = command
            }
        }

        return closest
    }

    fun getClosest(name: String): CommandHandler.Command? {
        var minDst = 0
        var closest: CommandHandler.Command? = null

        for (command in Vars.netServer.clientCommands.commandList) {
            val dst = Strings.levenshtein(command.text, name)
            if (dst < 3 && (closest == null || dst < minDst)) {
                minDst = dst
                closest = command
            }
        }

        return closest
    }

    fun getCommand(name: String?): CommandData? {
        return commands.find(Boolf { c: CommandData? -> c!!.name == name })
    }

    fun registerCommand(name: String, runner: CommandRunner<Player?>) {
        registerCommand(name, "", Permission.none, runner)
    }

    fun registerCommand(name: String, args: String, runner: CommandRunner<Player?>) {
        registerCommand(name, args, Permission.none, runner)
    }

    fun registerCommand(name: String, args: String, perm: Permission?, runner: CommandRunner<Player?>) {
        // CommandData cd = new CommandData(name, args, perm);
        commands.add(CommandData(name, args, perm))

        handler!!.register<Player>(name, args, "", CommandRunner { a: Array<String?>?, p: Player ->
            if (!Permission.getPerms(p).contains(perm)) {
                //Bundle.sendMessage("noperms", p);
                val command = getClosest(name, p)
                if (command == null) {
                    Bundle.sendMessage("commands.unknown", p)
                } else {
                    Bundle.sendMessage("commands.didyoumean", p, command.text)
                }
                return@CommandRunner
            }
            runner.accept(a, p)
        })
    }

    fun registerPseudoCommands() {
        addPseudoCommand("t", "<message...>")
        // addPseudoCommand("a", "<message...>", Permission.admin);
        // addPseudoCommand("votekick", "[player] [reason...]");
        // addPseudoCommand("vote", "<y/n/c>");
        // addPseudoCommand("sync", "");
    }

    fun addPseudoCommand(name: String, args: String) {
        addPseudoCommand(name, args, Permission.none)
    }

    fun addPseudoCommand(name: String, args: String, perm: Permission?) {
        commands.add(CommandData(name, args, perm))
    }

    class CommandData internal constructor(var name: String, var args: String, var permission: Permission?) {
        fun getDesc(p: Player): String {
            val req = "commands." + this.name + ".description"
            var desc = Bundle.get(req, p.locale)
            if (desc == req) desc = Bundle.get("commands.nodesc", p.locale)
            return desc
        }
    }
}
