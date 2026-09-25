package fr.draftmc.events;

import fr.draftmc.Draftmc;
import fr.draftmc.events.battleroyal.BattleRoyal;
import fr.draftmc.events.battleroyal.model.GameState;
import fr.draftmc.events.conquest.ConquestPlugin;
import fr.draftmc.events.conquest.model.ConquestState;
import fr.draftmc.events.domination.DominationPlugin;
import fr.draftmc.events.domination.model.DominationState;
import fr.draftmc.events.masterkill.MasterKillPlugin;
import fr.draftmc.events.masterkill.model.MasterKillState;
import fr.draftmc.events.koth.KothPlugin;
import fr.draftmc.events.koth.KothZone;
import fr.draftmc.events.largage.LargagePlugin;
import fr.draftmc.events.teamfight.TeamFightPlugin;
import fr.draftmc.events.teamfight.TeamFightState;
import fr.draftmc.events.totem.Totem;
import fr.draftmc.events.totem.TotemPlugin;
import fr.draftmc.events.totem.TotemStatus;
import fr.draftmc.util.CC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class EventHub {
    public enum StartResult {
        STARTED,
        REGISTRATION_OPENED,
        LAUNCHED,
        NEED_TEAMS,
        ALREADY_RUNNING,
        UNKNOWN_MAP,
        FAILED
    }

    public enum StopResult {
        STOPPED,
        WRONG_MAP,
        NOT_RUNNING,
        UNKNOWN_MAP
    }

    private final Draftmc plugin;
    private final ConquestPlugin conquest;
    private final DominationPlugin domination;
    private final BattleRoyal battleroyal;
    private final MasterKillPlugin masterkill;
    private final TotemPlugin totem;
    private final KothPlugin koth;
    private final TeamFightPlugin teamfight;
    private final LargagePlugin largage;
    private final EventMonthStats monthStats;
    private FileConfiguration catalog;
    private final Map<EventType, String> activeMaps = new EnumMap<EventType, String>(EventType.class);
    private EventScheduler scheduler;

    public EventHub(Draftmc plugin) {
        this.plugin = plugin;
        loadCatalog();
        this.conquest = new ConquestPlugin(plugin);
        this.domination = new DominationPlugin(plugin);
        this.battleroyal = new BattleRoyal(plugin);
        this.masterkill = new MasterKillPlugin(plugin);
        this.totem = new TotemPlugin(plugin);
        this.koth = new KothPlugin(plugin);
        this.teamfight = new TeamFightPlugin(plugin);
        this.largage = new LargagePlugin(plugin);
        this.scheduler = new EventScheduler(plugin);
        this.monthStats = new EventMonthStats(plugin);
    }

    public FileConfiguration catalog() {
        return catalog;
    }

    private void loadCatalog() {
        File file = new File(plugin.getDataFolder(), "events.yml");
        if (!file.exists() && plugin.getResource("events.yml") != null) {
            plugin.saveResource("events.yml", false);
        }
        this.catalog = YamlConfiguration.loadConfiguration(file);
        fillMissingSchedule(file);
    }

    private void fillMissingSchedule(File file) {
        java.io.InputStream stream = plugin.getResource("events.yml");
        if (stream == null) {
            return;
        }
        try {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8));
            boolean changed = false;
            if (!catalog.isConfigurationSection("schedule") && defaults.isConfigurationSection("schedule")) {
                catalog.set("schedule", defaults.get("schedule"));
                changed = true;
            } else {
                String[] days = {"monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"};
                for (int i = 0; i < days.length; i++) {
                    String path = "schedule." + days[i];
                    if (scheduleSlots(days[i]).isEmpty() && defaults.contains(path)) {
                        catalog.set(path, defaults.get(path));
                        changed = true;
                    }
                }
            }
            if (changed) {
                catalog.save(file);
            }
        } catch (Exception ignored) {
        } finally {
            try {
                stream.close();
            } catch (Exception ignored) {
            }
        }
    }

    public void disable() {
        if (conquest != null) {
            conquest.disable();
        }
        if (domination != null) {
            domination.disable();
        }
        if (battleroyal != null) {
            battleroyal.disable();
        }
        if (masterkill != null) {
            masterkill.disable();
        }
        if (totem != null) {
            totem.disable();
        }
        if (koth != null) {
            koth.disable();
        }
        if (teamfight != null) {
            teamfight.disable();
        }
        if (largage != null) {
            largage.disable();
        }
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    public void reload() {
        loadCatalog();
        if (scheduler != null) {
            scheduler.start();
        }
        if (conquest != null && conquest.getConquestManager() != null) {
            conquest.getConquestManager().reload();
        }
        if (domination != null && domination.getDominationManager() != null) {
            domination.getDominationManager().reload();
        }
        if (battleroyal != null) {
            battleroyal.reloadConfig();
        }
        if (masterkill != null && masterkill.getMasterKillManager() != null) {
            masterkill.getMasterKillManager().reload();
        }
        if (totem != null && totem.getTotemManager() != null) {
            totem.getTotemManager().reload();
        }
        if (koth != null && koth.getKothManager() != null) {
            koth.getKothManager().reload();
        }
        if (teamfight != null) {
            teamfight.reloadConfig();
            if (teamfight.getKit() != null) {
                teamfight.getKit().load();
            }
        }
        if (largage != null) {
            largage.reloadConfig();
        }
    }

    public String prefix() {
        return catalog.getString("prefix", "&8[&6Event&8] &7");
    }

    public String scheduleTitle() {
        return catalog.getString("schedule.gui-title", "&8Events de la semaine");
    }

    public Location joinLocation(EventType type) {
        return joinLocation(type, null);
    }

    public Location joinLocation(EventType type, String mapId) {
        if (type == null) {
            return null;
        }
        if (plugin.warps() != null) {
            Location warp = plugin.warps().location(warpName(type, mapId));
            if (warp != null && warp.getWorld() != null) {
                return warp;
            }
        }
        switch (type) {
            case TOTEM:
            case TOTEM_GEANT:
                return totemJoin(mapId);
            case KOTH:
                return kothJoin(mapId);
            case CONQUEST:
                return conquestJoin();
            case DOMINATION:
                return dominationJoin();
            case TEAMFIGHT:
                return teamfight == null || teamfight.getManager() == null
                        ? null : teamfight.getManager().joinPoint();
            case BATTLEROYAL:
                return battleroyal == null || battleroyal.getGameManager() == null
                        ? null : battleroyal.getGameManager().getCenter();
            case MASTERKILL:
                return masterkill == null || masterkill.getArenaManager() == null
                        ? null : masterkill.getArenaManager().getCenter();
            case LARGAGE:
                return largage == null || largage.getManager() == null
                        ? null : largage.getManager().firstSpot();
            default:
                return null;
        }
    }

    public String warpName(EventType type) {
        return warpName(type, null);
    }

    public String warpName(EventType type, String mapId) {
        if (type == null) {
            return "";
        }
        String map = resolveMap(type, mapId);
        if (map == null || map.isEmpty()) {
            map = "default";
        }
        map = map.toLowerCase(Locale.ROOT);
        if (catalog != null) {
            String path = "warps." + type.id() + "." + map;
            String configured = catalog.getString(path);
            if (configured != null && !configured.trim().isEmpty()) {
                return configured.trim().toLowerCase(Locale.ROOT);
            }
            String single = catalog.getString("warps." + type.id());
            if (single != null && !single.trim().isEmpty() && !catalog.isConfigurationSection("warps." + type.id())) {
                return single.trim().toLowerCase(Locale.ROOT);
            }
        }
        return type.id() + "-" + map;
    }

    private Location totemJoin() {
        return totemJoin(null);
    }

    private Location totemJoin(String mapId) {
        if (totem == null || totem.getTotemManager() == null) {
            return null;
        }
        String map = resolveMap(EventType.TOTEM, mapId);
        if (map == null) {
            map = resolveMap(EventType.TOTEM_GEANT, mapId);
        }
        if (map != null) {
            Totem named = totem.getTotemManager().get(map);
            Location loc = standing(named == null ? null : named.getLocation());
            if (loc != null) {
                return loc;
            }
        }
        Totem active = totem.getTotemManager().getActive();
        Location activeLoc = standing(active == null ? null : active.getLocation());
        if (activeLoc != null) {
            return activeLoc;
        }
        for (Totem entry : totem.getTotemManager().all()) {
            Location loc = standing(entry.getLocation());
            if (loc != null) {
                return loc;
            }
        }
        return null;
    }

    private Location kothJoin() {
        return kothJoin(null);
    }

    private Location kothJoin(String mapId) {
        if (koth == null || koth.getKothManager() == null) {
            return null;
        }
        String map = resolveMap(EventType.KOTH, mapId);
        if (map != null) {
            KothZone named = koth.getKothManager().get(map);
            if (named != null && named.getCenter() != null) {
                return named.getCenter();
            }
        }
        KothZone active = koth.getKothManager().getActive();
        if (active != null && active.getCenter() != null) {
            return active.getCenter();
        }
        for (KothZone zone : koth.getKothManager().all()) {
            if (zone.getCenter() != null) {
                return zone.getCenter();
            }
        }
        return null;
    }

    private Location conquestJoin() {
        if (conquest == null || conquest.getZoneManager() == null) {
            return null;
        }
        for (fr.draftmc.events.conquest.model.Zone zone : conquest.getZoneManager().getZones().values()) {
            World world = zone.getWorldName() == null ? null : Bukkit.getWorld(zone.getWorldName());
            if (world != null) {
                Location center = zone.getCenter(world);
                center.setY(center.getY() + 1);
                return center;
            }
        }
        return null;
    }

    private Location dominationJoin() {
        if (domination == null || domination.getZoneManager() == null) {
            return null;
        }
        for (fr.draftmc.events.domination.model.Zone zone : domination.getZoneManager().getZones().values()) {
            World world = zone.getWorldName() == null ? null : Bukkit.getWorld(zone.getWorldName());
            if (world != null) {
                Location center = zone.getCenter(world);
                center.setY(center.getY() + 1);
                return center;
            }
        }
        return null;
    }

    private Location standing(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        Location loc = location.clone();
        loc.add(0.5, 1, 0.5);
        return loc;
    }

    public List<String> scheduleLore(String day) {
        List<String> lore = new ArrayList<String>();
        List<Slot> slots = scheduleSlots(day);
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            StringBuilder line = new StringBuilder("&8» &e");
            line.append(formatClock(slot.hour, slot.minute));
            line.append(" &6").append(slot.displayName());
            if (slot.map != null && !slot.map.isEmpty()) {
                line.append(" &7- &f").append(slot.map);
            }
            lore.add(line.toString());
        }
        if (lore.isEmpty()) {
            List<String> lines = catalog.getStringList("schedule." + day);
            for (String line : lines) {
                if (line != null && !line.isEmpty()) {
                    lore.add(line);
                }
            }
        }
        return lore;
    }

    public int topPointsFor(EventType type) {
        return catalog.getInt("top-points." + type.id(), 15);
    }

    public void awardTopPoints(EventType type, String factionId) {
        awardTopPoints(type, factionId, topPointsFor(type));
    }

    public void awardTopPoints(EventType type, String factionId, int amount) {
        if (factionId == null || factionId.isEmpty() || plugin.factions() == null) {
            return;
        }
        if (amount == 0) {
            return;
        }
        plugin.factions().addTopPoints(factionId, amount);
        String raw = catalog.getString("top-points.member-message",
                "&aVotre faction a reçu &e{points} points PVP !");
        if (raw == null || raw.isEmpty()) {
            return;
        }
        String msg = CC.color(prefix() + raw
                .replace("{points}", String.valueOf(amount))
                .replace("{faction}", plugin.factions().displayName(factionId))
                .replace("{event}", type.display()));
        for (String member : plugin.factions().members(factionId)) {
            try {
                Player player = Bukkit.getPlayer(UUID.fromString(member));
                if (player != null && player.isOnline()) {
                    player.sendMessage(msg);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public int rankingPointsFor(EventType type, int place) {
        if (type == null || place < 1) {
            return 0;
        }
        switch (type) {
            case KOTH:
                return koth == null ? 0 : koth.rankingPoints(place);
            case TEAMFIGHT:
                return teamfight == null ? 0 : teamfight.rankingPoints(place);
            case TOTEM_GEANT:
                return totem == null ? 0 : totem.rankingPoints(place);
            default:
                return place == 1 ? topPointsFor(type) : 0;
        }
    }

    public void announceDiscord(EventType type, String subtitle, List<String> lines) {
        if (plugin.discord() == null || type == null) {
            return;
        }
        FileConfiguration cfg = plugin.getConfig();
        String map = discordMapLabel(type, subtitle);
        String headerKey = map.isEmpty() ? "discord.events.header-no-map" : "discord.events.header";
        String headerDefault = map.isEmpty()
                ? ":loudspeaker: | {event}"
                : ":loudspeaker: | {event} - {map}";
        String title = cfg.getString(headerKey, headerDefault)
                .replace("{event}", type.display())
                .replace("{map}", map);
        String results = cfg.getString("discord.events.results", ":crossed_swords: Résultats");
        List<String> all = new ArrayList<String>();
        if (lines != null) {
            all.addAll(lines);
        }
        plugin.discord().postEventResult(title, results, all, discordColor(type));
    }

    public void announceDiscordRanking(EventType type, String subtitle,
            List<Map.Entry<String, Integer>> ranking, int limit) {
        String tpl = plugin.getConfig().getString("discord.events.result-line", "{medal} • {name} - {score}");
        List<String> lines = new ArrayList<String>();
        int place = 1;
        if (ranking != null) {
            for (Map.Entry<String, Integer> entry : ranking) {
                if (place > limit) {
                    break;
                }
                String name = plugin.factions() == null
                        ? entry.getKey()
                        : plugin.factions().displayName(entry.getKey());
                int pts = rankingPointsFor(type, place);
                if (pts <= 0) {
                    break;
                }
                lines.add(discordResultLine(tpl, place, name, pts));
                place++;
            }
        }
        if (lines.isEmpty()) {
            lines.add(plugin.getConfig().getString("discord.events.empty", "Aucun score."));
        }
        announceDiscord(type, subtitle, lines);
    }

    public void announceDiscordWinner(EventType type, String subtitle, String winnerName, int score) {
        String name = winnerName == null || winnerName.isEmpty() ? "?" : winnerName;
        int pts = rankingPointsFor(type, 1);
        if (pts <= 0 && score > 0) {
            pts = score;
        }
        String tpl = plugin.getConfig().getString("discord.events.result-line", "{medal} • {name} - {score}");
        List<String> lines = new ArrayList<String>();
        lines.add(discordResultLine(tpl, 1, name, pts));
        announceDiscord(type, subtitle, lines);
    }

    private String discordResultLine(String tpl, int place, String name, int score) {
        String line = tpl == null || tpl.isEmpty() ? "{medal} • {name} - {score}" : tpl;
        return line
                .replace("{medal}", discordMedal(place))
                .replace("{place}", String.valueOf(place))
                .replace("{name}", CC.strip(name == null ? "?" : name))
                .replace("{score}", String.valueOf(Math.max(0, score)));
    }

    private static String discordMedal(int place) {
        if (place == 1) {
            return ":first_place:";
        }
        if (place == 2) {
            return ":second_place:";
        }
        if (place == 3) {
            return ":third_place:";
        }
        return ":black_small_square:";
    }

    private String discordMapLabel(EventType type, String subtitle) {
        String id = subtitle == null ? "" : subtitle.trim();
        if (id.isEmpty() && type != null) {
            String active = activeMaps.get(type);
            if (active != null) {
                id = active;
            }
        }
        if (id.isEmpty()) {
            return "";
        }
        String display = mapDisplay(type, id);
        return CC.strip(display == null ? id : display);
    }

    private static int discordColor(EventType type) {
        switch (type) {
            case CONQUEST:
                return 0xE67E22;
            case DOMINATION:
                return 0xE74C3C;
            case BATTLEROYAL:
                return 0xF1C40F;
            case MASTERKILL:
                return 0xC0392B;
            case TOTEM:
                return 0x9B59B6;
            case TOTEM_GEANT:
                return 0xD35400;
            case KOTH:
                return 0xF39C12;
            case TEAMFIGHT:
                return 0x3498DB;
            case LARGAGE:
                return 0xF1C40F;
            default:
                return 0x95A5A6;
        }
    }

    public List<String> mapIds(EventType type) {
        return new ArrayList<String>(mapsOf(type).keySet());
    }

    public LinkedHashMap<String, String> mapsOf(EventType type) {
        LinkedHashMap<String, String> out = new LinkedHashMap<String, String>();
        if (type == EventType.KOTH && koth != null) {
            for (KothZone zone : koth.getKothManager().all()) {
                out.put(zone.getId(), zone.getDisplay());
            }
            return out;
        }
        ConfigurationSection section = catalog.getConfigurationSection("types." + type.id() + ".maps");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                out.put(id.toLowerCase(Locale.ROOT), section.getString(id + ".display", id));
            }
        }
        FileConfiguration eventCfg = eventConfig(type);
        if (eventCfg != null && eventCfg.isConfigurationSection("maps")) {
            for (String id : eventCfg.getConfigurationSection("maps").getKeys(false)) {
                String key = id.toLowerCase(Locale.ROOT);
                if (!out.containsKey(key)) {
                    out.put(key, eventCfg.getString("maps." + id + ".display", id));
                }
            }
        }
        if (out.isEmpty()) {
            out.put("default", "Default");
        }
        return out;
    }

    public String resolveMap(EventType type, String mapId) {
        if (mapId == null) {
            return null;
        }
        String want = mapId.toLowerCase(Locale.ROOT);
        for (String id : mapsOf(type).keySet()) {
            if (id.equals(want)) {
                return id;
            }
        }
        return null;
    }

    public String mapDisplay(EventType type, String mapId) {
        String id = resolveMap(type, mapId);
        if (id == null) {
            return mapId;
        }
        String display = mapsOf(type).get(id);
        return display == null ? id : display;
    }

    public String activeMap(EventType type) {
        return activeMaps.get(type);
    }

    public void markActive(EventType type, String mapId) {
        if (type != null && mapId != null) {
            activeMaps.put(type, mapId);
        }
    }

    public void clearActive(EventType type, String mapId) {
        String current = activeMaps.get(type);
        if (current != null && (mapId == null || current.equalsIgnoreCase(mapId))) {
            activeMaps.remove(type);
        }
    }

    public String status(EventType type) {
        switch (type) {
            case CONQUEST:
                return label(conquest.getConquestManager().getState().name());
            case DOMINATION:
                return label(domination.getDominationManager().getState().name());
            case BATTLEROYAL:
                return label(battleroyal.getGameManager().getState().name());
            case MASTERKILL:
                return label(masterkill.getMasterKillManager().getState().name());
            case TOTEM:
                Totem classic = activeTotem(false);
                return label(classic == null ? TotemStatus.WAITING.name() : classic.getStatus().name());
            case TOTEM_GEANT:
                Totem giant = activeTotem(true);
                return label(giant == null ? TotemStatus.WAITING.name() : giant.getStatus().name());
            case KOTH:
                return label(koth.getKothManager().isRunning() ? "RUNNING" : "WAITING");
            case TEAMFIGHT:
                return label(teamfight.getManager().getState().name());
            case LARGAGE:
                return label(largage.getManager().running() ? "RUNNING" : "WAITING");
            default:
                return "?";
        }
    }

    public boolean isBusy(EventType type) {
        switch (type) {
            case CONQUEST:
                return conquest.getConquestManager().getState() != ConquestState.WAITING;
            case DOMINATION:
                return domination.getDominationManager().getState() != DominationState.WAITING;
            case BATTLEROYAL:
                return battleroyal.getGameManager().getState() != GameState.WAITING;
            case MASTERKILL:
                return masterkill.getMasterKillManager().getState() != MasterKillState.WAITING;
            case TOTEM:
                return totem.getTotemManager().isBusy() && !totem.getTotemManager().isGiantMode();
            case TOTEM_GEANT:
                return totem.getTotemManager().isBusy() && totem.getTotemManager().isGiantMode();
            case KOTH:
                return koth.getKothManager().isRunning();
            case TEAMFIGHT:
                return teamfight.getManager().isBusy();
            case LARGAGE:
                return largage.getManager().running();
            default:
                return false;
        }
    }

    public StartResult start(EventType type, String mapId) {
        String map = resolveMap(type, mapId);
        if (map == null) {
            return StartResult.UNKNOWN_MAP;
        }
        applyMap(type, map);
        switch (type) {
            case CONQUEST:
                if (conquest.getConquestManager().getState() != ConquestState.WAITING) {
                    return StartResult.ALREADY_RUNNING;
                }
                activeMaps.put(type, map);
                return conquest.getConquestManager().start() ? StartResult.STARTED : StartResult.FAILED;
            case DOMINATION:
                if (domination.getDominationManager().getState() != DominationState.WAITING) {
                    return StartResult.ALREADY_RUNNING;
                }
                activeMaps.put(type, map);
                return domination.getDominationManager().start() ? StartResult.STARTED : StartResult.FAILED;
            case BATTLEROYAL:
                GameState br = battleroyal.getGameManager().getState();
                if (br == GameState.INGAME || br == GameState.ENDING) {
                    return StartResult.ALREADY_RUNNING;
                }
                activeMaps.put(type, map);
                if (br == GameState.WAITING) {
                    return battleroyal.getGameManager().openRegistrations()
                            ? StartResult.REGISTRATION_OPENED : StartResult.FAILED;
                }
                if (!battleroyal.getGameManager().isReadyToLaunch()) {
                    return StartResult.NEED_TEAMS;
                }
                battleroyal.getGameManager().launchGame();
                return battleroyal.getGameManager().getState() == GameState.INGAME
                        ? StartResult.LAUNCHED : StartResult.FAILED;
            case MASTERKILL:
                MasterKillState mk = masterkill.getMasterKillManager().getState();
                if (mk == MasterKillState.RUNNING) {
                    return StartResult.ALREADY_RUNNING;
                }
                activeMaps.put(type, map);
                if (mk == MasterKillState.WAITING) {
                    return masterkill.getMasterKillManager().start()
                            ? StartResult.REGISTRATION_OPENED : StartResult.FAILED;
                }
                if (!masterkill.getMasterKillManager().isReadyToLaunch()) {
                    return StartResult.NEED_TEAMS;
                }
                masterkill.getMasterKillManager().launch();
                return masterkill.getMasterKillManager().getState() == MasterKillState.RUNNING
                        ? StartResult.LAUNCHED : StartResult.FAILED;
            case TOTEM:
            case TOTEM_GEANT:
                if (totem.getTotemManager().isBusy()) {
                    return StartResult.ALREADY_RUNNING;
                }
                if (!totem.getTotemManager().startCountdown(map, type == EventType.TOTEM_GEANT)) {
                    return StartResult.FAILED;
                }
                activeMaps.put(type, map);
                return StartResult.STARTED;
            case KOTH:
                if (koth.getKothManager().isRunning()) {
                    return StartResult.ALREADY_RUNNING;
                }
                if (!koth.getKothManager().start(map)) {
                    return StartResult.FAILED;
                }
                activeMaps.put(type, map);
                return StartResult.STARTED;
            case TEAMFIGHT:
                TeamFightState tf = teamfight.getManager().getState();
                if (tf == TeamFightState.FIGHTING || tf == TeamFightState.COUNTDOWN
                        || tf == TeamFightState.BETWEEN) {
                    return StartResult.ALREADY_RUNNING;
                }
                activeMaps.put(type, map);
                if (tf == TeamFightState.WAITING || tf == TeamFightState.ENDED) {
                    return teamfight.getManager().openRegistrations()
                            ? StartResult.REGISTRATION_OPENED : StartResult.FAILED;
                }
                if (!teamfight.getManager().isReadyToLaunch()) {
                    return StartResult.NEED_TEAMS;
                }
                return teamfight.getManager().launch() ? StartResult.LAUNCHED : StartResult.FAILED;
            case LARGAGE:
                if (largage.getManager().running()) {
                    return StartResult.ALREADY_RUNNING;
                }
                if (!largage.getManager().start()) {
                    return StartResult.FAILED;
                }
                activeMaps.put(type, map);
                return StartResult.STARTED;
            default:
                return StartResult.FAILED;
        }
    }

    public StopResult stop(EventType type, String mapId) {
        String map = resolveMap(type, mapId);
        if (map == null) {
            return StopResult.UNKNOWN_MAP;
        }
        if (!isBusy(type)) {
            return StopResult.NOT_RUNNING;
        }
        String running = activeMaps.get(type);
        if (running != null && !running.equalsIgnoreCase(map)) {
            return StopResult.WRONG_MAP;
        }
        boolean ok = false;
        switch (type) {
            case CONQUEST:
                ok = conquest.getConquestManager().stop();
                break;
            case DOMINATION:
                ok = domination.getDominationManager().stop();
                break;
            case BATTLEROYAL:
                ok = battleroyal.getGameManager().stopGame();
                break;
            case MASTERKILL:
                ok = masterkill.getMasterKillManager().stop();
                break;
            case TOTEM:
            case TOTEM_GEANT:
                ok = totem.getTotemManager().stop(map);
                break;
            case KOTH:
                ok = koth.getKothManager().stop(false);
                break;
            case TEAMFIGHT:
                ok = teamfight.getManager().stop();
                break;
            case LARGAGE:
                ok = largage.getManager().stop(true);
                break;
            default:
                break;
        }
        if (ok) {
            activeMaps.remove(type);
            return StopResult.STOPPED;
        }
        return StopResult.NOT_RUNNING;
    }

    private void applyMap(EventType type, String mapId) {
        String world = mapWorld(type, mapId);
        ConfigurationSection mapSec = catalog.getConfigurationSection("types." + type.id() + ".maps." + mapId);
        switch (type) {
            case CONQUEST:
                if (world != null) {
                    conquest.getConfig().set("general.world", world);
                }
                conquest.getZoneManager().loadForMap(mapId, world);
                break;
            case DOMINATION:
                if (world != null) {
                    domination.getConfig().set("general.world", world);
                }
                domination.getZoneManager().loadForMap(mapId, world);
                break;
            case BATTLEROYAL:
                if (world != null) {
                    battleroyal.getConfig().set("arena.world", world);
                }
                if (mapSec != null) {
                    if (mapSec.contains("center-x")) {
                        battleroyal.getConfig().set("arena.center-x", mapSec.getDouble("center-x"));
                    }
                    if (mapSec.contains("center-y")) {
                        battleroyal.getConfig().set("arena.center-y", mapSec.getDouble("center-y"));
                    }
                    if (mapSec.contains("center-z")) {
                        battleroyal.getConfig().set("arena.center-z", mapSec.getDouble("center-z"));
                    }
                    if (mapSec.contains("initial-size")) {
                        battleroyal.getConfig().set("arena.initial-size", mapSec.getDouble("initial-size"));
                    }
                }
                break;
            case MASTERKILL:
                if (world != null) {
                    masterkill.getConfig().set("arena.pos1.world", world);
                    masterkill.getConfig().set("arena.pos2.world", world);
                }
                if (mapSec != null) {
                    applyCorner(masterkill.getConfig(), "arena.pos1", mapSec.getConfigurationSection("pos1"), world);
                    applyCorner(masterkill.getConfig(), "arena.pos2", mapSec.getConfigurationSection("pos2"), world);
                }
                break;
            case TOTEM:
            case TOTEM_GEANT:
                Double x = mapSec != null && mapSec.contains("x") ? Double.valueOf(mapSec.getDouble("x")) : null;
                Double y = mapSec != null && mapSec.contains("y") ? Double.valueOf(mapSec.getDouble("y")) : null;
                Double z = mapSec != null && mapSec.contains("z") ? Double.valueOf(mapSec.getDouble("z")) : null;
                totem.getTotemManager().applyMap(mapId, world, x, y, z);
                break;
            case KOTH:
                break;
            case TEAMFIGHT:
                break;
            case LARGAGE:
                break;
            default:
                break;
        }
    }

    private void applyCorner(FileConfiguration cfg, String path, ConfigurationSection pos, String world) {
        if (pos == null) {
            return;
        }
        if (world != null) {
            cfg.set(path + ".world", world);
        } else if (pos.contains("world")) {
            cfg.set(path + ".world", pos.getString("world"));
        }
        if (pos.contains("x")) {
            cfg.set(path + ".x", pos.getDouble("x"));
        }
        if (pos.contains("y")) {
            cfg.set(path + ".y", pos.getDouble("y"));
        }
        if (pos.contains("z")) {
            cfg.set(path + ".z", pos.getDouble("z"));
        }
    }

    private String mapWorld(EventType type, String mapId) {
        String fromCatalog = catalog.getString("types." + type.id() + ".maps." + mapId + ".world");
        if (fromCatalog != null && !fromCatalog.isEmpty()) {
            return fromCatalog;
        }
        FileConfiguration eventCfg = eventConfig(type);
        if (eventCfg != null) {
            String fromEvent = eventCfg.getString("maps." + mapId + ".world");
            if (fromEvent != null && !fromEvent.isEmpty()) {
                return fromEvent;
            }
        }
        return null;
    }

    private FileConfiguration eventConfig(EventType type) {
        switch (type) {
            case CONQUEST:
                return conquest.getConfig();
            case DOMINATION:
                return domination.getConfig();
            case BATTLEROYAL:
                return battleroyal.getConfig();
            case MASTERKILL:
                return masterkill.getConfig();
            case TOTEM:
            case TOTEM_GEANT:
                return totem.getConfig();
            case KOTH:
                return koth.getConfig();
            case TEAMFIGHT:
                return teamfight.getConfig();
            case LARGAGE:
                return largage.getConfig();
            default:
                return null;
        }
    }

    private static String label(String raw) {
        if ("WAITING".equals(raw)) {
            return "En attente";
        }
        if ("STARTING".equals(raw) || "REGISTRATION".equals(raw)) {
            return "Inscriptions / compte a rebours";
        }
        if ("RUNNING".equals(raw) || "INGAME".equals(raw) || "STARTED".equals(raw)
                || "FIGHTING".equals(raw) || "BETWEEN".equals(raw) || "COUNTDOWN".equals(raw)) {
            return "En cours";
        }
        if ("ENDING".equals(raw)) {
            return "Fin de manche";
        }
        if ("FINISHED".equals(raw) || "ENDED".equals(raw)) {
            return "Termine";
        }
        return raw;
    }

    public ConquestPlugin conquest() {
        return conquest;
    }

    public DominationPlugin domination() {
        return domination;
    }

    public BattleRoyal battleroyal() {
        return battleroyal;
    }

    public MasterKillPlugin masterkill() {
        return masterkill;
    }

    public TotemPlugin totem() {
        return totem;
    }

    public KothPlugin koth() {
        return koth;
    }

    public TeamFightPlugin teamfight() {
        return teamfight;
    }

    public EventMonthStats monthStats() {
        return monthStats;
    }

    public LargagePlugin largage() {
        return largage;
    }

    private Totem activeTotem(boolean giant) {
        if (totem.getTotemManager().isGiantMode() != giant) {
            return null;
        }
        return totem.getTotemManager().getActive();
    }

    public Upcoming nextUpcoming() {
        Calendar now = Calendar.getInstance();
        long nowMs = now.getTimeInMillis();
        Upcoming best = null;
        String[] days = {"monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"};
        for (int offset = 0; offset < 8; offset++) {
            Calendar dayCal = Calendar.getInstance();
            dayCal.add(Calendar.DAY_OF_YEAR, offset);
            int dayIndex = (dayCal.get(Calendar.DAY_OF_WEEK) + 5) % 7;
            List<Slot> slots = scheduleSlots(days[dayIndex]);
            for (int i = 0; i < slots.size(); i++) {
                Slot slot = slots.get(i);
                if (!slot.auto) {
                    continue;
                }
                Calendar at = (Calendar) dayCal.clone();
                at.set(Calendar.HOUR_OF_DAY, slot.hour);
                at.set(Calendar.MINUTE, slot.minute);
                at.set(Calendar.SECOND, 0);
                at.set(Calendar.MILLISECOND, 0);
                long atMs = at.getTimeInMillis();
                if (atMs <= nowMs) {
                    continue;
                }
                if (best == null || atMs < best.atMillis) {
                    best = new Upcoming(upcomingLabel(slot), atMs);
                }
            }
        }
        return best;
    }

    private String upcomingLabel(Slot slot) {
        String event = slot.displayName();
        String map = mapLabel(slot);
        if (map == null || map.isEmpty()) {
            return event;
        }
        return event + " &7(" + map + ")";
    }

    private String mapLabel(Slot slot) {
        if (slot.map == null || slot.map.trim().isEmpty()) {
            return "";
        }
        EventType type = EventType.from(slot.event);
        if (type != null) {
            String display = mapDisplay(type, slot.map);
            if (display != null && !display.trim().isEmpty()) {
                return display;
            }
        }
        return slot.map;
    }

    public List<Slot> scheduleSlots(String day) {
        List<Slot> out = new ArrayList<Slot>();
        if (catalog == null || day == null) {
            return out;
        }
        String path = "schedule." + day;
        List<?> raw = catalog.getList(path);
        if (raw != null) {
            for (int i = 0; i < raw.size(); i++) {
                Slot slot = slotFrom(raw.get(i));
                if (slot != null) {
                    out.add(slot);
                }
            }
        }
        if (out.isEmpty()) {
            List<Map<?, ?>> maps = catalog.getMapList(path);
            for (int i = 0; i < maps.size(); i++) {
                Slot slot = slotFrom(maps.get(i));
                if (slot != null) {
                    out.add(slot);
                }
            }
        }
        if (out.isEmpty() && catalog.isConfigurationSection(path)) {
            ConfigurationSection section = catalog.getConfigurationSection(path);
            for (String key : section.getKeys(false)) {
                Slot slot = slotFrom(section.get(key));
                if (slot != null) {
                    out.add(slot);
                }
            }
        }
        return out;
    }

    private Slot slotFrom(Object raw) {
        if (raw instanceof Slot) {
            return (Slot) raw;
        }
        String event = null;
        String map = "default";
        Object timeObj = null;
        boolean auto = true;
        if (raw instanceof Map<?, ?>) {
            Map<?, ?> mapRaw = (Map<?, ?>) raw;
            event = stringify(mapGet(mapRaw, "event"));
            String parsedMap = stringify(mapGet(mapRaw, "map"));
            if (!parsedMap.isEmpty()) {
                map = parsedMap;
            }
            timeObj = mapGet(mapRaw, "time");
            Object autoObj = mapGet(mapRaw, "auto");
            if (autoObj instanceof Boolean) {
                auto = ((Boolean) autoObj).booleanValue();
            }
        } else if (raw instanceof ConfigurationSection) {
            ConfigurationSection sec = (ConfigurationSection) raw;
            event = stringify(sec.get("event"));
            String parsedMap = stringify(sec.get("map"));
            if (!parsedMap.isEmpty()) {
                map = parsedMap;
            }
            timeObj = sec.get("time");
            if (sec.contains("auto")) {
                auto = sec.getBoolean("auto");
            }
        } else {
            return null;
        }
        int[] clock = parseClock(timeObj);
        if (clock == null || event.isEmpty()) {
            return null;
        }
        return new Slot(event, map, clock[0], clock[1], auto);
    }

    private Object mapGet(Map<?, ?> map, String key) {
        if (map.containsKey(key)) {
            return map.get(key);
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null && key.equalsIgnoreCase(String.valueOf(entry.getKey()))) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String stringify(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private int[] parseClock(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Calendar) {
            Calendar cal = (Calendar) raw;
            return new int[]{cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)};
        }
        if (raw instanceof java.util.Date) {
            Calendar cal = Calendar.getInstance();
            cal.setTime((java.util.Date) raw);
            return new int[]{cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)};
        }
        if (raw instanceof Number) {
            int value = ((Number) raw).intValue();
            if (value >= 0 && value < 24) {
                return new int[]{value, 0};
            }
            if (value >= 24 && value < 24 * 60) {
                return new int[]{value / 60, value % 60};
            }
            if (value >= 1000 && value <= 2359) {
                int hour = value / 100;
                int minute = value % 100;
                if (hour < 24 && minute < 60) {
                    return new int[]{hour, minute};
                }
            }
            return null;
        }
        String text = String.valueOf(raw).trim().toLowerCase(Locale.ROOT).replace('h', ':');
        if (text.matches("\\d+")) {
            return parseClock(Integer.valueOf(text));
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d{1,2})\\D+(\\d{1,2})").matcher(text);
        if (matcher.find()) {
            int hour = Integer.parseInt(matcher.group(1));
            int minute = Integer.parseInt(matcher.group(2));
            if (hour >= 0 && hour < 24 && minute >= 0 && minute < 60) {
                return new int[]{hour, minute};
            }
        }
        matcher = java.util.regex.Pattern.compile("^(\\d{1,2})$").matcher(text);
        if (matcher.find()) {
            int hour = Integer.parseInt(matcher.group(1));
            if (hour >= 0 && hour < 24) {
                return new int[]{hour, 0};
            }
        }
        return null;
    }

    public static String formatClock(int hour, int minute) {
        return String.format(Locale.ROOT, "%02d:%02d", hour, minute);
    }

    public static String formatUntil(long atMillis) {
        long sec = Math.max(0L, (atMillis - System.currentTimeMillis()) / 1000L);
        long days = sec / 86400L;
        sec %= 86400L;
        long hours = sec / 3600L;
        sec %= 3600L;
        long minutes = sec / 60L;
        sec %= 60L;
        if (days > 0L) {
            return days + "j " + hours + "h";
        }
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0L) {
            return minutes + "m " + sec + "s";
        }
        return sec + "s";
    }

    public static final class Slot {
        public final String event;
        public final String map;
        public final int hour;
        public final int minute;
        public final boolean auto;

        public Slot(String event, String map, int hour, int minute, boolean auto) {
            this.event = event;
            this.map = map;
            this.hour = hour;
            this.minute = minute;
            this.auto = auto;
        }

        public String displayName() {
            EventType type = EventType.from(event);
            return type != null ? type.display() : event;
        }
    }

    public static final class Upcoming {
        public final String name;
        public final long atMillis;

        public Upcoming(String name, long atMillis) {
            this.name = name;
            this.atMillis = atMillis;
        }
    }
}
