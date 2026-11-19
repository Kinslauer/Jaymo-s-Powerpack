package com.jaymoskingdoms.client.mixin.disasters;

import com.jaymoskingdoms.client.disasters.WindCloth;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.renderer.blockentity.state.BannerRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A banner's flag leans downwind ({@link WindCloth}): the lean rides in the integer part of the render state's phase
 * (the flag's swing repeats every whole phase, so it shows nothing there) to {@code BannerFlagWindMixin}.
 */
@Mixin(BannerRenderer.class)
abstract class BannerWindMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/level/block/entity/BannerBlockEntity;Lnet/minecraft/client/renderer/blockentity/state/BannerRenderState;FLnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V",
		at = @At("TAIL"))
	private void jaymoskingdoms$leanInTheWind(BannerBlockEntity banner, BannerRenderState state, float partialTicks, Vec3 camera,
		ModelFeatureRenderer.@Nullable CrumblingOverlay overlay, CallbackInfo ci) {
		Level level = banner.getLevel();
		if (level == null) {
			return;
		}
		BlockState block = banner.getBlockState();
		double fx, fz;
		if (block.getBlock() instanceof BannerBlock) {
			float yaw = block.getValue(BannerBlock.ROTATION) * 22.5F;
			fx = -Mth.sin(yaw * Mth.DEG_TO_RAD);
			fz = Mth.cos(yaw * Mth.DEG_TO_RAD);
		} else {
			Direction facing = block.getValue(WallBannerBlock.FACING);
			fx = facing.getStepX();
			fz = facing.getStepZ();
		}
		state.phase = WindCloth.encodeBanner(state.phase, WindCloth.bannerLean(level, banner.getBlockPos(), fx, fz));
	}
}
