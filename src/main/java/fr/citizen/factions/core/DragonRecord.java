package fr.citizen.factions.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Fiche d'un dragon Ice and Fire connu du mod. La source de verite du lien est l'entite elle-meme
 * (donnees persistantes du dragon) ; cette fiche en est le miroir, pour les dragons non charges et les commandes.
 */
public final class DragonRecord {
    private final UUID uuid;
    private String species = "";
    private String variant = "";
    private boolean corrupted;
    private String customName = "";
    private UUID owner;
    private String dimension = "";
    private int x, y, z;
    private int stage;
    private String primordial = "";
    private long lastSeen;
    private final Map<UUID, Bond> bonds = new LinkedHashMap<>();

    public DragonRecord(UUID uuid) { this.uuid = uuid; }

    public UUID uuid() { return uuid; }
    public String species() { return species; }
    public String variant() { return variant; }
    public boolean corrupted() { return corrupted; }
    public String customName() { return customName; }
    public UUID owner() { return owner; }
    public String dimension() { return dimension; }
    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }
    public int stage() { return stage; }
    public long lastSeen() { return lastSeen; }
    public Map<UUID, Bond> bonds() { return bonds; }
    /** Faction a laquelle ce dragon primordial est reserve ; vide = dragon ordinaire. */
    public String primordial() { return primordial; }
    public void primordial(String factionId) { this.primordial = factionId == null ? "" : factionId; }

    public Bond bond(UUID player) { return bonds.getOrDefault(player, Bond.NONE); }

    public void describe(String species, String variant, boolean corrupted, String customName, UUID owner, int stage) {
        this.species = species == null ? "" : species;
        this.variant = variant == null ? "" : variant;
        this.corrupted = corrupted;
        this.customName = customName == null ? "" : customName;
        this.owner = owner;
        this.stage = stage;
    }

    public void seen(String dimension, int x, int y, int z, long now) {
        this.dimension = dimension; this.x = x; this.y = y; this.z = z; this.lastSeen = now;
    }

    /** Nom lisible : nom donne par le joueur, sinon espece + couleur. */
    public String displayName() {
        if (!customName.isBlank()) return customName;
        if (!primordial.isBlank()) return speciesLabel() + " primordial";
        return speciesLabel() + (variant.isBlank() ? "" : " " + variant);
    }

    public String speciesLabel() {
        return switch (species) {
            case "fire" -> "Dragon de feu";
            case "ice" -> "Dragon de glace";
            case "lightning" -> "Dragon de foudre";
            default -> "Dragon";
        };
    }
}
