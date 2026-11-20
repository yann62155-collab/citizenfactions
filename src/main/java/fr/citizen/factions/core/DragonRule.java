package fr.citizen.factions.core;

/** Quels dragons les membres d'une faction ont le droit de monter. */
public enum DragonRule {
    NON_CORROMPUS("Tous les dragons sauf les corrompus"),
    CORROMPUS("Uniquement les dragons corrompus"),
    TOUS("Tous les dragons"),
    AUCUN("Aucun dragon");

    private final String label;

    DragonRule(String label) { this.label = label; }

    public String label() { return label; }

    public boolean allows(boolean corrupted) {
        return switch (this) {
            case NON_CORROMPUS -> !corrupted;
            case CORROMPUS -> corrupted;
            case TOUS -> true;
            case AUCUN -> false;
        };
    }

    public DragonRule next() { return values()[(ordinal() + 1) % values().length]; }

    public static DragonRule parse(String s) {
        for (DragonRule r : values()) if (r.name().equalsIgnoreCase(s)) return r;
        throw new FactionException("Règle de dragon inconnue : " + s + " (NON_CORROMPUS, CORROMPUS, TOUS, AUCUN)");
    }
}
