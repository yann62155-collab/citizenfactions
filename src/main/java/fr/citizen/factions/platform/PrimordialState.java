package fr.citizen.factions.platform;

import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Qui est un dragon primordial, consultable tres vite (appele a chaque calcul de taille du dragon).
 * Cote serveur : rempli depuis les donnees persistantes du dragon. Cote client : rempli par PrimordialPayload.
 * Aucune classe client ici.
 */
public final class PrimordialState {
    /** UUID du dragon -> identifiant de la faction (cote serveur). */
    static final Map<UUID, String> SERVER = new ConcurrentHashMap<>();
    /** UUID du dragon -> identifiant de la faction (cote client). */
    public static final Map<UUID, String> CLIENT = new ConcurrentHashMap<>();

    private PrimordialState() { }

    public static String kind(Entity e) {
        Map<UUID, String> m = e.level().isClientSide() ? CLIENT : SERVER;
        return m.get(e.getUUID());
    }

    /** Multiplicateur de taille a appliquer a ce dragon (1 = normal). */
    public static float sizeFactor(Entity e) {
        if (kind(e) == null) return 1F;
        try { return Config.PRIMORDIAL_SIZE.get().floatValue(); }
        catch (IllegalStateException notLoaded) { return 2F; }
    }
}
