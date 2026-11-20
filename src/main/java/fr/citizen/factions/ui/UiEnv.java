package fr.citizen.factions.ui;

import fr.citizen.factions.core.BondSettings;

import java.util.UUID;

/** Ce dont l'interface a besoin du serveur Minecraft (fourni par la plateforme, simule dans les tests). */
public interface UiEnv {
    record PlayerRef(UUID uuid, String name) { }

    boolean online(UUID player);

    /** Retrouve un joueur (connecte ou deja venu sur le serveur) par son pseudo ; null si inconnu. */
    PlayerRef resolve(String name);

    BondSettings bond();

    /** Message prive a un joueur s'il est connecte. */
    void notify(UUID player, String message);

    /** Previent les membres connectes d'une faction ayant le droit indique (ex. : nouvelle demande). */
    void notifyFaction(String factionId, fr.citizen.factions.core.FactionPermission perm, String message);
}
