package fr.citizen.factions.core;

/** Erreur metier : le message est destine directement au joueur (francais). */
public final class FactionException extends RuntimeException {
    public FactionException(String message) { super(message); }
}
