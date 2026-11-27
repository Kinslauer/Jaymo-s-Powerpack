package com.jaymospowers.mixin;

import com.jaymospowers.combat.HuskBurrow;
import com.jaymospowers.combat.LungeGoal;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Zombies and husks lunge ({@link LungeGoal}): the goal is added with the melee goal, so every zombie that keeps vanilla's
 * goals has it (drowned and zombified piglins set their own and don't). A husk that spawns on sand after dark may start
 * buried ({@link HuskBurrow#spawned}).
 */
@Mixin(Zombie.class)
abstract class ZombieLungeMixin {
	@Shadow
	protected GoalSelector goalSelector;

	@Inject(method = "addBehaviourGoals", at = @At("TAIL"))
	private void jaymospowers$lunge(CallbackInfo ci) {
		this.goalSelector.addGoal(1, new LungeGoal((PathfinderMob) (Object) this));
	}

	@Inject(method = "finalizeSpawn", at = @At("RETURN"))
	private void jaymospowers$buried(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, SpawnGroupData data,
		CallbackInfoReturnable<SpawnGroupData> cir) {
		if ((Object) this instanceof Husk husk) {
			HuskBurrow.spawned(husk, reason);
		}
	}
}
