package fr.sydaria.anticommand;

import fr.sydaria.Sydaria;
import fr.sydaria.util.CC;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AntiCommandListener implements Listener {
    private final Sydaria plugin;

    public AntiCommandListener(Sydaria plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.getConfig().getBoolean("anti-command.enabled", true)) {
            return;
        }
        if (event.getPlayer().hasPermission("sydaria.bypass.commands")) {
            return;
        }
        String msg = event.getMessage().substring(1).toLowerCase();
        String label = msg.split(" ")[0];
        Set<String> blocked = new HashSet<String>();
        List<String> list = plugin.getConfig().getStringList("anti-command.blocked");
        for (String s : list) {
            blocked.add(s.toLowerCase());
        }
        if (blocked.contains(label)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(CC.color(plugin.getConfig().getString("anti-command.message", "&cCommande désactivée.")));
        }
    }
}
