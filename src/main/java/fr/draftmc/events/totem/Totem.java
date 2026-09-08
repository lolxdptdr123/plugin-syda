package fr.draftmc.events.totem;

import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class Totem {
    private final String name;
    private final List<Block> blocks = new ArrayList<Block>();
    private Location location;
    private Material blockMaterial = Material.QUARTZ_BLOCK;
    private Material itemInteract = Material.DIAMOND_SWORD;
    private int size = 5;
    private int actualSize = 5;
    private String capturingFactionId;
    private String lastBreakerName;
    private String lastBreakerFaction;
    /** Un nom par etage, index 0 = bloc du bas. Null = pas encore casse. */
    private final List<String> breakers = new ArrayList<String>();
    private TotemStatus status = TotemStatus.WAITING;
    private boolean giant;
    private int lastGained;
    private int lastBonus;
    private int lastBlockNumber;

    public Totem(String name) {
        this.name = name;
    }

    public void generate() {
        blocks.clear();
        if (location == null || location.getWorld() == null) {
            return;
        }
        for (int i = 0; i < size; i++) {
            blocks.add(location.clone().add(0, i, 0).getBlock());
        }
    }

    public void spawn() {
        generate();
        for (Block block : blocks) {
            block.setType(blockMaterial);
        }
        capturingFactionId = null;
        lastBreakerName = null;
        lastBreakerFaction = null;
        lastGained = 0;
        lastBonus = 0;
        lastBlockNumber = 0;
        resetBreakers();
        actualSize = size;
        status = TotemStatus.STARTED;
    }

    public void finishWithBedrock() {
        generate();
        for (Block block : blocks) {
            block.setType(Material.BEDROCK);
        }
        status = TotemStatus.FINISHED;
    }

    public void stop() {
        finishWithBedrock();
        capturingFactionId = null;
    }

    public void reset() {
        capturingFactionId = null;
        lastBreakerName = null;
        lastBreakerFaction = null;
        lastGained = 0;
        lastBonus = 0;
        lastBlockNumber = 0;
        resetBreakers();
        actualSize = size;
        generate();
        for (Block block : blocks) {
            block.setType(blockMaterial);
        }
        status = TotemStatus.STARTED;
    }

    public void playerBreak(TotemPlugin plugin, Player player, Block broken) {
        String factionId = plugin.getEventFactionHook().getFactionId(player);
        if (factionId == null) {
            return;
        }
        if (capturingFactionId != null && !capturingFactionId.equals(factionId)) {
            lastBreakerName = player.getName();
            lastBreakerFaction = plugin.getEventFactionHook().getFactionDisplayName(factionId);
            lastGained = 0;
            lastBonus = 0;
            lastBlockNumber = 0;
            int index = broken == null ? -1 : blocks.indexOf(broken);
            if (giant && index >= 0) {
                lastBlockNumber = size - index;
                lastGained = plugin.blockPointsFor(this, index);
                if (lastGained > 0) {
                    plugin.getTotemManager().addScore(factionId, lastGained);
                }
                plugin.broadcast("break-cancel-giant", this, player);
            } else {
                plugin.broadcast("break-cancel", this, player);
            }
            reset();
            return;
        }
        if (broken != null) {
            broken.setType(Material.AIR);
        }
        if (location != null && location.getWorld() != null && broken != null) {
            location.getWorld().playEffect(broken.getLocation(), Effect.EXPLOSION, 1);
        }
        if (capturingFactionId == null) {
            capturingFactionId = factionId;
        }
        lastBreakerName = player.getName();
        lastBreakerFaction = plugin.getEventFactionHook().getFactionDisplayName(factionId);
        int index = broken == null ? -1 : blocks.indexOf(broken);
        lastGained = 0;
        lastBonus = 0;
        lastBlockNumber = 0;
        if (index >= 0) {
            while (breakers.size() <= index) {
                breakers.add(null);
            }
            breakers.set(index, player.getName());
            lastBlockNumber = size - index;
            if (giant) {
                lastGained = plugin.blockPointsFor(this, index);
                plugin.getTotemManager().addScore(factionId, lastGained);
            }
        }
        actualSize--;
        plugin.broadcast(giant ? "break-giant" : "break", this, player);
        if (actualSize <= 0) {
            if (giant) {
                lastBonus = plugin.oneshotBonus();
                if (lastBonus > 0) {
                    plugin.getTotemManager().addScore(factionId, lastBonus);
                }
                plugin.broadcast("oneshot", this, player);
                spawn();
            } else {
                plugin.victory(this, player);
            }
        }
    }

    public boolean contains(Block block) {
        return block != null && blocks.contains(block);
    }

    public String getName() {
        return name;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location == null ? null : location.clone();
    }

    public Material getBlockMaterial() {
        return blockMaterial;
    }

    public void setBlockMaterial(Material blockMaterial) {
        this.blockMaterial = blockMaterial;
    }

    public Material getItemInteract() {
        return itemInteract;
    }

    public void setItemInteract(Material itemInteract) {
        this.itemInteract = itemInteract;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = Math.max(1, size);
        if (status != TotemStatus.STARTED) {
            this.actualSize = this.size;
        }
    }

    public int getActualSize() {
        return actualSize;
    }

    public String getCapturingFactionId() {
        return capturingFactionId;
    }

    public void clearCapturingFaction() {
        this.capturingFactionId = null;
    }

    public TotemStatus getStatus() {
        return status;
    }

    public void setStatusWaiting() {
        this.status = TotemStatus.WAITING;
        this.capturingFactionId = null;
        this.lastBreakerName = null;
        this.lastBreakerFaction = null;
        this.lastGained = 0;
        this.lastBonus = 0;
        this.lastBlockNumber = 0;
        this.giant = false;
        resetBreakers();
        this.actualSize = size;
    }

    public void setStatusStarting() {
        this.status = TotemStatus.STARTING;
        this.capturingFactionId = null;
        this.lastBreakerName = null;
        this.lastBreakerFaction = null;
        this.lastGained = 0;
        this.lastBonus = 0;
        this.lastBlockNumber = 0;
        resetBreakers();
        this.actualSize = size;
    }

    public boolean isGiant() {
        return giant;
    }

    public void setGiant(boolean giant) {
        this.giant = giant;
    }

    public int getLastGained() {
        return lastGained;
    }

    public int getLastBonus() {
        return lastBonus;
    }

    public int getLastBlockNumber() {
        return lastBlockNumber;
    }

    public String getLastBreakerName() {
        return lastBreakerName;
    }

    public String getLastBreakerFaction() {
        return lastBreakerFaction;
    }

    public List<String> getBreakers() {
        return breakers;
    }

    /** Nom du joueur qui a casse l'etage, ou null. Index 0 = bas du totem. */
    public String getBreakerAt(int blockIndex) {
        if (blockIndex < 0 || blockIndex >= breakers.size()) {
            return null;
        }
        return breakers.get(blockIndex);
    }

    private void resetBreakers() {
        breakers.clear();
        for (int i = 0; i < size; i++) {
            breakers.add(null);
        }
    }

    public int getBrokenCount() {
        return Math.max(0, size - actualSize);
    }

    public List<Block> getBlocks() {
        return blocks;
    }
}
