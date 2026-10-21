package com.jaymoskitchen.mixin;

import com.jaymoskitchen.mill.power.VillageWindmills;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Village windmill towers get a real windmill: once a jigsaw piece's part in this chunk ({@code box}) is placed,
 * {@link VillageWindmills} swaps the stone sails of a listed template for a turning one. Jigsaw pieces are never
 * mirrored, so the rotation and the piece's corner are all it needs.
 */
@Mixin(SinglePoolElement.class)
abstract class VillageWindmillMixin {
	@Shadow
	@Final
	protected Either<Identifier, StructureTemplate> template;

	@Inject(method = "place", at = @At("RETURN"))
	private void jaymoskitchen$villageWindmill(StructureTemplateManager templates, WorldGenLevel level, StructureManager structures,
		ChunkGenerator generator, BlockPos position, BlockPos referencePos, Rotation rotation, BoundingBox box, RandomSource random,
		LiquidSettings liquidSettings, boolean keepJigsaws, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) {
			template.left().ifPresent(id -> VillageWindmills.placed(id, templates, level, position, rotation, box));
		}
	}
}
