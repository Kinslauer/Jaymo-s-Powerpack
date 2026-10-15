package com.jaymospowers.enchanting.mixin;

import com.jaymospowers.enchanting.EnchantingMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The enchanting table opens our table ({@link com.jaymospowers.enchanting.Enchanting}) instead of vanilla's. */
@Mixin(EnchantingTableBlock.class)
public class EnchantingTableMixin {
	@Inject(method = "getMenuProvider", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$ourTable(BlockState state, Level level, BlockPos pos, CallbackInfoReturnable<MenuProvider> cir) {
		if (level.getBlockEntity(pos) instanceof EnchantingTableBlockEntity table) {
			cir.setReturnValue(new SimpleMenuProvider((id, inventory, player) -> new EnchantingMenu(id, inventory,
				ContainerLevelAccess.create(level, pos)), table.getDisplayName()));
		}
	}
}
