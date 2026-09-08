package fr.draftmc.events.battleroyal.listeners;

import fr.draftmc.events.battleroyal.BattleRoyal;
import fr.draftmc.events.battleroyal.model.GameState;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class PlayerListener implements Listener {

    private final BattleRoyal plugin;

    public PlayerListener(BattleRoyal plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (plugin.getGameManager().getState() != GameState.INGAME) return;

        // Le stuff ne doit jamais tomber au sol pendant le BR (evite le
        // pillage de cadavres et les items perdus si personne ne les ramasse
        // avant la fin de la manche).
        event.getDrops().clear();
        event.setDroppedExp(0);

        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        plugin.getGameManager().onPlayerEliminated(victim, killer);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (plugin.getGameManager().getState() == GameState.INGAME) {
            // event.setRespawnLocation() plutot qu'un teleport() apres coup :
            // la teleportation fait partie de l'evenement de respawn lui-meme,
            // donc pas de frame intermediaire au point de mort ni de second
            // deplacement a gerer.
            Location spawn = plugin.getGameManager().resolveSpawnLocation();
            if (spawn != null) {
                event.setRespawnLocation(spawn);
            }
            event.getPlayer().setGameMode(GameMode.SURVIVAL);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (plugin.getGameManager().getState() == GameState.INGAME) {
            // Une deconnexion en pleine partie compte comme une elimination
            plugin.getGameManager().onPlayerEliminated(event.getPlayer(), null);
        }
    }

    /**
     * Anti-commande : bloque TOUTES les commandes (y compris celles d'autres
     * plugins) pour un joueur physiquement present dans la zone du BR
     * pendant une partie en cours. Les admins (permission battleroyal.admin)
     * gardent l'usage des commandes, pour pouvoir gerer la partie meme s'ils
     * se teleportent dans la zone.
     */
    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getConfig().getBoolean("command-block.enabled", true)) return;
        if (player.hasPermission("battleroyal.admin") || player.hasPermission("draftmc.admin")) return;

        if (plugin.getGameManager().isInsideArena(player)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Les commandes sont desactivees tant que tu es dans la zone du Battle Royale.");
        }
    }
}
