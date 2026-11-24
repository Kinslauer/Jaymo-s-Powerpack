package com.jaymospowers.buildblocks.mixin;

import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** A trapdoor's block set, to tell whether a hand can work it ({@link com.jaymospowers.buildblocks.TrapdoorUse}). */
@Mixin(TrapDoorBlock.class)
public interface TrapdoorTypeAccessor {
	@Invoker("getType")
	BlockSetType jaymospowers$type();
}
