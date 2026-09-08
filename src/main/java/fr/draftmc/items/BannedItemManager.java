package fr.draftmc.items;

import fr.draftmc.Draftmc;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.BrewingStand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.potion.Potion;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Interdiction d'items, potions et enchantements.
 * Ni craftables, ni brewables, ni utilisables.
 */
public class BannedItemManager implements Listener, CommandExecutor {
    private final Draftmc plugin;
    private final Set<Material> bannedAll = new HashSet<Material>();
    private final Set<String> bannedWithData = new HashSet<String>();
    private final Set<PotionType> bannedPotions = new HashSet<PotionType>();
    private final Map<PotionType, Integer> potionMinLevel = new HashMap<PotionType, Integer>();
    private final Set<Enchantment> bannedEnchants = new HashSet<Enchantment>();

    public BannedItemManager(Draftmc plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        bannedAll.clear();
        bannedWithData.clear();
        bannedPotions.clear();
        potionMinLevel.clear();
        bannedEnchants.clear();

        for (String raw : plugin.getConfig().getStringList("banned-items.items")) {
            if (raw == null || raw.trim().isEmpty()) {
                continue;
            }
            String[] parts = raw.trim().toUpperCase(Locale.ROOT).split(":");
            Material material = Material.matchMaterial(parts[0]);
            if (material == null) {
                plugin.getLogger().warning("banned-items: matériau inconnu \"" + raw + "\" ignoré.");
                continue;
            }
            if (parts.length >= 2) {
                try {
                    bannedWithData.add(material.name() + ":" + Short.parseShort(parts[1]));
                } catch (NumberFormatException ex) {
                    plugin.getLogger().warning("banned-items: data invalide \"" + raw + "\" ignoré.");
                }
            } else {
                bannedAll.add(material);
            }
        }

        for (String raw : plugin.getConfig().getStringList("banned-items.potions")) {
            PotionType type = potionType(raw);
            if (type != null) {
                bannedPotions.add(type);
            }
        }

        if (plugin.getConfig().isConfigurationSection("banned-items.potion-min-level")) {
            for (String key : plugin.getConfig().getConfigurationSection("banned-items.potion-min-level").getKeys(false)) {
                PotionType type = potionType(key);
                int min = plugin.getConfig().getInt("banned-items.potion-min-level." + key, 2);
                if (type != null) {
                    potionMinLevel.put(type, Math.max(1, min));
                }
            }
        }

        for (String raw : plugin.getConfig().getStringList("banned-items.enchantments")) {
            Enchantment enchant = Enchantment.getByName(raw.trim().toUpperCase(Locale.ROOT));
            if (enchant == null) {
                plugin.getLogger().warning("banned-items: enchantement inconnu \"" + raw + "\" ignoré.");
                continue;
            }
            bannedEnchants.add(enchant);
        }

        plugin.getLogger().info("Interdits: " + bannedAll.size() + " matériaux, "
                + bannedWithData.size() + " variantes, "
                + bannedPotions.size() + " potions, "
                + bannedEnchants.size() + " enchantements.");
    }

    private static PotionType potionType(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return PotionType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("banned-items.enabled", true);
    }

    public boolean isBanned(ItemStack stack) {
        if (!enabled() || stack == null || stack.getType() == Material.AIR) {
            return false;
        }
        if (bannedAll.contains(stack.getType())) {
            return true;
        }
        if (bannedWithData.contains(stack.getType().name() + ":" + stack.getDurability())) {
            return true;
        }
        if (hasBannedEnchant(stack)) {
            return true;
        }
        return isBannedPotion(stack);
    }

    private boolean isBannedPotion(ItemStack stack) {
        if (stack.getType() != Material.POTION) {
            return false;
        }
        try {
            Potion potion = Potion.fromItemStack(stack);
            if (potion == null || potion.getType() == null) {
                return false;
            }
            PotionType type = potion.getType();
            if (bannedPotions.contains(type)) {
                return true;
            }
            Integer min = potionMinLevel.get(type);
            return min != null && potion.getLevel() >= min;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean hasBannedEnchant(ItemStack stack) {
        if (bannedEnchants.isEmpty()) {
            return false;
        }
        for (Enchantment enchant : stack.getEnchantments().keySet()) {
            if (bannedEnchants.contains(enchant)) {
                return true;
            }
        }
        if (stack.getType() == Material.ENCHANTED_BOOK && stack.hasItemMeta()
                && stack.getItemMeta() instanceof EnchantmentStorageMeta) {
            EnchantmentStorageMeta meta = (EnchantmentStorageMeta) stack.getItemMeta();
            for (Enchantment enchant : meta.getStoredEnchants().keySet()) {
                if (bannedEnchants.contains(enchant)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void deny(Player player) {
        plugin.msg(player, plugin.getConfig().getString("banned-items.message",
                "&cCet item est interdit sur le serveur."));
    }

    private boolean bypass(Player player) {
        return plugin.getConfig().getBoolean("banned-items.admin-bypass", true)
                && player.hasPermission("draftmc.banitem.bypass");
    }

    @EventHandler(ignoreCancelled = true)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result != null && isBanned(result)) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        ItemStack result = event.getRecipe() == null ? null : event.getRecipe().getResult();
        if (result != null && isBanned(result)) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player) {
                deny((Player) event.getWhoClicked());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        final org.bukkit.block.Block block = event.getBlock();
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (!(block.getState() instanceof BrewingStand)) {
                    return;
                }
                BrewerInventory inv = ((BrewingStand) block.getState()).getInventory();
                for (int i = 0; i < 3; i++) {
                    ItemStack bottle = inv.getItem(i);
                    if (bottle != null && isBanned(bottle)) {
                        inv.setItem(i, new ItemStack(Material.GLASS_BOTTLE));
                    }
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        if (!enabled() || bannedEnchants.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<Enchantment, Integer>> it = event.getEnchantsToAdd().entrySet().iterator();
        boolean removed = false;
        while (it.hasNext()) {
            if (bannedEnchants.contains(it.next().getKey())) {
                it.remove();
                removed = true;
            }
        }
        if (removed && event.getEnchantsToAdd().isEmpty()) {
            event.setCancelled(true);
            deny(event.getEnchanter());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAnvil(InventoryClickEvent event) {
        if (event.getInventory() == null || event.getInventory().getType() != InventoryType.ANVIL) {
            return;
        }
        if (event.getRawSlot() != 2) {
            return;
        }
        ItemStack result = event.getCurrentItem();
        if (result == null || !isBanned(result)) {
            return;
        }
        if (event.getWhoClicked() instanceof Player && bypass((Player) event.getWhoClicked())) {
            return;
        }
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player) {
            deny((Player) event.getWhoClicked());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        ItemStack hand = event.getItem();
        if (hand == null || !isBanned(hand) || bypass(event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
        deny(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack hand = event.getItemInHand();
        if (hand == null || !isBanned(hand) || bypass(event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
        deny(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (!isBanned(event.getItem()) || bypass(event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
        deny(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        Player attacker = null;
        if (event.getDamager() instanceof Player) {
            attacker = (Player) event.getDamager();
        } else if (event.getDamager() instanceof Projectile
                && ((Projectile) event.getDamager()).getShooter() instanceof Player) {
            attacker = (Player) ((Projectile) event.getDamager()).getShooter();
        }
        if (attacker == null) {
            return;
        }
        ItemStack hand = attacker.getItemInHand();
        if (hand == null || !isBanned(hand) || bypass(attacker)) {
            return;
        }
        event.setCancelled(true);
        deny(attacker);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSplash(PotionSplashEvent event) {
        if (isBanned(event.getPotion().getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        if (isBanned(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("draftmc.admin")) {
            plugin.msg(sender, "&cPas la permission.");
            return true;
        }
        if (args.length > 0 && "list".equalsIgnoreCase(args[0])) {
            plugin.msg(sender, "&6Items: &f" + join(plugin.getConfig().getStringList("banned-items.items")));
            plugin.msg(sender, "&6Potions: &f" + join(plugin.getConfig().getStringList("banned-items.potions")));
            plugin.msg(sender, "&6Enchantements: &f" + join(plugin.getConfig().getStringList("banned-items.enchantments")));
            return true;
        }
        if (!(sender instanceof Player)) {
            plugin.msg(sender, "&e/banitem &7(item en main) ou &e/banitem list");
            return true;
        }
        Player player = (Player) sender;
        ItemStack hand = player.getItemInHand();
        if (hand == null || hand.getType() == Material.AIR) {
            plugin.msg(player, "&cPrends l'item à interdire en main. &7(&e/banitem list &7pour voir la liste)");
            return true;
        }

        String entry = hand.getType().name();
        if (hand.getDurability() != 0 && hand.getType().getMaxDurability() == 0) {
            entry = entry + ":" + hand.getDurability();
        }

        List<String> items = new ArrayList<String>(plugin.getConfig().getStringList("banned-items.items"));
        if (items.remove(entry)) {
            plugin.msg(player, "&aItem &e" + entry + " &aretiré de la liste des interdits.");
        } else {
            items.add(entry);
            plugin.msg(player, "&cItem &e" + entry + " &cajouté à la liste des interdits.");
        }
        plugin.getConfig().set("banned-items.items", items);
        plugin.saveConfig();
        reload();
        return true;
    }

    private static String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "(aucun)";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append("&7, &f");
            }
            sb.append(values.get(i));
        }
        return sb.toString();
    }
}
