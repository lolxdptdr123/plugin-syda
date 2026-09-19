package fr.draftmc.events.largage;

import fr.draftmc.Draftmc;
import fr.draftmc.events.EventModule;
import fr.draftmc.util.CC;

public class LargagePlugin extends EventModule {
    private LargageManager manager;
    private LargageScoreboard scoreboard;

    public LargagePlugin(Draftmc host) {
        super(host, "largage", "largage.yml");
        enable();
    }

    private void enable() {
        manager = new LargageManager(this);
        scoreboard = new LargageScoreboard(this);
        getServer().getPluginManager().registerEvents(new LargageListener(this), getHost());
        LargageCommand command = new LargageCommand(this);
        if (getCommand("largage") != null) {
            getCommand("largage").setExecutor(command);
            getCommand("largage").setTabCompleter(command);
        } else {
            getLogger().warning("[Largage] Commande /largage absente de plugin.yml");
        }
        getLogger().info("[Largage] Event chargé (largage.yml).");
    }

    @Override
    public void disable() {
        if (manager != null && manager.running()) {
            manager.stop(false);
        }
        if (scoreboard != null) {
            scoreboard.stop();
        }
    }

    public LargageManager getManager() {
        return manager;
    }

    public LargageScoreboard getScoreboard() {
        return scoreboard;
    }

    public String prefix() {
        return CC.color(getConfig().getString("prefix", "&8[&6Largage&8] &7"));
    }
}
