package fr.sydaria.staff;

import fr.sydaria.Sydaria;
import fr.sydaria.util.CC;
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
    private final Sydaria plugin;
    private final StaffStateStore store;
    private final Set<UUID> staff = new HashSet<UUID>();
    private final Map<UUID, StaffState> savedStates = new HashMap<UUID, StaffState>();

    /** Joueurs pour qui /sc a été activé : tout leur chat normal part en chat staff
     *  jusqu'à ce qu'ils fassent /sc à nouveau. Indépendant du mode staff lui-même. */
    private final Set<UUID> staffChatMode = new HashSet<UUID>();

    private final Map<UUID, List<Long>> clicks = new HashMap<UUID, List<Long>>();

    public StaffManager(Sydaria plugin) {
        this.plugin = plugin;
        this.store = new StaffStateStore(plugin);
    }

    public boolean isStaff(Player player) {
        return staff.contains(player.getUniqueId());
    }

    /**
     * À appeler une fois au démarrage (Sydaria#onEnable), après construction.
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
        if (cmd.equals("sc")) {
            if (!sender.hasPermission("sydaria.staff")) {
                return true;
            }
            if (args.length == 0) {
                if (!(sender instanceof Player)) {
                    plugin.msg(sender, "&e/sc <message>");
                    return true;
                }
                Player player = (Player) sender;
                if (staffChatMode.remove(player.getUniqueId())) {
                    plugin.msg(player, "&cChat staff désactivé. &7Tes messages repartent dans le chat normal.");
                } else {
                    staffChatMode.add(player.getUniqueId());
                    plugin.msg(player, "&aChat staff activé. &7Tout ce que tu écris va au chat staff. &e/sc &7pour désactiver.");
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
            broadcastStaffChat(sender.getName(), sb.toString());
            return true;
        }
        if (cmd.equals("cps")) {
            if (!sender.hasPermission("sydaria.staff")) {
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
            plugin.msg(sender, "&eCPS de " + target.getName() + " &7» &6" + String.format("%.1f", cps(target.getUniqueId())));
            return true;
        }
        if (!(sender instanceof Player)) {
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("sydaria.staff")) {
            plugin.msg(player, "&cPas la permission.");
            return true;
        }
        if (isStaff(player)) {
            disable(player);
            plugin.msg(player, "&cMode staff off.");
        } else {
            enable(player);
            plugin.msg(player, "&aMode staff on. &7Chat: &e/sc &7Freeze: &e/freeze <joueur>");
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
                if (!other.hasPermission("sydaria.staff")) {
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
        inv.setContents(state.inventory());
        inv.setArmorContents(state.armor());

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
            if (p.hasPermission("sydaria.staff")) {
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
        if (!staffChatMode.contains(player.getUniqueId())) {
            return;
        }
        if (!player.hasPermission("sydaria.staff")) {
            // Permission retirée entre-temps (ex: /op-, plugin permissions) :
            // on désactive proprement au lieu de laisser un état incohérent.
            staffChatMode.remove(player.getUniqueId());
            return;
        }
        event.setCancelled(true);
        final String message = event.getMessage();
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                broadcastStaffChat(player.getName(), message);
            }
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        for (UUID id : staff) {
            Player s = Bukkit.getPlayer(id);
            if (s != null && !player.hasPermission("sydaria.staff")) {
                player.hidePlayer(s);
            }
        }

        // Filet de sécurité : un snapshot est resté sur disque (arrêt non
        // propre pendant que ce joueur était en mode staff) et n'a pas encore
        // été rechargé en mémoire dans cette session -> on restaure avant
        // qu'il ne touche à quoi que ce soit.
        final UUID uuid = player.getUniqueId();
        if (!savedStates.containsKey(uuid) && store.has(uuid)) {
            StaffState state = store.load(uuid);
            store.remove(uuid);
            if (state != null) {
                final StaffState toRestore = state;
                Bukkit.getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        restore(player, toRestore);
                        plugin.msg(player, "&aTon inventaire a été restauré suite à un redémarrage du serveur pendant le mode staff.");
                    }
                });
                plugin.getLogger().info("Snapshot staff restauré pour " + player.getName() + " après un arrêt non propre.");
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        clicks.remove(player.getUniqueId());
        staffChatMode.remove(player.getUniqueId());
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
        int window = plugin.getConfig().getInt("staff.cps-window-seconds", 2) * 1000;
        Iterator<Long> it = list.iterator();
        while (it.hasNext()) {
            if (now - it.next() > window) {
                it.remove();
            }
        }
    }

    public double cps(UUID uuid) {
        List<Long> list = clicks.get(uuid);
        if (list == null || list.isEmpty()) {
            return 0;
        }
        int window = plugin.getConfig().getInt("staff.cps-window-seconds", 2);
        return list.size() / (double) Math.max(1, window);
    }
}
