package fr.draftmc.anticleanup;

import fr.draftmc.Draftmc;
import fr.draftmc.util.CC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class AntiCleanupListener implements Listener {
    private final Draftmc plugin;
    private final List<Hologram> holograms = new ArrayList<Hologram>();

    public AntiCleanupListener(Draftmc plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                tickHolograms();
            }
        }, 20L, 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        if (!plugin.getConfig().getBoolean("anti-cleanup.enabled", true)) {
            return;
        }
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) {
            return;
        }
        final UUID owner = killer.getUniqueId();
        int seconds = plugin.getConfig().getInt("anti-cleanup.duration-seconds", 30);
        long until = System.currentTimeMillis() + seconds * 1000L;
        List<ItemStack> drops = new ArrayList<ItemStack>(event.getDrops());
        event.getDrops().clear();
        for (ItemStack stack : drops) {
            if (stack == null) {
                continue;
            }
            Item item = victim.getWorld().dropItemNaturally(victim.getLocation(), stack);
            item.setMetadata("draftmc-protect", new FixedMetadataValue(plugin, owner.toString() + ":" + until));
            item.setPickupDelay(5);
        }
        spawnHologram(victim.getLocation(), until);
        plugin.msg(killer, "&aStuff protégé &7pendant &e" + seconds + "s&7.");
        plugin.msg(victim, "&7Ton stuff est protégé pour &e" + killer.getName() + " &7pendant &e" + seconds + "s&7.");
    }

    private void spawnHologram(Location deathLoc, long until) {
        if (!plugin.getConfig().getBoolean("anti-cleanup.hologram", true)) {
            return;
        }
        if (deathLoc.getWorld() == null) {
            return;
        }
        double height = plugin.getConfig().getDouble("anti-cleanup.hologram-height", 1.8);
        Location loc = deathLoc.clone().add(0.0, height, 0.0);
        ArmorStand stand = deathLoc.getWorld().spawn(loc, ArmorStand.class);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setBasePlate(false);
        stand.setArms(false);
        stand.setSmall(true);
        stand.setCanPickupItems(false);
        stand.setCustomNameVisible(true);
        try {
            stand.setMarker(true);
        } catch (Throwable ignored) {
        }
        stand.setCustomName(hologramText(secondsLeft(until)));
        holograms.add(new Hologram(stand, until));
    }

    private void tickHolograms() {
        if (holograms.isEmpty()) {
            return;
        }
        Iterator<Hologram> it = holograms.iterator();
        while (it.hasNext()) {
            Hologram holo = it.next();
            int left = secondsLeft(holo.until);
            if (left <= 0 || holo.stand == null || !holo.stand.isValid()) {
                if (holo.stand != null && holo.stand.isValid()) {
                    holo.stand.remove();
                }
                it.remove();
                continue;
            }
            holo.stand.setCustomName(hologramText(left));
        }
    }

    private String hologramText(int seconds) {
        return CC.color(plugin.getConfig().getString("anti-cleanup.hologram-text",
                "&cAnti-clean &7» &e{time}s").replace("{time}", String.valueOf(seconds)));
    }

    private int secondsLeft(long until) {
        long left = until - System.currentTimeMillis();
        if (left <= 0) {
            return 0;
        }
        return (int) Math.ceil(left / 1000.0);
    }

    @EventHandler
    public void onPickup(PlayerPickupItemEvent event) {
        Item item = event.getItem();
        if (!item.hasMetadata("draftmc-protect")) {
            return;
        }
        String raw = item.getMetadata("draftmc-protect").get(0).asString();
        String[] p = raw.split(":");
        UUID owner = UUID.fromString(p[0]);
        long until = Long.parseLong(p[1]);
        Player player = event.getPlayer();
        if (System.currentTimeMillis() > until) {
            return;
        }
        if (!player.getUniqueId().equals(owner) && !player.hasPermission("draftmc.admin")) {
            event.setCancelled(true);
        }
    }

    private static final class Hologram {
        private final ArmorStand stand;
        private final long until;

        private Hologram(ArmorStand stand, long until) {
            this.stand = stand;
            this.until = until;
        }
    }
}
