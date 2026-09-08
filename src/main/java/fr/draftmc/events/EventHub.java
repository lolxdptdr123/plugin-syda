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
import fr.draftmc.events.teamfight.TeamFightPlugin;
import fr.draftmc.events.teamfight.TeamFightState;
import fr.draftmc.events.totem.Totem;
import fr.draftmc.events.totem.TotemPlugin;
import fr.draftmc.events.totem.TotemStatus;
import fr.draftmc.util.CC;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
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
    private FileConfiguration catalog;
    private final Map<EventType, String> activeMaps = new EnumMap<EventType, String>(EventType.class);

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
    }

    private void loadCatalog() {
        File file = new File(plugin.getDataFolder(), "events.yml");
        if (!file.exists() && plugin.getResource("events.yml") != null) {
            plugin.saveResource("events.yml", false);
        }
        this.catalog = YamlConfiguration.loadConfiguration(file);
        if (!catalog.isConfigurationSection("schedule")) {
            java.io.InputStream stream = plugin.getResource("events.yml");
            if (stream != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8));
                if (defaults.isConfigurationSection("schedule")) {
                    catalog.set("schedule", defaults.get("schedule"));
                    try {
                        catalog.save(file);
                    } catch (Exception ignored) {
                    }
                }
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
    }

    public void reload() {
        loadCatalog();
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
    }

    public String prefix() {
        return catalog.getString("prefix", "&8[&6Event&8] &7");
    }

    public String scheduleTitle() {
        return catalog.getString("schedule.gui-title", "&8Events de la semaine");
    }

    public List<String> scheduleLore(String day) {
        List<String> lore = new ArrayList<String>();
        String path = "schedule." + day;
        List<java.util.Map<?, ?>> maps = catalog.getMapList(path);
        if (maps != null && !maps.isEmpty()) {
            for (java.util.Map<?, ?> entry : maps) {
                if (entry == null) {
                    continue;
                }
                Object event = entry.get("event");
                Object map = entry.get("map");
                Object time = entry.get("time");
                if (event == null && map == null) {
                    continue;
                }
                String eventName = event == null ? "?" : String.valueOf(event);
                String mapName = map == null || String.valueOf(map).isEmpty() ? "" : String.valueOf(map);
                String timeText = time == null || String.valueOf(time).isEmpty() ? "" : String.valueOf(time);
                StringBuilder line = new StringBuilder("&8» ");
                if (!timeText.isEmpty()) {
                    line.append("&e").append(timeText).append(" ");
                }
                line.append("&6").append(eventName);
                if (!mapName.isEmpty()) {
                    line.append(" &7- &f").append(mapName);
                }
                lore.add(line.toString());
            }
        }
        if (lore.isEmpty()) {
            List<String> lines = catalog.getStringList(path);
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
                ok = koth.getKothManager().stop(true);
                break;
            case TEAMFIGHT:
                ok = teamfight.getManager().stop();
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

    private Totem activeTotem(boolean giant) {
        if (totem.getTotemManager().isGiantMode() != giant) {
            return null;
        }
        return totem.getTotemManager().getActive();
    }
}
