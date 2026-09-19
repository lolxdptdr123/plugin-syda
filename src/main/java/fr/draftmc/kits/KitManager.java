package fr.draftmc.kits;

import fr.draftmc.Draftmc;
import fr.draftmc.util.CC;
import fr.draftmc.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.Potion;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class KitManager implements Listener, CommandExecutor {
    private final Draftmc plugin;
    private FileConfiguration kitsConfig;

    public KitManager(Draftmc plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        java.io.File file = new java.io.File(plugin.getDataFolder(), "kits.yml");
        if (!file.exists()) {
            plugin.saveResource("kits.yml", false);
        }
        kitsConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
    }

    public FileConfiguration kitsConfig() {
        return kitsConfig;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cJoueur uniquement.");
            return true;
        }
        if (args.length > 0) {
            claim((Player) sender, args[0]);
            return true;
        }
        open((Player) sender);
        return true;
    }

    public void open(Player player) {
        ConfigurationSection kits = kitsSection();
        if (kits == null || kits.getKeys(false).isEmpty()) {
            plugin.msg(player, "&cAucun kit configure.");
            return;
        }
        int size = Math.max(9, Math.min(54, kitsConfig.getInt("gui.size", 54)));
        size = (size / 9) * 9;
        String title = CC.color(kitsConfig.getString("gui.title", "&8Mes kits"));
        KitGuiHolder holder = new KitGuiHolder();
        Inventory inv = Bukkit.createInventory(holder, size, title);
        holder.inventory = inv;
        fillGui(inv, size);

        List<String> gradeKits = new ArrayList<String>();
        List<String> shopKits = new ArrayList<String>();
        for (String id : kits.getKeys(false)) {
            ConfigurationSection section = kits.getConfigurationSection(id);
            if (isGradeKit(section)) {
                gradeKits.add(id);
            } else {
                shopKits.add(id);
            }
        }

        Map<Integer, String> slotMap = new HashMap<Integer, String>();
        placeKits(player, inv, kits, gradeKits, gradeSlots(), size, slotMap);
        placeKits(player, inv, kits, shopKits, shopSlots(), size, slotMap);
        holder.slotToKit = slotMap;
        player.openInventory(inv);
    }

    private void placeKits(Player player, Inventory inv, ConfigurationSection kits,
                            List<String> ids, List<Integer> slots, int size, Map<Integer, String> slotMap) {
        for (int i = 0; i < ids.size() && i < slots.size(); i++) {
            int slot = slots.get(i);
            if (slot < 0 || slot >= size) {
                continue;
            }
            String kitId = ids.get(i);
            inv.setItem(slot, displayKit(player, kitId, kits.getConfigurationSection(kitId)));
            slotMap.put(slot, kitId.toUpperCase(Locale.ROOT));
        }
    }

    private void fillGui(Inventory inv, int size) {
        short borderData = (short) kitsConfig.getInt("gui.border-data", 3);
        short innerData = (short) kitsConfig.getInt("gui.inner-data", 7);
        ItemStack border = new ItemBuilder(Material.STAINED_GLASS_PANE, 1, borderData).name("&r").build();
        ItemStack inner = new ItemBuilder(Material.STAINED_GLASS_PANE, 1, innerData).name("&r").build();
        int rows = size / 9;
        for (int slot = 0; slot < size; slot++) {
            int col = slot % 9;
            int row = slot / 9;
            boolean edge = row == 0 || row == rows - 1 || col == 0 || col == 8 || col == 4;
            inv.setItem(slot, edge ? border : inner);
        }
    }

    private boolean isGradeKit(ConfigurationSection section) {
        if (section == null) {
            return false;
        }
        String minGrade = section.getString("min-grade");
        return minGrade != null && !minGrade.isEmpty();
    }

    private List<Integer> gradeSlots() {
        List<Integer> slots = kitsConfig.getIntegerList("gui.grade-slots");
        if (slots == null || slots.isEmpty()) {
            return toList(10, 11, 12, 19, 20, 21, 28, 29, 30, 37, 38, 39);
        }
        return slots;
    }

    private List<Integer> shopSlots() {
        List<Integer> slots = kitsConfig.getIntegerList("gui.kit-slots");
        if (slots == null || slots.isEmpty()) {
            return toList(14, 15, 16, 23, 24, 25, 32, 33, 34);
        }
        return slots;
    }

    private List<Integer> toList(int... values) {
        List<Integer> out = new ArrayList<Integer>();
        for (int value : values) {
            out.add(value);
        }
        return out;
    }

    private ItemStack displayKit(Player player, String kitId, ConfigurationSection section) {
        if (section == null) {
            return new ItemBuilder(Material.BARRIER).name("&cKit invalide").build();
        }
        String normalizedId = kitId.toUpperCase(Locale.ROOT);
        Material material = Material.matchMaterial(section.getString("material", "CHEST"));
        if (material == null) {
            material = Material.CHEST;
        }
        boolean owned = canUseKit(player, normalizedId, section);
        int remaining = remainingCooldown(player.getUniqueId(), normalizedId, section.getInt("cooldown-seconds", 0));
        List<String> lore = new ArrayList<String>(section.getStringList("lore"));
        lore.add("");
        if (!owned) {
            lore.add("&cNon debloque");
            String minGrade = section.getString("min-grade");
            if (minGrade != null && !minGrade.isEmpty()) {
                lore.add("&7Grade requis: &e" + plugin.grades().displayNameForGroup(minGrade));
            } else {
                lore.add("&7Achète ce kit dans &e/boutique");
            }
        } else if (remaining > 0) {
            lore.add("&eCooldown: &c" + formatTime(remaining));
        } else {
            lore.add("&aDisponible");
            lore.add("&eClique pour recuperer le kit");
        }
        int cooldown = section.getInt("cooldown-seconds", 0);
        if (cooldown > 0 && owned) {
            lore.add("&7Cooldown: &f" + formatTime(cooldown));
        }
        short data = (short) section.getInt("data", 0);
        return new ItemBuilder(material, 1, data)
                .name(section.getString("name", "&f" + kitId))
                .lore(lore)
                .build();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof KitGuiHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        KitGuiHolder holder = (KitGuiHolder) event.getView().getTopInventory().getHolder();
        String kitId = holder.slotToKit.get(event.getRawSlot());
        if (kitId == null) {
            return;
        }
        claim(player, kitId);
        open(player);
    }

    public boolean claim(Player player, String kitId) {
        String normalizedId = kitId.toUpperCase(Locale.ROOT);
        ConfigurationSection section = kitSection(normalizedId);
        if (section == null) {
            plugin.msg(player, "&cKit inconnu.");
            return false;
        }
        if (!ownsKit(player.getUniqueId(), normalizedId) && !hasGradeKit(player, normalizedId, section)) {
            plugin.msg(player, "&cTu ne possèdes pas ce kit.");
            return false;
        }
        int cooldown = section.getInt("cooldown-seconds", 0);
        int remaining = remainingCooldown(player.getUniqueId(), normalizedId, cooldown);
        if (remaining > 0) {
            plugin.msg(player, "&cKit en cooldown. &7Attends &e" + formatTime(remaining) + "&7.");
            return false;
        }
        List<KitEntry> items = buildKitEntries(section);
        if (items.isEmpty()) {
            plugin.msg(player, "&cCe kit ne contient aucun item.");
            return false;
        }
        for (KitEntry entry : items) {
            giveEntry(player, entry);
        }
        if (cooldown > 0) {
            plugin.data().setLong(player.getUniqueId(), cooldownKey(normalizedId),
                    System.currentTimeMillis() + cooldown * 1000L);
        }
        String msg = kitsConfig.getString("messages.claimed", "&aKit &e%kit% &arecupere.");
        plugin.msg(player, msg.replace("%kit%", CC.strip(section.getString("name", normalizedId))));
        return true;
    }

    public boolean unlockKit(UUID uuid, String kitId) {
        String normalizedId = kitId.toUpperCase(Locale.ROOT);
        if (kitSection(normalizedId) == null) {
            return false;
        }
        List<String> owned = plugin.data().getList(uuid, "kits_owned");
        if (owned.contains(normalizedId)) {
            return false;
        }
        owned.add(normalizedId);
        plugin.data().setList(uuid, "kits_owned", owned);
        return true;
    }

    public boolean ownsKit(UUID uuid, String kitId) {
        return plugin.data().getList(uuid, "kits_owned").contains(kitId.toUpperCase(Locale.ROOT));
    }

    public boolean canUseKit(Player player, String kitId, ConfigurationSection section) {
        if (ownsKit(player.getUniqueId(), kitId)) {
            return true;
        }
        return hasGradeKit(player, kitId, section);
    }

    private boolean hasGradeKit(Player player, String kitId, ConfigurationSection section) {
        if (section == null) {
            section = kitSection(kitId);
        }
        if (section == null) {
            return false;
        }
        String minGrade = section.getString("min-grade");
        return minGrade != null && !minGrade.isEmpty() && plugin.grades().hasMinGrade(player, minGrade);
    }

    public ItemStack buildShopIcon(Player player, ConfigurationSection sec, String id, boolean money) {
        String kitId = sec.getString("kit", id).toUpperCase(Locale.ROOT);
        ConfigurationSection kit = kitSection(kitId);
        Material material = Material.CHEST;
        String name = sec.getString("name", kitId);
        List<String> lore = new ArrayList<String>(sec.getStringList("lore"));
        if (kit != null) {
            Material kitMat = Material.matchMaterial(kit.getString("material", "CHEST"));
            if (kitMat != null) {
                material = kitMat;
            }
            if (!sec.contains("name")) {
                name = kit.getString("name", name);
            }
            if (lore.isEmpty()) {
                lore.addAll(kit.getStringList("lore"));
            }
        }
        boolean owned = ownsKit(player.getUniqueId(), kitId);
        lore.add("");
        if (owned) {
            lore.add("&aDeja debloque");
        } else if (money) {
            lore.add("&7Prix: &a" + plugin.economy().format(sec.getDouble("price", 0.0)) + " &7(argent)");
        } else {
            lore.add("&7Prix: &6" + sec.getLong("price", 0L) + " tokens");
        }
        lore.add(owned ? "&8Utilise &e/kit &8pour le recuperer." : "&eClique pour acheter.");
        return new ItemBuilder(material).name(name).lore(lore).build();
    }

    public void sendPurchasedMessage(Player player, String kitId) {
        String msg = kitsConfig.getString("messages.purchased", "&aKit &e%kit% &adebloque. Utilise &e/kit&a.");
        ConfigurationSection kit = kitSection(kitId);
        String display = kit != null ? kit.getString("name", kitId) : kitId;
        plugin.msg(player, msg.replace("%kit%", CC.strip(display)));
    }

    private void giveEntry(Player player, KitEntry entry) {
        if (entry.keepStack) {
            int remaining = entry.count;
            while (remaining > 0) {
                ItemStack stack = entry.template.clone();
                int stackSize = Math.min(remaining, stack.getMaxStackSize());
                stack.setAmount(stackSize);
                remaining -= stackSize;
                giveOne(player, stack);
            }
            return;
        }
        for (int i = 0; i < entry.count; i++) {
            giveOne(player, entry.template.clone());
        }
    }

    private void giveOne(Player player, ItemStack stack) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stack);
        for (ItemStack left : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
    }

    private List<KitEntry> buildKitEntries(ConfigurationSection kitSection) {
        List<KitEntry> out = new ArrayList<KitEntry>();
        List<?> entries = kitSection.getList("items");
        if (entries == null) {
            return out;
        }
        for (Object entry : entries) {
            KitEntry kitEntry = entryFromConfig(entry);
            if (kitEntry != null) {
                out.add(kitEntry);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private KitEntry entryFromConfig(Object entry) {
        Map<String, Object> map;
        if (entry instanceof ConfigurationSection) {
            map = sectionToMap((ConfigurationSection) entry);
        } else if (entry instanceof Map) {
            map = (Map<String, Object>) entry;
        } else {
            return null;
        }
        ItemStack template = itemTemplateFromMap(map);
        if (template == null) {
            return null;
        }
        applyEnchants(map, template);
        template.setAmount(1);
        KitEntry kitEntry = new KitEntry();
        kitEntry.template = template;
        kitEntry.count = Math.max(1, parseInt(map.get("amount"), 1));
        kitEntry.keepStack = Boolean.TRUE.equals(map.get("stack"));
        return kitEntry;
    }

    private Map<String, Object> sectionToMap(ConfigurationSection section) {
        Map<String, Object> map = new HashMap<String, Object>();
        for (String key : section.getKeys(false)) {
            map.put(key, section.get(key));
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private ItemStack itemTemplateFromMap(Map<String, Object> map) {
        if (map.containsKey("item")) {
            return plugin.items().create(String.valueOf(map.get("item")));
        }

        Object potionObj = map.get("potion");
        if (potionObj instanceof Map) {
            Map<String, Object> potionMap = (Map<String, Object>) potionObj;
            return createPotion(
                    String.valueOf(potionMap.get("type")),
                    parseInt(potionMap.get("level"), 1),
                    Boolean.TRUE.equals(potionMap.get("splash"))
            );
        }
        if (map.containsKey("potion-type")) {
            return createPotion(
                    String.valueOf(map.get("potion-type")),
                    parseInt(map.get("level"), 1),
                    Boolean.TRUE.equals(map.get("splash"))
            );
        }
        if (map.containsKey("type") && "potion".equalsIgnoreCase(String.valueOf(map.get("type")))) {
            Object potionField = map.get("potion");
            String typeName = potionField != null ? String.valueOf(potionField) : "INSTANT_HEAL";
            return createPotion(typeName, parseInt(map.get("level"), 1), Boolean.TRUE.equals(map.get("splash")));
        }

        Material material = Material.matchMaterial(String.valueOf(map.get("material")));
        if (material == null) {
            return null;
        }
        return new ItemStack(material, 1);
    }

    private void applyEnchants(Map<String, Object> map, ItemStack stack) {
        Object enchantsObj = map.get("enchants");
        if (enchantsObj == null) {
            enchantsObj = map.get("enchantments");
        }
        applyEnchantsObject(enchantsObj, stack);
        if (stack.getType() == Material.BOW && hasInfinityRequest(enchantsObj)) {
            applyEnchant(stack, "ARROW_INFINITE", 1);
        }
    }

    private boolean hasInfinityRequest(Object enchantsObj) {
        if (enchantsObj == null) {
            return false;
        }
        if (enchantsObj instanceof ConfigurationSection) {
            for (String key : ((ConfigurationSection) enchantsObj).getKeys(false)) {
                if (isInfinityName(key)) {
                    return true;
                }
            }
            return false;
        }
        if (enchantsObj instanceof Map) {
            for (Object key : ((Map<?, ?>) enchantsObj).keySet()) {
                if (isInfinityName(String.valueOf(key))) {
                    return true;
                }
            }
            return false;
        }
        if (enchantsObj instanceof List) {
            for (Object entry : (List<?>) enchantsObj) {
                if (entry instanceof Map) {
                    Map<?, ?> emap = (Map<?, ?>) entry;
                    if (isInfinityName(String.valueOf(emap.get("type")))) {
                        return true;
                    }
                    for (Object key : emap.keySet()) {
                        if (isInfinityName(String.valueOf(key))) {
                            return true;
                        }
                    }
                } else if (isInfinityName(String.valueOf(entry))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isInfinityName(String name) {
        if (name == null) {
            return false;
        }
        String upper = name.toUpperCase(Locale.ROOT);
        return upper.contains("INFIN") || upper.contains("ARROW_INFINITE");
    }

    private void applyEnchantsObject(Object enchantsObj, ItemStack stack) {
        if (enchantsObj == null) {
            return;
        }
        if (enchantsObj instanceof ConfigurationSection) {
            ConfigurationSection section = (ConfigurationSection) enchantsObj;
            for (String key : section.getKeys(false)) {
                applyEnchant(stack, key, section.getInt(key, 1));
            }
            return;
        }
        if (enchantsObj instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) enchantsObj).entrySet()) {
                applyEnchant(stack, String.valueOf(entry.getKey()), parseInt(entry.getValue(), 1));
            }
            return;
        }
        if (!(enchantsObj instanceof List)) {
            return;
        }
        for (Object entry : (List<?>) enchantsObj) {
            if (entry instanceof ConfigurationSection) {
                applyEnchantsObject(entry, stack);
                continue;
            }
            if (entry instanceof Map) {
                Map<?, ?> emap = (Map<?, ?>) entry;
                if (emap.containsKey("type")) {
                    applyEnchant(stack, String.valueOf(emap.get("type")), parseInt(emap.get("level"), 1));
                } else {
                    for (Map.Entry<?, ?> enchantEntry : emap.entrySet()) {
                        applyEnchant(stack, String.valueOf(enchantEntry.getKey()),
                                parseInt(enchantEntry.getValue(), 1));
                    }
                }
                continue;
            }
            String raw = String.valueOf(entry);
            String[] parts = raw.split("[: ]+");
            applyEnchant(stack, parts[0], parts.length > 1 ? parseInt(parts[1], 1) : 1);
        }
    }

    private void applyEnchant(ItemStack stack, String name, int level) {
        Enchantment enchant = resolveEnchantment(name);
        if (enchant == null) {
            plugin.getLogger().warning("Kit: enchantement inconnu '" + name + "'");
            return;
        }
        int safeLevel = Math.max(1, level);
        stack.addUnsafeEnchantment(enchant, safeLevel);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.addEnchant(enchant, safeLevel, true);
            stack.setItemMeta(meta);
        }
    }

    private Enchantment resolveEnchantment(String name) {
        if (name == null || name.isEmpty() || "null".equalsIgnoreCase(name)) {
            return null;
        }
        String upper = name.toUpperCase(Locale.ROOT).replace(" ", "_").replace("-", "_");
        if (upper.contains("INFIN")) {
            return infinityEnchant();
        }
        if ("PROTECTION".equals(upper) || "PROT".equals(upper)) {
            upper = "PROTECTION_ENVIRONMENTAL";
        } else if ("UNBREAKING".equals(upper)) {
            upper = "DURABILITY";
        } else if ("SHARPNESS".equals(upper)) {
            upper = "DAMAGE_ALL";
        } else if ("EFFICIENCY".equals(upper)) {
            upper = "DIG_SPEED";
        } else if ("FEATHER_FALLING".equals(upper)) {
            upper = "PROTECTION_FALL";
        } else if ("BLAST_PROTECTION".equals(upper)) {
            upper = "PROTECTION_EXPLOSIONS";
        } else if ("FIRE_PROTECTION".equals(upper)) {
            upper = "PROTECTION_FIRE";
        } else if ("PROJECTILE_PROTECTION".equals(upper)) {
            upper = "PROTECTION_PROJECTILE";
        } else if ("POWER".equals(upper)) {
            upper = "ARROW_DAMAGE";
        } else if ("PUNCH".equals(upper)) {
            upper = "ARROW_KNOCKBACK";
        } else if ("FLAME".equals(upper)) {
            upper = "ARROW_FIRE";
        }
        Enchantment byName = Enchantment.getByName(upper);
        if (byName != null) {
            return byName;
        }
        for (Enchantment enchantment : Enchantment.values()) {
            if (enchantment != null && enchantment.getName() != null
                    && enchantment.getName().equalsIgnoreCase(upper)) {
                return enchantment;
            }
        }
        return null;
    }

    private Enchantment infinityEnchant() {
        try {
            if (Enchantment.ARROW_INFINITE != null) {
                return Enchantment.ARROW_INFINITE;
            }
        } catch (Throwable ignored) {
        }
        Enchantment named = Enchantment.getByName("ARROW_INFINITE");
        if (named != null) {
            return named;
        }
        named = Enchantment.getByName("INFINITY");
        if (named != null) {
            return named;
        }
        try {
            return Enchantment.getById(51);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private ItemStack createPotion(String typeName, int level, boolean splash) {
        try {
            PotionType type = PotionType.valueOf(typeName.toUpperCase(Locale.ROOT));
            int tier = Math.max(1, level) - 1;
            Potion potion = new Potion(type, tier);
            potion.setSplash(splash);
            ItemStack stack = potion.toItemStack(1);
            return stack.getAmount() > 0 ? stack : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private int parseInt(Object value, int fallback) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private int remainingCooldown(UUID uuid, String kitId, int cooldownSeconds) {
        if (cooldownSeconds <= 0) {
            return 0;
        }
        long until = plugin.data().getLong(uuid, cooldownKey(kitId));
        long remainingMs = until - System.currentTimeMillis();
        if (remainingMs <= 0) {
            return 0;
        }
        return (int) Math.ceil(remainingMs / 1000.0);
    }

    private String cooldownKey(String kitId) {
        return "kit_cd." + kitId.toUpperCase(Locale.ROOT);
    }

    private ConfigurationSection kitsSection() {
        return kitsConfig.getConfigurationSection("kits");
    }

    private ConfigurationSection kitSection(String kitId) {
        ConfigurationSection kits = kitsSection();
        if (kits == null) {
            return null;
        }
        ConfigurationSection direct = kits.getConfigurationSection(kitId);
        if (direct != null) {
            return direct;
        }
        for (String key : kits.getKeys(false)) {
            if (key.equalsIgnoreCase(kitId)) {
                return kits.getConfigurationSection(key);
            }
        }
        return null;
    }

    private String formatTime(int seconds) {
        if (seconds >= 3600) {
            return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
        }
        if (seconds >= 60) {
            return (seconds / 60) + "m " + (seconds % 60) + "s";
        }
        return seconds + "s";
    }

    private static class KitGuiHolder implements InventoryHolder {
        private Inventory inventory;
        private Map<Integer, String> slotToKit = new HashMap<Integer, String>();

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static class KitEntry {
        private ItemStack template;
        private int count;
        private boolean keepStack;
    }
}
