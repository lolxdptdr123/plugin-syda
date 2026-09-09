package fr.draftmc.events.teamfight;

import fr.draftmc.Draftmc;
import fr.draftmc.events.EventFactionHook;
import fr.draftmc.events.EventModule;
import fr.draftmc.util.CC;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class TeamFightPlugin extends EventModule {
    private EventFactionHook factionHook;
    private TeamFightManager manager;
    private TeamFightKit kit;
    private TeamFightScoreboard scoreboard;

    public TeamFightPlugin(Draftmc host) {
        super(host, "teamfight", "teamfight.yml");
        enable();
    }

    private void enable() {
        factionHook = new EventFactionHook(getHost());
        kit = new TeamFightKit(this);
        manager = new TeamFightManager(this);
        scoreboard = new TeamFightScoreboard(this);
        getServer().getPluginManager().registerEvents(new TeamFightListener(this), getHost());
        TeamFightCommand command = new TeamFightCommand(this);
        if (getCommand("teamfight") != null) {
            getCommand("teamfight").setExecutor(command);
            getCommand("teamfight").setTabCompleter(command);
        } else {
            getLogger().warning("[TeamFight] Commande /teamfight absente de plugin.yml");
        }
        getLogger().info("[TeamFight] Event charge (teamfight.yml).");
    }

    @Override
    public void disable() {
        if (manager != null && manager.isBusy()) {
            manager.stop();
        }
    }

    public EventFactionHook getEventFactionHook() {
        return factionHook;
    }

    public TeamFightManager getManager() {
        return manager;
    }

    public TeamFightKit getKit() {
        return kit;
    }

    public TeamFightScoreboard getScoreboard() {
        return scoreboard;
    }

    public String prefix() {
        return CC.color(getConfig().getString("prefix", "&8[&6TeamFight&8] &7"));
    }

    public String msg(String key) {
        return getConfig().getString("messages." + key, "");
    }

    public void broadcast(String key, TfTeam team, Player player, int number) {
        String text = format(key, team, player, number);
        if (!text.isEmpty()) {
            Bukkit.broadcastMessage(prefix() + text);
        }
    }

    public void broadcastRaw(String raw) {
        if (raw != null && !raw.isEmpty()) {
            Bukkit.broadcastMessage(prefix() + CC.color(raw));
        }
    }

    public String format(String key, TfTeam team, Player player, int number) {
        String raw = msg(key);
        if (raw == null) {
            raw = "";
        }
        return CC.color(raw
                .replace("{team}", team == null ? "" : team.getName())
                .replace("{player}", player == null ? "" : player.getName())
                .replace("{leader}", team == null ? "" : String.valueOf(team.leaderName()))
                .replace("{count}", String.valueOf(number))
                .replace("{size}", String.valueOf(manager == null ? 8 : manager.rosterSize()))
                .replace("{min}", String.valueOf(manager == null ? 3 : manager.minRoster()))
                .replace("{alive}", String.valueOf(number))
                .replace("{seconds}", String.valueOf(number)));
    }
}
