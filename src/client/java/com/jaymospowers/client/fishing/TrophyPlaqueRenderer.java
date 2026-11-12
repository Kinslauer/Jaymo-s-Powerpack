package com.jaymospowers.client.fishing;

import com.jaymospowers.fishing.FishCatch;
import com.jaymospowers.fishing.FishingBlocks;
import com.jaymospowers.fishing.Species;
import com.jaymospowers.fishing.TrophyPlaqueEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws what's mounted on a trophy plaque: the fish (or a shark's jaw) on the board, bigger for a bigger fish, and under it
 * a brass plate: its name (a legend's own), weight and length, who caught it and on which day.
 */
public class TrophyPlaqueRenderer implements BlockEntityRenderer<TrophyPlaqueEntity, TrophyPlaqueRenderer.State> {
	private static final float TEXT_SCALE = 0.006F;
	private static final int PLATE = 0xFFE8C878, PLATE_LEGEND = 0xFFFFD34A;

	public static class State extends BlockEntityRenderState {
		final ItemStackRenderState item = new ItemStackRenderState();
		boolean has;
		float size = 0.6F;
		Direction facing = Direction.NORTH;
		final List<FormattedCharSequence> lines = new ArrayList<>();
		boolean legend;
	}

	private final ItemModelResolver itemModels;
	private final Font font;

	public TrophyPlaqueRenderer(BlockEntityRendererProvider.Context context) {
		this.itemModels = context.itemModelResolver();
		this.font = context.font();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(TrophyPlaqueEntity plaque, State state, float partialTicks, Vec3 camera,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(plaque, state, partialTicks, camera, breakProgress);
		state.facing = plaque.getBlockState().getValue(FishingBlocks.FACING);
		ItemStack stack = plaque.mounted();
		state.has = !stack.isEmpty();
		state.lines.clear();
		if (!state.has) {
			return;
		}
		itemModels.updateForTopItem(state.item, stack, ItemDisplayContext.FIXED, plaque.getLevel(), null, 0);
		FishCatch.Info info = plaque.info();
		if (info == null) {
			return;
		}
		Species sp = info.kind();
		state.legend = !info.legend().isEmpty();
		// a bigger fish on a bigger mount, within the board
		float usual = sp == null ? 1.0F : sp.usualKg();
		state.size = Math.max(0.32F, Math.min(0.55F, 0.42F * (float) Math.cbrt(info.kg() / usual)));
		Component name = state.legend ? Component.literal(info.legend()) : Component.translatable(sp == null ? "species.jaymospowers.other" : sp.nameKey());
		state.lines.add(name.getVisualOrderText());
		state.lines.add(Component.translatable("block.jaymospowers.trophy_plaque.weight", String.format(Locale.ROOT, "%.1f", info.kg()), info.cm())
			.getVisualOrderText());
		if (!info.angler().isEmpty()) {
			state.lines.add(Component.translatable("block.jaymospowers.trophy_plaque.by", info.angler(), info.day()).getVisualOrderText());
		}
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (!state.has) {
			return;
		}
		poseStack.pushPose();
		poseStack.translate(0.5F, 0.5F, 0.5F);
		poseStack.rotateDegrees(Axis.YP, -state.facing.toYRot() + 180.0F);
		// the board's face is 2.5/16 out from the wall (the back of the block)
		poseStack.translate(0.0F, 0.0F, 0.5F - 2.6F / 16.0F);
		poseStack.pushPose();
		poseStack.translate(0.0F, 0.13F, -0.03F);
		poseStack.rotateDegrees(Axis.YP, 180.0F);
		poseStack.scale(state.size, state.size, state.size);
		state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
		// the brass plate's words, under the fish
		poseStack.translate(0.0F, -0.13F, -0.005F);
		poseStack.rotateDegrees(Axis.YP, 180.0F);
		poseStack.scale(TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);
		for (int i = 0; i < state.lines.size(); i++) {
			FormattedCharSequence line = state.lines.get(i);
			float w = font.width(line);
			collector.submitText(poseStack, -w / 2.0F, i * 10, line, false, Font.DisplayMode.POLYGON_OFFSET, state.lightCoords,
				i == 0 && state.legend ? PLATE_LEGEND : PLATE, 0, 0);
		}
		poseStack.popPose();
	}
}
