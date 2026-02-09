package plugin.commands;

import arc.struct.Seq;
import arc.util.CommandHandler;
import arc.util.CommandHandler.CommandRunner;
import arc.util.Strings;
import mindustry.Vars;
import mindustry.gen.Player;

import plugin.Bundle;
import plugin.utils.Permission;

import static mindustry.Vars.netServer;

public class CustomHandler {
    final CommandHandler handler;
    public final Seq<CommandData> commands = new Seq<CommandData>();

    public CustomHandler(CommandHandler handler) {
        this.handler = handler;
        registerPseudoCommands();
        Vars.netServer.invalidHandler = (player, response) -> {
            var command = response.command;
            if(response.type == CommandHandler.ResponseType.manyArguments){
                //return "[scarlet]Too many arguments. Usage:[lightgray] " + response.command.text + "[gray] " + response.command.paramText;
                return Bundle.get("commands.manyargs", player.locale, command.text, command.paramText);
            }else if(response.type == CommandHandler.ResponseType.fewArguments){
                //return "[scarlet]Too few arguments. Usage:[lightgray] " + response.command.text + "[gray] " + response.command.paramText;
                return Bundle.get("commands.fewargs", player.locale, command.text, command.paramText);
            }else{ //unknown command
                CommandHandler.Command closest = getClosest(command.text);

                if(closest != null){
                    return Bundle.get("commands.didyoumean", player.locale, closest.text);
                }else{
                    return Bundle.get("commands.unknown", player.locale);
                }
            }
        };
    }

    public CommandHandler.Command getClosest(String name, Player player) {
        int minDst = 0;
        CommandHandler.Command closest = null;
        Seq<Permission> perms = Permission.getPerms(player);

        for(CommandHandler.Command command : netServer.clientCommands.getCommandList()){
            if(!perms.contains(getCommand(command.text).permission)) continue;
            int dst = Strings.levenshtein(command.text, name);
            if(dst < 3 && (closest == null || dst < minDst)){
                minDst = dst;
                closest = command;
            }
        }

        return closest;
    }

    public CommandHandler.Command getClosest(String name) {
        int minDst = 0;
        CommandHandler.Command closest = null;

        for(CommandHandler.Command command : netServer.clientCommands.getCommandList()){
            int dst = Strings.levenshtein(command.text, name);
            if(dst < 3 && (closest == null || dst < minDst)){
                minDst = dst;
                closest = command;
            }
        }

        return closest;
    }

    public CommandData getCommand(String name) {
        return commands.find(c->c.name.equals(name));
    }

    public void registerCommand(String name, CommandRunner<Player> runner) {
        registerCommand(name, "", Permission.none, runner);
    }

    public void registerCommand(String name, String args, CommandRunner<Player> runner) {
        registerCommand(name, args, Permission.none, runner);
    }

    public void registerCommand(String name, String args, Permission perm, CommandRunner<Player> runner) {
        // CommandData cd = new CommandData(name, args, perm);
        commands.add(new CommandData(name, args, perm));

        handler.<Player>register(name, args, "", (a, p)->{
            if(!Permission.getPerms(p).contains(perm)) {
                //Bundle.sendMessage("noperms", p);
                var command = getClosest(name, p);
                if(command == null) {
                    Bundle.sendMessage("commands.unknown", p.locale);
                } else {
                    Bundle.sendMessage("commands.didyoumean", p.locale, command.text);
                }
                return;
            }
            runner.accept(a, p);
        });
    }

    public void registerPseudoCommands() {
        addPseudoCommand("t", "<message...>");
        addPseudoCommand("a", "<message...>", Permission.admin);
        addPseudoCommand("votekick", "[player] [reason...]");
        addPseudoCommand("vote", "<y/n/c>");
        addPseudoCommand("sync", "");
    }

    public void addPseudoCommand(String name, String args) {
        addPseudoCommand(name, args, Permission.none);
    }

    public void addPseudoCommand(String name, String args, Permission perm) {
        commands.add(new CommandData(name, args, perm));
    }

    public static class CommandData {
        public String name, args;
        public Permission permission;

        CommandData(String name, String args, Permission perm) {
            this.args = args;
            this.permission = perm;
        }
    }
}
