package com.jaymospowers.mixin;

import com.jaymospowers.core.PieceIndex;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/** Keeps the {@link PieceIndex} of a structure start, built the first time it is asked for. */
@Mixin(StructureStart.class)
abstract class StructureStartPieceIndexMixin implements PieceIndex.Holder {
	@Unique
	private volatile PieceIndex jaymospowers$pieceIndex;

	@Shadow
	public abstract java.util.List<net.minecraft.world.level.levelgen.structure.StructurePiece> getPieces();

	@Override
	public PieceIndex jaymospowers$pieceIndex() {
		PieceIndex index = this.jaymospowers$pieceIndex;
		if (index == null) {
			index = new PieceIndex(this.getPieces());
			this.jaymospowers$pieceIndex = index;
		}
		return index;
	}
}
