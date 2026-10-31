package com.jaymospowers.mixin;

import com.jaymospowers.ground.ThrownRocks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.18.84) Andy: "the thrown rocks that break leave what looks like an egg splat". A snowball wearing a rock (ours, or
 * Kingdoms' guards' cobblestone) never sends the snowball's burst, which Subtle Effects turns into a splat on the block:
 * it cracks and sheds stone instead ({@link ThrownRocks#landed}).
 */
@Mixin(Snowball.class)
abstract class RockSnowballMixin {
	/** What this snowball just hit (onHit's own argument), for the burst below. */
	@Unique
	private @Nullable HitResult jaymospowers$hit;

	@Inject(method = "onHit", at = @At("HEAD"))
	private void jaymospowers$noteHit(HitResult hit, CallbackInfo ci) {
		jaymospowers$hit = hit;
	}

	@Redirect(method = "onHit", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;broadcastEntityEvent(Lnet/minecraft/world/entity/Entity;B)V"))
	private void jaymospowers$rockBreaks(Level level, Entity entity, byte event) {
		HitResult hit = jaymospowers$hit;
		jaymospowers$hit = null;
		if (hit == null || !ThrownRocks.landed((Snowball) (Object) this, hit)) {
			level.broadcastEntityEvent(entity, event);
		}
	}
}
