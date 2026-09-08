package fr.draftmc.events.masterkill.managers;

import fr.draftmc.events.masterkill.MasterKillPlugin;
import fr.draftmc.events.masterkill.commands.MasterKillCommand;

/** Enregistre la commande /masterkill et son tab-completer. */
public class CommandManager {

    private final MasterKillPlugin plugin;

    public CommandManager(MasterKillPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerCommands() {
        MasterKillCommand command = new MasterKillCommand(plugin);
        if (plugin.getCommand("masterkill") == null) {
            plugin.getLogger().warning("[MasterKill] Commande /masterkill absente de plugin.yml");
            return;
        }
        plugin.getCommand("masterkill").setExecutor(command);
        plugin.getCommand("masterkill").setTabCompleter(command);
    }
}
