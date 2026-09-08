package fr.draftmc.deathban.manager;

import java.util.UUID;

public final class DeathBanEntry {

    private final UUID uuid;
    private final String name;
    private final String world;
    private final long expireAt;

    public DeathBanEntry(UUID uuid, String name, String world, long expireAt) {
        this.uuid = uuid;
        this.name = name;
        this.world = world;
        this.expireAt = expireAt;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public String getWorld() {
        return world;
    }

    public long getExpireAt() {
        return expireAt;
    }

    public boolean isPermanent() {
        return expireAt <= 0L;
    }

    public boolean isExpired() {
        return !isPermanent() && System.currentTimeMillis() >= expireAt;
    }

    public long getRemainingMillis() {
        if (isPermanent()) {
            return -1L;
        }
        return Math.max(0L, expireAt - System.currentTimeMillis());
    }
}
