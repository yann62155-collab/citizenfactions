package fr.citizen.factions.core;

/**
 * Lien d'UN joueur avec UN dragon. La valeur stockee n'est mise a jour que lorsqu'on monte le dragon ;
 * la baisse due a l'abandon est calculee a partir de l'heure reelle (donc aussi serveur eteint ou joueur deconnecte).
 *
 * @param value      valeur (0..100) au moment de lastUpdate
 * @param lastRide   derniere fois (ms epoch) que le joueur montait le dragon ; 0 = jamais
 * @param lastUpdate moment (ms epoch) ou value a ete ecrite
 */
public record Bond(double value, long lastRide, long lastUpdate) {
    public static final Bond NONE = new Bond(0, 0, 0);
}
