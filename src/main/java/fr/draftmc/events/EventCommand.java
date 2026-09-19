package fr.draftmc.events;

import fr.draftmc.Draftmc;
import fr.draftmc.util.CC;
import fr.draftmc.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EventCommand implements CommandExecutor, TabCompleter, Listener {
    private static final List<String> ROOT = Arrays.asList("help", "start", "stop", "info", "list",
            "conquest", "domination", "battleroyal", "masterkill", "totem", "totemgeant", "koth", "teamfight",
            "largage", "br", "cq", "dom", "mk", "tot", "totemg", "tf", "kothgeant", "kothg", "airdrop");
    private static final String[] DAYS = {
            "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"
    };
    private static final String[] DAY_LABELS = {
            "Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche"
    };
    private static final int[] DAY_SLOTS = {10, 11, 12, 13, 14, 15, 16};

    private final Draftmc plugin;

    public EventCommand(Draftmc plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        EventHub hub = plugin.events();
        if (hub == null) {
            msg(sender, "&cEvents non charges.");
            return true;
        }
        if (args.length == 0) {
            if (sender instanceof Player) {
                openSchedule((Player) sender, hub);
            } else {
                sendHelp(sender);
            }
            return true;
        }
        if (!isEventAdmin(sender)) {
            if (sender instanceof Player) {
                openSchedule((Player) sender, hub);
            } else {
                msg(sender, "&cPas la permission.");
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }
        if (args[0].equalsIgnoreCase("info")) {
            sendInfo(sender, hub);
            return true;
        }
        if (args[0].equalsIgnoreCase("start")) {
            return handleStart(sender, hub, args);
        }
        if (args[0].equalsIgnoreCase("stop")) {
            return handleStop(sender, hub, args);
        }
        if (args[0].equalsIgnoreCase("list") && args.length >= 2) {
            return handleList(sender, hub, EventType.from(args[1]));
        }

        EventType type = EventType.from(args[0]);
        if (type != null && args.length >= 2 && args[1].equalsIgnoreCase("list")) {
            return handleList(sender, hub, type);
        }

        sendHelp(sender);
        return true;
    }

    private boolean isEventAdmin(CommandSender sender) {
        return sender.hasPermission("draftmc.event") || sender.hasPermission("draftmc.admin");
    }

    private void openSchedule(Player player, EventHub hub) {
        Inventory inv = Bukkit.createInventory(new ScheduleHolder(), 27, CC.color(hub.scheduleTitle()));
        int today = todayIndex();
        for (int i = 0; i < DAYS.length; i++) {
            List<String> lore = new ArrayList<String>();
            lore.add("&7Events du jour :");
            List<String> events = hub.scheduleLore(DAYS[i]);
            if (events.isEmpty()) {
                lore.add("&8» &7Aucun event");
            } else {
                lore.addAll(events);
            }
            if (i == today) {
                lore.add("");
                lore.add("&aAujourd'hui");
            }
            ItemBuilder item = new ItemBuilder(i == today ? Material.GOLD_BLOCK : Material.IRON_BLOCK)
                    .name((i == today ? "&6" : "&f") + DAY_LABELS[i]);
            item.lore(lore);
            inv.setItem(DAY_SLOTS[i], item.build());
        }
        player.openInventory(inv);
    }

    private int todayIndex() {
        int cal = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        return (cal + 5) % 7;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ScheduleHolder)) {
            return;
        }
        event.setCancelled(true);
    }

    private boolean handleStart(CommandSender sender, EventHub hub, String[] args) {
        if (args.length < 3) {
            msg(sender, "&e/event start <event> <map>");
            return true;
        }
        EventType type = EventType.from(args[1]);
        if (type == null) {
            msg(sender, "&cEvent inconnu. &7Utilise /event help");
            return true;
        }
        EventHub.StartResult result = hub.start(type, args[2]);
        String mapName = hub.mapDisplay(type, args[2]);
        switch (result) {
            case STARTED:
                msg(sender, "&a" + type.display() + " &7demarre sur &e" + mapName + "&7.");
                break;
            case REGISTRATION_OPENED:
                msg(sender, "&aInscriptions " + type.display() + " &7ouvertes (map &e" + mapName
                        + "&7). Relance &e/event start " + type.id() + " " + args[2].toLowerCase(Locale.ROOT)
                        + " &7quand 2 equipes sont pretes.");
                break;
            case LAUNCHED:
                msg(sender, "&a" + type.display() + " &7lance sur &e" + mapName + "&7.");
                break;
            case NEED_TEAMS:
                msg(sender, "&cIl faut au moins 2 equipes. Map selectionnee : &e" + mapName);
                break;
            case ALREADY_RUNNING:
                msg(sender, "&c" + type.display() + " est deja en cours"
                        + (hub.activeMap(type) != null ? " sur &e" + hub.mapDisplay(type, hub.activeMap(type)) : "")
                        + "&c.");
                break;
            case UNKNOWN_MAP:
                msg(sender, "&cMap inconnue. &7/event " + type.id() + " list");
                break;
            default:
                msg(sender, "&cImpossible de demarrer " + type.display() + ".");
                break;
        }
        return true;
    }

    private boolean handleStop(CommandSender sender, EventHub hub, String[] args) {
        if (args.length < 3) {
            msg(sender, "&e/event stop <event> <map>");
            return true;
        }
        EventType type = EventType.from(args[1]);
        if (type == null) {
            msg(sender, "&cEvent inconnu. &7Utilise /event help");
            return true;
        }
        EventHub.StopResult result = hub.stop(type, args[2]);
        switch (result) {
            case STOPPED:
                msg(sender, "&c" + type.display() + " &7arrete (&e" + hub.mapDisplay(type, args[2]) + "&7).");
                break;
            case WRONG_MAP:
                msg(sender, "&c" + type.display() + " tourne sur &e" + hub.mapDisplay(type, hub.activeMap(type))
                        + " &c, pas &e" + args[2] + "&c.");
                break;
            case UNKNOWN_MAP:
                msg(sender, "&cMap inconnue. &7/event " + type.id() + " list");
                break;
            default:
                msg(sender, "&cAucun " + type.display() + " en cours.");
                break;
        }
        return true;
    }

    private boolean handleList(CommandSender sender, EventHub hub, EventType type) {
        if (type == null) {
            msg(sender, "&cEvent inconnu. &7Utilise /event help");
            return true;
        }
        msg(sender, "&6Maps " + type.display() + " &7:");
        for (Map.Entry<String, String> entry : hub.mapsOf(type).entrySet()) {
            boolean active = entry.getKey().equalsIgnoreCase(hub.activeMap(type));
            msg(sender, (active ? "&a▶ " : "&8- ") + "&e" + entry.getKey()
                    + " &7(" + entry.getValue() + ")" + (active ? " &a[en cours]" : ""));
        }
        return true;
    }

    private void sendInfo(CommandSender sender, EventHub hub) {
        msg(sender, "&6Events Draftmc");
        for (EventType type : EventType.values()) {
            String map = hub.activeMap(type);
            String mapText = map == null ? "&7-" : "&e" + hub.mapDisplay(type, map);
            msg(sender, "&8- &f" + type.display() + " &8» &7" + hub.status(type) + " &8| map: " + mapText);
        }
        msg(sender, "&7Maps : &e/event <event> list");
    }

    private void sendHelp(CommandSender sender) {
        msg(sender, "&6/event &7- Planning de la semaine");
        msg(sender, "&e/event start <event> <map> &7- Demarre un event");
        msg(sender, "&e/event stop <event> <map> &7- Arrete un event");
        msg(sender, "&e/event <event> list &7- Liste les maps");
        msg(sender, "&e/event info &7- Etat des events");
        msg(sender, "&7Events : &fconquest&7, &fdomination&7, &fbattleroyal&7, &fmasterkill&7, &ftotem&7, &ftotemgeant&7, &fkothgeant&7, &fteamfight&7, &flargage");
        msg(sender, "&7Ex. &e/event start conquest default");
        msg(sender, "&7Battleroyal / MasterKill / TeamFight : 1er start = inscriptions, 2e start = lancement.");
    }

    private void msg(CommandSender sender, String message) {
        EventHub hub = plugin.events();
        String prefix = hub != null ? hub.prefix() : "&8[&6Event&8] &7";
        sender.sendMessage(CC.color(prefix + message));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!isEventAdmin(sender)) {
            return Collections.emptyList();
        }
        EventHub hub = plugin.events();
        if (args.length == 1) {
            return filter(ROOT, args[0]);
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("start") || args[0].equalsIgnoreCase("stop")
                    || args[0].equalsIgnoreCase("list")) {
                return filter(eventNames(), args[1]);
            }
            EventType type = EventType.from(args[0]);
            if (type != null) {
                return filter(Collections.singletonList("list"), args[1]);
            }
        }
        if (args.length == 3 && hub != null
                && (args[0].equalsIgnoreCase("start") || args[0].equalsIgnoreCase("stop"))) {
            EventType type = EventType.from(args[1]);
            if (type != null) {
                return filter(hub.mapIds(type), args[2]);
            }
        }
        return Collections.emptyList();
    }

    private List<String> eventNames() {
        List<String> names = new ArrayList<String>();
        for (EventType type : EventType.values()) {
            names.add(type.id());
        }
        return names;
    }

    private List<String> filter(List<String> options, String token) {
        String prefix = token.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<String>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                result.add(option);
            }
        }
        return result;
    }

    private static class ScheduleHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
