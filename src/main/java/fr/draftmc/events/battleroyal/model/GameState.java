package fr.draftmc.events.battleroyal.model;

public enum GameState {
    /** Rien n'est en cours : aucune equipe ne peut se creer. */
    WAITING,
    /** Inscriptions ouvertes par un admin (/br start) : les teams peuvent se creer. */
    REGISTRATION,
    /** Partie en cours (/br launch). */
    INGAME,
    /** Fin de manche, retour automatique vers WAITING. */
    ENDING
}
