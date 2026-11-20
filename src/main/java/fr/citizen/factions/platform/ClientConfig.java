package fr.citizen.factions.platform;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Configuration client (config/citizenfactions-client.toml). Ne contient aucune classe client : chargeable partout. */
public final class ClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue HUD_OFFSET_Y;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        HUD_OFFSET_Y = b.comment("Hauteur de la barre de lien au-dessus de la barre d'inventaire (en pixels d'interface).")
                .defineInRange("bondHudOffsetY", 50, 0, 400);
        SPEC = b.build();
    }

    private ClientConfig() { }
}
