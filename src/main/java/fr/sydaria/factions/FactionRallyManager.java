package fr.sydaria.factions;

import fr.sydaria.Sydaria;
import fr.sydaria.util.ActionBars;
import fr.sydaria.util.CC;
import fr.sydaria.util.Locations;
import fr.sydaria.util.YamlFile;
import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Locale;
import java.util.UUID;

/**
 * Waypoint "Rally" visible uniquement par les membres de la faction :
 * particules, boussole orientee, distance en actionbar.
 */
public class FactionRallyManager implements Listener {
    private final Sydaria plugin;
    private final FactionManager factions;
    private final YamlFile file;
    private LunarWaypointHook lunar;

    public FactionRallyManager(Sydaria plugin, FactionManager factions, YamlFile file) {
        this.plugin = plugin;
        this.factions = factions;
        this.file = file;
        long interval = plugin.getConfig().getLong("factions.rally.update-ticks", 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                tick();
            }
        }, interval, interval);
    }

    public void setLunarHook(LunarWaypointHook lunar) {
        this.lunar = lunar;
    }

    public void handleCommand(Player player, String[] args) {
        if (!plugin.getConfig().getBoolean("factions.rally.enabled", true)) {
            plugin.msg(player, "&cLe rally est desactive.");
            return;
        }
        String fac = factions.factionOf(player);
        if (fac == null || fac.isEmpty()) {
            plugin.msg(player, "&cPas de faction.");
            return;
        }
        if (args.length >= 2) {
            String action = args[1].toLowerCase(Locale.ROOT);
            if ("del".equals(action) || "delete".equals(action) || "remove".equals(action) || "clear".equals(action)) {
                deleteRally(player, fac);
                return;
            }
            if ("info".equals(action)) {
                showInfo(player, fac);
                return;
            }
        }
        setRally(player, fac);
    }

    private void setRally(Player player, String fac) {
        if (!factions.hasPerm(player, FactionPerm.RALLY)) {
            plugin.msg(player, "&cTu ne peux pas definir le rally.");
            return;
        }
        file.get().set("factions." + fac + ".rally", Locations.serialize(player.getLocation()));
        file.get().set("factions." + fac + ".rally-setter", player.getName());
        file.get().set("factions." + fac + ".rally-time", System.currentTimeMillis());
        file.save();

        Location rally = player.getLocation().clone();
        String msg = plugin.getConfig().getString("factions.rally.messages.set",
                "&aWaypoint &eRally &adefini par &e%player%&a.");
        msg = msg.replace("%player%", player.getName())
                .replace("%faction%", factions.displayName(fac));
        notifyFaction(fac, msg);

        if (rally.getWorld() != null) {
            msg = plugin.getConfig().getString("factions.rally.messages.coords",
                    "&7Position: &f%x%&7, &f%y%&7, &f%z%");
            msg = msg.replace("%x%", String.valueOf(rally.getBlockX()))
                    .replace("%y%", String.valueOf(rally.getBlockY()))
                    .replace("%z%", String.valueOf(rally.getBlockZ()))
                    .replace("%world%", rally.getWorld().getName());
            notifyFaction(fac, msg);
        }
        applyCompass(player, fac);
        syncLunar(fac, rally);
        if (lunar != null && lunar.isEnabled()) {
            plugin.msg(player, "&7Waypoint Lunar envoye aux membres Lunar Client.");
        }
    }

    private void deleteRally(Player player, String fac) {
        if (!factions.hasPerm(player, FactionPerm.RALLY)) {
            plugin.msg(player, "&cTu ne peux pas supprimer le rally.");
            return;
        }
        if (!hasRally(fac)) {
            plugin.msg(player, "&cAucun rally actif.");
            return;
        }
        clearRally(fac);
        String msg = plugin.getConfig().getString("factions.rally.messages.removed",
                "&eWaypoint Rally supprime par &f%player%&e.");
        notifyFaction(fac, msg.replace("%player%", player.getName()));
    }

    private void syncLunar(String fac) {
        syncLunar(fac, getRally(fac));
    }

    private void syncLunar(String fac, Location rally) {
        if (lunar == null || !lunar.isEnabled()) {
            return;
        }
        lunar.syncFaction(fac, rally);
    }

    private void showInfo(Player player, String fac) {
        Location rally = getRally(fac);
        if (rally == null) {
            plugin.msg(player, "&cAucun rally actif. &7Utilise &e/f rally &7pour en placer un.");
            return;
        }
        plugin.msg(player, "&6Rally &7» &f" + rally.getBlockX() + "&7, &f" + rally.getBlockY()
                + "&7, &f" + rally.getBlockZ() + " &7(&f" + rally.getWorld().getName() + "&7)");
        plugin.msg(player, "&7Distance: &e" + (int) player.getLocation().distance(rally) + "m");
        plugin.msg(player, "&7Supprimer: &e/f rally del");
    }

    public void clearRally(String fac) {
        file.get().set("factions." + fac + ".rally", null);
        file.get().set("factions." + fac + ".rally-setter", null);
        file.get().set("factions." + fac + ".rally-time", null);
        file.save();
        resetCompassForFaction(fac);
        if (lunar != null) {
            lunar.clearFactionRally(fac);
        }
        syncLunar(fac);
    }

    public boolean hasRally(String fac) {
        return getRally(fac) != null;
    }

    public Location getRally(String fac) {
        if (fac == null || fac.isEmpty()) {
            return null;
        }
        if (isExpired(fac)) {
            clearRally(fac);
            return null;
        }
        return Locations.deserialize(file.get().getString("factions." + fac + ".rally"));
    }

    private boolean isExpired(String fac) {
        int expireMinutes = plugin.getConfig().getInt("factions.rally.expire-minutes", 30);
        if (expireMinutes <= 0) {
            return false;
        }
        long setAt = file.get().getLong("factions." + fac + ".rally-time", 0L);
        if (setAt <= 0) {
            return false;
        }
        return System.currentTimeMillis() - setAt > expireMinutes * 60L * 1000L;
    }

    private void tick() {
        if (!plugin.getConfig().getBoolean("factions.rally.enabled", true)) {
            return;
        }
        boolean particles = plugin.getConfig().getBoolean("factions.rally.particles", false);
        if (particles && lunar != null && lunar.isEnabled()) {
            particles = plugin.getConfig().getBoolean("factions.rally.particles-with-lunar", false);
        }
        boolean compass = plugin.getConfig().getBoolean("factions.rally.compass", true);
        boolean actionbar = plugin.getConfig().getBoolean("factions.rally.actionbar-distance", true);

        for (Player player : Bukkit.getOnlinePlayers()) {
            String fac = factions.factionOf(player);
            if (fac.isEmpty()) {
                continue;
            }
            Location rally = getRally(fac);
            if (rally == null) {
                continue;
            }
            if (compass) {
                applyCompass(player, fac);
            }
            if (actionbar && player.getWorld().equals(rally.getWorld())) {
                int distance = (int) player.getLocation().distance(rally);
                String direction = directionTo(player.getLocation(), rally);
                String msg = plugin.getConfig().getString("factions.rally.messages.actionbar",
                        "&6Rally &7» &e%distance%m &7(%direction%)");
                ActionBars.send(player, CC.color(msg
                        .replace("%distance%", String.valueOf(distance))
                        .replace("%direction%", direction)));
            }
            if (particles && player.getWorld().equals(rally.getWorld())) {
                showParticles(player, rally);
            }
        }
    }

    private void showParticles(Player player, Location rally) {
        int height = plugin.getConfig().getInt("factions.rally.particle-height", 6);
        Effect effect = Effect.ENDER_SIGNAL;
        try {
            effect = Effect.valueOf(plugin.getConfig().getString("factions.rally.particle-effect", "ENDER_SIGNAL"));
        } catch (Throwable ignored) {
        }
        Location base = rally.clone().add(0.5, 0.2, 0.5);
        for (int y = 0; y < height; y++) {
            Location point = base.clone().add(0, y * 0.8, 0);
            if (player.getLocation().distanceSquared(point) > 128 * 128) {
                continue;
            }
            try {
                player.playEffect(point, effect, 0);
            } catch (Throwable ignored) {
            }
        }
    }

    private void applyCompass(Player player, String fac) {
        if (!plugin.getConfig().getBoolean("factions.rally.compass", true)) {
            return;
        }
        Location rally = getRally(fac);
        if (rally == null || rally.getWorld() == null) {
            return;
        }
        if (!player.getWorld().equals(rally.getWorld())) {
            return;
        }
        player.setCompassTarget(rally);
    }

    private void resetCompassForFaction(String fac) {
        for (String raw : factions.members(fac)) {
            Player online = Bukkit.getPlayer(UUID.fromString(raw));
            if (online != null && online.getWorld() != null) {
                online.setCompassTarget(online.getWorld().getSpawnLocation());
            }
        }
    }

    private String directionTo(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double angle = Math.toDegrees(Math.atan2(-dx, dz));
        if (angle < 0) {
            angle += 360;
        }
        if (angle >= 337.5 || angle < 22.5) return "Sud";
        if (angle < 67.5) return "Sud-Ouest";
        if (angle < 112.5) return "Ouest";
        if (angle < 157.5) return "Nord-Ouest";
        if (angle < 202.5) return "Nord";
        if (angle < 247.5) return "Nord-Est";
        if (angle < 292.5) return "Est";
        return "Sud-Est";
    }

    private void notifyFaction(String fac, String message) {
        for (String raw : factions.members(fac)) {
            Player online = Bukkit.getPlayer(UUID.fromString(raw));
            if (online != null) {
                plugin.msg(online, message);
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                String fac = factions.factionOf(event.getPlayer());
                if (!fac.isEmpty()) {
                    applyCompass(event.getPlayer(), fac);
                }
            }
        }, 10L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        String fac = factions.factionOf(event.getPlayer());
        if (!fac.isEmpty()) {
            applyCompass(event.getPlayer(), fac);
        }
    }
}
