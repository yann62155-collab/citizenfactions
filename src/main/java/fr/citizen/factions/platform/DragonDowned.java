package fr.citizen.factions.platform;

import fr.citizen.factions.CitizenFactions;
import fr.citizen.factions.core.Faction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Dragons « a terre » : un dragon apprivoise (ou primordial) qui devrait mourir reste en vie avec 1 PV, couche au sol
 * dans la pose de carcasse d'Ice and Fire, immobile et invulnerable, jusqu'a ce qu'un Dragonnier ou un Chasseur de dragon
 * le sauve avec l'objet prevu. Son proprietaire, sa couleur, son statut primordial et son LIEN sont conserves.
 */
public final class DragonDowned {
    static final String KEY = "aterre";

    private DragonDowned() { }

    public static boolean isDowned(Entity e) {
        return e.getPersistentData().getCompound(BondSystem.NBT_ROOT).getBoolean(KEY);
    }

    /** Appele en priorite haute sur la mort : annule la mort et met le dragon a terre. */
    public static void onDeath(LivingDeathEvent e) {
        LivingEntity d = e.getEntity();
        if (d.level().isClientSide() || !DragonBridge.isDragon(d) || !Config.get(Config.DOWNED_ENABLED)) return;
        boolean tamed = d instanceof TamableAnimal t && t.isTame();
        if (!tamed && Primordial.of(d) == null && !Config.get(Config.DOWNED_WILD)) return;
        e.setCanceled(true);
        down(d);
        FactionRuntime rt = FactionRuntime.current();
        if (rt != null) {
            rt.bonds().sync(d);
            if (d instanceof TamableAnimal t && t.getOwnerUUID() != null) {
                ServerPlayer owner = rt.server().getPlayerList().getPlayer(t.getOwnerUUID());
                if (owner != null) rt.message(owner, name(d) + " est à terre ! Un Dragonnier ou un Chasseur de dragon peut le sauver ("
                        + d.blockPosition().getX() + " " + d.blockPosition().getY() + " " + d.blockPosition().getZ() + ").");
            }
        }
    }

    static void down(LivingEntity d) {
        d.setHealth(1F);
        d.ejectPassengers();
        if (d.isPassenger()) d.stopRiding();
        d.clearFire();
        d.setInvulnerable(true);
        if (d instanceof Mob m) { m.setNoAi(true); m.setTarget(null); m.getNavigation().stop(); }
        setModelDead(d, true);
        CompoundTag root = d.getPersistentData().getCompound(BondSystem.NBT_ROOT);
        root.putBoolean(KEY, true);
        d.getPersistentData().put(BondSystem.NBT_ROOT, root);
        if (d.level() instanceof ServerLevel sl)
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, d.getX(), d.getY() + 1, d.getZ(), 40, 1.5, 1, 1.5, 0.02);
    }

    static void revive(LivingEntity d, ServerPlayer by) {
        setModelDead(d, false);
        d.setInvulnerable(false);
        if (d instanceof Mob m) m.setNoAi(false);
        CompoundTag root = d.getPersistentData().getCompound(BondSystem.NBT_ROOT);
        root.remove(KEY);
        d.getPersistentData().put(BondSystem.NBT_ROOT, root);
        d.setHealth(Math.max(1F, (float) (d.getMaxHealth() * Config.get(Config.REVIVE_HEALTH))));
        if (d.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, d.getX(), d.getY() + 2, d.getZ(), 120, 1.5, 1.5, 1.5, 0.4);
            sl.sendParticles(ParticleTypes.HEART, d.getX(), d.getY() + 3, d.getZ(), 20, 1.5, 1, 1.5, 0.1);
            sl.playSound(null, d.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.NEUTRAL, 1.5F, 0.8F);
        }
        FactionRuntime rt = FactionRuntime.current();
        if (rt != null) {
            rt.bonds().sync(d);
            rt.message(by, "Vous avez sauvé " + name(d) + " !");
            if (d instanceof TamableAnimal t && t.getOwnerUUID() != null && !t.getOwnerUUID().equals(by.getUUID())) {
                ServerPlayer owner = rt.server().getPlayerList().getPlayer(t.getOwnerUUID());
                if (owner != null) rt.message(owner, by.getGameProfile().getName() + " a sauvé " + name(d) + ".");
            }
        }
    }

    /** Interaction avec un dragon a terre : sauvetage, sinon refus (empeche aussi le depecage de la carcasse). */
    static net.minecraft.world.InteractionResult interact(ServerPlayer p, LivingEntity d, ItemStack stack, boolean mainHand) {
        FactionRuntime rt = FactionRuntime.current();
        if (rt == null) return net.minecraft.world.InteractionResult.FAIL;
        if (!mainHand) return net.minecraft.world.InteractionResult.FAIL;
        Faction f = rt.service().factionOf(p.getUUID());
        if (!isReviveItem(stack)) {
            p.displayClientMessage(Component.literal(name(d) + " est à terre. Un Dragonnier ou un Chasseur de dragon peut le sauver avec une pomme dorée."), true);
            return net.minecraft.world.InteractionResult.FAIL;
        }
        if (f == null && !(Config.get(Config.ADMIN_BYPASS) && Admins.is(p))) {
            rt.message(p, "Seuls les Dragonniers et les Chasseurs de dragon peuvent sauver un dragon.");
            return net.minecraft.world.InteractionResult.FAIL;
        }
        if (!p.isCreative()) stack.shrink(1);
        revive(d, p);
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    private static boolean isReviveItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String id = String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        for (String s : Config.get(Config.REVIVE_ITEMS)) if (s.equalsIgnoreCase(id)) return true;
        return false;
    }

    private static String name(Entity d) {
        return d.hasCustomName() && d.getCustomName() != null ? d.getCustomName().getString() : "Votre dragon";
    }

    private static void setModelDead(Entity e, boolean dead) {
        try {
            e.getClass().getMethod("setModelDead", boolean.class).invoke(e, dead);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            CitizenFactions.LOGGER.debug("setModelDead indisponible : {}", ex.toString());
        }
    }
}
