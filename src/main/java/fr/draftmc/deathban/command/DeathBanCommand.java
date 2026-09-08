package fr.draftmc.deathban.command;

import fr.draftmc.deathban.DeathBanPlugin;
import fr.draftmc.deathban.manager.DeathBanEntry;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
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
import java.util.UUID;

public final class DeathBanCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = Arrays.asList("unban", "ban", "check", "list", "reload");

    private final DeathBanPlugin plugin;

    public DeathBanCommand(DeathBanPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("deathban.admin") && !sender.hasPermission("draftmc.admin")) {
            sender.sendMessage(plugin.getConfigManager().message("no-permission"));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(plugin.getConfigManager().message("usage"));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("reload")) {
            plugin.getConfigManager().reload();
            plugin.getBanManager().load();
            sender.sendMessage(plugin.getConfigManager().message("reloaded"));
            return true;
        }
        if (sub.equals("list")) {
            List<DeathBanEntry> bans = plugin.getBanManager().getActiveBans();
            if (bans.isEmpty()) {
                sender.sendMessage(plugin.getConfigManager().message("list-empty"));
                return true;
            }
            sender.sendMessage(plugin.getConfigManager().message("list-header"));
            for (DeathBanEntry entry : bans) {
                sender.sendMessage(plugin.getConfigManager().raw("list-entry")
                        .replace("{player}", entry.getName())
                        .replace("{world}", entry.getWorld())
                        .replace("{duration}", plugin.getBanManager().formatDuration(entry)));
            }
            return true;
        }
        if (sub.equals("unban")) {
            if (args.length < 2) {
                sender.sendMessage(plugin.getConfigManager().message("usage"));
                return true;
            }
            OfflinePlayer target = resolvePlayer(args[1]);
            if (target == null) {
                sender.sendMessage(plugin.getConfigManager().message("player-not-found"));
                return true;
            }
            boolean removed = plugin.getBanManager().unban(target.getUniqueId());
            if (removed) {
                sender.sendMessage(plugin.getConfigManager().message("unbanned-player")
                        .replace("{player}", nameOf(target, args[1])));
            } else {
                sender.sendMessage(plugin.getConfigManager().message("not-banned")
                        .replace("{player}", nameOf(target, args[1])));
            }
            return true;
        }
        if (sub.equals("check")) {
            if (args.length < 2) {
                sender.sendMessage(plugin.getConfigManager().message("usage"));
                return true;
            }
            OfflinePlayer target = resolvePlayer(args[1]);
            if (target == null) {
                sender.sendMessage(plugin.getConfigManager().message("player-not-found"));
                return true;
            }
            DeathBanEntry entry = plugin.getBanManager().getBan(target.getUniqueId());
            if (entry == null) {
                sender.sendMessage(plugin.getConfigManager().message("check-ok")
                        .replace("{player}", nameOf(target, args[1])));
            } else {
                sender.sendMessage(plugin.getConfigManager().message("check-banned")
                        .replace("{player}", entry.getName())
                        .replace("{world}", entry.getWorld())
                        .replace("{duration}", plugin.getBanManager().formatDuration(entry)));
            }
            return true;
        }
        if (sub.equals("ban")) {
            if (args.length < 2) {
                sender.sendMessage(plugin.getConfigManager().message("usage"));
                return true;
            }
            OfflinePlayer target = resolvePlayer(args[1]);
            if (target == null) {
                sender.sendMessage(plugin.getConfigManager().message("player-not-found"));
                return true;
            }
            String world;
            if (args.length >= 3) {
                world = args[2];
            } else if (!plugin.getConfigManager().getWorldNames().isEmpty()) {
                world = plugin.getConfigManager().getWorldNames().get(0);
            } else if (target.isOnline()) {
                world = ((Player) target).getWorld().getName();
            } else {
                sender.sendMessage(plugin.getConfigManager().message("usage"));
                return true;
            }
            DeathBanEntry entry = plugin.getBanManager().ban(target, world);
            sender.sendMessage(plugin.getConfigManager().message("banned-by-admin")
                    .replace("{player}", nameOf(target, args[1]))
                    .replace("{world}", world));
            if (target.isOnline()) {
                Player online = (Player) target;
                online.sendMessage(plugin.getConfigManager().message("death-banned")
                        .replace("{world}", world)
                        .replace("{duration}", plugin.getBanManager().formatDuration(entry)));
                if (plugin.getBanManager().isBannedFrom(online.getUniqueId(), online.getWorld().getName())
                        && !online.hasPermission("deathban.bypass.teleport")) {
                    plugin.getBanManager().sendToSafeWorld(online);
                }
            }
            return true;
        }

        sender.sendMessage(plugin.getConfigManager().message("usage"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("deathban.admin") && !sender.hasPermission("draftmc.admin")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filter(SUBS, args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("unban")
                || args[0].equalsIgnoreCase("ban")
                || args[0].equalsIgnoreCase("check"))) {
            List<String> names = new ArrayList<String>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            return filter(names, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("ban")) {
            return filter(plugin.getConfigManager().getWorldNames(), args[2]);
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> options, String token) {
        String prefix = token.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<String>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                result.add(option);
            }
        }
        return result;
    }

    @SuppressWarnings("deprecation")
    private OfflinePlayer resolvePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        for (DeathBanEntry entry : plugin.getBanManager().getActiveBans()) {
            if (entry.getName().equalsIgnoreCase(name)) {
                return Bukkit.getOfflinePlayer(entry.getUuid());
            }
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (offline != null && (offline.hasPlayedBefore() || offline.isOnline())) {
            return offline;
        }
        try {
            return Bukkit.getOfflinePlayer(UUID.fromString(name));
        } catch (IllegalArgumentException ignored) {
            return offline != null && offline.getUniqueId() != null ? offline : null;
        }
    }

    private String nameOf(OfflinePlayer player, String fallback) {
        return player.getName() != null ? player.getName() : fallback;
    }
}
