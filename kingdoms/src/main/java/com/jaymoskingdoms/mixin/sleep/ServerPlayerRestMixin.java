package com.jaymoskingdoms.mixin.sleep;

import com.jaymoskingdoms.BedRest;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.7.4) Using a bed by day, where sleep isn't possible, or while sneaking lays you down to rest ({@link BedRest}). */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerRestMixin {
	@Shadow
	private boolean bedInRange(BlockPos pos, Direction direction) {
		throw new AssertionError();
	}

	@Shadow
	private boolean bedBlocked(BlockPos pos, Direction direction) {
		throw new AssertionError();
	}

	@Inject(method = "startSleepInBed", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$rest(AbstractBedBlock bed, BlockState state, BedRule rule, BlockPos pos,
		CallbackInfoReturnable<Either<Player.BedSleepingProblem, Unit>> cir) {
		ServerPlayer self = (ServerPlayer) (Object) this;
		if (BedRest.wantsRest(self, rule)) {
			Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
			cir.setReturnValue(BedRest.start(self, pos, this.bedInRange(pos, facing), this.bedBlocked(pos, facing), rule.canSetSpawn(self.level())));
		}
	}
}
