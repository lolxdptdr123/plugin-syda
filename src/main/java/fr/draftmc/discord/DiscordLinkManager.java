package fr.draftmc.discord;

import fr.draftmc.Draftmc;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Lien Minecraft ↔ Discord via un bot separe (dossier discord-bot/).
 * /discord donne un code. Le joueur l'envoie dans le salon Discord.
 * Le bot appelle l'API HTTP du plugin pour valider le code.
 */
public class DiscordLinkManager implements CommandExecutor, Listener {
    private final Draftmc plugin;
    private final Map<String, PendingLink> pending = new ConcurrentHashMap<String, PendingLink>();
    private final ConcurrentLinkedQueue<SyncJob> syncQueue = new ConcurrentLinkedQueue<SyncJob>();
    private final ConcurrentLinkedQueue<EventPost> eventQueue = new ConcurrentLinkedQueue<EventPost>();
    private final Random random = new Random();
    private HttpServer httpServer;

    public DiscordLinkManager(Draftmc plugin) {
        this.plugin = plugin;
        startApi();
    }

    public void shutdown() {
        stopApi();
    }

    public void reload() {
        stopApi();
        startApi();
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("discord-link.enabled", false)
                && !secret().isEmpty();
    }

    private String secret() {
        String value = plugin.getConfig().getString("discord-link.api-secret", "");
        return value == null ? "" : value.trim();
    }

    private void startApi() {
        if (!plugin.getConfig().getBoolean("discord-link.enabled", false)) {
            plugin.getLogger().info("Discord link: desactive.");
            return;
        }
        if (secret().isEmpty() || "CHANGE_ME".equalsIgnoreCase(secret())) {
            plugin.getLogger().warning("Discord link: definis discord-link.api-secret dans config.yml.");
            return;
        }
        String bind = plugin.getConfig().getString("discord-link.api-bind", "0.0.0.0");
        int port = plugin.getConfig().getInt("discord-link.api-port", 8765);
        try {
            httpServer = HttpServer.create(new InetSocketAddress(bind, port), 0);
            httpServer.createContext("/health", new com.sun.net.httpserver.HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) {
                    reply(exchange, 200, "{\"ok\":true}");
                }
            });
            httpServer.createContext("/link", new com.sun.net.httpserver.HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) {
                    handleLink(exchange);
                }
            });
            httpServer.createContext("/sync", new com.sun.net.httpserver.HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) {
                    handleSync(exchange);
                }
            });
            httpServer.createContext("/events", new com.sun.net.httpserver.HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) {
                    handleEvents(exchange);
                }
            });
            httpServer.setExecutor(null);
            httpServer.start();
            plugin.getLogger().info("Discord link: API http://" + bind + ":" + port + " (bot discord-bot/)");
        } catch (Exception ex) {
            plugin.getLogger().warning("Discord link: impossible de demarrer l'API (" + ex.getMessage() + ")");
            httpServer = null;
        }
    }

    private void stopApi() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cJoueur uniquement.");
            return true;
        }
        Player player = (Player) sender;
        if (args.length > 0 && "unlink".equalsIgnoreCase(args[0])) {
            unlink(player);
            return true;
        }
        if (args.length > 0 && "status".equalsIgnoreCase(args[0])) {
            status(player);
            return true;
        }
        if (!enabled()) {
            plugin.msg(player, "&cLe lien Discord n'est pas configuré. Contacte un admin.");
            return true;
        }
        String existing = plugin.data().getString(player.getUniqueId(), "discord_id");
        if (existing != null && !existing.isEmpty()) {
            plugin.msg(player, "&aCompte déjà lié.");
            plugin.msg(player, "&7Unlink: &e/discord unlink");
            applyRank(player);
            return true;
        }
        expireOld();
        removePendingFor(player.getUniqueId());
        String code = generateCode();
        int expire = plugin.getConfig().getInt("discord-link.code-expire-seconds", 300);
        pending.put(code, new PendingLink(player.getUniqueId(), System.currentTimeMillis() + expire * 1000L));
        plugin.msg(player, "&6Lien Discord");
        plugin.msg(player, "&7Envoie ce code dans le salon Discord :");
        plugin.msg(player, "&e&l" + code);
        plugin.msg(player, "&7Valable &e" + (expire / 60) + " min&7.");
        return true;
    }

    private void status(Player player) {
        String id = plugin.data().getString(player.getUniqueId(), "discord_id");
        if (id == null || id.isEmpty()) {
            plugin.msg(player, "&cAucun compte Discord lié. &e/discord");
            return;
        }
        plugin.msg(player, "&aCompte Discord lié.");
        plugin.msg(player, "&7Pseudo Discord : &e" + nicknameFor(player));
    }

    private void unlink(Player player) {
        String id = plugin.data().getString(player.getUniqueId(), "discord_id");
        if (id == null || id.isEmpty()) {
            plugin.msg(player, "&cAucun compte lié.");
            return;
        }
        plugin.data().setString(player.getUniqueId(), "discord_id", "");
        syncQueue.add(new SyncJob(id, "", "", allRoleIds(), true));
        plugin.msg(player, "&eCompte Discord délié.");
    }

    public void applyRank(Player player) {
        if (!enabled()) {
            return;
        }
        String discordId = plugin.data().getString(player.getUniqueId(), "discord_id");
        if (discordId == null || discordId.isEmpty()) {
            return;
        }
        syncQueue.add(new SyncJob(discordId, nicknameFor(player), roleFor(player), allRoleIds(), false));
    }

    private String nicknameFor(Player player) {
        return clipNick(player == null ? "" : player.getName());
    }

    private String nicknameForName(String name) {
        return clipNick(name);
    }

    private static String clipNick(String name) {
        if (name == null) {
            return "";
        }
        String nick = name.replaceAll("(?i)[&§][0-9A-FK-OR]", "").trim();
        if (nick.length() > 32) {
            nick = nick.substring(0, 32);
        }
        return nick;
    }

    private String roleFor(Player player) {
        String group = plugin.grades().highestGroup(player);
        String role = plugin.getConfig().getString("discord-link.roles." + group, "");
        return role == null ? "" : role.trim();
    }

    private List<String> allRoleIds() {
        List<String> ids = new ArrayList<String>();
        ConfigurationSection roles = plugin.getConfig().getConfigurationSection("discord-link.roles");
        if (roles == null) {
            return ids;
        }
        for (String group : roles.getKeys(false)) {
            String roleId = roles.getString(group, "").trim();
            if (!roleId.isEmpty() && !ids.contains(roleId)) {
                ids.add(roleId);
            }
        }
        return ids;
    }

    private String generateCode() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder("DMC-");
        for (int i = 0; i < 6; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    private void expireOld() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, PendingLink>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().expiresAt < now) {
                it.remove();
            }
        }
    }

    private void removePendingFor(UUID uuid) {
        Iterator<Map.Entry<String, PendingLink>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().uuid.equals(uuid)) {
                it.remove();
            }
        }
    }

    private void handleLink(HttpExchange exchange) {
        try {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                reply(exchange, 405, "{\"ok\":false,\"error\":\"method\"}");
                return;
            }
            if (!authorized(exchange)) {
                reply(exchange, 401, "{\"ok\":false,\"error\":\"unauthorized\"}");
                return;
            }
            String body = readBody(exchange);
            final String code = jsonString(body, "code");
            final String discordId = jsonString(body, "discordId");
            if (code == null || discordId == null) {
                reply(exchange, 400, "{\"ok\":false,\"error\":\"invalid\"}");
                return;
            }
            final String[] result = new String[1];
            final CountDownLatch latch = new CountDownLatch(1);
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override
                public void run() {
                    result[0] = completeLink(code.toUpperCase(), discordId);
                    latch.countDown();
                }
            });
            if (!latch.await(5, TimeUnit.SECONDS) || result[0] == null) {
                reply(exchange, 504, "{\"ok\":false,\"error\":\"timeout\"}");
                return;
            }
            reply(exchange, result[0].contains("\"ok\":true") ? 200 : 400, result[0]);
        } catch (Exception ex) {
            reply(exchange, 500, "{\"ok\":false,\"error\":\"server\"}");
        }
    }

    private String completeLink(String code, String discordId) {
        expireOld();
        PendingLink pendingLink = pending.remove(code);
        if (pendingLink == null) {
            return "{\"ok\":false,\"error\":\"unknown_code\"}";
        }
        UUID already = plugin.data().findUuidByString("discord_id", discordId);
        if (already != null && !already.equals(pendingLink.uuid)) {
            pending.put(code, pendingLink);
            return "{\"ok\":false,\"error\":\"discord_taken\"}";
        }
        plugin.data().setString(pendingLink.uuid, "discord_id", discordId);
        Player player = Bukkit.getPlayer(pendingLink.uuid);
        String name = plugin.data().nameOf(pendingLink.uuid);
        String nick = player != null && player.isOnline() ? nicknameFor(player) : nicknameForName(name);
        String roleId = "";
        if (player != null && player.isOnline()) {
            plugin.msg(player, "&aCompte Discord lié !");
            plugin.msg(player, "&7Ton pseudo Discord devient &e" + nick + "&7.");
            roleId = roleFor(player);
            applyRank(player);
        }
        return "{\"ok\":true,\"player\":\"" + escape(name) + "\",\"nick\":\"" + escape(nick)
                + "\",\"roleId\":\"" + escape(roleId) + "\",\"removeRoleIds\":" + jsonArray(allRoleIds()) + "}";
    }

    private void handleSync(HttpExchange exchange) {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            reply(exchange, 405, "{\"ok\":false}");
            return;
        }
        if (!authorized(exchange)) {
            reply(exchange, 401, "{\"ok\":false,\"error\":\"unauthorized\"}");
            return;
        }
        StringBuilder sb = new StringBuilder("{\"ok\":true,\"pending\":[");
        boolean first = true;
        SyncJob job;
        int count = 0;
        while (count < 40 && (job = syncQueue.poll()) != null) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append("{\"discordId\":\"").append(escape(job.discordId))
                    .append("\",\"nick\":\"").append(escape(job.nick))
                    .append("\",\"roleId\":\"").append(escape(job.roleId))
                    .append("\",\"unlink\":").append(job.unlink)
                    .append(",\"removeRoleIds\":").append(jsonArray(job.removeRoleIds))
                    .append('}');
            count++;
        }
        sb.append("]}");
        reply(exchange, 200, sb.toString());
    }

    private boolean authorized(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header == null) {
            header = exchange.getRequestHeaders().getFirst("X-Secret");
        }
        if (header == null) {
            return false;
        }
        String want = "Bearer " + secret();
        return want.equals(header) || secret().equals(header);
    }

    private void reply(HttpExchange exchange, int code, String json) {
        try {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(code, bytes.length);
            OutputStream out = exchange.getResponseBody();
            out.write(bytes);
            out.close();
        } catch (Exception ignored) {
        }
    }

    private String readBody(HttpExchange exchange) throws Exception {
        InputStream in = exchange.getRequestBody();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] data = new byte[1024];
        int n;
        while ((n = in.read(data)) != -1) {
            buf.write(data, 0, n);
        }
        return new String(buf.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String jsonString(String json, String key) {
        if (json == null) {
            return null;
        }
        String needle = "\"" + key + "\"";
        int idx = json.indexOf(needle);
        if (idx < 0) {
            return null;
        }
        int colon = json.indexOf(':', idx + needle.length());
        int start = json.indexOf('"', colon + 1);
        if (colon < 0 || start < 0) {
            return null;
        }
        int end = start + 1;
        while (end < json.length()) {
            char c = json.charAt(end);
            if (c == '"' && json.charAt(end - 1) != '\\') {
                break;
            }
            end++;
        }
        if (end >= json.length()) {
            return null;
        }
        return json.substring(start + 1, end).replace("\\\"", "\"");
    }

    public void postEventResult(String title, String subtitle, List<String> lines, int color) {
        if (!eventsEnabled()) {
            return;
        }
        final EventPost post = new EventPost(
                title,
                "",
                lines,
                color,
                subtitle == null || subtitle.isEmpty()
                        ? plugin.getConfig().getString("discord.events.results", ":crossed_swords: Résultats")
                        : subtitle,
                plugin.getConfig().getString("discord.events.footer", "Draftmc"));
        final String webhook = plugin.getConfig().getString("discord.webhook-url", "");
        if (webhook != null && webhook.startsWith("https://discord.com/api/webhooks/")) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
                @Override
                public void run() {
                    sendWebhook(webhook, post);
                }
            });
            return;
        }
        eventQueue.add(post);
    }

    private boolean eventsEnabled() {
        if (!plugin.getConfig().getBoolean("discord.enabled", true)) {
            return false;
        }
        if (plugin.getConfig().isSet("discord.events.enabled")) {
            return plugin.getConfig().getBoolean("discord.events.enabled");
        }
        return plugin.getConfig().getBoolean("discord.event-results", true);
    }

    private void handleEvents(HttpExchange exchange) {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            reply(exchange, 405, "{\"ok\":false}");
            return;
        }
        if (!authorized(exchange)) {
            reply(exchange, 401, "{\"ok\":false,\"error\":\"unauthorized\"}");
            return;
        }
        StringBuilder sb = new StringBuilder("{\"ok\":true,\"pending\":[");
        boolean first = true;
        EventPost post;
        int count = 0;
        while (count < 20 && (post = eventQueue.poll()) != null) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(post.toJson());
            count++;
        }
        sb.append("]}");
        reply(exchange, 200, sb.toString());
    }

    private void sendWebhook(String url, EventPost post) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("User-Agent", "Draftmc");
            byte[] body = ("{\"content\":\"" + escape(post.messageBody()) + "\"}").getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(body.length);
            OutputStream out = conn.getOutputStream();
            out.write(body);
            out.close();
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                plugin.getLogger().warning("Discord events: webhook HTTP " + code);
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Discord events: webhook " + ex.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String jsonArray(List<String> values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(escape(values.get(i))).append('"');
        }
        sb.append(']');
        return sb.toString();
    }

    private static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "").replace("\n", "\\n");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                applyRank(player);
            }
        }, 40L);
    }

    private static class PendingLink {
        private final UUID uuid;
        private final long expiresAt;

        private PendingLink(UUID uuid, long expiresAt) {
            this.uuid = uuid;
            this.expiresAt = expiresAt;
        }
    }

    private static class SyncJob {
        private final String discordId;
        private final String nick;
        private final String roleId;
        private final List<String> removeRoleIds;
        private final boolean unlink;

        private SyncJob(String discordId, String nick, String roleId, List<String> removeRoleIds, boolean unlink) {
            this.discordId = discordId;
            this.nick = nick;
            this.roleId = roleId;
            this.removeRoleIds = removeRoleIds;
            this.unlink = unlink;
        }
    }

    private static class EventPost {
        private final String title;
        private final String subtitle;
        private final List<String> lines;
        private final int color;
        private final String field;
        private final String footer;

        private EventPost(String title, String subtitle, List<String> lines, int color, String field, String footer) {
            this.title = title == null || title.isEmpty() ? "Event" : title;
            this.subtitle = subtitle == null ? "" : subtitle;
            this.lines = lines == null ? new ArrayList<String>() : new ArrayList<String>(lines);
            this.color = color;
            this.field = field == null || field.isEmpty() ? "Classement" : field;
            this.footer = footer == null || footer.isEmpty() ? "Draftmc" : footer;
        }

        private String messageBody() {
            StringBuilder sb = new StringBuilder();
            sb.append(title);
            sb.append("\n\n");
            sb.append(field);
            sb.append("\n\n");
            if (lines.isEmpty()) {
                sb.append("Aucun score.");
            } else {
                for (int i = 0; i < lines.size(); i++) {
                    if (i > 0) {
                        sb.append('\n');
                    }
                    sb.append(lines.get(i));
                }
            }
            if (sb.length() > 2000) {
                sb.setLength(2000);
            }
            return sb.toString();
        }

        private String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"content\":\"").append(escape(messageBody())).append('"');
            sb.append(",\"title\":\"").append(escape(title)).append('"');
            sb.append(",\"description\":\"").append(escape(subtitle)).append('"');
            sb.append(",\"color\":").append(color);
            sb.append(",\"field\":\"").append(escape(field)).append('"');
            sb.append(",\"footer\":\"").append(escape(footer)).append('"');
            sb.append(",\"lines\":[");
            for (int i = 0; i < lines.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append('"').append(escape(lines.get(i))).append('"');
            }
            sb.append("]}");
            return sb.toString();
        }
    }
}
