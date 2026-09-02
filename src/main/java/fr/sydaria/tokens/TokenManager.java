package fr.sydaria.tokens;

import fr.sydaria.Sydaria;
import fr.sydaria.data.DataManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class TokenManager implements CommandExecutor {
    private final Sydaria plugin;

    public TokenManager(Sydaria plugin) {
        this.plugin = plugin;
    }

    public long get(UUID uuid) {
        return plugin.data().getTokens(uuid);
    }

    public void set(UUID uuid, long amount) {
        plugin.data().setTokens(uuid, Math.max(0, amount));
    }

    public boolean take(UUID uuid, long amount) {
        if (get(uuid) < amount) {
            return false;
        }
        set(uuid, get(uuid) - amount);
        return true;
    }

    public void add(UUID uuid, long amount) {
        set(uuid, get(uuid) + amount);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        DataManager data = plugin.data();
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cJoueur uniquement.");
                return true;
            }
            Player player = (Player) sender;
            plugin.msg(player, "&eTes tokens &7(boutique) &7» &6" + get(player.getUniqueId()));
            plugin.msg(player, "&7Utilise &e/boutique &7pour acheter. L'argent (&e/money&7) sert à &e/shop&7.");
            return true;
        }
        if (args[0].equalsIgnoreCase("pay") && args.length >= 3) {
            if (!(sender instanceof Player)) {
                return true;
            }
            Player from = (Player) sender;
            Player to = Bukkit.getPlayer(args[1]);
            long amount;
            try {
                amount = Long.parseLong(args[2]);
            } catch (NumberFormatException e) {
                plugin.msg(from, "&cMontant invalide.");
                return true;
            }
            if (to == null) {
                plugin.msg(from, "&cJoueur hors-ligne.");
                return true;
            }
            if (amount < plugin.getConfig().getLong("tokens.pay-min", 1)) {
                plugin.msg(from, "&cMontant trop bas.");
                return true;
            }
            if (!take(from.getUniqueId(), amount)) {
                plugin.msg(from, "&cTokens insuffisants.");
                return true;
            }
            add(to.getUniqueId(), amount);
            plugin.msg(from, "&7Tu as envoyé &6" + amount + " &7tokens à &e" + to.getName());
            plugin.msg(to, "&e" + from.getName() + " &7t'a envoyé &6" + amount + " &7tokens boutique.");
            return true;
        }
        if (args[0].equalsIgnoreCase("give") && args.length >= 3 && sender.hasPermission("sydaria.tokens.give")) {
            Player to = Bukkit.getPlayer(args[1]);
            if (to == null) {
                plugin.msg(sender, "&cJoueur hors-ligne.");
                return true;
            }
            long amount = Long.parseLong(args[2]);
            add(to.getUniqueId(), amount);
            plugin.msg(sender, "&a+" + amount + " tokens boutique pour " + to.getName());
            return true;
        }
        if (args[0].equalsIgnoreCase("set") && args.length >= 3 && sender.hasPermission("sydaria.tokens.give")) {
            Player to = Bukkit.getPlayer(args[1]);
            if (to == null) {
                return true;
            }
            set(to.getUniqueId(), Long.parseLong(args[2]));
            plugin.msg(sender, "&aTokens de " + to.getName() + " définis.");
            data.ensure(to);
            return true;
        }
        plugin.msg(sender, "&e/tokens &7| &e/tokens pay <joueur> <montant> &8- &7tokens de /boutique");
        return true;
    }
}
