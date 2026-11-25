package com.jaymoskingdoms.mixin.nightgifts;

import com.jaymoskingdoms.HollowBlight;
import com.jaymoskingdoms.HollowCaptains;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Shadow;

/** While a player carries the Hollow's blight, zombies never target them: they're passing out and turning,
 * not food. And no undead targets one of the risen dead's Hollow captains ({@link HollowCaptains}). Hooked into {@link NearestAttackableTargetGoal#findTarget}'s tail so zombie attackers skip
 * blighted players and never re-target one, for the whole blight from stage 0. {@link HollowBlight#standDownAndSpeak}
 * clears any target on you each tick, so an attacker that already had you targeted stands down at once. */
@Mixin(NearestAttackableTargetGoal.class)
abstract class ZombieBlightMixin extends TargetGoal {
	@Shadow
	@Nullable
	protected LivingEntity target;

	// Never called: lets the mixin reach TargetGoal's own 'mob' field (a mixin can only @Shadow its target's fields).
	private ZombieBlightMixin(Mob mob, boolean mustSee) {
		super(mob, mustSee);
	}

	@Inject(method = "findTarget", at = @At("TAIL"))
	private void jaymoskingdoms$skipBlighted(CallbackInfo ci) {
		if (mob instanceof Zombie && target instanceof ServerPlayer player && HollowBlight.blighted(player)) {
			target = null;
		} else if (target != null && HollowCaptains.is(target) && mob.is(EntityTypeTags.UNDEAD)) {
			// (nor do the dead go for the Hollow men who lead them)
			target = null;
		}
	}
}
