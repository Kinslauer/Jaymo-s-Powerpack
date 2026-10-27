package com.jaymoskitchen.mixin;

import com.jaymoskitchen.delight.CanvasSign;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * (From Farmer's Delight, MIT.) A canvas sign on a dark canvas starts with white text rather than black: wherever a sign
 * makes its blank text (new, loaded without any, or placed from an item) it is made white instead.
 */
@Mixin(SignBlockEntity.class)
public abstract class SignBlockEntityMixin extends BlockEntity {
	public SignBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@ModifyExpressionValue(method = {"<init>(Lnet/minecraft/world/level/block/entity/BlockEntityType;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V", "loadAdditional", "applyImplicitComponents"},
		at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/block/entity/SignText;EMPTY:Lnet/minecraft/world/level/block/entity/SignText;"))
	private SignText jaymoskitchen$whiteTextOnDarkCanvas(SignText text) {
		if (getBlockState().getBlock() instanceof CanvasSign canvas && canvas.isDarkBackground()) {
			return text.withColor(DyeColor.WHITE);
		}
		return text;
	}
}
