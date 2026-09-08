package fr.draftmc.events.koth;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

public class KothZone {
    private final String id;
    private String display;
    private String worldName;
    private int minX;
    private int minY;
    private int minZ;
    private int maxX;
    private int maxY;
    private int maxZ;
    private KothType type = KothType.GIANT;
    private int pointsToWin = 1200;

    public KothZone(String id) {
        this.id = id;
        this.display = id;
    }

    public boolean contains(Location location) {
        if (location == null || location.getWorld() == null || worldName == null) {
            return false;
        }
        if (!worldName.equalsIgnoreCase(location.getWorld().getName())) {
            return false;
        }
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        int top = maxY;
        if (top <= minY) {
            top = minY + 4;
        } else {
            top = maxY + 3;
        }
        return x >= minX && x <= maxX
                && z >= minZ && z <= maxZ
                && y >= minY - 1 && y <= top;
    }

    public void setCuboid(World world, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.worldName = world == null ? worldName : world.getName();
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
        if (this.maxY <= this.minY) {
            this.maxY = this.minY + 4;
        }
    }

    public void save(ConfigurationSection section) {
        section.set("display", display);
        section.set("world", worldName);
        section.set("min-x", minX);
        section.set("min-y", minY);
        section.set("min-z", minZ);
        section.set("max-x", maxX);
        section.set("max-y", maxY);
        section.set("max-z", maxZ);
        section.set("type", type.name());
        section.set("points-to-win", pointsToWin);
    }

    public static KothZone load(String id, ConfigurationSection section) {
        KothZone zone = new KothZone(id);
        if (section == null) {
            return zone;
        }
        zone.display = section.getString("display", id);
        zone.worldName = section.getString("world");
        zone.minX = section.getInt("min-x");
        zone.minY = section.getInt("min-y");
        zone.minZ = section.getInt("min-z");
        zone.maxX = section.getInt("max-x");
        zone.maxY = section.getInt("max-y");
        zone.maxZ = section.getInt("max-z");
        int nx1 = Math.min(zone.minX, zone.maxX);
        int ny1 = Math.min(zone.minY, zone.maxY);
        int nz1 = Math.min(zone.minZ, zone.maxZ);
        int nx2 = Math.max(zone.minX, zone.maxX);
        int ny2 = Math.max(zone.minY, zone.maxY);
        int nz2 = Math.max(zone.minZ, zone.maxZ);
        zone.minX = nx1;
        zone.minY = ny1;
        zone.minZ = nz1;
        zone.maxX = nx2;
        zone.maxY = ny2;
        zone.maxZ = nz2;
        zone.type = KothType.from(section.getString("type", "GIANT"));
        zone.pointsToWin = section.getInt("points-to-win", 1200);
        return zone;
    }

    public String getId() {
        return id;
    }

    public String getDisplay() {
        return display == null || display.isEmpty() ? id : display;
    }

    public void setDisplay(String display) {
        this.display = display;
    }

    public String getWorldName() {
        return worldName;
    }

    public int getMinX() {
        return minX;
    }

    public int getMinY() {
        return minY;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMaxY() {
        return maxY;
    }

    public int getMaxZ() {
        return maxZ;
    }

    public KothType getType() {
        return type;
    }

    public void setType(KothType type) {
        this.type = type == null ? KothType.GIANT : type;
    }

    public int getPointsToWin() {
        return pointsToWin;
    }

    public void setPointsToWin(int pointsToWin) {
        this.pointsToWin = Math.max(1, pointsToWin);
    }
}
