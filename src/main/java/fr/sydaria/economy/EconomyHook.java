package fr.sydaria.economy;

import fr.sydaria.Sydaria;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Locale;
import java.util.UUID;

/**
 * Argent (money) : uniquement pour /shop.
 * Les tokens restent un solde séparé, uniquement pour /boutique.
 */
public class EconomyHook implements CommandExecutor {
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
                return vault.getBalance(online);
            }
        }
        return Sydaria.get().data().getMoney(uuid);
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
        Sydaria.get().data().setMoney(player.getUniqueId(), getMoney(player) - amount);
        return true;
    }

    public boolean deposit(Player player, double amount) {
        if (amount < 0) {
            return false;
        }
        if (vault != null) {
            return vault.depositPlayer(player, amount).transactionSuccess();
        }
        Sydaria.get().data().setMoney(player.getUniqueId(), getMoney(player) + amount);
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
        Sydaria plugin = Sydaria.get();
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cJoueur uniquement.");
                return true;
            }
            Player player = (Player) sender;
            plugin.msg(player, "&aTon argent &7(shop) &7» &f" + format(player));
            plugin.msg(player, "&7Utilise &e/shop &7pour acheter. Les tokens (&e/tokens&7) servent à &e/boutique&7.");
            return true;
        }
        if (args[0].equalsIgnoreCase("give") && args.length >= 3 && sender.hasPermission("sydaria.money.give")) {
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
        if (args[0].equalsIgnoreCase("set") && args.length >= 3 && sender.hasPermission("sydaria.money.give")) {
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
        plugin.msg(sender, "&e/money &7| &e/money give <joueur> <montant> &8- &7argent du /shop");
        return true;
    }
}
