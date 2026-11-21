package fr.citizen.factions.platform;

import fr.citizen.factions.core.Faction;
import fr.citizen.factions.core.Grade;

import java.util.List;
import java.util.UUID;

/** Lecture des listes de config « grade=valeur » / « faction/grade=valeur » / « * » / « aucune ». */
final class GradeValues {
    private GradeValues() { }

    /** @return la valeur pour ce joueur, ou null si rien ne correspond. */
    static String lookup(FactionRuntime rt, UUID player, List<? extends String> entries) {
        Faction f = rt.service().factionOf(player);
        Grade g = rt.service().gradeOf(player);
        String exact = f == null || g == null ? null : f.id() + "/" + g.id();
        String grade = g == null ? null : g.id();
        String any = null, grad = null;
        for (String line : entries) {
            int i = line.indexOf('=');
            if (i <= 0) continue;
            String key = line.substring(0, i).strip().toLowerCase(java.util.Locale.ROOT);
            String val = line.substring(i + 1).strip();
            if (f == null) { if (key.equals("aucune")) return val; continue; }
            if (key.equals(exact)) return val;
            if (key.equals(grade)) grad = val;
            else if (key.equals("*")) any = val;
        }
        return grad != null ? grad : any;
    }

    static double number(FactionRuntime rt, UUID player, List<? extends String> entries, double def) {
        String v = lookup(rt, player, entries);
        if (v == null) return def;
        try { return Double.parseDouble(v.replace(',', '.')); } catch (NumberFormatException e) { return def; }
    }
}
