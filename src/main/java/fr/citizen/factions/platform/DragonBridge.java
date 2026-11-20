package fr.citizen.factions.platform;

import fr.citizen.factions.CitizenFactions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Integration Ice and Fire SANS dependance de compilation : un dragon est reconnu par son TYPE D'ENTITE
 * (iceandfire:fire_dragon...) et sa couleur par la VARIANTE INTERNE de l'entite (getVariant(), ou la donnee
 * sauvegardee "Variant"), jamais par le nom affiche. Les dragons d'Ice and Fire etendent TamableAnimal (vanilla),
 * ce qui donne directement proprietaire et apprivoisement.
 */
public final class DragonBridge {
    public record Info(Entity entity, String typeId, String species, int variant, String variantLabel, boolean corrupted, int stage,
                       boolean tamed, UUID owner) { }

    private static final ConcurrentHashMap<Class<?>, Method> VARIANT = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, Method> STAGE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, Method> PARENT = new ConcurrentHashMap<>();
    private static final Method NONE;
    static {
        try { NONE = Object.class.getMethod("hashCode"); } catch (NoSuchMethodException e) { throw new IllegalStateException(e); }
    }

    private DragonBridge() { }

    public static String typeId(Entity e) {
        ResourceLocation k = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
        return k == null ? "" : k.toString();
    }

    public static boolean isDragon(Entity e) {
        if (e == null) return false;
        String id = typeId(e);
        for (String t : Config.get(Config.DRAGON_TYPES)) if (t.equalsIgnoreCase(id)) return true;
        return false;
    }

    /** Les dragons Ice and Fire ont plusieurs hitbox (parties) : on remonte a l'entite principale. */
    public static Entity root(Entity e) {
        if (e == null || isDragon(e)) return e;
        Method m = PARENT.computeIfAbsent(e.getClass(), c -> find(c, "getParent"));
        if (m == NONE) return e;
        try {
            Object o = m.invoke(e);
            return o instanceof Entity parent ? parent : e;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return e;
        }
    }

    public static Info info(Entity e) {
        String type = typeId(e);
        String species = species(type);
        int variant = variant(e);
        boolean corrupted = false;
        for (String c : Config.get(Config.CORRUPTED)) {
            int i = c.lastIndexOf('#');
            if (i <= 0) continue;
            if (c.substring(0, i).equalsIgnoreCase(type)) {
                try { if (Integer.parseInt(c.substring(i + 1).strip()) == variant) corrupted = true; }
                catch (NumberFormatException ignored) { }
            }
        }
        int stage = intCall(e, STAGE, "getDragonStage", 0);
        UUID owner = null;
        boolean tamed = false;
        if (e instanceof TamableAnimal t) { tamed = t.isTame(); owner = t.getOwnerUUID(); }
        return new Info(e, type, species, variant, label(species, variant), corrupted, stage, tamed, owner);
    }

    private static String species(String typeId) {
        String path = typeId.contains(":") ? typeId.substring(typeId.indexOf(':') + 1) : typeId;
        if (path.contains("fire")) return "fire";
        if (path.contains("ice")) return "ice";
        if (path.contains("lightning")) return "lightning";
        return path;
    }

    private static int variant(Entity e) {
        int v = intCall(e, VARIANT, "getVariant", Integer.MIN_VALUE);
        if (v != Integer.MIN_VALUE) return v;
        try {
            CompoundTag tag = e.saveWithoutId(new CompoundTag());
            return tag.getInt("Variant");
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    private static int intCall(Entity e, ConcurrentHashMap<Class<?>, Method> cache, String name, int def) {
        Method m = cache.computeIfAbsent(e.getClass(), c -> find(c, name));
        if (m == NONE) return def;
        try {
            Object o = m.invoke(e);
            return o instanceof Number n ? n.intValue() : def;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return def;
        }
    }

    private static Method find(Class<?> c, String name) {
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            Method m;
            try {
                m = k.getDeclaredMethod(name);
            } catch (NoSuchMethodException | RuntimeException ignored) {
                continue;
            }
            if (m.getParameterCount() != 0) continue;
            try { m.setAccessible(true); }
            catch (RuntimeException ex) { CitizenFactions.LOGGER.debug("Méthode {} non rendue accessible sur {}", name, k.getName()); }
            return m;
        }
        return NONE;
    }

    public static String label(String species, int v) {
        String[] names = switch (species) {
            case "fire" -> new String[]{"Rouge", "Vert", "Bronze", "Gris"};
            case "ice" -> new String[]{"Bleu", "Blanc", "Saphir", "Argent"};
            case "lightning" -> new String[]{"Électrique", "Améthyste", "Cuivre", "Noir"};
            default -> new String[0];
        };
        return v >= 0 && v < names.length ? names[v] : "variante " + v;
    }

    // ------------------------------------------------------------------ oeufs

    public static boolean isEgg(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String id = itemId(stack);
        for (String p : Config.get(Config.EGG_PREFIXES)) if (id.startsWith(p.toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    public static boolean isCorruptedEgg(ItemStack stack) {
        String id = itemId(stack);
        List<? extends String> l = Config.get(Config.CORRUPTED_EGGS);
        for (String s : l) if (s.equalsIgnoreCase(id)) return true;
        return false;
    }

    private static String itemId(ItemStack stack) {
        ResourceLocation k = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return k == null ? "" : k.toString().toLowerCase(Locale.ROOT);
    }
}
