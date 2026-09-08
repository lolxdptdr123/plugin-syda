package fr.draftmc.events.battleroyal;

import fr.draftmc.Draftmc;
import fr.draftmc.events.EventFactionHook;
import fr.draftmc.events.EventModule;
import fr.draftmc.events.battleroyal.commands.BRCommand;
import fr.draftmc.events.battleroyal.listeners.PlayerListener;
import fr.draftmc.events.battleroyal.managers.BuffZoneManager;
import fr.draftmc.events.battleroyal.managers.GameManager;
import fr.draftmc.events.battleroyal.managers.KitManager;
import fr.draftmc.events.battleroyal.managers.PointsManager;
import fr.draftmc.events.battleroyal.managers.ScoreboardManager;
import fr.draftmc.events.battleroyal.managers.TeamManager;
import org.bukkit.command.PluginCommand;

public class BattleRoyal extends EventModule {

    private TeamManager teamManager;
    private PointsManager pointsManager;
    private ScoreboardManager scoreboardManager;
    private KitManager kitManager;
    private GameManager gameManager;
    private EventFactionHook factionHook;
    private BuffZoneManager buffZoneManager;

    public BattleRoyal(Draftmc host) {
        super(host, "battleroyal", "battleroyal.yml");
        enable();
    }

    private void enable() {
        boolean requireSameFaction = getConfig().getBoolean("factions.require-same-faction", true);

        teamManager = new TeamManager(requireSameFaction);
        pointsManager = new PointsManager(this);
        scoreboardManager = new ScoreboardManager(this);
        kitManager = new KitManager(this);
        gameManager = new GameManager(this, teamManager, pointsManager, scoreboardManager, kitManager);
        buffZoneManager = new BuffZoneManager(this);

        getServer().getPluginManager().registerEvents(new PlayerListener(this), getHost());

        BRCommand command = new BRCommand(this);
        PluginCommand cmd = getCommand("br");
        if (cmd != null) {
            cmd.setExecutor(command);
            cmd.setTabCompleter(command);
        }

        factionHook = new EventFactionHook(getHost());
        teamManager.setEventFactionHook(factionHook);

        getLogger().info("[Battleroyal] Event charge (battleroyal.yml).");
    }

    @Override
    public void disable() {
        if (pointsManager != null) {
            pointsManager.save();
        }
    }

    public TeamManager getTeamManager() {
        return teamManager;
    }

    public PointsManager getPointsManager() {
        return pointsManager;
    }

    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    public KitManager getKitManager() {
        return kitManager;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public EventFactionHook getEventFactionHook() {
        return factionHook;
    }

    public BuffZoneManager getBuffZoneManager() {
        return buffZoneManager;
    }
}
