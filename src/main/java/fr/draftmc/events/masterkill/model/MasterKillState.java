package fr.draftmc.events.masterkill.model;

public enum MasterKillState {
    /** Aucun match en cours. */
    WAITING,
    /** Inscriptions ouvertes (/masterkill start) : les equipes peuvent se creer. */
    REGISTRATION,
    /** Match en cours (/masterkill launch) : elimination et kills actifs. */
    RUNNING
}
