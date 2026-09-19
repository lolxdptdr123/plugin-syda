package fr.draftmc.grades;

import fr.draftmc.Draftmc;
import fr.draftmc.gui.GuiHolder;
import fr.draftmc.util.CC;
import fr.draftmc.util.Cooldowns;
import fr.draftmc.util.Items;
import fr.draftmc.util.Locations;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.Potion;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Commandes débloquées selon le grade du joueur (LuckPerms).
 * Configurable dans grade-commands.commands.<id>.
 */
public class GradeCommandManager implements CommandExecutor, Listener {
    private final Draftmc plugin;
    private final java.util.Set<UUID> invseeViewers = new HashSet<UUID>();

    public GradeCommandManager(Draftmc plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("grade-commands.enabled", true);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cJoueur uniquement.");
            return true;
        }
        Player player = (Player) sender;
        String key = command.getName().toLowerCase(Locale.ROOT);
        if ("sell".equals(key)) {
            if (args.length == 0 || !"all".equalsIgnoreCase(args[0])) {
                plugin.msg(player, "&e/sell all");
                return true;
            }
            key = "sellall";
        }

        if ("grades".equals(key) || "gradecmds".equals(key)) {
            sendHelp(player);
            return true;
        }

        if (!isEnabled()) {
            plugin.msg(player, "&cLes commandes de grade sont desactivees.");
            return true;
        }

        ConfigurationSection section = commandSection(key);
        if (section == null) {
            plugin.msg(player, "&cCommande inconnue.");
            return true;
        }

        if (!checkAccess(player, key, section, args)) {
            return true;
        }

        if ("feed".equals(key)) {
            feed(player);
        } else if ("pv".equals(key)) {
            openVault(player, section, args);
        } else if ("ec".equals(key)) {
            if (args.length >= 1) {
                viewEnderchest(player, args[0]);
            } else {
                player.openInventory(player.getEnderChest());
            }
        } else if ("refill".equals(key)) {
            refill(player, section);
        } else if ("craft".equals(key)) {
            player.openWorkbench(player.getLocation(), true);
        } else if ("near".equals(key)) {
            near(player, section);
        } else if ("bottlexp".equals(key) || "bottle".equals(key)) {
            bottleXp(player);
        } else if ("compact".equals(key)) {
            compact(player);
        } else if ("hat".equals(key)) {
            hat(player);
        } else if ("back".equals(key)) {
            goBack(player);
        } else if ("repair".equals(key)) {
            if (args.length > 0 && "all".equalsIgnoreCase(args[0])) {
                repairAll(player);
            } else {
                repairHand(player);
            }
        } else if ("invsee".equals(key)) {
            invsee(player, args);
        } else if ("sellall".equals(key)) {
            sellAll(player);
        } else {
            plugin.msg(player, "&cCette commande n'est pas encore implementee.");
        }
        return true;
    }

    private void sendHelp(Player player) {
        plugin.msg(player, "&6&lCommandes par grade");
        plugin.msg(player, "&7Ton grade: &e" + plugin.grades().displayName(player));
        ConfigurationSection commands = plugin.getConfig().getConfigurationSection("grade-commands.commands");
        if (commands == null) {
            plugin.msg(player, "&cSection grade-commands.commands manquante dans config.yml");
            return;
        }
        for (String id : commands.getKeys(false)) {
            ConfigurationSection sec = commands.getConfigurationSection(id);
            if (sec == null || !sec.getBoolean("enabled", true)) {
                continue;
            }
            String minGrade = sec.getString("min-grade");
            if (minGrade == null || minGrade.isEmpty()) {
                continue;
            }
            boolean unlocked = plugin.grades().hasMinGrade(player, minGrade);
            String status = unlocked ? "&a[OK]" : "&c[X]";
            String usage = "pv".equals(id) ? "/pv [numero]" : "/" + id;
            String description = sec.getString("description", "");
            if (description != null && !description.isEmpty()) {
                plugin.msg(player, status + " &e" + usage + " &7- " + description
                        + " &8(" + plugin.grades().displayNameForGroup(minGrade) + ")");
            } else {
                plugin.msg(player, status + " &e" + usage + " &7- grade &e"
                        + plugin.grades().displayNameForGroup(minGrade) + " &7min.");
            }
        }
    }

    private ConfigurationSection commandSection(String key) {
        return plugin.getConfig().getConfigurationSection("grade-commands.commands." + key);
    }

    private boolean checkAccess(Player player, String key, ConfigurationSection section, String[] args) {
        if (!section.getBoolean("enabled", true)) {
            plugin.msg(player, "&cCette commande est desactivee.");
            return false;
        }
        String minGrade = section.getString("min-grade");
        if (minGrade == null || minGrade.isEmpty()) {
            plugin.msg(player, "&cCommande mal configuree (&emin-grade&c manquant).");
            return false;
        }
        if (!plugin.grades().hasMinGrade(player, minGrade)) {
            String msg = plugin.getConfig().getString("grade-commands.no-permission",
                    "&cTu dois etre au grade &e%grade% &cminimum pour utiliser &e/%cmd%&c.");
            msg = msg.replace("%grade%", plugin.grades().displayNameForGroup(minGrade))
                    .replace("%cmd%", key);
            plugin.msg(player, msg);
            return false;
        }
        int cooldown = section.getInt("cooldown-seconds", 0);
        if ("repair".equals(key)) {
            boolean all = args.length > 0 && "all".equalsIgnoreCase(args[0]);
            cooldown = all
                    ? plugin.grades().perks().repairAllCooldown(player)
                    : plugin.grades().perks().repairCooldown(player);
            String cdKey = all ? "gradecmd:repair-all" : "gradecmd:repair";
            if (cooldown > 0) {
                int remaining = Cooldowns.remaining(player, cdKey);
                if (remaining > 0) {
                    String msg = plugin.getConfig().getString("grade-commands.cooldown-message",
                            "&cCommande en cooldown. &7Attends &e%time%s&7.");
                    plugin.msg(player, msg.replace("%time%", formatDuration(remaining)));
                    return false;
                }
                Cooldowns.ready(player, cdKey, cooldown);
            }
            return true;
        }
        if (cooldown > 0) {
            int remaining = Cooldowns.remaining(player, "gradecmd:" + key);
            if (remaining > 0) {
                String msg = plugin.getConfig().getString("grade-commands.cooldown-message",
                        "&cCommande en cooldown. &7Attends &e%time%s&7.");
                plugin.msg(player, msg.replace("%time%", String.valueOf(remaining)));
                return false;
            }
            Cooldowns.ready(player, "gradecmd:" + key, cooldown);
        }
        return true;
    }

    private void feed(Player player) {
        player.setFoodLevel(20);
        player.setSaturation(20f);
        plugin.msg(player, plugin.getConfig().getString("grade-commands.messages.feed", "&aTu as ete nourri."));
    }

    private void near(Player player, ConfigurationSection section) {
        int radius = section.getInt("radius", 100);
        List<String> found = new ArrayList<String>();
        Location origin = player.getLocation();
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player) || !other.getWorld().equals(origin.getWorld())) {
                continue;
            }
            if (other.getLocation().distance(origin) <= radius) {
                found.add(other.getName());
            }
        }
        if (found.isEmpty()) {
            plugin.msg(player, "&7Aucun joueur dans un rayon de &e" + radius + " &7blocs.");
            return;
        }
        plugin.msg(player, "&7Joueurs proches (&e" + found.size() + "&7) : &f" + join(found));
    }

    private void openVault(Player player, ConfigurationSection section, String[] args) {
        int maxVaults = plugin.grades().perks().maxVaults(player);
        if (maxVaults <= 0) {
            maxVaults = Math.max(1, section.getInt("max-vaults", 1));
        }
        int vaultId = 1;
        if (args.length > 0) {
            try {
                vaultId = Integer.parseInt(args[0]);
            } catch (NumberFormatException ex) {
                plugin.msg(player, "&cUsage: /pv [1-" + maxVaults + "]");
                return;
            }
        }
        if (vaultId < 1 || vaultId > maxVaults) {
            plugin.msg(player, "&cNumero invalide. Utilise &e/pv 1 &cà &e/pv " + maxVaults + "&c.");
            return;
        }
        int size = Math.max(9, Math.min(54, section.getInt("size", 54)));
        size = (size / 9) * 9;
        String titleTemplate = section.getString("title", "&8Coffre prive #%number%");
        String title = CC.color(titleTemplate.replace("%number%", String.valueOf(vaultId)));
        VaultHolder holder = new VaultHolder(player.getUniqueId(), vaultId);
        Inventory inv = Bukkit.createInventory(holder, size, title);
        holder.inventory = inv;
        loadVault(player.getUniqueId(), vaultId, inv);
        player.openInventory(inv);
    }

    private void refill(Player player, ConfigurationSection section) {
        List<ItemStack> templates = buildRefillPotions(section);
        if (templates.isEmpty()) {
            plugin.msg(player, "&cRefill mal configure dans config.yml.");
            return;
        }
        int filled = 0;
        int templateIndex = 0;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack slot = contents[i];
            if (slot != null && slot.getType() != Material.AIR) {
                continue;
            }
            ItemStack potion = templates.get(templateIndex % templates.size()).clone();
            player.getInventory().setItem(i, potion);
            filled++;
            templateIndex++;
        }
        String msg = plugin.getConfig().getString("grade-commands.messages.refill",
                "&a%count% slot(s) rempli(s) avec des potions.");
        plugin.msg(player, msg.replace("%count%", String.valueOf(filled)));
    }

    private List<ItemStack> buildRefillPotions(ConfigurationSection section) {
        List<ItemStack> out = new ArrayList<ItemStack>();
        List<?> entries = section.getList("potions");
        if (entries != null) {
            for (Object entry : entries) {
                ItemStack stack = potionFromConfig(entry);
                if (stack != null) {
                    out.add(stack);
                }
            }
        }
        if (out.isEmpty()) {
            ItemStack fallback = createPotion(
                    section.getString("potion-type", "INSTANT_HEAL"),
                    section.getInt("potion-level", 2),
                    section.getBoolean("splash", false)
            );
            if (fallback != null) {
                out.add(fallback);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private ItemStack potionFromConfig(Object entry) {
        if (entry instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) entry;
            Object typeObj = map.get("type");
            if (typeObj == null) {
                return null;
            }
            int level = 1;
            Object levelObj = map.get("level");
            if (levelObj instanceof Number) {
                level = ((Number) levelObj).intValue();
            }
            boolean splash = Boolean.TRUE.equals(map.get("splash"));
            return createPotion(String.valueOf(typeObj), level, splash);
        }
        return null;
    }

    private ItemStack createPotion(String typeName, int level, boolean splash) {
        try {
            PotionType type = PotionType.valueOf(typeName.toUpperCase(Locale.ROOT));
            int amplifier = Math.max(1, level) - 1;
            Potion potion = new Potion(type, amplifier);
            potion.setSplash(splash);
            return potion.toItemStack(1);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @EventHandler
    public void onVaultClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            invseeViewers.remove(event.getPlayer().getUniqueId());
        }
        if (!(event.getInventory().getHolder() instanceof VaultHolder)) {
            return;
        }
        VaultHolder holder = (VaultHolder) event.getInventory().getHolder();
        saveVault(holder.owner, holder.vaultId, event.getInventory());
    }

    @EventHandler
    public void onInvseeClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player viewer = (Player) event.getWhoClicked();
        if (!invseeViewers.contains(viewer.getUniqueId())) {
            InventoryHolder holder = event.getInventory().getHolder();
            if (holder instanceof GuiHolder && "ec-view".equals(((GuiHolder) holder).menu())) {
                event.setCancelled(true);
            }
            return;
        }
        event.setCancelled(true);
        viewer.updateInventory();
    }

    @EventHandler
    public void onInvseeDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        if (!invseeViewers.contains(event.getWhoClicked().getUniqueId())) {
            InventoryHolder holder = event.getInventory().getHolder();
            if (holder instanceof GuiHolder && "ec-view".equals(((GuiHolder) holder).menu())) {
                event.setCancelled(true);
            }
            return;
        }
        event.setCancelled(true);
        ((Player) event.getWhoClicked()).updateInventory();
    }

    private void loadVault(UUID uuid, int vaultId, Inventory inv) {
        String key = vaultKey(vaultId);
        List<String> stored = plugin.data().getList(uuid, key);
        if (stored.isEmpty() && vaultId == 1) {
            List<String> legacy = plugin.data().getList(uuid, "pv_vault");
            if (!legacy.isEmpty()) {
                stored = legacy;
                plugin.data().setList(uuid, key, legacy);
            }
        }
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, null);
        }
        for (int i = 0; i < stored.size() && i < inv.getSize(); i++) {
            String raw = stored.get(i);
            if (raw == null || raw.isEmpty()) {
                continue;
            }
            ItemStack item = Items.fromBase64(raw);
            if (item != null) {
                inv.setItem(i, item);
            }
        }
    }

    private void saveVault(UUID uuid, int vaultId, Inventory inv) {
        List<String> stored = new ArrayList<String>();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack != null) {
                stored.add(Items.toBase64(stack));
            } else {
                stored.add("");
            }
        }
        plugin.data().setList(uuid, vaultKey(vaultId), stored);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        rememberBack(event.getEntity());
    }

    public void rememberBack(Player player) {
        plugin.data().setString(player.getUniqueId(), "back_location", Locations.serialize(player.getLocation()));
    }

    private void bottleXp(Player player) {
        int per = plugin.getConfig().getInt("core.bottle-xp.xp-per-bottle", 20);
        int total = player.getTotalExperience();
        if (total < per) {
            plugin.msg(player, "&cPas assez d'XP. &7(" + per + " requis)");
            return;
        }
        int bottles = Math.min(64, total / per);
        int take = bottles * per;
        player.setTotalExperience(0);
        player.setLevel(0);
        player.setExp(0f);
        player.giveExp(total - take);
        ItemStack item = new org.bukkit.inventory.ItemStack(Material.EXP_BOTTLE, bottles);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(CC.color("&aBouteille d'XP"));
                    java.util.List<String> lore = new ArrayList<String>();
                    lore.add(CC.color("&7Contient &e" + per + " XP &7chacune."));
        meta.setLore(lore);
        item.setItemMeta(meta);
        player.getInventory().addItem(item);
        plugin.msg(player, "&a+" + bottles + " bouteilles d'XP.");
    }

    private void compact(Player player) {
        int compacted = 0;
        compacted += compactMaterial(player, Material.IRON_INGOT, (short) 0, Material.IRON_BLOCK, (short) 0, 9);
        compacted += compactMaterial(player, Material.GOLD_INGOT, (short) 0, Material.GOLD_BLOCK, (short) 0, 9);
        compacted += compactMaterial(player, Material.DIAMOND, (short) 0, Material.DIAMOND_BLOCK, (short) 0, 9);
        compacted += compactMaterial(player, Material.EMERALD, (short) 0, Material.EMERALD_BLOCK, (short) 0, 9);
        compacted += compactMaterial(player, Material.REDSTONE, (short) 0, Material.REDSTONE_BLOCK, (short) 0, 9);
        compacted += compactMaterial(player, Material.COAL, (short) 0, Material.COAL_BLOCK, (short) 0, 9);
        compacted += compactMaterial(player, Material.INK_SACK, (short) 4, Material.LAPIS_BLOCK, (short) 0, 9);
        compacted += compactMaterial(player, Material.GOLD_NUGGET, (short) 0, Material.GOLD_INGOT, (short) 0, 9);
        compacted += compactMaterial(player, Material.WHEAT, (short) 0, Material.HAY_BLOCK, (short) 0, 9);
        compacted += compactMaterial(player, Material.SLIME_BALL, (short) 0, Material.SLIME_BLOCK, (short) 0, 9);
        if (compacted <= 0) {
            plugin.msg(player, "&cRien à compacter dans ton inventaire.");
            return;
        }
        plugin.msg(player, "&aCompactage: &e" + compacted + " &abloc(s) créé(s).");
    }

    private int compactMaterial(Player player, Material from, short fromData, Material to, short toData, int ratio) {
        int count = 0;
        ItemStack[] contents = player.getInventory().getContents();
        for (ItemStack stack : contents) {
            if (stack == null || stack.getType() != from) {
                continue;
            }
            if (stack.getDurability() != fromData) {
                continue;
            }
            if (stack.hasItemMeta() && (stack.getItemMeta().hasDisplayName() || stack.getItemMeta().hasLore())) {
                continue;
            }
            count += stack.getAmount();
        }
        int blocks = count / ratio;
        if (blocks <= 0) {
            return 0;
        }
        int remove = blocks * ratio;
        int leftToRemove = remove;
        for (int i = 0; i < contents.length && leftToRemove > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != from || stack.getDurability() != fromData) {
                continue;
            }
            if (stack.hasItemMeta() && (stack.getItemMeta().hasDisplayName() || stack.getItemMeta().hasLore())) {
                continue;
            }
            int take = Math.min(stack.getAmount(), leftToRemove);
            stack.setAmount(stack.getAmount() - take);
            leftToRemove -= take;
            if (stack.getAmount() <= 0) {
                contents[i] = null;
            }
        }
        player.getInventory().setContents(contents);
        ItemStack result = new ItemStack(to, blocks, toData);
        java.util.Map<Integer, ItemStack> leftover = player.getInventory().addItem(result);
        for (ItemStack extra : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), extra);
        }
        return blocks;
    }

    private void hat(Player player) {
        ItemStack hand = player.getItemInHand();
        if (hand == null || hand.getType() == Material.AIR) {
            plugin.msg(player, "&cPrends un item en main.");
            return;
        }
        ItemStack helmet = player.getInventory().getHelmet();
        player.getInventory().setHelmet(hand.clone());
        player.setItemInHand(helmet);
        plugin.msg(player, "&aItem placé sur ta tête.");
    }

    private void goBack(Player player) {
        Location loc = Locations.deserialize(plugin.data().getString(player.getUniqueId(), "back_location"));
        if (loc == null) {
            plugin.msg(player, "&cAucune position précédente.");
            return;
        }
        plugin.teleports().request(player, loc, "&aRetour à ta dernière position.");
    }

    private void repairHand(Player player) {
        ItemStack item = player.getItemInHand();
        if (item == null || item.getType() == Material.AIR || !Locations.isTool(item)) {
            plugin.msg(player, "&cPrends un item réparable en main.");
            return;
        }
        item.setDurability((short) 0);
        player.setItemInHand(item);
        plugin.msg(player, "&aItem réparé.");
    }

    private void repairAll(Player player) {
        int repaired = 0;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (repairItem(contents[i])) {
                repaired++;
            }
        }
        player.getInventory().setContents(contents);
        ItemStack[] armor = player.getInventory().getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            if (repairItem(armor[i])) {
                repaired++;
            }
        }
        player.getInventory().setArmorContents(armor);
        plugin.msg(player, "&a" + repaired + " item(s) réparé(s).");
    }

    private boolean repairItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !Locations.isTool(item)) {
            return false;
        }
        if (item.getDurability() == 0) {
            return false;
        }
        item.setDurability((short) 0);
        return true;
    }

    private void viewEnderchest(Player player, String name) {
        if (!plugin.grades().hasMinGrade(player, "supreme") && !player.hasPermission("draftmc.admin")) {
            plugin.msg(player, "&cSeul le grade Supreme peut voir l'enderchest d'un joueur.");
            return;
        }
        Player target = Bukkit.getPlayer(name);
        if (target == null) {
            plugin.msg(player, "&cJoueur hors-ligne.");
            return;
        }
        Inventory inv = Bukkit.createInventory(new GuiHolder("ec-view"), 27, CC.color("&8EC &7" + target.getName()));
        ItemStack[] contents = target.getEnderChest().getContents();
        for (int i = 0; i < contents.length && i < inv.getSize(); i++) {
            inv.setItem(i, contents[i] == null ? null : contents[i].clone());
        }
        player.openInventory(inv);
        plugin.msg(player, "&7Enderchest de &e" + target.getName() + " &8(lecture seule)");
    }

    private void invsee(Player player, String[] args) {
        if (args.length < 1) {
            plugin.msg(player, "&e/invsee <joueur>");
            return;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            plugin.msg(player, "&cJoueur introuvable.");
            return;
        }
        player.openInventory(target.getInventory());
        invseeViewers.add(player.getUniqueId());
        plugin.msg(player, "&7Inventaire de &e" + target.getName() + " &8(lecture seule)");
    }

    private void sellAll(Player player) {
        ConfigurationSection prices = plugin.getConfig().getConfigurationSection("sell-prices");
        if (prices == null || prices.getKeys(false).isEmpty()) {
            plugin.msg(player, "&cAucun prix de vente configuré.");
            return;
        }
        double total = 0;
        int sold = 0;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() == Material.AIR) {
                continue;
            }
            if (stack.hasItemMeta() && stack.getItemMeta().hasDisplayName()) {
                continue;
            }
            String key = stack.getType().name();
            if (!prices.contains(key)) {
                continue;
            }
            double unit = prices.getDouble(key);
            if (unit <= 0) {
                continue;
            }
            total += unit * stack.getAmount();
            sold += stack.getAmount();
            contents[i] = null;
        }
        player.getInventory().setContents(contents);
        if (sold <= 0) {
            plugin.msg(player, "&cRien à vendre (items vanilla listés dans sell-prices).");
            return;
        }
        plugin.economy().deposit(player, total);
        plugin.msg(player, "&aVendu &e" + sold + " &aitems pour &e" + plugin.economy().format(total) + "&a.");
    }

    private String formatDuration(int seconds) {
        if (seconds >= 3600) {
            return (seconds / 3600) + "h" + ((seconds % 3600) / 60) + "m";
        }
        if (seconds >= 60) {
            return (seconds / 60) + "m" + (seconds % 60) + "s";
        }
        return seconds + "s";
    }

    private String vaultKey(int vaultId) {
        return "pv_vault_" + vaultId;
    }

    private String join(List<String> values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(values.get(i));
        }
        return sb.toString();
    }

    private static class VaultHolder implements InventoryHolder {
        private final UUID owner;
        private final int vaultId;
        private Inventory inventory;

        private VaultHolder(UUID owner, int vaultId) {
            this.owner = owner;
            this.vaultId = vaultId;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
