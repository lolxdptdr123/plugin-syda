package fr.draftmc.social;

import fr.draftmc.Draftmc;
import fr.draftmc.util.CC;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class MsgCommand implements CommandExecutor, TabCompleter, Listener {
    private static final String[] ALIASES = {
            "msg", "m", "tell", "whisper", "w", "t", "message", "pm", "r", "reply"
    };
    private final Draftmc plugin;
    private final Map<UUID, UUID> last = new HashMap<UUID, UUID>();

    public MsgCommand(Draftmc plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String raw = event.getMessage();
        if (raw.length() < 2 || raw.charAt(0) != '/') {
            return;
        }
        int space = raw.indexOf(' ');
        String label = space < 0 ? raw.substring(1) : raw.substring(1, space);
        if (label.contains(":")) {
            label = label.substring(label.indexOf(':') + 1);
        }
        String lower = label.toLowerCase(Locale.ROOT);
        boolean match = false;
        for (int i = 0; i < ALIASES.length; i++) {
            if (ALIASES[i].equals(lower)) {
                match = true;
                break;
            }
        }
        if (!match || label.equals(lower)) {
            return;
        }
        String args = space < 0 ? "" : raw.substring(space);
        event.setMessage("/" + lower + args);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.msg(sender, "&cJoueur uniquement.");
            return true;
        }
        Player player = (Player) sender;
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("r") || name.equals("reply")) {
            return reply(player, args);
        }
        if (args.length < 2) {
            plugin.msg(player, "&e/" + label + " <joueur> <message>");
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            plugin.msg(player, "&cJoueur hors-ligne.");
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            plugin.msg(player, "&cTu ne peux pas te MP toi-même.");
            return true;
        }
        send(player, target, join(args, 1));
        return true;
    }

    private boolean reply(Player player, String[] args) {
        if (args.length < 1) {
            plugin.msg(player, "&e/r <message>");
            return true;
        }
        UUID lastId = last.get(player.getUniqueId());
        if (lastId == null) {
            plugin.msg(player, "&cPersonne à qui répondre.");
            return true;
        }
        Player target = Bukkit.getPlayer(lastId);
        if (target == null || !target.isOnline()) {
            plugin.msg(player, "&cJoueur hors-ligne.");
            return true;
        }
        send(player, target, join(args, 0));
        return true;
    }

    private void send(Player from, Player to, String message) {
        String outgoing = CC.color("&8[&eMoi &8→ &e" + to.getName() + "&8] &f") + message;
        String incoming = CC.color("&8[&e" + from.getName() + " &8→ &eMoi&8] &f") + message;
        from.sendMessage(outgoing);
        to.sendMessage(incoming);
        last.put(from.getUniqueId(), to.getUniqueId());
        last.put(to.getUniqueId(), from.getUniqueId());
    }

    private String join(String[] args, int start) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (i > start) {
                sb.append(' ');
            }
            sb.append(args[i]);
        }
        return sb.toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<String>();
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("r") || name.equals("reply") || args.length != 1) {
            return out;
        }
        String token = args[0].toLowerCase(Locale.ROOT);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(token)) {
                out.add(player.getName());
            }
        }
        return out;
    }
}
