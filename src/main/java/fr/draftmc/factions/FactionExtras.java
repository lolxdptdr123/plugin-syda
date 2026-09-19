package fr.draftmc.factions;

import fr.draftmc.Draftmc;
import fr.draftmc.gui.GuiHolder;
import fr.draftmc.gui.Menus;
import fr.draftmc.util.CC;
import fr.draftmc.util.ItemBuilder;
import fr.draftmc.util.Locations;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class FactionExtras implements Listener {
    public static final String WAND_NAME = "&6Sélecteur de zone privée";
    private static final SimpleDateFormat TIME = new SimpleDateFormat("dd/MM HH:mm");

    private final Draftmc plugin;
    private final FactionManager factions;
    private final Map<UUID, Location> pos1 = new HashMap<UUID, Location>();
    private final Map<UUID, Location> pos2 = new HashMap<UUID, Location>();

    public FactionExtras(Draftmc plugin, FactionManager factions) {
        this.plugin = plugin;
        this.factions = factions;
    }

    public void addLog(String fac, String type, String message) {
        String path = "factions." + fac + ".logs." + type;
        List<String> logs = new ArrayList<String>(factions.store().get().getStringList(path));
        logs.add(0, TIME.format(new Date()) + " | " + message);
        int max = plugin.getConfig().getInt("factions.logs.max", 50);
        while (logs.size() > max) {
            logs.remove(logs.size() - 1);
        }
        factions.store().get().set(path, logs);
        factions.store().save();
    }

    public List<String> logs(String fac, String type) {
        return new ArrayList<String>(factions.store().get().getStringList("factions." + fac + ".logs." + type));
    }

    public int prestige(String fac) {
        return factions.store().get().getInt("factions." + fac + ".prestige", 0);
    }

    public int extraMembers(String fac) {
        return plugin.getConfig().getInt("faction-prestige.levels." + prestige(fac) + ".extra-members", 0);
    }

    public int extraWarps(String fac) {
        return plugin.getConfig().getInt("faction-prestige.levels." + prestige(fac) + ".extra-warps", 0);
    }

    public int maxWarps(String fac) {
        return plugin.getConfig().getInt("factions.warps.base", 1) + extraWarps(fac);
    }

    public int pvpPoints(String fac) {
        return factions.store().get().getInt("factions." + fac + ".points.pvp", 0);
    }

    public int farmPoints(String fac) {
        return factions.store().get().getInt("factions." + fac + ".points.farm", 0);
    }

    public void addPoints(String fac, String type, int amount) {
        if (fac == null || fac.isEmpty() || amount <= 0) {
            return;
        }
        String path = "factions." + fac + ".points." + type;
        factions.store().get().set(path, factions.store().get().getInt(path, 0) + amount);
    }

    public void addMission(String fac, String id, int amount) {
        if (fac == null || fac.isEmpty()) {
            return;
        }
        String path = "factions." + fac + ".missions." + id;
        factions.store().get().set(path, factions.store().get().getInt(path, 0) + amount);
    }

    public boolean accessAllows(Player player, Location location, FactionPerm perm) {
        String owner = factions.claimAt(location);
        if (owner == null) {
            return false;
        }
        ConfigurationSection zones = factions.store().get().getConfigurationSection("factions." + owner + ".access");
        if (zones == null) {
            return false;
        }
        for (String id : zones.getKeys(false)) {
            Location a = Locations.deserialize(zones.getString(id + ".pos1"));
            Location b = Locations.deserialize(zones.getString(id + ".pos2"));
            if (a == null || b == null || !inside(location, a, b)) {
                continue;
            }
            if (factions.factionOf(player).equalsIgnoreCase(owner)
                    && (factions.rankOf(player) == FactionRank.LEADER
                    || factions.rankOf(player) == FactionRank.COLEADER
                    || factions.hasPerm(player, FactionPerm.ACCESS))) {
                return true;
            }
            String allowed = zones.getString(id + ".players." + player.getUniqueId());
            if (allowed == null || allowed.isEmpty()) {
                return false;
            }
            return allowed.contains(perm.name()) || allowed.contains("ALL");
        }
        return false;
    }

    public boolean inPrivateZone(Location location) {
        String owner = factions.claimAt(location);
        if (owner == null) {
            return false;
        }
        ConfigurationSection zones = factions.store().get().getConfigurationSection("factions." + owner + ".access");
        if (zones == null) {
            return false;
        }
        for (String id : zones.getKeys(false)) {
            Location a = Locations.deserialize(zones.getString(id + ".pos1"));
            Location b = Locations.deserialize(zones.getString(id + ".pos2"));
            if (a != null && b != null && inside(location, a, b)) {
                return true;
            }
        }
        return false;
    }

    public void openLogs(Player player) {
        String fac = factions.factionOf(player);
        if (fac.isEmpty()) {
            plugin.msg(player, "&cTu n'es dans aucune faction.");
            return;
        }
        Inventory inv = Bukkit.createInventory(new GuiHolder("f-logs"), 45, CC.color("&8Logs faction"));
        Menus.fill(inv);
        inv.setItem(10, new ItemBuilder(Material.BOOK).name("&eLogs du Roster")
                .lore("&7Kicks, invites, join, leave.", "&eClique pour ouvrir.").build());
        inv.setItem(11, new ItemBuilder(Material.GOLD_HOE).name("&eLogs des claims")
                .lore("&7Qui a claim / unclaim.", "&eClique pour ouvrir.").build());
        inv.setItem(12, new ItemBuilder(Material.GOLD_SWORD).name("&eLogs des promotions")
                .lore("&7Qui a été promu.", "&eClique pour ouvrir.").build());
        inv.setItem(13, new ItemBuilder(Material.CHEST).name("&eLogs du f chest")
                .lore("&7Qui a pris / déposé.", "&eClique pour ouvrir.").build());
        inv.setItem(19, new ItemBuilder(Material.MAP).name("&aVos claims")
                .lore("&7Liste et coordonnées.", "&eClique pour ouvrir.").build());
        inv.setItem(21, new ItemBuilder(Material.IRON_FENCE).name("&dZone privée")
                .lore("&7Cuboïdes /f acces", "&eClique pour gérer.").build());
        inv.setItem(23, new ItemBuilder(Material.ENDER_CHEST).name("&6Coffre de faction")
                .lore("&7Ouvrir le /f chest", "&eClique pour ouvrir.").build());
        inv.setItem(25, new ItemBuilder(Material.ENDER_PEARL).name("&bWarps de faction")
                .lore("&7Liste des warps", "&eClique pour ouvrir.").build());
        inv.setItem(40, Menus.close());
        player.openInventory(inv);
    }

    public void openLogList(Player player, String type, String title) {
        String fac = factions.factionOf(player);
        Inventory inv = Bukkit.createInventory(new GuiHolder("f-loglist", type), 54, CC.color(title));
        Menus.fill(inv);
        List<String> lines = "claims-list".equals(type) ? claimLines(fac) : logs(fac, type);
        int slot = 10;
        if (lines.isEmpty()) {
            inv.setItem(22, new ItemBuilder(Material.BARRIER).name("&7Aucun log").build());
        } else {
            int max = Math.min(28, lines.size());
            for (int i = 0; i < max; i++) {
                inv.setItem(slot, new ItemBuilder(Material.PAPER).name("&f#" + (i + 1)).lore("&7" + lines.get(i)).build());
                slot++;
                if (slot % 9 == 8) {
                    slot += 2;
                }
            }
        }
        inv.setItem(45, Menus.back());
        inv.setItem(49, Menus.close());
        player.openInventory(inv);
    }

    public void openMenu(Player player) {
        String fac = factions.factionOf(player);
        if (fac.isEmpty()) {
            plugin.msg(player, "&cTu n'es dans aucune faction.");
            return;
        }
        Inventory inv = Bukkit.createInventory(new GuiHolder("f-menu"), 45, CC.color("&8Menu faction"));
        Menus.fill(inv);
        inv.setItem(4, new ItemBuilder(Material.NAME_TAG)
                .name("&6&l" + factions.displayName(fac))
                .lore("&7Prestige : &e" + prestige(fac),
                        "&7Membres : &e" + factions.members(fac).size() + "&7/&e" + factions.maxMembers(fac))
                .build());
        inv.setItem(20, new ItemBuilder(Material.DIAMOND_SWORD)
                .name("&cClassement PvP")
                .lore("&7Points : &e" + pvpPoints(fac), "&7Kills de la faction.").build());
        inv.setItem(24, new ItemBuilder(Material.DIAMOND_HOE)
                .name("&aClassement Farm")
                .lore("&7Points : &e" + farmPoints(fac), "&7Farm de la faction.").build());
        inv.setItem(29, new ItemBuilder(Material.NETHER_STAR)
                .name("&6Prestige")
                .lore("&7Voir tes prestiges", "&7et les prochains.", "&eClique pour ouvrir.").build());
        inv.setItem(31, new ItemBuilder(Material.REDSTONE_TORCH_ON)
                .name("&ePermissions")
                .lore("&7Changer les perms des ranks.", "&eClique pour ouvrir.").build());
        inv.setItem(33, new ItemBuilder(Material.BOOK)
                .name("&bMissions de faction")
                .lore("&7Avancer dans le prestige.", "&eClique pour ouvrir.").build());
        inv.setItem(40, Menus.close());
        player.openInventory(inv);
    }

    public void openPrestige(Player player) {
        String fac = factions.factionOf(player);
        if (fac.isEmpty()) {
            plugin.msg(player, "&cPas de faction.");
            return;
        }
        Inventory inv = Bukkit.createInventory(new GuiHolder("f-prestige"), 54, CC.color("&8Prestige faction"));
        Menus.fill(inv);
        int current = prestige(fac);
        inv.setItem(4, new ItemBuilder(Material.NETHER_STAR)
                .name("&6Prestige actuel : &e" + current)
                .lore("&7Clique un palier pour monter", "&7si les conditions sont OK.").build());
        ConfigurationSection levels = plugin.getConfig().getConfigurationSection("faction-prestige.levels");
        int slot = 19;
        if (levels != null) {
            for (String key : levels.getKeys(false)) {
                int level = parseInt(key, 0);
                if (level <= 0) {
                    continue;
                }
                ConfigurationSection lvl = levels.getConfigurationSection(key);
                List<String> lore = new ArrayList<String>();
                lore.addAll(lvl.getStringList("lore"));
                lore.add("");
                lore.add("&7Conditions :");
                lore.add("&8- Membres : &e" + factions.members(fac).size() + "&7/&e" + lvl.getInt("members", 0));
                lore.add("&8- Claims : &e" + factions.claimCount(fac) + "&7/&e" + lvl.getInt("claims", 0));
                lore.add("&8- Farm : &e" + farmPoints(fac) + "&7/&e" + lvl.getInt("farm-points", 0));
                lore.add("&8- PvP : &e" + pvpPoints(fac) + "&7/&e" + lvl.getInt("pvp-points", 0));
                lore.add("&8- Missions : voir &e/f mission");
                lore.add("");
                if (current >= level) {
                    lore.add("&aDéjà atteint");
                } else if (current + 1 == level) {
                    lore.add("&eClique pour prestige !");
                } else {
                    lore.add("&7Prestige &e" + (current + 1) + " &7requis d'abord.");
                }
                short data = (short) (current >= level ? 5 : (current + 1 == level ? 4 : 14));
                inv.setItem(slot, new ItemBuilder(Material.STAINED_GLASS_PANE, 1, data)
                        .name("&6Prestige " + level)
                        .lore(lore)
                        .build());
                slot++;
                if (slot % 9 == 8) {
                    slot += 2;
                }
            }
        }
        inv.setItem(45, Menus.back());
        inv.setItem(49, Menus.close());
        player.openInventory(inv);
    }

    public void openMissions(Player player) {
        String fac = factions.factionOf(player);
        if (fac.isEmpty()) {
            plugin.msg(player, "&cPas de faction.");
            return;
        }
        Inventory inv = Bukkit.createInventory(new GuiHolder("f-mission"), 54, CC.color("&8Missions faction"));
        Menus.fill(inv);
        ConfigurationSection list = plugin.getConfig().getConfigurationSection("faction-missions.list");
        int slot = 10;
        if (list != null) {
            for (String id : list.getKeys(false)) {
                ConfigurationSection m = list.getConfigurationSection(id);
                if (m == null) {
                    continue;
                }
                int need = m.getInt("amount", 100);
                int have = factions.store().get().getInt("factions." + fac + ".missions." + id, 0);
                inv.setItem(slot, new ItemBuilder(material(m.getString("material", "BOOK")))
                        .name(m.getString("name", "&e" + id))
                        .lore("&7" + m.getString("description", ""),
                                "&7Progression : &e" + Math.min(have, need) + "&7/&e" + need,
                                have >= need ? "&aTerminée" : "&7Continue pour le prestige.")
                        .build());
                slot++;
                if (slot % 9 == 8) {
                    slot += 2;
                }
            }
        }
        inv.setItem(45, Menus.back());
        inv.setItem(49, Menus.close());
        player.openInventory(inv);
    }

    public void openWarps(Player player) {
        String fac = factions.factionOf(player);
        if (fac.isEmpty()) {
            plugin.msg(player, "&cPas de faction.");
            return;
        }
        Inventory inv = Bukkit.createInventory(new GuiHolder("f-warps"), 45, CC.color("&8Warps faction"));
        Menus.fill(inv);
        ConfigurationSection warps = factions.store().get().getConfigurationSection("factions." + fac + ".warps");
        int slot = 10;
        if (warps == null || warps.getKeys(false).isEmpty()) {
            inv.setItem(22, new ItemBuilder(Material.BARRIER)
                    .name("&7Aucun warp")
                    .lore("&7Crée-en un avec &e/f setwarp <nom>").build());
        } else {
            for (String name : warps.getKeys(false)) {
                Location loc = Locations.deserialize(warps.getString(name));
                List<String> lore = new ArrayList<String>();
                if (loc != null) {
                    lore.add("&7" + loc.getWorld().getName() + " " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ());
                }
                lore.add("&eClique pour te tp.");
                inv.setItem(slot, new ItemBuilder(Material.ENDER_PEARL).name("&b" + name).lore(lore).build());
                slot++;
                if (slot % 9 == 8) {
                    slot += 2;
                }
            }
        }
        inv.setItem(4, new ItemBuilder(Material.BOOK)
                .name("&6Warps")
                .lore("&7" + warpCount(fac) + "&7/&e" + maxWarps(fac),
                        "&e/f setwarp <nom>", "&e/f delwarp <nom>").build());
        inv.setItem(40, Menus.back());
        player.openInventory(inv);
    }

    public void openAccess(Player player) {
        String fac = factions.factionOf(player);
        if (fac.isEmpty()) {
            plugin.msg(player, "&cPas de faction.");
            return;
        }
        Inventory inv = Bukkit.createInventory(new GuiHolder("f-access"), 45, CC.color("&8Zones privées"));
        Menus.fill(inv);
        inv.setItem(4, new ItemBuilder(Material.BLAZE_ROD)
                .name("&6Sélecteur")
                .lore("&7Clique pour recevoir le stick", "&7puis &e/f acces create <nom>").build());
        ConfigurationSection zones = factions.store().get().getConfigurationSection("factions." + fac + ".access");
        int slot = 19;
        if (zones != null) {
            for (String id : zones.getKeys(false)) {
                inv.setItem(slot, new ItemBuilder(Material.IRON_FENCE)
                        .name("&d" + id)
                        .lore("&7Clic gauche : gérer les accès", "&cClic droit : supprimer")
                        .build());
                slot++;
            }
        }
        inv.setItem(40, Menus.back());
        player.openInventory(inv);
    }

    public void openAccessPlayers(Player player, String zone) {
        String fac = factions.factionOf(player);
        Inventory inv = Bukkit.createInventory(new GuiHolder("f-access-p", zone), 54, CC.color("&8Accès &7» " + zone));
        Menus.fill(inv);
        ConfigurationSection players = factions.store().get().getConfigurationSection("factions." + fac + ".access." + zone + ".players");
        int slot = 10;
        if (players != null) {
            for (String uuid : players.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(uuid);
                    inv.setItem(slot, Menus.skull(plugin.data().nameOf(id), "&e" + plugin.data().nameOf(id),
                            Arrays.asList("&7Droits : &f" + players.getString(uuid), "&cClic : retirer")));
                    slot++;
                    if (slot % 9 == 8) {
                        slot += 2;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        inv.setItem(4, new ItemBuilder(Material.NAME_TAG)
                .name("&aAjouter un joueur")
                .lore("&7Dans le chat : &e/f acces add " + zone + " <joueur>",
                        "&7Droits : BUILD,BREAK,CONTAINER,ALL").build());
        inv.setItem(45, Menus.back());
        inv.setItem(49, Menus.close());
        player.openInventory(inv);
    }

    public boolean handleWarp(Player player, String[] args) {
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("warp") || sub.equals("warps")) {
            if (args.length >= 2) {
                teleportWarp(player, args[1]);
            } else {
                openWarps(player);
            }
            return true;
        }
        if (sub.equals("setwarp") && args.length >= 2) {
            setWarp(player, args[1]);
            return true;
        }
        if (sub.equals("delwarp") && args.length >= 2) {
            delWarp(player, args[1]);
            return true;
        }
        return false;
    }

    public boolean handleAccess(Player player, String[] args) {
        if (args.length == 1) {
            openAccess(player);
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("wand")) {
            player.getInventory().addItem(new ItemBuilder(Material.STICK).name(WAND_NAME)
                    .lore("&7Clic gauche : pos1", "&7Clic droit : pos2").build());
            plugin.msg(player, "&aSélecteur reçu.");
            return true;
        }
        if (action.equals("create") && args.length >= 3) {
            createZone(player, args[2]);
            return true;
        }
        if (action.equals("delete") && args.length >= 3) {
            deleteZone(player, args[2]);
            return true;
        }
        if (action.equals("add") && args.length >= 4) {
            addAccess(player, args[2], args[3], args.length >= 5 ? args[4] : "ALL");
            return true;
        }
        if (action.equals("remove") && args.length >= 4) {
            removeAccess(player, args[2], args[3]);
            return true;
        }
        plugin.msg(player, "&e/f acces wand");
        plugin.msg(player, "&e/f acces create <nom>");
        plugin.msg(player, "&e/f acces add <zone> <joueur> [ALL|BUILD|BREAK|CONTAINER]");
        plugin.msg(player, "&e/f acces remove <zone> <joueur>");
        plugin.msg(player, "&e/f acces delete <zone>");
        return true;
    }

    public boolean tryPrestige(Player player) {
        String fac = factions.factionOf(player);
        if (fac.isEmpty()) {
            return true;
        }
        if (factions.rankOf(player) != FactionRank.LEADER) {
            plugin.msg(player, "&cSeul le chef peut prestige.");
            return true;
        }
        int next = prestige(fac) + 1;
        ConfigurationSection lvl = plugin.getConfig().getConfigurationSection("faction-prestige.levels." + next);
        if (lvl == null) {
            plugin.msg(player, "&ePrestige maximum atteint.");
            return true;
        }
        if (factions.members(fac).size() < lvl.getInt("members", 0)
                || factions.claimCount(fac) < lvl.getInt("claims", 0)
                || farmPoints(fac) < lvl.getInt("farm-points", 0)
                || pvpPoints(fac) < lvl.getInt("pvp-points", 0)
                || !missionsDone(fac, lvl.getStringList("missions"))) {
            plugin.msg(player, "&cConditions non remplies. Regarde le menu prestige.");
            return true;
        }
        factions.store().get().set("factions." + fac + ".prestige", next);
        factions.store().save();
        Bukkit.broadcastMessage(CC.color(plugin.prefix() + "&6" + factions.displayName(fac)
                + " &7passe prestige &e" + next + "&7 !"));
        return true;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof FactionManager.ChestHolder) {
            logChest((Player) event.getWhoClicked(), (FactionManager.ChestHolder) holder, event.getCurrentItem(), event.getCursor());
            return;
        }
        if (!(holder instanceof GuiHolder)) {
            return;
        }
        GuiHolder gui = (GuiHolder) holder;
        if (!gui.menu().startsWith("f-")) {
            return;
        }
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();
        if (slot == 49 || (slot == 40 && ("f-logs".equals(gui.menu()) || "f-menu".equals(gui.menu())))) {
            player.closeInventory();
            return;
        }
        if ("f-logs".equals(gui.menu())) {
            handleLogsClick(player, slot);
            return;
        }
        if ("f-menu".equals(gui.menu())) {
            handleMenuClick(player, slot);
            return;
        }
        if (slot == 45 || slot == 40) {
            if ("f-loglist".equals(gui.menu()) || "f-access".equals(gui.menu()) || "f-warps".equals(gui.menu())) {
                openLogs(player);
            } else if ("f-access-p".equals(gui.menu())) {
                openAccess(player);
            } else {
                openMenu(player);
            }
            return;
        }
        if ("f-prestige".equals(gui.menu()) && event.getCurrentItem() != null
                && event.getCurrentItem().getType() == Material.STAINED_GLASS_PANE) {
            tryPrestige(player);
            openPrestige(player);
            return;
        }
        if ("f-warps".equals(gui.menu()) && event.getCurrentItem() != null
                && event.getCurrentItem().getType() == Material.ENDER_PEARL
                && event.getCurrentItem().hasItemMeta()) {
            teleportWarp(player, CC.strip(event.getCurrentItem().getItemMeta().getDisplayName()));
            return;
        }
        if ("f-access".equals(gui.menu())) {
            if (slot == 4) {
                player.closeInventory();
                player.performCommand("f acces wand");
                return;
            }
            ItemStack current = event.getCurrentItem();
            if (current != null && current.getType() == Material.IRON_FENCE && current.hasItemMeta()) {
                String zone = CC.strip(current.getItemMeta().getDisplayName());
                if (event.isRightClick()) {
                    deleteZone(player, zone);
                    openAccess(player);
                } else {
                    openAccessPlayers(player, zone);
                }
            }
            return;
        }
        if ("f-access-p".equals(gui.menu()) && event.getCurrentItem() != null
                && event.getCurrentItem().getType() == Material.SKULL_ITEM
                && event.getCurrentItem().hasItemMeta()) {
            removeAccess(player, gui.extra(), CC.strip(event.getCurrentItem().getItemMeta().getDisplayName()));
            openAccessPlayers(player, gui.extra());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onWand(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.STICK || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return;
        }
        if (!item.getItemMeta().getDisplayName().equals(CC.color(WAND_NAME))) {
            return;
        }
        if (event.getClickedBlock() == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            pos1.put(player.getUniqueId(), event.getClickedBlock().getLocation());
            plugin.msg(player, "&aPos1 définie.");
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            pos2.put(player.getUniqueId(), event.getClickedBlock().getLocation());
            plugin.msg(player, "&aPos2 définie.");
        }
    }

    private void handleLogsClick(Player player, int slot) {
        if (slot == 10) openLogList(player, "roster", "&8Logs roster");
        else if (slot == 11) openLogList(player, "claim", "&8Logs claims");
        else if (slot == 12) openLogList(player, "promote", "&8Logs promotions");
        else if (slot == 13) openLogList(player, "chest", "&8Logs coffre");
        else if (slot == 19) openLogList(player, "claims-list", "&8Vos claims");
        else if (slot == 21) openAccess(player);
        else if (slot == 23) {
            player.closeInventory();
            factions.openChest(player);
        } else if (slot == 25) openWarps(player);
    }

    private void handleMenuClick(Player player, int slot) {
        if (slot == 29) openPrestige(player);
        else if (slot == 31) factions.menus().openPerm(player);
        else if (slot == 33) openMissions(player);
    }

    private void logChest(Player player, FactionManager.ChestHolder holder, ItemStack current, ItemStack cursor) {
        if (current != null && current.getType() != Material.AIR) {
            addLog(holder.fac, "chest", player.getName() + " a retiré " + Menus.prettyItem(current));
        } else if (cursor != null && cursor.getType() != Material.AIR) {
            addLog(holder.fac, "chest", player.getName() + " a déposé " + Menus.prettyItem(cursor));
        }
    }

    private List<String> claimLines(String fac) {
        List<String> out = new ArrayList<String>();
        ConfigurationSection section = factions.store().get().getConfigurationSection("claims");
        if (section == null) {
            return out;
        }
        for (String key : section.getKeys(false)) {
            if (!fac.equalsIgnoreCase(section.getString(key))) {
                continue;
            }
            String[] p = key.split(";");
            if (p.length >= 3) {
                try {
                    World world = Bukkit.getWorld(p[0]);
                    int cx = Integer.parseInt(p[1]);
                    int cz = Integer.parseInt(p[2]);
                    int x = cx * 16 + 8;
                    int z = cz * 16 + 8;
                    int y = world != null ? world.getHighestBlockYAt(x, z) : 64;
                    out.add(p[0] + "  x:" + x + " y:" + y + " z:" + z + "  (chunk " + cx + "," + cz + ")");
                } catch (Exception ignored) {
                    out.add(key);
                }
            } else {
                out.add(key);
            }
        }
        return out;
    }

    private void teleportWarp(Player player, String name) {
        String fac = factions.factionOf(player);
        Location loc = Locations.deserialize(factions.store().get().getString("factions." + fac + ".warps." + name.toLowerCase(Locale.ROOT)));
        if (loc == null) {
            plugin.msg(player, "&cWarp introuvable.");
            return;
        }
        plugin.teleports().request(player, loc, "&aTéléporté au warp &e" + name);
    }

    private void setWarp(Player player, String name) {
        String fac = factions.factionOf(player);
        if (fac.isEmpty() || !factions.hasPerm(player, FactionPerm.WARP)) {
            plugin.msg(player, "&cTu ne peux pas définir un warp.");
            return;
        }
        String owner = factions.claimAt(player.getLocation());
        if (owner == null || !owner.equalsIgnoreCase(fac)) {
            plugin.msg(player, "&cLe warp doit être dans un claim.");
            return;
        }
        String id = name.toLowerCase(Locale.ROOT);
        boolean exists = factions.store().get().contains("factions." + fac + ".warps." + id);
        if (!exists && warpCount(fac) >= maxWarps(fac)) {
            plugin.msg(player, "&cLimite de warps atteinte (&e" + maxWarps(fac) + "&c). Monte de prestige.");
            return;
        }
        factions.store().get().set("factions." + fac + ".warps." + id, Locations.serialize(player.getLocation()));
        factions.store().save();
        plugin.msg(player, "&aWarp &e" + name + " &adéfini.");
    }

    private void delWarp(Player player, String name) {
        String fac = factions.factionOf(player);
        if (fac.isEmpty() || !factions.hasPerm(player, FactionPerm.WARP)) {
            plugin.msg(player, "&cTu ne peux pas supprimer un warp.");
            return;
        }
        factions.store().get().set("factions." + fac + ".warps." + name.toLowerCase(Locale.ROOT), null);
        factions.store().save();
        plugin.msg(player, "&eWarp supprimé.");
    }

    private int warpCount(String fac) {
        ConfigurationSection section = factions.store().get().getConfigurationSection("factions." + fac + ".warps");
        return section == null ? 0 : section.getKeys(false).size();
    }

    private void createZone(Player player, String name) {
        String fac = factions.factionOf(player);
        if (fac.isEmpty() || !factions.hasPerm(player, FactionPerm.ACCESS)) {
            plugin.msg(player, "&cTu ne peux pas créer de zone privée.");
            return;
        }
        Location a = pos1.get(player.getUniqueId());
        Location b = pos2.get(player.getUniqueId());
        if (a == null || b == null) {
            plugin.msg(player, "&cSélectionne pos1 et pos2 avec &e/f acces wand");
            return;
        }
        if (factions.claimAt(a) == null || !fac.equalsIgnoreCase(factions.claimAt(a))
                || factions.claimAt(b) == null || !fac.equalsIgnoreCase(factions.claimAt(b))) {
            plugin.msg(player, "&cLa zone doit être dans tes claims.");
            return;
        }
        String id = name.toLowerCase(Locale.ROOT);
        factions.store().get().set("factions." + fac + ".access." + id + ".pos1", Locations.serialize(a));
        factions.store().get().set("factions." + fac + ".access." + id + ".pos2", Locations.serialize(b));
        factions.store().save();
        plugin.msg(player, "&aZone privée &e" + name + " &acréée. &e/f acces add " + name + " <joueur>");
    }

    private void deleteZone(Player player, String name) {
        String fac = factions.factionOf(player);
        if (fac.isEmpty() || !factions.hasPerm(player, FactionPerm.ACCESS)) {
            plugin.msg(player, "&cTu ne peux pas supprimer cette zone.");
            return;
        }
        factions.store().get().set("factions." + fac + ".access." + name.toLowerCase(Locale.ROOT), null);
        factions.store().save();
        plugin.msg(player, "&eZone supprimée.");
    }

    private void addAccess(Player player, String zone, String targetName, String perms) {
        String fac = factions.factionOf(player);
        if (fac.isEmpty() || !factions.hasPerm(player, FactionPerm.ACCESS)) {
            plugin.msg(player, "&cPas la permission.");
            return;
        }
        Player target = Bukkit.getPlayer(targetName);
        UUID uuid = target != null ? target.getUniqueId() : plugin.data().findUuidByString("name", targetName);
        if (uuid == null) {
            plugin.msg(player, "&cJoueur introuvable.");
            return;
        }
        String id = zone.toLowerCase(Locale.ROOT);
        if (!factions.store().get().contains("factions." + fac + ".access." + id)) {
            plugin.msg(player, "&cZone introuvable.");
            return;
        }
        factions.store().get().set("factions." + fac + ".access." + id + ".players." + uuid.toString(), perms.toUpperCase(Locale.ROOT));
        factions.store().save();
        plugin.msg(player, "&aAccès donné à &e" + plugin.data().nameOf(uuid) + " &7(" + perms + ")");
    }

    private void removeAccess(Player player, String zone, String targetName) {
        String fac = factions.factionOf(player);
        if (fac.isEmpty() || !factions.hasPerm(player, FactionPerm.ACCESS)) {
            plugin.msg(player, "&cPas la permission.");
            return;
        }
        Player target = Bukkit.getPlayer(targetName);
        UUID uuid = target != null ? target.getUniqueId() : plugin.data().findUuidByString("name", targetName);
        if (uuid == null) {
            ConfigurationSection players = factions.store().get().getConfigurationSection(
                    "factions." + fac + ".access." + zone.toLowerCase(Locale.ROOT) + ".players");
            if (players != null) {
                for (String raw : players.getKeys(false)) {
                    if (plugin.data().nameOf(UUID.fromString(raw)).equalsIgnoreCase(targetName)) {
                        uuid = UUID.fromString(raw);
                        break;
                    }
                }
            }
        }
        if (uuid == null) {
            plugin.msg(player, "&cJoueur introuvable.");
            return;
        }
        factions.store().get().set("factions." + fac + ".access." + zone.toLowerCase(Locale.ROOT) + ".players." + uuid, null);
        factions.store().save();
        plugin.msg(player, "&eAccès retiré.");
    }

    private boolean missionsDone(String fac, List<String> required) {
        if (required == null || required.isEmpty()) {
            return true;
        }
        for (String id : required) {
            ConfigurationSection m = plugin.getConfig().getConfigurationSection("faction-missions.list." + id);
            int need = m == null ? 1 : m.getInt("amount", 1);
            int have = factions.store().get().getInt("factions." + fac + ".missions." + id, 0);
            if (have < need) {
                return false;
            }
        }
        return true;
    }

    private boolean inside(Location loc, Location a, Location b) {
        if (loc.getWorld() == null || a.getWorld() == null || !loc.getWorld().equals(a.getWorld())) {
            return false;
        }
        int minX = Math.min(a.getBlockX(), b.getBlockX());
        int maxX = Math.max(a.getBlockX(), b.getBlockX());
        int minY = Math.min(a.getBlockY(), b.getBlockY());
        int maxY = Math.max(a.getBlockY(), b.getBlockY());
        int minZ = Math.min(a.getBlockZ(), b.getBlockZ());
        int maxZ = Math.max(a.getBlockZ(), b.getBlockZ());
        return loc.getBlockX() >= minX && loc.getBlockX() <= maxX
                && loc.getBlockY() >= minY && loc.getBlockY() <= maxY
                && loc.getBlockZ() >= minZ && loc.getBlockZ() <= maxZ;
    }

    private int parseInt(String raw, int def) {
        try {
            return Integer.parseInt(raw);
        } catch (Exception e) {
            return def;
        }
    }

    private Material material(String raw) {
        try {
            return Material.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return Material.BOOK;
        }
    }
}
