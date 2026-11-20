package fr.citizen.factions.core;

import java.util.UUID;

/** Demande d'un joueur sans faction pour rejoindre une faction. */
public record JoinRequest(UUID player, String playerName, String factionId, long at) { }
