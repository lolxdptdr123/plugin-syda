package fr.draftmc.economy;

import fr.draftmc.Draftmc;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class PayCommand implements CommandExecutor, TabCompleter {
    private final Draftmc plugin;

    public PayCommand(Draftmc plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cJoueur uniquement.");
            return true;
        }
        Player from = (Player) sender;
        if (args.length < 2) {
            plugin.msg(from, "&e/pay <joueur> <montant>");
            return true;
        }
        Player to = Bukkit.getPlayer(args[0]);
        if (to == null) {
            plugin.msg(from, "&cJoueur hors-ligne.");
            return true;
        }
        if (to.getUniqueId().equals(from.getUniqueId())) {
            plugin.msg(from, "&cTu ne peux pas te payer toi-même.");
            return true;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[1].replace(',', '.'));
        } catch (NumberFormatException e) {
            plugin.msg(from, "&cMontant invalide.");
            return true;
        }
        double min = plugin.getConfig().getDouble("pay.min", 1.0);
        if (amount < min) {
            plugin.msg(from, "&cMontant minimum: &e" + plugin.economy().format(min));
            return true;
        }
        if (!plugin.economy().withdraw(from, amount)) {
            plugin.msg(from, "&cPas assez d'argent. &7Solde: &a" + plugin.economy().format(from));
            return true;
        }
        if (!plugin.economy().deposit(to, amount)) {
            plugin.economy().deposit(from, amount);
            plugin.msg(from, "&cPaiement impossible.");
            return true;
        }
        String pretty = plugin.economy().format(amount);
        plugin.msg(from, "&7Tu as envoyé &a" + pretty + " &7à &e" + to.getName() + "&7.");
        plugin.msg(to, "&e" + from.getName() + " &7t'a envoyé &a" + pretty + "&7.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return Collections.emptyList();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<String>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)
                    && (!(sender instanceof Player) || !online.equals(sender))) {
                names.add(online.getName());
            }
        }
        return names;
    }
}
