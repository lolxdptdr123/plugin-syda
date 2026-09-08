package fr.draftmc.events.koth;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.scheduler.BukkitTask;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Verifie les horaires chaque seconde et lance le KOTH de la minute courante.
 */
public class KothScheduler {
    private final KothPlugin plugin;
    private BukkitTask task;
    private String lastFired;

    public KothScheduler(KothPlugin plugin) {
        this.plugin = plugin;
        start();
    }

    public void start() {
        stop();
        task = plugin.getHost().getServer().getScheduler().runTaskTimer(plugin.getHost(), new Runnable() {
            @Override
            public void run() {
                tick();
            }
        }, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        if (!plugin.getConfig().getBoolean("schedule.enabled", true)) {
            return;
        }
        if (plugin.getKothManager().isRunning()) {
            return;
        }
        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);
        String now = hourKey(cal.get(Calendar.DAY_OF_WEEK), hour, minute);
        if (now.equals(lastFired)) {
            return;
        }
        List<KothScheduleEntry> entries = loadEntries();
        for (int i = 0; i < entries.size(); i++) {
            KothScheduleEntry entry = entries.get(i);
            if (entry.dayOfWeek != cal.get(Calendar.DAY_OF_WEEK)
                    || entry.hour != hour
                    || entry.minute != minute) {
                continue;
            }
            KothZone zone = plugin.getKothManager().get(entry.zoneId);
            if (zone == null) {
                continue;
            }
            lastFired = now;
            if (plugin.getKothManager().start(zone.getId())) {
                plugin.broadcastScheduled(zone, entry.dayLabel, entry.timeLabel);
                if (plugin.getHost().events() != null) {
                    plugin.getHost().events().markActive(fr.draftmc.events.EventType.KOTH, zone.getId());
                }
            }
            return;
        }
    }

    public List<KothScheduleEntry> loadEntries() {
        List<KothScheduleEntry> out = new java.util.ArrayList<KothScheduleEntry>();
        List<?> raw = plugin.getConfig().getMapList("schedule.entries");
        if (raw != null && !raw.isEmpty()) {
            for (int i = 0; i < raw.size(); i++) {
                Object item = raw.get(i);
                if (!(item instanceof java.util.Map)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> map = (java.util.Map<String, Object>) item;
                KothScheduleEntry entry = parse(String.valueOf(map.get("day")),
                        String.valueOf(map.get("time")),
                        String.valueOf(map.get("zone")));
                if (entry != null) {
                    out.add(entry);
                }
            }
            return out;
        }
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("schedule.entries");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection node = section.getConfigurationSection(key);
                if (node == null) {
                    continue;
                }
                KothScheduleEntry entry = parse(node.getString("day"), node.getString("time"), node.getString("zone"));
                if (entry != null) {
                    out.add(entry);
                }
            }
        }
        return out;
    }

    public boolean add(String day, String time, String zoneId) {
        KothScheduleEntry entry = parse(day, time, zoneId);
        if (entry == null || plugin.getKothManager().get(zoneId) == null) {
            return false;
        }
        List<java.util.Map<String, Object>> list = new java.util.ArrayList<java.util.Map<String, Object>>();
        List<KothScheduleEntry> current = loadEntries();
        for (int i = 0; i < current.size(); i++) {
            list.add(current.get(i).toMap());
        }
        list.add(entry.toMap());
        plugin.getConfig().set("schedule.entries", list);
        plugin.saveConfig();
        return true;
    }

    public boolean remove(int index) {
        List<KothScheduleEntry> current = loadEntries();
        if (index < 0 || index >= current.size()) {
            return false;
        }
        current.remove(index);
        List<java.util.Map<String, Object>> list = new java.util.ArrayList<java.util.Map<String, Object>>();
        for (int i = 0; i < current.size(); i++) {
            list.add(current.get(i).toMap());
        }
        plugin.getConfig().set("schedule.entries", list);
        plugin.saveConfig();
        return true;
    }

    static KothScheduleEntry parse(String dayRaw, String timeRaw, String zoneId) {
        if (dayRaw == null || timeRaw == null || zoneId == null || "null".equals(zoneId)) {
            return null;
        }
        Integer dow = dayOfWeek(dayRaw);
        if (dow == null) {
            return null;
        }
        String[] parts = timeRaw.trim().split(":");
        if (parts.length < 2) {
            return null;
        }
        try {
            int hour = Integer.parseInt(parts[0].trim());
            int minute = Integer.parseInt(parts[1].trim());
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                return null;
            }
            return new KothScheduleEntry(dow.intValue(), hour, minute,
                    zoneId.toLowerCase(Locale.ROOT), dayRaw, String.format("%02d:%02d", hour, minute));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static Integer dayOfWeek(String raw) {
        String n = raw.trim().toLowerCase(Locale.ROOT);
        if ("sunday".equals(n) || "dimanche".equals(n) || "dim".equals(n)) {
            return Calendar.SUNDAY;
        }
        if ("monday".equals(n) || "lundi".equals(n) || "lun".equals(n)) {
            return Calendar.MONDAY;
        }
        if ("tuesday".equals(n) || "mardi".equals(n) || "mar".equals(n)) {
            return Calendar.TUESDAY;
        }
        if ("wednesday".equals(n) || "mercredi".equals(n) || "mer".equals(n)) {
            return Calendar.WEDNESDAY;
        }
        if ("thursday".equals(n) || "jeudi".equals(n) || "jeu".equals(n)) {
            return Calendar.THURSDAY;
        }
        if ("friday".equals(n) || "vendredi".equals(n) || "ven".equals(n)) {
            return Calendar.FRIDAY;
        }
        if ("saturday".equals(n) || "samedi".equals(n) || "sam".equals(n)) {
            return Calendar.SATURDAY;
        }
        return null;
    }

    private static String hourKey(int day, int hour, int minute) {
        return day + "-" + hour + "-" + minute;
    }

    public static class KothScheduleEntry {
        final int dayOfWeek;
        final int hour;
        final int minute;
        final String zoneId;
        final String dayLabel;
        final String timeLabel;

        KothScheduleEntry(int dayOfWeek, int hour, int minute, String zoneId, String dayLabel, String timeLabel) {
            this.dayOfWeek = dayOfWeek;
            this.hour = hour;
            this.minute = minute;
            this.zoneId = zoneId;
            this.dayLabel = dayLabel;
            this.timeLabel = timeLabel;
        }

        java.util.Map<String, Object> toMap() {
            java.util.Map<String, Object> map = new java.util.LinkedHashMap<String, Object>();
            map.put("day", dayLabel);
            map.put("time", timeLabel);
            map.put("zone", zoneId);
            return map;
        }
    }
}
