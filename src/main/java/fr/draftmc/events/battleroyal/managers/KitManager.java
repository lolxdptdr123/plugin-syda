package fr.draftmc.events.battleroyal.managers;

import fr.draftmc.events.battleroyal.BattleRoyal;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.Potion;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Distribue un stuff identique et predefini a tous les joueurs au debut
 * de la partie (aucun loot aleatoire) : chaque equipe demarre exactement
 * a egalite. Configurable dans config.yml, section "kit.items".
 *
 * Les potions utilisent la classe Bukkit "Potion" (API officielle 1.8 pour
 * construire des ItemStack de potion avec le bon type/niveau/splash) plutot
 * que de deviner une valeur de donnees brute, pour rester fiable.
 */
public class KitManager {

    private final BattleRoyal plugin;
    private final List<KitItem> items = new ArrayList<>();

    public KitManager(BattleRoyal plugin) {
        this.plugin = plugin;
        loadKit();
    }

    @SuppressWarnings("unchecked")
    public void loadKit() {
        items.clear();
        List<?> rawItems = plugin.getConfig().getList("kit.items");
        if (rawItems == null) return;

        for (Object obj : rawItems) {
            if (!(obj instanceof Map)) continue;
            Map<String, Object> map = (Map<String, Object>) obj;
            try {
                Material material = Material.valueOf(String.valueOf(map.get("material")));
                int amount = toInt(map.get("amount"), 1);
                String slot = map.get("slot") != null ? String.valueOf(map.get("slot")).toLowerCase() : null;
                List<?> enchantments = map.get("enchantments") instanceof List ? (List<?>) map.get("enchantments") : null;

                String potionType = map.get("potion-type") != null ? String.valueOf(map.get("potion-type")).toUpperCase() : null;
                int potionLevel = toInt(map.get("potion-level"), 1);
                boolean splash = map.get("splash") instanceof Boolean ? (Boolean) map.get("splash") : false;

                items.add(new KitItem(material, amount, slot, enchantments, potionType, potionLevel, splash));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Materiau de kit invalide ignore : " + map.get("material"));
            }
        }
    }

    /** Vide l'inventaire du joueur (y compris l'armure) puis lui donne le kit fixe. */
    public void giveKit(Player player) {
        PlayerInventory inv = player.getInventory();
        inv.clear();
        inv.setHelmet(null);
        inv.setChestplate(null);
        inv.setLeggings(null);
        inv.setBoots(null);

        for (KitItem item : items) {
            List<ItemStack> stacks = buildStacks(item);
            if (stacks.isEmpty()) continue;

            if (item.slot == null) {
                // Ajoute chaque stack separement (important pour les potions,
                // qui ne se stackent jamais en vanilla : 1 par emplacement).
                for (ItemStack stack : stacks) {
                    inv.addItem(stack);
                }
                continue;
            }

            ItemStack stack = stacks.get(0);
            switch (item.slot) {
                case "helmet":
                    inv.setHelmet(stack);
                    break;
                case "chestplate":
                    inv.setChestplate(stack);
                    break;
                case "leggings":
                    inv.setLeggings(stack);
                    break;
                case "boots":
                    inv.setBoots(stack);
                    break;
                case "hand":
                    inv.setItemInHand(stack);
                    break;
                default:
                    try {
                        int slotIndex = Integer.parseInt(item.slot);
                        inv.setItem(slotIndex, stack);
                    } catch (NumberFormatException e) {
                        inv.addItem(stack);
                    }
            }
        }
    }

    /**
     * Construit la ou les ItemStack correspondant a un objet du kit.
     * Cas particulier : les potions ne sont JAMAIS stackables en vanilla,
     * donc on retourne "amount" stacks distincts d'1 potion chacun plutot
     * qu'un seul stack de quantite "amount" (qui s'afficherait, a tort,
     * comme un unique tas de 20 potions dans un seul emplacement).
     */
    @SuppressWarnings("unchecked")
    private List<ItemStack> buildStacks(KitItem item) {
        if (item.material == Material.POTION && item.potionType != null) {
            List<ItemStack> stacks = new ArrayList<>();
            try {
                PotionType type = PotionType.valueOf(item.potionType);
                for (int i = 0; i < item.amount; i++) {
                    Potion potion = new Potion(type, item.potionLevel);
                    potion.setSplash(item.splash);
                    stacks.add(potion.toItemStack(1));
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Type de potion invalide ignore : " + item.potionType);
            }
            return stacks;
        }

        ItemStack stack = new ItemStack(item.material, item.amount);
        if (item.enchantments != null) {
            for (Object encObj : item.enchantments) {
                if (!(encObj instanceof Map)) continue;
                Map<String, Object> enc = (Map<String, Object>) encObj;
                Enchantment enchantment = Enchantment.getByName(String.valueOf(enc.get("type")).toUpperCase());
                int level = toInt(enc.get("level"), 1);
                if (enchantment != null) {
                    stack.addUnsafeEnchantment(enchantment, level);
                }
            }
        }
        return java.util.Collections.singletonList(stack);
    }

    private int toInt(Object o, int def) {
        return o == null ? def : Integer.parseInt(String.valueOf(o));
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
