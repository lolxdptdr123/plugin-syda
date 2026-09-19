package fr.draftmc.events;

import fr.draftmc.Draftmc;
import fr.draftmc.util.CC;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Lance les events du planning events.yml à l'heure indiquée (auto-start).
 */
public class EventScheduler {
    private static final String[] DAYS = {
            "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"
    };

    private final Draftmc plugin;
    private BukkitTask task;
    private String lastFired = "";

    public EventScheduler(Draftmc plugin) {
        this.plugin = plugin;
        start();
    }

    public void start() {
        shutdown();
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                tick();
            }
        }, 20L * 15L, 20L * 15L);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        EventHub hub = plugin.events();
        if (hub == null) {
            return;
        }
        FileConfiguration catalog = hub.catalog();
        if (catalog == null || !catalog.getBoolean("schedule.auto-start", true)) {
            return;
        }
        Calendar cal = Calendar.getInstance();
        int dayIndex = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        String day = DAYS[dayIndex];
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);
        String now = EventHub.formatClock(hour, minute);
        List<EventHub.Slot> slots = hub.scheduleSlots(day);
        for (int i = 0; i < slots.size(); i++) {
            EventHub.Slot slot = slots.get(i);
            if (!slot.auto || slot.hour != hour || slot.minute != minute) {
                continue;
            }
            String eventName = slot.event;
            String mapId = slot.map == null || slot.map.isEmpty() ? "default" : slot.map;
            EventType type = EventType.from(eventName);
            if (type == null) {
                continue;
            }
            String key = day + "-" + now + "-" + type.id() + "-" + mapId.toLowerCase(Locale.ROOT);
            if (key.equals(lastFired)) {
                continue;
            }
            lastFired = key;
            EventHub.StartResult result = hub.start(type, mapId);
            String mapName = hub.mapDisplay(type, mapId);
            if (result == EventHub.StartResult.STARTED
                    || result == EventHub.StartResult.REGISTRATION_OPENED
                    || result == EventHub.StartResult.LAUNCHED) {
                Bukkit.broadcastMessage(CC.color(hub.prefix()
                        + "&e" + type.display() + " &7démarre automatiquement (&e" + mapName + "&7)."));
            } else {
                plugin.getLogger().info("Auto-event " + type.id() + " " + mapId + " : " + result.name());
            }
        }
    }
}
