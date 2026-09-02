package fr.sydaria.factions;

import fr.sydaria.Sydaria;
import fr.sydaria.util.CC;
import fr.sydaria.util.Locations;
import fr.sydaria.util.YamlFile;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class FactionManager implements CommandExecutor, TabCompleter, Listener {
    private static final List<String> SUBS = Arrays.asList(
            "help", "create", "disband", "invite", "deinvite", "join", "kick", "leave",
            "promote", "demote", "rank", "leader", "show", "who", "list", "desc", "motd", "rename",
            "open", "close", "chest", "upgrade", "perm", "fly", "home", "sethome", "delhome",
            "claim", "unclaim", "unclaimall", "map", "enemy", "neutral",
            "chat", "c"
    );

    private final Sydaria plugin;
    private final YamlFile file;
    private final FactionMenus menus;
    private final FactionListener protection;
    private final Map<UUID, FactionChatMode> chatModes = new HashMap<UUID, FactionChatMode>();
    private final Map<UUID, String> pendingDisband = new HashMap<UUID, String>();

    public FactionManager(Sydaria plugin) {
        this.plugin = plugin;
        this.file = new YamlFile(plugin, "factions.yml");
        this.menus = new FactionMenus(plugin, this);
        this.protection = new FactionListener(plugin, this);
        Bukkit.getPluginManager().registerEvents(menus, plugin);
        Bukkit.getPluginManager().registerEvents(protection, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                regenPower();
            }
        }, 20L * 60, 20L * 60);
    }

    public FactionMenus menus() {
        return menus;
    }

    public String factionOf(Player player) {
        return factionOf(player.getUniqueId());
    }

    public String factionOf(UUID uuid) {
        String id = plugin.data().getString(uuid, "faction");
        return id == null ? "" : id;
    }

    public String displayName(String id) {
        if (id == null || id.isEmpty()) {
            return "Wilderness";
        }
        return file.get().getString("factions." + id + ".name", id);
    }

    public String displayOf(Player player) {
        String id = factionOf(player);
        return id.isEmpty() ? "Aucune" : displayName(id);
    }

    public List<String> members(String fac) {
        return new ArrayList<String>(file.get().getStringList("factions." + fac + ".members"));
    }

    public FactionRank rankOf(Player player) {
        return rankOf(factionOf(player), player.getUniqueId());
    }

    public FactionRank rankOf(String fac, UUID uuid) {
        if (fac == null || fac.isEmpty()) {
            return FactionRank.RECRUIT;
        }
        String stored = file.get().getString("factions." + fac + ".ranks." + uuid.toString());
        if (stored != null && !stored.isEmpty()) {
            return FactionRank.from(stored);
        }
        String leader = file.get().getString("factions." + fac + ".leader", "");
        if (uuid.toString().equals(leader)) {
            return FactionRank.LEADER;
        }
        return FactionRank.MEMBER;
    }

    public boolean hasPerm(Player player, FactionPerm perm) {
        if (player.hasPermission("sydaria.admin") || player.hasPermission("sydaria.faction.bypass")) {
            return true;
        }
        String fac = factionOf(player);
        if (fac.isEmpty()) {
            return false;
        }
        FactionRank rank = rankOf(player);
        if (rank == FactionRank.LEADER) {
            return true;
        }
        return permValue(fac, rank.name(), perm);
    }

    public boolean permValue(String fac, String group, FactionPerm perm) {
        String path = "factions." + fac + ".perms." + group + "." + perm.name();
        if (file.get().contains(path)) {
            return file.get().getBoolean(path);
        }
        return perm.defaultValue(group);
    }

    public void togglePerm(String fac, String group, FactionPerm perm) {
        boolean now = !permValue(fac, group, perm);
        file.get().set("factions." + fac + ".perms." + group + "." + perm.name(), now);
        file.save();
    }

    public int upgradeLevel(String fac, String key) {
        if ("fly".equals(key)) {
            return hasFly(fac) ? 1 : 0;
        }
        int stored = file.get().getInt("factions." + fac + ".upgrades." + key, -1);
        if (stored >= 0) {
            return stored;
        }
        if ("chest".equals(key)) {
            int size = file.get().getInt("factions." + fac + ".chest-size", 27);
            List<Integer> sizes = upgradeInts("chest", "sizes", Arrays.asList(27, 36, 45, 54));
            int best = 0;
            for (int i = 0; i < sizes.size(); i++) {
                if (size >= sizes.get(i)) {
                    best = i;
                }
            }
            return best;
        }
        return 0;
    }

    public List<Integer> upgradeInts(String upgrade, String key, List<Integer> fallback) {
        List<Integer> list = plugin.getConfig().getIntegerList("factions.upgrades." + upgrade + "." + key);
        return list == null || list.isEmpty() ? fallback : list;
    }

    public int chestSize(String fac) {
        List<Integer> sizes = upgradeInts("chest", "sizes", Arrays.asList(27, 36, 45, 54));
        int level = Math.min(upgradeLevel(fac, "chest"), sizes.size() - 1);
        return sizes.get(Math.max(0, level));
    }

    public boolean hasFly(String fac) {
        return file.get().getBoolean("factions." + fac + ".fly", false)
                || file.get().getInt("factions." + fac + ".upgrades.fly", 0) > 0;
    }

    public int maxMembers(String fac) {
        List<Integer> amounts = upgradeInts("members", "amounts", Arrays.asList(10, 15, 20, 30));
        int level = Math.min(upgradeLevel(fac, "members"), amounts.size() - 1);
        return amounts.get(Math.max(0, level));
    }

    public int extraPower(String fac) {
        List<Integer> amounts = upgradeInts("power", "amounts", Arrays.asList(0, 10, 20, 40));
        int level = Math.min(upgradeLevel(fac, "power"), amounts.size() - 1);
        return amounts.get(Math.max(0, level));
    }

    public int claimCount(String fac) {
        ConfigurationSection section = file.get().getConfigurationSection("claims");
        if (section == null) {
            return 0;
        }
        int n = 0;
        for (String key : section.getKeys(false)) {
            if (fac.equalsIgnoreCase(section.getString(key))) {
                n++;
            }
        }
        return n;
    }

    public String claimAt(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        String owner = file.get().getString("claims." + claimKey(location.getChunk()));
        return owner == null || owner.isEmpty() ? null : owner;
    }

    public String powerText(String fac) {
        return formatPower(factionPower(fac)) + "&7/&e" + formatPower(factionMaxPower(fac));
    }

    public double factionPower(String fac) {
        double total = 0;
        for (String raw : members(fac)) {
            total += currentPower(UUID.fromString(raw));
        }
        return total;
    }

    public double factionMaxPower(String fac) {
        double max = extraPower(fac);
        double per = plugin.getConfig().getDouble("factions.power.max", 10);
        max += members(fac).size() * per;
        return max;
    }

    public void ensurePower(Player player) {
        String key = "power." + player.getUniqueId().toString();
        if (!file.get().contains(key)) {
            file.get().set(key, plugin.getConfig().getDouble("factions.power.start", 10));
            file.save();
        }
    }

    public double currentPower(UUID uuid) {
        String key = "power." + uuid.toString();
        if (!file.get().contains(key)) {
            return plugin.getConfig().getDouble("factions.power.start", 10);
        }
        return file.get().getDouble(key);
    }

    public void setPower(UUID uuid, double value) {
        setPower(uuid, value, true);
    }

    public void setPower(UUID uuid, double value, boolean save) {
        double max = plugin.getConfig().getDouble("factions.power.max", 10);
        double v = Math.max(0, Math.min(max, value));
        file.get().set("power." + uuid.toString(), v);
        if (save) {
            file.save();
        }
    }

    public void onDeath(Player player) {
        if (factionOf(player).isEmpty()) {
            return;
        }
        double loss = plugin.getConfig().getDouble("factions.power.per-death", 2);
        setPower(player.getUniqueId(), currentPower(player.getUniqueId()) - loss);
        plugin.msg(player, "&c-" + formatPower(loss) + " power &7(" + formatPower(currentPower(player.getUniqueId())) + ")");
    }

    private void regenPower() {
        double regen = plugin.getConfig().getDouble("factions.power.regen-per-minute", 0.2);
        if (regen <= 0) {
            return;
        }
        boolean changed = false;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (factionOf(player).isEmpty()) {
                continue;
            }
            setPower(player.getUniqueId(), currentPower(player.getUniqueId()) + regen, false);
            changed = true;
        }
        if (changed) {
            file.save();
        }
    }

    public boolean canBuild(Player player, Location location, FactionPerm perm) {
        if (player.hasPermission("sydaria.admin") || player.hasPermission("sydaria.faction.bypass")) {
            return true;
        }
        String owner = claimAt(location);
        if (owner == null) {
            return true;
        }
        String fac = factionOf(player);
        if (owner.equalsIgnoreCase(fac)) {
            return hasPerm(player, perm);
        }
        FactionRelation rel = relationOf(owner, fac);
        return permValue(owner, rel.name(), perm);
    }

    public void deny(Player player, Location location) {
        String owner = claimAt(location);
        String name = owner == null ? "Wilderness" : displayName(owner);
        plugin.msg(player, plugin.getConfig().getString("factions.claim.deny-message", "&cCe chunk appartient à &e{faction}&c.")
                .replace("{faction}", name));
    }

    public boolean sameFaction(Player a, Player b) {
        String fa = factionOf(a);
        String fb = factionOf(b);
        return !fa.isEmpty() && fa.equalsIgnoreCase(fb);
    }

    public Player damager(Entity entity) {
        if (entity instanceof Player) {
            return (Player) entity;
        }
        if (entity instanceof Projectile) {
            Object shooter = ((Projectile) entity).getShooter();
            if (shooter instanceof Player) {
                return (Player) shooter;
            }
        }
        return null;
    }

    public FactionRelation relationOf(String from, String to) {
        if (from == null || from.isEmpty() || to == null || to.isEmpty()) {
            return FactionRelation.NEUTRAL;
        }
        if (from.equalsIgnoreCase(to)) {
            return FactionRelation.MEMBER;
        }
        if (listContains("factions." + from + ".enemies", to)) {
            return FactionRelation.ENEMY;
        }
        return FactionRelation.NEUTRAL;
    }

    public void onChunkChange(Player player, Location to) {
        if (!player.getAllowFlight() || player.hasPermission("sydaria.fly")) {
            return;
        }
        String fac = factionOf(player);
        if (fac.isEmpty() || !hasFly(fac)) {
            return;
        }
        String owner = claimAt(to);
        if (owner == null || !owner.equalsIgnoreCase(fac)) {
            player.setAllowFlight(false);
            player.setFlying(false);
            plugin.msg(player, "&cFly désactivé hors de tes claims.");
        }
    }

    public void handleChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        FactionChatMode mode = chatModes.get(player.getUniqueId());
        String fac = factionOf(player);
        if (mode == FactionChatMode.FACTION) {
            if (fac.isEmpty()) {
                chatModes.remove(player.getUniqueId());
                return;
            }
            event.setCancelled(true);
            String format = plugin.getConfig().getString("factions.chat-format", "&8[&6{faction}&8] &e{player}&7: &f{message}");
            final String line = CC.color(format
                    .replace("{faction}", displayName(fac))
                    .replace("{player}", player.getName())
                    .replace("{message}", event.getMessage()));
            final String factionId = fac;
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override
                public void run() {
                    sendFactionMessage(factionId, line);
                }
            });
            return;
        }
        // Le préfixe de faction dans le chat public n'est plus appliqué ici via
        // event.setFormat() : c'est TagManager.onChat qui compose le format final
        // (faction + grade + tag) en un seul endroit via publicPrefix(), pour éviter
        // qu'un handler n'écrase le format posé par un autre (voir TagManager).
    }

    /**
     * Préfixe de faction pour le chat public, ex: "&8[&6MaFaction&8] ", ou "" si le
     * joueur n'a pas de faction ou si factions.public-prefix est désactivé.
     */
    public String publicPrefix(Player player) {
        String fac = factionOf(player);
        if (!plugin.getConfig().getBoolean("factions.public-prefix", true) || fac.isEmpty()) {
            return "";
        }
        return CC.color(plugin.getConfig().getString("factions.public-format", "&8[&6{faction}&8] ")
                .replace("{faction}", displayName(fac)));
    }

    public void clearChat(UUID uuid) {
        chatModes.remove(uuid);
    }

    public void sendMotd(Player player) {
        String fac = factionOf(player);
        if (fac.isEmpty()) {
            return;
        }
        String motd = file.get().getString("factions." + fac + ".motd", "");
        if (motd != null && !motd.isEmpty()) {
            plugin.msg(player, "&6MOTD &7» &f" + motd);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cJoueur uniquement.");
            return true;
        }
        Player player = (Player) sender;
        if (args.length == 0) {
            help(player);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("help")) {
            help(player);
        } else if (sub.equals("create") && args.length >= 2) {
            create(player, args[1]);
        } else if (sub.equals("disband")) {
            disband(player, args.length >= 2 && args[1].equalsIgnoreCase("confirm"));
        } else if (sub.equals("invite") && args.length >= 2) {
            invite(player, args[1]);
        } else if (sub.equals("deinvite") && args.length >= 2) {
            deinvite(player, args[1]);
        } else if (sub.equals("join") && args.length >= 2) {
            join(player, args[1]);
        } else if (sub.equals("kick") && args.length >= 2) {
            kick(player, args[1]);
        } else if (sub.equals("leave")) {
            leave(player);
        } else if (sub.equals("promote") && args.length >= 2) {
            changeRank(player, args[1], true);
        } else if (sub.equals("demote") && args.length >= 2) {
            changeRank(player, args[1], false);
        } else if (sub.equals("rank")) {
            if (args.length >= 3) {
                setRank(player, args[1], args[2]);
            } else {
                plugin.msg(player, "&cUsage : &e/f rank <joueur> <Co-leader|Officier|Membre|Recrue>");
            }
        } else if (sub.equals("ally") || sub.equals("allies") || sub.equals("unally") || sub.equals("truce")) {
            plugin.msg(player, "&cLes alliances de faction sont désactivées.");
        } else if (sub.equals("leader") && args.length >= 2) {
            transfer(player, args[1]);
        } else if (sub.equals("show") || sub.equals("who") || sub.equals("info")) {
            show(player, args.length >= 2 ? args[1] : null);
        } else if (sub.equals("list")) {
            list(player, args.length >= 2 ? parseInt(args[1], 1) : 1);
        } else if (sub.equals("desc") || sub.equals("description")) {
            desc(player, args);
        } else if (sub.equals("motd")) {
            motd(player, args);
        } else if (sub.equals("rename") || sub.equals("tag")) {
            if (args.length >= 2) rename(player, args[1]);
            else plugin.msg(player, "&cUsage : &e/f rename <nom>");
        } else if (sub.equals("open")) {
            setOpen(player, true);
        } else if (sub.equals("close")) {
            setOpen(player, false);
        } else if (sub.equals("chest")) {
            openChest(player);
        } else if (sub.equals("upgrade")) {
            menus.openUpgrade(player);
        } else if (sub.equals("perm") || sub.equals("perms")) {
            menus.openPerm(player);
        } else if (sub.equals("fly")) {
            fly(player);
        } else if (sub.equals("home")) {
            home(player);
        } else if (sub.equals("sethome")) {
            sethome(player);
        } else if (sub.equals("delhome")) {
            delhome(player);
        } else if (sub.equals("claim")) {
            claim(player);
        } else if (sub.equals("unclaim")) {
            unclaim(player, false);
        } else if (sub.equals("unclaimall")) {
            unclaim(player, true);
        } else if (sub.equals("map")) {
            map(player);
        } else if (sub.equals("enemy") && args.length >= 2) {
            setRelation(player, args[1], FactionRelation.ENEMY);
        } else if (sub.equals("neutral") && args.length >= 2) {
            setRelation(player, args[1], FactionRelation.NEUTRAL);
        } else if (sub.equals("chat")) {
            cycleChat(player);
        } else if (sub.equals("c")) {
            if (args.length >= 2) {
                sendQuickChat(player, joinArgs(args, 1), false);
            } else {
                cycleChat(player);
            }
        } else {
            help(player);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<String>();
        if (args.length == 1) {
            for (String s : SUBS) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(s);
                }
            }
            return out;
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("invite") || sub.equals("kick") || sub.equals("promote") || sub.equals("demote")
                    || sub.equals("rank") || sub.equals("leader") || sub.equals("deinvite")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                        out.add(p.getName());
                    }
                }
            } else if (sub.equals("join") || sub.equals("show") || sub.equals("who") || sub.equals("info")
                    || sub.equals("enemy") || sub.equals("neutral")) {
                ConfigurationSection section = file.get().getConfigurationSection("factions");
                if (section != null) {
                    for (String id : section.getKeys(false)) {
                        String name = displayName(id);
                        if (name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                            out.add(name);
                        }
                    }
                }
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("rank")) {
            for (String s : Arrays.asList("co-leader", "officier", "membre", "recrue")) {
                if (s.startsWith(args[2].toLowerCase(Locale.ROOT))) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    private void help(Player player) {
        plugin.msg(player, "&6&lFaction &7- commandes");
        plugin.msg(player, "&e/f create <nom> &8- &7Créer une faction");
        plugin.msg(player, "&e/f show [faction] &8- &7Infos d'une faction");
        plugin.msg(player, "&e/f invite/kick/promote/demote <joueur>");
        plugin.msg(player, "&e/f rank <joueur> <grade> &8- &7Leader, Co-leader, Officier, Membre, Recrue");
        plugin.msg(player, "&e/f leader <joueur> &8- &7Transférer le lead");
        plugin.msg(player, "&e/f join/leave/disband");
        plugin.msg(player, "&e/f claim/unclaim/map/home");
        plugin.msg(player, "&e/f enemy/neutral <faction>");
        plugin.msg(player, "&e/f upgrade &8- &7Menu des améliorations");
        plugin.msg(player, "&e/f perm &8- &7Menu des permissions");
        plugin.msg(player, "&e/f chest, /f fly, /f chat, /f list");
    }

    private void create(Player player, String name) {
        if (!factionOf(player).isEmpty()) {
            plugin.msg(player, "&cTu es déjà dans une faction.");
            return;
        }
        if (!validName(name)) {
            plugin.msg(player, "&cNom invalide (3-16, lettres/chiffres).");
            return;
        }
        String id = name.toLowerCase(Locale.ROOT);
        if (exists(id)) {
            plugin.msg(player, "&cCe nom existe déjà.");
            return;
        }
        String path = "factions." + id;
        file.get().set(path + ".name", name);
        file.get().set(path + ".leader", player.getUniqueId().toString());
        file.get().set(path + ".members", Collections.singletonList(player.getUniqueId().toString()));
        file.get().set(path + ".ranks." + player.getUniqueId().toString(), FactionRank.LEADER.name());
        file.get().set(path + ".created", System.currentTimeMillis());
        file.get().set(path + ".description", plugin.getConfig().getString("factions.default-description", "Aucune description."));
        file.get().set(path + ".open", false);
        file.get().set(path + ".fly", false);
        file.get().set(path + ".chest-size", plugin.getConfig().getInt("factions.chest-size", 27));
        file.save();
        plugin.data().setString(player.getUniqueId(), "faction", id);
        setPower(player.getUniqueId(), plugin.getConfig().getDouble("factions.power.start", 10));
        plugin.msg(player, "&aFaction &e" + name + " &acréée.");
        broadcast("&6" + player.getName() + " &7a créé la faction &e" + name + "&7.");
    }

    private void disband(Player player, boolean confirm) {
        String fac = factionOf(player);
        if (fac.isEmpty()) {
            plugin.msg(player, "&cPas de faction.");
            return;
        }
        if (rankOf(player) != FactionRank.LEADER) {
            plugin.msg(player, "&cSeul le chef peut dissoudre la faction.");
            return;
        }
        if (!confirm) {
            pendingDisband.put(player.getUniqueId(), fac);
            plugin.msg(player, "&cConfirme avec &e/f disband confirm");
            return;
        }
        if (!fac.equals(pendingDisband.get(player.getUniqueId()))) {
            plugin.msg(player, "&cRefais &e/f disband &cpuis confirme.");
            return;
        }
        pendingDisband.remove(player.getUniqueId());
        String name = displayName(fac);
        for (String raw : members(fac)) {
            UUID uuid = UUID.fromString(raw);
            plugin.data().setString(uuid, "faction", "");
            Player online = Bukkit.getPlayer(uuid);
            if (online != null && online.getAllowFlight() && !online.hasPermission("sydaria.fly")) {
                online.setAllowFlight(false);
                online.setFlying(false);
            }
        }
        removeClaims(fac);
        file.get().set("factions." + fac, null);
        file.save();
        plugin.msg(player, "&cFaction dissoute.");
        broadcast("&cLa faction &e" + name + " &ca été dissoute.");
    }

    private void invite(Player player, String targetName) {
        String fac = requireFac(player);
        if (fac == null) return;
        if (!hasPerm(player, FactionPerm.INVITE)) {
            plugin.msg(player, "&cTu ne peux pas inviter.");
            return;
        }
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            plugin.msg(player, "&cJoueur hors-ligne.");
            return;
        }
        if (!factionOf(target).isEmpty()) {
            plugin.msg(player, "&cCe joueur a déjà une faction.");
            return;
        }
        List<String> invites = file.get().getStringList("factions." + fac + ".invites");
        if (!invites.contains(target.getUniqueId().toString())) {
            invites.add(target.getUniqueId().toString());
        }
        file.get().set("factions." + fac + ".invites", invites);
        file.save();
        plugin.msg(player, "&aInvitation envoyée à &e" + target.getName());
        plugin.msg(target, "&e" + player.getName() + " &7t'invite dans &6" + displayName(fac) + "&7. &e/f join " + displayName(fac));
    }

    private void deinvite(Player player, String targetName) {
        String fac = requireFac(player);
        if (fac == null) return;
        if (!hasPerm(player, FactionPerm.INVITE)) {
            plugin.msg(player, "&cTu ne peux pas gérer les invitations.");
            return;
        }
        Player target = Bukkit.getPlayer(targetName);
        UUID uuid = target != null ? target.getUniqueId() : findOffline(targetName);
        if (uuid == null) {
            plugin.msg(player, "&cJoueur introuvable.");
            return;
        }
        List<String> invites = file.get().getStringList("factions." + fac + ".invites");
        invites.remove(uuid.toString());
        file.get().set("factions." + fac + ".invites", invites);
        file.save();
        plugin.msg(player, "&eInvitation annulée.");
    }

    private void join(Player player, String name) {
        if (!factionOf(player).isEmpty()) {
            plugin.msg(player, "&cTu es déjà dans une faction.");
            return;
        }
        String id = resolveFaction(name);
        if (id == null) {
            plugin.msg(player, "&cFaction introuvable.");
            return;
        }
        boolean open = file.get().getBoolean("factions." + id + ".open", false);
        List<String> invites = file.get().getStringList("factions." + id + ".invites");
        if (!open && !invites.contains(player.getUniqueId().toString()) && !player.hasPermission("sydaria.admin")) {
            plugin.msg(player, "&cPas d'invitation.");
            return;
        }
        if (members(id).size() >= maxMembers(id)) {
            plugin.msg(player, "&cCette faction est pleine.");
            return;
        }
        invites.remove(player.getUniqueId().toString());
        List<String> members = members(id);
        members.add(player.getUniqueId().toString());
        file.get().set("factions." + id + ".members", members);
        file.get().set("factions." + id + ".invites", invites);
        file.get().set("factions." + id + ".ranks." + player.getUniqueId().toString(), FactionRank.RECRUIT.name());
        file.save();
        plugin.data().setString(player.getUniqueId(), "faction", id);
        setPower(player.getUniqueId(), plugin.getConfig().getDouble("factions.power.start", 10));
        plugin.msg(player, "&aTu as rejoint &e" + displayName(id));
        notifyMembers(id, "&e" + player.getName() + " &7a rejoint la faction.");
    }

    private void kick(Player player, String targetName) {
        String fac = requireFac(player);
        if (fac == null) return;
        if (!hasPerm(player, FactionPerm.KICK)) {
            plugin.msg(player, "&cTu ne peux pas expulser.");
            return;
        }
        UUID uuid = findMember(fac, targetName);
        if (uuid == null) {
            plugin.msg(player, "&cCe joueur n'est pas dans la faction.");
            return;
        }
        if (uuid.equals(player.getUniqueId())) {
            plugin.msg(player, "&cUtilise &e/f leave");
            return;
        }
        FactionRank mine = rankOf(player);
        FactionRank theirs = rankOf(fac, uuid);
        if (theirs.weight() >= mine.weight()) {
            plugin.msg(player, "&cTu ne peux pas expulser ce grade.");
            return;
        }
        removeMember(fac, uuid, "&cTu as été expulsé de &e" + displayName(fac));
        plugin.msg(player, "&e" + plugin.data().nameOf(uuid) + " &7a été expulsé.");
    }

    private void leave(Player player) {
        String fac = requireFac(player);
        if (fac == null) return;
        if (rankOf(player) == FactionRank.LEADER) {
            plugin.msg(player, "&cTransfère le lead (&e/f leader&c) ou dissous (&e/f disband&c).");
            return;
        }
        removeMember(fac, player.getUniqueId(), "&cTu as quitté la faction.");
        notifyMembers(fac, "&e" + player.getName() + " &7a quitté la faction.");
    }

    private void changeRank(Player player, String targetName, boolean up) {
        String fac = requireFac(player);
        if (fac == null) return;
        if (!hasPerm(player, FactionPerm.PROMOTE)) {
            plugin.msg(player, "&cTu ne peux pas changer les grades.");
            return;
        }
        UUID uuid = findMember(fac, targetName);
        if (uuid == null) {
            plugin.msg(player, "&cMembre introuvable.");
            return;
        }
        FactionRank current = rankOf(fac, uuid);
        if (current == FactionRank.LEADER) {
            plugin.msg(player, "&cImpossible de modifier le leader. Utilise &e/f leader&c.");
            return;
        }
        FactionRank actor = rankOf(player);
        if (current.weight() >= actor.weight() && actor != FactionRank.LEADER) {
            plugin.msg(player, "&cTu ne peux pas changer le grade de ce membre.");
            return;
        }
        FactionRank next = up ? current.promote() : current.demote();
        if (next == null) {
            plugin.msg(player, up ? "&cDéjà au grade max (hors leader)." : "&cDéjà recrue.");
            return;
        }
        if (next.weight() >= actor.weight() && actor != FactionRank.LEADER) {
            plugin.msg(player, "&cTu ne peux pas promouvoir à ce grade.");
            return;
        }
        applyRank(player, fac, uuid, next);
    }

    private void setRank(Player player, String targetName, String rankName) {
        String fac = requireFac(player);
        if (fac == null) return;
        if (!hasPerm(player, FactionPerm.PROMOTE)) {
            plugin.msg(player, "&cTu ne peux pas changer les grades.");
            return;
        }
        UUID uuid = findMember(fac, targetName);
        if (uuid == null) {
            plugin.msg(player, "&cMembre introuvable.");
            return;
        }
        FactionRank wanted = FactionRank.parse(rankName);
        if (wanted == null) {
            plugin.msg(player, "&cGrades : &eCo-leader, Officier, Membre, Recrue");
            return;
        }
        if (wanted == FactionRank.LEADER) {
            plugin.msg(player, "&cUtilise &e/f leader <joueur> &cpour transférer le lead.");
            return;
        }
        FactionRank current = rankOf(fac, uuid);
        if (current == FactionRank.LEADER) {
            plugin.msg(player, "&cImpossible de modifier le leader. Utilise &e/f leader&c.");
            return;
        }
        FactionRank actor = rankOf(player);
        if (current.weight() >= actor.weight() && actor != FactionRank.LEADER) {
            plugin.msg(player, "&cTu ne peux pas changer le grade de ce membre.");
            return;
        }
        if (wanted.weight() >= actor.weight() && actor != FactionRank.LEADER) {
            plugin.msg(player, "&cTu ne peux pas attribuer ce grade.");
            return;
        }
        applyRank(player, fac, uuid, wanted);
    }

    private void applyRank(Player player, String fac, UUID uuid, FactionRank next) {
        file.get().set("factions." + fac + ".ranks." + uuid.toString(), next.name());
        file.save();
        plugin.msg(player, "&e" + plugin.data().nameOf(uuid) + " &7est maintenant &e" + next.display());
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            plugin.msg(online, "&7Ton grade de faction : &e" + next.display());
        }
    }

    private void transfer(Player player, String targetName) {
        String fac = requireFac(player);
        if (fac == null) return;
        if (rankOf(player) != FactionRank.LEADER) {
            plugin.msg(player, "&cSeul le chef peut transférer le lead.");
            return;
        }
        UUID uuid = findMember(fac, targetName);
        if (uuid == null) {
            plugin.msg(player, "&cMembre introuvable.");
            return;
        }
        file.get().set("factions." + fac + ".leader", uuid.toString());
        file.get().set("factions." + fac + ".ranks." + player.getUniqueId().toString(), FactionRank.COLEADER.name());
        file.get().set("factions." + fac + ".ranks." + uuid.toString(), FactionRank.LEADER.name());
        file.save();
        plugin.msg(player, "&e" + plugin.data().nameOf(uuid) + " &7est le nouveau chef.");
        notifyMembers(fac, "&6" + plugin.data().nameOf(uuid) + " &7est le nouveau chef de faction.");
    }

    private void show(Player player, String query) {
        String id;
        if (query == null || query.isEmpty()) {
            id = factionOf(player);
            if (id.isEmpty()) {
                plugin.msg(player, "&cTu n'es dans aucune faction. &e/f show <nom>");
                return;
            }
        } else {
            id = resolveFaction(query);
            if (id == null) {
                Player target = Bukkit.getPlayer(query);
                if (target != null) {
                    id = factionOf(target);
                } else {
                    UUID offline = findOffline(query);
                    if (offline != null) {
                        id = factionOf(offline);
                    }
                }
            }
            if (id == null || id.isEmpty()) {
                plugin.msg(player, "&cFaction introuvable.");
                return;
            }
        }
        String name = displayName(id);
        String desc = file.get().getString("factions." + id + ".description", "Aucune description.");
        String motd = file.get().getString("factions." + id + ".motd", "");
        long created = file.get().getLong("factions." + id + ".created", 0L);
        boolean open = file.get().getBoolean("factions." + id + ".open", false);
        player.sendMessage(CC.color("&8&m-----------&r &6" + name + " &8&m-----------"));
        player.sendMessage(CC.color("&eDescription &7» &f" + desc));
        if (motd != null && !motd.isEmpty()) {
            player.sendMessage(CC.color("&eMOTD &7» &f" + motd));
        }
        player.sendMessage(CC.color("&eLand / Power &7» &f" + claimCount(id) + " claims &8| &e"
                + formatPower(factionPower(id)) + "&7/&e" + formatPower(factionMaxPower(id))));
        player.sendMessage(CC.color("&eMembres &7» &f" + members(id).size() + "&7/&f" + maxMembers(id)
                + " &8| &eOuverte &7» &f" + (open ? "Oui" : "Non")));
        if (created > 0) {
            player.sendMessage(CC.color("&eCréée le &7» &f" + new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date(created))));
        }
        player.sendMessage(CC.color("&eEnnemis &7» &c" + relNames(id, "enemies")));
        player.sendMessage(CC.color("&6Grades de faction"));
        for (FactionRank rank : new FactionRank[] {
                FactionRank.LEADER, FactionRank.COLEADER, FactionRank.OFFICER, FactionRank.MEMBER, FactionRank.RECRUIT
        }) {
            player.sendMessage(CC.color("&8▪ " + rank.color() + rank.showLabel() + " &7» " + membersOfRank(id, rank)));
        }
        player.sendMessage(CC.color("&8&m--------------------------------"));
    }

    private String membersOfRank(String fac, FactionRank rank) {
        List<String> names = new ArrayList<String>();
        for (String raw : members(fac)) {
            UUID uuid = UUID.fromString(raw);
            if (rankOf(fac, uuid) != rank) {
                continue;
            }
            String name = plugin.data().nameOf(uuid);
            Player online = Bukkit.getPlayer(uuid);
            if (online != null && online.isOnline()) {
                names.add("&a" + name);
            } else {
                names.add("&7" + name);
            }
        }
        return names.isEmpty() ? "&8-" : joinComma(names);
    }

    private void list(Player player, int page) {
        ConfigurationSection section = file.get().getConfigurationSection("factions");
        List<String> ids = section == null ? new ArrayList<String>() : new ArrayList<String>(section.getKeys(false));
        int per = 8;
        int pages = Math.max(1, (int) Math.ceil(ids.size() / (double) per));
        page = Math.max(1, Math.min(page, pages));
        plugin.msg(player, "&6Factions &7(page " + page + "/" + pages + ")");
        int start = (page - 1) * per;
        for (int i = start; i < start + per && i < ids.size(); i++) {
            String id = ids.get(i);
            int on = 0;
            for (String raw : members(id)) {
                Player p = Bukkit.getPlayer(UUID.fromString(raw));
                if (p != null && p.isOnline()) on++;
            }
            player.sendMessage(CC.color("&8- &e" + displayName(id) + " &7(" + on + "/" + members(id).size()
                    + ") &8| &e" + claimCount(id) + " claims &8| &e" + formatPower(factionPower(id)) + " pwr"));
        }
    }

    private void desc(Player player, String[] args) {
        String fac = requireFac(player);
        if (fac == null) return;
        if (!hasPerm(player, FactionPerm.DESC)) {
            plugin.msg(player, "&cTu ne peux pas changer la description.");
            return;
        }
        if (args.length < 2) {
            plugin.msg(player, "&eDescription &7» &f" + file.get().getString("factions." + fac + ".description"));
            return;
        }
        String text = joinArgs(args, 1);
        int max = plugin.getConfig().getInt("factions.max-description-length", 80);
        if (text.length() > max) {
            plugin.msg(player, "&cDescription trop longue.");
            return;
        }
        file.get().set("factions." + fac + ".description", text);
        file.save();
        plugin.msg(player, "&aDescription mise à jour.");
    }

    private void motd(Player player, String[] args) {
        String fac = requireFac(player);
        if (fac == null) return;
        if (args.length < 2) {
            plugin.msg(player, "&eMOTD &7» &f" + file.get().getString("factions." + fac + ".motd", "-"));
            return;
        }
        if (!hasPerm(player, FactionPerm.MOTD)) {
            plugin.msg(player, "&cTu ne peux pas changer le MOTD.");
            return;
        }
        file.get().set("factions." + fac + ".motd", joinArgs(args, 1));
        file.save();
        plugin.msg(player, "&aMOTD mis à jour.");
    }

    private void rename(Player player, String name) {
        String fac = requireFac(player);
        if (fac == null) return;
        if (rankOf(player) != FactionRank.LEADER) {
            plugin.msg(player, "&cSeul le chef peut renommer.");
            return;
        }
        if (!validName(name)) {
            plugin.msg(player, "&cNom invalide.");
            return;
        }
        String newId = name.toLowerCase(Locale.ROOT);
        if (!newId.equals(fac) && exists(newId)) {
            plugin.msg(player, "&cCe nom est pris.");
            return;
        }
        file.get().set("factions." + fac + ".name", name);
        file.save();
        plugin.msg(player, "&aFaction renommée en &e" + name);
    }

    private void setOpen(Player player, boolean open) {
        String fac = requireFac(player);
        if (fac == null) return;
        if (!hasPerm(player, FactionPerm.OPEN)) {
            plugin.msg(player, "&cTu ne peux pas changer ça.");
            return;
        }
        file.get().set("factions." + fac + ".open", open);
        file.save();
        plugin.msg(player, open ? "&aFaction ouverte (join sans invite)." : "&eFaction fermée.");
    }

    private void openChest(Player player) {
        String fac = requireFac(player);
        if (fac == null) return;
        if (!hasPerm(player, FactionPerm.CHEST)) {
            plugin.msg(player, "&cTu n'as pas accès au coffre.");
            return;
        }
        int size = chestSize(fac);
        Inventory inv = Bukkit.createInventory(new ChestHolder(fac), size, CC.color("&8Coffre &e" + displayName(fac)));
        List<?> stored = file.get().getList("factions." + fac + ".chest");
        if (stored != null) {
            for (int i = 0; i < stored.size() && i < size; i++) {
                Object o = stored.get(i);
                if (o instanceof ItemStack) {
                    inv.setItem(i, (ItemStack) o);
                }
            }
        }
        player.openInventory(inv);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof ChestHolder)) {
            return;
        }
        ChestHolder holder = (ChestHolder) event.getInventory().getHolder();
        List<ItemStack> items = new ArrayList<ItemStack>();
        for (ItemStack stack : event.getInventory().getContents()) {
            items.add(stack == null ? new ItemStack(Material.AIR) : stack);
        }
        file.get().set("factions." + holder.fac + ".chest", items);
        file.save();
    }

    public void buyUpgrade(Player player, String type) {
        String fac = factionOf(player);
        if (fac.isEmpty()) {
            return;
        }
        if ("fly".equals(type)) {
            if (hasFly(fac)) {
                plugin.msg(player, "&eFly déjà débloqué.");
                return;
            }
            int cost = plugin.getConfig().getInt("factions.upgrades.fly.cost",
                    plugin.getConfig().getInt("factions.fly-upgrade-cost", 2500));
            if (!plugin.tokens().take(player.getUniqueId(), cost)) {
                plugin.msg(player, "&cIl faut &e" + cost + " tokens&c.");
                return;
            }
            file.get().set("factions." + fac + ".fly", true);
            file.get().set("factions." + fac + ".upgrades.fly", 1);
            file.save();
            plugin.msg(player, "&aFly faction débloqué. &e/f fly &7dans tes claims.");
            return;
        }
        List<Integer> costs = upgradeInts(type, "costs", Collections.singletonList(0));
        int level = upgradeLevel(fac, type);
        if (level >= costs.size() - 1) {
            plugin.msg(player, "&eNiveau maximum atteint.");
            return;
        }
        int cost = costs.get(level + 1);
        if (!plugin.tokens().take(player.getUniqueId(), cost)) {
            plugin.msg(player, "&cIl faut &e" + cost + " tokens&c.");
            return;
        }
        file.get().set("factions." + fac + ".upgrades." + type, level + 1);
        if ("chest".equals(type)) {
            file.get().set("factions." + fac + ".chest-size", chestSize(fac));
        }
        file.save();
        plugin.msg(player, "&aUpgrade acheté.");
    }

    private void fly(Player player) {
        String fac = requireFac(player);
        if (fac == null) return;
        if (!hasFly(fac) && !player.hasPermission("sydaria.fly")) {
            plugin.msg(player, "&cUpgrade fly non débloqué. &e/f upgrade");
            return;
        }
        if (!hasPerm(player, FactionPerm.FLY) && !player.hasPermission("sydaria.fly")) {
            plugin.msg(player, "&cTu n'as pas le droit d'utiliser le fly.");
            return;
        }
        if (!player.hasPermission("sydaria.fly")) {
            String owner = claimAt(player.getLocation());
            if (owner == null || !owner.equalsIgnoreCase(fac)) {
                plugin.msg(player, "&cLe fly faction fonctionne uniquement dans tes claims.");
                return;
            }
        }
        boolean now = !player.getAllowFlight();
        player.setAllowFlight(now);
        player.setFlying(now);
        plugin.msg(player, now ? "&aFly activé." : "&cFly désactivé.");
    }

    private void home(Player player) {
        String fac = requireFac(player);
        if (fac == null) return;
        if (!hasPerm(player, FactionPerm.HOME)) {
            plugin.msg(player, "&cTu ne peux pas utiliser le home.");
            return;
        }
        Location loc = Locations.deserialize(file.get().getString("factions." + fac + ".home"));
        if (loc == null) {
            plugin.msg(player, "&cAucun home défini. &e/f sethome");
            return;
        }
        player.teleport(loc);
        plugin.msg(player, "&aTéléporté au home faction.");
    }

    private void sethome(Player player) {
        String fac = requireFac(player);
        if (fac == null) return;
        if (!hasPerm(player, FactionPerm.SETHOME)) {
            plugin.msg(player, "&cTu ne peux pas définir le home.");
            return;
        }
        String owner = claimAt(player.getLocation());
        if (owner == null || !owner.equalsIgnoreCase(fac)) {
            plugin.msg(player, "&cLe home doit être dans un claim de ta faction.");
            return;
        }
        file.get().set("factions." + fac + ".home", Locations.serialize(player.getLocation()));
        file.save();
        plugin.msg(player, "&aHome faction défini.");
    }

    private void delhome(Player player) {
        String fac = requireFac(player);
        if (fac == null) return;
        if (!hasPerm(player, FactionPerm.SETHOME)) {
            plugin.msg(player, "&cTu ne peux pas faire ça.");
            return;
        }
        file.get().set("factions." + fac + ".home", null);
        file.save();
        plugin.msg(player, "&eHome supprimé.");
    }

    private void claim(Player player) {
        if (!plugin.getConfig().getBoolean("factions.claim.enabled", true)) {
            plugin.msg(player, "&cLes claims sont désactivés.");
            return;
        }
        String fac = requireFac(player);
        if (fac == null) return;
        if (!hasPerm(player, FactionPerm.CLAIM)) {
            plugin.msg(player, "&cTu ne peux pas claim.");
            return;
        }
        if (!claimWorld(player.getWorld())) {
            plugin.msg(player, "&cTu ne peux pas claim dans ce monde.");
            return;
        }
        Chunk chunk = player.getLocation().getChunk();
        String existing = claimAt(player.getLocation());
        if (existing != null) {
            plugin.msg(player, "&cDéjà claim par &e" + displayName(existing));
            return;
        }
        if (claimCount(fac) + 1 > factionPower(fac)) {
            plugin.msg(player, "&cPas assez de power. &7(" + formatPower(factionPower(fac)) + ")");
            return;
        }
        file.get().set("claims." + claimKey(chunk), fac);
        file.save();
        plugin.msg(player, "&aChunk claim &7(" + chunk.getX() + ", " + chunk.getZ() + ")");
    }

    private void unclaim(Player player, boolean all) {
        String fac = requireFac(player);
        if (fac == null) return;
        if (!hasPerm(player, FactionPerm.UNCLAIM)) {
            plugin.msg(player, "&cTu ne peux pas unclaim.");
            return;
        }
        if (all) {
            int n = removeClaims(fac);
            file.save();
            plugin.msg(player, "&e" + n + " claims retirés.");
            return;
        }
        String existing = claimAt(player.getLocation());
        if (existing == null || !existing.equalsIgnoreCase(fac)) {
            plugin.msg(player, "&cCe chunk n'est pas à toi.");
            return;
        }
        file.get().set("claims." + claimKey(player.getLocation().getChunk()), null);
        file.save();
        plugin.msg(player, "&eChunk unclaim.");
    }

    private void map(Player player) {
        Chunk center = player.getLocation().getChunk();
        String mine = factionOf(player);
        plugin.msg(player, "&6Carte des claims &7(vous = &a+)");
        StringBuilder legend = new StringBuilder();
        Set<String> seen = new HashSet<String>();
        for (int dz = -4; dz <= 4; dz++) {
            StringBuilder line = new StringBuilder();
            for (int dx = -8; dx <= 8; dx++) {
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                String owner = file.get().getString("claims." + player.getWorld().getName() + ";" + x + ";" + z);
                String symbol;
                if (dx == 0 && dz == 0) {
                    symbol = "&a+";
                } else if (owner == null) {
                    symbol = "&7-";
                } else if (owner.equalsIgnoreCase(mine)) {
                    symbol = "&2#";
                } else {
                    FactionRelation rel = relationOf(mine, owner);
                    if (rel == FactionRelation.ENEMY) symbol = "&cX";
                    else symbol = "&8/";
                    if (seen.add(owner)) {
                        legend.append(CC.color(rel.color() + displayName(owner) + " "));
                    }
                }
                line.append(symbol);
            }
            player.sendMessage(CC.color(line.toString()));
        }
        plugin.msg(player, "&7&2# &7toi  &cX &7ennemi  &7- wilderness");
        if (legend.length() > 0) {
            plugin.msg(player, "&7Légende : " + legend);
        }
    }

    private void setRelation(Player player, String name, FactionRelation wanted) {
        String fac = requireFac(player);
        if (fac == null) return;
        if (!hasPerm(player, FactionPerm.RELATIONS)) {
            plugin.msg(player, "&cTu ne peux pas gérer les relations.");
            return;
        }
        String other = resolveFaction(name);
        if (other == null) {
            plugin.msg(player, "&cFaction introuvable.");
            return;
        }
        if (other.equalsIgnoreCase(fac)) {
            plugin.msg(player, "&cImpossible.");
            return;
        }
        clearRelations(fac, other);
        if (wanted == FactionRelation.ENEMY) {
            addRel(fac, "enemies", other);
            addRel(other, "enemies", fac);
        }
        file.save();
        String label = wanted == FactionRelation.NEUTRAL ? "neutre" : wanted.name().toLowerCase(Locale.ROOT);
        notifyMembers(fac, "&7Relation avec &e" + displayName(other) + " &7: " + wanted.color() + label);
        notifyMembers(other, "&7Relation avec &e" + displayName(fac) + " &7: " + wanted.color() + label);
    }

    private void cycleChat(Player player) {
        if (factionOf(player).isEmpty()) {
            plugin.msg(player, "&cPas de faction.");
            return;
        }
        FactionChatMode current = chatModes.get(player.getUniqueId());
        FactionChatMode next;
        if (current == null || current == FactionChatMode.PUBLIC) {
            next = FactionChatMode.FACTION;
        } else {
            next = FactionChatMode.PUBLIC;
        }
        if (next == FactionChatMode.PUBLIC) {
            chatModes.remove(player.getUniqueId());
        } else {
            chatModes.put(player.getUniqueId(), next);
        }
        if (next == FactionChatMode.FACTION) {
            plugin.msg(player, "&aChat faction activé.");
        } else {
            plugin.msg(player, "&7Chat public.");
        }
    }

    private void sendQuickChat(Player player, String message, boolean ignored) {
        String fac = requireFac(player);
        if (fac == null) return;
        String format = plugin.getConfig().getString("factions.chat-format", "&8[&6{faction}&8] &e{player}&7: &f{message}");
        String line = CC.color(format.replace("{faction}", displayName(fac)).replace("{player}", player.getName()).replace("{message}", message));
        sendFactionMessage(fac, line);
    }

    private void sendFactionMessage(String fac, String line) {
        for (String raw : members(fac)) {
            Player p = Bukkit.getPlayer(UUID.fromString(raw));
            if (p != null) {
                p.sendMessage(line);
            }
        }
    }

    private void notifyMembers(String fac, String message) {
        for (String raw : members(fac)) {
            Player p = Bukkit.getPlayer(UUID.fromString(raw));
            if (p != null) {
                plugin.msg(p, message);
            }
        }
    }

    private void removeMember(String fac, UUID uuid, String msg) {
        List<String> members = members(fac);
        members.remove(uuid.toString());
        file.get().set("factions." + fac + ".members", members);
        file.get().set("factions." + fac + ".ranks." + uuid.toString(), null);
        file.save();
        plugin.data().setString(uuid, "faction", "");
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            if (online.getAllowFlight() && !online.hasPermission("sydaria.fly")) {
                online.setAllowFlight(false);
                online.setFlying(false);
            }
            plugin.msg(online, msg);
        }
    }

    private int removeClaims(String fac) {
        ConfigurationSection section = file.get().getConfigurationSection("claims");
        if (section == null) {
            return 0;
        }
        List<String> remove = new ArrayList<String>();
        for (String key : section.getKeys(false)) {
            if (fac.equalsIgnoreCase(section.getString(key))) {
                remove.add(key);
            }
        }
        for (String key : remove) {
            file.get().set("claims." + key, null);
        }
        return remove.size();
    }

    private String requireFac(Player player) {
        String fac = factionOf(player);
        if (fac.isEmpty()) {
            plugin.msg(player, "&cPas de faction.");
            return null;
        }
        return fac;
    }

    private boolean exists(String id) {
        return file.get().contains("factions." + id);
    }

    private boolean validName(String name) {
        int min = plugin.getConfig().getInt("factions.min-name-length", 3);
        int max = plugin.getConfig().getInt("factions.max-name-length", 16);
        return name != null && name.matches("[A-Za-z0-9_]+") && name.length() >= min && name.length() <= max;
    }

    private String resolveFaction(String name) {
        if (name == null) {
            return null;
        }
        String id = name.toLowerCase(Locale.ROOT);
        if (exists(id)) {
            return id;
        }
        ConfigurationSection section = file.get().getConfigurationSection("factions");
        if (section == null) {
            return null;
        }
        for (String key : section.getKeys(false)) {
            if (displayName(key).equalsIgnoreCase(name)) {
                return key;
            }
        }
        return null;
    }

    private UUID findMember(String fac, String name) {
        Player online = Bukkit.getPlayer(name);
        if (online != null && fac.equalsIgnoreCase(factionOf(online))) {
            return online.getUniqueId();
        }
        for (String raw : members(fac)) {
            UUID uuid = UUID.fromString(raw);
            if (plugin.data().nameOf(uuid).equalsIgnoreCase(name)) {
                return uuid;
            }
        }
        return null;
    }

    private UUID findOffline(String name) {
        ConfigurationSection players = plugin.data().section();
        if (players == null) {
            return null;
        }
        for (String key : players.getKeys(false)) {
            if (name.equalsIgnoreCase(players.getString(key + ".name"))) {
                return UUID.fromString(key);
            }
        }
        OfflinePlayer off = Bukkit.getOfflinePlayer(name);
        return off != null && off.hasPlayedBefore() ? off.getUniqueId() : null;
    }

    private boolean listContains(String path, String value) {
        for (String s : file.get().getStringList(path)) {
            if (s.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private void addRel(String fac, String key, String other) {
        List<String> list = file.get().getStringList("factions." + fac + "." + key);
        if (!list.contains(other)) {
            list.add(other);
        }
        file.get().set("factions." + fac + "." + key, list);
    }

    private void clearRelations(String a, String b) {
        removeRel(a, "allies", b);
        removeRel(a, "truces", b);
        removeRel(a, "enemies", b);
        removeRel(b, "allies", a);
        removeRel(b, "truces", a);
        removeRel(b, "enemies", a);
    }

    private void removeRel(String fac, String key, String other) {
        List<String> list = file.get().getStringList("factions." + fac + "." + key);
        list.remove(other);
        file.get().set("factions." + fac + "." + key, list);
    }

    private String relNames(String fac, String key) {
        List<String> list = file.get().getStringList("factions." + fac + "." + key);
        if (list.isEmpty()) {
            return "Aucun";
        }
        List<String> names = new ArrayList<String>();
        for (String id : list) {
            names.add(displayName(id));
        }
        return joinComma(names);
    }

    private String claimKey(Chunk chunk) {
        return chunk.getWorld().getName() + ";" + chunk.getX() + ";" + chunk.getZ();
    }

    private boolean claimWorld(World world) {
        List<String> worlds = plugin.getConfig().getStringList("factions.claim.worlds");
        if (worlds == null || worlds.isEmpty()) {
            return true;
        }
        return worlds.contains(world.getName());
    }

    private void broadcast(String message) {
        Bukkit.broadcastMessage(CC.color(plugin.prefix() + message));
    }

    private String joinArgs(String[] args, int start) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (i > start) sb.append(' ');
            sb.append(args[i]);
        }
        return sb.toString();
    }

    private String joinComma(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append("&7, ");
            sb.append(list.get(i));
        }
        return sb.toString();
    }

    private int parseInt(String raw, int def) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private String formatPower(double value) {
        if (Math.abs(value - Math.round(value)) < 0.05) {
            return String.valueOf((int) Math.round(value));
        }
        return String.format(Locale.US, "%.1f", value);
    }

    public void save() {
        file.save();
    }

    static class ChestHolder implements InventoryHolder {
        private final String fac;

        ChestHolder(String fac) {
            this.fac = fac;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
