package fr.sydaria.anticleanup;

import fr.sydaria.Sydaria;
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
import java.util.List;
import java.util.UUID;

public class AntiCleanupListener implements Listener {
    private final Sydaria plugin;

    public AntiCleanupListener(Sydaria plugin) {
        this.plugin = plugin;
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
        List<ItemStack> drops = new ArrayList<ItemStack>(event.getDrops());
        event.getDrops().clear();
        for (ItemStack stack : drops) {
            if (stack == null) {
                continue;
            }
            Item item = victim.getWorld().dropItemNaturally(victim.getLocation(), stack);
            item.setMetadata("sydaria-protect", new FixedMetadataValue(plugin, owner.toString() + ":" + (System.currentTimeMillis() + seconds * 1000L)));
            item.setPickupDelay(5);
        }
        plugin.msg(killer, "&aStuff protégé &7pendant &e" + seconds + "s&7.");
        plugin.msg(victim, "&7Ton stuff est protégé pour &e" + killer.getName() + " &7pendant &e" + seconds + "s&7.");
    }

    @EventHandler
    public void onPickup(PlayerPickupItemEvent event) {
        Item item = event.getItem();
        if (!item.hasMetadata("sydaria-protect")) {
            return;
        }
        String raw = item.getMetadata("sydaria-protect").get(0).asString();
        String[] p = raw.split(":");
        UUID owner = UUID.fromString(p[0]);
        long until = Long.parseLong(p[1]);
        Player player = event.getPlayer();
        if (System.currentTimeMillis() > until) {
            return;
        }
        if (!player.getUniqueId().equals(owner) && !player.hasPermission("sydaria.admin")) {
            event.setCancelled(true);
        }
    }
}
