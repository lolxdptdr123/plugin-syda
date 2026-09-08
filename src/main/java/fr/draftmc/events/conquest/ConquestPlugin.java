package fr.draftmc.events.conquest;

import fr.draftmc.Draftmc;
import fr.draftmc.events.EventFactionHook;
import fr.draftmc.events.EventModule;
import fr.draftmc.events.conquest.managers.ActionBarManager;
import fr.draftmc.events.conquest.managers.BossBarManager;
import fr.draftmc.events.conquest.managers.CaptureManager;
import fr.draftmc.events.conquest.managers.CommandManager;
import fr.draftmc.events.conquest.managers.ConquestManager;
import fr.draftmc.events.conquest.managers.HologramManager;
import fr.draftmc.events.conquest.managers.ListenerManager;
import fr.draftmc.events.conquest.managers.MessageManager;
import fr.draftmc.events.conquest.managers.ParticleManager;
import fr.draftmc.events.conquest.managers.ScoreboardManager;
import fr.draftmc.events.conquest.managers.StorageManager;
import fr.draftmc.events.conquest.managers.ZoneManager;
import fr.draftmc.events.conquest.model.ConquestState;

public class ConquestPlugin extends EventModule {

    private EventFactionHook factionHook;
    private MessageManager messageManager;
    private ZoneManager zoneManager;
    private ActionBarManager actionBarManager;
    private CaptureManager captureManager;
    private ConquestManager conquestManager;
    private StorageManager storageManager;
    private ScoreboardManager scoreboardManager;
    private BossBarManager bossBarManager;
    private HologramManager hologramManager;
    private ParticleManager particleManager;

    public ConquestPlugin(Draftmc host) {
        super(host, "conquest", "conquest.yml");
        enable();
    }

    private void enable() {
        factionHook = new EventFactionHook(getHost());
        messageManager = new MessageManager(this);
        zoneManager = new ZoneManager(this);
        actionBarManager = new ActionBarManager(this);
        captureManager = new CaptureManager(this);
        conquestManager = new ConquestManager(this);
        storageManager = new StorageManager(this);
        scoreboardManager = new ScoreboardManager(this);
        bossBarManager = new BossBarManager(this);
        hologramManager = new HologramManager(this);
        particleManager = new ParticleManager(this);

        new ListenerManager(this).registerAll();
        new CommandManager(this).registerCommands();

        storageManager.load();
        getLogger().info("[Conquest] Event charge (conquest.yml).");
    }

    @Override
    public void disable() {
        if (storageManager != null) {
            storageManager.saveSync();
        }
        if (conquestManager != null && conquestManager.getState() == ConquestState.RUNNING) {
            if (captureManager != null) captureManager.stop();
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

    public CaptureManager getCaptureManager() {
        return captureManager;
    }

    public ConquestManager getConquestManager() {
        return conquestManager;
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
