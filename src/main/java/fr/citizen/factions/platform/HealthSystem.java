package fr.citizen.factions.platform;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * PV par grade, sans toucher a l'attribut vanilla (plafonne a 1024) : la vie reelle reste celle de Minecraft,
 * et les degats recus sont divises par (PV du grade / vie max reelle). Un joueur a 5000 PV encaisse donc
 * 5000 points de degats « vanilla » avant de mourir ; regeneration, potions et nourriture restent proportionnelles.
 * Meme principe pour les dragons, selon le grade de leur proprietaire (et le bonus primordial).
 */
public final class HealthSystem {
    private final FactionRuntime rt;
    private final Map<UUID, Float> sent = new HashMap<>();

    HealthSystem(FactionRuntime rt) { this.rt = rt; }

    static boolean enabled() { return Config.get(Config.HP_ENABLED); }

    /** PV maximum affiches pour ce joueur. */
    public float maxHp(ServerPlayer p) {
        double v = GradeValues.number(rt, p.getUUID(), Config.get(Config.PLAYER_HP), p.getMaxHealth());
        return (float) Math.max(1, v);
    }

    /** Multiplicateur de resistance d'un dragon (grade du proprietaire x primordial). */
    public double dragonMultiplier(LivingEntity dragon) {
        double m = 1;
        if (dragon instanceof TamableAnimal t && t.getOwnerUUID() != null)
            m *= Math.max(1, GradeValues.number(rt, t.getOwnerUUID(), Config.get(Config.DRAGON_HP), 1));
        if (Primordial.of(dragon) != null) m *= Math.max(1, Config.get(Config.PRIMORDIAL_HEALTH));
        return m;
    }

    public void onDamage(LivingDamageEvent.Pre e) {
        if (!enabled() || e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        LivingEntity target = e.getEntity();
        double factor;
        if (target instanceof ServerPlayer p) factor = maxHp(p) / Math.max(1F, p.getMaxHealth());
        else if (DragonBridge.isDragon(target)) factor = dragonMultiplier(target);
        else return;
        if (factor > 1.0001) e.setNewDamage((float) (e.getNewDamage() / factor));
    }

    /** Toutes les 2 s : envoie aux joueurs leurs PV maximum s'ils ont change. */
    void sync() {
        boolean on = enabled();
        for (ServerPlayer p : rt.server().getPlayerList().getPlayers()) {
            float max = on ? maxHp(p) : 0F;
            Float before = sent.put(p.getUUID(), max);
            if (before == null || before != max) Network.sendHp(p, new HpPayload(max));
        }
    }

    void forget(ServerPlayer p) { sent.remove(p.getUUID()); }
}
