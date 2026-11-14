package com.jaymospowers.client.tinkering;

import com.jaymospowers.tinkering.smeltery.ChannelBlock;
import com.jaymospowers.tinkering.smeltery.ChannelEntity;
import com.jaymospowers.tinkering.smeltery.MelterEntity;
import com.jaymospowers.tinkering.smeltery.Molten;
import com.jaymospowers.tinkering.smeltery.MoltenEntity;
import com.jaymospowers.tinkering.smeltery.Tank;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** The metal in a seared tank, a melter (its upper half) or a channel (the trough and its joined arms). */
public class MoltenRenderer implements BlockEntityRenderer<MoltenEntity, MoltenRenderer.State> {
	public static class State extends BlockEntityRenderState {
		/** The liquids bottom first, and each one's share of the whole capacity. */
		final List<Molten> molten = new ArrayList<>();
		final List<Float> share = new ArrayList<>();
		/** 0 a tank, 1 a melter, 2 a channel. */
		int kind;
		/** A channel's joined sides (north, east, south, west) and whether it opens downward. */
		final boolean[] sides = new boolean[4];
		boolean down;
	}

	private static final float PX = 1 / 16.0F;
	private final SpriteGetter sprites;

	public MoltenRenderer(BlockEntityRendererProvider.Context context) {
		this.sprites = context.sprites();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(MoltenEntity entity, State state, float partialTicks, Vec3 camera,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(entity, state, partialTicks, camera, breakProgress);
		state.molten.clear();
		state.share.clear();
		for (Tank.Layer layer : entity.tank().layers()) {
			state.molten.add(layer.molten);
			state.share.add(layer.amount / (float) Math.max(1, entity.tank().capacity()));
		}
		state.kind = entity instanceof ChannelEntity ? 2 : entity instanceof MelterEntity ? 1 : 0;
		if (state.kind == 2) {
			BlockState block = entity.getBlockState();
			state.sides[0] = block.getValue(ChannelBlock.NORTH);
			state.sides[1] = block.getValue(ChannelBlock.EAST);
			state.sides[2] = block.getValue(ChannelBlock.SOUTH);
			state.sides[3] = block.getValue(ChannelBlock.WEST);
			state.down = block.getValue(ChannelBlock.DOWN);
		}
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.molten.isEmpty()) {
			return;
		}
		if (state.kind == 2) {
			TextureAtlasSprite sprite = MoltenQuads.sprite(sprites, state.molten.getFirst());
			float top = (6.5F + 2.0F * Math.min(1.0F, state.share.getFirst())) * PX;
			MoltenQuads.box(poseStack, collector, sprite, 6 * PX, 6 * PX, 6 * PX, 10 * PX, top, 10 * PX, true);
			if (state.sides[0]) {
				MoltenQuads.box(poseStack, collector, sprite, 6 * PX, 6 * PX, 0, 10 * PX, top, 6 * PX, true);
			}
			if (state.sides[1]) {
				MoltenQuads.box(poseStack, collector, sprite, 10 * PX, 6 * PX, 6 * PX, 1, top, 10 * PX, true);
			}
			if (state.sides[2]) {
				MoltenQuads.box(poseStack, collector, sprite, 6 * PX, 6 * PX, 10 * PX, 10 * PX, top, 1, true);
			}
			if (state.sides[3]) {
				MoltenQuads.box(poseStack, collector, sprite, 0, 6 * PX, 6 * PX, 6 * PX, top, 10 * PX, true);
			}
			if (state.down) {
				// the stream falling through the open floor onto what is under it
				MoltenQuads.box(poseStack, collector, sprite, 7 * PX, -12 * PX, 7 * PX, 9 * PX, 6 * PX, 9 * PX, false);
			}
			return;
		}
		// inside the glass: the whole block for a tank, the upper half for a melter
		float floor = state.kind == 1 ? 8.1F * PX : 0.1F * PX, room = 15.9F * PX - floor, y = floor;
		for (int i = 0; i < state.molten.size(); i++) {
			float top = Math.min(15.9F * PX, y + room * state.share.get(i));
			MoltenQuads.box(poseStack, collector, MoltenQuads.sprite(sprites, state.molten.get(i)), 0.1F * PX, y, 0.1F * PX, 15.9F * PX, top,
				15.9F * PX, i == state.molten.size() - 1);
			y = top;
		}
	}
}
