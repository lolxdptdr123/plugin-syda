package fr.draftmc.deathban;

import fr.draftmc.Draftmc;
import fr.draftmc.deathban.command.DeathBanCommand;
import fr.draftmc.deathban.listener.CommandRestrictListener;
import fr.draftmc.deathban.listener.DeathListener;
import fr.draftmc.deathban.listener.JoinListener;
import fr.draftmc.deathban.listener.TeleportListener;
import fr.draftmc.deathban.manager.BanManager;
import fr.draftmc.deathban.manager.ConfigManager;
import fr.draftmc.events.EventModule;
import org.bukkit.command.PluginCommand;

public final class DeathBanPlugin extends EventModule {

    private ConfigManager configManager;
    private BanManager banManager;

    public DeathBanPlugin(Draftmc host) {
        super(host, "deathban", "deathban.yml");
        enable();
    }

    private void enable() {
        this.configManager = new ConfigManager(this);
        this.banManager = new BanManager(this);
        this.banManager.load();

        getServer().getPluginManager().registerEvents(new DeathListener(this), getHost());
        getServer().getPluginManager().registerEvents(new TeleportListener(this), getHost());
        getServer().getPluginManager().registerEvents(new CommandRestrictListener(this), getHost());
        getServer().getPluginManager().registerEvents(new JoinListener(this), getHost());

        DeathBanCommand command = new DeathBanCommand(this);
        PluginCommand cmd = getCommand("deathban");
        if (cmd != null) {
            cmd.setExecutor(command);
            cmd.setTabCompleter(command);
        }

        getLogger().info("[DeathBan] Charge (" + configManager.getWorlds().size()
                + " monde(s), config deathban.yml).");
    }

    @Override
    public void disable() {
        if (banManager != null) {
            banManager.save();
        }
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public BanManager getBanManager() {
        return banManager;
    }
}
