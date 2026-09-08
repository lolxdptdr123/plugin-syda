package fr.draftmc.events.masterkill;

import fr.draftmc.Draftmc;
import fr.draftmc.events.EventFactionHook;
import fr.draftmc.events.EventModule;
import fr.draftmc.events.masterkill.managers.ArenaManager;
import fr.draftmc.events.masterkill.managers.CommandManager;
import fr.draftmc.events.masterkill.managers.KillManager;
import fr.draftmc.events.masterkill.managers.KitManager;
import fr.draftmc.events.masterkill.managers.ListenerManager;
import fr.draftmc.events.masterkill.managers.MasterKillManager;
import fr.draftmc.events.masterkill.managers.MessageManager;
import fr.draftmc.events.masterkill.managers.ScoreboardManager;
import fr.draftmc.events.masterkill.managers.TeamManager;
import fr.draftmc.events.masterkill.model.MasterKillState;

public class MasterKillPlugin extends EventModule {

    private EventFactionHook factionHook;
    private MessageManager messageManager;
    private TeamManager teamManager;
    private KillManager killManager;
    private KitManager kitManager;
    private ArenaManager arenaManager;
    private MasterKillManager masterKillManager;
    private ScoreboardManager scoreboardManager;

    public MasterKillPlugin(Draftmc host) {
        super(host, "masterkill", "masterkill.yml");
        enable();
    }

    private void enable() {
        factionHook = new EventFactionHook(getHost());
        messageManager = new MessageManager(this);
        teamManager = new TeamManager(this);
        killManager = new KillManager();
        kitManager = new KitManager(this);
        arenaManager = new ArenaManager(this);
        masterKillManager = new MasterKillManager(this);
        scoreboardManager = new ScoreboardManager(this);

        new ListenerManager(this).registerAll();
        new CommandManager(this).registerCommands();

        getLogger().info("[MasterKill] Event charge (masterkill.yml).");
    }

    @Override
    public void disable() {
        if (masterKillManager != null && masterKillManager.getState() != MasterKillState.WAITING) {
            getLogger().info("[MasterKill] Un match etait en cours : il ne sera pas repris au redemarrage.");
        }
    }

    public EventFactionHook getEventFactionHook() {
        return factionHook;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public TeamManager getTeamManager() {
        return teamManager;
    }

    public KillManager getKillManager() {
        return killManager;
    }

    public KitManager getKitManager() {
        return kitManager;
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    public MasterKillManager getMasterKillManager() {
        return masterKillManager;
    }

    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }
}
