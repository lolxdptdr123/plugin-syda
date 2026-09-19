package fr.draftmc.homes;

import fr.draftmc.Draftmc;
import fr.draftmc.util.Locations;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class HomeManager implements CommandExecutor, TabCompleter {
    private final Draftmc plugin;

    public HomeManager(Draftmc plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cJoueur uniquement.");
            return true;
        }
        Player player = (Player) sender;
        String name = command.getName().toLowerCase(Locale.ROOT);
        if ("sethome".equals(name)) {
            setHome(player, args);
        } else if ("delhome".equals(name)) {
            delHome(player, args);
        } else if ("homes".equals(name)) {
            listHomes(player);
        } else {
            goHome(player, args);
        }
        return true;
    }

    private void setHome(Player player, String[] args) {
        int max = plugin.grades().perks().maxHomes(player);
        if (max <= 0) {
            plugin.msg(player, "&cTon grade n'a pas accès aux homes. &7Grade &eChevalier &7minimum.");
            return;
        }
        String homeName = homeName(args, 0);
        List<String> homes = homeNames(player.getUniqueId());
        if (!homes.contains(homeName) && homes.size() >= max) {
            plugin.msg(player, "&cLimite de homes atteinte (&e" + max + "&c). &7Supprime-en un avec &e/delhome");
            return;
        }
        plugin.data().setString(player.getUniqueId(), "homes." + homeName, Locations.serialize(player.getLocation()));
        if (!homes.contains(homeName)) {
            homes.add(homeName);
            plugin.data().setList(player.getUniqueId(), "home_names", homes);
        }
        plugin.msg(player, "&aHome &e" + homeName + " &adéfini. &7(" + homes.size() + "/" + max + ")");
    }

    private void delHome(Player player, String[] args) {
        String homeName = homeName(args, 0);
        List<String> homes = homeNames(player.getUniqueId());
        if (!homes.contains(homeName)) {
            plugin.msg(player, "&cHome introuvable. &e/homes");
            return;
        }
        homes.remove(homeName);
        plugin.data().setList(player.getUniqueId(), "home_names", homes);
        plugin.data().setString(player.getUniqueId(), "homes." + homeName, null);
        plugin.msg(player, "&cHome &e" + homeName + " &csupprimé.");
    }

    private void goHome(Player player, String[] args) {
        int max = plugin.grades().perks().maxHomes(player);
        if (max <= 0) {
            plugin.msg(player, "&cTon grade n'a pas accès aux homes.");
            return;
        }
        String homeName = homeName(args, 0);
        Location loc = Locations.deserialize(plugin.data().getString(player.getUniqueId(), "homes." + homeName));
        if (loc == null) {
            plugin.msg(player, "&cHome &e" + homeName + " &cintrouvable. &e/sethome " + homeName);
            return;
        }
        plugin.teleports().request(player, loc, "&aTéléporté au home &e" + homeName + "&a.");
    }

    private void listHomes(Player player) {
        int max = plugin.grades().perks().maxHomes(player);
        List<String> homes = homeNames(player.getUniqueId());
        plugin.msg(player, "&6Homes &7» &f" + homes.size() + "&7/&f" + max);
        if (homes.isEmpty()) {
            plugin.msg(player, "&7Aucun home. &e/sethome");
            return;
        }
        plugin.msg(player, "&e" + join(homes));
    }

    private List<String> homeNames(UUID uuid) {
        return plugin.data().getList(uuid, "home_names");
    }

    private String homeName(String[] args, int index) {
        if (args.length <= index || args[index] == null || args[index].isEmpty()) {
            return "home";
        }
        return args[index].toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
    }

    private String join(List<String> values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append("&7, &e");
            }
            sb.append(values.get(i));
        }
        return sb.toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player) || args.length != 1) {
            return Collections.emptyList();
        }
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (!"home".equals(name) && !"delhome".equals(name)) {
            return Collections.emptyList();
        }
        Player player = (Player) sender;
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<String>();
        for (String home : homeNames(player.getUniqueId())) {
            if (home.startsWith(prefix)) {
                out.add(home);
            }
        }
        return out;
    }
}
