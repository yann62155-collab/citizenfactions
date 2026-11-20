package fr.citizen.factions.core;

/** Calculs purs du lien : progression, abandon et effets sur le comportement du dragon. */
public final class BondMath {
    private static final double HOUR_MS = 3_600_000d;

    private BondMath() { }

    public static double clamp(double v) { return Math.max(0, Math.min(100, v)); }

    /** Valeur actuelle du lien en tenant compte de l'abandon. */
    public static double effective(Bond b, long now, BondSettings s) {
        if (b == null) return 0;
        double v = clamp(b.value());
        if (b.lastRide() <= 0 || s.lossPerHour() <= 0) return v;
        long decayStart = Math.max(b.lastRide() + (long) (s.graceHours() * HOUR_MS), b.lastUpdate());
        if (now <= decayStart) return v;
        return clamp(v - s.lossPerHour() * ((now - decayStart) / HOUR_MS));
    }

    /** Nouveau lien apres avoir monte le dragon pendant elapsedMs. */
    public static Bond ride(Bond b, long now, long elapsedMs, BondSettings s) {
        double v = effective(b, now, s);
        v = clamp(v + s.gainPerMinute() * (elapsedMs / 60_000d));
        return new Bond(v, now, now);
    }

    /** Probabilite qu'une tentative de monte soit refusee (0 a partir de 60 %). */
    public static double refuseChance(double bond, BondSettings s) {
        if (bond >= 60) return 0;
        return s.maxRefuseChance() * (60 - bond) / 60d;
    }

    /** Probabilite, par seconde de vol, d'un mouvement brusque (0 a partir de 50 %). */
    public static double jerkChancePerSecond(double bond) {
        if (bond >= 50) return 0;
        return 0.06 * (50 - bond) / 50d;
    }

    /** Probabilite, par seconde, que le dragon desarconne son cavalier (lien tres faible uniquement). */
    public static double buckChancePerSecond(double bond) {
        if (bond >= 10) return 0;
        return 0.01 * (10 - bond) / 10d;
    }

    /** Modificateur de vitesse : -25 % a 0 de lien, 0 vers 71 %, +10 % a 100 %. */
    public static double speedModifier(double bond) {
        return -0.25 + 0.35 * clamp(bond) / 100d;
    }

    /** Points de vie regeneres par seconde de monte (lien eleve uniquement). */
    public static double regenPerSecond(double bond) {
        if (bond < 80) return 0;
        return 0.5 * (clamp(bond) - 80) / 20d;
    }

    public static String tier(double bond) {
        if (bond < 25) return "Sauvage";
        if (bond < 50) return "Méfiant";
        if (bond < 75) return "Coopératif";
        if (bond < 95) return "Fidèle";
        return "Lié à vie";
    }

    public static String percent(double bond) {
        return String.format(java.util.Locale.ROOT, "%.1f %%", clamp(bond)).replace('.', ',');
    }
}
