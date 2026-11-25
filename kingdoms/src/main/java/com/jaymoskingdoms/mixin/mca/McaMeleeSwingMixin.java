package com.jaymoskingdoms.mixin.mca;

import com.jaymoskingdoms.Blows;
import com.jaymoskingdoms.MobCombat;
import com.jaymoskingdoms.MobShields;
import net.conczin.mca.entity.ai.brain.tasks.ExtendedMeleeAttackTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.7.283) Andy: "it doesnt seem like MCA villagers have animations when attacking". MCA's melee task (guards, crews,
 * rulers, the gifted, anyone MCA sends at a foe) struck at once with vanilla's little arm flick, past every wind-up we
 * give other fighters. Now its blows go through {@link MobCombat} as {@code MeleeAttackGoalMixin}'s do: a weapon
 * Better Combat knows swings its combo (with the glint, the blade guard and the parry window), anything else
 * ({@link MobCombat#plainBlow}) a punch or a cut with the glint. Without Better Combat, MCA's own blow stands.
 * <p>
 * Fighting AI patch 3: a raised shield holds the blow with or without Better Combat ({@link MobShields#guard}: the block, the
 * counter after it), and without Better Combat MCA's own blow waits for a full charge and may come from a jump's fall
 * ({@link Blows#holds}).
 */
@Mixin(ExtendedMeleeAttackTask.class)
abstract class McaMeleeSwingMixin {
	@Shadow
	private LivingEntity getTarget(Mob mob) {
		throw new AssertionError();
	}

	@Inject(method = "start(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Mob;J)V", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$swing(ServerLevel level, Mob mob, long time, CallbackInfo ci) {
		LivingEntity target = this.getTarget(mob);
		if (target == null) {
			return;
		}
		// a guard held up: no blow until it comes down (MobShields)
		if (MobShields.guard(mob, target)) {
			ci.cancel();
			BehaviorUtils.lookAtEntity(mob, target);
			return;
		}
		if (!MobCombat.ENABLED) {
			// (MCA's own blow: not before a full charge, and a heavy weapon's maybe from a jump's fall)
			if (Blows.holds(mob, target)) {
				ci.cancel();
				BehaviorUtils.lookAtEntity(mob, target);
			}
			return;
		}
		ci.cancel();
		BehaviorUtils.lookAtEntity(mob, target);
		if (MobCombat.handles(mob)) {
			MobCombat.attackTick(mob, target);
		} else {
			MobCombat.plainBlow(mob, target);
		}
	}
}
