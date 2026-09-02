package fr.sydaria.factions;

import fr.sydaria.Sydaria;
import fr.sydaria.util.CC;
import fr.sydaria.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FactionMenus implements Listener {
    static final String UPGRADE_TITLE = CC.color("&8Upgrades faction");
    static final String PERM_TITLE = CC.color("&8Permissions faction");
    static final String PERM_GROUP_PREFIX = CC.color("&8Perms &7» ");

    private final Sydaria plugin;
    private final FactionManager factions;

    public FactionMenus(Sydaria plugin, FactionManager factions) {
        this.plugin = plugin;
        this.factions = factions;
    }

    public void openUpgrade(Player player) {
        String fac = factions.factionOf(player);
        if (fac.isEmpty()) {
            plugin.msg(player, "&cTu n'es dans aucune faction.");
            return;
        }
        Inventory inv = Bukkit.createInventory(new UpgradeHolder(), 45, UPGRADE_TITLE);
        fill(inv);
        inv.setItem(4, info(player, fac));
        inv.setItem(19, chestItem(fac));
        inv.setItem(21, flyItem(fac));
        inv.setItem(23, membersItem(fac));
        inv.setItem(25, powerItem(fac));
        inv.setItem(40, new ItemBuilder(Material.BARRIER).name("&cFermer").lore("&7Fermer le menu.").build());
        player.openInventory(inv);
    }

    public void openPerm(Player player) {
        String fac = factions.factionOf(player);
        if (fac.isEmpty()) {
            plugin.msg(player, "&cTu n'es dans aucune faction.");
            return;
        }
        if (!factions.hasPerm(player, FactionPerm.PERM) && factions.rankOf(player) != FactionRank.LEADER) {
            plugin.msg(player, "&cTu n'as pas la permission de gérer les perms.");
            return;
        }
        Inventory inv = Bukkit.createInventory(new PermHolder(), 45, PERM_TITLE);
        fill(inv);
        inv.setItem(4, new ItemBuilder(Material.BOOK)
                .name("&6&lPermissions")
                .lore(Arrays.asList(
                        "&7Clique sur un groupe pour",
                        "&7activer ou désactiver ses droits.",
                        "",
                        "&7Faction : &e" + factions.displayName(fac)
                )).build());
        inv.setItem(19, groupIcon("RECRUIT", Material.LEATHER_HELMET, "&7Recrues"));
        inv.setItem(20, groupIcon("MEMBER", Material.IRON_HELMET, "&fMembres"));
        inv.setItem(21, groupIcon("OFFICER", Material.GOLD_HELMET, "&eOfficiers"));
        inv.setItem(22, groupIcon("COLEADER", Material.DIAMOND_HELMET, "&6Co-leaders"));
        inv.setItem(24, groupIcon("NEUTRAL", Material.IRON_INGOT, "&7Neutres"));
        inv.setItem(25, groupIcon("ENEMY", Material.REDSTONE, "&cEnnemis"));
        inv.setItem(40, new ItemBuilder(Material.BARRIER).name("&cFermer").lore("&7Fermer le menu.").build());
        player.openInventory(inv);
    }

    public void openPermGroup(Player player, String group) {
        String fac = factions.factionOf(player);
        if (fac.isEmpty()) {
            return;
        }
        Inventory inv = Bukkit.createInventory(new PermGroupHolder(group), 54, PERM_GROUP_PREFIX + groupLabel(group));
        fill(inv);
        int slot = 10;
        for (FactionPerm perm : FactionPerm.values()) {
            if (!perm.shownFor(group)) {
                continue;
            }
            boolean on = factions.permValue(fac, group, perm);
            List<String> lore = new ArrayList<String>();
            lore.add("&7Groupe : " + groupLabel(group));
            lore.add("&7État : " + (on ? "&aAutorisé" : "&cRefusé"));
            lore.add("");
            lore.add("&eClique pour basculer.");
            short data = (short) (on ? 5 : 14);
            inv.setItem(slot, new ItemBuilder(Material.STAINED_GLASS_PANE, 1, data)
                    .name((on ? "&a" : "&c") + perm.display())
                    .lore(lore)
                    .build());
            slot++;
            if (slot % 9 == 8) {
                slot += 2;
            }
            if (slot >= 44) {
                break;
            }
        }
        inv.setItem(45, new ItemBuilder(Material.ARROW).name("&eRetour").lore("&7Menu des groupes.").build());
        inv.setItem(49, new ItemBuilder(Material.BARRIER).name("&cFermer").lore("&7Fermer le menu.").build());
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof UpgradeHolder) && !(holder instanceof PermHolder) && !(holder instanceof PermGroupHolder)) {
            return;
        }
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType() == Material.AIR || !current.hasItemMeta()) {
            return;
        }
        if (holder instanceof UpgradeHolder) {
            handleUpgrade(player, event.getRawSlot());
            return;
        }
        if (holder instanceof PermHolder) {
            handlePermMain(player, event.getRawSlot());
            return;
        }
        handlePermGroup(player, (PermGroupHolder) holder, event.getRawSlot(), current);
    }

    private void handleUpgrade(Player player, int slot) {
        if (slot == 40) {
            player.closeInventory();
            return;
        }
        String fac = factions.factionOf(player);
        if (fac.isEmpty()) {
            player.closeInventory();
            return;
        }
        if (!factions.hasPerm(player, FactionPerm.UPGRADE)) {
            plugin.msg(player, "&cTu n'as pas la permission d'upgrader.");
            return;
        }
        if (slot == 19) {
            factions.buyUpgrade(player, "chest");
        } else if (slot == 21) {
            factions.buyUpgrade(player, "fly");
        } else if (slot == 23) {
            factions.buyUpgrade(player, "members");
        } else if (slot == 25) {
            factions.buyUpgrade(player, "power");
        } else {
            return;
        }
        openUpgrade(player);
    }

    private void handlePermMain(Player player, int slot) {
        if (slot == 40) {
            player.closeInventory();
            return;
        }
        String group = null;
        if (slot == 19) group = "RECRUIT";
        else if (slot == 20) group = "MEMBER";
        else if (slot == 21) group = "OFFICER";
        else if (slot == 22) group = "COLEADER";
        else if (slot == 24) group = "NEUTRAL";
        else if (slot == 25) group = "ENEMY";
        if (group != null) {
            openPermGroup(player, group);
        }
    }

    private void handlePermGroup(Player player, PermGroupHolder holder, int slot, ItemStack current) {
        if (slot == 45) {
            openPerm(player);
            return;
        }
        if (slot == 49) {
            player.closeInventory();
            return;
        }
        if (factions.rankOf(player) != FactionRank.LEADER && !factions.hasPerm(player, FactionPerm.PERM)) {
            plugin.msg(player, "&cSeul le chef (ou un membre autorisé) peut modifier.");
            return;
        }
        ItemMeta meta = current.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return;
        }
        String stripped = CC.strip(meta.getDisplayName());
        FactionPerm perm = null;
        for (FactionPerm candidate : FactionPerm.values()) {
            if (candidate.display().equals(stripped)) {
                perm = candidate;
                break;
            }
        }
        if (perm == null) {
            return;
        }
        String fac = factions.factionOf(player);
        factions.togglePerm(fac, holder.group, perm);
        openPermGroup(player, holder.group);
    }

    private ItemStack info(Player player, String fac) {
        return new ItemBuilder(Material.BOOK)
                .name("&6&l" + factions.displayName(fac))
                .lore(Arrays.asList(
                        "&7Membres : &e" + factions.members(fac).size() + "&7/&e" + factions.maxMembers(fac),
                        "&7Power : &e" + factions.powerText(fac),
                        "&7Claims : &e" + factions.claimCount(fac),
                        "&7Tokens : &6" + plugin.tokens().get(player.getUniqueId())
                )).build();
    }

    private ItemStack chestItem(String fac) {
        int level = factions.upgradeLevel(fac, "chest");
        List<Integer> sizes = factions.upgradeInts("chest", "sizes", Arrays.asList(27, 36, 45, 54));
        List<Integer> costs = factions.upgradeInts("chest", "costs", Arrays.asList(0, 1000, 2000, 3500));
        boolean max = level >= sizes.size() - 1;
        int next = max ? sizes.get(sizes.size() - 1) : sizes.get(level + 1);
        int cost = max ? 0 : costs.get(Math.min(level + 1, costs.size() - 1));
        return new ItemBuilder(Material.CHEST)
                .name("&6Coffre faction")
                .lore(statusLore("Taille actuelle : &e" + factions.chestSize(fac) + " slots",
                        max, next + " slots", cost)).build();
    }

    private ItemStack flyItem(String fac) {
        boolean unlocked = factions.hasFly(fac);
        int cost = plugin.getConfig().getInt("factions.upgrades.fly.cost", 2500);
        List<String> lore = new ArrayList<String>();
        lore.add("&7Vole dans tes claims.");
        lore.add("");
        if (unlocked) {
            lore.add("&aDéjà débloqué");
            lore.add("&7Utilise &e/f fly");
        } else {
            lore.add("&7Coût : &6" + cost + " tokens");
            lore.add("&eClique pour acheter.");
        }
        return new ItemBuilder(Material.FEATHER).name("&bFly faction").lore(lore).build();
    }

    private ItemStack membersItem(String fac) {
        int level = factions.upgradeLevel(fac, "members");
        List<Integer> amounts = factions.upgradeInts("members", "amounts", Arrays.asList(10, 15, 20, 30));
        List<Integer> costs = factions.upgradeInts("members", "costs", Arrays.asList(0, 800, 1600, 3000));
        boolean max = level >= amounts.size() - 1;
        int next = max ? amounts.get(amounts.size() - 1) : amounts.get(level + 1);
        int cost = max ? 0 : costs.get(Math.min(level + 1, costs.size() - 1));
        return new ItemBuilder(Material.SKULL_ITEM, 1, (short) 3)
                .name("&ePlaces membres")
                .lore(statusLore("Places actuelles : &e" + factions.maxMembers(fac),
                        max, next + " places", cost)).build();
    }

    private ItemStack powerItem(String fac) {
        int level = factions.upgradeLevel(fac, "power");
        List<Integer> amounts = factions.upgradeInts("power", "amounts", Arrays.asList(0, 10, 20, 40));
        List<Integer> costs = factions.upgradeInts("power", "costs", Arrays.asList(0, 1200, 2500, 5000));
        boolean max = level >= amounts.size() - 1;
        int next = max ? amounts.get(amounts.size() - 1) : amounts.get(level + 1);
        int cost = max ? 0 : costs.get(Math.min(level + 1, costs.size() - 1));
        return new ItemBuilder(Material.BLAZE_POWDER)
                .name("&cPower bonus")
                .lore(statusLore("Bonus actuel : &e+" + factions.extraPower(fac),
                        max, "+" + next + " power", cost)).build();
    }

    private List<String> statusLore(String current, boolean max, String next, int cost) {
        List<String> lore = new ArrayList<String>();
        lore.add(current);
        lore.add("");
        if (max) {
            lore.add("&aNiveau maximum");
        } else {
            lore.add("&7Prochain niveau : &e" + next);
            lore.add("&7Coût : &6" + cost + " tokens");
            lore.add("&eClique pour acheter.");
        }
        return lore;
    }

    private ItemStack groupIcon(String group, Material material, String name) {
        return new ItemBuilder(material)
                .name(name)
                .lore(Arrays.asList("&7Configurer les droits de ce groupe.", "&eClique pour ouvrir.")).build();
    }

    private String groupLabel(String group) {
        if ("RECRUIT".equals(group)) return CC.color("&7Recrues");
        if ("MEMBER".equals(group)) return CC.color("&fMembres");
        if ("OFFICER".equals(group)) return CC.color("&eOfficiers");
        if ("COLEADER".equals(group)) return CC.color("&6Co-leaders");
        if ("NEUTRAL".equals(group)) return CC.color("&7Neutres");
        if ("ENEMY".equals(group)) return CC.color("&cEnnemis");
        return group;
    }

    private void fill(Inventory inv) {
        ItemStack pane = new ItemBuilder(Material.STAINED_GLASS_PANE, 1, (short) 15).name("&r").build();
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, pane);
        }
    }

    static class UpgradeHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    static class PermHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    static class PermGroupHolder implements InventoryHolder {
        private final String group;

        PermGroupHolder(String group) {
            this.group = group;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
