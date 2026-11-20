package fr.citizen.factions.core;

/** Droits qu'un grade peut accorder a ses membres, a l'interieur de leur faction uniquement. */
public enum FactionPermission {
    VOIR_MEMBRES("Voir les membres"),
    VOIR_DRAGONS("Voir les dragons des membres"),
    GERER_DEMANDES("Accepter / refuser les demandes"),
    PROMOUVOIR("Changer le grade des membres inférieurs"),
    EXCLURE("Exclure les membres inférieurs");

    private final String label;

    FactionPermission(String label) { this.label = label; }

    public String label() { return label; }

    public static FactionPermission parse(String s) {
        for (FactionPermission p : values()) if (p.name().equalsIgnoreCase(s)) return p;
        throw new FactionException("Permission inconnue : " + s);
    }
}
