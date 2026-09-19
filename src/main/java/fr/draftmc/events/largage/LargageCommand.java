package fr.draftmc.events.largage;

import fr.draftmc.events.EventType;
import fr.draftmc.util.CC;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class LargageCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBS = Arrays.asList(
            "help", "start", "stop", "add", "remove", "list", "clear", "status");
    private final LargagePlugin plugin;

    public LargageCommand(LargagePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }
        if (!isAdmin(sender)) {
            sender.sendMessage(CC.color("&cPas la permission."));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("start")) {
            if (plugin.getManager().start()) {
                if (plugin.getHost().events() != null) {
                    plugin.getHost().events().markActive(EventType.LARGAGE, "default");
                }
            } else if (plugin.getManager().running()) {
                sender.sendMessage(plugin.prefix() + CC.color(plugin.getConfig().getString("messages.already")));
            } else {
                sender.sendMessage(plugin.prefix() + CC.color(plugin.getConfig().getString("messages.no-chests")));
            }
            return true;
        }
        if (sub.equals("stop")) {
            if (plugin.getManager().stop(true)) {
                if (plugin.getHost().events() != null) {
                    plugin.getHost().events().clearActive(EventType.LARGAGE, "default");
                }
            } else {
                sender.sendMessage(plugin.prefix() + CC.color(plugin.getConfig().getString("messages.not-running")));
            }
            return true;
        }
        if (sub.equals("status")) {
            if (!plugin.getManager().running()) {
                sender.sendMessage(plugin.prefix() + CC.color("&7Aucun largage en cours."));
                return true;
            }
            if (!plugin.getManager().dropped()) {
                sender.sendMessage(plugin.prefix() + CC.color("&7Spawn dans : &e"
                        + plugin.getManager().countdown() + "s"));
                return true;
            }
            sender.sendMessage(plugin.prefix() + CC.color("&7Coffres : &e" + plugin.getManager().remainingChests()
                    + " &7| Temps : &e" + plugin.getManager().timeLeft() + "s"));
            return true;
        }
        if (sub.equals("list")) {
            plugin.getManager().listChests(sender);
            return true;
        }
        if (sub.equals("clear")) {
            plugin.getManager().clearChests(sender);
            return true;
        }
        if (sub.equals("remove") || sub.equals("del") || sub.equals("delete")) {
            plugin.getManager().removeChest(sender, args.length >= 2 ? args[1] : null);
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cJoueur uniquement.");
            return true;
        }
        Player player = (Player) sender;
        if (sub.equals("add") || sub.equals("set") || sub.equals("setpos")
                || sub.equals("setpos1") || sub.equals("setpos2")) {
            plugin.getManager().addChest(player);
            return true;
        }
        sendHelp(sender);
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(plugin.prefix() + CC.color("&6/largage add &7- enregistre ta position comme coffre"));
        sender.sendMessage(plugin.prefix() + CC.color("&6/largage remove [n] &7- retire un coffre (ou le plus proche)"));
        sender.sendMessage(plugin.prefix() + CC.color("&6/largage list &7- liste les positions"));
        sender.sendMessage(plugin.prefix() + CC.color("&6/largage clear &7- vide toutes les positions"));
        sender.sendMessage(plugin.prefix() + CC.color("&6/largage start &7- spawn les coffres"));
        sender.sendMessage(plugin.prefix() + CC.color("&6/largage stop &7- retire les coffres restants"));
        sender.sendMessage(plugin.prefix() + CC.color("&6/largage status"));
    }

    private boolean isAdmin(CommandSender sender) {
        return sender.hasPermission("largage.admin") || sender.hasPermission("draftmc.admin")
                || sender.hasPermission("draftmc.event");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<String>();
            String token = args[0].toLowerCase(Locale.ROOT);
            for (String sub : SUBS) {
                if (sub.startsWith(token)) {
                    out.add(sub);
                }
            }
            return out;
        }
        return Collections.emptyList();
    }
}
