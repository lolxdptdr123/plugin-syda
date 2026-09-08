package fr.draftmc.events;

import fr.draftmc.Draftmc;
import org.bukkit.Server;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * Module event/systeme heberge par Draftmc : meme API que JavaPlugin
 * (getConfig, getDataFolder, getLogger...) mais config YAML separee.
 */
public abstract class EventModule {
    private final Draftmc host;
    private final String moduleId;
    private final File dataFolder;
    private final File configFile;
    private FileConfiguration config;

    protected EventModule(Draftmc host, String moduleId, String configResource) {
        this.host = host;
        this.moduleId = moduleId;
        this.dataFolder = new File(host.getDataFolder(), "events/" + moduleId);
        this.dataFolder.mkdirs();
        this.configFile = new File(host.getDataFolder(), configResource);
        if (!configFile.exists() && host.getResource(configResource) != null) {
            host.saveResource(configResource, false);
        }
        reloadConfig();
    }

    public Draftmc getHost() {
        return host;
    }

    public JavaPlugin getJavaPlugin() {
        return host;
    }

    public String getModuleId() {
        return moduleId;
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public void reloadConfig() {
        this.config = YamlConfiguration.loadConfiguration(configFile);
        InputStream def = host.getResource(configFile.getName());
        if (def != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(def, StandardCharsets.UTF_8));
            config.setDefaults(defaults);
        }
    }

    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            getLogger().warning("[" + moduleId + "] Impossible de sauvegarder "
                    + configFile.getName() + " : " + e.getMessage());
        }
    }

    public void saveDefaultConfig() {
        if (!configFile.exists() && host.getResource(configFile.getName()) != null) {
            host.saveResource(configFile.getName(), false);
        }
    }

    public Logger getLogger() {
        return host.getLogger();
    }

    public File getDataFolder() {
        return dataFolder;
    }

    public Server getServer() {
        return host.getServer();
    }

    public PluginCommand getCommand(String name) {
        return host.getCommand(name);
    }

    public InputStream getResource(String name) {
        return host.getResource(name);
    }

    public abstract void disable();
}
