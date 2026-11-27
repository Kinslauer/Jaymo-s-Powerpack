package com.jaymoskingdoms.mixin.mca;

import net.conczin.mca.entity.ai.Messenger;
import net.conczin.mca.entity.ai.brain.tasks.ExtendedMeleeAttackTask;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * (Andy, 2026-11-27) MCA's melee kill line ({@code villager.kill}: "Monster down!", "Take that, scum!") was one list for
 * every kill, a cow's as much as a zombie's. It's kept quiet: {@link com.jaymoskingdoms.GuardCries} calls a kill out in
 * kind, whatever weapon made it.
 */
@Mixin(ExtendedMeleeAttackTask.class)
abstract class McaGuardKillLineMixin {
	@Redirect(method = "start(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Mob;J)V",
		at = @At(value = "INVOKE", target = "Lnet/conczin/mca/entity/ai/Messenger;sendChatToAllAround(Ljava/lang/String;[Ljava/lang/Object;)V"), require = 0)
	private void jaymoskingdoms$quietKill(Messenger speaker, String phrase, Object[] args) {
		if (!"villager.kill".equals(phrase)) {
			speaker.sendChatToAllAround(phrase, args);
		}
	}
}
