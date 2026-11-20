package fr.citizen.factions.platform;

import fr.citizen.factions.core.Faction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.damagesource.DamageTypes;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Application des regles des factions aux dragons Ice and Fire, cote serveur :
 * - sans faction : aucun dragon (ni monte, ni interaction avec ses dragons, ni oeuf a poser) ;
 * - regle de la faction (ex. Dragonniers : tous sauf corrompus ; Chasseurs : corrompus uniquement) ;
 * - lien faible : le dragon peut refuser d'etre monte.
 * Les verifications se font AVANT le code d'Ice and Fire (interaction), avec un filet de securite sur la monte elle-meme.
 */
public final class DragonGuard {
    private DragonGuard() { }

    // ------------------------------------------------------------------ interactions avec un dragon

    public static void onInteract(PlayerInteractEvent.EntityInteract e) {
        InteractionResult r = check(e.getEntity() instanceof ServerPlayer p ? p : null, e.getTarget(), e.getHand(), e.getItemStack());
        if (r != null) { e.setCancellationResult(r); e.setCanceled(true); }
    }

    public static void onInteractSpecific(PlayerInteractEvent.EntityInteractSpecific e) {
        InteractionResult r = check(e.getEntity() instanceof ServerPlayer p ? p : null, e.getTarget(), e.getHand(), e.getItemStack());
        if (r != null) { e.setCancellationResult(r); e.setCanceled(true); }
    }

    /** @return null pour laisser faire, sinon le resultat d'annulation. */
    private static InteractionResult check(ServerPlayer p, Entity rawTarget, InteractionHand hand, ItemStack stack) {
        if (p == null) return null;
        FactionRuntime rt = FactionRuntime.current();
        if (rt == null) return null;
        Entity target = DragonBridge.root(rawTarget);
        if (!DragonBridge.isDragon(target) || !(target instanceof LivingEntity dragon)) return null;
        DragonBridge.Info info = DragonBridge.info(target);
        boolean mine = info.tamed() && p.getUUID().equals(info.owner());
        if (mine) rt.bonds().sync(target); // le dragon apparait dans « Mes dragons » meme s'il n'a jamais ete monte
        boolean mountAttempt = mine && stack.isEmpty() && !p.isShiftKeyDown() && !p.isPassenger();

        boolean bypass = Config.get(Config.ADMIN_BYPASS) && Admins.is(p);
        Faction f = rt.service().factionOf(p.getUUID());
        if (f == null && !bypass && (mine && Config.get(Config.BLOCK_INTERACTIONS) || mountAttempt)) {
            if (hand == InteractionHand.MAIN_HAND) rt.message(p, "Vous n'appartenez à aucune faction : vous ne pouvez pas avoir de dragon.");
            return InteractionResult.FAIL;
        }
        if (mountAttempt) {
            String deny = rt.bonds().denyReason(p, info);
            if (deny != null) {
                if (hand == InteractionHand.MAIN_HAND) rt.message(p, deny);
                return InteractionResult.FAIL;
            }
            if (info.stage() > 2 && hand == InteractionHand.MAIN_HAND && rt.bonds().refuses(dragon, p.getUUID())) {
                p.displayClientMessage(Component.literal("Votre dragon refuse de se laisser monter… Votre lien est encore trop faible."), true);
                return InteractionResult.FAIL;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ filet de securite : la monte elle-meme

    public static void onMount(EntityMountEvent e) {
        if (!e.isMounting() || e.getLevel().isClientSide()) return;
        FactionRuntime rt = FactionRuntime.current();
        if (rt == null) return;
        Entity mounting = e.getEntityMounting(), mounted = e.getEntityBeingMounted();
        ServerPlayer p;
        Entity dragon;
        if (mounting instanceof ServerPlayer sp && DragonBridge.isDragon(mounted)) { p = sp; dragon = mounted; }
        else if (mounted instanceof ServerPlayer sp && DragonBridge.isDragon(mounting)) { p = sp; dragon = mounting; } // petit dragon sur l'epaule
        else return;
        String deny = rt.bonds().denyReason(p, DragonBridge.info(dragon));
        if (deny == null) return;
        e.setCanceled(true);
        rt.message(p, deny);
        resync(dragon);
        resync(p);
    }

    /** Ice and Fire previent les clients d'une monte avant qu'elle soit validee : on renvoie l'etat reel. */
    private static void resync(Entity e) {
        if (e.level() instanceof ServerLevel sl) {
            sl.getServer().execute(() -> sl.getChunkSource().broadcastAndSend(e, new ClientboundSetPassengersPacket(e)));
        }
    }

    // ------------------------------------------------------------------ oeufs

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        if (e.getEntity() instanceof ServerPlayer p && eggDenied(p, e.getItemStack())) e.setCanceled(true);
    }

    public static void onRightClickItem(PlayerInteractEvent.RightClickItem e) {
        if (e.getEntity() instanceof ServerPlayer p && eggDenied(p, e.getItemStack())) {
            e.setCancellationResult(InteractionResult.FAIL);
            e.setCanceled(true);
        }
    }

    private static boolean eggDenied(ServerPlayer p, ItemStack stack) {
        if (!Config.get(Config.BLOCK_EGGS) || !DragonBridge.isEgg(stack)) return false;
        FactionRuntime rt = FactionRuntime.current();
        if (rt == null || Config.get(Config.ADMIN_BYPASS) && Admins.is(p)) return false;
        Faction f = rt.service().factionOf(p.getUUID());
        if (f == null) {
            rt.message(p, "Vous n'appartenez à aucune faction : vous ne pouvez pas faire éclore de dragon.");
            return true;
        }
        boolean corrupted = DragonBridge.isCorruptedEgg(stack);
        if (!f.rule().allows(corrupted)) {
            rt.message(p, corrupted ? "Votre faction ne peut pas élever de dragon corrompu."
                    : "Votre faction ne peut élever que des dragons corrompus (Noir, Gris, Cuivre).");
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ cycle de vie des dragons

    public static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || !DragonBridge.isDragon(e.getEntity())) return;
        FactionRuntime rt = FactionRuntime.current();
        if (rt == null) return;
        Entity d = e.getEntity();
        Primordial.onLoaded(d);
        boolean known = rt.service().dragon(d.getUUID()) != null;
        boolean tamed = d instanceof TamableAnimal t && t.isTame();
        // on ne suit que les dragons apprivoises ou deja lies : les dragons sauvages ne sont pas enregistres
        if (known || tamed || !BondSystem.readBonds(d).isEmpty()) rt.bonds().sync(d);
    }

    public static void onLeave(EntityLeaveLevelEvent e) {
        if (!e.getLevel().isClientSide() && DragonBridge.isDragon(e.getEntity())) Primordial.forget(e.getEntity());
    }

    public static void onStartTracking(PlayerEvent.StartTracking e) {
        if (e.getEntity() instanceof ServerPlayer p && DragonBridge.isDragon(e.getTarget())) Primordial.sendTo(p, e.getTarget());
    }

    /** Souffle d'un primordial : degats multiplies et cible embrasee (la morsure est deja doublee par l'attribut d'attaque). */
    public static void onIncomingDamage(LivingIncomingDamageEvent e) {
        if (e.getEntity().level().isClientSide()) return;
        Entity src = e.getSource().getEntity();
        if (src == null || Primordial.of(src) == null || src == e.getEntity()) return;
        if (e.getSource().is(DamageTypes.MOB_ATTACK)) return;
        e.setAmount(e.getAmount() * Config.get(Config.PRIMORDIAL_DAMAGE).floatValue());
        int burn = Config.get(Config.PRIMORDIAL_BURN_SECONDS);
        if (burn > 0) e.getEntity().igniteForSeconds(burn);
    }

    public static void onDeath(LivingDeathEvent e) {
        if (e.getEntity().level().isClientSide() || !DragonBridge.isDragon(e.getEntity())) return;
        Primordial.forget(e.getEntity());
        FactionRuntime rt = FactionRuntime.current();
        if (rt != null) rt.service().forgetDragon(e.getEntity().getUUID());
    }
}
