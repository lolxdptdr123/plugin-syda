package fr.draftmc.gui;

import fr.draftmc.util.CC;
import fr.draftmc.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Arrays;
import java.util.List;

public final class Menus {
    private Menus() {}

    public static ItemStack pane() {
        return new ItemBuilder(Material.STAINED_GLASS_PANE, 1, (short) 15).name("&r").build();
    }

    public static void fill(Inventory inv) {
        ItemStack pane = pane();
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, pane);
        }
    }

    public static ItemStack close() {
        return new ItemBuilder(Material.BARRIER).name("&cFermer").lore("&7Fermer le menu.").build();
    }

    public static ItemStack back() {
        return new ItemBuilder(Material.ARROW).name("&eRetour").lore("&7Menu précédent.").build();
    }

    public static ItemStack skull(String owner, String name, String... lore) {
        return skull(owner, name, Arrays.asList(lore));
    }

    public static ItemStack skull(String owner, String name, List<String> lore) {
        ItemStack item = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (owner != null && !owner.isEmpty()) {
            meta.setOwner(owner);
        }
        meta.setDisplayName(CC.color(name));
        meta.setLore(CC.color(lore));
        item.setItemMeta(meta);
        return item;
    }

    public static String prettyItem(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return "air";
        }
        String name = stack.getType().name().toLowerCase().replace('_', ' ');
        if (stack.hasItemMeta() && stack.getItemMeta().hasDisplayName()) {
            name = CC.strip(stack.getItemMeta().getDisplayName());
        }
        return stack.getAmount() + "x " + name;
    }
}
