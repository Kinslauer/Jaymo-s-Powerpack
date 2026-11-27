package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.Overgrowth;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.feature.TemplateFeature;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A tree, fallen log, bush or boulder the world pastes from a template ({@code minecraft:template}, as Just Better Forest's
 * trees are) isn't set down in or against anything built: a house, a wall, a road or a field ({@link Overgrowth#crowds}).
 */
@Mixin(TemplateFeature.class)
abstract class TemplateFeatureMixin {
	@WrapOperation(method = "place", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;placeInWorld(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructurePlaceSettings;Lnet/minecraft/util/RandomSource;I)Z"))
	private boolean jaymoskingdoms$notOnBuildings(StructureTemplate template, ServerLevelAccessor level, BlockPos pos, BlockPos pivot,
		StructurePlaceSettings settings, RandomSource random, int flags, Operation<Boolean> original) {
		if (Overgrowth.crowds(level, template.getBoundingBox(settings, pos))) {
			return false;
		}
		return original.call(template, level, pos, pivot, settings, random, flags);
	}
}
