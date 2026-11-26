package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.VillagerFixes;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.behavior.HarvestFarmland;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A farmer looks over its whole field for work, not only the blocks at its feet, and plants back the crop it just cut
 * ({@link VillagerFixes#field}, {@link VillagerFixes#replant}).
 */
@Mixin(HarvestFarmland.class)
abstract class VillagerFarmMixin {
	@Shadow
	private @Nullable BlockPos aboveFarmlandPos;
	@Shadow
	@Final
	private List<BlockPos> validFarmlandAroundVillager;

	@Unique
	private long jaymoskingdoms$nextLook;

	@Shadow
	private boolean validPos(BlockPos pos, ServerLevel level) {
		throw new AssertionError();
	}

	@Inject(method = "checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/villager/Villager;)Z",
		at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$wholeField(ServerLevel level, Villager body, CallbackInfoReturnable<Boolean> cir) {
		long now = level.getGameTime();
		if (cir.getReturnValueZ() || now < this.jaymoskingdoms$nextLook || !level.getGameRules().get(GameRules.MOB_GRIEFING)
			|| !body.getVillagerData().profession().is(VillagerProfession.FARMER)) {
			return;
		}
		this.jaymoskingdoms$nextLook = now + VillagerFixes.FIELD_LOOK_EVERY;
		BlockPos work = VillagerFixes.field(body, p -> this.validPos(p, level));
		if (work != null) {
			this.validFarmlandAroundVillager.add(work);
			this.aboveFarmlandPos = work;
			cir.setReturnValue(true);
		}
	}

	@WrapOperation(method = "tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/villager/Villager;J)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;)Z"))
	private boolean jaymoskingdoms$replant(ServerLevel level, BlockPos pos, boolean drop, @Nullable Entity by, Operation<Boolean> original) {
		BlockState cut = level.getBlockState(pos);
		boolean done = original.call(level, pos, drop, by);
		if (done && by instanceof Villager farmer) {
			VillagerFixes.replant(level, farmer, pos, cut);
		}
		return done;
	}
}
