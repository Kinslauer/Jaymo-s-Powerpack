package com.jaymospowers.mixin;

import com.jaymospowers.core.TemplateUpgrades;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.datafixers.DataFixer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.levelgen.structure.templatesystem.loader.TemplateSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A structure template saved by an older game is upgraded once and kept ({@link TemplateUpgrades}), not every session. */
@Mixin(TemplateSource.class)
abstract class TemplateUpgradeMixin {
	@WrapOperation(method = "readStructure(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/util/datafix/DataFixTypes;updateToCurrentVersion(Lcom/mojang/datafixers/DataFixer;Lnet/minecraft/nbt/CompoundTag;I)Lnet/minecraft/nbt/CompoundTag;"))
	private CompoundTag jaymospowers$keptUpgrade(DataFixTypes type, DataFixer fixer, CompoundTag tag, int version, Operation<CompoundTag> original) {
		return TemplateUpgrades.upgraded(tag, version, () -> original.call(type, fixer, tag, version));
	}
}
