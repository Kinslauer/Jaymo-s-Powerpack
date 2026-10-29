package com.jaymoskingdoms.mixin.mca;

import com.jaymoskingdoms.mixin.MobAccessor;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.MCAMoveControl;
import net.conczin.mca.entity.ai.brain.tasks.ArcherMovementTask;
import net.minecraft.world.entity.ai.control.MoveControl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * (0.7.338) The Deck server crashed ("Ticking entity", a horse with an MCA archer on it): MCA's archer movement task
 * casts {@code villager.getMoveControl()} to its {@link MCAMoveControl} to strafe, but vanilla's
 * {@code Mob.getMoveControl()} hands back the <i>vehicle's</i> control while a mob rides another mob, and a horse's
 * is a plain {@link MoveControl}. MCA never sat its archers on horses; {@code Cavalry} does (0.7.295). Here the task's
 * three calls get the villager's own control whenever the one vanilla returns isn't MCA's: a mounted archer's strafe
 * then steers nothing (the horse carries him) instead of crashing the server.
 */
@Mixin(ArcherMovementTask.class)
abstract class McaMountedArcherMixin {
	@Redirect(method = {"continueStrafe", "finishStrafe", "clearCombatState"},
		at = @At(value = "INVOKE", target = "Lnet/conczin/mca/entity/VillagerEntityMCA;getMoveControl()Lnet/minecraft/world/entity/ai/control/MoveControl;"))
	private MoveControl<?> jaymoskingdoms$ownControl(VillagerEntityMCA villager) {
		MoveControl<?> control = villager.getMoveControl();
		return control instanceof MCAMoveControl ? control : ((MobAccessor) villager).jaymoskingdoms$moveControl();
	}
}
