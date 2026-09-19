package fr.draftmc.tournament;

import fr.draftmc.util.Locations;
import org.bukkit.Location;

public class TournamentArena {
    private final String name;
    private Location spawn1;
    private Location spawn2;
    private Location spectator;
    private TournamentMatch match;

    public TournamentArena(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public Location spawn1() {
        return spawn1;
    }

    public Location spawn2() {
        return spawn2;
    }

    public Location spectator() {
        return spectator;
    }

    public void setSpawn1(Location spawn1) {
        this.spawn1 = spawn1;
    }

    public void setSpawn2(Location spawn2) {
        this.spawn2 = spawn2;
    }

    public void setSpectator(Location spectator) {
        this.spectator = spectator;
    }

    public boolean ready() {
        return spawn1 != null && spawn2 != null && spawn1.getWorld() != null && spawn2.getWorld() != null;
    }

    public boolean free() {
        return match == null;
    }

    public TournamentMatch match() {
        return match;
    }

    public void setMatch(TournamentMatch match) {
        this.match = match;
    }

    public Location midpoint() {
        if (spawn1 == null || spawn2 == null || spawn1.getWorld() == null) {
            return spawn1;
        }
        return new Location(spawn1.getWorld(),
                (spawn1.getX() + spawn2.getX()) / 2D,
                (spawn1.getY() + spawn2.getY()) / 2D,
                (spawn1.getZ() + spawn2.getZ()) / 2D);
    }

    public String serialize(Location loc) {
        return Locations.serialize(loc);
    }
}
