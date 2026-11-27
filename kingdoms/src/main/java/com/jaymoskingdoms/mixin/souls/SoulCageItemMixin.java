package com.jaymoskingdoms.mixin.souls;

import com.jaymoskingdoms.SoulJars;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A cage taken up whole with a soul jar ({@link SoulJars}) is set down as it was: its mob, its timing and its charges.
 * Vanilla lets only an operator set a spawner's data from an item, so this is our own copy, kept under our own key.
 */
@Mixin(BlockItem.class)
abstract class SoulCageItemMixin {
	@Inject(method = "updateCustomBlockEntityTag(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)Z",
		at = @At("HEAD"), cancellable = true)
	private static void jaymoskingdoms$setCageDown(Level level, @Nullable Player player, BlockPos pos, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (level.isClientSide() || data == null || !(level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner)) {
			return;
		}
		CompoundTag cage = data.copyTag().getCompound(SoulJars.CAGE_KEY).orElse(null);
		if (cage == null) {
			return;
		}
		try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(spawner.problemPath(), com.jaymoskingdoms.JaymosKingdoms.LOGGER)) {
			spawner.loadCustomOnly(TagValueInput.create(reporter, level.registryAccess(), cage));
		}
		spawner.setChanged();
		level.sendBlockUpdated(pos, spawner.getBlockState(), spawner.getBlockState(), 3);
		cir.setReturnValue(true);
	}
}
