package com.jaymoskingdoms.mixin.mca;

import com.jaymoskingdoms.GuardCries;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.entity.ai.brain.tasks.SayTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (Andy, 2026-11-27: "guards should respond contextually, for example they always say halt criminal scum, even if
 * attacking a zombie, or animal") MCA's guard battle cry ({@code villager.attack}, one list for every foe) gives way to
 * {@link GuardCries}: a line for what the guard is going for, how far off it is, how many there are and how hurt the
 * guard is. MCA's other shouts (its retreat) stand.
 */
@Mixin(SayTask.class)
abstract class McaGuardCryMixin {
	@Unique
	private static final String ATTACK = "villager.attack";

	@Shadow
	@Final
	private String phrase;

	@Shadow
	private long lastShout;

	@Inject(method = "start(Lnet/minecraft/server/level/ServerLevel;Lnet/conczin/mca/entity/VillagerEntityMCA;J)V", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$cry(ServerLevel level, VillagerEntityMCA villager, long time, CallbackInfo ci) {
		if (!ATTACK.equals(this.phrase)) {
			return;
		}
		ci.cancel();
		this.lastShout = level.getGameTime();
		// what MCA's guard is going for: its attack target, or the nearest foe its senses picked out
		LivingEntity target = villager.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET).orElse(null);
		if (target == null || !target.isAlive()) {
			target = villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY).orElse(null);
		}
		GuardCries.shout(level, villager, target);
	}
}
