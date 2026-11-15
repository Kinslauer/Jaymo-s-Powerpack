package com.jaymoskingdoms.mixin;

import net.minecraft.world.entity.raid.Raider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Illagers on the march (patrols, our pillager caravans) size you up, then come for you. On its own a marching illager
 * that sees you stops and stares for as long as you stay more than 10 blocks off, and only attacks once you come
 * closer or hit one of them, so Andy swam past a caravan that just watched him go (2026-11-14: "Illagers werent
 * hostile either"). Now the stare lasts {@link #STARE_TICKS}; then it and every raider near it come at you.
 */
@Mixin(targets = "net.minecraft.world.entity.raid.Raider$HoldGroundAttackGoal")
abstract class HoldGroundStareMixin {
	/** How long an illager stares before it attacks (ticks: 3 seconds). */
	@Unique
	private static final int STARE_TICKS = 60;

	@Shadow
	@Final
	private Raider mob;

	@Unique
	private int jaymoskingdoms$stared;

	@Inject(method = "start", at = @At("HEAD"))
	private void jaymoskingdoms$startStare(CallbackInfo ci) {
		this.jaymoskingdoms$stared = 0;
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void jaymoskingdoms$stare(CallbackInfo ci) {
		if (++this.jaymoskingdoms$stared >= STARE_TICKS && this.mob.getTarget() != null) {
			this.mob.setAggressive(true);
		}
	}
}
