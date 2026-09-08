package fr.draftmc.events.masterkill.model;

import org.bukkit.ChatColor;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represente une equipe formee manuellement par les joueurs (systeme
 * identique a DraftRoyale : un leader cree l'equipe et invite qui il veut),
 * PAS liee a une vraie faction du serveur.
 */
public class Team {

    private final String name;
    private final ChatColor color;
    private UUID leader;
    private final Set<UUID> members = new LinkedHashSet<>();

    public Team(String name, ChatColor color, UUID leader) {
        this.name = name;
        this.color = color;
        this.leader = leader;
        this.members.add(leader);
    }

    public String getName() {
        return name;
    }

    public ChatColor getColor() {
        return color;
    }

    public UUID getLeader() {
        return leader;
    }

    public void setLeader(UUID leader) {
        this.leader = leader;
    }

    public Set<UUID> getMembers() {
        return members;
    }

    public void addMember(UUID uuid) {
        members.add(uuid);
    }

    public String getDisplayName() {
        return color + name;
    }
}
