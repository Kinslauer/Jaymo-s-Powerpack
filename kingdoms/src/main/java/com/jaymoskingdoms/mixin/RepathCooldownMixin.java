package com.jaymoskingdoms.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A mob sent to the same block it just failed to reach (no path, or one that stops where it stands) isn't pathed there again
 * for {@link #COOLDOWN} ticks: the answer is the same. Dozens of Kingdoms' errands re-path "every second, or whenever
 * the walk is done", and a walk that can't reach is done at once, so a guard whose post was out of reach pathed every
 * tick; a camp's worth of them held the Deck server half a minute behind as a player joined (2026-11-16, 39% of the
 * villagers' time in one profile). Only {@code moveTo(x, y, z, ...)}: following an entity is left alone.
 */
@Mixin(PathNavigation.class)
abstract class RepathCooldownMixin {
	/** Ticks a failed target isn't tried again (one second). */
	@Unique
	private static final long COOLDOWN = 20L;

	@Shadow
	@Final
	protected Level level;
	@Shadow
	@Final
	protected Mob mob;
	@Shadow
	protected @Nullable Path path;

	@Unique
	private @Nullable BlockPos jaymoskingdoms$failedAt;
	@Unique
	private long jaymoskingdoms$failedTime;

	@Inject(method = "moveTo(DDDD)Z", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$skipRepeat(double x, double y, double z, double speed, CallbackInfoReturnable<Boolean> cir) {
		this.jaymoskingdoms$skip(x, y, z, cir);
	}

	@Inject(method = "moveTo(DDDID)Z", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$skipRepeat(double x, double y, double z, int reach, double speed, CallbackInfoReturnable<Boolean> cir) {
		this.jaymoskingdoms$skip(x, y, z, cir);
	}

	@Inject(method = "moveTo(DDDD)Z", at = @At("RETURN"))
	private void jaymoskingdoms$noteFailure(double x, double y, double z, double speed, CallbackInfoReturnable<Boolean> cir) {
		this.jaymoskingdoms$note(x, y, z, cir.getReturnValueZ());
	}

	@Inject(method = "moveTo(DDDID)Z", at = @At("RETURN"))
	private void jaymoskingdoms$noteFailure(double x, double y, double z, int reach, double speed, CallbackInfoReturnable<Boolean> cir) {
		this.jaymoskingdoms$note(x, y, z, cir.getReturnValueZ());
	}

	@Unique
	private void jaymoskingdoms$skip(double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
		BlockPos failed = this.jaymoskingdoms$failedAt;
		if (failed != null && this.level.getGameTime() - this.jaymoskingdoms$failedTime < COOLDOWN && failed.equals(BlockPos.containing(x, y, z))) {
			cir.setReturnValue(false);
		}
	}

	/**
	 * Notes {@code x, y, z} as failed when there was no path, or one that stops short where the mob already stands (it can
	 * get no nearer). A partial path that still goes somewhere counts as moving, as in vanilla.
	 */
	@Unique
	private void jaymoskingdoms$note(double x, double y, double z, boolean moving) {
		Path now = this.path;
		boolean stuck = now != null && !now.canReach() && now.getEndNode() != null
			&& now.getEndNode().asBlockPos().distManhattan(this.mob.blockPosition()) <= 1;
		if (!moving || now == null || stuck) {
			this.jaymoskingdoms$failedAt = BlockPos.containing(x, y, z);
			this.jaymoskingdoms$failedTime = this.level.getGameTime();
		} else {
			this.jaymoskingdoms$failedAt = null;
		}
	}
}
