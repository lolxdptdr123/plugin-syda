package fr.draftmc.tournament;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.Locale;
import java.util.UUID;

public class TournamentListener implements Listener {
    private final TournamentManager manager;

    public TournamentListener(TournamentManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!manager.running()) {
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
        TournamentMatch match = manager.matchOf(victim.getUniqueId());
        if (match == null || !match.hasPlayer(damager.getUniqueId())) {
            if (manager.matchOf(damager.getUniqueId()) != null || match != null) {
                event.setCancelled(true);
            }
            return;
        }
        if (!match.fighting() || match.dead().contains(victim.getUniqueId())
                || match.dead().contains(damager.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (damager.equals(victim)) {
            return;
        }
        TournamentTeam a = match.teamOf(damager.getUniqueId());
        TournamentTeam b = match.teamOf(victim.getUniqueId());
        if (a != null && a == b && manager.config().getBoolean("cancel-ally-damage", true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (!manager.running() || manager.matchOf(event.getEntity().getUniqueId()) == null) {
            return;
        }
        if (manager.config().getBoolean("prevent-item-drop", true)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
        manager.onEliminated(event.getEntity());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Location dest = manager.deathRespawnLocation(event.getPlayer());
        if (dest != null) {
            event.setRespawnLocation(dest);
        }
        manager.afterDeathRespawn(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.cancelCreate(event.getPlayer().getUniqueId());
        if (manager.matchOf(event.getPlayer().getUniqueId()) != null) {
            manager.onEliminated(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCreateChat(AsyncPlayerChatEvent event) {
        if (!manager.hasCreateDraft(event.getPlayer().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        final Player player = event.getPlayer();
        final String message = event.getMessage();
        Bukkit.getScheduler().runTask(manager.plugin(), new Runnable() {
            @Override
            public void run() {
                manager.handleCreateChat(player, message);
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!manager.config().getBoolean("freeze-countdown", true)) {
            return;
        }
        Player player = event.getPlayer();
        TournamentMatch match = manager.matchOf(player.getUniqueId());
        if (match == null || match.fighting() || match.dead().contains(player.getUniqueId())) {
            return;
        }
        if (event.getTo() == null) {
            return;
        }
        if (event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            event.setTo(event.getFrom());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (manager.isFighter(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player
                && manager.isFighter(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
            ((Player) event.getEntity()).setFoodLevel(20);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (manager.matchOf(uuid) == null) {
            return;
        }
        String label = event.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        if (label.equals("status") || label.equals("tournament") || label.equals("tournoi")) {
            return;
        }
        if (event.getPlayer().hasPermission("draftmc.tournament.admin")
                || event.getPlayer().hasPermission("draftmc.admin")) {
            return;
        }
        event.setCancelled(true);
        manager.plugin().msg(event.getPlayer(), "&cCommandes bloquées pendant le tournoi. &e/status");
    }

    private Player damagerOf(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            return (Player) event.getDamager();
        }
        if (event.getDamager() instanceof Projectile) {
            ProjectileSource shooter = ((Projectile) event.getDamager()).getShooter();
            if (shooter instanceof Player) {
                return (Player) shooter;
            }
        }
        return null;
    }
}
