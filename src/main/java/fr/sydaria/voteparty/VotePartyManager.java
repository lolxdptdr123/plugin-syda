package fr.sydaria.voteparty;

import fr.sydaria.Sydaria;
import fr.sydaria.util.CC;
import fr.sydaria.util.YamlFile;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

import java.lang.reflect.Method;

public class VotePartyManager implements CommandExecutor, Listener {
    private final Sydaria plugin;
    private final YamlFile file;

    public VotePartyManager(Sydaria plugin) {
        this.plugin = plugin;
        this.file = new YamlFile(plugin, "voteparty.yml");
        if (!file.get().contains("current")) {
            file.get().set("current", 0);
            file.save();
        }
        hookVotifier();
    }

    @SuppressWarnings("unchecked")
    private void hookVotifier() {
        try {
            Class<? extends Event> clazz = (Class<? extends Event>) Class.forName("com.vexsoftware.votifier.model.VotifierEvent");
            Bukkit.getPluginManager().registerEvent(clazz, this, EventPriority.NORMAL, new EventExecutor() {
                @Override
                public void execute(Listener listener, Event event) throws EventException {
                    add(1);
                    try {
                        Object vote = event.getClass().getMethod("getVote").invoke(event);
                        String user = String.valueOf(vote.getClass().getMethod("getUsername").invoke(vote));
                        Player p = Bukkit.getPlayer(user);
                        if (p != null) {
                            plugin.tokens().add(p.getUniqueId(), 10);
                            plugin.msg(p, "&aMerci pour ton vote ! &7+10 tokens");
                        }
                    } catch (Exception ignored) {
                    }
                }
            }, plugin);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            plugin.msg(sender, "&6VoteParty &7» &e" + current() + "&7/&e" + goal());
            return true;
        }
        if (!sender.hasPermission("sydaria.admin")) {
            return true;
        }
        if (args[0].equalsIgnoreCase("add")) {
            int n = args.length >= 2 ? Integer.parseInt(args[1]) : 1;
            add(n);
            plugin.msg(sender, "&a+" + n + " vote(s).");
            return true;
        }
        if (args[0].equalsIgnoreCase("set") && args.length >= 2) {
            file.get().set("current", Integer.parseInt(args[1]));
            file.save();
            return true;
        }
        return true;
    }

    public int current() {
        return file.get().getInt("current", 0);
    }

    public int goal() {
        return plugin.getConfig().getInt("voteparty.goal", 50);
    }

    public void add(int amount) {
        int now = current() + amount;
        int g = goal();
        if (now >= g) {
            now = 0;
            Bukkit.broadcastMessage(CC.color(plugin.prefix() + plugin.getConfig().getString("voteparty.broadcast")));
            for (Player player : Bukkit.getOnlinePlayers()) {
                for (String cmd : plugin.getConfig().getStringList("voteparty.reward-commands")) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", player.getName()));
                }
            }
        }
        file.get().set("current", now);
        file.save();
        Bukkit.broadcastMessage(CC.color(plugin.prefix() + "&6VoteParty &7» &e" + now + "&7/&e" + g));
    }
}
