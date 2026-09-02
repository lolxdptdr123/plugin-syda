package fr.sydaria.util;

import org.bukkit.Bukkit;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.logging.Level;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class Items {
    private Items() {}

    public static ItemStack named(Material mat, String name, String... lore) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Chat.color(name));
            if (lore.length > 0) {
                List<String> lines = new ArrayList<String>();
                for (String l : lore) {
                    lines.add(Chat.color(l));
                }
                meta.setLore(lines);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static boolean namedIs(ItemStack stack, String colorName) {
        if (stack == null || !stack.hasItemMeta() || !stack.getItemMeta().hasDisplayName()) {
            return false;
        }
        return stack.getItemMeta().getDisplayName().equals(Chat.color(colorName));
    }

    public static String toBase64(ItemStack item) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes);
            out.writeObject(item);
            out.close();
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Sérialisation d'ItemStack impossible", e);
        }
    }

    public static ItemStack fromBase64(String data) {
        try {
            ByteArrayInputStream bytes = new ByteArrayInputStream(Base64.getDecoder().decode(data));
            BukkitObjectInputStream in = new BukkitObjectInputStream(bytes);
            ItemStack item = (ItemStack) in.readObject();
            in.close();
            return item;
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.WARNING, "Item keep-on-death corrompu, ignoré.", e);
            return null; // on ignore l'item plutôt que de crasher le respawn de tout le monde
        }
    }

}
