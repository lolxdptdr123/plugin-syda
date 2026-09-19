package fr.draftmc.core;

import fr.draftmc.Draftmc;
import fr.draftmc.util.ActionBars;
import fr.draftmc.util.CC;
import fr.draftmc.util.Locations;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Délai de 5s (configurable) sur chaque téléportation joueur.
 * Annulé si le joueur bouge ou prend des dégâts.
 */
public class TeleportWarmup implements Listener {
    private final Draftmc plugin;
    private final Map<UUID, Integer> tasks = new HashMap<UUID, Integer>();
    private final Map<UUID, Location> starts = new HashMap<UUID, Location>();

    public TeleportWarmup(Draftmc plugin) {
        this.plugin = plugin;
    }

    public boolean pending(Player player) {
        return tasks.containsKey(player.getUniqueId());
    }

    public boolean request(Player player, Location dest, String successMessage) {
        return request(player, dest, successMessage, null);
    }

    public boolean request(Player player, Location dest, String successMessage, Runnable after) {
        if (player == null || dest == null || dest.getWorld() == null) {
            return false;
        }
        if (plugin.combat() != null && plugin.combat().denyIfTagged(player)) {
            return false;
        }
        if (plugin.denyTpCooldown(player)) {
            return false;
        }
        if (pending(player)) {
            plugin.msg(player, "&cUne téléportation est déjà en cours.");
            return false;
        }
        int delay = Math.max(0, plugin.getConfig().getInt("teleport.delay-seconds", 5));
        plugin.startTpCooldown(player);
        if (delay <= 0) {
            finish(player, dest, successMessage, after);
            return true;
        }
        plugin.msg(player, plugin.getConfig().getString("teleport.warmup-message",
                "&7Téléportation dans &e{time}s&7. &8Ne bouge pas.")
                .replace("{time}", String.valueOf(delay)));
        starts.put(player.getUniqueId(), player.getLocation().clone());
        final int[] left = {delay};
        int task = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel(player, null);
                    return;
                }
                if (plugin.combat() != null && plugin.combat().isTagged(player)) {
                    cancel(player, "&cTéléportation annulée : tu es en combat.");
                    return;
                }
                left[0]--;
                if (left[0] <= 0) {
                    cancel(player, null);
                    finish(player, dest, successMessage, after);
                    return;
                }
                ActionBars.send(player, CC.color("&eTéléportation &7» &f" + left[0] + "s"));
            }
        }, 20L, 20L);
        tasks.put(player.getUniqueId(), task);
        return true;
    }

    public void cancel(Player player, String message) {
        Integer task = tasks.remove(player.getUniqueId());
        starts.remove(player.getUniqueId());
        if (task == null) {
            return;
        }
        Bukkit.getScheduler().cancelTask(task);
        if (message != null && player.isOnline()) {
            plugin.msg(player, message);
        }
    }

    private void finish(Player player, Location dest, String successMessage, Runnable after) {
        plugin.data().setString(player.getUniqueId(), "back_location",
                Locations.serialize(player.getLocation()));
        player.teleport(dest);
        if (successMessage != null && !successMessage.isEmpty()) {
            plugin.msg(player, successMessage);
        }
        if (after != null) {
            after.run();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location start = starts.get(player.getUniqueId());
        if (start == null || !plugin.getConfig().getBoolean("teleport.cancel-on-move", true)) {
            return;
        }
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        if (start.getBlockX() != to.getBlockX()
                || start.getBlockY() != to.getBlockY()
                || start.getBlockZ() != to.getBlockZ()) {
            cancel(player, plugin.getConfig().getString("teleport.cancel-move-message",
                    "&cTéléportation annulée : tu as bougé."));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        if (!plugin.getConfig().getBoolean("teleport.cancel-on-damage", true)) {
            return;
        }
        Player player = (Player) event.getEntity();
        if (pending(player)) {
            cancel(player, plugin.getConfig().getString("teleport.cancel-damage-message",
                    "&cTéléportation annulée : tu as pris des dégâts."));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancel(event.getPlayer(), null);
    }
}
