package com.jaymospowers.mixin;

import com.jaymospowers.JaymosPowers;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A goal selector that hands vanilla a null goal skips that creature's goals for the tick instead of stopping the
 * server. The set of goals is a fastutil hash set: removing a goal while it is walked (another mod's hook, or a goal
 * changing its own creature) can shift keys under the iterator so it returns null, and the NullPointerException in
 * {@code GoalSelector.tick} stopped the Deck server (a zombie, 2026-11-18 1:07 PM). Only that exception is caught.
 */
@Mixin(Mob.class)
abstract class NullGoalMixin {
	/** The kinds of creature already reported, so a cause that keeps coming back still shows once for each. */
	private static final java.util.Set<net.minecraft.world.entity.EntityType<?>> jaymospowers$warned = java.util.concurrent.ConcurrentHashMap.newKeySet();

	@WrapOperation(method = "serverAiStep", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/ai/goal/GoalSelector;tick()V"))
	private void jaymospowers$guardTick(GoalSelector selector, Operation<Void> original) {
		try {
			original.call(selector);
		} catch (NullPointerException e) {
			jaymospowers$warn(e);
		}
	}

	@WrapOperation(method = "serverAiStep", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/ai/goal/GoalSelector;tickRunningGoals(Z)V"))
	private void jaymospowers$guardRunning(GoalSelector selector, boolean everyTick, Operation<Void> original) {
		try {
			original.call(selector, everyTick);
		} catch (NullPointerException e) {
			jaymospowers$warn(e);
		}
	}

	private void jaymospowers$warn(NullPointerException e) {
		String message = String.valueOf(e.getMessage());
		if (!message.contains("WrappedGoal")) {
			throw e;
		}
		// (the throw left the selector's profiler section open)
		net.minecraft.util.profiling.Profiler.get().pop();
		net.minecraft.world.entity.EntityType<?> type = ((Mob)(Object)this).getType();
		if (jaymospowers$warned.add(type)) {
			JaymosPowers.LOGGER.warn("A {} had a null goal; its goals skip this tick (further ones of this kind are not logged)", type, e);
		}
	}
}
