package fr.citizen.factions.platform;

import fr.citizen.factions.CitizenFactions;
import fr.citizen.factions.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Lien progressif joueur / dragon.
 * - Source de verite : les donnees persistantes DE L'ENTITE dragon (sauvegardees avec elle par Minecraft),
 *   donc le lien survit a la mort du joueur, a sa deconnexion et aux redemarrages.
 * - Miroir dans factions.json (DragonRecord) pour l'interface et les commandes quand le dragon n'est pas charge.
 * - Chaque seconde de monte fait grandir le lien ; l'abandon le fait baisser (calcul en temps reel, voir BondMath).
 */
public final class BondSystem {
    static final String NBT_ROOT = "citizenfactions";
    static final ResourceLocation SPEED_ID = ResourceLocation.fromNamespaceAndPath(CitizenFactions.MOD_ID, "bond_speed");

    private final FactionRuntime rt;
    /** Joueur -> dragon monte a la seconde precedente (pour masquer la barre et retirer les effets). */
    private final Map<UUID, LivingEntity> riding = new HashMap<>();
    private final Random random = new Random();

    BondSystem(FactionRuntime rt) { this.rt = rt; }

    // ================================================================== donnees sur l'entite

    public static Map<UUID, Bond> readBonds(Entity dragon) {
        Map<UUID, Bond> out = new LinkedHashMap<>();
        CompoundTag root = dragon.getPersistentData().getCompound(NBT_ROOT);
        ListTag list = root.getList("bonds", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            if (!t.hasUUID("p")) continue;
            out.put(t.getUUID("p"), new Bond(t.getDouble("v"), t.getLong("r"), t.getLong("u")));
        }
        return out;
    }

    public static void writeBond(Entity dragon, UUID player, Bond b) {
        Map<UUID, Bond> all = readBonds(dragon);
        all.put(player, b);
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Bond> e : all.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("p", e.getKey());
            t.putDouble("v", e.getValue().value());
            t.putLong("r", e.getValue().lastRide());
            t.putLong("u", e.getValue().lastUpdate());
            list.add(t);
        }
        CompoundTag root = dragon.getPersistentData().getCompound(NBT_ROOT);
        root.put("bonds", list);
        dragon.getPersistentData().put(NBT_ROOT, root);
    }

    /** Lien actuel (abandon compris) d'un joueur avec ce dragon charge. */
    public double bondOf(Entity dragon, UUID player) {
        Bond b = readBonds(dragon).get(player);
        return BondMath.effective(b == null ? Bond.NONE : b, System.currentTimeMillis(), Config.bond());
    }

    /** Recopie l'etat du dragon (espece, couleur, proprietaire, position, liens) dans sa fiche. */
    public DragonRecord sync(Entity dragon) {
        DragonBridge.Info info = DragonBridge.info(dragon);
        FactionService s = rt.service();
        DragonRecord d = s.dragonRecord(dragon.getUUID());
        String name = dragon.hasCustomName() && dragon.getCustomName() != null ? dragon.getCustomName().getString() : "";
        d.describe(info.species(), info.variantLabel(), info.corrupted(), name, info.tamed() ? info.owner() : null, info.stage());
        String prim = PrimordialState.kind(dragon);
        d.primordial(prim == null ? "" : prim);
        d.downed(DragonDowned.isDowned(dragon));
        d.seen(dragon.level().dimension().location().toString(), dragon.getBlockX(), dragon.getBlockY(), dragon.getBlockZ(), System.currentTimeMillis());
        d.bonds().clear();
        d.bonds().putAll(readBonds(dragon));
        s.markDirty();
        return d;
    }

    // ================================================================== regles

    /** Raison du refus si ce joueur n'a pas le droit d'avoir / monter ce dragon ; null si autorise. */
    public String denyReason(ServerPlayer p, DragonBridge.Info info) {
        if (Config.get(Config.ADMIN_BYPASS) && Admins.is(p)) return null;
        Faction f = rt.service().factionOf(p.getUUID());
        if (f == null) return "Vous n'appartenez à aucune faction : vous ne pouvez pas avoir de dragon.";
        String prim = PrimordialState.kind(dragonEntity(info));
        if (prim != null) {
            if (!prim.equals(f.id()))
                return "Ce dragon primordial est réservé à la faction « " + nameOfFaction(prim) + " ».";
            if (!rt.service().has(p.getUUID(), FactionPermission.MONTER_PRIMORDIAL))
                return "Seul le grade le plus élevé de votre faction (Primodiau) peut monter le " + Primordial.label(prim) + ".";
            return null;
        }
        if (!f.rule().allows(info.corrupted())) {
            return switch (f.rule()) {
                case NON_CORROMPUS -> "Faction " + f.name() + " : vous ne pouvez pas monter un dragon corrompu (" + info.variantLabel() + ").";
                case CORROMPUS -> "Faction " + f.name() + " : vous ne pouvez monter que les dragons corrompus (Noir, Gris, Cuivre).";
                default -> "Faction " + f.name() + " : vous n'avez le droit de monter aucun dragon.";
            };
        }
        return null;
    }

    private String nameOfFaction(String id) {
        Faction f = rt.service().faction(id);
        return f == null ? id : f.name();
    }

    private static Entity dragonEntity(DragonBridge.Info info) { return info.entity(); }

    /** Tirage du refus de monte du a un lien faible. */
    public boolean refuses(LivingEntity dragon, UUID player) {
        if (!Config.get(Config.BEHAVIOUR)) return false;
        double chance = BondMath.refuseChance(bondOf(dragon, player), Config.bond());
        return chance > 0 && random.nextDouble() < chance;
    }

    // ================================================================== boucle

    /** Chaque seconde : progression du lien, effets sur le dragon, barre d'affichage. */
    void tickSecond() {
        Set<UUID> still = new HashSet<>();
        long now = System.currentTimeMillis();
        BondSettings settings = Config.bond();
        boolean behaviour = Config.get(Config.BEHAVIOUR);
        for (ServerPlayer p : rt.server().getPlayerList().getPlayers()) {
            Entity v = p.getVehicle();
            if (!(v instanceof LivingEntity dragon) || !DragonBridge.isDragon(v)) continue;
            if (dragon.getControllingPassenger() != null && dragon.getControllingPassenger() != p) continue;

            DragonBridge.Info info = DragonBridge.info(dragon);
            String deny = denyReason(p, info);
            if (deny != null) {
                p.stopRiding();
                rt.message(p, deny);
                continue;
            }
            still.add(p.getUUID());
            LivingEntity before = riding.put(p.getUUID(), dragon);
            if (before != null && before != dragon) clearEffects(before);

            Map<UUID, Bond> bonds = readBonds(dragon);
            Bond old = bonds.getOrDefault(p.getUUID(), Bond.NONE);
            Bond nb = BondMath.ride(old, now, 1000L, settings);
            writeBond(dragon, p.getUUID(), nb);
            double value = nb.value();
            DragonRecord rec = sync(dragon);

            if (behaviour) applyBehaviour(p, dragon, value);
            else clearEffects(dragon);
            Network.sendBond(p, new BondPayload(true, rec.displayName(), (float) value, BondMath.tier(value)));
        }
        // ceux qui viennent de descendre
        Iterator<Map.Entry<UUID, LivingEntity>> it = riding.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, LivingEntity> e = it.next();
            if (still.contains(e.getKey())) continue;
            clearEffects(e.getValue());
            if (e.getValue().isAlive()) sync(e.getValue());
            ServerPlayer p = rt.server().getPlayerList().getPlayer(e.getKey());
            if (p != null) Network.sendBond(p, BondPayload.HIDE);
            it.remove();
        }
    }

    private void applyBehaviour(ServerPlayer p, LivingEntity dragon, double bond) {
        double speed = BondMath.speedModifier(bond);
        setSpeed(dragon.getAttribute(Attributes.MOVEMENT_SPEED), speed);
        setSpeed(dragon.getAttribute(Attributes.FLYING_SPEED), speed);

        double regen = BondMath.regenPerSecond(bond);
        if (regen > 0 && dragon.getHealth() < dragon.getMaxHealth()) dragon.heal((float) regen);

        if (random.nextDouble() < BondMath.buckChancePerSecond(bond)) {
            p.stopRiding();
            p.displayClientMessage(Component.literal("Votre dragon, trop méfiant, vous désarçonne !"), true);
            return;
        }
        if (random.nextDouble() < BondMath.jerkChancePerSecond(bond)) {
            double strength = 0.4 + (50 - bond) / 50d * 0.8;
            Vec3 jerk = new Vec3((random.nextDouble() - 0.5) * strength, (random.nextDouble() - 0.3) * strength * 0.6, (random.nextDouble() - 0.5) * strength);
            dragon.setDeltaMovement(dragon.getDeltaMovement().add(jerk));
            dragon.hurtMarked = true; // renvoie le mouvement au client qui pilote
            p.displayClientMessage(Component.literal("Votre dragon résiste et fait un mouvement brusque !"), true);
        }
    }

    private static void setSpeed(AttributeInstance inst, double amount) {
        if (inst == null) return;
        inst.addOrUpdateTransientModifier(new AttributeModifier(SPEED_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    private static void clearEffects(LivingEntity dragon) {
        AttributeInstance a = dragon.getAttribute(Attributes.MOVEMENT_SPEED);
        if (a != null) a.removeModifier(SPEED_ID);
        AttributeInstance b = dragon.getAttribute(Attributes.FLYING_SPEED);
        if (b != null) b.removeModifier(SPEED_ID);
    }

    /** Toutes les 10 secondes : mise a jour des fiches des dragons charges et option « dragons sans faction ». */
    void tickSlow() {
        boolean untame = Config.get(Config.UNTAME_FACTIONLESS);
        for (DragonRecord rec : new ArrayList<>(rt.service().dragons())) {
            Entity e = find(rec.uuid());
            if (e == null || !e.isAlive()) continue;
            sync(e);
            if (untame && e instanceof TamableAnimal t && t.isTame() && t.getOwnerUUID() != null) {
                ServerPlayer owner = rt.server().getPlayerList().getPlayer(t.getOwnerUUID());
                if (owner != null && rt.service().factionOf(owner.getUUID()) == null && !(Config.get(Config.ADMIN_BYPASS) && Admins.is(owner))) {
                    t.setTame(false, true);
                    t.setOwnerUUID(null);
                    rt.message(owner, "Sans faction, vous ne pouvez pas avoir de dragon : " + rec.displayName() + " est redevenu sauvage (votre lien est conservé).");
                    sync(e);
                }
            }
        }
    }

    public Entity find(UUID uuid) {
        for (ServerLevel l : rt.server().getAllLevels()) {
            Entity e = l.getEntity(uuid);
            if (e != null) return e;
        }
        return null;
    }

    void onLogout(ServerPlayer p) {
        LivingEntity d = riding.remove(p.getUUID());
        if (d != null) { clearEffects(d); if (d.isAlive()) sync(d); }
    }
}
