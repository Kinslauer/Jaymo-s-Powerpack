package com.jaymospowers.mixin.alexscaves;

import com.jaymospowers.compat.AlexsCavesCompat;
import java.util.function.Supplier;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Alex's Caves' ferrocave (the great hollow in a Magnetic Cave) walls its pools with our ferrosite, not galena ({@link AlexsCavesCompat}). */
@Pseudo
@Mixin(targets = "com.github.alexmodguy.alexscaves.server.level.structure.piece.FerrocaveStructurePiece", remap = false)
abstract class FerrocaveStoneMixin {
	@Redirect(method = "surroundCornerOfLiquid", require = 0, at = @At(value = "FIELD", opcode = org.objectweb.asm.Opcodes.GETSTATIC,
		target = "Lcom/github/alexmodguy/alexscaves/server/block/ACBlockRegistry;GALENA:Ljava/util/function/Supplier;"))
	private Supplier<Block> jaymospowers$ferrosite() {
		return AlexsCavesCompat.FERROSITE;
	}
}
