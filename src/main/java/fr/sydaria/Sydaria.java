package fr.sydaria;

import fr.sydaria.anticleanup.AntiCleanupListener;
import fr.sydaria.anticommand.AntiCommandListener;
import fr.sydaria.atouts.AtoutManager;
import fr.sydaria.classement.ClassementManager;
import fr.sydaria.core.AdminCommand;
import fr.sydaria.core.CoreCommands;
import fr.sydaria.core.DeathInventoryManager;
import fr.sydaria.core.ScoreboardManager;
import fr.sydaria.data.DataManager;
import fr.sydaria.economy.EconomyHook;
import fr.sydaria.factions.FactionManager;
import fr.sydaria.grades.GradeCommandManager;
import fr.sydaria.grades.GradeManager;
import fr.sydaria.items.ItemManager;
import fr.sydaria.kits.KitManager;
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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class Sydaria extends JavaPlugin {
    private static Sydaria instance;
    private DataManager data;
    private TokenManager tokens;
    private EconomyHook economy;
    private AtoutManager atouts;
    private ClassementManager classement;
    private ItemManager items;
    private StaffManager staff;
    private FreezeManager freeze;
    private TagManager tags;
    private GradeManager grades;
    private FactionManager factions;
    private VotePartyManager voteParty;
    private ScoreboardManager scoreboard;
    private RankUpManager rankup;
    private DeathInventoryManager deathInventory;
    private KitManager kits;
    private FileConfiguration itemsConfig;

    public static Sydaria get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        reloadConfig();
        ensureConfigSections("rankup", "grade-commands");
        saveResourceIfMissing("items.yml");
        saveResourceIfMissing("kits.yml");
        reloadItems();

        this.data = new DataManager(this);
        this.tokens = new TokenManager(this);
        this.economy = new EconomyHook();
        this.atouts = new AtoutManager(this);
        this.classement = new ClassementManager(this);
        this.items = new ItemManager(this);
        this.staff = new StaffManager(this);
        this.staff.logPendingCrashRecoveries();
        this.freeze = new FreezeManager(this);
        this.tags = new TagManager(this);
        this.grades = new GradeManager(this);
        GradeCommandManager gradeCommands = new GradeCommandManager(this);
        this.factions = new FactionManager(this);
        CoreCommands core = new CoreCommands(this);
        this.deathInventory = new DeathInventoryManager(this);
        PortalManager portals = new PortalManager(this);
        this.voteParty = new VotePartyManager(this);
        ShopManager shop = new ShopManager(this);
        this.rankup = new RankUpManager(this);
        this.kits = new KitManager(this);

        Bukkit.getPluginManager().registerEvents(new AntiCleanupListener(this), this);
        Bukkit.getPluginManager().registerEvents(new AntiCommandListener(this), this);
        Bukkit.getPluginManager().registerEvents(atouts, this);
        Bukkit.getPluginManager().registerEvents(classement, this);
        Bukkit.getPluginManager().registerEvents(items, this);
        Bukkit.getPluginManager().registerEvents(staff, this);
        Bukkit.getPluginManager().registerEvents(freeze, this);
        Bukkit.getPluginManager().registerEvents(tags, this);
        Bukkit.getPluginManager().registerEvents(gradeCommands, this);
        Bukkit.getPluginManager().registerEvents(factions, this);
        Bukkit.getPluginManager().registerEvents(core, this);
        Bukkit.getPluginManager().registerEvents(deathInventory, this);
        Bukkit.getPluginManager().registerEvents(portals, this);
        Bukkit.getPluginManager().registerEvents(shop, this);
        Bukkit.getPluginManager().registerEvents(rankup, this);
        Bukkit.getPluginManager().registerEvents(kits, this);

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
        cmd("f", factions);
        cmd("voteparty", voteParty);
        cmd("portal", portals);
        cmd("itemsyd", items);
        cmd("boutique", shop);
        cmd("shop", shop);
        cmd("rankup", rankup);
        cmd("deathinv", deathInventory);
        cmd("feed", gradeCommands);
        cmd("pv", gradeCommands);
        cmd("ec", gradeCommands);
        cmd("refill", gradeCommands);
        cmd("craft", gradeCommands);
        cmd("near", gradeCommands);
        cmd("grades", gradeCommands);
        cmd("kit", kits);

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
        ensureConfigSections("rankup", "grade-commands");
        reloadItems();
        if (kits != null) {
            kits.reload();
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
    public ItemManager items() { return items; }
    public TagManager tags() { return tags; }
    public GradeManager grades() { return grades; }
    public FactionManager factions() { return factions; }
    public VotePartyManager voteParty() { return voteParty; }
    public RankUpManager rankup() { return rankup; }
    public DeathInventoryManager deathInventory() { return deathInventory; }
    public KitManager kits() { return kits; }
    public ScoreboardManager scoreboard() { return scoreboard; }
    public FileConfiguration getItemsConfig() { return itemsConfig; }
}
