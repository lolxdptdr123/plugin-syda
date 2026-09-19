package fr.draftmc.tournament;

import fr.draftmc.Draftmc;

public class TournamentPlugin {
    private final TournamentManager manager;
    private final TournamentCommand command;

    public TournamentPlugin(Draftmc host) {
        this.manager = new TournamentManager(host);
        this.command = new TournamentCommand(manager);
        if (host.getCommand("tournament") != null) {
            host.getCommand("tournament").setExecutor(command);
            host.getCommand("tournament").setTabCompleter(command);
        } else {
            host.getLogger().warning("[Tournament] Commande /tournament absente de plugin.yml");
        }
        if (host.getCommand("status") != null) {
            host.getCommand("status").setExecutor(command);
            host.getCommand("status").setTabCompleter(command);
        }
        host.getLogger().info("[Tournament] Module chargé (tournament.yml).");
    }

    public TournamentManager manager() {
        return manager;
    }

    public void disable() {
        manager.disable();
    }
}
