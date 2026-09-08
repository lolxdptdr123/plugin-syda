package fr.draftmc.events.masterkill.listeners;

import fr.draftmc.events.masterkill.MasterKillPlugin;
import fr.draftmc.events.masterkill.model.MasterKillState;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

/** Un joueur elimine repart en mode spectateur tant que le match tourne. */
public class RespawnListener implements Listener {

    private final MasterKillPlugin plugin;

    public RespawnListener(MasterKillPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (plugin.getMasterKillManager().getState() == MasterKillState.RUNNING) {
            // Meme spawn que celui utilise en fin de match (config spawn.*),
            // fixe directement sur l'evenement de respawn plutot qu'un
            // teleport() apres coup : pas de frame intermediaire au point
            // de mort, pas de second deplacement a gerer.
            Location spawn = plugin.getMasterKillManager().resolveSpawnLocation();
            if (spawn != null) {
                event.setRespawnLocation(spawn);
            }
            event.getPlayer().setGameMode(GameMode.SURVIVAL);
        }
    }
}
