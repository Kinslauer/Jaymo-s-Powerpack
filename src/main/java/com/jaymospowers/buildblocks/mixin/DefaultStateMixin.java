package com.jaymospowers.buildblocks.mixin;

import com.jaymospowers.buildblocks.Waterlogging;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * A door, gate or pressure plate gets its default state from the first value of each property, and a boolean's first
 * value is true: left alone every one would start waterlogged (and every door in an old world would load that way, since
 * a saved state only names the properties it had). Whichever block registers its default state, this one starts dry.
 */
@Mixin(Block.class)
public abstract class DefaultStateMixin {
	@ModifyVariable(method = "registerDefaultState", at = @At("HEAD"), argsOnly = true)
	private BlockState jaymospowers$dry(BlockState state) {
		return Waterlogging.dry(state);
	}
}
