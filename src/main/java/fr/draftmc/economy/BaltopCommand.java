package fr.draftmc.economy;

import fr.draftmc.Draftmc;
import fr.draftmc.util.CC;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class BaltopCommand implements CommandExecutor {
    private final Draftmc plugin;

    public BaltopCommand(Draftmc plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        int page = 1;
        if (args.length >= 1) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
                page = 1;
            }
        }
        show(sender, page);
        return true;
    }

    private void show(CommandSender sender, int page) {
        int pageSize = plugin.getConfig().getInt("baltop.page-size", 10);
        if (pageSize < 1) {
            pageSize = 10;
        }
        List<Entry> list = ranking();
        if (list.isEmpty()) {
            plugin.msg(sender, "&7Personne n'a encore d'argent.");
            return;
        }
        int pages = (list.size() + pageSize - 1) / pageSize;
        if (page < 1) {
            page = 1;
        }
        if (page > pages) {
            page = pages;
        }
        int from = (page - 1) * pageSize;
        int to = Math.min(from + pageSize, list.size());
        sender.sendMessage(CC.color(plugin.getConfig().getString("baltop.header",
                "&8&m-----&r &6Baltop &8&m-----")));
        String line = plugin.getConfig().getString("baltop.line",
                "&e{rank}. &f{player} &8- &a{money}");
        for (int i = from; i < to; i++) {
            Entry e = list.get(i);
            sender.sendMessage(CC.color(line
                    .replace("{rank}", String.valueOf(i + 1))
                    .replace("{player}", e.name)
                    .replace("{money}", plugin.economy().format(e.money))));
        }
        sender.sendMessage(CC.color(plugin.getConfig().getString("baltop.footer",
                "&7Page &e{page}&7/&e{pages} &8- &e/baltop [page]")
                .replace("{page}", String.valueOf(page))
                .replace("{pages}", String.valueOf(pages))));
        if (sender instanceof Player) {
            Player player = (Player) sender;
            int rank = rankOf(list, player.getUniqueId());
            if (rank > 0) {
                plugin.msg(player, "&7Toi : &e#" + rank + " &8- &a"
                        + plugin.economy().format(plugin.economy().getMoney(player)));
            }
        }
    }

    private int rankOf(List<Entry> list, UUID uuid) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).uuid.equals(uuid)) {
                return i + 1;
            }
        }
        return 0;
    }

    private List<Entry> ranking() {
        List<Entry> list = new ArrayList<Entry>();
        ConfigurationSection section = plugin.data().section();
        if (section == null) {
            return list;
        }
        for (String id : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(id);
                double money = plugin.economy().getMoney(uuid);
                if (money <= 0) {
                    continue;
                }
                list.add(new Entry(uuid, plugin.data().nameOf(uuid), money));
            } catch (Exception ignored) {
            }
        }
        Collections.sort(list, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                return Double.compare(b.money, a.money);
            }
        });
        return list;
    }

    private static class Entry {
        private final UUID uuid;
        private final String name;
        private final double money;

        private Entry(UUID uuid, String name, double money) {
            this.uuid = uuid;
            this.name = name;
            this.money = money;
        }
    }
}
