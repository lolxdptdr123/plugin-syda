package fr.draftmc.events.totem;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Iterator;

public class TotemListener implements Listener {
    private final TotemPlugin plugin;

    public TotemListener(TotemPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Totem totem = plugin.getTotemManager().findByBlock(event.getBlock());
        if (totem == null || totem.getStatus() != TotemStatus.STARTED) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!plugin.getEventFactionHook().hasFaction(player)) {
            player.sendMessage(ChatColor.RED + "Tu dois etre dans une faction pour casser le totem.");
            return;
        }
        if (!holdingRequiredItem(player, totem.getItemInteract())) {
            player.sendMessage(ChatColor.RED + "Casse le totem avec : " + totem.getItemInteract().name() + ".");
            return;
        }
        totem.playerBreak(plugin, player, event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (plugin.getTotemManager().findByBlock(event.getBlock()) != null
                || plugin.getTotemManager().findByBlock(event.getBlockAgainst()) != null) {
            Totem totem = plugin.getTotemManager().findByBlock(event.getBlock());
            if (totem == null) {
                totem = plugin.getTotemManager().findByBlock(event.getBlockAgainst());
            }
            if (totem != null && totem.getStatus() == TotemStatus.STARTED) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        Iterator<Block> it = event.blockList().iterator();
        while (it.hasNext()) {
            Totem totem = plugin.getTotemManager().findByBlock(it.next());
            if (totem != null && totem.getStatus() == TotemStatus.STARTED) {
                it.remove();
            }
        }
    }

    private boolean holdingRequiredItem(Player player, Material required) {
        if (required == null) {
            return true;
        }
        ItemStack hand = player.getItemInHand();
        return hand != null && hand.getType() == required;
    }
}
