package fr.draftmc.homes;

import fr.draftmc.Draftmc;
import fr.draftmc.util.ActionBars;
import fr.draftmc.util.CC;
import fr.draftmc.util.Locations;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class HomeManager implements CommandExecutor, TabCompleter, Listener {
    private final Draftmc plugin;
    private final Map<UUID, Integer> warmups = new HashMap<UUID, Integer>();
    private final Map<UUID, Location> warmupStart = new HashMap<UUID, Location>();

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
        if (plugin.combat() != null && plugin.combat().denyIfTagged(player)) {
            return;
        }
        if (plugin.denyTpCooldown(player)) {
            return;
        }
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
        if (warmups.containsKey(player.getUniqueId())) {
            plugin.msg(player, "&cUne téléportation est déjà en cours.");
            return;
        }
        int delay = Math.max(0, plugin.getConfig().getInt("homes.teleport-delay-seconds", 5));
        plugin.startTpCooldown(player);
        if (delay <= 0) {
            finishTeleport(player, loc, homeName);
            return;
        }
        plugin.msg(player, plugin.getConfig().getString("homes.warmup-message",
                "&7Téléportation dans &e{time}s&7. &8Ne bouge pas.")
                .replace("{time}", String.valueOf(delay))
                .replace("{home}", homeName));
        warmupStart.put(player.getUniqueId(), player.getLocation().clone());
        final int[] left = {delay};
        int task = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancelWarmup(player, null);
                    return;
                }
                if (plugin.combat() != null && plugin.combat().isTagged(player)) {
                    cancelWarmup(player, "&cTéléportation annulée : tu es en combat.");
                    return;
                }
                left[0]--;
                if (left[0] <= 0) {
                    cancelWarmup(player, null);
                    finishTeleport(player, loc, homeName);
                    return;
                }
                ActionBars.send(player, CC.color("&eTéléportation &7» &f" + left[0] + "s"));
            }
        }, 20L, 20L);
        warmups.put(player.getUniqueId(), task);
    }

    private void finishTeleport(Player player, Location loc, String homeName) {
        plugin.data().setString(player.getUniqueId(), "back_location",
                Locations.serialize(player.getLocation()));
        player.teleport(loc);
        plugin.msg(player, "&aTéléporté au home &e" + homeName + "&a.");
    }

    public void cancelWarmup(Player player, String message) {
        Integer task = warmups.remove(player.getUniqueId());
        warmupStart.remove(player.getUniqueId());
        if (task == null) {
            return;
        }
        Bukkit.getScheduler().cancelTask(task);
        if (message != null && player.isOnline()) {
            plugin.msg(player, message);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location start = warmupStart.get(player.getUniqueId());
        if (start == null) {
            return;
        }
        if (!plugin.getConfig().getBoolean("homes.cancel-on-move", true)) {
            return;
        }
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        if (start.getBlockX() != to.getBlockX()
                || start.getBlockY() != to.getBlockY()
                || start.getBlockZ() != to.getBlockZ()) {
            cancelWarmup(player, plugin.getConfig().getString("homes.cancel-move-message",
                    "&cTéléportation annulée : tu as bougé."));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        if (!plugin.getConfig().getBoolean("homes.cancel-on-damage", true)) {
            return;
        }
        Player player = (Player) event.getEntity();
        if (warmups.containsKey(player.getUniqueId())) {
            cancelWarmup(player, plugin.getConfig().getString("homes.cancel-damage-message",
                    "&cTéléportation annulée : tu as pris des dégâts."));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancelWarmup(event.getPlayer(), null);
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
