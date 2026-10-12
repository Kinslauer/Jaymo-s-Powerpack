package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Wagon;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * (0.5.14) A wagon drawn from blocks, scaled down and put together: a storage cart (a plank bed with low sides, its load
 * of barrels, hay or stone showing what it carries, shafts out to its horse) or a king's carriage (a dark cabin open at
 * the windows, a roof and cushion in his colour, gilt edges, lanterns, big wheels behind and small ones in front). Its
 * wheels turn with the ground it covers. Original, no other mod's model.
 * <p>
 * (0.5.27) A rickshaw: two tall wheels under a bench seat, a folding hood in the settlement's colour, a footboard, a
 * rack behind for its load, and long shafts out to its puller's hands.
 */
public final class WagonRenderer extends EntityRenderer<Wagon, WagonRenderer.State> {
	/** A box of one block's look: its lowest corner and its size, in blocks, in the wagon's own frame (+z toward the horse). */
	private record Box(BlockState state, float x, float y, float z, float sx, float sy, float sz) {
	}

	/** A wheel: its middle, its radius, which side. */
	private record Wheel(float x, float y, float z, float radius) {
	}

	public static final class State extends EntityRenderState {
		int kind;
		int load;
		int color;
		float yaw;
		float wheel;
		final MovingBlockRenderState block = new MovingBlockRenderState();
	}

	private static final Map<String, BlockState> STATES = new HashMap<>();

	public WagonRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 1.0F;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected AABB getBoundingBoxForCulling(Wagon entity, float partialTicks) {
		return entity.getBoundingBox().inflate(3.0);
	}

	@Override
	public void extractRenderState(Wagon entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.kind = entity.kind();
		state.load = entity.load();
		state.color = entity.colorId();
		state.yaw = entity.getYRot(partialTicks);
		state.wheel = Mth.lerp(partialTicks, entity.wheelO, entity.wheel);
		MovingBlockRenderState block = state.block;
		BlockPos at = entity.blockPosition();
		block.blockPos = at.above();
		block.randomSeedPos = at;
		if (entity.level() instanceof ClientLevel level) {
			block.biome = level.getBiome(at);
			block.cardinalLighting = level.cardinalLighting();
			block.lightEngine = level.getLightEngine();
		}
	}

	private static BlockState block(String name) {
		return STATES.computeIfAbsent(name, n -> BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(n)).defaultBlockState());
	}

	private static String dye(int color) {
		return (color >= 0 && color < DyeColor.values().length ? DyeColor.byId(color) : DyeColor.RED).getName();
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		List<Box> boxes = new ArrayList<>();
		List<Wheel> wheels = new ArrayList<>();
		BlockState wheelWood;
		if (state.kind == Wagon.CARRIAGE || state.kind == Wagon.CAB) {
			carriage(boxes, wheels, state.color, state.kind == Wagon.CARRIAGE);
			wheelWood = block("dark_oak_planks");
		} else if (state.kind == Wagon.RICKSHAW) {
			rickshaw(boxes, wheels, state.load, state.color);
			wheelWood = block("spruce_planks");
		} else {
			cart(boxes, wheels, state.load, state.color);
			wheelWood = block("spruce_planks");
		}
		poseStack.pushPose();
		poseStack.rotateDegrees(Axis.YP, -state.yaw);
		for (Box b : boxes) {
			poseStack.pushPose();
			poseStack.translate(b.x(), b.y(), b.z());
			poseStack.scale(b.sx(), b.sy(), b.sz());
			this.draw(state, b.state(), poseStack, collector);
			poseStack.popPose();
		}
		for (Wheel w : wheels) {
			float r = w.radius();
			// (two squares crossed at 45 degrees read as a round wheel; an iron hub at its middle)
			for (int i = 0; i < 2; i++) {
				poseStack.pushPose();
				poseStack.translate(w.x(), w.y(), w.z());
				poseStack.rotateDegrees(Axis.XP, state.wheel * Mth.RAD_TO_DEG + i * 45.0F);
				float s = r * 1.42F;
				poseStack.translate(-0.06F, -s / 2.0F, -s / 2.0F);
				poseStack.scale(0.12F, s, s);
				this.draw(state, wheelWood, poseStack, collector);
				poseStack.popPose();
			}
			poseStack.pushPose();
			poseStack.translate(w.x() + Math.signum(w.x()) * 0.06F - 0.09F, w.y() - 0.09F, w.z() - 0.09F);
			poseStack.scale(0.18F, 0.18F, 0.18F);
			this.draw(state, Blocks.IRON_BLOCK.defaultBlockState(), poseStack, collector);
			poseStack.popPose();
		}
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	private void draw(State state, BlockState blockState, PoseStack poseStack, SubmitNodeCollector collector) {
		if (blockState.getRenderShape() != RenderShape.MODEL) {
			return;
		}
		// (each its own: the collector draws them later, not now)
		MovingBlockRenderState block = new MovingBlockRenderState();
		block.blockState = blockState;
		block.blockPos = state.block.blockPos;
		block.randomSeedPos = state.block.randomSeedPos;
		block.biome = state.block.biome;
		block.cardinalLighting = state.block.cardinalLighting;
		block.lightEngine = state.block.lightEngine;
		collector.submitMovingBlock(poseStack, block, state.outlineColor);
	}

	/** A box from its corner {@code (x, y, z)} to {@code (x2, y2, z2)}. */
	private static void box(List<Box> out, BlockState state, float x, float y, float z, float x2, float y2, float z2) {
		out.add(new Box(state, x, y, z, x2 - x, y2 - y, z2 - z));
	}

	private static void cart(List<Box> b, List<Wheel> wheels, int load, int color) {
		BlockState plank = block("spruce_planks"), dark = block("dark_oak_planks"), pole = block("stripped_spruce_log");
		for (float z : new float[] {-0.75F, 0.75F}) {
			wheels.add(new Wheel(-0.98F, 0.5F, z, 0.5F));
			wheels.add(new Wheel(0.98F, 0.5F, z, 0.5F));
			box(b, dark, -0.95F, 0.45F, z - 0.05F, 0.95F, 0.55F, z + 0.05F);
		}
		// the bed and its low sides
		box(b, plank, -0.85F, 0.6F, -1.2F, 0.85F, 0.8F, 1.2F);
		box(b, dark, -0.9F, 0.8F, -1.2F, -0.8F, 1.15F, 1.2F);
		box(b, dark, 0.8F, 0.8F, -1.2F, 0.9F, 1.15F, 1.2F);
		box(b, dark, -0.8F, 0.8F, -1.2F, 0.8F, 1.1F, -1.1F);
		box(b, dark, -0.8F, 0.8F, 1.1F, 0.8F, 1.1F, 1.2F);
		// the shafts, out to the horse's flanks
		box(b, pole, -0.5F, 0.72F, 1.2F, -0.4F, 0.82F, 2.5F);
		box(b, pole, 0.4F, 0.72F, 1.2F, 0.5F, 0.82F, 2.5F);
		box(b, pole, -0.5F, 0.72F, 2.4F, 0.5F, 0.82F, 2.5F);
		switch (load) {
			case Wagon.STONE -> {
				box(b, block("cobblestone"), -0.7F, 0.8F, -1.0F, 0.0F, 1.5F, -0.3F);
				box(b, block("cobblestone"), 0.05F, 0.8F, -1.0F, 0.7F, 1.4F, -0.35F);
				box(b, block("coal_ore"), -0.6F, 0.8F, -0.2F, 0.0F, 1.35F, 0.4F);
				box(b, block("iron_ore"), 0.1F, 0.8F, -0.2F, 0.65F, 1.3F, 0.35F);
				box(b, block("stone"), -0.4F, 0.8F, 0.5F, 0.4F, 1.25F, 1.0F);
			}
			case Wagon.FOOD -> {
				box(b, block("hay_block"), -0.75F, 0.8F, -1.05F, 0.05F, 1.55F, -0.25F);
				box(b, block("hay_block"), 0.05F, 0.8F, -1.05F, 0.75F, 1.5F, -0.3F);
				box(b, block("hay_block"), -0.4F, 1.5F, -0.95F, 0.4F, 2.1F, -0.35F);
				box(b, block("barrel"), -0.7F, 0.8F, -0.15F, -0.05F, 1.45F, 0.5F);
				box(b, block("pumpkin"), 0.1F, 0.8F, -0.1F, 0.6F, 1.3F, 0.4F);
				box(b, block("barrel"), -0.35F, 0.8F, 0.55F, 0.35F, 1.4F, 1.05F);
			}
			case Wagon.GOODS -> {
				box(b, block("barrel"), -0.75F, 0.8F, -1.05F, -0.05F, 1.5F, -0.35F);
				box(b, block("barrel"), 0.05F, 0.8F, -1.05F, 0.75F, 1.5F, -0.35F);
				box(b, block("barrel"), -0.4F, 1.5F, -1.0F, 0.3F, 2.1F, -0.4F);
				box(b, block(dye(color) + "_wool"), -0.75F, 0.8F, -0.25F, 0.75F, 1.25F, 0.95F);
			}
			default -> {
			}
		}
	}

	private static void rickshaw(List<Box> b, List<Wheel> wheels, int load, int color) {
		BlockState wood = block("spruce_planks"), dark = block("dark_oak_planks"), pole = block("stripped_bamboo_block");
		BlockState cloth = block(dye(color) + "_wool"), cushion = block(dye(color) + "_wool");
		wheels.add(new Wheel(-0.72F, 0.6F, -0.15F, 0.6F));
		wheels.add(new Wheel(0.72F, 0.6F, -0.15F, 0.6F));
		// the axle and the frame over it
		box(b, dark, -0.7F, 0.55F, -0.2F, 0.7F, 0.65F, -0.1F);
		box(b, dark, -0.55F, 0.65F, -0.75F, 0.55F, 0.75F, 0.35F);
		// the bench, its cushion, its back and arms
		box(b, wood, -0.5F, 0.75F, -0.6F, 0.5F, 0.8F, 0.1F);
		box(b, cushion, -0.45F, 0.8F, -0.55F, 0.45F, 0.88F, 0.05F);
		box(b, wood, -0.5F, 0.8F, -0.65F, 0.5F, 1.55F, -0.55F);
		box(b, dark, -0.55F, 0.8F, -0.6F, -0.48F, 1.1F, 0.1F);
		box(b, dark, 0.48F, 0.8F, -0.6F, 0.55F, 1.1F, 0.1F);
		// the footboard, down in front
		box(b, wood, -0.4F, 0.45F, 0.1F, 0.4F, 0.52F, 0.55F);
		box(b, dark, -0.42F, 0.45F, 0.08F, -0.36F, 0.75F, 0.14F);
		box(b, dark, 0.36F, 0.45F, 0.08F, 0.42F, 0.75F, 0.14F);
		// the hood: ribs up from the arms and a cloth over them, folded back
		box(b, dark, -0.55F, 1.1F, -0.62F, -0.5F, 2.0F, -0.56F);
		box(b, dark, 0.5F, 1.1F, -0.62F, 0.55F, 2.0F, -0.56F);
		box(b, dark, -0.55F, 1.1F, 0.0F, -0.5F, 1.85F, 0.06F);
		box(b, dark, 0.5F, 1.1F, 0.0F, 0.55F, 1.85F, 0.06F);
		box(b, cloth, -0.58F, 1.95F, -0.7F, 0.58F, 2.03F, -0.05F);
		box(b, cloth, -0.58F, 1.8F, -0.1F, 0.58F, 1.95F, 0.12F);
		box(b, cloth, -0.6F, 1.2F, -0.72F, -0.55F, 2.0F, -0.1F);
		box(b, cloth, 0.55F, 1.2F, -0.72F, 0.6F, 2.0F, -0.1F);
		// the rack behind, and its load
		box(b, dark, -0.5F, 0.6F, -1.1F, 0.5F, 0.68F, -0.65F);
		switch (load) {
			case Wagon.STONE -> {
				box(b, block("cobblestone"), -0.45F, 0.68F, -1.08F, 0.0F, 1.05F, -0.7F);
				box(b, block("iron_ore"), 0.02F, 0.68F, -1.05F, 0.42F, 1.0F, -0.72F);
			}
			case Wagon.FOOD -> {
				box(b, block("hay_block"), -0.45F, 0.68F, -1.08F, 0.05F, 1.1F, -0.7F);
				box(b, block("pumpkin"), 0.08F, 0.68F, -1.02F, 0.42F, 1.0F, -0.72F);
			}
			case Wagon.GOODS -> {
				box(b, block("barrel"), -0.45F, 0.68F, -1.08F, -0.02F, 1.12F, -0.7F);
				box(b, block("barrel"), 0.02F, 0.68F, -1.08F, 0.45F, 1.05F, -0.7F);
			}
			default -> {
			}
		}
		// the shafts, out to the puller's hands (about his waist, either side of him)
		box(b, pole, -0.42F, 0.72F, 0.3F, -0.35F, 0.79F, 2.1F);
		box(b, pole, 0.35F, 0.72F, 0.3F, 0.42F, 0.79F, 2.1F);
		box(b, pole, -0.42F, 0.72F, 0.3F, 0.42F, 0.79F, 0.36F);
	}

	/** A king's carriage ({@code royal}: gilt) or (0.5.27) a hired carriage, the same coach in plain wood and its realm's cloth. */
	private static void carriage(List<Box> b, List<Wheel> wheels, int color, boolean royal) {
		BlockState wood = block(royal ? "dark_oak_planks" : "spruce_planks"), post = block(royal ? "stripped_dark_oak_log" : "stripped_spruce_log");
		BlockState pole = block("stripped_spruce_log");
		BlockState cloth = block(dye(color) + "_wool"), gold = block(royal ? "gold_block" : "dark_oak_planks");
		wheels.add(new Wheel(-0.98F, 0.65F, -0.85F, 0.65F));
		wheels.add(new Wheel(0.98F, 0.65F, -0.85F, 0.65F));
		wheels.add(new Wheel(-0.92F, 0.45F, 0.95F, 0.45F));
		wheels.add(new Wheel(0.92F, 0.45F, 0.95F, 0.45F));
		box(b, wood, -0.95F, 0.6F, -0.9F, 0.95F, 0.7F, -0.8F);
		box(b, wood, -0.9F, 0.4F, 0.9F, 0.9F, 0.5F, 1.0F);
		// the chassis and the coachman's boot in front
		box(b, wood, -0.8F, 0.6F, -1.35F, 0.8F, 0.75F, 1.3F);
		box(b, wood, -0.7F, 0.75F, 0.8F, 0.7F, 1.15F, 1.3F);
		// the cabin: a back and front wall, low panels and a top band on each side (open windows between), corner posts
		box(b, wood, -0.8F, 0.75F, -1.25F, 0.8F, 2.7F, -1.15F);
		box(b, wood, -0.8F, 0.75F, 0.65F, 0.8F, 2.7F, 0.75F);
		for (float x : new float[] {-0.85F, 0.75F}) {
			box(b, wood, x, 0.75F, -1.15F, x + 0.1F, 1.3F, 0.65F);
			box(b, wood, x, 2.4F, -1.15F, x + 0.1F, 2.7F, 0.65F);
			box(b, post, x - 0.02F, 0.75F, -1.27F, x + 0.12F, 2.75F, -1.13F);
			box(b, post, x - 0.02F, 0.75F, 0.63F, x + 0.12F, 2.75F, 0.77F);
			box(b, gold, x - 0.02F, 1.3F, -1.15F, x + 0.12F, 1.35F, 0.65F);
		}
		// its roof and gilt edge, the cushion inside
		box(b, cloth, -0.95F, 2.7F, -1.4F, 0.95F, 2.85F, 0.9F);
		box(b, gold, -0.97F, 2.65F, -1.42F, 0.97F, 2.7F, 0.92F);
		box(b, cloth, -0.65F, 0.75F, -1.1F, 0.65F, 1.0F, -0.45F);
		// lanterns at the front corners
		box(b, block("lantern"), -1.05F, 1.8F, 0.7F, -0.75F, 2.2F, 1.0F);
		box(b, block("lantern"), 0.75F, 1.8F, 0.7F, 1.05F, 2.2F, 1.0F);
		// the shafts
		box(b, pole, -0.5F, 0.72F, 1.3F, -0.4F, 0.82F, 2.85F);
		box(b, pole, 0.4F, 0.72F, 1.3F, 0.5F, 0.82F, 2.85F);
		box(b, pole, -0.5F, 0.72F, 2.75F, 0.5F, 0.82F, 2.85F);
	}
}
