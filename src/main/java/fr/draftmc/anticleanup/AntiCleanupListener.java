package fr.draftmc.anticleanup;

import fr.draftmc.Draftmc;
import fr.draftmc.util.CC;
import fr.draftmc.util.NMS;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.projectiles.ProjectileSource;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AntiCleanupListener implements Listener {
    static final String META = "draftmc-protect";

    private final Draftmc plugin;
    private final List<Hologram> holograms = new ArrayList<Hologram>();
    private final Map<UUID, UUID> lastHit = new ConcurrentHashMap<UUID, UUID>();
    private final Map<UUID, String> lastHitName = new ConcurrentHashMap<UUID, String>();
    private final Map<UUID, Long> killerProtectedUntil = new ConcurrentHashMap<UUID, Long>();
    private final List<DropZone> zones = new ArrayList<DropZone>();

    public AntiCleanupListener(Draftmc plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                tickHolograms();
                retagZones();
                prune();
            }
        }, 20L, 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player victim = (Player) event.getEntity();
        Player attacker = attackerOf(event.getDamager());
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        lastHit.put(victim.getUniqueId(), attacker.getUniqueId());
        lastHitName.put(victim.getUniqueId(), attacker.getName());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        if (!plugin.getConfig().getBoolean("anti-cleanup.enabled", true)) {
            return;
        }
        Player victim = event.getEntity();
        LootOwner loot = lootOwnerOf(victim);
        if (loot == null) {
            return;
        }
        int seconds = Math.max(1, plugin.getConfig().getInt("anti-cleanup.duration-seconds", 30));
        long until = System.currentTimeMillis() + seconds * 1000L;
        if (!loot.uuid.equals(victim.getUniqueId())) {
            killerProtectedUntil.put(loot.uuid, until);
        }
        lastHit.remove(victim.getUniqueId());
        lastHitName.remove(victim.getUniqueId());

        Location loc = victim.getLocation();
        DropZone zone = new DropZone(loc, loot.uuid, loot.name, until, seconds);
        zones.add(zone);
        tagNearby(loc, zone);
        spawnHologram(loc, until);

        if (loot.uuid.equals(victim.getUniqueId())) {
            plugin.msg(victim, "&aTon stuff est protégé &7pendant &e" + seconds + "s&7.");
        } else {
            Player ownerPlayer = Bukkit.getPlayer(loot.uuid);
            if (ownerPlayer != null) {
                plugin.msg(ownerPlayer, "&aStuff protégé &7pendant &e" + seconds + "s&7.");
            }
            plugin.msg(victim, "&7Ton stuff est protégé pour &e" + loot.name
                    + " &7pendant &e" + seconds + "s&7.");
        }
        final Location deathLoc = loc.clone();
        final DropZone tagged = zone;
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                tagNearby(deathLoc, tagged);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        DropZone zone = zoneAt(event.getLocation());
        if (zone != null) {
            protectItem(event.getEntity(), zone);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickup(PlayerPickupItemEvent event) {
        if (blockedPickup(event.getPlayer(), event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHopper(InventoryPickupItemEvent event) {
        if (protectedItem(event.getItem())) {
            event.setCancelled(true);
        }
    }

    private boolean blockedPickup(Player player, Item item) {
        if (player.hasPermission("draftmc.admin") || player.hasPermission("draftmc.anticleanup.bypass")) {
            return false;
        }
        ProtectInfo info = protectInfo(item);
        if (info != null && System.currentTimeMillis() <= info.until) {
            return !player.getUniqueId().equals(info.owner);
        }
        DropZone zone = zoneAt(item.getLocation());
        if (zone == null || System.currentTimeMillis() > zone.until) {
            return false;
        }
        if (item.getTicksLived() > zone.seconds * 20 + 40) {
            return false;
        }
        return !player.getUniqueId().equals(zone.owner);
    }

    private boolean protectedItem(Item item) {
        ProtectInfo info = protectInfo(item);
        if (info != null && System.currentTimeMillis() <= info.until) {
            return true;
        }
        DropZone zone = zoneAt(item.getLocation());
        return zone != null && System.currentTimeMillis() <= zone.until
                && item.getTicksLived() <= zone.seconds * 20 + 40;
    }

    private ProtectInfo protectInfo(Item item) {
        if (item == null || !item.hasMetadata(META) || item.getMetadata(META).isEmpty()) {
            return null;
        }
        String raw = item.getMetadata(META).get(0).asString();
        if (raw == null || !raw.contains(":")) {
            return null;
        }
        int split = raw.indexOf(':');
        try {
            UUID owner = UUID.fromString(raw.substring(0, split));
            long until = Long.parseLong(raw.substring(split + 1));
            return new ProtectInfo(owner, until);
        } catch (Exception ignored) {
            return null;
        }
    }

    private LootOwner lootOwnerOf(Player victim) {
        if (plugin.getConfig().getBoolean("anti-cleanup.protect-killer-drops", true)
                && isKillerProtected(victim.getUniqueId())) {
            return new LootOwner(victim.getUniqueId(), victim.getName());
        }
        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim)) {
            return new LootOwner(killer.getUniqueId(), killer.getName());
        }
        UUID last = lastHit.get(victim.getUniqueId());
        if (last == null || last.equals(victim.getUniqueId())) {
            return null;
        }
        String name = lastHitName.get(victim.getUniqueId());
        Player online = Bukkit.getPlayer(last);
        if (online != null) {
            name = online.getName();
        }
        if (name == null) {
            return null;
        }
        return new LootOwner(last, name);
    }

    private boolean isKillerProtected(UUID uuid) {
        Long until = killerProtectedUntil.get(uuid);
        return until != null && until > System.currentTimeMillis();
    }

    private Player attackerOf(Entity entity) {
        if (entity instanceof Player) {
            return (Player) entity;
        }
        if (entity instanceof Projectile) {
            ProjectileSource shooter = ((Projectile) entity).getShooter();
            if (shooter instanceof Player) {
                return (Player) shooter;
            }
        }
        return null;
    }

    private void retagZones() {
        for (int i = 0; i < zones.size(); i++) {
            DropZone zone = zones.get(i);
            if (System.currentTimeMillis() <= zone.until) {
                tagNearby(zone.loc, zone);
            }
        }
    }

    private void tagNearby(Location loc, DropZone zone) {
        if (loc == null || loc.getWorld() == null) {
            return;
        }
        int maxTicks = zone.seconds * 20 + 40;
        for (Entity entity : loc.getWorld().getNearbyEntities(loc, 6.0, 6.0, 6.0)) {
            if (entity instanceof Item && entity.getTicksLived() <= maxTicks) {
                protectItem((Item) entity, zone);
            }
        }
    }

    private void protectItem(Item item, DropZone zone) {
        if (item == null || zone == null) {
            return;
        }
        item.setMetadata(META, new FixedMetadataValue(plugin, zone.owner.toString() + ":" + zone.until));
        NMS.setDroppedItemOwner(item, zone.ownerName);
        if (item.getPickupDelay() < 5) {
            item.setPickupDelay(5);
        }
    }

    private DropZone zoneAt(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        DropZone best = null;
        double bestDist = 36.0;
        for (int i = 0; i < zones.size(); i++) {
            DropZone zone = zones.get(i);
            if (zone.until < now || zone.loc.getWorld() == null
                    || !zone.loc.getWorld().equals(loc.getWorld())) {
                continue;
            }
            double dist = zone.loc.distanceSquared(loc);
            if (dist < bestDist) {
                bestDist = dist;
                best = zone;
            }
        }
        return best;
    }

    private void prune() {
        long now = System.currentTimeMillis();
        Iterator<DropZone> zoneIt = zones.iterator();
        while (zoneIt.hasNext()) {
            if (zoneIt.next().until < now) {
                zoneIt.remove();
            }
        }
        Iterator<Map.Entry<UUID, Long>> prot = killerProtectedUntil.entrySet().iterator();
        while (prot.hasNext()) {
            if (prot.next().getValue() < now) {
                prot.remove();
            }
        }
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
        stand.setMetadata(META, new FixedMetadataValue(plugin, "hologram"));
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

    private static final class LootOwner {
        private final UUID uuid;
        private final String name;

        private LootOwner(UUID uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }
    }

    private static final class ProtectInfo {
        private final UUID owner;
        private final long until;

        private ProtectInfo(UUID owner, long until) {
            this.owner = owner;
            this.until = until;
        }
    }

    private static final class DropZone {
        private final Location loc;
        private final UUID owner;
        private final String ownerName;
        private final long until;
        private final int seconds;

        private DropZone(Location loc, UUID owner, String ownerName, long until, int seconds) {
            this.loc = loc.clone();
            this.owner = owner;
            this.ownerName = ownerName;
            this.until = until;
            this.seconds = seconds;
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
