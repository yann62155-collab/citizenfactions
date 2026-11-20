package fr.citizen.factions.ui;

import java.util.List;
import java.util.Map;

/**
 * Description d'un ecran, construite et validee cote SERVEUR puis simplement affichee par le client
 * (meme principe que Citizen Economy : le client ne decide rien, il renvoie seulement l'action cliquee).
 * Styles des lignes : h = titre, n = normal, g = or, r = rouge, m = discret, v = vert (en ligne).
 * emblem : embleme encode (voir Emblem#encode) dessine a gauche de la ligne / du titre ; vide = aucun.
 */
public record View(String id, String title, String emblem, String nonce, String message, String kind,
                   List<Line> lines, List<Field> fields, List<Btn> buttons) {

    public record Btn(String label, String action, Map<String, String> args, String confirm) { }

    public record Line(String text, String style, String emblem, List<Btn> btns) { }

    public record Field(String id, String label, String value, int max) { }
}
