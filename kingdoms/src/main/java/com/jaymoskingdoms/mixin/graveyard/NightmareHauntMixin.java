package com.jaymoskingdoms.mixin.graveyard;

import com.jaymoskingdoms.NightmareHaunt;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The Graveyard's Nightmare hunts by {@link NightmareHaunt} instead of as an enderman: its stare-to-anger target goal,
 * its walk-up chase and its melee goal are taken out, both its teleports do nothing, and a plain "hunt the nearest
 * player in sight" goal takes the stare's place. Skipped without The Graveyard.
 */
@Pseudo
@Mixin(targets = "com.lion.graveyard.entities.NightmareEntity", remap = false)
abstract class NightmareHauntMixin extends Monster implements NightmareHaunt.Body {
	/** It notices a player in sight this near (blocks). */
	@Unique
	private static final double NOTICE_RANGE = 24.0;

	@Unique
	private final NightmareHaunt.State jaymoskingdoms$haunt = new NightmareHaunt.State();

	protected NightmareHauntMixin(EntityType<? extends Monster> type, Level level) {
		super(type, level);
	}

	@Shadow
	public abstract void setAttackAnimTimer(int timer);

	@Override
	public NightmareHaunt.State jaymoskingdoms$haunt() {
		return jaymoskingdoms$haunt;
	}

	@Override
	public void jaymoskingdoms$swing() {
		setAttackAnimTimer(15);
	}

	@Inject(method = "registerGoals()V", at = @At("TAIL"), require = 0)
	private void jaymoskingdoms$hauntGoals(CallbackInfo ci) {
		this.goalSelector.removeAllGoals(g -> {
			String name = g.getClass().getName();
			return name.endsWith("NightmareEntity$ChasePlayerGoal") || name.endsWith("NightmareMeleeAttackGoal");
		});
		this.targetSelector.removeAllGoals(g -> g.getClass().getName().endsWith("NightmareEntity$TeleportTowardsPlayerGoal"));
		Monster self = this;
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
			(target, level) -> target.distanceToSqr(self) < NOTICE_RANGE * NOTICE_RANGE));
	}

	@Inject(method = "teleportTo(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true, require = 0)
	private void jaymoskingdoms$noBlinkTo(Entity target, CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(false);
	}

	@Inject(method = "teleportRandomly()Z", at = @At("HEAD"), cancellable = true, require = 0)
	private void jaymoskingdoms$noBlink(CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(false);
	}

	@Inject(method = "customServerAiStep(Lnet/minecraft/server/level/ServerLevel;)V", at = @At("TAIL"), require = 0)
	private void jaymoskingdoms$haunt(ServerLevel level, CallbackInfo ci) {
		NightmareHaunt.tick(this, level);
	}

	@Inject(method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
		at = @At("HEAD"), cancellable = true, require = 0)
	private void jaymoskingdoms$goneIntoTheDark(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		if (NightmareHaunt.gone(this) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
		at = @At("RETURN"), require = 0)
	private void jaymoskingdoms$breaksApart(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) {
			NightmareHaunt.hurt(this, level);
		}
	}
}
