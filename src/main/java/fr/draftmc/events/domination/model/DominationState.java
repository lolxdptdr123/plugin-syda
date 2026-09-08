package fr.draftmc.events.domination.model;

public enum DominationState {
    /** Aucun evenement en cours : configuration des zones possible. */
    WAITING,
    /** Compte a rebours en cours (/domination start vient d'etre execute), scoring pas encore actif. */
    STARTING,
    /** Evenement en cours : scoring, victoire actifs. */
    RUNNING
}
