package fr.sydaria.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class Locations {
    private Locations() {}

    public static String serialize(Location location) {
        if (location == null || location.getWorld() == null) {
            return "";
        }
        return location.getWorld().getName() + "," + location.getX() + "," + location.getY() + "," + location.getZ()
                + "," + location.getYaw() + "," + location.getPitch();
    }

    public static Location deserialize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String[] p = raw.split(",");
        if (p.length < 4) {
            return null;
        }
        org.bukkit.World world = Bukkit.getWorld(p[0]);
        if (world == null) {
            return null;
        }
        Location loc = new Location(world, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]));
        if (p.length >= 6) {
            loc.setYaw(Float.parseFloat(p[4]));
            loc.setPitch(Float.parseFloat(p[5]));
        }
        return loc;
    }

    public static boolean isSafe(Location location) {
        Block feet = location.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        Block below = feet.getRelative(0, -1, 0);
        return feet.getType() == Material.AIR
                && head.getType() == Material.AIR
                && below.getType().isSolid()
                && below.getType() != Material.LAVA
                && below.getType() != Material.STATIONARY_LAVA
                && below.getType() != Material.CACTUS;
    }

    public static boolean isTool(ItemStack item) {
        if (item == null) {
            return false;
        }
        String n = item.getType().name();
        return n.endsWith("_SWORD") || n.endsWith("_AXE") || n.endsWith("_PICKAXE")
                || n.endsWith("_SPADE") || n.endsWith("_HOE") || n.endsWith("_HELMET")
                || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS")
                || n.equals("BOW") || n.equals("FISHING_ROD") || n.equals("FLINT_AND_STEEL")
                || n.equals("SHEARS");
    }

    public static Player find(String name) {
        return Bukkit.getPlayer(name);
    }
}
