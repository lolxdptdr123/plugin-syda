package fr.draftmc.events.teamfight;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.Potion;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TeamFightKit {
    private final TeamFightPlugin plugin;
    private final List<KitItem> items = new ArrayList<KitItem>();

    public TeamFightKit(TeamFightPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    @SuppressWarnings("unchecked")
    public void load() {
        items.clear();
        List<?> rawItems = plugin.getConfig().getList("kit.items");
        if (rawItems == null) {
            return;
        }
        for (Object obj : rawItems) {
            if (!(obj instanceof Map)) {
                continue;
            }
            Map<String, Object> map = (Map<String, Object>) obj;
            try {
                Material material = Material.valueOf(String.valueOf(map.get("material")));
                int amount = toInt(map.get("amount"), 1);
                String slot = map.get("slot") != null ? String.valueOf(map.get("slot")).toLowerCase() : null;
                List<?> enchantments = map.get("enchantments") instanceof List ? (List<?>) map.get("enchantments") : null;
                String potionType = map.get("potion-type") != null
                        ? String.valueOf(map.get("potion-type")).toUpperCase() : null;
                int potionLevel = toInt(map.get("potion-level"), 1);
                boolean splash = map.get("splash") instanceof Boolean && ((Boolean) map.get("splash")).booleanValue();
                items.add(new KitItem(material, amount, slot, enchantments, potionType, potionLevel, splash));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[TeamFight] Materiau de kit ignore : " + map.get("material"));
            }
        }
    }

    public void give(Player player) {
        clear(player);
        if (plugin.getConfig().getBoolean("kit.enabled", true)) {
            if (!giveSaved(player)) {
                PlayerInventory inv = player.getInventory();
                for (int i = 0; i < items.size(); i++) {
                    KitItem item = items.get(i);
                    List<ItemStack> stacks = buildStacks(item);
                    if (stacks.isEmpty()) {
                        continue;
                    }
                    if (item.slot == null) {
                        for (int s = 0; s < stacks.size(); s++) {
                            inv.addItem(stacks.get(s));
                        }
                        continue;
                    }
                    ItemStack stack = stacks.get(0);
                    if ("helmet".equals(item.slot)) {
                        inv.setHelmet(stack);
                    } else if ("chestplate".equals(item.slot)) {
                        inv.setChestplate(stack);
                    } else if ("leggings".equals(item.slot)) {
                        inv.setLeggings(stack);
                    } else if ("boots".equals(item.slot)) {
                        inv.setBoots(stack);
                    } else if ("hand".equals(item.slot)) {
                        inv.setItemInHand(stack);
                    } else {
                        try {
                            inv.setItem(Integer.parseInt(item.slot), stack);
                        } catch (NumberFormatException e) {
                            inv.addItem(stack);
                        }
                    }
                }
                player.updateInventory();
            }
        }
        applyArenaEffects(player);
    }

    public void clear(Player player) {
        PlayerInventory inv = player.getInventory();
        inv.clear();
        inv.setHelmet(null);
        inv.setChestplate(null);
        inv.setLeggings(null);
        inv.setBoots(null);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setFireTicks(0);
        player.updateInventory();
    }

    public void applyArenaEffects(Player player) {
        if (player == null) {
            return;
        }
        List<Map<?, ?>> raw = plugin.getConfig().getMapList("arena-effects");
        if (raw == null || raw.isEmpty()) {
            applyEffect(player, PotionEffectType.SPEED, 1);
            applyEffect(player, PotionEffectType.INCREASE_DAMAGE, 0);
            applyEffect(player, PotionEffectType.FIRE_RESISTANCE, 0);
            return;
        }
        for (int i = 0; i < raw.size(); i++) {
            Map<?, ?> map = raw.get(i);
            if (map == null || map.get("type") == null) {
                continue;
            }
            PotionEffectType type = PotionEffectType.getByName(String.valueOf(map.get("type")).toUpperCase());
            int amplifier = toInt(map.get("amplifier"), 0);
            applyEffect(player, type, amplifier);
        }
    }

    private void applyEffect(Player player, PotionEffectType type, int amplifier) {
        if (type == null) {
            return;
        }
        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (effect.getType().equals(type) && effect.getAmplifier() >= amplifier && effect.getDuration() > 80) {
                return;
            }
        }
        player.addPotionEffect(new PotionEffect(type, Integer.MAX_VALUE, Math.max(0, amplifier), true, false), true);
    }

    public void saveFrom(Player player) {
        PlayerInventory inv = player.getInventory();
        plugin.getConfig().set("kit.saved", null);
        ConfigurationSection section = plugin.getConfig().createSection("kit.saved");
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack != null && stack.getType() != Material.AIR) {
                section.set("contents." + i, stack);
            }
        }
        if (inv.getHelmet() != null) {
            section.set("helmet", inv.getHelmet());
        }
        if (inv.getChestplate() != null) {
            section.set("chestplate", inv.getChestplate());
        }
        if (inv.getLeggings() != null) {
            section.set("leggings", inv.getLeggings());
        }
        if (inv.getBoots() != null) {
            section.set("boots", inv.getBoots());
        }
        plugin.saveConfig();
    }

    private boolean giveSaved(Player player) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("kit.saved");
        if (section == null) {
            return false;
        }
        PlayerInventory inv = player.getInventory();
        ConfigurationSection contents = section.getConfigurationSection("contents");
        if (contents != null) {
            for (String key : contents.getKeys(false)) {
                try {
                    inv.setItem(Integer.parseInt(key), contents.getItemStack(key));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        inv.setHelmet(section.getItemStack("helmet"));
        inv.setChestplate(section.getItemStack("chestplate"));
        inv.setLeggings(section.getItemStack("leggings"));
        inv.setBoots(section.getItemStack("boots"));
        player.updateInventory();
        return true;
    }

    @SuppressWarnings("unchecked")
    private List<ItemStack> buildStacks(KitItem item) {
        List<ItemStack> stacks = new ArrayList<ItemStack>();
        if (item.material == Material.POTION && item.potionType != null) {
            try {
                PotionType type = PotionType.valueOf(item.potionType);
                PotionEffectType effectType = type.getEffectType();
                for (int i = 0; i < item.amount; i++) {
                    Potion potion = new Potion(type, Math.max(1, item.potionLevel));
                    potion.setSplash(item.splash);
                    ItemStack stack = potion.toItemStack(1);
                    if (effectType != null) {
                        ItemMeta meta = stack.getItemMeta();
                        if (meta instanceof PotionMeta) {
                            PotionMeta potionMeta = (PotionMeta) meta;
                            int amplifier = Math.max(0, item.potionLevel - 1);
                            int durationTicks = type.isInstant() ? 1 : 20 * 60;
                            potionMeta.addCustomEffect(new PotionEffect(effectType, durationTicks, amplifier), true);
                            stack.setItemMeta(potionMeta);
                        }
                    }
                    stacks.add(stack);
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[TeamFight] Potion ignoree : " + item.potionType);
            }
            return stacks;
        }
        ItemStack stack = new ItemStack(item.material, item.amount);
        if (item.enchantments != null) {
            for (Object encObj : item.enchantments) {
                if (!(encObj instanceof Map)) {
                    continue;
                }
                Map<String, Object> enc = (Map<String, Object>) encObj;
                Enchantment enchantment = Enchantment.getByName(String.valueOf(enc.get("type")).toUpperCase());
                if (enchantment != null) {
                    stack.addUnsafeEnchantment(enchantment, toInt(enc.get("level"), 1));
                }
            }
        }
        stacks.add(stack);
        return stacks;
    }

    private int toInt(Object o, int def) {
        if (o == null) {
            return def;
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static class KitItem {
        final Material material;
        final int amount;
        final String slot;
        final List<?> enchantments;
        final String potionType;
        final int potionLevel;
        final boolean splash;

        KitItem(Material material, int amount, String slot, List<?> enchantments,
                String potionType, int potionLevel, boolean splash) {
            this.material = material;
            this.amount = amount;
            this.slot = slot;
            this.enchantments = enchantments;
            this.potionType = potionType;
            this.potionLevel = potionLevel;
            this.splash = splash;
        }
    }
}
