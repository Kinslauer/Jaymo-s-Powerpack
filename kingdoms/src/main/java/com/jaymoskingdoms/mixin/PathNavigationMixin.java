package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.Unstick;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hands every mob's walking to {@link Unstick} after each step, so one that makes no headway works itself loose. */
@Mixin(PathNavigation.class)
abstract class PathNavigationMixin {
	@Shadow
	@Final
	protected Mob mob;
	@Shadow
	protected double speedModifier;
	@Shadow
	private int reachRange;

	@Unique
	private final Unstick.State jaymoskingdoms$unstick = new Unstick.State();

	@Inject(method = "tick", at = @At("TAIL"))
	private void jaymoskingdoms$unstick(CallbackInfo ci) {
		Unstick.tick(this.mob, (PathNavigation) (Object) this, this.jaymoskingdoms$unstick, this.speedModifier, this.reachRange);
	}
}
