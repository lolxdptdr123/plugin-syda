package fr.draftmc.events.masterkill.managers;

import fr.draftmc.events.masterkill.MasterKillPlugin;
import fr.draftmc.events.masterkill.listeners.DeathListener;
import fr.draftmc.events.masterkill.listeners.QuitListener;
import fr.draftmc.events.masterkill.listeners.RespawnListener;

/** Enregistre tous les listeners Bukkit du plugin en un seul endroit. */
public class ListenerManager {

    private final MasterKillPlugin plugin;

    public ListenerManager(MasterKillPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerAll() {
        plugin.getServer().getPluginManager().registerEvents(new DeathListener(plugin), plugin.getHost());
        plugin.getServer().getPluginManager().registerEvents(new QuitListener(plugin), plugin.getHost());
        plugin.getServer().getPluginManager().registerEvents(new RespawnListener(plugin), plugin.getHost());
    }
}
