package fr.draftmc.combat;

import fr.draftmc.Draftmc;
import fr.draftmc.util.ActionBars;
import fr.draftmc.util.CC;
import fr.draftmc.util.NMS;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Combat tag Factions : tag PvP, blocage des TP, combat-log.
 */
public class CombatTagManager implements Listener, CommandExecutor {
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
    private final Set<UUID> dropInventoryOnRespawn = new HashSet<UUID>();
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

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cJoueur uniquement.");
            return true;
        }
        Player player = (Player) sender;
        if (!enabled()) {
            plugin.msg(player, "&cCombat tag désactivé.");
            return true;
        }
        int left = remainingSeconds(player);
        if (left <= 0) {
            plugin.msg(player, plugin.getConfig().getString("combat-tag.ct-not-tagged",
                    "&aTu n'es pas en combat."));
            return true;
        }
        plugin.msg(player, plugin.getConfig().getString("combat-tag.ct-message",
                "&cTu es en combat encore &e{time}s&c.").replace("{time}", String.valueOf(left)));
        return true;
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
        if (!enabled() || !isTagged(player) || commandBypass(player)) {
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
        if (player.isDead() || player.getHealth() <= 0.0) {
            return;
        }
        if (inStaffMode(player)) {
            return;
        }
        boolean wasTagged = isTagged(player);
        int duration = Math.max(1, plugin.getConfig().getInt("combat-tag.duration-seconds", 25));
        taggedUntil.put(player.getUniqueId(), System.currentTimeMillis() + duration * 1000L);
        if (!wasTagged) {
            plugin.msg(player, plugin.getConfig().getString("combat-tag.tagged-message",
                    "&cTu es en combat pendant &e{time}s&c.").replace("{time}", String.valueOf(duration)));
            if (plugin.teleports() != null) {
                plugin.teleports().cancel(player, "&cTéléportation annulée : tu es en combat.");
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

    private boolean inStaffMode(Player player) {
        return plugin.staff() != null && plugin.staff().isStaff(player);
    }

    private boolean commandBypass(Player player) {
        return inStaffMode(player) || player.hasPermission("draftmc.combattag.bypass");
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

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMeleeSyncHand(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) {
            return;
        }
        Player attacker = (Player) event.getDamager();
        ItemStack hand = attacker.getItemInHand();
        if (hand == null || hand.getType() == Material.AIR) {
            NMS.resetAttackDamage(attacker);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!enabled() || !(event.getEntity() instanceof Player)) {
            return;
        }
        Player victim = (Player) event.getEntity();
        if (victim.isDead() || victim.getHealth() <= 0.0) {
            return;
        }
        Player attacker = attackerOf(event.getDamager());
        if (attacker == null) {
            return;
        }
        if (event.isCancelled()) {
            return;
        }
        boolean projectile = event.getDamager() instanceof Projectile;
        if (event.getDamager() instanceof ThrownPotion) {
            return;
        }
        if (attacker.equals(victim)) {
            if (projectile) {
                tag(victim);
            }
            return;
        }
        tagPair(attacker, victim);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        Player shooter = shooterOf(projectile);
        if (shooter != null) {
            projectile.setMetadata("draftmc-shooter",
                    new FixedMetadataValue(plugin, shooter.getUniqueId().toString()));
        }
        if (!enabled() || shooter == null) {
            return;
        }
        if (projectile instanceof EnderPearl) {
            tag(shooter);
        }
    }

    private void tagPair(Player attacker, Player victim) {
        if (plugin.getConfig().getBoolean("combat-tag.ignore-faction", true)
                && plugin.factions() != null && plugin.factions().sameFaction(attacker, victim)) {
            return;
        }
        tag(attacker);
        tag(victim);
    }

    private Player attackerOf(Entity entity) {
        if (entity instanceof Player) {
            return (Player) entity;
        }
        if (entity instanceof Projectile) {
            Player shooter = shooterOf((Projectile) entity);
            if (shooter != null) {
                return shooter;
            }
        }
        if (plugin.factions() != null) {
            return plugin.factions().damager(entity);
        }
        return null;
    }

    private Player shooterOf(Projectile projectile) {
        ProjectileSource source = projectile.getShooter();
        if (source instanceof Player) {
            return (Player) source;
        }
        if (projectile.hasMetadata("draftmc-shooter") && !projectile.getMetadata("draftmc-shooter").isEmpty()) {
            try {
                UUID id = UUID.fromString(projectile.getMetadata("draftmc-shooter").get(0).asString());
                return Bukkit.getPlayer(id);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        final Player player = event.getEntity();
        untag(player.getUniqueId(), false);
        if (event.getKeepInventory()) {
            return;
        }
        dropInventoryOnRespawn.add(player.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    return;
                }
                wipeDroppedGear(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        final Player player = event.getPlayer();
        untag(player.getUniqueId(), false);
        if (dropInventoryOnRespawn.remove(player.getUniqueId())) {
            wipeDroppedGear(player);
        }
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    return;
                }
                untag(player.getUniqueId(), false);
                ItemStack hand = player.getItemInHand();
                if (hand == null || hand.getType() == Material.AIR) {
                    NMS.resetAttackDamage(player);
                }
                player.updateInventory();
            }
        });
    }

    /** L'épée a déjà été mise dans les drops. On enlève la copie restée côté serveur. */
    private void wipeDroppedGear(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.setItemInHand(new ItemStack(Material.AIR));
        NMS.resetAttackDamage(player);
        player.updateInventory();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        dropInventoryOnRespawn.remove(player.getUniqueId());
        if (!enabled() || !isTagged(player)) {
            taggedUntil.remove(player.getUniqueId());
            return;
        }
        taggedUntil.remove(player.getUniqueId());
        if (!plugin.getConfig().getBoolean("combat-tag.combat-log", true)) {
            return;
        }
        if (inStaffMode(player)) {
            return;
        }
        player.setHealth(0.0);
        wipeDroppedGear(player);
        dropInventoryOnRespawn.remove(player.getUniqueId());
        String broadcast = plugin.getConfig().getString("combat-tag.log-broadcast",
                "&c{player} s'est déconnecté en combat.");
        Bukkit.broadcastMessage(CC.color(plugin.prefix() + broadcast.replace("{player}", player.getName())));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!enabled() || !isTagged(player) || commandBypass(player)) {
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
