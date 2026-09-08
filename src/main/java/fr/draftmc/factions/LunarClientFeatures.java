package fr.draftmc.factions;

import fr.draftmc.Draftmc;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.awt.Color;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * TeamView Lunar (marqueurs de faction) et activation des cosmetiques Lunar.
 * Reflection sur Apollo, sans dependance Maven.
 */
public class LunarClientFeatures implements Listener {
    private final Draftmc plugin;
    private final FactionManager factions;
    private boolean enabled;
    private Object playerManager;
    private Object teamModule;
    private Object modSettingModule;
    private Object serverRuleModule;
    private Method getPlayerMethod;
    private Class<?> apolloPlayerClass;
    private Method updateTeamMembersMethod;
    private Method resetTeamMembersMethod;
    private boolean recipientsWrap;
    private Class<?> recipientsClass;
    private Object teamViewEnabledOption;
    private Object apolloTeamsOption;

    public LunarClientFeatures(Draftmc plugin, FactionManager factions) {
        this.plugin = plugin;
        this.factions = factions;
        init();
        if (enabled) {
            long interval = plugin.getConfig().getLong("factions.lunar.update-ticks", 5L);
            Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
                @Override
                public void run() {
                    tick();
                }
            }, interval, interval);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    private void init() {
        if (!plugin.getConfig().getBoolean("factions.lunar.enabled", true)) {
            return;
        }
        Plugin apollo = findApolloPlugin();
        if (apollo == null) {
            plugin.getLogger().info("Lunar: installe Apollo-Bukkit pour TeamView et les cosmetiques.");
            return;
        }
        try {
            Class<?> apolloClass = Class.forName("com.lunarclient.apollo.Apollo");
            Object moduleManager = apolloClass.getMethod("getModuleManager").invoke(null);
            playerManager = apolloClass.getMethod("getPlayerManager").invoke(null);
            getPlayerMethod = playerManager.getClass().getMethod("getPlayer", UUID.class);
            apolloPlayerClass = Class.forName("com.lunarclient.apollo.player.ApolloPlayer");

            if (plugin.getConfig().getBoolean("factions.lunar.teamview", true)) {
                Class<?> teamModuleClass = Class.forName("com.lunarclient.apollo.module.team.TeamModule");
                teamModule = moduleManager.getClass().getMethod("getModule", Class.class)
                        .invoke(moduleManager, teamModuleClass);
                resolveTeamMethods(teamModuleClass);
                loadTeamViewOptions();
            }

            try {
                Class<?> modClass = Class.forName("com.lunarclient.apollo.module.modsetting.ModSettingModule");
                modSettingModule = moduleManager.getClass().getMethod("getModule", Class.class)
                        .invoke(moduleManager, modClass);
            } catch (Throwable ignored) {
            }

            try {
                Class<?> ruleClass = Class.forName("com.lunarclient.apollo.module.serverrule.ServerRuleModule");
                serverRuleModule = moduleManager.getClass().getMethod("getModule", Class.class)
                        .invoke(moduleManager, ruleClass);
            } catch (Throwable ignored) {
            }

            if (plugin.getConfig().getBoolean("factions.lunar.cosmetics", true)) {
                enableCosmetics();
            }

            enabled = teamModule != null || modSettingModule != null || serverRuleModule != null;
            if (enabled) {
                plugin.getLogger().info("Lunar: TeamView/cosmetiques actifs (Apollo).");
            }
        } catch (Throwable ex) {
            plugin.getLogger().warning("Lunar features: " + ex.getMessage());
        }
    }

    private void resolveTeamMethods(Class<?> teamModuleClass) {
        for (Method method : teamModuleClass.getMethods()) {
            if (method.getParameterTypes().length != 2) {
                continue;
            }
            if ("updateTeamMembers".equals(method.getName()) && List.class.isAssignableFrom(method.getParameterTypes()[1])) {
                updateTeamMembersMethod = method;
                recipientsWrap = method.getParameterTypes()[0].getName().contains("Recipients");
                if (recipientsWrap) {
                    recipientsClass = method.getParameterTypes()[0];
                }
            }
            if ("resetTeamMembers".equals(method.getName())) {
                resetTeamMembersMethod = method;
            }
        }
    }

    private void loadTeamViewOptions() {
        try {
            Class<?> mod = Class.forName("com.lunarclient.apollo.mods.impl.ModTeamView");
            teamViewEnabledOption = mod.getField("ENABLED").get(null);
            try {
                apolloTeamsOption = mod.getField("APOLLO_TEAMS").get(null);
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
    }

    private void enableCosmetics() {
        if (serverRuleModule != null) {
            try {
                Class<?> ruleClass = Class.forName("com.lunarclient.apollo.module.serverrule.ServerRuleModule");
                Object options = serverRuleModule.getClass().getMethod("getOptions").invoke(serverRuleModule);
                Method set = findOptionsSet(options.getClass(), 2);
                for (Field field : ruleClass.getFields()) {
                    String name = field.getName().toUpperCase();
                    if (name.contains("COSMETIC")) {
                        boolean disable = name.contains("DISABLE") || name.contains("HIDE") || name.contains("FORCE_OFF");
                        if (set != null) {
                            set.invoke(options, field.get(null), Boolean.valueOf(!disable));
                        }
                    }
                    if (name.equals("COMPETITIVE_GAME") && set != null) {
                        set.invoke(options, field.get(null), Boolean.FALSE);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private void tick() {
        if (!enabled || teamModule == null || updateTeamMembersMethod == null) {
            return;
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            syncViewer(viewer);
        }
    }

    private void syncViewer(Player viewer) {
        Object apolloPlayer = apolloPlayer(viewer);
        if (apolloPlayer == null) {
            return;
        }
        enableTeamViewMod(apolloPlayer);
        String fac = factions.factionOf(viewer);
        if (fac == null || fac.isEmpty()) {
            resetTeam(apolloPlayer);
            return;
        }
        List<Object> members = new ArrayList<Object>();
        for (String raw : factions.members(fac)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(raw);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            if (uuid.equals(viewer.getUniqueId())) {
                continue;
            }
            Player member = Bukkit.getPlayer(uuid);
            if (member == null || !member.isOnline() || member.isDead()) {
                continue;
            }
            if (!viewer.getWorld().equals(member.getWorld())) {
                continue;
            }
            Object built = buildTeamMember(member);
            if (built != null) {
                members.add(built);
            }
        }
        try {
            Object target = wrapRecipients(apolloPlayer);
            updateTeamMembersMethod.invoke(teamModule, target, members);
        } catch (Throwable ignored) {
        }
    }

    private void enableTeamViewMod(Object apolloPlayer) {
        if (modSettingModule == null || teamViewEnabledOption == null) {
            return;
        }
        try {
            Object options = modSettingModule.getClass().getMethod("getOptions").invoke(modSettingModule);
            Method set = findOptionsSet(options.getClass(), 3);
            if (set != null) {
                set.invoke(options, apolloPlayer, teamViewEnabledOption, Boolean.TRUE);
                if (apolloTeamsOption != null) {
                    set.invoke(options, apolloPlayer, apolloTeamsOption, Boolean.TRUE);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private Object buildTeamMember(Player member) {
        try {
            Class<?> teamMemberClass = Class.forName("com.lunarclient.apollo.module.team.TeamMember");
            Object builder = teamMemberClass.getMethod("builder").invoke(null);
            Class<?> builderClass = builder.getClass();
            builderClass.getMethod("playerUuid", UUID.class).invoke(builder, member.getUniqueId());
            builderClass.getMethod("markerColor", Color.class).invoke(builder, new Color(85, 255, 85));
            try {
                builderClass.getMethod("location", Class.forName("com.lunarclient.apollo.common.location.ApolloLocation"))
                        .invoke(builder, buildLocation(member.getLocation()));
            } catch (Throwable ignored) {
            }
            try {
                Class<?> componentClass = Class.forName("net.kyori.adventure.text.Component");
                Object component = componentClass.getMethod("text", String.class).invoke(null, member.getName());
                builderClass.getMethod("displayName", componentClass).invoke(builder, component);
            } catch (Throwable ignored) {
            }
            return builderClass.getMethod("build").invoke(builder);
        } catch (Throwable ex) {
            return null;
        }
    }

    private Object buildLocation(Location location) throws Exception {
        Class<?> locClass = Class.forName("com.lunarclient.apollo.common.location.ApolloLocation");
        Object builder = locClass.getMethod("builder").invoke(null);
        Class<?> builderClass = builder.getClass();
        builderClass.getMethod("world", String.class).invoke(builder, location.getWorld().getName());
        builderClass.getMethod("x", double.class).invoke(builder, location.getX());
        builderClass.getMethod("y", double.class).invoke(builder, location.getY());
        builderClass.getMethod("z", double.class).invoke(builder, location.getZ());
        return builderClass.getMethod("build").invoke(builder);
    }

    private void resetTeam(Object apolloPlayer) {
        if (resetTeamMembersMethod == null) {
            return;
        }
        try {
            Object target = wrapRecipients(apolloPlayer);
            resetTeamMembersMethod.invoke(teamModule, target);
        } catch (Throwable ignored) {
        }
    }

    private Object wrapRecipients(Object apolloPlayer) throws Exception {
        if (!recipientsWrap || recipientsClass == null) {
            return apolloPlayer;
        }
        try {
            return recipientsClass.getMethod("of", apolloPlayerClass).invoke(null, apolloPlayer);
        } catch (NoSuchMethodException ignored) {
        }
        for (Method method : recipientsClass.getMethods()) {
            if (!"of".equals(method.getName()) || method.getParameterTypes().length != 1) {
                continue;
            }
            if (method.getParameterTypes()[0].isInstance(apolloPlayer)) {
                return method.invoke(null, apolloPlayer);
            }
        }
        return apolloPlayer;
    }

    private Object apolloPlayer(Player player) {
        try {
            Object optional = getPlayerMethod.invoke(playerManager, player.getUniqueId());
            if (!(optional instanceof Optional)) {
                return null;
            }
            Optional<?> opt = (Optional<?>) optional;
            if (!opt.isPresent()) {
                return null;
            }
            Object value = opt.get();
            return apolloPlayerClass.isInstance(value) ? value : null;
        } catch (Throwable ex) {
            return null;
        }
    }

    private static Method findOptionsSet(Class<?> optionsClass, int params) {
        for (Method method : optionsClass.getMethods()) {
            if ("set".equals(method.getName()) && method.getParameterTypes().length == params) {
                return method;
            }
        }
        return null;
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

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (!enabled || teamModule == null) {
            return;
        }
        Object apolloPlayer = apolloPlayer(event.getPlayer());
        if (apolloPlayer != null) {
            resetTeam(apolloPlayer);
        }
    }
}
