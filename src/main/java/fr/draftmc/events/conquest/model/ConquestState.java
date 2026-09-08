package fr.draftmc.events.conquest.model;

public enum ConquestState {
    /** Aucun evenement en cours : configuration des zones possible. */
    WAITING,
    /** Compte a rebours en cours (/conquest start vient d'etre execute), capture pas encore active. */
    STARTING,
    /** Evenement en cours : capture, points, victoire actifs. */
    RUNNING
}
