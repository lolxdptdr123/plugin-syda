package fr.sydaria.staff;

import fr.sydaria.Sydaria;
import fr.sydaria.util.CC;
import fr.sydaria.util.NmsTitles;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * /freeze : immobilise complètement un joueur suspecté (triche, comportement
 * à investiguer) sans le déconnecter, pour qu'un staff ait le temps
 * d'intervenir sans que le joueur puisse bouger, se battre, casser/poser des
 * blocs, toucher son inventaire ou fuir via une commande.
 *
 * Volontairement séparé de StaffManager : le mode staff (créatif/vanish) et
 * le freeze (immobilisation d'un tiers) sont deux responsabilités
 * indépendantes — un membre du staff peut geler quelqu'un sans être
 * lui-même en mode staff, et le mélange des deux dans une seule classe
 * grossirait inutilement StaffManager sans bénéfice.
 *
 * Le chat public reste volontairement autorisé pendant le freeze (choix
 * produit) : seuls mouvement, combat et interactions sont bloqués.
 *
 * Anti-évasion : l'état "gelé" est conservé en mémoire indépendamment de la
 * session (Set<UUID>, jamais vidé par une déconnexion), donc si le joueur se
 * déconnecte pendant qu'il est gelé puis se reconnecte, il est immédiatement
 * regelé à son retour et le staff est alerté dans les deux cas.
 */
public class FreezeManager implements CommandExecutor, Listener {
    private final Sydaria plugin;
    private final Set<UUID> frozen = new HashSet<UUID>();
    private final Map<UUID, String> frozenBy = new HashMap<UUID, String>();
    private final Map<UUID, float[]> savedSpeeds = new HashMap<UUID, float[]>();
    private BukkitTask reminderTask;

    public FreezeManager(Sydaria plugin) {
        this.plugin = plugin;
        startReminderTask();
    }

    public boolean isFrozen(Player player) {
        return frozen.contains(player.getUniqueId());
    }

    private void startReminderTask() {
        int intervalSeconds = Math.max(1, plugin.getConfig().getInt("staff.freeze.reminder-interval-seconds", 3));
        this.reminderTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                if (frozen.isEmpty()) {
                    return;
                }
                for (UUID uuid : frozen) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null && player.isOnline()) {
                        sendReminder(player);
                    }
                }
            }
        }, 20L, intervalSeconds * 20L);
    }

    /** Appelé depuis Sydaria#onDisable pour ne pas laisser la tâche tourner dans le vide. */
    public void shutdown() {
        if (reminderTask != null) {
            reminderTask.cancel();
        }
    }

    private void sendReminder(Player player) {
        String title = CC.color(plugin.getConfig().getString("staff.freeze.title", "&c&lVOUS ÊTES GELÉ"));
        String subtitle = CC.color(plugin.getConfig().getString("staff.freeze.subtitle", "&7Un staff va s'occuper de vous, ne vous déconnectez pas."));
        NmsTitles.send(player, title, subtitle, 0, 40, 10);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("sydaria.staff.freeze")) {
            plugin.msg(sender, "&cPas la permission.");
            return true;
        }
        if (args.length < 1) {
            plugin.msg(sender, "&e/freeze <joueur>");
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            plugin.msg(sender, "&cHors-ligne.");
            return true;
        }
        if (isFrozen(target)) {
            unfreeze(target);
            plugin.msg(sender, "&a" + target.getName() + " &7a été dégelé.");
        } else {
            freeze(target, sender.getName());
            plugin.msg(sender, "&c" + target.getName() + " &7a été gelé.");
        }
        return true;
    }

    private void freeze(Player target, String staffName) {
        UUID uuid = target.getUniqueId();
        frozen.add(uuid);
        frozenBy.put(uuid, staffName);
        savedSpeeds.put(uuid, new float[] { target.getWalkSpeed(), target.getFlySpeed() });
        target.setWalkSpeed(0f);
        target.setFlySpeed(0f);
        sendReminder(target);
        alertStaff(CC.color("&8[&cFreeze&8] &e" + target.getName() + " &7a été gelé par &e" + staffName + "&7."));
    }

    private void unfreeze(Player target) {
        UUID uuid = target.getUniqueId();
        frozen.remove(uuid);
        frozenBy.remove(uuid);
        float[] speeds = savedSpeeds.remove(uuid);
        target.setWalkSpeed(speeds != null ? speeds[0] : 0.2f);
        target.setFlySpeed(speeds != null ? speeds[1] : 0.1f);
        target.sendMessage(CC.color(plugin.prefix() + "&aTu as été dégelé."));
        alertStaff(CC.color("&8[&cFreeze&8] &e" + target.getName() + " &7a été dégelé."));
    }

    private void alertStaff(String line) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("sydaria.staff")) {
                p.sendMessage(line);
            }
        }
        Bukkit.getConsoleSender().sendMessage(line);
    }

    private boolean isCommandAllowedWhileFrozen(String label) {
        List<String> allowed = plugin.getConfig().getStringList("staff.freeze.allowed-commands");
        for (String s : allowed) {
            if (s.equalsIgnoreCase(label)) {
                return true;
            }
        }
        return false;
    }

    // --- Immobilisation ---------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent event) {
        if (!frozen.contains(event.getPlayer().getUniqueId())) {
            return;
        }
        if (event.getFrom().getX() == event.getTo().getX()
                && event.getFrom().getY() == event.getTo().getY()
                && event.getFrom().getZ() == event.getTo().getZ()) {
            // Seule la tête a bougé (regarder autour de soi) : autorisé.
            return;
        }
        // On force le retour à la position de départ plutôt que de se fier
        // uniquement à setCancelled(true) : sur certaines versions/plugins,
        // un PlayerMoveEvent annulé peut encore laisser passer un résidu de
        // vélocité côté client. Cette double protection est la pratique
        // standard des plugins de freeze (CMI, EssentialsX...).
        event.setCancelled(true);
        event.setTo(event.getFrom());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (frozen.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        // Un joueur gelé ne peut ni infliger ni subir de dégâts : il est en
        // pleine investigation, il ne doit pas pouvoir mourir (chute,
        // environnement, PvP) pendant qu'il est immobilisé et sans défense.
        if (frozen.contains(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player && frozen.contains(event.getDamager().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (frozen.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (frozen.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (frozen.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player && frozen.contains(event.getWhoClicked().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!frozen.contains(player.getUniqueId())) {
            return;
        }
        String msg = event.getMessage().substring(1);
        String label = msg.split(" ")[0].toLowerCase();
        if (isCommandAllowedWhileFrozen(label)) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage(CC.color(plugin.prefix() + "&cTu ne peux pas utiliser de commandes pendant que tu es gelé."));
    }

    // --- Anti-évasion -------------------------------------------------------

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (frozen.contains(uuid)) {
            String by = frozenBy.get(uuid);
            alertStaff(CC.color("&8[&cFreeze&8] &c⚠ " + player.getName()
                    + " &7s'est déconnecté alors qu'il était gelé par &e" + (by != null ? by : "?")
                    + " &7(évasion possible). Il sera regelé à sa reconnexion."));
            plugin.getLogger().warning(player.getName() + " s'est déconnecté alors qu'il était gelé (gelé par " + by + ").");
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        if (frozen.contains(player.getUniqueId())) {
            float[] speeds = savedSpeeds.get(player.getUniqueId());
            player.setWalkSpeed(0f);
            player.setFlySpeed(0f);
            if (speeds == null) {
                savedSpeeds.put(player.getUniqueId(), new float[] { 0.2f, 0.1f });
            }
            sendReminder(player);
            alertStaff(CC.color("&8[&cFreeze&8] &e" + player.getName() + " &7(gelé) s'est reconnecté."));
        }
    }
}
