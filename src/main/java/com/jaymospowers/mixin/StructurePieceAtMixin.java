package com.jaymospowers.mixin;

import com.jaymospowers.core.PieceIndex;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "Is this block inside a piece of that structure" is answered from the start's {@link PieceIndex} when it has many
 * pieces, rather than by walking them all on every natural spawn attempt (same answer, much less work).
 */
@Mixin(StructureManager.class)
abstract class StructurePieceAtMixin {
	@Inject(method = "structureHasPieceAt(IIILnet/minecraft/world/level/levelgen/structure/StructureStart;)Z", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$indexed(int x, int y, int z, StructureStart start, CallbackInfoReturnable<Boolean> cir) {
		if (start.getPieces().size() >= PieceIndex.MIN_PIECES) {
			cir.setReturnValue(((PieceIndex.Holder) (Object) start).jaymospowers$pieceIndex().has(x, y, z));
		}
	}
}
