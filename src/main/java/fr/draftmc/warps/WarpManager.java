package fr.draftmc.warps;

import fr.draftmc.Draftmc;
import fr.draftmc.util.ActionBars;
import fr.draftmc.util.CC;
import fr.draftmc.util.Locations;
import fr.draftmc.util.YamlFile;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
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

public class WarpManager implements CommandExecutor, TabCompleter, Listener {
    private final Draftmc plugin;
    private final YamlFile file;
    private final Map<UUID, Integer> warmups = new HashMap<UUID, Integer>();
    private final Map<UUID, Location> warmupStart = new HashMap<UUID, Location>();

    public WarpManager(Draftmc plugin) {
        this.plugin = plugin;
        this.file = new YamlFile(plugin, "warps.yml");
    }

    private boolean isStaff(CommandSender sender) {
        return sender.hasPermission("draftmc.staff") || sender.hasPermission("draftmc.admin");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if ("setwarp".equals(name)) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cJoueur uniquement.");
                return true;
            }
            if (!isStaff(sender)) {
                plugin.msg(sender, "&cCommande staff uniquement.");
                return true;
            }
            setWarp((Player) sender, args);
            return true;
        }
        if ("delwarp".equals(name)) {
            if (!isStaff(sender)) {
                plugin.msg(sender, "&cCommande staff uniquement.");
                return true;
            }
            delWarp(sender, args);
            return true;
        }
        if ("warps".equals(name)) {
            list(sender);
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cJoueur uniquement.");
            return true;
        }
        goWarp((Player) sender, args);
        return true;
    }

    private void setWarp(Player player, String[] args) {
        if (args.length < 1) {
            plugin.msg(player, "&e/setwarp <nom>");
            return;
        }
        String id = warpName(args[0]);
        if (id.isEmpty()) {
            plugin.msg(player, "&cNom invalide.");
            return;
        }
        file.get().set("warps." + id, Locations.serialize(player.getLocation()));
        file.save();
        plugin.msg(player, "&aWarp &e" + id + " &adéfini.");
    }

    private void delWarp(CommandSender sender, String[] args) {
        if (args.length < 1) {
            plugin.msg(sender, "&e/delwarp <nom>");
            return;
        }
        String id = warpName(args[0]);
        if (locationOf(id) == null) {
            plugin.msg(sender, "&cWarp introuvable.");
            return;
        }
        file.get().set("warps." + id, null);
        file.save();
        plugin.msg(sender, "&cWarp &e" + id + " &csupprimé.");
    }

    private void list(CommandSender sender) {
        List<String> names = warpNames();
        plugin.msg(sender, "&6Warps &7» &f" + names.size());
        if (names.isEmpty()) {
            plugin.msg(sender, "&7Aucun warp. &e/setwarp <nom>");
            return;
        }
        plugin.msg(sender, "&e" + join(names));
    }

    private void goWarp(Player player, String[] args) {
        if (args.length < 1) {
            plugin.msg(player, "&e/warp <nom> &7| &e/warps");
            return;
        }
        if (plugin.combat() != null && plugin.combat().denyIfTagged(player)) {
            return;
        }
        if (plugin.denyTpCooldown(player)) {
            return;
        }
        String id = warpName(args[0]);
        Location loc = locationOf(id);
        if (loc == null) {
            plugin.msg(player, "&cWarp &e" + id + " &cintrouvable. &e/warps");
            return;
        }
        if (warmups.containsKey(player.getUniqueId())) {
            plugin.msg(player, "&cUne téléportation est déjà en cours.");
            return;
        }
        int delay = Math.max(0, plugin.getConfig().getInt("warps.teleport-delay-seconds", 5));
        plugin.startTpCooldown(player);
        if (delay <= 0) {
            finishTeleport(player, loc, id);
            return;
        }
        plugin.msg(player, plugin.getConfig().getString("warps.warmup-message",
                "&7Téléportation dans &e{time}s&7. &8Ne bouge pas.")
                .replace("{time}", String.valueOf(delay))
                .replace("{warp}", id));
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
                    finishTeleport(player, loc, id);
                    return;
                }
                ActionBars.send(player, CC.color("&eTéléportation &7» &f" + left[0] + "s"));
            }
        }, 20L, 20L);
        warmups.put(player.getUniqueId(), task);
    }

    private void finishTeleport(Player player, Location loc, String id) {
        plugin.data().setString(player.getUniqueId(), "back_location",
                Locations.serialize(player.getLocation()));
        player.teleport(loc);
        plugin.msg(player, "&aTéléporté au warp &e" + id + "&a.");
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
        if (!plugin.getConfig().getBoolean("warps.cancel-on-move", true)) {
            return;
        }
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        if (start.getBlockX() != to.getBlockX()
                || start.getBlockY() != to.getBlockY()
                || start.getBlockZ() != to.getBlockZ()) {
            cancelWarmup(player, plugin.getConfig().getString("warps.cancel-move-message",
                    "&cTéléportation annulée : tu as bougé."));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        if (!plugin.getConfig().getBoolean("warps.cancel-on-damage", true)) {
            return;
        }
        Player player = (Player) event.getEntity();
        if (warmups.containsKey(player.getUniqueId())) {
            cancelWarmup(player, plugin.getConfig().getString("warps.cancel-damage-message",
                    "&cTéléportation annulée : tu as pris des dégâts."));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancelWarmup(event.getPlayer(), null);
    }

    private Location locationOf(String id) {
        return Locations.deserialize(file.get().getString("warps." + id));
    }

    private List<String> warpNames() {
        List<String> names = new ArrayList<String>();
        ConfigurationSection section = file.get().getConfigurationSection("warps");
        if (section == null) {
            return names;
        }
        names.addAll(section.getKeys(false));
        Collections.sort(names);
        return names;
    }

    private String warpName(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
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
        if (args.length != 1) {
            return Collections.emptyList();
        }
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (!"warp".equals(name) && !"delwarp".equals(name)) {
            return Collections.emptyList();
        }
        if ("delwarp".equals(name) && !isStaff(sender)) {
            return Collections.emptyList();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<String>();
        for (String warp : warpNames()) {
            if (warp.startsWith(prefix)) {
                out.add(warp);
            }
        }
        return out;
    }
}
