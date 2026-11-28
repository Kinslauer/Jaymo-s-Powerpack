package com.jaymospowers.mixin;

import com.jaymospowers.combat.AttackTells;
import com.jaymospowers.core.Dismember;
import java.util.Map;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Brain-driven villager tasks (MCA's {@code BowTask} and {@code ExtendedMeleeAttackTask}) when an NPC has lost both
 * arms: bow tasks stop, hand/weapon punches and slashes are skipped, and an NPC that can still kick (or a zombie
 * villager that can still bite) strikes through {@link AttackTells} at low damage instead.
 */
@Mixin(Behavior.class)
abstract class ArmlessBehaviorMixin<E extends LivingEntity> {
	@Unique
	private static final long ARMLESS_MELEE_COOLDOWN = 20L;

	@Shadow
	@Final
	protected Map<MemoryModuleType<?>, MemoryStatus> entryCondition;

	@Shadow
	protected abstract boolean hasRequiredMemories(E body);

	@Shadow
	protected abstract boolean checkExtraStartConditions(ServerLevel level, E body);

	@Inject(method = "tryStart", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$armlessTryStart(ServerLevel level, E body, long timestamp, CallbackInfoReturnable<Boolean> cir) {
		if (!(body instanceof Mob mob) || !Dismember.armless(mob)) {
			return;
		}
		String task = this.getClass().getSimpleName();
		if ("BowTask".equals(task)) {
			cir.setReturnValue(false);
			return;
		}
		if (!"ExtendedMeleeAttackTask".equals(task)) {
			return;
		}
		cir.setReturnValue(false);
		if (!Dismember.canAttack(mob) || !this.hasRequiredMemories(body) || !this.checkExtraStartConditions(level, body)) {
			return;
		}
		LivingEntity target = jaymospowers$targetOf(mob);
		if (target != null && target.isAlive() && AttackTells.closeEnough(mob, target)) {
			BehaviorUtils.lookAtEntity(mob, target);
			AttackTells.windUp(mob, level, target);
			mob.getBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_COOLING_DOWN, true, ARMLESS_MELEE_COOLDOWN);
		}
	}

	@Inject(method = "tickOrStop", at = @At("HEAD"), cancellable = true)
	@SuppressWarnings("unchecked")
	private void jaymospowers$armlessStopBow(ServerLevel level, E body, long timestamp, CallbackInfo ci) {
		if (body instanceof Mob mob && Dismember.armless(mob) && "BowTask".equals(this.getClass().getSimpleName())) {
			((BehaviorControl<E>) this).doStop(level, body, timestamp);
			ci.cancel();
		}
	}

	@Unique
	private LivingEntity jaymospowers$targetOf(Mob mob) {
		for (Map.Entry<MemoryModuleType<?>, MemoryStatus> entry : this.entryCondition.entrySet()) {
			if (entry.getValue() == MemoryStatus.VALUE_PRESENT) {
				Optional<?> value = mob.getBrain().getMemory(entry.getKey());
				if (value.isPresent() && value.get() instanceof LivingEntity living) {
					return living;
				}
			}
		}
		return null;
	}
}
