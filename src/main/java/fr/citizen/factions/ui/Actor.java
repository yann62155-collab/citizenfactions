package fr.citizen.factions.ui;

import java.util.UUID;

/** Qui agit : identite et statut administrateur determines par le SERVEUR, jamais par le client. */
public record Actor(UUID uuid, String name, boolean admin) { }
