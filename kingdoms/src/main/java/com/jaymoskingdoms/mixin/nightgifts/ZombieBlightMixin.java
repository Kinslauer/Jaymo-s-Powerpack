package com.jaymoskingdoms.mixin.nightgifts;

import com.jaymoskingdoms.HollowBlight;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Shadow;

/** While a player carries the Hollow's blight, zombies never target them: they're passing out and turning,
 * not food. Hooked into {@link NearestAttackableTargetGoal#findTarget}'s tail so zombie attackers skip
 * blighted players and never re-target one, for the whole blight from stage 0. {@link HollowBlight#standDownAndSpeak}
 * clears any target on you each tick, so an attacker that already had you targeted stands down at once. */
@Mixin(NearestAttackableTargetGoal.class)
final class ZombieBlightMixin {
	@Shadow
	@Nullable
	protected LivingEntity target;
	@Shadow
	protected Entity mob;

	@Inject(method = "findTarget", at = @At("TAIL"))
	private void jaymoskingdoms$skipBlighted(CallbackInfo ci) {
		if (mob instanceof Zombie && target instanceof ServerPlayer player && HollowBlight.blighted(player)) {
			target = null;
		}
	}
}
