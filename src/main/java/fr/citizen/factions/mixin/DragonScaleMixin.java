package fr.citizen.factions.mixin;

import fr.citizen.factions.platform.PrimordialState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ice and Fire calcule lui-meme la taille de ses dragons (getRenderSize / getScale) et ignore l'attribut de taille vanilla.
 * On multiplie donc ces deux valeurs pour les dragons primordiaux : modele, hitbox et parties du corps grandissent ensemble.
 * @Pseudo + require = 0 : si la classe ou les methodes n'existent pas dans le portage, rien ne plante (taille simplement inchangee).
 */
@Pseudo
@Mixin(targets = "com.github.alexthe666.iceandfire.entity.EntityDragonBase", remap = false)
public abstract class DragonScaleMixin {

    @Inject(method = "getRenderSize()F", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private void citizenfactions$renderSize(CallbackInfoReturnable<Float> cir) {
        float f = PrimordialState.sizeFactor((Entity) (Object) this);
        if (f != 1F) cir.setReturnValue(cir.getReturnValueF() * f);
    }

    @Inject(method = "getScale()F", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private void citizenfactions$scale(CallbackInfoReturnable<Float> cir) {
        float f = PrimordialState.sizeFactor((Entity) (Object) this);
        if (f != 1F) cir.setReturnValue(cir.getReturnValueF() * f);
    }
}
