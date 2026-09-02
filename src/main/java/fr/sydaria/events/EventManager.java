package fr.sydaria.events;

import fr.sydaria.Sydaria;
import fr.sydaria.util.CC;
import fr.sydaria.util.Locations;
import fr.sydaria.util.NMS;
import fr.sydaria.util.YamlFile;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class EventManager implements Listener, CommandExecutor {
    private final Sydaria plugin;
    private final YamlFile file;
    private EventType current;
    private Location center;
    private int task = -1;
    private int secondsLeft;
    private int objectiveHp;
    private final Map<UUID, Integer> scores = new HashMap<UUID, Integer>();
    private UUID king;
    private String teamA = "Rouge";
    private String teamB = "Bleu";
    private final Map<UUID, String> teams = new HashMap<UUID, String>();
    private int capture;

    public EventManager(Sydaria plugin) {
        this.plugin = plugin;
        this.file = new YamlFile(plugin, "events.yml");
    }

    public String currentName() {
        return current == null ? "Aucun" : current.display();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            plugin.msg(sender, "&e/event start <type> &7| &e/event stop &7| &e/event set <type> &7| &e/event join");
            plugin.msg(sender, "&7Types: totem, totem_geant, koth, koth_geant, domination, sanctuaire, dtc, nexus, protect_the_king, masterkill, teamfight, battleroyal, ctf");
            return true;
        }
        String sub = args[0].toLowerCase();
        if (sub.equals("join") && sender instanceof Player) {
            join((Player) sender);
            return true;
        }
        if (!sender.hasPermission("sydaria.event.admin")) {
            plugin.msg(sender, "&cPas la permission.");
            return true;
        }
        if (sub.equals("stop")) {
            stop("Arrêt admin");
            return true;
        }
        if (sub.equals("set") && args.length >= 2 && sender instanceof Player) {
            EventType type = EventType.from(args[1]);
            if (type == null) {
                plugin.msg(sender, "&cType invalide.");
                return true;
            }
            file.get().set("locations." + type.name(), Locations.serialize(((Player) sender).getLocation()));
            file.save();
            plugin.msg(sender, "&aPosition de &e" + type.display() + " &adéfinie.");
            return true;
        }
        if (sub.equals("start") && args.length >= 2) {
            EventType type = EventType.from(args[1]);
            if (type == null) {
                plugin.msg(sender, "&cType invalide.");
                return true;
            }
            start(type);
            return true;
        }
        return true;
    }

    public void join(Player player) {
        if (current == null) {
            plugin.msg(player, "&cAucun event en cours.");
            return;
        }
        if (center != null) {
            player.teleport(center.clone().add(0.5, 1, 0.5));
        }
        scores.put(player.getUniqueId(), 0);
        if (current == EventType.TEAMFIGHT || current == EventType.CTF || current == EventType.DOMINATION) {
            teams.put(player.getUniqueId(), teams.size() % 2 == 0 ? teamA : teamB);
            plugin.msg(player, "&7Équipe &e" + teams.get(player.getUniqueId()));
        }
        plugin.msg(player, "&aTu as rejoint &e" + current.display());
    }

    public void start(EventType type) {
        if (current != null) {
            stop("Nouvel event");
        }
        current = type;
        scores.clear();
        teams.clear();
        capture = 0;
        king = null;
        center = Locations.deserialize(file.get().getString("locations." + type.name()));
        if (center == null && !Bukkit.getOnlinePlayers().isEmpty()) {
            center = Bukkit.getOnlinePlayers().iterator().next().getLocation();
        }
        secondsLeft = plugin.getConfig().getInt("events.duration-seconds", 300);
        if (type == EventType.TOTEM) {
            objectiveHp = plugin.getConfig().getInt("events.totem-health", 50);
            placeTotem(Material.BEACON);
        } else if (type == EventType.TOTEM_GEANT) {
            objectiveHp = plugin.getConfig().getInt("events.giant-totem-health", 150);
            placeTotem(Material.BEACON);
        } else if (type == EventType.DTC) {
            objectiveHp = 1;
            if (center != null) {
                center.getBlock().setType(Material.valueOf(plugin.getConfig().getString("events.dtc-block", "OBSIDIAN")));
            }
        } else if (type == EventType.NEXUS) {
            objectiveHp = plugin.getConfig().getInt("events.nexus-health", 100);
            if (center != null) {
                center.getBlock().setType(Material.ENDER_STONE);
            }
        } else if (type == EventType.PROTECT_THE_KING) {
            List<Player> online = new ArrayList<Player>(Bukkit.getOnlinePlayers());
            if (!online.isEmpty()) {
                king = online.get(new java.util.Random().nextInt(online.size())).getUniqueId();
                Player k = Bukkit.getPlayer(king);
                if (k != null) {
                    Bukkit.broadcastMessage(CC.color(plugin.prefix() + "&e" + k.getName() + " &7est le Roi ! Protégez-le."));
                }
            }
        } else if (type == EventType.BATTLEROYAL && center != null) {
            World world = center.getWorld();
            world.getWorldBorder().setCenter(center);
            world.getWorldBorder().setSize(200);
            world.getWorldBorder().setSize(20, plugin.getConfig().getInt("events.br-shrink-seconds", 180));
        } else if (type == EventType.CTF && center != null) {
            center.clone().add(10, 0, 0).getBlock().setType(Material.WOOL);
            center.clone().add(10, 0, 0).getBlock().setData((byte) 14);
            center.clone().add(-10, 0, 0).getBlock().setType(Material.WOOL);
            center.clone().add(-10, 0, 0).getBlock().setData((byte) 11);
        }
        String msg = plugin.getConfig().getString("events.announce", "&6Event %event%").replace("%event%", type.display());
        Bukkit.broadcastMessage(CC.color(plugin.prefix() + msg));
        for (Player p : Bukkit.getOnlinePlayers()) {
            NMS.title(p, "&6" + type.display(), "&7/event join", 10, 40, 10);
            scores.put(p.getUniqueId(), 0);
        }
        task = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            @Override
            public void run() {
                tick();
            }
        }, 20L, 20L);
    }

    private void placeTotem(Material material) {
        if (center == null) {
            return;
        }
        center.getBlock().setType(material);
        center.clone().add(0, -1, 0).getBlock().setType(Material.IRON_BLOCK);
    }

    private void tick() {
        if (current == null) {
            return;
        }
        secondsLeft--;
        for (Player player : Bukkit.getOnlinePlayers()) {
            NMS.actionBar(player, "&6" + current.display() + " &7» &e" + secondsLeft + "s &8| &cHP " + objectiveHp + " &8| &aPts " + val(player.getUniqueId()));
        }
        if (current == EventType.KOTH || current == EventType.KOTH_GEANT || current == EventType.SANCTUAIRE) {
            tickCapture();
        }
        if (secondsLeft <= 0) {
            UUID winner = best();
            Player p = winner == null ? null : Bukkit.getPlayer(winner);
            finish(p);
        }
    }

    private void tickCapture() {
        if (center == null) {
            return;
        }
        int radius = current == EventType.KOTH_GEANT
                ? plugin.getConfig().getInt("events.giant-koth-radius", 8)
                : plugin.getConfig().getInt("events.koth-radius", 4);
        Player only = null;
        int count = 0;
        for (Player player : center.getWorld().getPlayers()) {
            if (player.getLocation().distance(center) <= radius) {
                count++;
                only = player;
            }
        }
        if (count == 1 && only != null) {
            capture++;
            addScore(only, 1);
            int need = plugin.getConfig().getInt("events.koth-capture-seconds", 60);
            if (capture >= need) {
                finish(only);
            }
        } else {
            capture = Math.max(0, capture - 1);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (current == null || center == null) {
            return;
        }
        Block block = event.getBlock();
        if (!sameBlock(block.getLocation(), center) && current != EventType.CTF) {
            return;
        }
        Player player = event.getPlayer();
        if (current == EventType.TOTEM || current == EventType.TOTEM_GEANT) {
            event.setCancelled(true);
            objectiveHp--;
            plugin.classement().addTotemBlock(player);
            plugin.classement().addEventHit(player);
            addScore(player, 1);
            if (objectiveHp <= 0) {
                block.setType(Material.AIR);
                finish(player);
            }
        } else if (current == EventType.DTC) {
            event.setCancelled(true);
            plugin.classement().addEventHit(player);
            block.setType(Material.AIR);
            finish(player);
        } else if (current == EventType.NEXUS) {
            event.setCancelled(true);
            objectiveHp--;
            plugin.classement().addEventHit(player);
            addScore(player, 1);
            if (objectiveHp <= 0) {
                finish(player);
            }
        } else if (current == EventType.CTF && block.getType() == Material.WOOL) {
            event.setCancelled(true);
            addScore(player, 5);
            plugin.msg(player, "&aDrapeau capturé (+5).");
        }
    }

    @EventHandler
    public void onHit(EntityDamageByEntityEvent event) {
        if (current == null) {
            return;
        }
        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) {
            return;
        }
        Player damager = (Player) event.getDamager();
        plugin.classement().addEventHit(damager);
        if (current == EventType.MASTERKILL || current == EventType.TEAMFIGHT || current == EventType.BATTLEROYAL) {
            addScore(damager, 1);
        }
        if (current == EventType.TEAMFIGHT) {
            String a = teams.get(damager.getUniqueId());
            String b = teams.get(event.getEntity().getUniqueId());
            if (a != null && a.equals(b)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (current == null) {
            return;
        }
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (current == EventType.PROTECT_THE_KING && king != null && victim.getUniqueId().equals(king)) {
            finish(killer);
            return;
        }
        if (killer != null && (current == EventType.MASTERKILL || current == EventType.BATTLEROYAL || current == EventType.TEAMFIGHT)) {
            addScore(killer, 10);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (current == EventType.DOMINATION && center != null) {
            if (event.getTo() != null && event.getTo().distance(center) < 5) {
                addScore(event.getPlayer(), 0);
                capture++;
                if (capture % 10 == 0) {
                    addScore(event.getPlayer(), 1);
                }
            }
        }
    }

    private boolean sameBlock(Location a, Location b) {
        return a.getWorld().equals(b.getWorld()) && a.getBlockX() == b.getBlockX() && a.getBlockY() == b.getBlockY() && a.getBlockZ() == b.getBlockZ();
    }

    private void addScore(Player player, int amount) {
        UUID id = player.getUniqueId();
        Integer v = scores.get(id);
        scores.put(id, (v == null ? 0 : v) + amount);
    }

    private int val(UUID uuid) {
        Integer v = scores.get(uuid);
        return v == null ? 0 : v;
    }

    private UUID best() {
        UUID best = null;
        int s = -1;
        for (Map.Entry<UUID, Integer> e : scores.entrySet()) {
            if (e.getValue() > s) {
                s = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }

    private void finish(Player winner) {
        String name = winner == null ? "Personne" : winner.getName();
        String type = current == null ? "Event" : current.display();
        String msg = plugin.getConfig().getString("events.win", "&6%player% remporte %event%")
                .replace("%player%", name)
                .replace("%event%", type);
        Bukkit.broadcastMessage(CC.color(plugin.prefix() + msg));
        if (winner != null) {
            plugin.tokens().add(winner.getUniqueId(), 100);
            plugin.items().give(winner, "KEY_RARE");
        }
        discord(type + " gagné par " + name);
        stop(null);
    }

    public void stop(String reason) {
        if (task != -1) {
            Bukkit.getScheduler().cancelTask(task);
            task = -1;
        }
        if (current == EventType.BATTLEROYAL && center != null) {
            center.getWorld().getWorldBorder().reset();
        }
        current = null;
        center = null;
        king = null;
        scores.clear();
        if (reason != null) {
            Bukkit.broadcastMessage(CC.color(plugin.prefix() + "&cEvent stoppé. &7" + reason));
        }
    }

    private void discord(final String content) {
        if (!plugin.getConfig().getBoolean("discord.enabled", false)
                || !plugin.getConfig().getBoolean("discord.event-results", true)) {
            return;
        }
        final String url = plugin.getConfig().getString("discord.webhook-url", "");
        if (url == null || url.isEmpty()) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                HttpURLConnection con = null;
                try {
                    con = (HttpURLConnection) new URL(url).openConnection();
                    con.setRequestMethod("POST");
                    con.setDoOutput(true);
                    con.setRequestProperty("Content-Type", "application/json");
                    String json = "{\"content\":\"" + content.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
                    byte[] bytes = json.getBytes(Charset.forName("UTF-8"));
                    OutputStream os = con.getOutputStream();
                    os.write(bytes);
                    os.flush();
                    os.close();
                    con.getResponseCode();
                } catch (Exception ignored) {
                } finally {
                    if (con != null) {
                        con.disconnect();
                    }
                }
            }
        });
    }
}
