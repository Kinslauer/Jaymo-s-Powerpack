package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.Collapse;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Fire takes a building the way it does in Andy's clip (2026-11-26: "flames should engulf blocks faster and burn for
 * longer before breaking, and unsupported blocks should fall"):
 * <ul>
 * <li>Flames leap to the air beside anything that burns {@link #SPREAD}x as often as vanilla's, so a house is wrapped
 * in fire fast.</li>
 * <li>A burning block lasts {@link #LASTS}x as long before the fire eats it, so it stands alight a while first.</li>
 * <li>When a built block does burn away, what it held up comes down, still burning ({@link Collapse#burnt}), and the
 * spot gives off a grey puff and black soot.</li>
 * </ul>
 * Rain, the fire-spread gamerule and everything else about vanilla fire are as before; Forest Fire leaves fire to
 * vanilla (its port's PORT.md), so this is the one place fire's pace is set.
 */
@Mixin(FireBlock.class)
abstract class FireBlockBurnMixin {
	/** How many times as often flames leap to a burnable block's air (vanilla rolls 1 in 100 for each place). */
	@Unique
	private static final float SPREAD = 2.5F;
	/** How many times as long a burning block lasts before it's gone. */
	@Unique
	private static final int LASTS = 3;
	/** The grey puffs and black soot where a block burns away. */
	@Unique
	private static final int PUFFS = 2, SOOT = 6;

	/** The spread roll's 100 (and the extra 100 a block for places higher up): smaller, so it lands more often. */
	@ModifyConstant(method = "tick", constant = @Constant(intValue = 100))
	private int jaymoskingdoms$spread(int rate) {
		return Math.max(1, Math.round(rate / SPREAD));
	}

	/** The burn-out roll's odds (1 in 300, or 250 up and down): larger, so a block burns longer. */
	@ModifyVariable(method = "checkBurnOut", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private int jaymoskingdoms$lasts(int chance) {
		return chance * LASTS;
	}

	@WrapOperation(method = "checkBurnOut", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
	private boolean jaymoskingdoms$burntToFire(Level level, BlockPos pos, BlockState now, Operation<Boolean> original) {
		Collapse.Look look = jaymoskingdoms$before(level, pos);
		boolean done = original.call(level, pos, now);
		jaymoskingdoms$after(level, pos, look);
		return done;
	}

	@WrapOperation(method = "checkBurnOut", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/Level;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"))
	private boolean jaymoskingdoms$burntAway(Level level, BlockPos pos, boolean moving, Operation<Boolean> original) {
		Collapse.Look look = jaymoskingdoms$before(level, pos);
		boolean done = original.call(level, pos, moving);
		jaymoskingdoms$after(level, pos, look);
		return done;
	}

	@Unique
	private static Collapse.Look jaymoskingdoms$before(Level level, BlockPos pos) {
		return level instanceof ServerLevel server ? Collapse.burnt(server, pos, level.getBlockState(pos)) : null;
	}

	@Unique
	private static void jaymoskingdoms$after(Level level, BlockPos pos, Collapse.Look look) {
		Collapse.after(look);
		if (level instanceof ServerLevel server) {
			RandomSource random = server.getRandom();
			double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
			server.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y + 0.3, z, PUFFS, 0.3, 0.3, 0.3, 0.01 + random.nextFloat() * 0.01);
			server.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, SOOT, 0.4, 0.4, 0.4, 0.02);
		}
	}
}
