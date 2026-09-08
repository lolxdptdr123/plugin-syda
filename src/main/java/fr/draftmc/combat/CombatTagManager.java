package fr.draftmc.combat;

import fr.draftmc.Draftmc;
import fr.draftmc.util.ActionBars;
import fr.draftmc.util.CC;
import org.bukkit.Bukkit;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Combat tag Factions : tag PvP, blocage des TP, combat-log.
 */
public class CombatTagManager implements Listener {
    private static final List<String> ALWAYS_BLOCKED = Arrays.asList(
            "home", "tpa", "tpahere", "tpyes", "tpaccept", "tpdeny", "back",
            "randomtp", "rtp", "spawn", "warp", "warps", "setwarp", "delwarp", "tp", "fly",
            "refill", "shop", "boutique", "magasin", "kit", "kits",
            "bin", "poubelle", "trash", "ec", "pv", "invsee",
            "hat", "hdv", "ah", "auction", "rankup", "grade", "grades", "gradecmds",
            "sell", "sellall", "repair", "repairall"
    );
    private static final List<String> ALWAYS_BLOCKED_SUB = Arrays.asList(
            "f home", "f sethome", "f fly", "repair all", "sell all"
    );

    private final Draftmc plugin;
    private final Map<UUID, Long> taggedUntil = new ConcurrentHashMap<UUID, Long>();
    private BukkitTask tickTask;

    public CombatTagManager(Draftmc plugin) {
        this.plugin = plugin;
        this.tickTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                tick();
            }
        }, 20L, 20L);
    }

    public void disable() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        taggedUntil.clear();
    }

    public boolean isTagged(Player player) {
        return player != null && remainingSeconds(player) > 0;
    }

    public int remainingSeconds(Player player) {
        Long until = taggedUntil.get(player.getUniqueId());
        if (until == null) {
            return 0;
        }
        long left = until - System.currentTimeMillis();
        if (left <= 0) {
            taggedUntil.remove(player.getUniqueId());
            return 0;
        }
        return (int) Math.ceil(left / 1000.0);
    }

    /**
     * @return true si la commande / téléportation doit être refusée
     */
    public boolean denyIfTagged(Player player) {
        if (!enabled() || !isTagged(player) || player.hasPermission("draftmc.combattag.bypass")) {
            return false;
        }
        int left = remainingSeconds(player);
        plugin.msg(player, plugin.getConfig().getString("combat-tag.message",
                "&cTu es en combat encore &e{time}s&c.").replace("{time}", String.valueOf(left)));
        return true;
    }

    public void tag(Player player) {
        if (!enabled() || player == null || !player.isOnline()) {
            return;
        }
        if (player.hasPermission("draftmc.combattag.bypass")) {
            return;
        }
        if (plugin.staff() != null && plugin.staff().isStaff(player)) {
            return;
        }
        boolean wasTagged = isTagged(player);
        int duration = Math.max(1, plugin.getConfig().getInt("combat-tag.duration-seconds", 15));
        taggedUntil.put(player.getUniqueId(), System.currentTimeMillis() + duration * 1000L);
        if (!wasTagged) {
            plugin.msg(player, plugin.getConfig().getString("combat-tag.tagged-message",
                    "&cTu es en combat pendant &e{time}s&c.").replace("{time}", String.valueOf(duration)));
            if (plugin.homes() != null) {
                plugin.homes().cancelWarmup(player, "&cTéléportation annulée : tu es en combat.");
            }
            if (plugin.warps() != null) {
                plugin.warps().cancelWarmup(player, "&cTéléportation annulée : tu es en combat.");
            }
            if (plugin.tpa() != null) {
                plugin.tpa().cancelFor(player, true);
            }
        }
    }

    public void untag(UUID uuid, boolean notify) {
        if (taggedUntil.remove(uuid) == null) {
            return;
        }
        if (!notify) {
            return;
        }
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            plugin.msg(player, plugin.getConfig().getString("combat-tag.untagged-message",
                    "&aTu n'es plus en combat."));
        }
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("combat-tag.enabled", true);
    }

    private void tick() {
        if (!enabled() || taggedUntil.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> it = taggedUntil.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (entry.getValue() <= now) {
                it.remove();
                if (player != null && player.isOnline()) {
                    plugin.msg(player, plugin.getConfig().getString("combat-tag.untagged-message",
                            "&aTu n'es plus en combat."));
                }
                continue;
            }
            if (player == null || !player.isOnline()) {
                continue;
            }
            if (plugin.freeze() != null && plugin.freeze().isFrozen(player)) {
                continue;
            }
            if (plugin.getConfig().getBoolean("combat-tag.actionbar", true)) {
                int left = (int) Math.ceil((entry.getValue() - now) / 1000.0);
                ActionBars.send(player, CC.color("&cCombat &7» &e" + left + "s"));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!enabled() || !(event.getEntity() instanceof Player)) {
            return;
        }
        Entity damager = event.getDamager();
        boolean pearlOrArrow = damager instanceof Arrow || damager instanceof EnderPearl;
        if (event.isCancelled() && !pearlOrArrow) {
            return;
        }
        Player victim = (Player) event.getEntity();
        Player attacker = attackerOf(damager);
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        if (plugin.getConfig().getBoolean("combat-tag.ignore-faction", true)
                && plugin.factions() != null && plugin.factions().sameFaction(attacker, victim)) {
            return;
        }
        tag(attacker);
        tag(victim);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPearlThrow(ProjectileLaunchEvent event) {
        if (!enabled() || !(event.getEntity() instanceof EnderPearl)) {
            return;
        }
        if (!(event.getEntity().getShooter() instanceof Player)) {
            return;
        }
        tag((Player) event.getEntity().getShooter());
    }

    private Player attackerOf(Entity entity) {
        if (entity instanceof Player) {
            return (Player) entity;
        }
        if (entity instanceof Projectile) {
            Object shooter = ((Projectile) entity).getShooter();
            if (shooter instanceof Player) {
                return (Player) shooter;
            }
        }
        if (plugin.factions() != null) {
            return plugin.factions().damager(entity);
        }
        return null;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        untag(event.getEntity().getUniqueId(), false);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!enabled() || !isTagged(player)) {
            taggedUntil.remove(player.getUniqueId());
            return;
        }
        taggedUntil.remove(player.getUniqueId());
        if (!plugin.getConfig().getBoolean("combat-tag.combat-log", true)) {
            return;
        }
        if (player.hasPermission("draftmc.combattag.bypass")) {
            return;
        }
        player.setHealth(0.0);
        String broadcast = plugin.getConfig().getString("combat-tag.log-broadcast",
                "&c{player} s'est déconnecté en combat.");
        Bukkit.broadcastMessage(CC.color(plugin.prefix() + broadcast.replace("{player}", player.getName())));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!enabled() || !isTagged(player) || player.hasPermission("draftmc.combattag.bypass")) {
            return;
        }
        String raw = event.getMessage().substring(1).trim();
        if (raw.isEmpty()) {
            return;
        }
        String[] parts = raw.split("\\s+");
        String cmd = parts[0].toLowerCase(Locale.ROOT);
        int colon = cmd.indexOf(':');
        if (colon >= 0 && colon + 1 < cmd.length()) {
            cmd = cmd.substring(colon + 1);
        }
        if (!isBlockedCommand(cmd, parts)) {
            return;
        }
        event.setCancelled(true);
        denyIfTagged(player);
    }

    private boolean isBlockedCommand(String cmd, String[] parts) {
        if (matchesAny(cmd, plugin.getConfig().getStringList("combat-tag.blocked-commands"))
                || matchesAny(cmd, ALWAYS_BLOCKED)) {
            return true;
        }
        List<String> subs = plugin.getConfig().getStringList("combat-tag.blocked-subcommands");
        if (matchesSubcommand(cmd, parts, subs) || matchesSubcommand(cmd, parts, ALWAYS_BLOCKED_SUB)) {
            return true;
        }
        return false;
    }

    private boolean matchesAny(String cmd, List<String> blocked) {
        for (String entry : blocked) {
            if (entry != null && cmd.equalsIgnoreCase(entry.trim())) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesSubcommand(String cmd, String[] parts, List<String> subs) {
        for (String entry : subs) {
            if (entry == null || entry.trim().isEmpty()) {
                continue;
            }
            String[] want = entry.trim().toLowerCase(Locale.ROOT).split("\\s+");
            if (want.length < 2 || !cmd.equals(want[0])) {
                continue;
            }
            boolean match = true;
            for (int i = 1; i < want.length; i++) {
                if (parts.length <= i || !parts[i].equalsIgnoreCase(want[i])) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return true;
            }
        }
        return false;
    }
}
