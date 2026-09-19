package fr.draftmc.tournament;

import fr.draftmc.util.CC;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.Potion;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TournamentKit {
    private final TournamentManager manager;
    private final List<KitItem> items = new ArrayList<KitItem>();

    public TournamentKit(TournamentManager manager) {
        this.manager = manager;
        load();
    }

    @SuppressWarnings("unchecked")
    public void load() {
        items.clear();
        List<?> raw = manager.config().getList("kit.items");
        if (raw == null) {
            return;
        }
        for (Object obj : raw) {
            if (!(obj instanceof Map)) {
                continue;
            }
            Map<String, Object> map = (Map<String, Object>) obj;
            try {
                Material material = Material.valueOf(String.valueOf(map.get("material")).toUpperCase(Locale.ROOT));
                int amount = toInt(map.get("amount"), 1);
                String slot = map.get("slot") != null ? String.valueOf(map.get("slot")).toLowerCase(Locale.ROOT) : null;
                List<?> enchants = map.get("enchantments") instanceof List ? (List<?>) map.get("enchantments") : null;
                String potionType = map.get("potion-type") != null
                        ? String.valueOf(map.get("potion-type")).toUpperCase(Locale.ROOT) : null;
                int potionLevel = toInt(map.get("potion-level"), 1);
                boolean splash = Boolean.TRUE.equals(map.get("splash"));
                items.add(new KitItem(material, amount, slot, enchants, potionType, potionLevel, splash));
            } catch (Exception ignored) {
            }
        }
    }

    public void give(Player player) {
        clear(player);
        if (!manager.config().getBoolean("kit.enabled", true)) {
            return;
        }
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < items.size(); i++) {
            KitItem item = items.get(i);
            ItemStack stack = build(item);
            if (stack == null) {
                continue;
            }
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
            } else if (item.slot != null) {
                try {
                    inv.setItem(Integer.parseInt(item.slot), stack);
                } catch (NumberFormatException e) {
                    inv.addItem(stack);
                }
            } else {
                inv.addItem(stack);
            }
        }
        player.updateInventory();
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
        player.setFireTicks(0);
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.updateInventory();
    }

    private ItemStack build(KitItem item) {
        ItemStack stack;
        if (item.potionType != null) {
            try {
                Potion potion = new Potion(PotionType.valueOf(item.potionType), Math.max(1, item.potionLevel));
                potion.setSplash(item.splash);
                stack = potion.toItemStack(Math.max(1, item.amount));
            } catch (Exception e) {
                stack = new ItemStack(item.material, Math.max(1, item.amount));
            }
        } else {
            stack = new ItemStack(item.material, Math.max(1, item.amount));
        }
        if (item.enchants != null) {
            for (Object entry : item.enchants) {
                if (entry instanceof Map) {
                    Map<?, ?> map = (Map<?, ?>) entry;
                    for (Object key : map.keySet()) {
                        Enchantment enchant = enchantOf(String.valueOf(key));
                        if (enchant != null) {
                            stack.addUnsafeEnchantment(enchant, toInt(map.get(key), 1));
                        }
                    }
                } else {
                    String raw = String.valueOf(entry);
                    String[] parts = raw.split(":");
                    Enchantment enchant = enchantOf(parts[0]);
                    int level = parts.length > 1 ? toInt(parts[1], 1) : 1;
                    if (enchant != null) {
                        stack.addUnsafeEnchantment(enchant, level);
                    }
                }
            }
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && item.material == Material.GOLDEN_APPLE) {
            meta.setDisplayName(CC.color("&ePomme en or"));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private Enchantment enchantOf(String raw) {
        if (raw == null) {
            return null;
        }
        String name = raw.toUpperCase(Locale.ROOT).replace(" ", "_");
        if (name.equals("PROTECTION")) name = "PROTECTION_ENVIRONMENTAL";
        if (name.equals("SHARPNESS")) name = "DAMAGE_ALL";
        if (name.equals("UNBREAKING")) name = "DURABILITY";
        if (name.equals("EFFICIENCY")) name = "DIG_SPEED";
        if (name.equals("FORTUNE")) name = "LOOT_BONUS_BLOCKS";
        if (name.equals("POWER")) name = "ARROW_DAMAGE";
        if (name.equals("INFINITY")) name = "ARROW_INFINITE";
        return Enchantment.getByName(name);
    }

    private int toInt(Object value, int def) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return def;
        }
    }

    private static class KitItem {
        private final Material material;
        private final int amount;
        private final String slot;
        private final List<?> enchants;
        private final String potionType;
        private final int potionLevel;
        private final boolean splash;

        private KitItem(Material material, int amount, String slot, List<?> enchants,
                         String potionType, int potionLevel, boolean splash) {
            this.material = material;
            this.amount = amount;
            this.slot = slot;
            this.enchants = enchants;
            this.potionType = potionType;
            this.potionLevel = potionLevel;
            this.splash = splash;
        }
    }
}
