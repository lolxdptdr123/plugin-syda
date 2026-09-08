package fr.draftmc.events.conquest.managers;

import fr.draftmc.events.conquest.ConquestPlugin;
import fr.draftmc.events.conquest.listeners.DeathListener;
import fr.draftmc.events.conquest.listeners.JoinListener;
import fr.draftmc.events.conquest.listeners.MoveListener;
import fr.draftmc.events.conquest.listeners.QuitListener;
import fr.draftmc.events.conquest.listeners.RespawnListener;
import fr.draftmc.events.conquest.listeners.TeleportListener;

/** Enregistre tous les listeners Bukkit du plugin en un seul endroit. */
public class ListenerManager {

    private final ConquestPlugin plugin;

    public ListenerManager(ConquestPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerAll() {
        plugin.getServer().getPluginManager().registerEvents(new JoinListener(plugin), plugin.getHost());
        plugin.getServer().getPluginManager().registerEvents(new QuitListener(plugin), plugin.getHost());
        plugin.getServer().getPluginManager().registerEvents(new MoveListener(plugin), plugin.getHost());
        plugin.getServer().getPluginManager().registerEvents(new DeathListener(plugin), plugin.getHost());
        plugin.getServer().getPluginManager().registerEvents(new TeleportListener(plugin), plugin.getHost());
        plugin.getServer().getPluginManager().registerEvents(new RespawnListener(plugin), plugin.getHost());
    }
}
