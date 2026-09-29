package fr.draftmc.core;

import fr.draftmc.Draftmc;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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

public class AdminCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBS = Arrays.asList("help", "reload", "questadd", "setupranks", "explosion");
    private final Draftmc plugin;
    private final AdminGui gui;

    public AdminCommand(Draftmc plugin, AdminGui gui) {
        this.plugin = plugin;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean admin = sender.hasPermission("draftmc.admin");
        boolean staff = sender.hasPermission("draftmc.staff");
        if (!admin && !staff) {
            plugin.msg(sender, "&cPas la permission.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help") || args[0].equalsIgnoreCase("gui")) {
            if (sender instanceof Player) {
                gui.openMain((Player) sender);
            } else {
                gui.sendTextHelp(sender);
            }
            return true;
        }
        if (!admin) {
            plugin.msg(sender, "&cCette sous-commande est admin. &7Ouvre &e/admin &7pour le menu staff.");
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadAll();
            plugin.msg(sender, "&aConfiguration rechargée.");
            return true;
        }
        if (args[0].equalsIgnoreCase("questadd") && args.length >= 2) {
            Player p = Bukkit.getPlayer(args[1]);
            if (p == null) {
                plugin.msg(sender, "&cHors-ligne.");
                return true;
            }
            plugin.classement().addQuest(p);
            plugin.msg(sender, "&aQuête ajoutée à " + p.getName());
            return true;
        }
        if (args[0].equalsIgnoreCase("setupranks")) {
            new fr.draftmc.grades.StaffRankSetup(plugin).apply();
            plugin.msg(sender, "&aGroupes LuckPerms helper/modo/admin/owner appliqués.");
            return true;
        }
        if (args[0].equalsIgnoreCase("explosion")) {
            if (args.length < 2) {
                plugin.msg(sender, "&e/dmc explosion <MATERIAL> [hits]");
                plugin.msg(sender, "&7Ex: &e/dmc explosion OBSIDIAN 8");
                plugin.msg(sender, "&7Hits = nombre d'explosions avant destruction (1 = vanilla).");
                return true;
            }
            Material mat = Material.matchMaterial(args[1].toUpperCase(Locale.ROOT));
            if (mat == null || !mat.isBlock()) {
                plugin.msg(sender, "&cMatériau invalide.");
                return true;
            }
            if (args.length < 3) {
                int current = plugin.getConfig().getInt("core.explosion-durability.blocks." + mat.name(),
                        plugin.getConfig().getInt("core.explosion-durability.default-hits", 1));
                plugin.msg(sender, "&7" + mat.name() + " &8» &e" + current + " hit(s)");
                return true;
            }
            int hits;
            try {
                hits = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                plugin.msg(sender, "&cNombre invalide.");
                return true;
            }
            if (hits < 1) {
                hits = 1;
            }
            plugin.getConfig().set("core.explosion-durability.enabled", true);
            plugin.getConfig().set("core.explosion-durability.blocks." + mat.name(), hits);
            plugin.saveConfig();
            plugin.msg(sender, "&aDurabilité explosion &e" + mat.name() + " &a» &e" + hits + " hit(s)");
            return true;
        }
        gui.sendTextHelp(sender);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("draftmc.admin") && !sender.hasPermission("draftmc.staff")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filter(SUBS, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("questadd") && sender.hasPermission("draftmc.admin")) {
            List<String> names = new ArrayList<String>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            return filter(names, args[1]);
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> values, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<String>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(value);
            }
        }
        return out;
    }
}
