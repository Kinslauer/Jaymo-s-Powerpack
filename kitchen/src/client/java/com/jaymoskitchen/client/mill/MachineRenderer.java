package com.jaymoskitchen.client.mill;

import com.jaymoskitchen.mill.MachineBlock;
import com.jaymoskitchen.mill.MachineEntity;
import com.jaymoskitchen.mill.MachineKind;
import com.jaymoskitchen.mill.MachinePartBlock.Part;
import com.jaymoskitchen.mill.Mill;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws a mill machine's moving parts and what's in it (after Horse Power, LGPL-3.0): the hand grindstone's stone
 * turning with each crank, the chopper's blade and the press plate coming down as the work goes on, the mill's
 * flour, the log on the chopping block, the load in each machine, and the lead out to a working horse.
 */
public class MachineRenderer implements BlockEntityRenderer<MachineEntity, MachineRenderer.State> {
	private static final BlockDisplayContext DISPLAY = BlockDisplayContext.create();
	/** Client-side smoothing of the hand grindstone's turns, per machine. */
	private static final Map<BlockPos, Float> TURNS = new HashMap<>();

	public static class State extends BlockEntityRenderState {
		MachineKind kind = MachineKind.HAND_GRINDSTONE;
		float facing;
		float work;
		float turn;
		final ItemStackRenderState load = new ItemStackRenderState();
		boolean hasLoad;
		boolean loadSolid;
		final BlockModelRenderState part = new BlockModelRenderState();
		boolean hasPart;
		final BlockModelRenderState flour = new BlockModelRenderState();
		boolean hasFlour;
		final EntityRenderState.LeashState leash = new EntityRenderState.LeashState();
		boolean hasLeash;
	}

	private final BlockModelResolver blocks;
	private final ItemModelResolver items;

	public MachineRenderer(BlockEntityRendererProvider.Context context) {
		this.blocks = context.blockModelResolver();
		this.items = context.itemModelResolver();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(MachineEntity machine, State state, float partialTicks, Vec3 camera,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(machine, state, partialTicks, camera, breakProgress);
		MachineKind kind = machine.kind();
		state.kind = kind;
		Direction facing = machine.getBlockState().hasProperty(MachineBlock.FACING) ? machine.getBlockState().getValue(MachineBlock.FACING) : Direction.NORTH;
		state.facing = facing.toYRot() + 180.0F;
		state.work = machine.workFraction();

		ItemStack load = machine.input();
		state.hasLoad = !load.isEmpty();
		if (state.hasLoad) {
			items.updateForTopItem(state.load, load, ItemDisplayContext.FIXED, machine.getLevel(), null, 0);
			state.loadSolid = state.load.usesBlockLight();
			if (state.loadSolid) {
				items.updateForTopItem(state.load, load, ItemDisplayContext.NONE, machine.getLevel(), null, 0);
			}
		}

		Part part = switch (kind) {
			case HAND_GRINDSTONE -> Part.HAND_GRINDSTONE_CENTER;
			case CHOPPER -> Part.CHOPPER_BLADE;
			case PRESS, HORSE_PRESS -> Part.PRESS_TOP;
			default -> null;
		};
		state.hasPart = part != null;
		if (part != null) {
			blocks.update(state.part, Mill.PART.part(part), DISPLAY);
		}
		state.hasFlour = kind == MachineKind.GRINDSTONE && !machine.output().isEmpty();
		if (state.hasFlour) {
			blocks.update(state.flour, Mill.PART.part(Part.GRINDSTONE_FLOUR), DISPLAY);
		}
		if (kind == MachineKind.HAND_GRINDSTONE) {
			// a quarter turn per crank, eased in
			float target = machine.efforts() * 90.0F;
			float shown = TURNS.getOrDefault(machine.getBlockPos(), target);
			shown = Math.abs(target - shown) > 360.0F ? target : shown + (target - shown) * 0.25F;
			TURNS.put(machine.getBlockPos().immutable(), shown);
			state.turn = shown;
		}

		state.hasLeash = false;
		if (kind.horse && machine.workerId() >= 0 && machine.getLevel() != null) {
			Entity horse = machine.getLevel().getEntity(machine.workerId());
			if (horse != null) {
				// from the top of the post to the horse's neck
				BlockPos pos = machine.getBlockPos();
				double top = switch (kind) {
					case CHOPPER -> 31.0;
					case HORSE_PRESS -> 19.0;
					default -> 13.0;
				} / 16.0;
				Vec3 offset = new Vec3(0.5, top, 0.5);
				float yaw = horse.getYRot(partialTicks) * Mth.DEG_TO_RAD;
				Vec3 neck = horse.getPosition(partialTicks).add(-Mth.sin(yaw) * horse.getBbWidth() * 0.5, horse.getBbHeight() * 0.85,
					Mth.cos(yaw) * horse.getBbWidth() * 0.5);
				state.leash.offset = offset;
				state.leash.start = Vec3.atLowerCornerOf(pos).add(offset);
				state.leash.end = neck;
				state.leash.startBlockLight = machine.getLevel().getBrightness(LightLayer.BLOCK, pos);
				state.leash.startSkyLight = machine.getLevel().getBrightness(LightLayer.SKY, pos);
				BlockPos neckPos = BlockPos.containing(neck);
				state.leash.endBlockLight = machine.getLevel().getBrightness(LightLayer.BLOCK, neckPos);
				state.leash.endSkyLight = machine.getLevel().getBrightness(LightLayer.SKY, neckPos);
				state.leash.slack = true;
				state.hasLeash = true;
			}
		}
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		int light = state.lightCoords;
		if (state.hasPart) {
			poseStack.pushPose();
			facing(state, poseStack);
			switch (state.kind) {
				case HAND_GRINDSTONE -> {
					poseStack.translate(0.5F, 0.0F, 0.5F);
					poseStack.rotateDegrees(Axis.YP, -state.turn);
					poseStack.translate(-0.5F, 0.0F, -0.5F);
				}
				// the blade comes down into the log, and the plate down onto the load, as the work goes on
				case CHOPPER -> poseStack.translate(0.0F, -6.0F / 16.0F * state.work, 0.0F);
				default -> poseStack.translate(0.0F, -9.0F / 16.0F * state.work, 0.0F);
			}
			state.part.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
			poseStack.popPose();
		}
		if (state.hasFlour) {
			poseStack.pushPose();
			facing(state, poseStack);
			state.flour.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
			poseStack.popPose();
		}
		if (state.hasLoad) {
			poseStack.pushPose();
			switch (state.kind) {
				// a log stood on the block, under the blade
				case CHOPPING_BLOCK, CHOPPER -> {
					poseStack.translate(0.5F, 6.0F / 16.0F + 0.5F, 0.5F);
					if (!state.loadSolid) {
						poseStack.scale(0.6F, 0.6F, 0.6F);
					}
				}
				// lying in the press box
				case PRESS, HORSE_PRESS -> {
					poseStack.translate(0.5F, 2.0F / 16.0F, 0.5F);
					poseStack.rotateDegrees(Axis.XP, 90.0F);
					poseStack.scale(0.7F, 0.7F, 0.7F);
				}
				// sat on the stone
				default -> {
					poseStack.translate(0.5F, (state.kind == MachineKind.HAND_GRINDSTONE ? 10.5F : 8.5F) / 16.0F, 0.5F);
					poseStack.rotateDegrees(Axis.XP, 90.0F);
					poseStack.scale(0.45F, 0.45F, 0.45F);
				}
			}
			state.load.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
			poseStack.popPose();
		}
		if (state.hasLeash) {
			collector.submitLeash(poseStack, state.leash);
		}
	}

	private static void facing(State state, PoseStack poseStack) {
		poseStack.translate(0.5F, 0.0F, 0.5F);
		poseStack.rotateDegrees(Axis.YP, -state.facing);
		poseStack.translate(-0.5F, 0.0F, -0.5F);
	}

	@Override
	public boolean shouldRenderOffScreen() {
		// the lead runs out to the horse
		return true;
	}
}
