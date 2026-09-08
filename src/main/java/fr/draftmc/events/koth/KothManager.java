package fr.draftmc.events.koth;

import fr.draftmc.events.EventHub;
import fr.draftmc.events.EventType;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class KothManager {
    private final KothPlugin plugin;
    private final Map<String, KothZone> zones = new LinkedHashMap<String, KothZone>();
    private final Map<String, Integer> scores = new HashMap<String, Integer>();
    private final Set<String> participating = new HashSet<String>();
    private final Map<UUID, Location> pos1 = new HashMap<UUID, Location>();
    private final Map<UUID, Location> pos2 = new HashMap<UUID, Location>();
    private BukkitTask tickTask;
    private KothZone active;
    private boolean running;

    public KothManager(KothPlugin plugin) {
        this.plugin = plugin;
        loadZones();
    }

    public void reload() {
        plugin.reloadConfig();
        loadZones();
    }

    public void loadZones() {
        Map<String, KothZone> keepActive = new LinkedHashMap<String, KothZone>();
        if (running && active != null) {
            keepActive.put(active.getId(), active);
        }
        zones.clear();
        zones.putAll(keepActive);
        File file = zonesFile();
        if (!file.exists()) {
            saveZones();
            return;
        }
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("zones");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            String key = id.toLowerCase(Locale.ROOT);
            if (zones.containsKey(key)) {
                continue;
            }
            zones.put(key, KothZone.load(key, section.getConfigurationSection(id)));
        }
    }

    public void saveZones() {
        File file = zonesFile();
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        YamlConfiguration yaml = new YamlConfiguration();
        for (KothZone zone : zones.values()) {
            zone.save(yaml.createSection("zones." + zone.getId()));
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("[KOTH] Impossible de sauver zones.yml : " + e.getMessage());
        }
    }

    private File zonesFile() {
        return new File(plugin.getDataFolder(), "zones.yml");
    }

    public KothZone get(String name) {
        if (name == null) {
            return null;
        }
        return zones.get(name.toLowerCase(Locale.ROOT));
    }

    public List<KothZone> all() {
        return new ArrayList<KothZone>(zones.values());
    }

    public List<String> ids() {
        return new ArrayList<String>(zones.keySet());
    }

    public void setCorner(Player player, int which) {
        if (player == null) {
            return;
        }
        Location loc = player.getLocation().getBlock().getLocation();
        if (which == 1) {
            pos1.put(player.getUniqueId(), loc);
        } else {
            pos2.put(player.getUniqueId(), loc);
        }
    }

    public Location getCorner(Player player, int which) {
        if (player == null) {
            return null;
        }
        return which == 1 ? pos1.get(player.getUniqueId()) : pos2.get(player.getUniqueId());
    }

    private World resolveWorld(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        World world = Bukkit.getWorld(name);
        if (world != null) {
            return world;
        }
        for (World loaded : Bukkit.getWorlds()) {
            if (loaded.getName().equalsIgnoreCase(name)) {
                return loaded;
            }
        }
        return null;
    }

    public KothZone create(String name, World world, int x1, int y1, int z1, int x2, int y2, int z2, KothType type) {
        String key = name.toLowerCase(Locale.ROOT);
        KothZone zone = zones.get(key);
        if (zone == null) {
            zone = new KothZone(key);
            zones.put(key, zone);
        }
        zone.setDisplay(name);
        zone.setCuboid(world, x1, y1, z1, x2, y2, z2);
        zone.setType(type);
        zone.setPointsToWin(plugin.getConfig().getInt("points-to-win", 1200));
        saveZones();
        return zone;
    }

    public boolean delete(String name) {
        KothZone zone = get(name);
        if (zone == null) {
            return false;
        }
        if (running && active != null && active.getId().equals(zone.getId())) {
            stop(false);
        }
        zones.remove(zone.getId());
        saveZones();
        return true;
    }

    public boolean isRunning() {
        return running;
    }

    public KothZone getActive() {
        return active;
    }

    public int getScore(String factionId) {
        if (factionId == null) {
            return 0;
        }
        Integer value = scores.get(factionId);
        return value == null ? 0 : value.intValue();
    }

    public boolean isParticipating(String factionId) {
        return factionId != null && participating.contains(factionId);
    }

    public boolean start(String name) {
        if (running) {
            return false;
        }
        KothZone zone = get(name);
        if (zone == null || zone.getWorldName() == null) {
            return false;
        }
        if (resolveWorld(zone.getWorldName()) == null) {
            plugin.broadcast("no-world", zone, null, 0, 0);
            return false;
        }
        active = zone;
        running = true;
        scores.clear();
        participating.clear();
        plugin.broadcast("start", zone, null, 0, 0);
        plugin.getScoreboard().start();
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin.getHost(), new Runnable() {
            @Override
            public void run() {
                tick();
            }
        }, 20L, 20L);
        return true;
    }

    public boolean stop(boolean announceRanking) {
        if (!running) {
            return false;
        }
        running = false;
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        plugin.getScoreboard().stop();
        if (announceRanking) {
            finish(null);
        }
        active = null;
        scores.clear();
        participating.clear();
        EventHub hub = plugin.getHost().events();
        if (hub != null) {
            hub.clearActive(EventType.KOTH, null);
        }
        return true;
    }

    public void stopAll() {
        if (running) {
            stop(false);
        }
    }

    public void applyDeathPenalty(Player player) {
        if (!running || player == null) {
            return;
        }
        String factionId = plugin.getEventFactionHook().getFactionId(player);
        if (!isParticipating(factionId)) {
            return;
        }
        int current = getScore(factionId);
        if (current <= 0) {
            return;
        }
        double percent = plugin.getConfig().getDouble("death-penalty-percent", 15);
        int lost = (int) Math.round(current * (percent / 100.0));
        if (lost <= 0) {
            lost = 1;
        }
        if (lost > current) {
            lost = current;
        }
        scores.put(factionId, current - lost);
        plugin.broadcast("death-penalty", active, player, lost, (int) Math.round(percent));
    }

    private void tick() {
        if (!running || active == null) {
            return;
        }
        Map<String, Integer> inZone = countPlayersInZone();
        if (inZone.isEmpty()) {
            return;
        }
        participating.addAll(inZone.keySet());
        int rate = Math.max(0, plugin.getConfig().getInt("points-per-second-per-player", 1));
        boolean multiply = plugin.getConfig().getBoolean("multiply-by-players", false);
        Set<String> scoring = scoringFactions(inZone);
        for (String factionId : scoring) {
            int players = inZone.get(factionId).intValue();
            int gain = multiply ? rate * players : rate;
            if (gain <= 0) {
                continue;
            }
            scores.put(factionId, getScore(factionId) + gain);
        }
        String winner = leadingWinner();
        if (winner != null) {
            finish(winner);
        }
    }

    private String leadingWinner() {
        if (active == null) {
            return null;
        }
        int need = active.getPointsToWin();
        String winner = null;
        int best = -1;
        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            int value = entry.getValue().intValue();
            if (value >= need && value > best) {
                best = value;
                winner = entry.getKey();
            }
        }
        return winner;
    }

    private Set<String> scoringFactions(Map<String, Integer> inZone) {
        if (active.getType() == KothType.GIANT) {
            return inZone.keySet();
        }
        if (inZone.size() == 1) {
            return inZone.keySet();
        }
        return Collections.emptySet();
    }

    private Map<String, Integer> countPlayersInZone() {
        Map<String, Integer> counts = new HashMap<String, Integer>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isDead() || !player.isOnline()) {
                continue;
            }
            GameMode mode = player.getGameMode();
            if ("SPECTATOR".equals(mode.name())) {
                continue;
            }
            if (!active.contains(player.getLocation())
                    && !active.contains(player.getEyeLocation())) {
                continue;
            }
            String factionId = plugin.getEventFactionHook().getFactionId(player);
            if (factionId == null) {
                continue;
            }
            Integer current = counts.get(factionId);
            counts.put(factionId, current == null ? 1 : current.intValue() + 1);
        }
        return counts;
    }

    private void finish(String winnerId) {
        running = false;
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        plugin.getScoreboard().stop();
        plugin.broadcast("stop", active, null, 0, 0);
        List<Map.Entry<String, Integer>> ranking = ranking();
        plugin.broadcastRanking(ranking);
        EventHub hub = plugin.getHost().events();
        int place = 1;
        for (Map.Entry<String, Integer> entry : ranking) {
            if (place > 3) {
                break;
            }
            int reward = plugin.rankingPoints(place);
            if (hub != null && reward > 0) {
                hub.awardTopPoints(EventType.KOTH, entry.getKey(), reward);
            }
            place++;
        }
        if (winnerId == null && !ranking.isEmpty()) {
            winnerId = ranking.get(0).getKey();
        }
        List<String> rewards = plugin.getConfig().getStringList("reward-commands");
        if (winnerId != null && rewards != null) {
            String faction = plugin.getEventFactionHook().getFactionDisplayName(winnerId);
            for (String command : rewards) {
                if (command == null || command.isEmpty()) {
                    continue;
                }
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command
                        .replace("{faction}", faction)
                        .replace("{zone}", active == null ? "" : active.getDisplay())
                        .replace("{points}", String.valueOf(getScore(winnerId))));
            }
        }
        KothZone ended = active;
        active = null;
        scores.clear();
        participating.clear();
        if (hub != null) {
            hub.clearActive(EventType.KOTH, ended == null ? null : ended.getId());
        }
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

    public List<Map.Entry<String, Integer>> top(int limit) {
        List<Map.Entry<String, Integer>> list = ranking();
        if (list.size() > limit) {
            return list.subList(0, limit);
        }
        return list;
    }
}
