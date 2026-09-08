package fr.draftmc.events.totem;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import fr.draftmc.util.NmsTitles;
import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TotemManager {
    private final TotemPlugin plugin;
    private final Map<String, Totem> totems = new LinkedHashMap<String, Totem>();
    private final Map<String, Integer> scores = new LinkedHashMap<String, Integer>();
    private BukkitTask countdownTask;
    private BukkitTask durationTask;
    private int countdownSecondsLeft;
    private String countdownTotem;
    private boolean giantMode;
    private long endAtMillis;

    public TotemManager(TotemPlugin plugin) {
        this.plugin = plugin;
        loadAll();
    }

    public void loadAll() {
        List<Totem> running = new ArrayList<Totem>();
        for (Totem existing : totems.values()) {
            if (existing.getStatus() == TotemStatus.STARTED
                    || existing.getStatus() == TotemStatus.STARTING) {
                running.add(existing);
            }
        }
        Map<String, Totem> keep = new LinkedHashMap<String, Totem>();
        for (Totem totem : running) {
            keep.put(totem.getName(), totem);
        }
        totems.clear();
        totems.putAll(keep);

        ConfigurationSection section = plugin.getConfig().getConfigurationSection("maps");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            String key = id.toLowerCase(Locale.ROOT);
            if (totems.containsKey(key)) {
                continue;
            }
            totems.put(key, readTotem(key, section.getConfigurationSection(id)));
        }
    }

    public void reload() {
        plugin.reloadConfig();
        loadAll();
    }

    public Totem get(String name) {
        if (name == null) {
            return null;
        }
        return totems.get(name.toLowerCase(Locale.ROOT));
    }

    public Totem getOrCreate(String name) {
        Totem existing = get(name);
        if (existing != null) {
            return existing;
        }
        String key = name.toLowerCase(Locale.ROOT);
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("maps." + key);
        Totem totem = section != null ? readTotem(key, section) : new Totem(key);
        if (section == null) {
            applyDefaults(totem);
        }
        totems.put(key, totem);
        return totem;
    }

    public Collection<Totem> all() {
        return totems.values();
    }

    public Totem findByBlock(Block block) {
        if (block == null) {
            return null;
        }
        for (Totem totem : totems.values()) {
            if (totem.contains(block)) {
                return totem;
            }
        }
        return null;
    }

    public boolean isBusy() {
        for (Totem totem : totems.values()) {
            if (totem.getStatus() == TotemStatus.STARTED
                    || totem.getStatus() == TotemStatus.STARTING) {
                return true;
            }
        }
        return false;
    }

    public Totem getActive() {
        for (Totem totem : totems.values()) {
            if (totem.getStatus() == TotemStatus.STARTED
                    || totem.getStatus() == TotemStatus.STARTING) {
                return totem;
            }
        }
        return null;
    }

    public int getCountdownSecondsLeft() {
        Totem active = getActive();
        if (active == null || active.getStatus() != TotemStatus.STARTING) {
            return -1;
        }
        return countdownSecondsLeft;
    }

    public boolean isStarted(String name) {
        Totem totem = get(name);
        return totem != null && totem.getStatus() == TotemStatus.STARTED;
    }

    public void applyMap(String mapId, String world, Double x, Double y, Double z) {
        Totem totem = getOrCreate(mapId);
        if (totem.getStatus() == TotemStatus.STARTED || totem.getStatus() == TotemStatus.STARTING) {
            return;
        }
        Location loc = totem.getLocation();
        World resolved = world != null ? Bukkit.getWorld(world) : (loc != null ? loc.getWorld() : null);
        double px = x != null ? x.doubleValue() : (loc != null ? loc.getX() : 0);
        double py = y != null ? y.doubleValue() : (loc != null ? loc.getY() : 64);
        double pz = z != null ? z.doubleValue() : (loc != null ? loc.getZ() : 0);
        if (resolved != null) {
            totem.setLocation(new Location(resolved, px, py, pz));
        }
    }

    public boolean spawn(String name) {
        return startCountdown(name, false);
    }

    public boolean startCountdown(String name) {
        return startCountdown(name, false);
    }

    public boolean startCountdown(String name, boolean giant) {
        Totem totem = get(name);
        if (totem == null) {
            totem = getOrCreate(name);
        }
        if (totem.getStatus() == TotemStatus.STARTED || totem.getStatus() == TotemStatus.STARTING) {
            return false;
        }
        if (isBusy()) {
            return false;
        }
        if (totem.getLocation() == null || totem.getLocation().getWorld() == null) {
            plugin.getLogger().warning("[Totem] Map " + name + " sans position (utilise /totem set " + name + ").");
            return false;
        }
        cancelCountdown();
        cancelDuration();
        giantMode = giant;
        scores.clear();
        endAtMillis = 0;
        totem.setGiant(giant);
        totem.setStatusStarting();
        countdownTotem = totem.getName();
        countdownSecondsLeft = plugin.getConfig().getInt("start-countdown-seconds", 60);
        plugin.broadcast("countdown-start", totem, null);
        plugin.broadcast("haste-disabled", totem, null);
        plugin.onTotemStarting();
        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin.getHost(), new Runnable() {
            @Override
            public void run() {
                tickCountdown();
            }
        }, 20L, 20L);
        return true;
    }

    public boolean isGiantMode() {
        return giantMode;
    }

    private void tickCountdown() {
        countdownSecondsLeft--;
        if (countdownSecondsLeft <= 0) {
            cancelCountdown();
            launchNow();
            return;
        }
        boolean announce = countdownSecondsLeft % 10 == 0 || countdownSecondsLeft <= 5;
        if (!announce) {
            return;
        }
        Totem totem = countdownTotem == null ? null : get(countdownTotem);
        plugin.broadcast("countdown-tick", totem, null);
        for (Player player : Bukkit.getOnlinePlayers()) {
            NmsTitles.send(player, ChatColor.translateAlternateColorCodes('&', "&e&l" + countdownSecondsLeft), "", 5, 25, 5);
            player.playSound(player.getLocation(), Sound.NOTE_PLING, 1f, 1f);
        }
    }

    private void launchNow() {
        Totem totem = countdownTotem == null ? null : get(countdownTotem);
        countdownTotem = null;
        if (totem == null || totem.getLocation() == null || totem.getLocation().getWorld() == null) {
            return;
        }
        totem.setGiant(giantMode);
        totem.spawn();
        plugin.broadcast("spawn", totem, null);
        if (giantMode) {
            startDurationTimer();
        }
    }

    private void startDurationTimer() {
        cancelDuration();
        int minutes = Math.max(1, plugin.getConfig().getInt("giant.duration-minutes", 30));
        endAtMillis = System.currentTimeMillis() + minutes * 60L * 1000L;
        durationTask = Bukkit.getScheduler().runTaskLater(plugin.getHost(), new Runnable() {
            @Override
            public void run() {
                durationTask = null;
                finishGiant();
            }
        }, minutes * 60L * 20L);
    }

    private void cancelCountdown() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
    }

    private void cancelDuration() {
        if (durationTask != null) {
            durationTask.cancel();
            durationTask = null;
        }
    }

    public int getDurationSecondsLeft() {
        if (!giantMode || endAtMillis <= 0L) {
            return -1;
        }
        return (int) Math.max(0L, (endAtMillis - System.currentTimeMillis()) / 1000L);
    }

    public void addScore(String factionId, int amount) {
        if (factionId == null || factionId.isEmpty() || amount == 0) {
            return;
        }
        Integer current = scores.get(factionId);
        scores.put(factionId, (current == null ? 0 : current.intValue()) + amount);
    }

    public int getScore(String factionId) {
        if (factionId == null) {
            return 0;
        }
        Integer value = scores.get(factionId);
        return value == null ? 0 : value.intValue();
    }

    public List<Map.Entry<String, Integer>> ranking() {
        List<Map.Entry<String, Integer>> list = new ArrayList<Map.Entry<String, Integer>>(scores.entrySet());
        Collections.sort(list, new Comparator<Map.Entry<String, Integer>>() {
            @Override
            public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                return b.getValue().compareTo(a.getValue());
            }
        });
        return list;
    }

    public List<Map.Entry<String, Integer>> top(int n) {
        List<Map.Entry<String, Integer>> all = ranking();
        if (all.size() <= n) {
            return all;
        }
        return new ArrayList<Map.Entry<String, Integer>>(all.subList(0, n));
    }

    public void finishGiant() {
        cancelDuration();
        cancelCountdown();
        countdownTotem = null;
        Totem totem = getActive();
        List<Map.Entry<String, Integer>> ranking = ranking();
        if (totem != null) {
            if (totem.getStatus() == TotemStatus.STARTED) {
                totem.finishWithBedrock();
            } else {
                totem.setStatusWaiting();
            }
            totem.setGiant(false);
        }
        plugin.completeGiant(totem, ranking);
        scores.clear();
        giantMode = false;
        endAtMillis = 0L;
    }

    public boolean stop(String name) {
        Totem totem = get(name);
        if (totem == null) {
            return false;
        }
        if (totem.getStatus() == TotemStatus.STARTING) {
            cancelCountdown();
            cancelDuration();
            countdownTotem = null;
            scores.clear();
            endAtMillis = 0L;
            totem.setStatusWaiting();
            plugin.onTotemEnded();
            plugin.broadcast("stop", totem, null);
            giantMode = false;
            return true;
        }
        if (totem.getStatus() != TotemStatus.STARTED) {
            return false;
        }
        if (giantMode) {
            finishGiant();
            return true;
        }
        cancelCountdown();
        totem.stop();
        plugin.onTotemEnded();
        plugin.broadcast("stop", totem, null);
        return true;
    }

    public boolean reset(String name) {
        Totem totem = get(name);
        if (totem == null || totem.getLocation() == null) {
            return false;
        }
        totem.reset();
        plugin.broadcast("reset", totem, null);
        return true;
    }

    public void setAtPlayer(Player player, String name) {
        Totem totem = getOrCreate(name);
        if (totem.getStatus() == TotemStatus.STARTED || totem.getStatus() == TotemStatus.STARTING) {
            stop(totem.getName());
        }
        totem.setLocation(player.getLocation().getBlock().getLocation());
        totem.generate();
        saveTotem(totem);
        totem.setStatusWaiting();
        plugin.send(player, "set", totem, player);
    }

    public boolean unset(String name) {
        Totem totem = get(name);
        if (totem == null) {
            return false;
        }
        if (totem.getStatus() == TotemStatus.STARTED || totem.getStatus() == TotemStatus.STARTING) {
            stop(totem.getName());
        }
        totems.remove(totem.getName());
        plugin.getConfig().set("maps." + totem.getName(), null);
        plugin.saveConfig();
        return true;
    }

    public void saveTotem(Totem totem) {
        String path = "maps." + totem.getName();
        Location loc = totem.getLocation();
        plugin.getConfig().set(path + ".display", plugin.getConfig().getString(path + ".display", totem.getName()));
        if (loc != null && loc.getWorld() != null) {
            plugin.getConfig().set(path + ".world", loc.getWorld().getName());
            plugin.getConfig().set(path + ".x", loc.getBlockX());
            plugin.getConfig().set(path + ".y", loc.getBlockY());
            plugin.getConfig().set(path + ".z", loc.getBlockZ());
        }
        plugin.getConfig().set(path + ".size", totem.getSize());
        plugin.getConfig().set(path + ".material", totem.getBlockMaterial().name());
        plugin.getConfig().set(path + ".item-interact", totem.getItemInteract().name());
        plugin.saveConfig();
    }

    public void onFactionDisband(String factionId) {
        if (factionId == null) {
            return;
        }
        for (Totem totem : totems.values()) {
            if (totem.getStatus() == TotemStatus.STARTED && factionId.equals(totem.getCapturingFactionId())) {
                totem.reset();
                plugin.broadcast("break-cancel", totem, null);
            }
        }
    }

    public void stopAll() {
        cancelCountdown();
        cancelDuration();
        countdownTotem = null;
        scores.clear();
        giantMode = false;
        endAtMillis = 0L;
        for (Totem totem : totems.values()) {
            if (totem.getStatus() == TotemStatus.STARTED) {
                totem.stop();
            } else if (totem.getStatus() == TotemStatus.STARTING) {
                totem.setStatusWaiting();
            }
            totem.setGiant(false);
        }
        plugin.onTotemEnded();
    }

    private Totem readTotem(String name, ConfigurationSection section) {
        Totem totem = new Totem(name);
        applyDefaults(totem);
        if (section == null) {
            return totem;
        }
        totem.setSize(section.getInt("size", totem.getSize()));
        Material block = material(section.getString("material"), totem.getBlockMaterial());
        Material item = material(section.getString("item-interact"), totem.getItemInteract());
        totem.setBlockMaterial(block);
        totem.setItemInteract(item);
        String worldName = section.getString("world");
        World world = worldName == null ? null : Bukkit.getWorld(worldName);
        if (world != null && section.contains("x") && section.contains("y") && section.contains("z")) {
            totem.setLocation(new Location(world, section.getDouble("x"), section.getDouble("y"), section.getDouble("z")));
        }
        return totem;
    }

    private void applyDefaults(Totem totem) {
        totem.setSize(plugin.getConfig().getInt("defaults.size", 5));
        totem.setBlockMaterial(material(plugin.getConfig().getString("defaults.material"), Material.QUARTZ_BLOCK));
        totem.setItemInteract(material(plugin.getConfig().getString("defaults.item-interact"), Material.DIAMOND_SWORD));
    }

    private static Material material(String raw, Material fallback) {
        if (raw == null || raw.isEmpty()) {
            return fallback;
        }
        Material mat = Material.getMaterial(raw.toUpperCase(Locale.ROOT));
        return mat == null ? fallback : mat;
    }
}
