package fr.draftmc.staff;

import fr.draftmc.Draftmc;
import fr.draftmc.util.CC;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Toggle du mode staff : /staff bascule un membre du staff en mode
 * "patrouille" (créatif + invisible) en mettant son état de survie de côté,
 * puis le lui rend intact à la sortie.
 *
 * Ce que ce mode garantit, et pourquoi c'est le point critique :
 * un membre du staff qui passe en créatif pour surveiller le serveur ne doit
 * JAMAIS pouvoir profiter de cette bascule pour dupliquer, transporter ou
 * perdre des items de son inventaire de survie (kit PvP, items custom
 * potentiellement uniques). L'ancienne version se contentait de changer le
 * gamemode sans toucher à l'inventaire : un staff gardait donc son kit de
 * combat complet en pleine invisibilité/créatif, ce qui est à la fois un
 * avantage déloyal et une vulnérabilité de duplication (poser un item
 * créatif, repasser en survie avec l'inventaire déjà rempli, etc.).
 *
 * Trois niveaux de garantie sur l'inventaire mis de côté :
 *  1. /staff à nouveau restaure tout immédiatement (cas normal).
 *  2. Déconnexion pendant le mode staff -> restauration forcée avant que le
 *     joueur ne quitte réellement (onQuit), pour que son .dat Minecraft soit
 *     sauvegardé avec son vrai inventaire.
 *  3. Crash serveur pendant le mode staff -> StaffStateStore a déjà écrit le
 *     snapshot sur disque à l'activation ; à la reconnexion du joueur après
 *     le redémarrage, il est restauré automatiquement (onJoin).
 */
public class StaffManager implements CommandExecutor, Listener {
    private final Draftmc plugin;
    private final StaffStateStore store;
    private final Set<UUID> staff = new HashSet<UUID>();
    private final Map<UUID, StaffState> savedStates = new HashMap<UUID, StaffState>();

    /** Joueurs pour qui /sc a été activé : tout leur chat normal part en chat staff
     *  jusqu'à ce qu'ils fassent /sc à nouveau. Indépendant du mode staff lui-même. */
    private final Set<UUID> staffChatMode = new HashSet<UUID>();
    /** Même principe pour /scadmin (admins uniquement). */
    private final Set<UUID> adminChatMode = new HashSet<UUID>();

    private final Map<UUID, List<Long>> clicks = new HashMap<UUID, List<Long>>();
    private final StaffItems items;
    private int rankCheckTask = -1;

    public StaffManager(Draftmc plugin) {
        this.plugin = plugin;
        this.store = new StaffStateStore(plugin);
        this.items = new StaffItems(plugin);
        Bukkit.getPluginManager().registerEvents(items, plugin);
        this.rankCheckTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            @Override
            public void run() {
                checkStaffRanks();
            }
        }, 40L, 40L);
    }

    public StaffItems items() {
        return items;
    }

    /** Coupe le mode staff si le joueur n'a plus le grade / la permission. */
    private void checkStaffRanks() {
        if (staff.isEmpty()) {
            return;
        }
        for (UUID uuid : new HashSet<UUID>(staff)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                continue;
            }
            if (!stillHasStaffAccess(player)) {
                disable(player);
                adminChatMode.remove(uuid);
                staffChatMode.remove(uuid);
                plugin.msg(player, "&cMode staff retiré : tu n'as plus le rank staff.");
            }
        }
    }

    private boolean stillHasStaffAccess(Player player) {
        if (player.isOp()) {
            return true;
        }
        if (plugin.grades() != null && plugin.grades().isStaffMember(player)) {
            return true;
        }
        return player.hasPermission("draftmc.staff") || player.hasPermission("draftmc.admin");
    }

    public boolean isStaff(Player player) {
        return staff.contains(player.getUniqueId());
    }

    /**
     * À appeler une fois au démarrage (Draftmc#onEnable), après construction.
     * Se contente de logger : la restauration effective a lieu quand (et si)
     * le joueur concerné se reconnecte, dans onJoin.
     */
    public void logPendingCrashRecoveries() {
        List<UUID> pending = store.pending();
        if (!pending.isEmpty()) {
            plugin.getLogger().warning(pending.size() + " snapshot(s) de mode staff non restauré(s) trouvé(s) "
                    + "(probable arrêt non propre). Ils seront restaurés à la reconnexion des joueurs concernés.");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase();
        if (cmd.equals("sc") || cmd.equals("scadmin")) {
            boolean adminChat = cmd.equals("scadmin");
            if (adminChat) {
                if (!sender.hasPermission("draftmc.admin") && !sender.hasPermission("draftmc.staff.adminchat")) {
                    plugin.msg(sender, "&cPas la permission.");
                    return true;
                }
            } else if (!sender.hasPermission("draftmc.staff.chat") && !sender.hasPermission("draftmc.staff")) {
                plugin.msg(sender, "&cPas la permission.");
                return true;
            }
            if (args.length == 0) {
                if (!(sender instanceof Player)) {
                    plugin.msg(sender, adminChat ? "&e/scadmin <message>" : "&e/sc <message>");
                    return true;
                }
                Player player = (Player) sender;
                Set<UUID> mode = adminChat ? adminChatMode : staffChatMode;
                if (mode.remove(player.getUniqueId())) {
                    plugin.msg(player, adminChat
                            ? "&cChat admin désactivé."
                            : "&cChat staff désactivé. &7Tes messages repartent dans le chat normal.");
                } else {
                    // Un seul mode chat à la fois
                    if (adminChat) {
                        staffChatMode.remove(player.getUniqueId());
                    } else {
                        adminChatMode.remove(player.getUniqueId());
                    }
                    mode.add(player.getUniqueId());
                    plugin.msg(player, adminChat
                            ? "&aChat admin activé. &e/scadmin &7pour désactiver."
                            : "&aChat staff activé. &7Tout ce que tu écris va au chat staff. &e/sc &7pour désactiver.");
                }
                return true;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < args.length; i++) {
                if (i > 0) {
                    sb.append(' ');
                }
                sb.append(args[i]);
            }
            if (adminChat) {
                broadcastAdminChat(sender.getName(), sb.toString());
            } else {
                broadcastStaffChat(sender.getName(), sb.toString());
            }
            return true;
        }
        if (cmd.equals("cps")) {
            if (!sender.hasPermission("draftmc.staff")) {
                return true;
            }
            if (args.length < 1) {
                plugin.msg(sender, "&e/cps <joueur>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                plugin.msg(sender, "&cHors-ligne.");
                return true;
            }
            int window = plugin.getConfig().getInt("staff.cps-window-seconds", 5);
            plugin.msg(sender, "&eCPS de " + target.getName() + " &7» &6" + String.format("%.1f", cps(target.getUniqueId()))
                    + " &7(moyenne sur " + window + "s)");
            return true;
        }
        if (!(sender instanceof Player)) {
            return true;
        }
        Player player = (Player) sender;
        if (!stillHasStaffAccess(player)) {
            plugin.msg(player, "&cPas la permission.");
            return true;
        }
        if (isStaff(player)) {
            disable(player);
            plugin.msg(player, "&cMode staff off.");
        } else {
            enable(player);
            plugin.msg(player, "&aMode staff on. &7Chat: &e/sc &7Admin: &e/scadmin &7Freeze: &e/freeze <joueur>");
        }
        return true;
    }

    private void enable(Player player) {
        UUID uuid = player.getUniqueId();
        if (staff.contains(uuid)) {
            return;
        }
        StaffState state = capture(player);
        savedStates.put(uuid, state);
        store.save(uuid, player.getName(), state);
        staff.add(uuid);

        if (plugin.getConfig().getBoolean("staff.clear-inventory-on-enable", true)) {
            PlayerInventory inv = player.getInventory();
            inv.setContents(new ItemStack[inv.getContents().length]);
            inv.setArmorContents(new ItemStack[inv.getArmorContents().length]);
        }
        items.give(player);

        double maxHealth = player.getMaxHealth();
        player.setHealth(maxHealth);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setGameMode(GameMode.CREATIVE);
        player.setAllowFlight(true);
        player.setFlying(true);

        if (plugin.getConfig().getBoolean("staff.vanish-on-enable", true)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, true, false));
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.hasPermission("draftmc.staff")) {
                    other.hidePlayer(player);
                }
            }
        }
    }

    private void disable(Player player) {
        UUID uuid = player.getUniqueId();
        if (!staff.remove(uuid)) {
            return;
        }
        StaffState state = savedStates.remove(uuid);
        store.remove(uuid);

        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        for (Player other : Bukkit.getOnlinePlayers()) {
            other.showPlayer(player);
        }

        if (state != null) {
            restore(player, state);
        } else {
            // Ne devrait pas arriver (staff contenait l'UUID donc capture() a
            // forcément eu lieu), mais on refuse de laisser un joueur bloqué
            // en créatif sans snapshot : on le repasse au moins en survie.
            player.setGameMode(GameMode.SURVIVAL);
            plugin.getLogger().warning("Aucun snapshot trouvé pour " + player.getName() + " à la sortie du mode staff (état incohérent).");
        }
    }

    private StaffState capture(Player player) {
        PlayerInventory inv = player.getInventory();
        return new StaffState(
                cloneArray(inv.getContents()),
                cloneArray(inv.getArmorContents()),
                player.getHealth(),
                player.getFoodLevel(),
                player.getSaturation(),
                player.getExp(),
                player.getLevel(),
                player.getGameMode(),
                player.getAllowFlight(),
                player.isFlying(),
                player.getWalkSpeed(),
                player.getFlySpeed(),
                player.getLocation()
        );
    }

    private void restore(Player player, StaffState state) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = state.inventory();
        ItemStack[] armor = state.armor();
        if (contents != null && contents.length == inv.getContents().length) {
            inv.setContents(contents);
        } else if (contents != null && contents.length > 0) {
            ItemStack[] padded = new ItemStack[inv.getContents().length];
            System.arraycopy(contents, 0, padded, 0, Math.min(contents.length, padded.length));
            inv.setContents(padded);
        }
        if (armor != null && armor.length == inv.getArmorContents().length) {
            inv.setArmorContents(armor);
        } else if (armor != null && armor.length > 0) {
            ItemStack[] padded = new ItemStack[inv.getArmorContents().length];
            System.arraycopy(armor, 0, padded, 0, Math.min(armor.length, padded.length));
            inv.setArmorContents(padded);
        }

        double maxHealth = player.getMaxHealth();
        player.setHealth(Math.min(state.health(), maxHealth));
        player.setFoodLevel(state.foodLevel());
        player.setSaturation(state.saturation());
        player.setExp(Math.max(0f, Math.min(1f, state.exp())));
        player.setLevel(Math.max(0, state.level()));

        player.setFlying(false);
        player.setAllowFlight(state.allowFlight());
        // setFlying doit être appelé après setAllowFlight quand on autorise
        // à nouveau le vol, sinon le client peut rejeter l'état "flying=true".
        if (state.allowFlight()) {
            player.setFlying(state.flying());
        }
        player.setWalkSpeed(state.walkSpeed());
        player.setFlySpeed(state.flySpeed());
        player.setGameMode(state.gameMode());
    }

    private static ItemStack[] cloneArray(ItemStack[] source) {
        ItemStack[] copy = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i] == null ? null : source[i].clone();
        }
        return copy;
    }

    private void broadcastStaffChat(String from, String message) {
        String line = CC.color("&8[&cStaff&8] &e" + from + " &7» &f" + message);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("draftmc.staff.chat") || p.hasPermission("draftmc.staff")) {
                p.sendMessage(line);
            }
        }
        Bukkit.getConsoleSender().sendMessage(line);
    }

    private void broadcastAdminChat(String from, String message) {
        String line = CC.color("&8[&4Admin&8] &c" + from + " &7» &f" + message);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("draftmc.admin") || p.hasPermission("draftmc.staff.adminchat")) {
                p.sendMessage(line);
            }
        }
        Bukkit.getConsoleSender().sendMessage(line);
    }

    /**
     * Intercepte le chat normal des joueurs en mode /sc : leur message part au chat
     * staff au lieu du chat public. AsyncPlayerChatEvent est asynchrone, donc on ne
     * touche l'API Bukkit (sendMessage à d'autres joueurs) que via une tâche
     * synchrone planifiée, par cohérence avec le reste du plugin.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        final Player player = event.getPlayer();
        final boolean admin = adminChatMode.contains(player.getUniqueId());
        final boolean staffMode = staffChatMode.contains(player.getUniqueId());
        if (!admin && !staffMode) {
            return;
        }
        if (admin) {
            if (!player.hasPermission("draftmc.admin") && !player.hasPermission("draftmc.staff.adminchat")) {
                adminChatMode.remove(player.getUniqueId());
                return;
            }
        } else if (!player.hasPermission("draftmc.staff.chat") && !player.hasPermission("draftmc.staff")) {
            staffChatMode.remove(player.getUniqueId());
            return;
        }
        event.setCancelled(true);
        final String message = event.getMessage();
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (admin) {
                    broadcastAdminChat(player.getName(), message);
                } else {
                    broadcastStaffChat(player.getName(), message);
                }
            }
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        for (UUID id : staff) {
            Player s = Bukkit.getPlayer(id);
            if (s != null && !player.hasPermission("draftmc.staff")) {
                player.hidePlayer(s);
            }
        }

        // Filet crash : staffmode.yml n'a du sens que si le .dat est encore
        // vide (inventaire vidé par /staff puis crash). Un snapshot vide ou
        // périmé ne doit JAMAIS écraser un inventaire déjà chargé.
        final UUID uuid = player.getUniqueId();
        if (savedStates.containsKey(uuid) || !store.has(uuid)) {
            return;
        }
        final StaffState state = store.load(uuid);
        if (state == null || !hasAnyItem(state.inventory(), state.armor())) {
            store.remove(uuid);
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    return;
                }
                if (hasAnyItem(player.getInventory().getContents(), player.getInventory().getArmorContents())) {
                    store.remove(uuid);
                    return;
                }
                restore(player, state);
                store.remove(uuid);
                player.updateInventory();
                plugin.msg(player, "&aTon inventaire a été restauré suite à un redémarrage du serveur pendant le mode staff.");
                plugin.getLogger().info("Snapshot staff restauré pour " + player.getName() + " après un arrêt non propre.");
            }
        }, 2L);
    }

    private static boolean hasAnyItem(ItemStack[] contents, ItemStack[] armor) {
        return hasAnyItem(contents) || hasAnyItem(armor);
    }

    private static boolean hasAnyItem(ItemStack[] items) {
        if (items == null) {
            return false;
        }
        for (int i = 0; i < items.length; i++) {
            if (items[i] != null && items[i].getType() != org.bukkit.Material.AIR) {
                return true;
            }
        }
        return false;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        clicks.remove(player.getUniqueId());
        staffChatMode.remove(player.getUniqueId());
        adminChatMode.remove(player.getUniqueId());
        // Ne jamais laisser un joueur se déconnecter avec son inventaire de
        // survie "en banque" dans savedStates : on restaure avant que le
        // .dat ne soit écrit, sinon son vrai kit resterait piégé en mémoire
        // (perdu si le serveur redémarre avant sa prochaine reconnexion).
        if (isStaff(player)) {
            disable(player);
        }
    }

    @EventHandler
    public void onClick(PlayerInteractEvent event) {
        if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            record(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onHit(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            record(event.getDamager().getUniqueId());
        }
    }

    private void record(UUID uuid) {
        List<Long> list = clicks.get(uuid);
        if (list == null) {
            list = new ArrayList<Long>();
            clicks.put(uuid, list);
        }
        long now = System.currentTimeMillis();
        list.add(now);
        prune(list, now);
    }

    private void prune(List<Long> list, long now) {
        long window = plugin.getConfig().getInt("staff.cps-window-seconds", 5) * 1000L;
        Iterator<Long> it = list.iterator();
        while (it.hasNext()) {
            if (now - it.next() > window) {
                it.remove();
            }
        }
    }

    /**
     * Moyenne des clics par seconde sur la fenêtre configurée
     * (staff.cps-window-seconds, 5s par défaut).
     */
    public double cps(UUID uuid) {
        List<Long> list = clicks.get(uuid);
        if (list == null || list.isEmpty()) {
            return 0;
        }
        prune(list, System.currentTimeMillis());
        int window = plugin.getConfig().getInt("staff.cps-window-seconds", 5);
        return list.size() / (double) Math.max(1, window);
    }
}
