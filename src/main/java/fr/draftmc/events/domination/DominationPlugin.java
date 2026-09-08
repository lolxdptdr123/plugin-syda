package fr.draftmc.events.domination;

import fr.draftmc.Draftmc;
import fr.draftmc.events.EventFactionHook;
import fr.draftmc.events.EventModule;
import fr.draftmc.events.domination.managers.ActionBarManager;
import fr.draftmc.events.domination.managers.BossBarManager;
import fr.draftmc.events.domination.managers.CommandManager;
import fr.draftmc.events.domination.managers.DominationManager;
import fr.draftmc.events.domination.managers.HologramManager;
import fr.draftmc.events.domination.managers.ListenerManager;
import fr.draftmc.events.domination.managers.MessageManager;
import fr.draftmc.events.domination.managers.ParticleManager;
import fr.draftmc.events.domination.managers.ScoreboardManager;
import fr.draftmc.events.domination.managers.ScoringManager;
import fr.draftmc.events.domination.managers.StorageManager;
import fr.draftmc.events.domination.managers.ZoneManager;
import fr.draftmc.events.domination.model.DominationState;

public class DominationPlugin extends EventModule {

    private EventFactionHook factionHook;
    private MessageManager messageManager;
    private ZoneManager zoneManager;
    private ActionBarManager actionBarManager;
    private ScoringManager scoringManager;
    private DominationManager dominationManager;
    private StorageManager storageManager;
    private ScoreboardManager scoreboardManager;
    private BossBarManager bossBarManager;
    private HologramManager hologramManager;
    private ParticleManager particleManager;

    public DominationPlugin(Draftmc host) {
        super(host, "domination", "domination.yml");
        enable();
    }

    private void enable() {
        factionHook = new EventFactionHook(getHost());
        messageManager = new MessageManager(this);
        zoneManager = new ZoneManager(this);
        actionBarManager = new ActionBarManager(this);
        scoringManager = new ScoringManager(this);
        dominationManager = new DominationManager(this);
        storageManager = new StorageManager(this);
        scoreboardManager = new ScoreboardManager(this);
        bossBarManager = new BossBarManager(this);
        hologramManager = new HologramManager(this);
        particleManager = new ParticleManager(this);

        new ListenerManager(this).registerAll();
        new CommandManager(this).registerCommands();

        storageManager.load();
        getLogger().info("[Domination] Event charge (domination.yml).");
    }

    @Override
    public void disable() {
        if (storageManager != null) {
            storageManager.saveSync();
        }
        if (dominationManager != null && dominationManager.getState() == DominationState.RUNNING) {
            if (scoringManager != null) scoringManager.stop();
            if (bossBarManager != null) bossBarManager.stop();
            if (particleManager != null) particleManager.stop();
            if (hologramManager != null) hologramManager.stop();
            if (scoreboardManager != null) scoreboardManager.stop();
        }
    }

    public EventFactionHook getEventFactionHook() {
        return factionHook;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public ZoneManager getZoneManager() {
        return zoneManager;
    }

    public ActionBarManager getActionBarManager() {
        return actionBarManager;
    }

    public ScoringManager getScoringManager() {
        return scoringManager;
    }

    public DominationManager getDominationManager() {
        return dominationManager;
    }

    public StorageManager getStorageManager() {
        return storageManager;
    }

    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    public BossBarManager getBossBarManager() {
        return bossBarManager;
    }

    public HologramManager getHologramManager() {
        return hologramManager;
    }

    public ParticleManager getParticleManager() {
        return particleManager;
    }
}
