package fr.sydaria.factions;

import fr.sydaria.Sydaria;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

import java.awt.Color;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Waypoint "Rally" sur la minimap Lunar Client.
 * Necessite le plugin Apollo sur le serveur (pas de dependance Maven).
 */
public class LunarWaypointHook implements Listener {
    public static final String RALLY_WAYPOINT_NAME = "Rally";
    private static final long RELOCATE_REMOVE_DELAY = 5L;
    private static final long RELOCATE_DISPLAY_DELAY = 10L;

    private final Sydaria plugin;
    private final FactionManager factions;
    private final FactionRallyManager rallyManager;
    private final Map<UUID, String> lastSent = new HashMap<UUID, String>();
    private final Map<String, String> factionRallyKeys = new HashMap<String, String>();
    private boolean enabled;
    private boolean debug;
    private Object playerManager;
    private Object waypointModule;
    private Method getPlayerMethod;
    private Method displayWaypointMethod;
    private Method removeWaypointMethod;
    private Method hideWaypointMethod;
    private Class<?> apolloPlayerClass;
    private Object serverHandlesWaypointsOption;

    public LunarWaypointHook(Sydaria plugin, FactionManager factions, FactionRallyManager rallyManager) {
        this.plugin = plugin;
        this.factions = factions;
        this.rallyManager = rallyManager;
        init();
    }

    public boolean isEnabled() {
        return enabled;
    }

    private void init() {
        debug = plugin.getConfig().getBoolean("factions.rally.lunar-debug", false);
        if (!plugin.getConfig().getBoolean("factions.rally.lunar-waypoints", true)) {
            return;
        }
        Plugin apollo = findApolloPlugin();
        if (apollo == null) {
            plugin.getLogger().info("Rally Lunar: installe Apollo-Bukkit pour les waypoints minimap.");
            return;
        }
        try {
            Class<?> apolloClass = Class.forName("com.lunarclient.apollo.Apollo");
            Class<?> waypointModuleClass = Class.forName("com.lunarclient.apollo.module.waypoint.WaypointModule");
            Object moduleManager = apolloClass.getMethod("getModuleManager").invoke(null);
            playerManager = apolloClass.getMethod("getPlayerManager").invoke(null);
            waypointModule = moduleManager.getClass().getMethod("getModule", Class.class).invoke(moduleManager, waypointModuleClass);
            displayWaypointMethod = findDisplayWaypointMethod(waypointModuleClass);
            removeWaypointMethod = findRemoveWaypointByNameMethod(waypointModuleClass);
            hideWaypointMethod = findHideWaypointMethod(waypointModuleClass);
            getPlayerMethod = playerManager.getClass().getMethod("getPlayer", UUID.class);
            apolloPlayerClass = Class.forName("com.lunarclient.apollo.player.ApolloPlayer");
            serverHandlesWaypointsOption = resolveServerHandlesOption(waypointModuleClass);
            registerApolloJoinEvent();
            enabled = true;
            plugin.getLogger().info("Rally Lunar: Apollo actif (" + apollo.getName() + " v"
                    + apollo.getDescription().getVersion() + ").");
        } catch (Throwable ex) {
            plugin.getLogger().warning("Rally Lunar: Apollo present mais API illisible - " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private static Method findDisplayWaypointMethod(Class<?> type) throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            if (!"displayWaypoint".equals(method.getName()) || method.getParameterTypes().length != 2) {
                continue;
            }
            if (method.getParameterTypes()[1].getName().endsWith("Waypoint")) {
                return method;
            }
        }
        throw new NoSuchMethodException("displayWaypoint(Recipients, Waypoint)");
    }

    private static Method findRemoveWaypointByNameMethod(Class<?> type) throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            if (!"removeWaypoint".equals(method.getName()) || method.getParameterTypes().length != 2) {
                continue;
            }
            if (method.getParameterTypes()[1] == String.class) {
                return method;
            }
        }
        throw new NoSuchMethodException("removeWaypoint(Recipients, String)");
    }

    private static Method findHideWaypointMethod(Class<?> type) {
        for (Method method : type.getMethods()) {
            if (!"hideWaypoint".equals(method.getName()) || method.getParameterTypes().length != 2) {
                continue;
            }
            if (method.getParameterTypes()[1] == String.class) {
                return method;
            }
        }
        return null;
    }

    private static Object resolveServerHandlesOption(Class<?> waypointModuleClass) {
        try {
            Field field = waypointModuleClass.getField("SERVER_HANDLES_WAYPOINTS");
            return field.get(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Plugin findApolloPlugin() {
        Plugin found = Bukkit.getPluginManager().getPlugin("Apollo-Bukkit");
        if (found != null) {
            return found;
        }
        found = Bukkit.getPluginManager().getPlugin("Apollo");
        if (found != null) {
            return found;
        }
        for (Plugin candidate : Bukkit.getPluginManager().getPlugins()) {
            if (candidate.getName().toLowerCase().contains("apollo")) {
                return candidate;
            }
        }
        return null;
    }

    private void registerApolloJoinEvent() {
        try {
            final Class<? extends org.bukkit.event.Event> eventClass =
                    (Class<? extends org.bukkit.event.Event>) Class.forName(
                            "com.lunarclient.apollo.event.player.ApolloRegisterPlayerEvent");
            Bukkit.getPluginManager().registerEvent(
                    eventClass,
                    this,
                    org.bukkit.event.EventPriority.MONITOR,
                    new EventExecutor() {
                        @Override
                        public void execute(org.bukkit.event.Listener listener, org.bukkit.event.Event event) {
                            if (!enabled || !eventClass.isInstance(event)) {
                                return;
                            }
                            try {
                                Object apolloPlayer = eventClass.getMethod("getPlayer").invoke(event);
                                UUID uuid = (UUID) apolloPlayer.getClass().getMethod("getUniqueId").invoke(apolloPlayer);
                                enableServerWaypoints(apolloPlayer);
                                Bukkit.getScheduler().runTask(plugin, new Runnable() {
                                    @Override
                                    public void run() {
                                        Player player = Bukkit.getPlayer(uuid);
                                        if (player != null) {
                                            syncPlayer(player);
                                        }
                                    }
                                });
                            } catch (Throwable ex) {
                                plugin.getLogger().warning("Rally Lunar event: " + ex.getMessage());
                            }
                        }
                    },
                    plugin,
                    false
            );
        } catch (Throwable ex) {
            plugin.getLogger().warning("Rally Lunar: event ApolloRegisterPlayerEvent introuvable.");
        }
    }

    private void enableServerWaypoints(Object apolloPlayer) {
        if (serverHandlesWaypointsOption == null || waypointModule == null) {
            return;
        }
        try {
            Method getOptions = waypointModule.getClass().getMethod("getOptions");
            Object options = getOptions.invoke(waypointModule);
            for (Method method : options.getClass().getMethods()) {
                if (!method.getName().equals("set") || method.getParameterTypes().length != 3) {
                    continue;
                }
                method.invoke(options, apolloPlayer, serverHandlesWaypointsOption, Boolean.TRUE);
                return;
            }
        } catch (Throwable ex) {
            logDebug("server-handles-waypoints: " + ex.getMessage());
        }
    }

    public void syncFaction(String fac, Location rally) {
        if (!enabled) {
            return;
        }
        if (rally == null) {
            factionRallyKeys.remove(fac);
            syncFactionNow(fac, null);
            return;
        }

        String newKey = locationKey(rally);
        String previousKey = factionRallyKeys.get(fac);
        boolean moved = previousKey != null && !previousKey.equals(newKey);
        factionRallyKeys.put(fac, newKey);

        if (moved) {
            logDebug("Rally faction " + fac + " deplace: " + previousKey + " -> " + newKey);
            for (String raw : factions.members(fac)) {
                try {
                    lastSent.remove(UUID.fromString(raw));
                } catch (IllegalArgumentException ignored) {
                }
            }
            syncFactionRelocate(fac, rally);
            scheduleFactionRelocate(fac, rally, 15L);
            scheduleFactionRelocate(fac, rally, 40L);
        } else {
            syncFactionNow(fac, rally);
            scheduleFactionRetry(fac, 20L);
            scheduleFactionRetry(fac, 100L);
        }
    }

    private void syncFactionNow(String fac, Location rally) {
        for (String raw : factions.members(fac)) {
            Player online = Bukkit.getPlayer(UUID.fromString(raw));
            if (online == null) {
                continue;
            }
            if (rally == null) {
                removeRally(online);
            } else {
                displayRallyFirstTime(online, rally);
            }
        }
    }

    private void syncFactionRelocate(String fac, Location rally) {
        for (String raw : factions.members(fac)) {
            Player online = Bukkit.getPlayer(UUID.fromString(raw));
            if (online != null && rally != null) {
                relocateRally(online, rally);
            }
        }
    }

    private void scheduleFactionRelocate(final String fac, final Location rally, long delay) {
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                Location current = rallyManager.getRally(fac);
                if (current != null && locationKey(current).equals(locationKey(rally))) {
                    syncFactionRelocate(fac, current);
                }
            }
        }, delay);
    }

    private void scheduleFactionRetry(final String fac, long delay) {
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                Location current = rallyManager.getRally(fac);
                if (current != null) {
                    syncFactionNow(fac, current);
                }
            }
        }, delay);
    }

    private void displayRallyFirstTime(Player player, Location location) {
        if (!enabled || player == null || location == null || location.getWorld() == null) {
            return;
        }
        final String key = locationKey(location);
        if (key.equals(lastSent.get(player.getUniqueId()))) {
            return;
        }
        withApolloPlayer(player, new ApolloCallback() {
            @Override
            public void run(Object apolloPlayer) throws Exception {
                enableServerWaypoints(apolloPlayer);
                sendWaypoint(apolloPlayer, player, location, key);
            }
        }, "afficher");
    }

    private void relocateRally(Player player, Location location) {
        if (!enabled || player == null || location == null || location.getWorld() == null) {
            return;
        }
        lastSent.remove(player.getUniqueId());
        final String key = locationKey(location);
        withApolloPlayer(player, new ApolloCallback() {
            @Override
            public void run(Object apolloPlayer) throws Exception {
                enableServerWaypoints(apolloPlayer);
                clearWaypointClient(apolloPlayer);
                scheduleRelocateDisplay(player, location, key);
                logDebug("Rally relocate demarre pour " + player.getName() + " -> " + key);
            }
        }, "deplacer");
    }

    private void clearWaypointClient(Object apolloPlayer) throws Exception {
        if (hideWaypointMethod != null) {
            hideWaypointMethod.invoke(waypointModule, apolloPlayer, RALLY_WAYPOINT_NAME);
        }
        removeWaypointMethod.invoke(waypointModule, apolloPlayer, RALLY_WAYPOINT_NAME);
    }

    private void scheduleRelocateDisplay(final Player player, final Location location, final String key) {
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !enabled) {
                    return;
                }
                withApolloPlayer(player, new ApolloCallback() {
                    @Override
                    public void run(Object apolloPlayer) throws Exception {
                        clearWaypointClient(apolloPlayer);
                    }
                }, "retirer");
            }
        }, RELOCATE_REMOVE_DELAY);

        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !enabled) {
                    return;
                }
                String fac = factions.factionOf(player);
                if (fac.isEmpty()) {
                    return;
                }
                Location current = rallyManager.getRally(fac);
                if (current == null || !locationKey(current).equals(key)) {
                    return;
                }
                withApolloPlayer(player, new ApolloCallback() {
                    @Override
                    public void run(Object apolloPlayer) throws Exception {
                        sendWaypoint(apolloPlayer, player, location, key);
                        logDebug("Rally relocate termine pour " + player.getName() + " @ " + key);
                    }
                }, "afficher");
            }
        }, RELOCATE_DISPLAY_DELAY);
    }

    private void sendWaypoint(Object apolloPlayer, Player player, Location location, String key) throws Exception {
        Object waypoint = buildWaypoint(location);
        displayWaypointMethod.invoke(waypointModule, apolloPlayer, waypoint);
        lastSent.put(player.getUniqueId(), key);
        logDebug("Waypoint Rally envoye a " + player.getName() + " @ " + key);
    }

    public void removeRally(Player player) {
        if (!enabled || player == null) {
            return;
        }
        lastSent.remove(player.getUniqueId());
        withApolloPlayer(player, new ApolloCallback() {
            @Override
            public void run(Object apolloPlayer) throws Exception {
                clearWaypointClient(apolloPlayer);
            }
        }, "retirer");
    }

    public void syncPlayer(Player player) {
        if (!enabled || player == null) {
            return;
        }
        String fac = factions.factionOf(player);
        if (fac.isEmpty()) {
            removeRally(player);
            return;
        }
        Location rally = rallyManager.getRally(fac);
        if (rally == null) {
            removeRally(player);
            return;
        }
        String facKey = factionRallyKeys.get(fac);
        String rallyKey = locationKey(rally);
        if (facKey != null && facKey.equals(rallyKey) && lastSent.containsKey(player.getUniqueId())) {
            if (rallyKey.equals(lastSent.get(player.getUniqueId()))) {
                return;
            }
        }
        if (lastSent.containsKey(player.getUniqueId()) && !rallyKey.equals(lastSent.get(player.getUniqueId()))) {
            relocateRally(player, rally);
        } else {
            displayRallyFirstTime(player, rally);
        }
    }

    private static String locationKey(Location location) {
        return location.getWorld().getName() + ":"
                + location.getBlockX() + ":"
                + location.getBlockY() + ":"
                + location.getBlockZ();
    }

    private Object buildWaypoint(Location location) throws Exception {
        Class<?> blockLocClass = Class.forName("com.lunarclient.apollo.common.location.ApolloBlockLocation");
        Object locBuilder = blockLocClass.getMethod("builder").invoke(null);
        Class<?> locBuilderClass = locBuilder.getClass();
        locBuilderClass.getMethod("world", String.class).invoke(locBuilder, location.getWorld().getName());
        locBuilderClass.getMethod("x", int.class).invoke(locBuilder, location.getBlockX());
        locBuilderClass.getMethod("y", int.class).invoke(locBuilder, location.getBlockY());
        locBuilderClass.getMethod("z", int.class).invoke(locBuilder, location.getBlockZ());
        Object apolloLocation = locBuilderClass.getMethod("build").invoke(locBuilder);

        Class<?> waypointClass = Class.forName("com.lunarclient.apollo.module.waypoint.Waypoint");
        Object builder = waypointClass.getMethod("builder").invoke(null);
        Class<?> builderClass = builder.getClass();
        builderClass.getMethod("name", String.class).invoke(builder, RALLY_WAYPOINT_NAME);
        builderClass.getMethod("location", blockLocClass).invoke(builder, apolloLocation);
        builderClass.getMethod("color", Color.class).invoke(builder, Color.ORANGE);
        builderClass.getMethod("preventRemoval", boolean.class).invoke(builder, true);
        builderClass.getMethod("hidden", boolean.class).invoke(builder, false);
        try {
            builderClass.getMethod("showBeam", boolean.class).invoke(builder, true);
            builderClass.getMethod("highlightBlock", boolean.class).invoke(builder, true);
        } catch (NoSuchMethodException ignored) {
        }
        return builderClass.getMethod("build").invoke(builder);
    }

    private void withApolloPlayer(Player player, ApolloCallback callback, String action) {
        try {
            Object optional = getPlayerMethod.invoke(playerManager, player.getUniqueId());
            if (!(optional instanceof Optional)) {
                return;
            }
            Optional<?> opt = (Optional<?>) optional;
            if (!opt.isPresent()) {
                schedulePlayerRetry(player, 20L);
                schedulePlayerRetry(player, 60L);
                schedulePlayerRetry(player, 100L);
                logDebug(action + " Rally: " + player.getName() + " pas encore Lunar.");
                return;
            }
            Object apolloPlayer = opt.get();
            if (!apolloPlayerClass.isInstance(apolloPlayer)) {
                return;
            }
            callback.run(apolloPlayer);
        } catch (Throwable ex) {
            plugin.getLogger().warning("Rally Lunar pour " + player.getName() + ": " + ex.getMessage());
            if (debug) {
                ex.printStackTrace();
            }
        }
    }

    private void schedulePlayerRetry(final Player player, long delay) {
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    syncPlayer(player);
                }
            }
        }, delay);
    }

    private void logDebug(String message) {
        if (debug) {
            plugin.getLogger().info("[Rally Lunar] " + message);
        }
    }

    @org.bukkit.event.EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                syncPlayer(event.getPlayer());
            }
        }, 60L);
    }

    @org.bukkit.event.EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastSent.remove(event.getPlayer().getUniqueId());
        removeRally(event.getPlayer());
    }

    public void clearFactionRally(String fac) {
        factionRallyKeys.remove(fac);
        for (String raw : factions.members(fac)) {
            try {
                lastSent.remove(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private interface ApolloCallback {
        void run(Object apolloPlayer) throws Exception;
    }
}
