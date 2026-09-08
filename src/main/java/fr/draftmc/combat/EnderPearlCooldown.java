package fr.draftmc.combat;

import fr.draftmc.Draftmc;
import fr.draftmc.util.Cooldowns;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EnderPearlCooldown implements Listener {
    private final Draftmc plugin;
    private final Map<UUID, Long> lastWarn = new ConcurrentHashMap<UUID, Long>();

    public EnderPearlCooldown(Draftmc plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (!enabled()) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (!isPearl(event.getItem()) && !isPearl(player.getItemInHand())) {
            return;
        }
        if (player.hasPermission("draftmc.enderpearl.bypass")) {
            return;
        }
        int left = Cooldowns.remaining(player, "enderpearl");
        if (left <= 0) {
            return;
        }
        event.setCancelled(true);
        player.updateInventory();
        warn(player, left);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!enabled() || !(event.getEntity() instanceof EnderPearl)) {
            return;
        }
        if (!(event.getEntity().getShooter() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity().getShooter();
        if (player.hasPermission("draftmc.enderpearl.bypass")) {
            return;
        }
        int seconds = Math.max(1, plugin.getConfig().getInt("enderpearl.cooldown-seconds", 15));
        if (Cooldowns.ready(player, "enderpearl", seconds)) {
            return;
        }
        event.setCancelled(true);
        refundPearl(player);
        warn(player, Cooldowns.remaining(player, "enderpearl"));
    }

    private void refundPearl(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            player.updateInventory();
            return;
        }
        ItemStack hand = player.getItemInHand();
        if (hand != null && hand.getType() == Material.ENDER_PEARL) {
            hand.setAmount(hand.getAmount() + 1);
            player.setItemInHand(hand);
        } else {
            player.getInventory().addItem(new ItemStack(Material.ENDER_PEARL, 1));
        }
        player.updateInventory();
    }

    private void warn(Player player, int left) {
        Long last = lastWarn.get(player.getUniqueId());
        long now = System.currentTimeMillis();
        if (last != null && now - last < 250L) {
            return;
        }
        lastWarn.put(player.getUniqueId(), now);
        plugin.msg(player, message(left));
    }

    private boolean isPearl(ItemStack item) {
        return item != null && item.getType() == Material.ENDER_PEARL;
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("enderpearl.enabled", true);
    }

    private String message(int left) {
        return plugin.getConfig().getString("enderpearl.message",
                "&cEnderpearl en cooldown : &e{time}s").replace("{time}", String.valueOf(left));
    }
}
