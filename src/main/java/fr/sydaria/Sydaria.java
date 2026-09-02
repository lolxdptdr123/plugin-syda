package fr.sydaria;

import fr.sydaria.anticleanup.AntiCleanupListener;
import fr.sydaria.anticommand.AntiCommandListener;
import fr.sydaria.atouts.AtoutManager;
import fr.sydaria.classement.ClassementManager;
import fr.sydaria.core.AdminCommand;
import fr.sydaria.core.CoreCommands;
import fr.sydaria.core.ScoreboardManager;
import fr.sydaria.data.DataManager;
import fr.sydaria.economy.EconomyHook;
import fr.sydaria.events.EventManager;
import fr.sydaria.factions.FactionManager;
import fr.sydaria.grades.GradeManager;
import fr.sydaria.items.ItemManager;
import fr.sydaria.placeholders.SydariaPlaceholders;
import fr.sydaria.portals.PortalManager;
import fr.sydaria.randomtp.RandomTpCommand;
import fr.sydaria.staff.FreezeManager;
import fr.sydaria.staff.StaffManager;
import fr.sydaria.tags.TagManager;
import fr.sydaria.tokens.TokenManager;
import fr.sydaria.rankup.RankUpManager;
import fr.sydaria.shop.ShopManager;
import fr.sydaria.util.CC;
import fr.sydaria.voteparty.VotePartyManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class Sydaria extends JavaPlugin {
    private static Sydaria instance;
    private DataManager data;
    private TokenManager tokens;
    private EconomyHook economy;
    private AtoutManager atouts;
    private ClassementManager classement;
    private EventManager events;
    private ItemManager items;
    private StaffManager staff;
    private FreezeManager freeze;
    private TagManager tags;
    private GradeManager grades;
    private FactionManager factions;
    private VotePartyManager voteParty;
    private ScoreboardManager scoreboard;
    private RankUpManager rankup;
    private FileConfiguration itemsConfig;

    public static Sydaria get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        saveResourceIfMissing("items.yml");
        reloadItems();

        this.data = new DataManager(this);
        this.tokens = new TokenManager(this);
        this.economy = new EconomyHook();
        this.atouts = new AtoutManager(this);
        this.classement = new ClassementManager(this);
        this.events = new EventManager(this);
        this.items = new ItemManager(this);
        this.staff = new StaffManager(this);
        this.staff.logPendingCrashRecoveries();
        this.freeze = new FreezeManager(this);
        this.tags = new TagManager(this);
        this.grades = new GradeManager(this);
        this.factions = new FactionManager(this);
        CoreCommands core = new CoreCommands(this);
        PortalManager portals = new PortalManager(this);
        this.voteParty = new VotePartyManager(this);
        ShopManager shop = new ShopManager(this);
        this.rankup = new RankUpManager(this);

        Bukkit.getPluginManager().registerEvents(new AntiCleanupListener(this), this);
        Bukkit.getPluginManager().registerEvents(new AntiCommandListener(this), this);
        Bukkit.getPluginManager().registerEvents(atouts, this);
        Bukkit.getPluginManager().registerEvents(classement, this);
        Bukkit.getPluginManager().registerEvents(events, this);
        Bukkit.getPluginManager().registerEvents(items, this);
        Bukkit.getPluginManager().registerEvents(staff, this);
        Bukkit.getPluginManager().registerEvents(freeze, this);
        Bukkit.getPluginManager().registerEvents(tags, this);
        Bukkit.getPluginManager().registerEvents(factions, this);
        Bukkit.getPluginManager().registerEvents(core, this);
        Bukkit.getPluginManager().registerEvents(portals, this);
        Bukkit.getPluginManager().registerEvents(shop, this);
        Bukkit.getPluginManager().registerEvents(rankup, this);

        cmd("sydaria", new AdminCommand(this));
        cmd("atouts", atouts);
        cmd("classement", classement);
        cmd("enclume", core);
        cmd("bottlexp", core);
        cmd("enchantement", core);
        cmd("furnace", core);
        cmd("randomkey", core);
        cmd("repair", core);
        cmd("title", core);
        cmd("actionbar", core);
        cmd("poubelle", core);
        cmd("vision", core);
        cmd("b", core);
        cmd("randomtp", new RandomTpCommand(this));
        cmd("staff", staff);
        cmd("sc", staff);
        cmd("cps", staff);
        cmd("freeze", freeze);
        cmd("tags", tags);
        cmd("tokens", tokens);
        cmd("money", economy);
        cmd("event", events);
        cmd("f", factions);
        cmd("voteparty", voteParty);
        cmd("portal", portals);
        cmd("itemsyd", items);
        cmd("boutique", shop);
        cmd("shop", shop);
        cmd("rankup", rankup);

        this.scoreboard = new ScoreboardManager(this);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                new SydariaPlaceholders(this).register();
                getLogger().info("PlaceholderAPI connecté (%sydaria_money%, %sydaria_tokens%...).");
            } catch (Throwable t) {
                getLogger().warning("PlaceholderAPI présent mais expansion non enregistrée: " + t.getMessage());
            }
        }

        getLogger().info("Sydaria 1.8.9 chargé.");
    }

    @Override
    public void onDisable() {
        if (freeze != null) {
            freeze.shutdown();
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
        reloadItems();
    }

    public void reloadItems() {
        File file = new File(getDataFolder(), "items.yml");
        this.itemsConfig = YamlConfiguration.loadConfiguration(file);
    }

    public void msg(CommandSender sender, String message) {
        sender.sendMessage(CC.color(prefix() + message));
    }

    public String prefix() {
        return CC.color(getConfig().getString("prefix", "&8[&6Sydaria&8] &7"));
    }

    public DataManager data() { return data; }
    public TokenManager tokens() { return tokens; }
    public EconomyHook economy() { return economy; }
    public ClassementManager classement() { return classement; }
    public EventManager events() { return events; }
    public ItemManager items() { return items; }
    public TagManager tags() { return tags; }
    public GradeManager grades() { return grades; }
    public FactionManager factions() { return factions; }
    public VotePartyManager voteParty() { return voteParty; }
    public RankUpManager rankup() { return rankup; }
    public ScoreboardManager scoreboard() { return scoreboard; }
    public FileConfiguration getItemsConfig() { return itemsConfig; }
}
