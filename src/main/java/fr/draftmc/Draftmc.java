package fr.draftmc;

import fr.draftmc.anticleanup.AntiCleanupListener;
import fr.draftmc.combat.CombatDamageListener;
import fr.draftmc.events.EventDeathSpawnListener;
import fr.draftmc.anticommand.AntiCommandListener;
import fr.draftmc.atouts.AtoutManager;
import fr.draftmc.classement.ClassementManager;
import fr.draftmc.core.AdminCommand;
import fr.draftmc.core.AdminGui;
import fr.draftmc.core.CoreCommands;
import fr.draftmc.core.DeathInventoryManager;
import fr.draftmc.core.PingCommand;
import fr.draftmc.core.ScoreboardManager;
import fr.draftmc.core.TeleportWarmup;
import fr.draftmc.data.DataManager;
import fr.draftmc.economy.EconomyHook;
import fr.draftmc.economy.PayCommand;
import fr.draftmc.factions.FactionManager;
import fr.draftmc.grades.GradeCommandManager;
import fr.draftmc.grades.GradeManager;
import fr.draftmc.grades.StaffRankSetup;
import fr.draftmc.help.HelpCommand;
import fr.draftmc.hub.HubCommand;
import fr.draftmc.hub.SpawnCommand;
import fr.draftmc.collection.CollectionManager;
import fr.draftmc.playtime.PlaytimeCommand;
import fr.draftmc.quests.QuestManager;
import fr.draftmc.outpost.OutpostManager;
import fr.draftmc.social.MsgCommand;
import fr.draftmc.social.YtCommand;
import fr.draftmc.economy.BaltopCommand;
import fr.draftmc.items.BannedItemManager;
import fr.draftmc.items.ItemManager;
import fr.draftmc.kits.KitManager;
import fr.draftmc.lag.ClearLagManager;
import fr.draftmc.placeholders.DraftmcPlaceholders;
import fr.draftmc.portals.PortalManager;
import fr.draftmc.randomtp.RandomTpCommand;
import fr.draftmc.staff.FlyCommand;
import fr.draftmc.staff.FreezeManager;
import fr.draftmc.staff.ModerationManager;
import fr.draftmc.staff.ReportManager;
import fr.draftmc.staff.StaffLogManager;
import fr.draftmc.staff.StaffManager;
import fr.draftmc.stats.StatsManager;
import fr.draftmc.tags.TagManager;
import fr.draftmc.tokens.TokenManager;
import fr.draftmc.tournament.TournamentPlugin;
import fr.draftmc.rankup.RankUpManager;
import fr.draftmc.shop.ShopManager;
import fr.draftmc.discord.DiscordLinkManager;
import fr.draftmc.hdv.AuctionManager;
import fr.draftmc.homes.HomeManager;
import fr.draftmc.warps.WarpManager;
import fr.draftmc.deathban.DeathBanPlugin;
import fr.draftmc.events.EventHub;
import fr.draftmc.events.EventCommand;
import fr.draftmc.combat.CombatTagManager;
import fr.draftmc.combat.EnderPearlCooldown;
import fr.draftmc.tpa.TpaManager;
import fr.draftmc.util.CC;
import fr.draftmc.util.Cooldowns;
import fr.draftmc.voteparty.VotePartyManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class Draftmc extends JavaPlugin {
    private static Draftmc instance;
    private DataManager data;
    private TokenManager tokens;
    private EconomyHook economy;
    private AtoutManager atouts;
    private ClassementManager classement;
    private StatsManager stats;
    private ItemManager items;
    private BannedItemManager bannedItems;
    private StaffManager staff;
    private StaffLogManager staffLogs;
    private FreezeManager freeze;
    private TagManager tags;
    private GradeManager grades;
    private FactionManager factions;
    private VotePartyManager voteParty;
    private ScoreboardManager scoreboard;
    private RankUpManager rankup;
    private DeathInventoryManager deathInventory;
    private KitManager kits;
    private DiscordLinkManager discord;
    private EventHub events;
    private DeathBanPlugin deathban;
    private CombatTagManager combat;
    private TeleportWarmup teleports;
    private HomeManager homes;
    private WarpManager warps;
    private TpaManager tpa;
    private TournamentPlugin tournament;
    private ClearLagManager clearLag;
    private FileConfiguration itemsConfig;

    public static Draftmc get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        reloadConfig();
        ensureConfigSections("rankup", "grade-commands", "grade-perks", "discord-link", "sell-prices", "banned-items",
                "combat-tag", "combat", "tpa", "enderpearl", "homes", "teleport", "anti-cleanup", "warps",
                "help-gui", "admin-gui", "playtime-rewards", "quests", "collections", "outposts", "yt-menu", "hub", "baltop",
                "faction-prestige", "faction-missions", "factions", "staff", "clear-lag", "spawn", "pay", "staff-ranks", "staff-logs", "scoreboard", "join-quit");
        saveResourceIfMissing("items.yml");
        saveResourceIfMissing("kits.yml");
        reloadItems();

        this.data = new DataManager(this);
        this.tokens = new TokenManager(this);
        this.economy = new EconomyHook();
        this.atouts = new AtoutManager(this);
        this.classement = new ClassementManager(this);
        this.stats = new StatsManager(this);
        this.items = new ItemManager(this);
        this.bannedItems = new BannedItemManager(this);
        this.staff = new StaffManager(this);
        this.staff.logPendingCrashRecoveries();
        ReportManager reports = new ReportManager(this);
        this.staffLogs = new StaffLogManager(this);
        this.freeze = new FreezeManager(this);
        ModerationManager moderation = new ModerationManager(this);
        this.tags = new TagManager(this);
        this.grades = new GradeManager(this);
        new StaffRankSetup(this).setupIfNeeded();
        GradeCommandManager gradeCommands = new GradeCommandManager(this);
        this.factions = new FactionManager(this);
        CoreCommands core = new CoreCommands(this);
        this.deathInventory = new DeathInventoryManager(this);
        PortalManager portals = new PortalManager(this);
        this.voteParty = new VotePartyManager(this);
        ShopManager shop = new ShopManager(this);
        this.rankup = new RankUpManager(this);
        this.kits = new KitManager(this);
        this.combat = new CombatTagManager(this);
        this.teleports = new TeleportWarmup(this);
        this.homes = new HomeManager(this);
        this.warps = new WarpManager(this);
        this.tpa = new TpaManager(this);
        AuctionManager hdv = new AuctionManager(this);
        this.discord = new DiscordLinkManager(this);
        this.events = new EventHub(this);
        this.deathban = new DeathBanPlugin(this);
        this.tournament = new TournamentPlugin(this);
        this.clearLag = new ClearLagManager(this);
        AdminGui adminGui = new AdminGui(this);
        HelpCommand help = new HelpCommand(this);
        PlaytimeCommand ptr = new PlaytimeCommand(this);
        CollectionManager collections = new CollectionManager(this);
        QuestManager quests = new QuestManager(this);
        OutpostManager outposts = new OutpostManager(this);
        YtCommand yt = new YtCommand(this);
        MsgCommand msg = new MsgCommand(this);
        HubCommand hub = new HubCommand(this);
        BaltopCommand baltop = new BaltopCommand(this);

        Bukkit.getPluginManager().registerEvents(new AntiCleanupListener(this), this);
        Bukkit.getPluginManager().registerEvents(new AntiCommandListener(this), this);
        Bukkit.getPluginManager().registerEvents(new EventDeathSpawnListener(this), this);
        Bukkit.getPluginManager().registerEvents(atouts, this);
        Bukkit.getPluginManager().registerEvents(classement, this);
        Bukkit.getPluginManager().registerEvents(stats, this);
        Bukkit.getPluginManager().registerEvents(items, this);
        Bukkit.getPluginManager().registerEvents(bannedItems, this);
        Bukkit.getPluginManager().registerEvents(staff, this);
        Bukkit.getPluginManager().registerEvents(reports, this);
        Bukkit.getPluginManager().registerEvents(staffLogs, this);
        Bukkit.getPluginManager().registerEvents(freeze, this);
        Bukkit.getPluginManager().registerEvents(moderation, this);
        Bukkit.getPluginManager().registerEvents(tags, this);
        Bukkit.getPluginManager().registerEvents(gradeCommands, this);
        Bukkit.getPluginManager().registerEvents(factions, this);
        Bukkit.getPluginManager().registerEvents(core, this);
        Bukkit.getPluginManager().registerEvents(deathInventory, this);
        Bukkit.getPluginManager().registerEvents(portals, this);
        Bukkit.getPluginManager().registerEvents(shop, this);
        Bukkit.getPluginManager().registerEvents(rankup, this);
        Bukkit.getPluginManager().registerEvents(kits, this);
        Bukkit.getPluginManager().registerEvents(combat, this);
        Bukkit.getPluginManager().registerEvents(new CombatDamageListener(this), this);
        Bukkit.getPluginManager().registerEvents(teleports, this);
        Bukkit.getPluginManager().registerEvents(warps, this);
        Bukkit.getPluginManager().registerEvents(tpa, this);
        Bukkit.getPluginManager().registerEvents(new EnderPearlCooldown(this), this);
        Bukkit.getPluginManager().registerEvents(hdv, this);
        Bukkit.getPluginManager().registerEvents(discord, this);
        Bukkit.getPluginManager().registerEvents(adminGui, this);
        Bukkit.getPluginManager().registerEvents(help, this);
        Bukkit.getPluginManager().registerEvents(ptr, this);
        Bukkit.getPluginManager().registerEvents(collections, this);
        Bukkit.getPluginManager().registerEvents(quests, this);
        Bukkit.getPluginManager().registerEvents(outposts, this);
        Bukkit.getPluginManager().registerEvents(yt, this);
        Bukkit.getPluginManager().registerEvents(msg, this);

        cmd("draftmc", new AdminCommand(this, adminGui));
        cmd("admin", new AdminCommand(this, adminGui));
        cmd("atouts", atouts);
        cmd("classement", classement);
        cmd("stats", stats);
        cmd("enclume", core);
        cmd("bottlexp", gradeCommands);
        cmd("enchantement", core);
        cmd("furnace", core);
        cmd("randomkey", core);
        cmd("repair", gradeCommands);
        cmd("title", core);
        cmd("actionbar", core);
        cmd("poubelle", core);
        cmd("vision", core);
        cmd("b", core);
        cmd("randomtp", new RandomTpCommand(this));
        cmd("staff", staff);
        cmd("sc", staff);
        cmd("scadmin", staff);
        cmd("cps", staff);
        cmd("report", reports);
        cmd("reports", reports);
        cmd("adminlogs", staffLogs);
        cmd("freeze", freeze);
        cmd("mute", moderation);
        cmd("unmute", moderation);
        cmd("unban", moderation);
        cmd("tempban", moderation);
        cmd("ban", moderation);
        cmd("clearlag", clearLag);
        cmd("tags", tags);
        cmd("tokens", tokens);
        cmd("money", economy);
        cmd("bal", economy);
        cmd("pay", new PayCommand(this));
        cmd("f", factions);
        cmd("voteparty", voteParty);
        cmd("portal", portals);
        cmd("itemdraft", items);
        cmd("banitem", bannedItems);
        cmd("boutique", shop);
        cmd("shop", shop);
        cmd("help", help);
        cmd("ptr", ptr);
        cmd("collection", collections);
        cmd("quete", quests);
        cmd("outpost", outposts);
        cmd("yt", yt);
        cmd("msg", msg);
        cmd("r", msg);
        cmd("hub", hub);
        cmd("spawn", new SpawnCommand(this));
        cmd("ping", new PingCommand(this));
        cmd("ct", combat);
        cmd("fly", new FlyCommand(this));
        cmd("baltop", baltop);
        cmd("rankup", rankup);
        cmd("deathinv", deathInventory);
        cmd("feed", gradeCommands);
        cmd("pv", gradeCommands);
        cmd("ec", gradeCommands);
        cmd("refill", gradeCommands);
        cmd("craft", gradeCommands);
        cmd("near", gradeCommands);
        cmd("hat", gradeCommands);
        cmd("back", gradeCommands);
        cmd("compact", gradeCommands);
        cmd("invsee", gradeCommands);
        cmd("sellall", gradeCommands);
        cmd("sell", gradeCommands);
        cmd("grades", gradeCommands);
        cmd("kit", kits);
        cmd("home", homes);
        cmd("sethome", homes);
        cmd("delhome", homes);
        cmd("homes", homes);
        cmd("warp", warps);
        cmd("warps", warps);
        cmd("setwarp", warps);
        cmd("delwarp", warps);
        cmd("tpa", tpa);
        cmd("tpahere", tpa);
        cmd("tpyes", tpa);
        cmd("tpdeny", tpa);
        cmd("hdv", hdv);
        cmd("discord", discord);
        EventCommand eventCommand = new EventCommand(this);
        Bukkit.getPluginManager().registerEvents(eventCommand, this);
        cmd("event", eventCommand);

        this.scoreboard = new ScoreboardManager(this);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                new DraftmcPlaceholders(this).register();
                getLogger().info("PlaceholderAPI connecté (%draftmc_money%, %draftmc_tokens%...).");
            } catch (Throwable t) {
                getLogger().warning("PlaceholderAPI présent mais expansion non enregistrée: " + t.getMessage());
            }
        }

        getLogger().info("Draftmc 1.8.9 chargé.");
    }

    @Override
    public void onDisable() {
        if (combat != null) {
            combat.disable();
        }
        if (events != null) {
            events.disable();
        }
        if (tournament != null) {
            tournament.disable();
        }
        if (deathban != null) {
            deathban.disable();
        }
        if (discord != null) {
            discord.shutdown();
        }
        if (staffLogs != null) {
            staffLogs.shutdown();
        }
        if (freeze != null) {
            freeze.shutdown();
        }
        if (clearLag != null) {
            clearLag.shutdown();
        }
        if (scoreboard != null) {
            scoreboard.disable();
        }
        if (data != null) {
            for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
                classement.flushPlaytime(p.getUniqueId());
            }
            data.save();
        }
        if (factions != null) {
            factions.save();
        }
    }

    private void cmd(String name, CommandExecutor executor) {
        if (getCommand(name) != null) {
            getCommand(name).setExecutor(executor);
            if (executor instanceof org.bukkit.command.TabCompleter) {
                getCommand(name).setTabCompleter((org.bukkit.command.TabCompleter) executor);
            }
        }
    }

    private void saveResourceIfMissing(String name) {
        if (!new File(getDataFolder(), name).exists()) {
            saveResource(name, false);
        }
    }

    public void reloadAll() {
        reloadConfig();
        ensureConfigSections("rankup", "grade-commands", "grade-perks", "discord-link", "sell-prices", "banned-items",
                "combat-tag", "combat", "tpa", "enderpearl", "homes", "teleport", "anti-cleanup", "warps",
                "help-gui", "admin-gui", "playtime-rewards", "quests", "collections", "outposts", "yt-menu", "hub", "baltop",
                "faction-prestige", "faction-missions", "factions", "staff", "clear-lag", "spawn", "pay", "staff-ranks", "staff-logs", "scoreboard", "join-quit");
        reloadItems();
        if (kits != null) {
            kits.reload();
        }
        if (discord != null) {
            discord.reload();
        }
        if (bannedItems != null) {
            bannedItems.reload();
        }
        if (events != null) {
            events.reload();
        }
        if (deathban != null) {
            deathban.getConfigManager().reload();
            deathban.getBanManager().load();
        }
        if (clearLag != null) {
            clearLag.start();
        }
    }

    /**
     * Ajoute les sections absentes du config.yml serveur depuis le config par defaut du jar.
     * Evite de devoir regenerer tout le fichier apres une mise a jour du plugin.
     */
    private void ensureConfigSections(String... sections) {
        InputStream stream = getResource("config.yml");
        if (stream == null) {
            return;
        }
        try {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            boolean changed = false;
            for (String section : sections) {
                if (getConfig().isConfigurationSection(section)) {
                    if ("grade-commands".equals(section)) {
                        changed |= ensureGradeCommandEntries(defaults);
                    }
                    if ("banned-items".equals(section)) {
                        changed |= ensureBannedItemEntries(defaults);
                    }
                    if ("combat-tag".equals(section)) {
                        changed |= mergeStringList(defaults, "combat-tag.blocked-commands");
                        changed |= mergeStringList(defaults, "combat-tag.blocked-subcommands");
                    }
                    if ("combat".equals(section) || "discord-link".equals(section) || "anti-cleanup".equals(section) || "teleport".equals(section)
                            || "homes".equals(section) || "warps".equals(section) || "factions".equals(section)
                            || "staff".equals(section) || "rankup".equals(section) || "scoreboard".equals(section)
                            || "staff-logs".equals(section) || "help-gui".equals(section)
                            || "playtime-rewards".equals(section) || "quests".equals(section)
                            || "collections".equals(section) || "faction-prestige".equals(section)
                            || "faction-missions".equals(section)) {
                        changed |= mergeMissingLeaves(defaults, section);
                    }
                    continue;
                }
                if (!defaults.isConfigurationSection(section)) {
                    continue;
                }
                for (String key : defaults.getConfigurationSection(section).getKeys(true)) {
                    String path = section + "." + key;
                    if (!defaults.isConfigurationSection(path)) {
                        getConfig().set(path, defaults.get(path));
                    }
                }
                changed = true;
                getLogger().info("Section config manquante ajoutee: " + section);
            }
            if (changed) {
                saveConfig();
            }
        } catch (Exception ex) {
            getLogger().warning("Impossible de fusionner config.yml: " + ex.getMessage());
        } finally {
            try {
                stream.close();
            } catch (Exception ignored) {
            }
        }
    }

    private boolean ensureGradeCommandEntries(YamlConfiguration defaults) {
        if (!defaults.isConfigurationSection("grade-commands.commands")) {
            return false;
        }
        boolean changed = false;
        for (String command : defaults.getConfigurationSection("grade-commands.commands").getKeys(false)) {
            String base = "grade-commands.commands." + command;
            if (getConfig().isConfigurationSection(base)) {
                continue;
            }
            for (String key : defaults.getConfigurationSection(base).getKeys(true)) {
                String path = base + "." + key;
                if (!defaults.isConfigurationSection(path)) {
                    getConfig().set(path, defaults.get(path));
                }
            }
            changed = true;
            getLogger().info("Commande grade ajoutee au config: " + command);
        }
        String refillMsg = "grade-commands.messages.refill";
        if (!getConfig().contains(refillMsg) && defaults.contains(refillMsg)) {
            getConfig().set(refillMsg, defaults.get(refillMsg));
            changed = true;
        }
        return changed;
    }

    private boolean ensureBannedItemEntries(YamlConfiguration defaults) {
        boolean changed = false;
        String[] lists = {"items", "potions", "enchantments"};
        for (int i = 0; i < lists.length; i++) {
            String path = "banned-items." + lists[i];
            if (!defaults.contains(path)) {
                continue;
            }
            List<String> current = new ArrayList<String>(getConfig().getStringList(path));
            List<String> wanted = defaults.getStringList(path);
            boolean added = false;
            for (String entry : wanted) {
                if (!containsIgnoreCase(current, entry)) {
                    current.add(entry);
                    added = true;
                }
            }
            if (added) {
                getConfig().set(path, current);
                changed = true;
            }
        }
        if (defaults.isConfigurationSection("banned-items.potion-min-level")) {
            for (String key : defaults.getConfigurationSection("banned-items.potion-min-level").getKeys(false)) {
                String path = "banned-items.potion-min-level." + key;
                if (!getConfig().contains(path)) {
                    getConfig().set(path, defaults.get(path));
                    changed = true;
                }
            }
        }
        if (changed) {
            getLogger().info("Interdictions d'items mises a jour depuis la config par defaut.");
        }
        return changed;
    }

    private boolean mergeMissingLeaves(YamlConfiguration defaults, String section) {
        if (!defaults.isConfigurationSection(section)) {
            return false;
        }
        boolean changed = false;
        for (String key : defaults.getConfigurationSection(section).getKeys(true)) {
            String path = section + "." + key;
            if (defaults.isConfigurationSection(path) || getConfig().contains(path)) {
                continue;
            }
            getConfig().set(path, defaults.get(path));
            changed = true;
        }
        return changed;
    }

    private boolean mergeStringList(YamlConfiguration defaults, String path) {
        if (!defaults.contains(path)) {
            return false;
        }
        List<String> current = new ArrayList<String>(getConfig().getStringList(path));
        List<String> wanted = defaults.getStringList(path);
        boolean added = false;
        for (String entry : wanted) {
            if (!containsIgnoreCase(current, entry)) {
                current.add(entry);
                added = true;
            }
        }
        if (added) {
            getConfig().set(path, current);
        }
        return added;
    }

    private static boolean containsIgnoreCase(List<String> list, String value) {
        for (String entry : list) {
            if (entry != null && entry.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    public void reloadItems() {
        File file = new File(getDataFolder(), "items.yml");
        this.itemsConfig = YamlConfiguration.loadConfiguration(file);
        InputStream stream = getResource("items.yml");
        if (stream == null) {
            return;
        }
        try {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            ConfigurationSection defItems = defaults.getConfigurationSection("items");
            if (defItems == null) {
                return;
            }
            boolean changed = false;
            for (String id : defItems.getKeys(false)) {
                if (!itemsConfig.isConfigurationSection("items." + id)) {
                    itemsConfig.set("items." + id, defItems.get(id));
                    changed = true;
                }
            }
            if (changed) {
                itemsConfig.save(file);
            }
        } catch (Exception ignored) {
        } finally {
            try {
                stream.close();
            } catch (Exception ignored) {
            }
        }
    }

    public void msg(CommandSender sender, String message) {
        sender.sendMessage(CC.color(prefix() + message));
    }

    public String prefix() {
        return CC.color(getConfig().getString("prefix", "&8[&6Draftmc&8] &7"));
    }

    public boolean denyTpCooldown(Player player) {
        int cd = getConfig().getInt("teleport.command-cooldown-seconds", 5);
        if (cd <= 0) {
            return false;
        }
        int left = Cooldowns.remaining(player, "tp-cmd");
        if (left <= 0) {
            return false;
        }
        msg(player, getConfig().getString("teleport.cooldown-message",
                "&cAttends encore &e{time}s &cavant une téléportation.")
                .replace("{time}", String.valueOf(left)));
        return true;
    }

    public void startTpCooldown(Player player) {
        int cd = getConfig().getInt("teleport.command-cooldown-seconds", 5);
        if (cd > 0) {
            Cooldowns.start(player, "tp-cmd", cd);
        }
    }

    public DataManager data() { return data; }
    public TokenManager tokens() { return tokens; }
    public EconomyHook economy() { return economy; }
    public ClassementManager classement() { return classement; }
    public StatsManager stats() { return stats; }
    public ItemManager items() { return items; }
    public BannedItemManager bannedItems() { return bannedItems; }
    public TagManager tags() { return tags; }
    public GradeManager grades() { return grades; }
    public FactionManager factions() { return factions; }
    public VotePartyManager voteParty() { return voteParty; }
    public RankUpManager rankup() { return rankup; }
    public DeathInventoryManager deathInventory() { return deathInventory; }
    public KitManager kits() { return kits; }
    public DiscordLinkManager discord() { return discord; }
    public EventHub events() { return events; }
    public TournamentPlugin tournament() { return tournament; }
    public DeathBanPlugin deathban() { return deathban; }
    public StaffManager staff() { return staff; }
    public FreezeManager freeze() { return freeze; }
    public CombatTagManager combat() { return combat; }
    public TeleportWarmup teleports() { return teleports; }
    public HomeManager homes() { return homes; }
    public WarpManager warps() { return warps; }
    public TpaManager tpa() { return tpa; }
    public ScoreboardManager scoreboard() { return scoreboard; }
    public AtoutManager atouts() { return atouts; }
    public FileConfiguration getItemsConfig() { return itemsConfig; }
}
