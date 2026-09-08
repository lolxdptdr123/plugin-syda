package fr.draftmc.events.teamfight;

import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.Potion;
import org.bukkit.potion.PotionType;
import org.bukkit.projectiles.ProjectileSource;

public class TeamFightListener implements Listener {
    private final TeamFightPlugin plugin;

    public TeamFightListener(TeamFightPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean fighting() {
        return plugin.getManager().getState() == TeamFightState.FIGHTING;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!fighting()) {
            return;
        }
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player victim = (Player) event.getEntity();
        Player damager = damagerOf(event);
        if (damager == null) {
            return;
        }
        if (!plugin.getManager().isFighter(damager.getUniqueId())
                || !plugin.getManager().isFighter(victim.getUniqueId())) {
            return;
        }
        if (plugin.getManager().sameTeam(damager, victim)) {
            if (plugin.getConfig().getBoolean("cancel-ally-damage", true)) {
                event.setCancelled(true);
            }
            return;
        }
        plugin.getManager().onHit(damager, victim);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (!fighting()) {
            return;
        }
        Player victim = event.getEntity();
        if (!plugin.getManager().isFighter(victim.getUniqueId())) {
            return;
        }
        if (plugin.getConfig().getBoolean("prevent-item-drop", true)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
        plugin.getManager().onEliminated(victim);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        TeamFightState state = plugin.getManager().getState();
        if (state != TeamFightState.FIGHTING && state != TeamFightState.BETWEEN
                && state != TeamFightState.COUNTDOWN) {
            return;
        }
        Player player = event.getPlayer();
        if (plugin.getManager().teamOf(player.getUniqueId()) == null) {
            return;
        }
        org.bukkit.Location wait = plugin.getManager().readSpawn("spawns.wait");
        if (wait != null) {
            event.setRespawnLocation(wait);
        }
        plugin.getHost().getServer().getScheduler().runTask(plugin.getHost(), new Runnable() {
            @Override
            public void run() {
                plugin.getKit().clear(player);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (!fighting()) {
            return;
        }
        plugin.getManager().onEliminated(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!fighting()) {
            return;
        }
        Player player = event.getPlayer();
        if (!plugin.getManager().isFighter(player.getUniqueId())
                || plugin.getManager().isDeadThisFight(player.getUniqueId())) {
            return;
        }
        if (event.getTo() == null || event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()) {
            return;
        }
        if (!plugin.getManager().insideArena(event.getTo())) {
            event.setTo(event.getFrom());
        }
    }

    @EventHandler
    public void onDrink(PlayerItemConsumeEvent event) {
        if (!fighting()) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.POTION) {
            return;
        }
        plugin.getManager().onPotionUsed(event.getPlayer(), potionName(item));
    }

    @EventHandler
    public void onThrow(ProjectileLaunchEvent event) {
        if (!fighting()) {
            return;
        }
        if (!(event.getEntity() instanceof ThrownPotion)) {
            return;
        }
        ProjectileSource source = event.getEntity().getShooter();
        if (!(source instanceof Player)) {
            return;
        }
        Player player = (Player) source;
        ThrownPotion thrown = (ThrownPotion) event.getEntity();
        plugin.getManager().onPotionUsed(player, potionName(thrown.getItem()));
    }

    private Player damagerOf(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            return (Player) event.getDamager();
        }
        if (event.getDamager() instanceof Arrow) {
            ProjectileSource shooter = ((Arrow) event.getDamager()).getShooter();
            if (shooter instanceof Player) {
                return (Player) shooter;
            }
        }
        if (event.getDamager() instanceof ThrownPotion) {
            ProjectileSource shooter = ((ThrownPotion) event.getDamager()).getShooter();
            if (shooter instanceof Player) {
                return (Player) shooter;
            }
        }
        return null;
    }

    private String potionName(ItemStack item) {
        if (item == null) {
            return "Potion";
        }
        try {
            Potion potion = Potion.fromItemStack(item);
            PotionType type = potion.getType();
            if (type == PotionType.INSTANT_HEAL) {
                return "Potion de soin";
            }
            if (type == PotionType.STRENGTH) {
                return "Potion de force";
            }
            if (type == PotionType.SPEED) {
                return "Potion de speed";
            }
            if (type == PotionType.REGEN) {
                return "Potion de regen";
            }
            if (type == PotionType.FIRE_RESISTANCE) {
                return "Potion de fire res";
            }
            return type.name().toLowerCase().replace('_', ' ');
        } catch (Exception ignored) {
            return "Potion";
        }
    }
}
