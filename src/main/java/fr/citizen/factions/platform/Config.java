package fr.citizen.factions.platform;

import fr.citizen.factions.core.BondSettings;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/** Configuration serveur (fichier world/serverconfig/citizenfactions-server.toml), modifiable a chaud. */
public final class Config {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue ADMIN_LEVEL;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ADMIN_PLAYERS;

    public static final ModConfigSpec.ConfigValue<List<? extends String>> DRAGON_TYPES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> CORRUPTED;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> EGG_PREFIXES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> CORRUPTED_EGGS;
    public static final ModConfigSpec.BooleanValue BLOCK_EGGS;
    public static final ModConfigSpec.BooleanValue BLOCK_INTERACTIONS;
    public static final ModConfigSpec.BooleanValue UNTAME_FACTIONLESS;
    public static final ModConfigSpec.BooleanValue ADMIN_BYPASS;

    public static final ModConfigSpec.DoubleValue GAIN_PER_MINUTE;
    public static final ModConfigSpec.DoubleValue LOSS_PER_HOUR;
    public static final ModConfigSpec.DoubleValue GRACE_HOURS;
    public static final ModConfigSpec.DoubleValue MAX_REFUSE;
    public static final ModConfigSpec.BooleanValue BEHAVIOUR;

    public static final ModConfigSpec.DoubleValue PRIMORDIAL_SIZE;
    public static final ModConfigSpec.DoubleValue PRIMORDIAL_HEALTH;
    public static final ModConfigSpec.DoubleValue PRIMORDIAL_DAMAGE;
    public static final ModConfigSpec.DoubleValue PRIMORDIAL_SPEED;
    public static final ModConfigSpec.DoubleValue PRIMORDIAL_ARMOR;
    public static final ModConfigSpec.IntValue PRIMORDIAL_BURN_SECONDS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Administration de Citizen Factions").push("admin");
        ADMIN_LEVEL = b.comment("Niveau de permission vanilla (op) donnant accès à l'administration des factions. 4 = opérateurs complets, 5 = personne par ce moyen.")
                .defineInRange("adminPermissionLevel", 4, 0, 5);
        ADMIN_PLAYERS = b.comment("Pseudos ou UUID supplémentaires autorisés à administrer les factions.")
                .defineListAllowEmpty("adminPlayers", List.of(), () -> "", o -> o instanceof String);
        b.pop();

        b.comment("Règles des dragons (Ice and Fire: Dragons, portage non officiel)").push("dragons");
        DRAGON_TYPES = b.comment("Types d'entités considérés comme des dragons (identifiants de registre).")
                .defineListAllowEmpty("dragonEntityTypes",
                        List.of("iceandfire:fire_dragon", "iceandfire:ice_dragon", "iceandfire:lightning_dragon"),
                        () -> "iceandfire:fire_dragon", o -> o instanceof String);
        CORRUPTED = b.comment("Dragons corrompus : « type#variante ». Variantes Ice and Fire :",
                        " feu 0=rouge 1=vert 2=bronze 3=gris ; glace 0=bleu 1=blanc 2=saphir 3=argent ; foudre 0=électrique 1=améthyste 2=cuivre 3=noir.",
                        " Par défaut : Gris (feu #3), Cuivre (foudre #2), Noir (foudre #3).")
                .defineListAllowEmpty("corruptedDragons",
                        List.of("iceandfire:fire_dragon#3", "iceandfire:lightning_dragon#2", "iceandfire:lightning_dragon#3"),
                        () -> "iceandfire:fire_dragon#3", o -> o instanceof String);
        EGG_PREFIXES = b.comment("Objets considérés comme des œufs de dragon (début de l'identifiant).")
                .defineListAllowEmpty("dragonEggItems", List.of("iceandfire:dragonegg_"), () -> "iceandfire:dragonegg_", o -> o instanceof String);
        CORRUPTED_EGGS = b.comment("Œufs donnant un dragon corrompu.")
                .defineListAllowEmpty("corruptedEggItems",
                        List.of("iceandfire:dragonegg_gray", "iceandfire:dragonegg_copper", "iceandfire:dragonegg_black"),
                        () -> "iceandfire:dragonegg_gray", o -> o instanceof String);
        BLOCK_EGGS = b.comment("Empêche de poser un œuf de dragon sans faction, ou un œuf que la faction n'a pas le droit d'élever.")
                .define("blockEggs", true);
        BLOCK_INTERACTIONS = b.comment("Un joueur sans faction ne peut plus interagir avec ses dragons apprivoisés (nourrir, ordres, inventaire).")
                .define("blockFactionlessInteractions", true);
        UNTAME_FACTIONLESS = b.comment("Rend sauvages les dragons dont le propriétaire est connecté mais sans faction (le lien est conservé). Désactivé par défaut.")
                .define("untameDragonsOfFactionlessOwners", false);
        ADMIN_BYPASS = b.comment("Les administrateurs des factions ignorent les règles des dragons (utile pour la modération).")
                .define("adminsBypassRules", true);
        b.pop();

        b.comment("Lien entre un joueur et son dragon").push("bond");
        GAIN_PER_MINUTE = b.comment("Points de lien (sur 100) gagnés par minute passée à monter son dragon.")
                .defineInRange("gainPerMinute", 0.75, 0.0, 100.0);
        LOSS_PER_HOUR = b.comment("Points de lien perdus par heure d'abandon (temps réel, même serveur éteint).")
                .defineInRange("lossPerHour", 1.0, 0.0, 100.0);
        GRACE_HOURS = b.comment("Heures sans monter son dragon avant que le lien commence à baisser.")
                .defineInRange("abandonGraceHours", 12.0, 0.0, 8760.0);
        MAX_REFUSE = b.comment("Probabilité (0 à 1) qu'un dragon refuse d'être monté avec un lien de 0 % (diminue jusqu'à 0 à 60 % de lien).")
                .defineInRange("maxMountRefuseChance", 0.35, 0.0, 1.0);
        BEHAVIOUR = b.comment("Active les effets du lien sur le dragon (refus, mouvements brusques, désarçonnage, vitesse, régénération).")
                .define("behaviourEffects", true);
        b.pop();

        b.comment("Dragons primordiaux (un par faction, réservés au grade ayant MONTER_PRIMORDIAL, Primodiau par défaut)").push("primordial");
        PRIMORDIAL_SIZE = b.comment("Taille par rapport à un dragon normal du même âge.").defineInRange("sizeMultiplier", 2.0, 1.0, 4.0);
        PRIMORDIAL_HEALTH = b.comment("Points de vie maximum (multiplicateur).").defineInRange("healthMultiplier", 2.0, 1.0, 10.0);
        PRIMORDIAL_DAMAGE = b.comment("Dégâts infligés : morsure, griffes et souffle (multiplicateur).").defineInRange("damageMultiplier", 2.0, 1.0, 10.0);
        PRIMORDIAL_SPEED = b.comment("Vitesse (multiplicateur). Baissez-la si le dragon devient difficile à piloter.").defineInRange("speedMultiplier", 2.0, 1.0, 4.0);
        PRIMORDIAL_ARMOR = b.comment("Armure (multiplicateur).").defineInRange("armorMultiplier", 2.0, 1.0, 10.0);
        PRIMORDIAL_BURN_SECONDS = b.comment("Secondes d'embrasement des cibles touchées par le souffle d'un primordial.").defineInRange("burnSeconds", 8, 0, 60);
        b.pop();
        SPEC = b.build();
    }

    private Config() { }

    public static BondSettings bond() {
        try {
            return new BondSettings(GAIN_PER_MINUTE.get(), LOSS_PER_HOUR.get(), GRACE_HOURS.get(), MAX_REFUSE.get());
        } catch (IllegalStateException notLoaded) {
            return BondSettings.DEFAULT;
        }
    }

    /** Lecture tolerante (config pas encore chargee = valeur par defaut). */
    public static <T> T get(ModConfigSpec.ConfigValue<T> v) {
        try { return v.get(); } catch (IllegalStateException e) { return v.getDefault(); }
    }
}
