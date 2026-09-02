package fr.sydaria.factions;

import fr.sydaria.Sydaria;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class FactionListener implements Listener {
    private final Sydaria plugin;
    private final FactionManager factions;

    public FactionListener(Sydaria plugin, FactionManager factions) {
        this.plugin = plugin;
        this.factions = factions;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        factions.ensurePower(event.getPlayer());
        factions.sendMotd(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        factions.clearChat(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        factions.onDeath(event.getEntity());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!factions.canBuild(event.getPlayer(), event.getBlock().getLocation(), FactionPerm.BREAK)) {
            factions.deny(event.getPlayer(), event.getBlock().getLocation());
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!factions.canBuild(event.getPlayer(), event.getBlock().getLocation(), FactionPerm.BUILD)) {
            factions.deny(event.getPlayer(), event.getBlock().getLocation());
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!factions.canBuild(event.getPlayer(), event.getBlockClicked().getLocation(), FactionPerm.BUILD)) {
            factions.deny(event.getPlayer(), event.getBlockClicked().getLocation());
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (!factions.canBuild(event.getPlayer(), event.getBlockClicked().getLocation(), FactionPerm.BREAK)) {
            factions.deny(event.getPlayer(), event.getBlockClicked().getLocation());
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.PHYSICAL) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        Material type = block.getType();
        FactionPerm perm = interactPerm(type);
        if (perm == null) {
            return;
        }
        if (!factions.canBuild(event.getPlayer(), block.getLocation(), perm)) {
            factions.deny(event.getPlayer(), block.getLocation());
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        java.util.Iterator<Block> it = event.blockList().iterator();
        while (it.hasNext()) {
            Block block = it.next();
            if (factions.claimAt(block.getLocation()) != null) {
                it.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player victim = (Player) event.getEntity();
        Player damager = factions.damager(event.getDamager());
        if (damager == null) {
            return;
        }
        if (!plugin.getConfig().getBoolean("factions.friendly-fire", false)
                && factions.sameFaction(damager, victim)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        factions.handleChat(event);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        if (event.getFrom().getChunk().getX() == event.getTo().getChunk().getX()
                && event.getFrom().getChunk().getZ() == event.getTo().getChunk().getZ()
                && event.getFrom().getWorld() == event.getTo().getWorld()) {
            return;
        }
        factions.onChunkChange(event.getPlayer(), event.getTo());
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() != null) {
            factions.onChunkChange(event.getPlayer(), event.getTo());
        }
    }

    private FactionPerm interactPerm(Material type) {
        String name = type.name();
        if (name.contains("CHEST") || name.contains("FURNACE") || name.equals("DISPENSER")
                || name.equals("DROPPER") || name.equals("HOPPER") || name.equals("BREWING_STAND")
                || name.equals("ANVIL") || name.equals("BEACON") || name.equals("JUKEBOX")
                || name.equals("TRAPPED_CHEST")) {
            return FactionPerm.CONTAINER;
        }
        if (name.contains("DOOR") || name.contains("GATE") || name.contains("TRAP_DOOR") || name.equals("TRAP_DOOR")) {
            return FactionPerm.DOOR;
        }
        if (name.contains("BUTTON") || name.equals("WOOD_BUTTON") || name.equals("STONE_BUTTON")) {
            return FactionPerm.BUTTON;
        }
        if (name.equals("LEVER") || name.contains("PLATE")) {
            return FactionPerm.LEVER;
        }
        return null;
    }
}
