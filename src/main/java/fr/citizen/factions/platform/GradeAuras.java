package fr.citizen.factions.platform;

import fr.citizen.factions.CitizenFactions;
import fr.citizen.factions.core.Grade;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Aura automatique selon le grade (Alpha : eclair blanc, Primodiau : eclair rouge par defaut), dessinee par Citizen Aura.
 * Liaison par reflexion sur fr.citizen.aura.api.CitizenAuraApi : aucun des deux mods n'est obligatoire pour l'autre.
 */
final class GradeAuras {
    private static final String SOURCE = "citizenfactions";
    private static boolean resolved;
    private static Method set, clear;

    private GradeAuras() { }

    private static boolean ready() {
        if (resolved) return set != null;
        resolved = true;
        if (!ModList.get().isLoaded("citizenaura")) return false;
        try {
            Class<?> api = Class.forName("fr.citizen.aura.api.CitizenAuraApi");
            set = api.getMethod("setAutomatic", UUID.class, String.class, String.class, int.class, String.class);
            clear = api.getMethod("clearAutomatic", UUID.class, String.class);
            CitizenFactions.LOGGER.info("Citizen Factions : auras de grade activées via Citizen Aura.");
        } catch (ReflectiveOperationException | RuntimeException e) {
            set = null;
            CitizenFactions.LOGGER.warn("Citizen Factions : Citizen Aura trop ancien (1.2.0 minimum requis pour les auras de grade).");
        }
        return set != null;
    }

    /** Reapplique l'aura de chaque joueur connecte selon son grade actuel. */
    static void refresh(FactionRuntime rt) {
        if (!ready()) return;
        for (ServerPlayer p : rt.server().getPlayerList().getPlayers()) {
            Grade g = rt.service().gradeOf(p.getUUID());
            String spec = g == null ? null : specFor(g.id());
            try {
                if (spec == null) { clear.invoke(null, p.getUUID(), SOURCE); continue; }
                String[] colorStyle = spec.split(":", 2);
                int rgb = Integer.parseInt(colorStyle[0].replace("#", "").strip(), 16);
                String style = colorStyle.length > 1 ? colorStyle[1].strip() : "eclair";
                set.invoke(null, p.getUUID(), p.getGameProfile().getName(), SOURCE, rgb, style);
            } catch (ReflectiveOperationException | RuntimeException e) {
                CitizenFactions.LOGGER.debug("Aura de grade impossible pour {} : {}", p.getGameProfile().getName(), e.toString());
            }
        }
    }

    static void remove(ServerPlayer p) {
        if (!ready()) return;
        try { clear.invoke(null, p.getUUID(), SOURCE); } catch (ReflectiveOperationException | RuntimeException ignored) { }
    }

    private static String specFor(String gradeId) {
        for (String line : Config.get(Config.GRADE_AURAS)) {
            int i = line.indexOf('=');
            if (i > 0 && line.substring(0, i).strip().equalsIgnoreCase(gradeId)) return line.substring(i + 1);
        }
        return null;
    }
}
