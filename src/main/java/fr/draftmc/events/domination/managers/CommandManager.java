package fr.draftmc.events.domination.managers;

import fr.draftmc.events.domination.DominationPlugin;
import fr.draftmc.events.domination.commands.DominationCommand;

/** Enregistre la commande /domination et son tab-completer. */
public class CommandManager {

    private final DominationPlugin plugin;

    public CommandManager(DominationPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerCommands() {
        DominationCommand command = new DominationCommand(plugin);
        if (plugin.getCommand("domination") == null) {
            plugin.getLogger().warning("[Domination] Commande /domination absente de plugin.yml");
            return;
        }
        plugin.getCommand("domination").setExecutor(command);
        plugin.getCommand("domination").setTabCompleter(command);
    }
}
