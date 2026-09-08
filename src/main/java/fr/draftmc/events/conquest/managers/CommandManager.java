package fr.draftmc.events.conquest.managers;

import fr.draftmc.events.conquest.ConquestPlugin;
import fr.draftmc.events.conquest.commands.ConquestCommand;

/** Enregistre la commande /conquest et son tab-completer. */
public class CommandManager {

    private final ConquestPlugin plugin;

    public CommandManager(ConquestPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerCommands() {
        ConquestCommand command = new ConquestCommand(plugin);
        if (plugin.getCommand("conquest") == null) {
            plugin.getLogger().warning("[Conquest] Commande /conquest absente de plugin.yml");
            return;
        }
        plugin.getCommand("conquest").setExecutor(command);
        plugin.getCommand("conquest").setTabCompleter(command);
    }
}
