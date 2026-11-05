package com.jaymospowers.mixin;

import com.jaymospowers.combat.Guard;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.20.76) Right click on a foe with a guarding weapon raises the guard instead of opening the foe's chat (MCA's orcs,
 * guards and bandits override {@code interact} themselves) or trades: the click on them passes, so it falls through to
 * the weapon (see Guard#guardsAgainst). Fabric's right-click-on-someone callbacks run before this, so they still come
 * first.
 */
@Mixin(Player.class)
abstract class GuardOverTalkMixin {
	@Inject(method = "interactOn", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$guardOverTalk(Entity entity, InteractionHand hand, Vec3 location, CallbackInfoReturnable<InteractionResult> cir) {
		if (entity instanceof Mob mob && Guard.guardsAgainst((Player) (Object) this, mob)) {
			cir.setReturnValue(InteractionResult.PASS);
		}
	}
}
