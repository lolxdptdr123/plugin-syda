package fr.draftmc.tpa;

import fr.draftmc.Draftmc;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TpaManager implements CommandExecutor, TabCompleter, Listener {
    private final Draftmc plugin;
    private final Map<UUID, TpaRequest> incoming = new ConcurrentHashMap<UUID, TpaRequest>();

    public TpaManager(Draftmc plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                expireOld();
            }
        }, 20L, 20L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cJoueur uniquement.");
            return true;
        }
        Player player = (Player) sender;
        String name = command.getName().toLowerCase(Locale.ROOT);
        if ("tpa".equals(name)) {
            request(player, args, false);
        } else if ("tpahere".equals(name)) {
            request(player, args, true);
        } else if ("tpyes".equals(name) || "tpaccept".equals(name)) {
            accept(player);
        } else {
            deny(player);
        }
        return true;
    }

    private void request(Player from, String[] args, boolean here) {
        if (plugin.combat() != null && plugin.combat().denyIfTagged(from)) {
            return;
        }
        if (!from.hasPermission("draftmc.tpa") && !from.hasPermission("draftmc.admin")) {
            plugin.msg(from, "&cPas la permission.");
            return;
        }
        if (plugin.freeze() != null && plugin.freeze().isFrozen(from)) {
            plugin.msg(from, "&cTu ne peux pas faire ça en freeze.");
            return;
        }
        if (args.length < 1) {
            plugin.msg(from, here ? "&e/tpahere <joueur>" : "&e/tpa <joueur>");
            return;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            plugin.msg(from, "&cJoueur introuvable.");
            return;
        }
        if (target.equals(from)) {
            plugin.msg(from, "&cTu ne peux pas te téléporter à toi-même.");
            return;
        }
        if (plugin.combat() != null && plugin.combat().isTagged(target)) {
            plugin.msg(from, "&cCe joueur est en combat.");
            return;
        }
        if (plugin.denyTpCooldown(from)) {
            return;
        }
        int expire = Math.max(5, plugin.getConfig().getInt("tpa.expire-seconds", 60));
        TpaRequest request = new TpaRequest(from.getUniqueId(), here,
                System.currentTimeMillis() + expire * 1000L);
        incoming.put(target.getUniqueId(), request);
        plugin.startTpCooldown(from);
        if (here) {
            plugin.msg(from, "&aDemande envoyée à &e" + target.getName() + "&a pour qu'il vienne à toi.");
            plugin.msg(target, "&e" + from.getName() + " &7veut que tu te téléportes à lui.");
        } else {
            plugin.msg(from, "&aDemande de téléportation envoyée à &e" + target.getName() + "&a.");
            plugin.msg(target, "&e" + from.getName() + " &7veut se téléporter à toi.");
        }
        plugin.msg(target, "&a/tpyes &7pour accepter &8| &c/tpdeny &7pour refuser &8(&e" + expire + "s&8)");
    }

    private void accept(Player player) {
        TpaRequest request = incoming.remove(player.getUniqueId());
        if (request == null || request.expired()) {
            plugin.msg(player, "&cAucune demande de téléportation.");
            return;
        }
        Player from = Bukkit.getPlayer(request.from);
        if (from == null || !from.isOnline()) {
            plugin.msg(player, "&cLe joueur n'est plus connecté.");
            return;
        }
        Player moving = request.here ? player : from;
        Player dest = request.here ? from : player;
        if (plugin.combat() != null && (plugin.combat().isTagged(moving) || plugin.combat().isTagged(dest))) {
            if (plugin.combat().isTagged(player)) {
                plugin.combat().denyIfTagged(player);
            } else {
                plugin.msg(player, "&cImpossible : un des joueurs est en combat.");
            }
            incoming.put(player.getUniqueId(), request);
            return;
        }
        if (plugin.denyTpCooldown(moving)) {
            incoming.put(player.getUniqueId(), request);
            return;
        }
        plugin.data().setString(moving.getUniqueId(), "back_location",
                fr.draftmc.util.Locations.serialize(moving.getLocation()));
        moving.teleport(dest);
        plugin.startTpCooldown(moving);
        plugin.msg(moving, "&aTéléporté vers &e" + dest.getName() + "&a.");
        plugin.msg(dest, "&e" + moving.getName() + " &as'est téléporté.");
    }

    private void deny(Player player) {
        TpaRequest request = incoming.remove(player.getUniqueId());
        if (request == null || request.expired()) {
            plugin.msg(player, "&cAucune demande de téléportation.");
            return;
        }
        Player from = Bukkit.getPlayer(request.from);
        plugin.msg(player, "&cDemande refusée.");
        if (from != null && from.isOnline()) {
            plugin.msg(from, "&c" + player.getName() + " a refusé ta demande.");
        }
    }

    public void cancelFor(Player player, boolean notify) {
        incoming.remove(player.getUniqueId());
        Iterator<Map.Entry<UUID, TpaRequest>> it = incoming.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, TpaRequest> entry = it.next();
            if (entry.getValue().from.equals(player.getUniqueId())) {
                it.remove();
                if (notify) {
                    Player target = Bukkit.getPlayer(entry.getKey());
                    if (target != null && target.isOnline()) {
                        plugin.msg(target, "&cLa demande de &e" + player.getName() + " &ca été annulée.");
                    }
                }
            }
        }
    }

    private void expireOld() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, TpaRequest>> it = incoming.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, TpaRequest> entry = it.next();
            if (entry.getValue().expireAt <= now) {
                it.remove();
                Player target = Bukkit.getPlayer(entry.getKey());
                Player from = Bukkit.getPlayer(entry.getValue().from);
                if (target != null && target.isOnline()) {
                    plugin.msg(target, "&7Demande de téléportation expirée.");
                }
                if (from != null && from.isOnline()) {
                    plugin.msg(from, "&cTa demande de téléportation a expiré.");
                }
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancelFor(event.getPlayer(), false);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if ((!"tpa".equals(name) && !"tpahere".equals(name)) || args.length != 1) {
            return Collections.emptyList();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<String>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                out.add(online.getName());
            }
        }
        return out;
    }

    private static final class TpaRequest {
        private final UUID from;
        private final boolean here;
        private final long expireAt;

        private TpaRequest(UUID from, boolean here, long expireAt) {
            this.from = from;
            this.here = here;
            this.expireAt = expireAt;
        }

        private boolean expired() {
            return System.currentTimeMillis() > expireAt;
        }
    }
}
