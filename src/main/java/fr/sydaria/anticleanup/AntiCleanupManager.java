package fr.sydaria.anticleanup;

import fr.sydaria.Sydaria;
import fr.sydaria.util.Chat;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Après un kill, si le tueur meurt, son loot n'est ramassable que par lui pendant X secondes.
 */
public final class AntiCleanupManager implements Listener {

    private static final String META = "sydaria_protect";
    private final Sydaria plugin;
    private final Map<UUID, Long> protectedUntil = new HashMap<UUID, Long>();

    public AntiCleanupManager(Sydaria plugin) {
        this.plugin = plugin;
    }

    public void protect(Player killer) {
        int sec = plugin.getConfig().getInt("anti-cleanup.duration-seconds", 30);
        protectedUntil.put(killer.getUniqueId(), System.currentTimeMillis() + sec * 1000L);
        String msg = plugin.getConfig().getString("anti-cleanup.message", "")
                .replace("{time}", String.valueOf(sec));
        plugin.msg(killer, msg);
    }

    public boolean isProtected(UUID uuid) {
        Long until = protectedUntil.get(uuid);
        return until != null && until > System.currentTimeMillis();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim)) {
            protect(killer);
        }
        if (isProtected(victim.getUniqueId()) && !victim.hasPermission("sydaria.anticleanup.bypass")) {
            victim.setMetadata(META, new FixedMetadataValue(plugin, victim.getUniqueId().toString()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeathDrops(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (!isProtected(victim.getUniqueId())) {
            return;
        }
        event.getDrops(); // conservé, tagged after spawn
        plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                for (org.bukkit.entity.Entity nearby : victim.getWorld().getNearbyEntities(victim.getLocation(), 6, 6, 6)) {
                    if (nearby instanceof Item) {
                        Item item = (Item) nearby;
                        item.setMetadata(META, new FixedMetadataValue(plugin, victim.getUniqueId().toString()));
                    }
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(PlayerPickupItemEvent event) {
        Item item = event.getItem();
        if (!item.hasMetadata(META)) {
            return;
        }
        String owner = item.getMetadata(META).get(0).asString();
        if (!event.getPlayer().getUniqueId().toString().equals(owner)
                && !event.getPlayer().hasPermission("sydaria.anticleanup.bypass")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Chat.color("&cCe stuff est protégé (anti-cleanup)."));
        }
    }

    public void tickCleanup() {
        Iterator<Map.Entry<UUID, Long>> it = protectedUntil.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue() < System.currentTimeMillis()) {
                it.remove();
            }
        }
    }
}
