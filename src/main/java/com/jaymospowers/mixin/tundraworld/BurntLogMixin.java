package com.jaymospowers.mixin.tundraworld;

import com.jaymospowers.tundra.Regrowth;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A log that fire burns away in a taiga leaves its soil scorched ({@link Regrowth#scorch}), from which the burnt land
 * comes back as lichen woodland. The game's burn-out check turns the burnt block into fire or air; this looks at what
 * stood there first and what is there after.
 */
@Mixin(FireBlock.class)
abstract class BurntLogMixin {
	@Inject(method = "checkBurnOut", at = @At("HEAD"))
	private void jaymospowers$before(Level level, BlockPos pos, int chance, RandomSource random, int age, CallbackInfo ci) {
		jaymospowers$wasLog = level.getBlockState(pos).is(BlockTags.LOGS);
	}

	@Inject(method = "checkBurnOut", at = @At("RETURN"))
	private void jaymospowers$after(Level level, BlockPos pos, int chance, RandomSource random, int age, CallbackInfo ci) {
		if (jaymospowers$wasLog && level instanceof ServerLevel server && !level.getBlockState(pos).is(BlockTags.LOGS)) {
			Regrowth.scorch(server, pos);
		}
		jaymospowers$wasLog = false;
	}

	/** Whether the block being checked was a log (the server thread checks one at a time). */
	private boolean jaymospowers$wasLog;
}
