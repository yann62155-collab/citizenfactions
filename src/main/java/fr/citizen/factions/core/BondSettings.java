package fr.citizen.factions.core;

/**
 * Reglages du lien joueur / dragon.
 * @param gainPerMinute   points de lien (sur 100) gagnes par minute passee a monter le dragon
 * @param lossPerHour     points perdus par heure d'abandon, une fois le delai de grace ecoule
 * @param graceHours      heures sans monter le dragon avant que le lien ne commence a baisser
 * @param maxRefuseChance probabilite (0..1) que le dragon refuse d'etre monte quand le lien est a 0
 */
public record BondSettings(double gainPerMinute, double lossPerHour, double graceHours, double maxRefuseChance) {
    public static final BondSettings DEFAULT = new BondSettings(0.75, 1.0, 12.0, 0.35);
}
