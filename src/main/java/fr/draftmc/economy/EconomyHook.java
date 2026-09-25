package fr.draftmc.economy;

import fr.draftmc.Draftmc;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Argent (money) : uniquement pour /shop.
 * Les tokens restent un solde séparé, uniquement pour /boutique.
 */
public class EconomyHook implements CommandExecutor, TabCompleter {
    private Economy vault;

    public EconomyHook() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return;
        }
        try {
            RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                vault = rsp.getProvider();
            }
        } catch (Throwable ignored) {
        }
    }

    public boolean hasVault() {
        return vault != null;
    }

    public double getMoney(Player player) {
        return getMoney(player.getUniqueId());
    }

    public double getMoney(UUID uuid) {
        if (vault != null) {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                try {
                    return vault.getBalance(online);
                } catch (Throwable ignored) {
                }
            }
            try {
                return vault.getBalance(Bukkit.getOfflinePlayer(uuid));
            } catch (Throwable ignored) {
            }
            try {
                return vault.getBalance(Draftmc.get().data().nameOf(uuid));
            } catch (Throwable ignored) {
            }
        }
        return Draftmc.get().data().getMoney(uuid);
    }

    public boolean has(Player player, double amount) {
        return getMoney(player) + 0.0001 >= amount;
    }

    public boolean withdraw(Player player, double amount) {
        if (amount < 0) {
            return false;
        }
        if (!has(player, amount)) {
            return false;
        }
        if (vault != null) {
            return vault.withdrawPlayer(player, amount).transactionSuccess();
        }
        Draftmc.get().data().setMoney(player.getUniqueId(), getMoney(player) - amount);
        return true;
    }

    public boolean deposit(Player player, double amount) {
        return deposit(player.getUniqueId(), amount);
    }

    public boolean deposit(UUID uuid, double amount) {
        if (amount < 0) {
            return false;
        }
        Player online = Bukkit.getPlayer(uuid);
        if (vault != null && online != null) {
            return vault.depositPlayer(online, amount).transactionSuccess();
        }
        Draftmc.get().data().setMoney(uuid, getMoney(uuid) + amount);
        return true;
    }

    public String format(Player player) {
        return format(getMoney(player));
    }

    public String format(double amount) {
        if (vault != null) {
            try {
                return vault.format(amount);
            } catch (Throwable ignored) {
            }
        }
        if (Math.abs(amount - Math.round(amount)) < 0.005) {
            return ((long) Math.round(amount)) + "$";
        }
        return String.format(Locale.US, "%.2f$", amount);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Draftmc plugin = Draftmc.get();
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cJoueur uniquement.");
                return true;
            }
            Player player = (Player) sender;
            plugin.msg(player, "&aArgent &7» &f" + format(player));
            return true;
        }
        if (args[0].equalsIgnoreCase("give") && args.length >= 3 && sender.hasPermission("draftmc.money.give")) {
            Player to = Bukkit.getPlayer(args[1]);
            if (to == null) {
                plugin.msg(sender, "&cJoueur hors-ligne.");
                return true;
            }
            double amount;
            try {
                amount = Double.parseDouble(args[2]);
            } catch (NumberFormatException e) {
                plugin.msg(sender, "&cMontant invalide.");
                return true;
            }
            deposit(to, amount);
            plugin.msg(sender, "&a+" + format(amount) + " &7(argent) pour &e" + to.getName());
            plugin.msg(to, "&a+" + format(amount) + " &7ajoutés à ton argent shop.");
            return true;
        }
        if (args[0].equalsIgnoreCase("set") && args.length >= 3 && sender.hasPermission("draftmc.money.give")) {
            Player to = Bukkit.getPlayer(args[1]);
            if (to == null) {
                plugin.msg(sender, "&cJoueur hors-ligne.");
                return true;
            }
            double amount;
            try {
                amount = Math.max(0, Double.parseDouble(args[2]));
            } catch (NumberFormatException e) {
                plugin.msg(sender, "&cMontant invalide.");
                return true;
            }
            if (vault != null) {
                double current = vault.getBalance(to);
                if (amount > current) {
                    vault.depositPlayer(to, amount - current);
                } else if (amount < current) {
                    vault.withdrawPlayer(to, current - amount);
                }
            } else {
                plugin.data().setMoney(to.getUniqueId(), amount);
            }
            plugin.msg(sender, "&aArgent de &e" + to.getName() + " &adéfini à &f" + format(amount));
            return true;
        }
        UUID uuid = resolveUuid(args[0]);
        if (uuid == null) {
            plugin.msg(sender, "&cJoueur introuvable.");
            return true;
        }
        String name = plugin.data().nameOf(uuid);
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            name = online.getName();
        }
        plugin.msg(sender, "&aArgent de &e" + name + " &7» &f" + format(getMoney(uuid)));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return Collections.emptyList();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<String>();
        if (sender.hasPermission("draftmc.money.give")) {
            if ("give".startsWith(prefix)) {
                out.add("give");
            }
            if ("set".startsWith(prefix)) {
                out.add("set");
            }
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                out.add(online.getName());
            }
        }
        return out;
    }

    private UUID resolveUuid(String raw) {
        Player online = Bukkit.getPlayer(raw);
        if (online != null) {
            return online.getUniqueId();
        }
        UUID stored = Draftmc.get().data().findUuidByString("name", raw);
        if (stored != null) {
            return stored;
        }
        org.bukkit.OfflinePlayer[] offline = Bukkit.getOfflinePlayers();
        for (int i = 0; i < offline.length; i++) {
            if (offline[i].getName() != null && offline[i].getName().equalsIgnoreCase(raw)) {
                return offline[i].getUniqueId();
            }
        }
        return null;
    }
}
