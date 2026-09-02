package fr.sydaria.util;

import fr.sydaria.Sydaria;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public class YamlFile {
    private final File file;
    private FileConfiguration config;

    public YamlFile(Sydaria plugin, String name) {
        plugin.getDataFolder().mkdirs();
        this.file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            try {
                if (plugin.getResource(name) != null) {
                    plugin.saveResource(name, false);
                } else {
                    file.createNewFile();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        reload();
    }

    public void reload() {
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public FileConfiguration get() {
        return config;
    }
}
