package fr.citizen.factions.platform;

import fr.citizen.factions.CitizenFactions;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Les deux dragons primordiaux :
 * - Dragonnier : dragon de glace BLANC (yeux bleus d'origine), souffle accompagne de flammes bleues qui embrasent ;
 * - Chasseur de dragon : dragon de foudre NOIR, souffle accompagne de flammes rouges qui embrasent.
 * Deux fois plus grands (mixin sur la taille Ice and Fire), deux fois plus de vie, de degats, de vitesse et d'armure
 * (multiplicateurs reglables). Reserves au grade ayant la permission MONTER_PRIMORDIAL de LEUR faction.
 */
public final class Primordial {
    public record Kind(String factionId, String entityType, int variant, String name, boolean blueFlame) { }

    public static final Kind DRAGONNIER = new Kind("dragonnier", "iceandfire:ice_dragon", 1, "Dragon Blanc primordial", true);
    public static final Kind CHASSEUR = new Kind("chasseur", "iceandfire:lightning_dragon", 3, "Dragon Noir primordial", false);

    static final String NBT_KEY = "primordial";
    private static final ResourceLocation MOD_ID = ResourceLocation.fromNamespaceAndPath(CitizenFactions.MOD_ID, "primordial");
    private static final DustParticleOptions RED_EMBER = new DustParticleOptions(new Vector3f(0.85F, 0.05F, 0.05F), 1.6F);
    private static final DustParticleOptions BLUE_EMBER = new DustParticleOptions(new Vector3f(0.25F, 0.55F, 1.0F), 3.0F);
    private static final DustParticleOptions BLUE_CORE = new DustParticleOptions(new Vector3f(0.75F, 0.92F, 1.0F), 4.0F);

    private Primordial() { }

    public static Kind kind(String factionId) {
        if (DRAGONNIER.factionId().equalsIgnoreCase(factionId)) return DRAGONNIER;
        if (CHASSEUR.factionId().equalsIgnoreCase(factionId)) return CHASSEUR;
        return null;
    }

    public static String of(Entity e) { return PrimordialState.kind(e); }

    // ================================================================== creation / marquage

    /** Fait apparaitre le primordial d'une faction, adulte, a la position du joueur. */
    public static Entity spawn(ServerPlayer at, Kind k, ServerPlayer owner) {
        Optional<EntityType<?>> type = EntityType.byString(k.entityType());
        if (type.isEmpty()) throw new IllegalStateException("Type d'entité introuvable : " + k.entityType() + " (Ice and Fire est-il installé ?)");
        ServerLevel level = at.serverLevel();
        Entity e = type.get().create(level);
        if (e == null) throw new IllegalStateException("Impossible de créer le dragon.");
        Vec3 pos = at.position().add(at.getLookAngle().multiply(6, 0, 6));
        e.moveTo(pos.x, at.getY(), pos.z, at.getYRot() + 180F, 0F);
        call(e, "setVariant", k.variant());
        call(e, "setAgeInDays", 125);
        if (owner != null && e instanceof TamableAnimal t) {
            t.setTame(true, true);
            t.setOwnerUUID(owner.getUUID());
        }
        level.addFreshEntity(e);
        mark(e, k);
        return e;
    }

    /** Transforme un dragon existant en primordial (sa couleur est imposee). */
    public static void mark(Entity e, Kind k) {
        if (!DragonBridge.typeId(e).equalsIgnoreCase(k.entityType()))
            throw new IllegalArgumentException("Le primordial de cette faction doit être un " + (k.blueFlame() ? "dragon de glace" : "dragon de foudre") + ".");
        call(e, "setVariant", k.variant());
        CompoundTag root = e.getPersistentData().getCompound(BondSystem.NBT_ROOT);
        root.putString(NBT_KEY, k.factionId());
        e.getPersistentData().put(BondSystem.NBT_ROOT, root);
        if (!e.hasCustomName()) e.setCustomName(Component.literal(k.name()));
        PrimordialState.SERVER.put(e.getUUID(), k.factionId());
        applyStats(e);
        if (e instanceof LivingEntity l) l.setHealth(l.getMaxHealth());
        e.refreshDimensions();
        broadcast(e, k.factionId());
    }

    public static void unmark(Entity e) {
        CompoundTag root = e.getPersistentData().getCompound(BondSystem.NBT_ROOT);
        root.remove(NBT_KEY);
        e.getPersistentData().put(BondSystem.NBT_ROOT, root);
        PrimordialState.SERVER.remove(e.getUUID());
        if (e instanceof LivingEntity l) {
            for (Holder<Attribute> a : STATS) {
                AttributeInstance inst = l.getAttribute(a);
                if (inst != null) inst.removeModifier(MOD_ID);
            }
            l.setHealth(Math.min(l.getHealth(), l.getMaxHealth()));
        }
        e.refreshDimensions();
        broadcast(e, "");
    }

    /** Appele quand un dragon est charge : relit le marquage sauvegarde. */
    static void onLoaded(Entity e) {
        String k = e.getPersistentData().getCompound(BondSystem.NBT_ROOT).getString(NBT_KEY);
        if (k.isEmpty()) return;
        PrimordialState.SERVER.put(e.getUUID(), k);
        applyStats(e);
    }

    static void forget(Entity e) { PrimordialState.SERVER.remove(e.getUUID()); }

    // ================================================================== statistiques

    private static final List<Holder<Attribute>> STATS = List.of(
            Attributes.MAX_HEALTH, Attributes.ATTACK_DAMAGE, Attributes.MOVEMENT_SPEED, Attributes.FLYING_SPEED, Attributes.ARMOR);

    /** Modificateurs PERMANENTS (sauvegardes avec le dragon) : Ice and Fire ne change que la valeur de base selon l'age. */
    static void applyStats(Entity e) {
        if (!(e instanceof LivingEntity l)) return;
        set(l, Attributes.MAX_HEALTH, 1.0); // la vie x2 passe par HealthSystem (pas de plafond 1024) : retire l'ancien bonus
        set(l, Attributes.ATTACK_DAMAGE, Config.get(Config.PRIMORDIAL_DAMAGE));
        set(l, Attributes.MOVEMENT_SPEED, Config.get(Config.PRIMORDIAL_SPEED));
        set(l, Attributes.FLYING_SPEED, Config.get(Config.PRIMORDIAL_SPEED));
        set(l, Attributes.ARMOR, Config.get(Config.PRIMORDIAL_ARMOR));
    }

    private static void set(LivingEntity l, Holder<Attribute> attr, double multiplier) {
        AttributeInstance inst = l.getAttribute(attr);
        if (inst == null) return;
        inst.removeModifier(MOD_ID);
        if (multiplier > 1.0) inst.addPermanentModifier(new AttributeModifier(MOD_ID, multiplier - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    // ================================================================== souffle colore

    /** Chaque tick : flammes bleues (Dragonnier) ou rouges (Chasseur) dans l'axe du souffle. */
    static void tickBreath(Entity e) {
        if (!(e.level() instanceof ServerLevel level)) return;
        String k = of(e);
        if (k == null || !Boolean.TRUE.equals(callBool(e, "isBreathingFire"))) return;
        boolean blue = DRAGONNIER.factionId().equals(k);
        Vec3 head = headPosition(e);
        Vec3 dir = Vec3.directionFromRotation(e.getXRot(), e instanceof LivingEntity l ? l.getYHeadRot() : e.getYRot());
        float size = PrimordialState.sizeFactor(e);
        RandomSource r = new RandomSource();
        // particules forcees : visibles de loin (le souffle d'un primordial est enorme)
        List<ServerPlayer> viewers = new ArrayList<>();
        for (ServerPlayer v : level.players()) if (v.distanceToSqr(e) < 160 * 160) viewers.add(v);
        if (viewers.isEmpty()) return;

        if (blue) {
            // Dragonnier : torrent de flammes bleues, large et long, coeur blanc-bleu, ames qui s'elevent
            double length = 14 * size;
            for (int i = 0; i < 60; i++) {
                double dist = 1 + Math.pow(r.next(), 0.7) * length;
                double spread = 0.25 + dist * 0.12;
                Vec3 p = head.add(dir.scale(dist)).add((r.next() - 0.5) * spread * 2, (r.next() - 0.5) * spread * 2, (r.next() - 0.5) * spread * 2);
                Vec3 v = dir.scale(0.5 + r.next() * 0.6);
                ParticleOptions type = i % 6 == 0 ? BLUE_CORE : i % 3 == 0 ? BLUE_EMBER : ParticleTypes.SOUL_FIRE_FLAME;
                send(viewers, type, p, v, 1.0);
            }
            for (int i = 0; i < 6; i++) {
                Vec3 p = head.add(dir.scale(length * (0.5 + r.next() * 0.5))).add((r.next() - 0.5) * 3, r.next() * 2, (r.next() - 0.5) * 3);
                send(viewers, ParticleTypes.SOUL, p, new Vec3(0, 0.08, 0), 1.0);
            }
            // flammes bleues au point d'impact
            Vec3 end = head.add(dir.scale(length));
            for (int i = 0; i < 10; i++) send(viewers, ParticleTypes.SOUL_FIRE_FLAME,
                    end.add((r.next() - 0.5) * 4, r.next() * 1.5, (r.next() - 0.5) * 4), new Vec3(0, 0.05, 0), 1.0);
        } else {
            // Chasseur : flammes rouges
            for (int i = 0; i < 20; i++) {
                double dist = 1 + r.next() * 10 * size;
                Vec3 p = head.add(dir.scale(dist)).add((r.next() - 0.5) * dist * 0.35, (r.next() - 0.5) * dist * 0.35, (r.next() - 0.5) * dist * 0.35);
                Vec3 v = dir.scale(0.35 + r.next() * 0.4);
                send(viewers, i % 3 == 0 ? RED_EMBER : ParticleTypes.FLAME, p, v, 1.0);
            }
        }
    }

    private static void send(List<ServerPlayer> viewers, ParticleOptions type, Vec3 p, Vec3 v, double speed) {
        for (ServerPlayer pl : viewers)
            pl.serverLevel().sendParticles(pl, type, true, p.x, p.y, p.z, 0, v.x, v.y, v.z, speed);
    }

    private static Vec3 headPosition(Entity e) {
        try {
            Method m = e.getClass().getMethod("getHeadPosition");
            Object o = m.invoke(e);
            if (o instanceof Vec3 v) return v;
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
        return e.getEyePosition();
    }

    private static final class RandomSource {
        private final Random r = new Random();
        double next() { return r.nextDouble(); }
    }

    // ================================================================== reflexion Ice and Fire

    private static void call(Entity e, String name, int arg) {
        try {
            Method m = e.getClass().getMethod(name, int.class);
            m.invoke(e, arg);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            CitizenFactions.LOGGER.warn("Citizen Factions : {}({}) indisponible sur {} ({})", name, arg, e.getClass().getName(), ex.toString());
        }
    }

    private static Object callBool(Entity e, String name) {
        try {
            return e.getClass().getMethod(name).invoke(e);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }

    // ================================================================== reseau

    static void broadcast(Entity e, String kind) {
        PacketDistributor.sendToPlayersTrackingEntity(e, new PrimordialPayload(e.getId(), e.getUUID(), kind));
    }

    static void sendTo(ServerPlayer p, Entity e) {
        String k = PrimordialState.SERVER.get(e.getUUID());
        if (k != null) PacketDistributor.sendToPlayer(p, new PrimordialPayload(e.getId(), e.getUUID(), k));
    }

    public static String label(String factionId) {
        Kind k = kind(factionId);
        return k == null ? "Primordial" : k.name();
    }
}
