package fr.draftmc.events.teamfight;

import fr.draftmc.util.CC;
import fr.draftmc.util.Chat;
import fr.draftmc.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class TeamFightCommand implements CommandExecutor, TabCompleter, Listener {
    private static final List<String> SUBS = Arrays.asList(
            "help", "create", "invite", "accept", "list", "start", "launch", "stop",
            "setspawn", "setpos1", "setpos2", "savekit", "reload");

    private final TeamFightPlugin plugin;

    public TeamFightCommand(TeamFightPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin.getHost());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("create")) {
            return handleCreate(sender, args);
        }
        if (sub.equals("invite")) {
            return handleInvite(sender, args);
        }
        if (sub.equals("accept")) {
            return handleAccept(sender, args);
        }
        if (sub.equals("list")) {
            return handleList(sender);
        }
        if (!isAdmin(sender)) {
            sender.sendMessage(CC.color("&cPas la permission."));
            return true;
        }
        if (sub.equals("start")) {
            if (plugin.getManager().openRegistrations()) {
                if (plugin.getHost().events() != null) {
                    plugin.getHost().events().markActive(fr.draftmc.events.EventType.TEAMFIGHT, "default");
                }
            } else {
                sender.sendMessage(plugin.prefix() + CC.color("&cInscriptions deja ouvertes ou combat en cours."));
            }
            return true;
        }
        if (sub.equals("launch")) {
            if (!plugin.getManager().isReadyToLaunch()) {
                sender.sendMessage(plugin.prefix() + plugin.format("need-teams", null, null, 0));
                return true;
            }
            plugin.getManager().launch();
            return true;
        }
        if (sub.equals("stop")) {
            if (!plugin.getManager().stop()) {
                sender.sendMessage(plugin.prefix() + CC.color("&cAucun TeamFight en cours."));
            }
            return true;
        }
        if (sub.equals("setspawn")) {
            return handleSetSpawn(sender, args);
        }
        if (sub.equals("setpos1") || sub.equals("setpos2")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(CC.color("&cCommande joueur uniquement."));
                return true;
            }
            plugin.getManager().saveArenaCorner((Player) sender, sub.equals("setpos1") ? "pos1" : "pos2");
            sender.sendMessage(plugin.prefix() + CC.color("&aCoin d'arene &e" + sub + " &adefini."));
            return true;
        }
        if (sub.equals("savekit")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(CC.color("&cCommande joueur uniquement."));
                return true;
            }
            plugin.getKit().saveFrom((Player) sender);
            sender.sendMessage(plugin.prefix() + plugin.format("kit-saved", null, null, 0));
            return true;
        }
        if (sub.equals("reload")) {
            plugin.reloadConfig();
            plugin.getKit().load();
            sender.sendMessage(plugin.prefix() + CC.color("&aTeamFight recharge."));
            return true;
        }
        sendHelp(sender);
        return true;
    }

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(CC.color("&cCommande joueur uniquement."));
            return true;
        }
        if (plugin.getManager().getState() != TeamFightState.REGISTRATION) {
            sender.sendMessage(plugin.prefix() + CC.color("&cLes inscriptions ne sont pas ouvertes."));
            return true;
        }
        Player player = (Player) sender;
        String deny = plugin.getManager().createDenyReason(player);
        if (deny != null) {
            String text = plugin.format(deny, null, player, 0);
            if (text == null || text.isEmpty()) {
                text = CC.color("&cImpossible de creer cette equipe.");
            }
            sender.sendMessage(plugin.prefix() + text);
            return true;
        }
        TfTeam team = plugin.getManager().createTeam(player);
        if (team == null) {
            sender.sendMessage(plugin.prefix() + CC.color("&cImpossible de creer cette equipe."));
            return true;
        }
        sender.sendMessage(plugin.prefix() + plugin.format("team-created", team, player, 1));
        return true;
    }

    private boolean handleInvite(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(CC.color("&cCommande joueur uniquement."));
            return true;
        }
        Player leader = (Player) sender;
        TfTeam team = plugin.getManager().teamOf(leader.getUniqueId());
        if (team == null) {
            sender.sendMessage(plugin.prefix() + plugin.format("not-in-team", null, null, 0));
            return true;
        }
        if (!team.isLeader(leader.getUniqueId())) {
            sender.sendMessage(plugin.prefix() + plugin.format("not-leader", null, null, 0));
            return true;
        }
        if (team.isFull(plugin.getManager().rosterSize())) {
            sender.sendMessage(plugin.prefix() + plugin.format("team-full", team, null, 0));
            return true;
        }
        if (args.length < 2) {
            openInviteGui(leader, team);
            return true;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(CC.color("&cJoueur hors-ligne."));
            return true;
        }
        return invitePlayer(leader, team, target);
    }

    private boolean invitePlayer(Player leader, TfTeam team, Player target) {
        if (plugin.getManager().teamOf(target.getUniqueId()) != null) {
            leader.sendMessage(plugin.prefix() + plugin.format("already-in-team", null, target, 0));
            return true;
        }
        if (!plugin.getManager().sameFaction(target, team)) {
            leader.sendMessage(plugin.prefix() + plugin.format("not-same-faction", team, target, 0));
            return true;
        }
        if (!plugin.getManager().invite(leader, target)) {
            leader.sendMessage(plugin.prefix() + plugin.format("team-full", team, null, 0));
            return true;
        }
        Chat.sendClick(target,
                plugin.prefix() + plugin.format("team-invite", team, leader, 0)
                        .replace("{leader}", leader.getName()),
                "&a[Rejoindre]",
                "&eClique pour rejoindre &6" + team.getName(),
                "/teamfight accept " + team.getId());
        leader.sendMessage(plugin.prefix() + CC.color("&aInvitation envoyee a &e" + target.getName()));
        return true;
    }

    private void openInviteGui(Player leader, TfTeam team) {
        Inventory inv = Bukkit.createInventory(new InviteHolder(), 54, CC.color("&8Invite roster"));
        int slot = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (slot >= 54) {
                break;
            }
            if (plugin.getManager().teamOf(online.getUniqueId()) != null) {
                continue;
            }
            if (!plugin.getManager().sameFaction(online, team)) {
                continue;
            }
            ItemStack skull = new ItemBuilder(Material.SKULL_ITEM, 1, (short) 3)
                    .name("&e" + online.getName())
                    .lore("&7Clique pour inviter dans &6" + team.getName())
                    .build();
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            meta.setOwner(online.getName());
            skull.setItemMeta(meta);
            inv.setItem(slot++, skull);
        }
        leader.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof InviteHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player leader = (Player) event.getWhoClicked();
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() != Material.SKULL_ITEM || !item.hasItemMeta()) {
            return;
        }
        String name = ChatColor.stripColor(item.getItemMeta().getDisplayName());
        Player target = Bukkit.getPlayerExact(name);
        TfTeam team = plugin.getManager().teamOf(leader.getUniqueId());
        if (target == null || team == null) {
            return;
        }
        invitePlayer(leader, team, target);
        leader.closeInventory();
    }

    private boolean handleAccept(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(CC.color("&cCommande joueur uniquement."));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(CC.color("&e/teamfight accept <equipe>"));
            return true;
        }
        Player player = (Player) sender;
        if (!plugin.getManager().accept(player, args[1])) {
            TfTeam team = plugin.getManager().getTeam(args[1]);
            if (team != null && !plugin.getManager().sameFaction(player, team)) {
                sender.sendMessage(plugin.prefix() + plugin.format("not-same-faction", team, player, 0));
                return true;
            }
            sender.sendMessage(plugin.prefix() + CC.color("&cInvitation invalide ou roster plein."));
            return true;
        }
        return true;
    }

    private boolean handleList(CommandSender sender) {
        sender.sendMessage(plugin.prefix() + CC.color("&6Equipes TeamFight"));
        int size = plugin.getManager().rosterSize();
        int min = plugin.getManager().minRoster();
        List<TfTeam> all = plugin.getManager().getTeams();
        if (all.isEmpty()) {
            sender.sendMessage(CC.color("&7Aucune equipe."));
            return true;
        }
        for (TfTeam team : all) {
            sender.sendMessage(CC.color((plugin.getManager().isTeamReady(team) ? "&a" : "&e")
                    + team.getName() + " &8(" + team.getMembers().size() + "/" + size + ")"
                    + " &7min &e" + min
                    + " &7leader: &f" + team.leaderName()));
        }
        return true;
    }

    private boolean handleSetSpawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(CC.color("&cCommande joueur uniquement."));
            return true;
        }
        if (args.length < 2 || (!args[1].equalsIgnoreCase("a") && !args[1].equalsIgnoreCase("b")
                && !args[1].equalsIgnoreCase("wait"))) {
            sender.sendMessage(CC.color("&e/teamfight setspawn <a|b|wait>"));
            return true;
        }
        String slot = args[1].toLowerCase(Locale.ROOT);
        plugin.getManager().saveSpawn((Player) sender, slot);
        sender.sendMessage(plugin.prefix() + plugin.format("spawn-set", null, null, 0).replace("{slot}", slot));
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(plugin.prefix() + CC.color("&6TeamFight"));
        sender.sendMessage(CC.color("&e/teamfight create &7- equipe au nom de ta faction"));
        sender.sendMessage(CC.color("&e/teamfight invite [joueur] &7- GUI si aucun nom"));
        sender.sendMessage(CC.color("&e/teamfight accept <equipe>"));
        sender.sendMessage(CC.color("&e/teamfight list"));
        if (isAdmin(sender)) {
            sender.sendMessage(CC.color("&e/teamfight start &7/ &elaunch &7/ &estop"));
            sender.sendMessage(CC.color("&e/teamfight setspawn a|b|wait"));
            sender.sendMessage(CC.color("&e/teamfight setpos1 &7/ &esetpos2"));
            sender.sendMessage(CC.color("&e/teamfight savekit"));
        }
    }

    private boolean isAdmin(CommandSender sender) {
        return sender.hasPermission("teamfight.admin") || sender.hasPermission("draftmc.admin");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(SUBS, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("invite")) {
            List<String> names = new ArrayList<String>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            return filter(names, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("accept")) {
            List<String> names = new ArrayList<String>();
            for (TfTeam team : plugin.getManager().getTeams()) {
                names.add(team.getId());
            }
            return filter(names, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setspawn")) {
            return filter(Arrays.asList("a", "b", "wait"), args[1]);
        }
        return Collections.emptyList();
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

    static class InviteHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
